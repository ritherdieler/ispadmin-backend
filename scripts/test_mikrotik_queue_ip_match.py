#!/usr/bin/env python3
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mikrotik_queue_match import queue_matches_ip, queue_targets_host  # noqa: E402


class MikrotikQueueIpMatchTest(unittest.TestCase):
    def test_exact_slash32(self):
        q = {"target": "192.168.30.18/32", "name": "id:2349, usuario:lab"}
        self.assertTrue(queue_matches_ip(q, "192.168.30.18"))

    def test_does_not_match_prefix_neighbors(self):
        ip = "192.168.30.18"
        for other in (
            "192.168.30.180/32",
            "192.168.30.181/32",
            "192.168.30.189/32",
            "192.168.30.1/32",
            "10.0.0.18/32",
        ):
            self.assertFalse(queue_matches_ip({"target": other, "name": f"x {other}"}, ip), other)

    def test_name_containing_ip_is_ignored(self):
        q = {"target": "10.0.0.1/32", "name": "cliente 192.168.30.18"}
        self.assertFalse(queue_matches_ip(q, "192.168.30.18"))

    def test_empty_ip_never_matches(self):
        self.assertFalse(queue_matches_ip({"target": "192.168.30.18/32"}, ""))

    def test_multi_target(self):
        q = {"target": "10.0.0.1/32,192.168.30.18/32"}
        self.assertTrue(queue_matches_ip(q, "192.168.30.18"))
        self.assertEqual(queue_targets_host(q["target"]), ["10.0.0.1", "192.168.30.18"])


if __name__ == "__main__":
    unittest.main()
