#!/usr/bin/env python3
import unittest

from tr069_e2e_mk_ping import is_ping_successful, summarize_ping


class Tr069E2eMkPingTest(unittest.TestCase):
    def test_empty_is_failure(self):
        self.assertFalse(is_ping_successful([]))
        self.assertFalse(is_ping_successful(None))
        self.assertFalse(is_ping_successful({}))

    def test_received_cumulative_is_success(self):
        body = [
            {
                "host": "192.168.30.233",
                "seq": "0",
                "received": "1",
                "sent": "1",
                "packet-loss": "0",
                "time": "2ms",
            },
            {
                "host": "192.168.30.233",
                "seq": "1",
                "received": "2",
                "sent": "2",
                "packet-loss": "0",
            },
        ]
        self.assertTrue(is_ping_successful(body))
        self.assertIn("received=2", summarize_ping(body))

    def test_all_loss_is_failure(self):
        body = [
            {"host": "192.168.30.1", "seq": "0", "received": "0", "sent": "1", "packet-loss": "100"},
            {"host": "192.168.30.1", "seq": "1", "received": "0", "sent": "2", "packet-loss": "100"},
        ]
        self.assertFalse(is_ping_successful(body))

    def test_alive_status_is_success(self):
        self.assertTrue(is_ping_successful([{"status": "alive", "host": "1.1.1.1"}]))


if __name__ == "__main__":
    unittest.main()
