"""Unit tests for the display-profile parsing rules."""

import os
import sys
import tempfile
import unittest

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from analyze_video import (  # noqa: E402
    PROFILES,
    detect_person_card,
    extract_frames,
    normalize_frame,
    nominal_of,
    sartorius_value,
)
from generate_qr import generate as generate_qr  # noqa: E402


class SartoriusValueTest(unittest.TestCase):
    def test_f002a_rules(self):
        self.assertAlmostEqual(sartorius_value("f002a", "1000004", 42), 100.0004)
        self.assertAlmostEqual(sartorius_value("f002a", "200002", 42), 20.0002)
        self.assertAlmostEqual(sartorius_value("f002a", "20001", 42), 2.0001)

    def test_f002b_g_range(self):
        self.assertAlmostEqual(sartorius_value("f002b", "1000003", 42), 100.0003)

    def test_f002b_mg_range_uses_left_edge(self):
        self.assertAlmostEqual(sartorius_value("f002b", "200001", 56), 20.0001)
        self.assertAlmostEqual(sartorius_value("f002b", "200008", 86), 2.00008)
        self.assertAlmostEqual(sartorius_value("f002b", "200006", 89), 2.00006)

    def test_unknown_length_is_rejected(self):
        self.assertIsNone(sartorius_value("f002b", "1234", 56))
        self.assertIsNone(sartorius_value("f002a", "12345678", 56))

    def test_nominal_clustering(self):
        nominals = [100, 20, 2]
        self.assertEqual(nominal_of(100.0008, nominals), 100)
        self.assertEqual(nominal_of(20.0001, nominals), 20)
        self.assertEqual(nominal_of(2.00008, nominals), 2)

    def test_profiles_are_data_driven(self):
        self.assertEqual(set(PROFILES), {"f001", "f002a", "f002b"})
        self.assertEqual(PROFILES["f002b"]["decoder"], "sartorius_mixed")

    def test_person_card_qr_is_detected(self):
        qr = cv2.QRCodeEncoder.create().encode("RY-001")
        image = np.full((480, 320), 230, dtype=np.uint8)
        image[80:280, 80:280] = cv2.resize(qr, (200, 200))
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "frame.jpg")
            cv2.imwrite(path, image)
            self.assertEqual(detect_person_card([path]), "RY-001")

    def test_mobile_entry_qr_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "mobile.png")
            value = "http://192.168.1.20:5173/mobile"
            generate_qr(value, path, 300)
            decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imread(path))
            self.assertEqual(decoded, value)

    def test_extract_frames_falls_back_to_opencv(self):
        with tempfile.TemporaryDirectory() as tmp:
            video = os.path.join(tmp, "sample.avi")
            writer = cv2.VideoWriter(
                video,
                cv2.VideoWriter_fourcc(*"MJPG"),
                10.0,
                (64, 48),
            )
            self.assertTrue(writer.isOpened())
            for _ in range(30):
                writer.write(np.zeros((48, 64, 3), dtype=np.uint8))
            writer.release()

            frames = extract_frames(
                video,
                os.path.join(tmp, "frames"),
                os.path.join(tmp, "missing-ffmpeg"),
            )
            self.assertEqual(len(frames), 3)

    def test_phone_resolution_is_normalized_to_reference(self):
        frame = np.zeros((1920, 1080, 3), dtype=np.uint8)
        normalized = normalize_frame(frame)
        self.assertEqual(normalized.shape, (960, 544, 3))


if __name__ == "__main__":
    unittest.main()
