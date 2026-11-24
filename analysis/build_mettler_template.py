"""Build a Mettler digit template from labelled calibration frames."""

import argparse
from collections import defaultdict
import json
import os

import cv2
import numpy as np

from analyze_video import display_panel, normalize_frame
from mettler_ocr import digit_boxes, norm_glyph


def parse_sample(value):
    path, separator, reading = value.rpartition("=")
    if not separator or not path or not reading.isdigit():
        raise argparse.ArgumentTypeError(
            "sample must use the form image-path=reading-digits"
        )
    return path, reading


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument(
        "--sample",
        action="append",
        type=parse_sample,
        required=True,
        help="repeat for each image; example: frame.jpg=2000006",
    )
    parser.add_argument("--roi", default="80,540,400,180")
    parser.add_argument("--base-template")
    args = parser.parse_args()

    template = {}
    if args.base_template:
        with np.load(args.base_template) as source:
            template = {key: source[key] for key in source.files}

    roi = tuple(int(value) for value in args.roi.split(","))
    glyphs = defaultdict(list)
    for image_path, reading in args.sample:
        image = cv2.imread(image_path, cv2.IMREAD_GRAYSCALE)
        if image is None:
            raise RuntimeError(f"cannot read calibration image: {image_path}")
        panel = display_panel(normalize_frame(image), roi)
        boxes = digit_boxes(panel)
        if len(boxes) != len(reading):
            raise RuntimeError(
                f"{image_path}: expected {len(reading)} digits for {reading}, "
                f"found {len(boxes)} boxes: {json.dumps(boxes)}"
            )
        for digit, box in zip(reading, boxes):
            glyphs[digit].append(norm_glyph(panel, box))

    for digit, samples in glyphs.items():
        template[digit] = np.stack(samples).mean(axis=0)

    if not template:
        raise RuntimeError("no digit templates were generated")
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    np.savez_compressed(args.output, **template)
    print(
        json.dumps(
            {
                "output": args.output,
                "digits": sorted(template),
                "samples": {digit: len(items) for digit, items in glyphs.items()},
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
