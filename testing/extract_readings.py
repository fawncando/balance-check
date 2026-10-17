"""从校验视频里提取稳定读数段，供人工/视觉标注金标准使用。

思路：按 1 fps 抽帧并裁剪出天平显示屏，比较相邻帧的灰度差，
连续多帧几乎不变即视为一次稳定读数；输出每段的起止时间与代表帧。

用法：
  python extract_readings.py video.mp4 --roi 80,590,400,180 --out out/v1 --min-run 3
"""

import argparse
import glob
import json
import os
import subprocess

from PIL import Image, ImageChops, ImageDraw, ImageFont

FFMPEG = os.environ.get("FFMPEG", r"D:\oopz\ffmpeg.exe")
THUMB = (160, 72)


def extract_frames(video, roi, out_dir, fps):
    os.makedirs(out_dir, exist_ok=True)
    x, y, w, h = roi
    pattern = os.path.join(out_dir, "f%05d.jpg")
    subprocess.run(
        [
            FFMPEG, "-hide_banner", "-loglevel", "error", "-i", video,
            "-vf", f"fps={fps},crop={w}:{h}:{x}:{y}",
            "-q:v", "2", pattern, "-y",
        ],
        check=True,
    )
    return sorted(glob.glob(os.path.join(out_dir, "f*.jpg")))


def load_thumb(path):
    with Image.open(path) as img:
        return img.convert("L").resize(THUMB)


def diff_score(a, b):
    diff = ImageChops.difference(a, b)
    pixels = list(diff.getdata())
    return sum(pixels) / len(pixels)


def group_runs(frames, threshold, fps):
    runs = []
    current = {"start": 0, "frames": [frames[0]], "prev": load_thumb(frames[0])}
    for idx in range(1, len(frames)):
        thumb = load_thumb(frames[idx])
        if diff_score(current["prev"], thumb) <= threshold:
            current["frames"].append(frames[idx])
            current["prev"] = thumb
        else:
            current["end"] = idx - 1
            runs.append(current)
            current = {"start": idx, "frames": [frames[idx]], "prev": thumb}
    current["end"] = len(frames) - 1
    runs.append(current)
    for run in runs:
        run["start_sec"] = round(run["start"] / fps, 1)
        run["end_sec"] = round((run["end"] + 1) / fps, 1)
        run["duration"] = round((run["end"] - run["start"] + 1) / fps, 1)
        run["representative"] = run["frames"][len(run["frames"]) // 2]
    return runs


def compose_sheet(runs, out_path, title):
    cols, rows = 4, (len(runs) + 3) // 4
    cell_w, cell_h = THUMB[0] + 12, THUMB[1] + 30
    sheet = Image.new("RGB", (cols * cell_w, rows * cell_h + 26), "white")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    draw.text((6, 6), title, fill="black", font=font)
    for idx, run in enumerate(runs):
        with Image.open(run["representative"]) as img:
            thumb = img.convert("RGB").resize(THUMB)
        x = (idx % cols) * cell_w + 6
        y = (idx // cols) * cell_h + 26
        sheet.paste(thumb, (x, y))
        draw.text(
            (x, y + THUMB[1] + 3),
            f"#{idx + 1} {run['start_sec']:.0f}-{run['end_sec']:.0f}s",
            fill="black",
            font=font,
        )
    sheet.save(out_path, quality=92)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video")
    parser.add_argument("--roi", required=True, help="x,y,w,h")
    parser.add_argument("--out", required=True)
    parser.add_argument("--fps", type=int, default=1)
    parser.add_argument("--min-run", type=float, default=3.0)
    parser.add_argument("--threshold", type=float, default=2.0)
    args = parser.parse_args()

    roi = tuple(int(v) for v in args.roi.split(","))
    frame_dir = os.path.join(args.out, "frames")
    frames = extract_frames(args.video, roi, frame_dir, args.fps)
    runs = group_runs(frames, args.threshold, args.fps)
    stable = [r for r in runs if r["duration"] >= args.min_run]

    sheets = []
    chunk = 12
    for i in range(0, len(stable), chunk):
        part = stable[i:i + chunk]
        path = os.path.join(args.out, f"sheet-{i // chunk + 1}.jpg")
        compose_sheet(part, path, os.path.basename(args.video))
        sheets.append(path)

    manifest = {
        "video": os.path.basename(args.video),
        "roi": roi,
        "fps": args.fps,
        "total_frames": len(frames),
        "stable_runs": [
            {
                "index": i + 1,
                "start_sec": r["start_sec"],
                "end_sec": r["end_sec"],
                "duration": r["duration"],
                "representative": os.path.basename(r["representative"]),
            }
            for i, r in enumerate(stable)
        ],
        "sheets": [os.path.basename(s) for s in sheets],
    }
    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print(f"frames={len(frames)} runs={len(runs)} stable={len(stable)} sheets={len(sheets)}")


if __name__ == "__main__":
    main()
