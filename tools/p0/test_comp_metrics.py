"""Reconstruction follows frozen production arguments; unknown RTT must remain zero."""
import unittest
from comp_metrics import allowance

class CompensationMetricsTest(unittest.TestCase):
    def test_unknown_ping_and_baseline_have_no_compensation(self):
        self.assertEqual(allowance(6,{'ping':'0','cap':'4','jitter':'1'}),0)
        self.assertEqual(allowance(6,{'ping':'100','cap':'0','jitter':'0'}),0)

    def test_age_config_and_rtt_caps_are_all_preserved(self):
        self.assertEqual(allowance(6,{'ping':'100','cap':'4','jitter':'1'}),2)
        self.assertEqual(allowance(1,{'ping':'100','cap':'4','jitter':'1'}),1)
        self.assertEqual(allowance(6,{'ping':'500','cap':'4','jitter':'1'}),4)
        self.assertEqual(allowance(0,{'ping':'100','cap':'4','jitter':'1'}),0)

if __name__=='__main__':unittest.main()
