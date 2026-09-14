#!/usr/bin/env python3
"""Unit checks for MikroTik e2e ping helpers (no live RouterOS)."""
from __future__ import annotations

import unittest

from tr069_e2e_mk_ping import (
    arp_has_address,
    is_ping_successful,
    is_wan_connection_ready,
    summarize_ping,
)


class Tr069E2eMkPingTest(unittest.TestCase):
    def test_ping_ok_when_received(self):
        body = [
            {"host": "192.168.250.21", "sent": "1", "received": "0", "status": "timeout"},
            {"host": "192.168.250.21", "sent": "4", "received": "2", "packet-loss": "50"},
        ]
        self.assertTrue(is_ping_successful(body))
        self.assertIn("received=2", summarize_ping(body))

    def test_ping_fail_host_unreachable(self):
        body = [
            {
                "host": "192.168.250.1",
                "sent": "4",
                "received": "0",
                "packet-loss": "100",
                "status": "host unreachable",
            }
        ]
        self.assertFalse(is_ping_successful(body))

    def test_arp_has_address(self):
        rows = [
            {".id": "*1", "address": "192.168.250.1", "mac-address": "AA:BB"},
            {".id": "*2", "address": "192.168.250.21", "mac-address": "CC:DD", "complete": "true"},
        ]
        self.assertTrue(arp_has_address(rows, "192.168.250.21"))
        self.assertFalse(arp_has_address(rows, "192.168.250.99"))
        self.assertFalse(arp_has_address([], "192.168.250.21"))

    def test_wan_connection_ready(self):
        self.assertTrue(is_wan_connection_ready("Connected"))
        self.assertTrue(is_wan_connection_ready("connected"))
        self.assertFalse(is_wan_connection_ready("Disconnected"))
        self.assertFalse(is_wan_connection_ready(None))
        self.assertFalse(is_wan_connection_ready(""))


if __name__ == "__main__":
    unittest.main()
