"""Template OCR for the Mettler Toledo graphical display (F001 / Balance_2)."""

import os

import cv2
import numpy as np

PANEL = (12, 56, 367, 90)


def digit_boxes(gray):
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    _, mask = cv2.threshold(
        blur, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    _, _, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    boxes = []
    for x, y, w, h, area in stats[1:]:
        if 8 <= w <= 32 and 30 <= h <= 55 and area >= 100 and 30 < x < 260:
            boxes.append(
                {"x": int(x), "y": int(y), "w": int(w), "h": int(h)}
            )
    return sorted(boxes, key=lambda box: box["x"])


def norm_glyph(gray, box):
    patch = gray[
        box["y"] : box["y"] + box["h"], box["x"] : box["x"] + box["w"]
    ]
    patch = cv2.resize(patch, (24, 40), interpolation=cv2.INTER_AREA)
    _, patch = cv2.threshold(
        patch, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    return patch.astype(np.float32) / 255.0


def read_panel(panel, templates):
    """Decode a pre-cropped display panel and return text plus confidence."""
    if panel is None or panel.size == 0:
        return "", 0.0
    boxes = digit_boxes(panel)
    text = ""
    confidences = []
    for box in boxes:
        glyph = norm_glyph(panel, box)
        ranked = []
        for digit, template in templates.items():
            mse = float(np.mean((glyph - template) ** 2))
            ranked.append((mse, digit))
        ranked.sort()
        best = ranked[0][1] if ranked else "?"
        text += best
        if len(ranked) >= 2:
            first, second = ranked[0][0], ranked[1][0]
            confidences.append(max(0.0, min(1.0, 1.0 - first / max(second, 1e-6))))

    blur = cv2.GaussianBlur(panel, (3, 3), 0)
    _, mask = cv2.threshold(
        blur, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    _, _, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    dots = [
        (int(x0), int(y0), int(width), int(height))
        for x0, y0, width, height, _ in stats[1:]
        if 2 <= width <= 9 and 2 <= height <= 9
        and 30 < x0 < 300
        and y0 > 35
    ]
    if dots and boxes:
        dot_center = dots[0][0] + dots[0][2] / 2
        decimal_pos = sum(
            1
            for box in boxes
            if box["x"] + box["w"] / 2 < dot_center
        )
        if 0 < decimal_pos < len(text):
            text = text[:decimal_pos] + "." + text[decimal_pos:]
    confidence = sum(confidences) / len(confidences) if confidences else 0.0
    return text, confidence


def read_frame(path, templates):
    gray = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if gray is None:
        return ""
    x, y, w, h = PANEL
    text, _ = read_panel(gray[y : y + h, x : x + w], templates)
    return text
