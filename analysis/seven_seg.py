"""用 OpenCV 定位七段数字并解码，用于校准显示屏读数。

用法：python seven_seg.py <帧图片> [--debug]
帧图片可以是完整画面，也可以是已裁剪的显示屏区域。
"""

import argparse

import cv2
import numpy as np

SEGMENTS = {
    "0": set("ABCDEF"),
    "1": set("BC"),
    "2": set("ABDEG"),
    "3": set("ABCDG"),
    "4": set("BCFG"),
    "5": set("ACDFG"),
    "6": set("ACDEFG"),
    "7": set("ABC"),
    "8": set("ABCDEFG"),
    "9": set("ABCDFG"),
}


def find_halves(gray):
    """找亮连通域，返回符合七段数字半格尺寸的框。"""
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    # 天平是浅色液晶底、深色笔画，取暗色连通域
    _, mask = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    halves = []
    for i in range(1, n):
        x, y, w, h, area = stats[i]
        if 14 <= w <= 40 and 10 <= h <= 32 and area >= 60:
            halves.append({"x": int(x), "y": int(y), "w": int(w), "h": int(h)})
    return halves


def pair_digits(halves):
    used = set()
    digits = []
    halves = sorted(halves, key=lambda b: (b["y"], b["x"]))
    for i, top in enumerate(halves):
        if i in used:
            continue
        best = None
        for j, bottom in enumerate(halves):
            if j == i or j in used or bottom["y"] <= top["y"]:
                continue
            overlap = min(top["x"] + top["w"], bottom["x"] + bottom["w"]) - max(top["x"], bottom["x"])
            gap = bottom["y"] - (top["y"] + top["h"])
            if overlap >= top["w"] * 0.7 and -2 <= gap <= 8 and abs(top["w"] - bottom["w"]) <= 5:
                best = j
                break
        if best is None:
            continue
        bottom = halves[best]
        used.update({i, best})
        digits.append({
            "x": min(top["x"], bottom["x"]),
            "y": top["y"],
            "w": max(top["x"] + top["w"], bottom["x"] + bottom["w"]) - min(top["x"], bottom["x"]),
            "h": bottom["y"] + bottom["h"] - top["y"],
        })
    return sorted(digits, key=lambda d: d["x"])


def read_segments(gray, box):
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    cell = gray[y:y + h, x:x + w]
    if cell.size == 0:
        return {}, set(), None
    # 用单元格内的 Otsu 阈值取暗笔画，并收紧到字形包围盒
    _, dark = cv2.threshold(cell, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    cols = (dark > 0).sum(axis=0)
    xs_all = np.where(cols > 0)[0]
    if len(xs_all) < 5:
        return {}, set(), None
    # 取最长连续列段，避免把相邻数字或小数点并进来
    runs, start = [], xs_all[0]
    for prev, cur in zip(xs_all, xs_all[1:]):
        if cur - prev > 2:
            runs.append((start, prev))
            start = cur
    runs.append((start, xs_all[-1]))
    gx0, gx1 = max(runs, key=lambda r: r[1] - r[0])
    sub = dark[:, gx0:gx1 + 1] > 0
    ys = np.where(sub.any(axis=1))[0]
    if len(ys) < 10:
        return {}, set(), None
    gy0, gy1 = int(ys.min()), int(ys.max())
    gw, gh = gx1 - gx0 + 1, gy1 - gy0 + 1

    def mean(px0, px1, py0, py1):
        patch = gray[y + gy0 + py0:y + gy0 + py1, x + gx0 + px0:x + gx0 + px1]
        return float(patch.mean()) if patch.size else 0.0

    def rel_x(a, b):
        return int(gw * a), max(int(gw * a) + 1, int(gw * b))

    def rel_y(a, b):
        return int(gh * a), max(int(gh * a) + 1, int(gh * b))

    ax0, ax1 = rel_x(.22, .78)
    lx0, lx1 = rel_x(0, .20)
    rx0, rx1 = rel_x(.80, 1.0)
    ay0, ay1 = rel_y(0, .12)
    my0, my1 = rel_y(.44, .56)
    dy0, dy1 = rel_y(.88, 1.0)
    uy0, uy1 = rel_y(.14, .44)
    ly0, ly1 = rel_y(.56, .86)
    probes = {
        "A": (ax0, ax1, ay0, ay1),
        "G": (ax0, ax1, my0, my1),
        "D": (ax0, ax1, dy0, dy1),
        "F": (lx0, lx1, uy0, uy1),
        "B": (rx0, rx1, uy0, uy1),
        "E": (lx0, lx1, ly0, ly1),
        "C": (rx0, rx1, ly0, ly1),
    }
    values = {k: mean(*v) for k, v in probes.items()}
    lo, hi = min(values.values()), max(values.values())
    cut = lo + (hi - lo) * 0.45 if hi > lo else lo
    # 笔画是暗色，均值越低越可能是点亮的段
    on = {k for k, v in values.items() if v < cut}
    text = None
    for digit, pattern in SEGMENTS.items():
        if pattern == on:
            text = digit
            break
    return values, on, text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--debug", action="store_true")
    parser.add_argument("--panel", default="12,56,367,90", help="显示屏区域 x,y,w,h")
    args = parser.parse_args()

    gray = cv2.imread(args.image, cv2.IMREAD_GRAYSCALE)
    if gray is None:
        raise SystemExit("无法读取图片：" + args.image)
    px, py, pw, ph = (int(v) for v in args.panel.split(","))
    if gray.shape[1] >= px + pw and gray.shape[0] >= py + ph:
        gray = gray[py:py + ph, px:px + pw]
    halves = find_halves(gray)
    digits = pair_digits(halves)
    print(f"shape={gray.shape} halves={len(halves)} digits={len(digits)}")
    out = []
    for idx, box in enumerate(digits, 1):
        values, on, text = read_segments(gray, box)
        out.append(text or "?")
        if args.debug:
            print(f"#{idx} {box} seg={''.join(sorted(on))} mean={ {k: round(v) for k, v in values.items()} } -> {text}")
    print("decoded=" + "".join(out))


if __name__ == "__main__":
    main()
