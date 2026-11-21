"""Unit tests for the display-profile parsing rules."""

import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from analyze_video import nominal_of, sartorius_value  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
