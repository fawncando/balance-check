"""Run the three sample-video recognition cases and compare to known results."""

import argparse
import os
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "analysis"))

from analyze_video import analyze  # noqa: E402


CASES = {
    "f001": {
        "video": "Balance_2.mp4",
        "values": [
            200.0009,
            200.0008,
            200.0009,
            50.0003,
            50.0002,
            50.0003,
            10.0001,
            10.0001,
            10.0001,
        ],
        "units": ["g"] * 9,
    },
    "f002c": {
        "video": "VID_20261124_155020.mp4",
        "values": [
            100.0573,
            100.0573,
            100.0573,
            20.00015,
            20.00015,
            20.00013,
            2.01486,
            2.01473,
            2.0147,
        ],
        "units": ["g", "g", "g", "mg", "mg", "mg", "mg", "mg", "mg"],
        "verdict": "FAIL",
    },
    "f002a": {
        "video": "Balance_1_first.mp4",
        "values": [
            100.0004,
            100.0004,
            100.0004,
            20.0002,
            20.0002,
            20.0002,
            2.0,
            2.0001,
            2.0001,
        ],
        "units": ["g"] * 9,
    },
    "f002b": {
        "video": "Balance_1_second.mp4",
        "values": [
            100.0003,
            100.0003,
            100.0008,
            20.0002,
            20.0001,
            20.0001,
            2.00008,
            2.00006,
            2.00008,
        ],
        "units": ["g", "g", "g", "mg", "mg", "mg", "mg", "mg", "mg"],
    },
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--video-dir",
        default=os.environ.get(
            "BALANCE_VIDEO_DIR", str(Path.home() / "Downloads")
        ),
    )
    parser.add_argument(
        "--ffmpeg", default=os.environ.get("FFMPEG", "ffmpeg")
    )
    parser.add_argument("--tolerance", type=float, default=1e-8)
    args = parser.parse_args()

    video_dir = Path(args.video_dir)
    failures = []
    for profile, case in CASES.items():
        video = video_dir / case["video"]
        if not video.is_file():
            failures.append(f"{profile}: missing video {video}")
            continue

        result = analyze(str(video), profile, args.ffmpeg)
        actual = [item["valueG"] for item in result["weighings"]]
        actual_units = [item.get("displayUnit") for item in result["weighings"]]
        expected = case["values"]
        expected_verdict = case.get("verdict", "PASS")
        if result["verdict"] != expected_verdict:
            failures.append(
                f"{profile}: verdict {result['verdict']}, expected {expected_verdict}"
            )
        if len(actual) != len(expected):
            failures.append(
                f"{profile}: expected {len(expected)} readings, got {len(actual)}"
            )
            continue
        if any(
            abs(got - want) > args.tolerance
            for got, want in zip(actual, expected)
        ):
            failures.append(
                f"{profile}: values mismatch\nexpected={expected}\nactual={actual}"
            )
            continue
        if actual_units != case["units"]:
            failures.append(
                f"{profile}: units mismatch\nexpected={case['units']}\nactual={actual_units}"
            )
            continue
        print(f"{profile}: PASS ({len(actual)} readings)")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
