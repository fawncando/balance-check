"""Template OCR for the Sartorius seven-segment display (F002)."""

import cv2
import numpy as np

PANEL = (12, 56, 367, 90)


def _threshold(gray, threshold_type, threshold_value):
    if threshold_value and threshold_value > 0:
        return cv2.threshold(
            gray, float(threshold_value), 255, threshold_type
        )[1]
    return cv2.threshold(
        gray, 0, 255, threshold_type + cv2.THRESH_OTSU
    )[1]


def digit_boxes(gray, light_digits=False, threshold_value=0):
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    threshold_type = (
        cv2.THRESH_BINARY if light_digits
        else cv2.THRESH_BINARY_INV
    )
    mask = _threshold(blur, threshold_type, threshold_value)
    _, _, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    comps = [
        {
            "x": int(x),
            "y": int(y),
            "w": int(w),
            "h": int(h),
            "area": int(area),
        }
        for x, y, w, h, area in stats[1:]
        if 4 <= w <= 40
        and 8 <= h <= 55
        and area >= 25
        and 30 < x < 340
    ]
    whole = [comp for comp in comps if comp["h"] >= 30]
    halves = [
        comp for comp in comps
        if comp["h"] < 30 and comp["area"] >= 50
    ]
    digits = list(whole)
    used = set()
    for index, top in enumerate(halves):
        if index in used:
            continue
        for bottom_index, bottom in enumerate(halves):
            if bottom_index <= index or bottom_index in used:
                continue
            gap = bottom["y"] - (top["y"] + top["h"])
            center_gap = abs(
                (top["x"] + top["w"] / 2)
                - (bottom["x"] + bottom["w"] / 2)
            )
            overlap = min(
                top["x"] + top["w"], bottom["x"] + bottom["w"]
            ) - max(top["x"], bottom["x"])
            narrow_width = min(top["w"], bottom["w"])
            widest = max(top["w"], bottom["w"])
            if (
                -2 <= gap <= 12
                and overlap > narrow_width * 0.35
                and center_gap <= max(6, widest * 0.5)
            ):
                left = min(top["x"], bottom["x"])
                right = max(
                    top["x"] + top["w"], bottom["x"] + bottom["w"]
                )
                digits.append(
                    {
                        "x": left,
                        "y": top["y"],
                        "w": right - left,
                        "h": bottom["y"] + bottom["h"] - top["y"],
                    }
                )
                used.update({index, bottom_index})
                break
    return sorted(digits, key=lambda box: box["x"])


def glyph(gray, box, light_digits=False, threshold_value=0):
    patch = gray[
        box["y"] : box["y"] + box["h"], box["x"] : box["x"] + box["w"]
    ]
    patch = cv2.resize(patch, (24, 42), interpolation=cv2.INTER_AREA)
    threshold_type = (
        cv2.THRESH_BINARY if light_digits
        else cv2.THRESH_BINARY_INV
    )
    patch = _threshold(patch, threshold_type, threshold_value)
    return patch.astype(np.float32) / 255.0
