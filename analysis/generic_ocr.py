"""General-purpose balance display OCR with nominal-weight validation."""

import re

import cv2
import numpy as np

_ENGINE = None
_AMOUNT = re.compile(
    r"^\s*([+-]?\d{1,7}(?:[.,]\d{1,6})?)\s*(mg|g|kg)?\s*$",
    re.IGNORECASE,
)


def _engine():
    global _ENGINE
    if _ENGINE is None:
        try:
            from rapidocr_onnxruntime import RapidOCR
        except ImportError as exc:
            raise RuntimeError(
                "通用 OCR 未安装，请执行 pip install -r analysis/requirements.txt"
            ) from exc
        _ENGINE = RapidOCR()
    return _ENGINE


def _box_metrics(box):
    points = np.asarray(box, dtype=np.float32)
    left = float(points[:, 0].min())
    right = float(points[:, 0].max())
    top = float(points[:, 1].min())
    bottom = float(points[:, 1].max())
    return {
        "left": left,
        "right": right,
        "top": top,
        "bottom": bottom,
        "width": right - left,
        "height": bottom - top,
        "cx": (left + right) / 2,
        "cy": (top + bottom) / 2,
    }


def _nearby_unit(metrics, tokens):
    for token in tokens:
        unit = (token["text"] or "").strip().lower()
        if unit not in {"g", "mg", "kg"}:
            continue
        other = token["box"]
        same_row = abs(other["cy"] - metrics["cy"]) <= max(24, metrics["height"])
        close_x = (
            -0.5 * metrics["width"]
            <= other["cx"] - metrics["cx"]
            <= 2.2 * metrics["width"]
        )
        if same_row and close_x:
            return unit
    return None


def _scaled_value(value, unit):
    if unit == "kg":
        return value * 1000
    if unit == "mg":
        return value / 1000
    return value


def choose_candidate(tokens, nominals, image_height, image_width):
    """Choose the most likely main weight from OCR tokens."""
    if not tokens or not nominals:
        return None
    closest_nominal = min(nominals, key=lambda value: abs(value))
    min_height = max(18.0, image_height * 0.012)
    candidates = []
    for token in tokens:
        match = _AMOUNT.match((token["text"] or "").replace(" ", ""))
        if not match:
            continue
        try:
            raw_value = float(match.group(1).replace(",", "."))
        except ValueError:
            continue
        if raw_value <= 0:
            continue
        metrics = token["box"]
        if metrics["height"] < min_height:
            continue
        explicit_unit = match.group(2)
        unit = explicit_unit.lower() if explicit_unit else _nearby_unit(metrics, tokens)
        unit_options = [unit] if unit else ["g", "mg"]
        for option in unit_options:
            value_g = _scaled_value(raw_value, option)
            nominal = min(nominals, key=lambda value: abs(value - value_g))
            relative_error = abs(value_g - nominal) / max(nominal, 1e-9)
            if relative_error > 0.08:
                continue
            # The main reading is usually large and near the horizontal centre.
            height_score = min(1.0, metrics["height"] / max(image_height * 0.07, 1))
            centre_score = 1.0 - min(
                1.0, abs(metrics["cx"] - image_width / 2) / max(image_width / 2, 1)
            )
            score = (
                float(token.get("confidence") or 0) * 0.65
                + height_score * 0.25
                + centre_score * 0.10
                - relative_error
            )
            candidates.append(
                {
                    "valueG": round(value_g, 8),
                    "confidence": max(0.0, min(1.0, score)),
                    "unit": option,
                    "text": token["text"],
                    "nominal": nominal,
                    "error": relative_error,
                    "box": metrics,
                }
            )
    if not candidates:
        return None
    candidates.sort(key=lambda item: (item["confidence"], -item["error"]), reverse=True)
    return candidates[0]


def _tokens(result):
    tokens = []
    for item in result or []:
        if not item or len(item) < 2:
            continue
        if len(item) >= 3 and not isinstance(item[0], str):
            box, text, confidence = item[0], item[1], item[2]
        elif isinstance(item[0], str):
            continue
        else:
            continue
        tokens.append(
            {
                "box": _box_metrics(box),
                "text": str(text),
                "confidence": float(confidence),
            }
        )
    return tokens


def _text_confidence(item):
    """Read text and confidence from both supported RapidOCR result layouts."""
    if not item or len(item) < 2:
        return None
    if len(item) >= 3 and not isinstance(item[0], str):
        text, confidence = item[1], item[2]
    elif isinstance(item[0], str):
        text, confidence = item[0], item[1]
    else:
        return None
    try:
        return str(text), float(confidence)
    except (TypeError, ValueError):
        return None


def read_gray_frame(frame, nominals):
    """Read one frame, retrying common rotations when no valid reading is found."""
    if frame is None or frame.size == 0 or not nominals:
        return None, 0.0, None
    variants = [
        frame,
        cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE),
        cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE),
        cv2.rotate(frame, cv2.ROTATE_180),
    ]
    for index, image in enumerate(variants):
        result, _ = _engine()(image)
        candidate = choose_candidate(
            _tokens(result), nominals, image.shape[0], image.shape[1]
        )
        if candidate:
            candidate["rotation"] = index * 90
            return candidate["valueG"], candidate["confidence"], candidate["unit"]
    return None, 0.0, None


def read_native_digits(frame, reference_roi, reference_size=(544, 960)):
    """Read digits from the native-resolution equivalent of a reference ROI."""
    if frame is None or frame.size == 0:
        return None, 0.0
    scale_x = frame.shape[1] / reference_size[0]
    scale_y = frame.shape[0] / reference_size[1]
    x, y, width, height = reference_roi
    x0 = max(0, int(x * scale_x))
    y0 = max(0, int(y * scale_y))
    x1 = min(frame.shape[1], int((x + width) * scale_x))
    y1 = min(frame.shape[0], int((y + height) * scale_y))
    crop = frame[y0:y1, x0:x1]
    if crop.size == 0:
        return None, 0.0
    result, _ = _engine()(crop, use_det=False, use_cls=False, use_rec=True)
    best = None
    for item in result or []:
        parsed = _text_confidence(item)
        if not parsed:
            continue
        text, score = parsed
        digits = "".join(char for char in text if char.isdigit())
        if 5 <= len(digits) <= 8 and (best is None or score > best[1]):
            best = (digits, score)
    return best if best else (None, 0.0)


def _rotate(image, rotation):
    if rotation == 90:
        return cv2.rotate(image, cv2.ROTATE_90_CLOCKWISE)
    if rotation == 180:
        return cv2.rotate(image, cv2.ROTATE_180)
    if rotation == 270:
        return cv2.rotate(image, cv2.ROTATE_90_COUNTERCLOCKWISE)
    return image


def _read_candidate(image, nominals, detect=True):
    if detect:
        result, _ = _engine()(image)
        tokens = _tokens(result)
    else:
        result, _ = _engine()(image, use_det=False, use_cls=False, use_rec=True)
        tokens = []
        for item in result or []:
            parsed = _text_confidence(item)
            if not parsed:
                continue
            text, confidence = parsed
            tokens.append(
                {
                    "box": _box_metrics(
                        [[0, 0], [image.shape[1], 0],
                         [image.shape[1], image.shape[0]], [0, image.shape[0]]]
                    ),
                    "text": text,
                    "confidence": confidence,
                }
            )
    return choose_candidate(tokens, nominals, image.shape[0], image.shape[1])


def read_frames(frames, nominals):
    """Detect orientation/ROI once, then OCR only the main reading area."""
    if not frames or not nominals:
        return []
    sample_count = min(3, len(frames))
    sample_indexes = sorted({
        round(index * (len(frames) - 1) / max(1, sample_count - 1))
        for index in range(sample_count)
    })
    best_rotation = None
    best_samples = []
    best_score = -1.0
    for rotation in (0, 90, 270, 180):
        found = []
        for index in sample_indexes:
            image = cv2.imread(frames[index], cv2.IMREAD_GRAYSCALE)
            if image is None:
                continue
            candidate = _read_candidate(_rotate(image, rotation), nominals)
            if candidate:
                found.append(candidate)
        score = (
            len(found) * 2.0
            + sum(item["confidence"] for item in found) / max(len(found), 1)
        )
        if score > best_score:
            best_score = score
            best_rotation = rotation
            best_samples = found
    if best_rotation is None or not best_samples:
        return [(index, None, 0.0, None) for index in range(1, len(frames) + 1)]

    left = min(item["box"]["left"] for item in best_samples)
    right = max(item["box"]["right"] for item in best_samples)
    top = min(item["box"]["top"] for item in best_samples)
    bottom = max(item["box"]["bottom"] for item in best_samples)
    width = max(right - left, 120.0)
    height = max(bottom - top, 36.0)
    x0 = int(max(0, left - width * 0.8))
    x1 = int(right + width * 0.8)
    y0 = int(max(0, top - height * 1.5))
    y1 = int(bottom + height * 1.5)

    decoded = []
    for second, path in enumerate(frames, 1):
        image = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
        if image is None:
            decoded.append((second, None, 0.0, None))
            continue
        image = _rotate(image, best_rotation)
        candidate = _read_candidate(
            image[max(0, y0):y1, max(0, x0):x1],
            nominals,
            detect=False,
        )
        if candidate is None:
            decoded.append((second, None, 0.0, None))
        else:
            decoded.append(
                (second, candidate["valueG"], candidate["confidence"], candidate["unit"])
            )
    return decoded
