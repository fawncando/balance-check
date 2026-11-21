"""Analyze one balance-calibration video and output the nine weighings as JSON.

Usage:
  python analyze_video.py --video path.mp4 --profile f001 [--out result.json]
Profiles:
  f001  Mettler Toledo graphical display
  f002a Sartorius seven-segment display, all readings in g
  f002b Sartorius seven-segment display, second range in mg
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

PROFILES = {
    "f001": ("f001_mettler.npz", [200, 50, 10]),
    "f002a": ("f002a_sartorius.npz", [100, 20, 2]),
    "f002b": ("f002b_sartorius.npz", [100, 20, 2]),
}

# The display is right-aligned. For f002b, the 20 g range starts near x=56
# (20000.x mg), while the 2 g range starts near x=86 (2000.0x mg). Keeping the
# threshold in the display profile avoids guessing the decimal position from
# the digit sequence alone.
SARTORIUS_LEFT_ALIGN_X = 70


def load_templates(profile):
    file, nominals = PROFILES[profile]
    data = np.load(os.path.join(HERE, "templates", file))
    return {key: data[key] for key in data.files}, nominals


def extract_frames(video, target, ffmpeg):
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
            "fps=1,crop=400:180:80:590",
            "-q:v",
            "2",
            pattern,
            "-y",
        ],
        check=True,
    )
    return sorted(glob.glob(os.path.join(target, "f*.jpg")))


def sartorius_value(profile, text, digit_x0):
    """Apply the display profile's decimal point and unit rules.

    f002b uses a 7-digit g reading on the 120 g range. On the lower range the
    display switches to mg: 20000.x mg and 2000.0x mg both have six digits, so
    their common digit representation is ambiguous. The left edge of the digit
    area resolves the decimal position without relying on weighing order.
    """

    unit_mg = False
    if profile == "f002a":
        positions = {7: 3, 6: 2, 5: 1}
        pos = positions.get(len(text))
        if pos is None:
            return None
    elif profile == "f002b":
        if len(text) == 7:
            pos = 3
        elif len(text) == 6:
            pos = 5 if digit_x0 < SARTORIUS_LEFT_ALIGN_X else 4
            unit_mg = True
        else:
            return None
    else:
        raise ValueError(f"unsupported Sartorius profile: {profile}")

    try:
        value = float(text[:pos] + "." + text[pos:])
    except ValueError:
        return None
    if unit_mg:
        value /= 1000
    return round(value, 8)


def decode_frame(path, profile, templates):
    gray = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if gray is None:
        return None
    x, y, w, h = sartorius.PANEL
    panel = gray[y : y + h, x : x + w]
    if profile == "f001":
        text = mettler.read_frame(path, templates)
        try:
            return round(float(text.replace(" ", "")), 8)
        except ValueError:
            return None

    boxes = sartorius.digit_boxes(panel)
    text = ""
    for box in boxes:
        glyph = sartorius.glyph(panel, box)
        best, score = "?", float("inf")
        for digit, template in templates.items():
            mse = float(((glyph - template) ** 2).mean())
            if mse < score:
                best, score = digit, mse
        text += best
    if "?" in text or not boxes:
        return None
    return sartorius_value(profile, text, boxes[0]["x"])


def nominal_of(value, nominals):
    return min(nominals, key=lambda nominal: abs(nominal - value))


def decode_values(frames, profile, templates):
    return [
        (index, decode_frame(path, profile, templates))
        for index, path in enumerate(frames, 1)
    ]


def collect_weighings(values, nominals):
    runs = []
    current = None
    for second, value in values:
        if current and current["value"] == value:
            current["end"] = second
        else:
            if current:
                runs.append(current)
            current = {"start": second, "end": second, "value": value}
    if current:
        runs.append(current)

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
                bucket[-1]["longest"] = run["end"] - run["start"]
        else:
            bucket.append(
                {
                    "start": run["start"],
                    "end": run["end"],
                    "value": run["value"],
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
                }
            )
    return weighings


def analyze(video, profile, ffmpeg, out_path=None):
    if profile not in PROFILES:
        raise ValueError(f"unknown profile: {profile}")
    if not os.path.isfile(video):
        raise FileNotFoundError(video)

    templates, nominals = load_templates(profile)
    with tempfile.TemporaryDirectory(prefix="bc-rec-") as tmp:
        frames = extract_frames(video, tmp, ffmpeg)
        values = decode_values(frames, profile, templates)

    weighings = collect_weighings(values, nominals)
    passed = len(weighings) == 9
    for weighing in weighings:
        low = weighing["nominalG"] * 0.9995
        high = weighing["nominalG"] * 1.0005
        weighing["qualified"] = low <= weighing["valueG"] <= high
        passed = passed and weighing["qualified"]

    result = {
        "video": os.path.basename(video),
        "profile": profile,
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
    parser.add_argument("--profile", required=True, choices=PROFILES)
    parser.add_argument("--out")
    parser.add_argument("--ffmpeg", default=os.environ.get("FFMPEG", "ffmpeg"))
    args = parser.parse_args()
    result = analyze(args.video, args.profile, args.ffmpeg, args.out)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
