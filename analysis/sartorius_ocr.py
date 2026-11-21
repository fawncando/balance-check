"""Template OCR for the Sartorius seven-segment display (F002)."""

import cv2
import numpy as np

PANEL = (12, 56, 367, 90)


def digit_boxes(gray):
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    _, mask = cv2.threshold(
        blur, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    _, _, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    comps = [
        {"x": int(x), "y": int(y), "w": int(w), "h": int(h)}
        for x, y, w, h, area in stats[1:]
        if 4 <= w <= 40
        and 8 <= h <= 55
        and area >= 25
        and 30 < x < 300
    ]
    whole = [comp for comp in comps if comp["h"] >= 30]
    halves = [comp for comp in comps if comp["h"] < 30]
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
            if -2 <= gap <= 12 and center_gap <= 6:
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


def glyph(gray, box):
    patch = gray[
        box["y"] : box["y"] + box["h"], box["x"] : box["x"] + box["w"]
    ]
    patch = cv2.resize(patch, (24, 42), interpolation=cv2.INTER_AREA)
    _, patch = cv2.threshold(
        patch, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    return patch.astype(np.float32) / 255.0
