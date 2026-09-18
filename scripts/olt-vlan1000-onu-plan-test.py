#!/usr/bin/env python3
import unittest

from olt_vlan1000_onu_plan import classify, summarize


class OltVlan1000OnuPlanTest(unittest.TestCase):
    def test_skips_when_service_port_1000_exists(self):
        action = classify(
            {
                "sn": "ZTEGDC47BFFD",
                "line_profile": "Generic_1_V100M1000MGM",
                "has_sp_1000": True,
            }
        )
        self.assertEqual("skip_already", action["kind"])

    def test_profile12_missing_sp_uses_gem2(self):
        action = classify(
            {
                "sn": "VSOL0031C0B6",
                "line_profile": "Generic_1_V100M1000MGM",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("add_sp", action["kind"])
        self.assertEqual(2, action["gemport"])
        self.assertFalse(action["needs_mapping"])

    def test_hd_profile_already_maps_1000_uses_gem1(self):
        action = classify(
            {
                "sn": "VSOL0086ABCD",
                "line_profile": "Generic_1_HD446E747",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("add_sp", action["kind"])
        self.assertEqual(1, action["gemport"])
        self.assertFalse(action["needs_mapping"])

    def test_hf291_needs_mapping_on_gem1_then_sp(self):
        action = classify(
            {
                "sn": "HWTC15F5FF36",
                "line_profile": "Generic_1_HF291F96D",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("add_mapping_then_sp", action["kind"])
        self.assertEqual(1, action["gemport"])
        self.assertEqual(
            "gem mapping 1 3 vlan 1000",
            action["mapping_cmd"],
        )
        self.assertEqual(5, action["profile_id"])

    def test_generic_v100_maps_unused_gem2(self):
        action = classify(
            {
                "sn": "ZTEGDC47E8A0",
                "line_profile": "Generic_1_V100",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("add_mapping_then_sp", action["kind"])
        self.assertEqual(2, action["gemport"])
        self.assertEqual("gem mapping 2 1 vlan 1000", action["mapping_cmd"])
        self.assertEqual(6, action["profile_id"])

    def test_smartolt_g_priority_mode_adds_sp_gem1_without_mapping(self):
        action = classify(
            {
                "sn": "ZTEGDC47E873",
                "line_profile": "SmartOLT_G",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("add_sp", action["kind"])
        self.assertEqual(1, action["gemport"])
        self.assertFalse(action["needs_mapping"])

    def test_unknown_profile_is_manual(self):
        action = classify(
            {
                "sn": "X",
                "line_profile": "NO_SUCH_PROFILE",
                "has_sp_1000": False,
            }
        )
        self.assertEqual("manual", action["kind"])

    def test_summarize_counts_kinds(self):
        plan = [
            classify({"sn": "a", "line_profile": "Generic_1_V100M1000MGM", "has_sp_1000": True}),
            classify({"sn": "b", "line_profile": "Generic_1_HF291F96D", "has_sp_1000": False}),
            classify({"sn": "c", "line_profile": "Generic_1_HD446E747", "has_sp_1000": False}),
        ]
        summary = summarize(plan)
        self.assertEqual(1, summary["skip_already"])
        self.assertEqual(1, summary["add_mapping_then_sp"])
        self.assertEqual(1, summary["add_sp"])


if __name__ == "__main__":
    unittest.main()
