"""Generate a PNG QR code for the mobile upload entry."""

import argparse

import cv2


def generate(text, out, size=320):
    image = cv2.QRCodeEncoder.create().encode(text)
    image = cv2.resize(
        image,
        (size, size),
        interpolation=cv2.INTER_NEAREST,
    )
    if not cv2.imwrite(out, image):
        raise RuntimeError("failed to write QR image")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--size", type=int, default=320)
    args = parser.parse_args()

    generate(args.text, args.out, args.size)


if __name__ == "__main__":
    main()
