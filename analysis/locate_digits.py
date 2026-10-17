"""定位天平显示屏中的七段数字，输出数字框与七段采样。

七段码数字在中间段（G）熄灭时会分成上下两块，因此先找亮连通域，
再把同一列上的上下两块配成一个数字框。
"""

import argparse
import json
from collections import deque

import numpy as np
from PIL import Image


def components(mask, min_area=30):
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    boxes = []
    for y in range(h):
        for x in range(w):
            if not mask[y, x] or seen[y, x]:
                continue
            q = deque([(y, x)])
            seen[y, x] = True
            miny = maxy = y
            minx = maxx = x
            area = 0
            while q:
                cy, cx = q.popleft()
                area += 1
                miny, maxy = min(miny, cy), max(maxy, cy)
                minx, maxx = min(minx, cx), max(maxx, cx)
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        q.append((ny, nx))
            if area >= min_area:
                boxes.append({"x": minx, "y": miny, "w": maxx - minx + 1, "h": maxy - miny + 1, "area": area})
    return boxes


def pair_digits(boxes):
    """把宽度接近、横向重叠的上下两块合成一个数字框。"""
    halves = [b for b in boxes if 16 <= b["w"] <= 34 and 12 <= b["h"] <= 30]
    used = set()
    digits = []
    for i, top in enumerate(halves):
        if i in used:
            continue
        best = None
        for j in range(i + 1, len(halves)):
            if j in used:
                continue
            bottom = halves[j]
            overlap = min(top["x"] + top["w"], bottom["x"] + bottom["w"]) - max(top["x"], bottom["x"])
            gap = bottom["y"] - (top["y"] + top["h"])
            if overlap > top["w"] * 0.7 and -2 <= gap <= 8 and abs(top["w"] - bottom["w"]) <= 4:
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
            "split": gap,
        })
    return sorted(digits, key=lambda d: d["x"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--roi", default="80,590,400,180")
    args = parser.parse_args()
    x, y, w, h = (int(v) for v in args.roi.split(","))

    img = Image.open(args.image).convert("L")
    full = np.asarray(img)
    if full.shape[0] < y + h or full.shape[1] < x + w:
        arr = full
        x = y = 0
        h, w = full.shape
    else:
        arr = full[y:y + h, x:x + w]
    low, mid, high = np.percentile(arr, [10, 50, 90])
    mask = arr > mid + (high - mid) * 0.6
    boxes = components(mask)
    digits = pair_digits(boxes)
    print(f"panel={arr.shape} p10={low:.0f} p50={mid:.0f} p90={high:.0f}")
    print(f"components={len(boxes)} digit_boxes={len(digits)}")
    for i, d in enumerate(digits, 1):
        print(f"  #{i} x={d['x']:3d} y={d['y']:2d} w={d['w']:2d} h={d['h']:2d}")
    print(json.dumps({"roi": [x, y, w, h], "digits": digits}, ensure_ascii=False))


if __name__ == "__main__":
    main()
