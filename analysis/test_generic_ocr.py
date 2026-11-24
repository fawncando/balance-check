"""Unit tests for general OCR candidate selection."""

import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from generic_ocr import _box_metrics, choose_candidate  # noqa: E402


def token(box, text, confidence=0.95):
    return {
        "box": _box_metrics(box),
        "text": text,
        "confidence": confidence,
    }


class GenericOcrTest(unittest.TestCase):
    def test_selects_large_central_reading(self):
        tokens = [
            token([[610, 660], [650, 660], [650, 682], [610, 682]], "2026-09-26"),
            token([[570, 700], [690, 700], [690, 716], [570, 716]], "Max 120 g"),
            token([[320, 780], [640, 780], [640, 850], [320, 850]], "49.99994", 0.99),
        ]
        result = choose_candidate(tokens, [50, 20, 10], 1300, 930)
        self.assertIsNotNone(result)
        self.assertAlmostEqual(result["valueG"], 49.99994)
        self.assertEqual(result["unit"], "g")

    def test_infers_milligrams_from_nominal_weight(self):
        tokens = [
            token([[80, 580], [360, 580], [360, 660], [80, 660]], "20000.12", 0.98),
        ]
        result = choose_candidate(tokens, [100, 20, 2], 960, 544)
        self.assertIsNotNone(result)
        self.assertAlmostEqual(result["valueG"], 20.00012)
        self.assertEqual(result["unit"], "mg")

    def test_rejects_menu_and_date_numbers(self):
        tokens = [
            token([[500, 100], [620, 100], [620, 120], [500, 120]], "0.1mg"),
            token([[500, 130], [650, 130], [650, 150], [500, 150]], "17:20"),
            token([[500, 160], [620, 160], [620, 180], [500, 180]], "0%"),
        ]
        self.assertIsNone(choose_candidate(tokens, [100, 20, 2], 960, 544))


if __name__ == "__main__":
    unittest.main()
