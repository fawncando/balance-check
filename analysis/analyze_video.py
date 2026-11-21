"""Analyze one balance-calibration video and output the nine weighings as JSON.

Usage:
  python analyze_video.py --video path.mp4 --profile f001 [--out result.json]

Display profiles are data-driven through profiles.json. Adding a device requires
a calibrated .npz template, one profile entry, and a backend device mapping.
"""

import argparse
import glob
import json
import os
import subprocess
import sys
import tempfile

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import mettler_ocr as mettler  # noqa: E402
import sartorius_ocr as sartorius  # noqa: E402

PROFILE_FILE = os.path.join(HERE, "profiles.json")
DISPLAY_ROI = (80, 590, 400, 180)


def load_profiles():
    with open(PROFILE_FILE, encoding="utf-8") as handle:
        return json.load(handle)


PROFILES = load_profiles()


def load_templates(profile):
    config = PROFILES[profile]
    data = np.load(os.path.join(HERE, "templates", config["template"]))
    return {key: data[key] for key in data.files}, config


def extract_frames(video, target, ffmpeg):
    """Extract full frames so the same pass can decode displays and QR cards."""
    os.makedirs(target, exist_ok=True)
    pattern = os.path.join(target, "f%05d.jpg")
    subprocess.run(
        [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            video,
            "-vf",
            "fps=1",
            "-q:v",
            "2",
            pattern,
            "-y",
        ],
        check=True,
    )
    return sorted(glob.glob(os.path.join(target, "f*.jpg")))


def display_panel(frame):
    x, y, w, h = DISPLAY_ROI
    cropped = frame[y : y + h, x : x + w]
    px, py, pw, ph = sartorius.PANEL
    return cropped[py : py + ph, px : px + pw]


def sartorius_value(profile, text, digit_x0):
    """Apply the profile's decimal-point and unit rules."""
    rule = PROFILES[profile]["decoder"]
    unit_mg = False
    if rule == "sartorius_g":
        positions = {7: 3, 6: 2, 5: 1}
        pos = positions.get(len(text))
        if pos is None:
            return None
    elif rule == "sartorius_mixed":
        if len(text) == 7:
            pos = 3
        elif len(text) == 6:
            threshold = PROFILES[profile].get("left_align_x", 70)
            pos = 5 if digit_x0 < threshold else 4
            unit_mg = True
        else:
            return None
    else:
        raise ValueError(f"unsupported Sartorius decoder: {rule}")

    try:
        value = float(text[:pos] + "." + text[pos:])
    except ValueError:
        return None
    if unit_mg:
        value /= 1000
    return round(value, 8)


def classify_glyph(glyph, templates):
    ranked = []
    for digit, template in templates.items():
        ranked.append((float(np.mean((glyph - template) ** 2)), digit))
    ranked.sort()
    if not ranked:
        return "?", 0.0
    best = ranked[0][1]
    if len(ranked) == 1:
        return best, 0.7
    first, second = ranked[0][0], ranked[1][0]
    confidence = max(0.0, min(1.0, 1.0 - first / max(second, 1e-6)))
    return best, confidence


def decode_frame(path, profile, templates):
    frame = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if frame is None:
        return None, 0.0
    panel = display_panel(frame)
    config = PROFILES[profile]
    if config["decoder"] == "mettler":
        text, confidence = mettler.read_panel(panel, templates)
        try:
            value = round(float(text.replace(" ", "")), 8)
        except ValueError:
            value = None
        return value, confidence

    boxes = sartorius.digit_boxes(panel)
    text = ""
    confidences = []
    for box in boxes:
        digit, confidence = classify_glyph(sartorius.glyph(panel, box), templates)
        text += digit
        confidences.append(confidence)
    if "?" in text or not boxes:
        return None, 0.0
    value = sartorius_value(profile, text, boxes[0]["x"])
    confidence = sum(confidences) / len(confidences) if confidences else 0.0
    return value, confidence


def detect_person_card(frames):
    """Detect a personnel QR card anywhere in sampled full-frame images."""
    detector = cv2.QRCodeDetector()
    for index, path in enumerate(frames):
        if index % 2:
            continue
        image = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
        if image is None:
            continue
        height, width = image.shape[:2]
        if width > 480:
            scale = 480.0 / width
            image = cv2.resize(image, (480, int(height * scale)))
        decoded, values, _, _ = detector.detectAndDecodeMulti(image)
        if not decoded:
            continue
        for value in values:
            card = (value or "").strip()
            if 2 <= len(card) <= 64:
                return card
    return None


def nominal_of(value, nominals):
    return min(nominals, key=lambda nominal: abs(nominal - value))


def decode_values(frames, profile, templates):
    decoded = []
    for second, path in enumerate(frames, 1):
        value, confidence = decode_frame(path, profile, templates)
        decoded.append((second, value, confidence))
    return decoded


def collect_weighings(values, nominals):
    runs = []
    current = None
    for second, value, confidence in values:
        if current and current["value"] == value:
            current["end"] = second
            current["confidences"].append(confidence)
        else:
            if current:
                runs.append(current)
            current = {
                "start": second,
                "end": second,
                "value": value,
                "confidences": [confidence],
            }
    if current:
        runs.append(current)

    for run in runs:
        run["confidence"] = (
            sum(run["confidences"]) / len(run["confidences"])
            if run["confidences"]
            else 0.0
        )

    stable = [
        run
        for run in runs
        if run["value"] is not None
        and run["end"] - run["start"] + 1 >= 3
        and run["value"] > 0.01
    ]
    clusters = {}
    for run in stable:
        nominal = nominal_of(run["value"], nominals)
        bucket = clusters.setdefault(nominal, [])
        if bucket and run["start"] - bucket[-1]["end"] <= 5:
            bucket[-1]["end"] = run["end"]
            if run["end"] - run["start"] > bucket[-1]["longest"]:
                bucket[-1]["value"] = run["value"]
                bucket[-1]["confidence"] = run["confidence"]
                bucket[-1]["longest"] = run["end"] - run["start"]
        else:
            bucket.append(
                {
                    "start": run["start"],
                    "end": run["end"],
                    "value": run["value"],
                    "confidence": run["confidence"],
                    "longest": run["end"] - run["start"],
                }
            )

    weighings = []
    for weight_index, nominal in enumerate(nominals, 1):
        chosen = sorted(
            clusters.get(nominal, []),
            key=lambda cluster: cluster["end"] - cluster["start"],
            reverse=True,
        )[:3]
        chosen.sort(key=lambda cluster: cluster["start"])
        for measure_index, cluster in enumerate(chosen, 1):
            weighings.append(
                {
                    "weightSeq": weight_index,
                    "measureSeq": measure_index,
                    "nominalG": nominal,
                    "valueG": cluster["value"],
                    "startSec": cluster["start"],
                    "endSec": cluster["end"],
                    "confidence": round(cluster["confidence"] * 100, 2),
                }
            )
    return weighings


def analyze(video, profile, ffmpeg, out_path=None, min_confidence=55.0):
    if profile not in PROFILES:
        raise ValueError(f"unknown profile: {profile}")
    if not os.path.isfile(video):
        raise FileNotFoundError(video)

    templates, config = load_templates(profile)
    with tempfile.TemporaryDirectory(prefix="bc-rec-") as tmp:
        frames = extract_frames(video, tmp, ffmpeg)
        values = decode_values(frames, profile, templates)
        person_card = detect_person_card(frames)

    weighings = collect_weighings(values, config["nominals"])
    passed = len(weighings) == 9
    for weighing in weighings:
        low = weighing["nominalG"] * 0.9995
        high = weighing["nominalG"] * 1.0005
        weighing["qualified"] = low <= weighing["valueG"] <= high
        passed = passed and weighing["qualified"]

    confidence = (
        round(sum(item["confidence"] for item in weighings) / len(weighings), 2)
        if weighings
        else 0.0
    )
    anomaly_frames = sum(1 for _, value, _ in values if value is None)
    # Tare and transition frames are expected to be undecodable. Review is
    # required only when the vast majority of frames cannot be decoded.
    anomaly_limit = max(30, int(len(frames) * 0.8))
    needs_review = (
        confidence < float(min_confidence)
        or anomaly_frames > anomaly_limit
        or len(weighings) != 9
    )

    result = {
        "video": os.path.basename(video),
        "profile": profile,
        "personCard": person_card,
        "confidence": confidence,
        "anomalyFrames": anomaly_frames,
        "needsReview": needs_review,
        "weighings": weighings,
        "verdict": "PASS" if passed else "FAIL",
    }
    if out_path:
        with open(out_path, "w", encoding="utf-8") as handle:
            json.dump(result, handle, ensure_ascii=False, indent=2)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", required=True)
    parser.add_argument("--profile", required=True, choices=sorted(PROFILES))
    parser.add_argument("--out")
    parser.add_argument("--ffmpeg", default=os.environ.get("FFMPEG", "ffmpeg"))
    parser.add_argument("--min-confidence", type=float, default=55.0)
    args = parser.parse_args()
    result = analyze(
        args.video,
        args.profile,
        args.ffmpeg,
        args.out,
        args.min_confidence,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
