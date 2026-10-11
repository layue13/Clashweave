import unittest
from edge_metrics import allowance, fields, outcome

class BoundaryMetricsTest(unittest.TestCase):
    def test_accept_requires_start_ack(self):
        self.assertEqual(outcome(['OK'], True), 'OTHER_NO_FINAL_ACK')
        self.assertEqual(outcome(['OK', 'START'], True), 'ACCEPTED')
        self.assertEqual(outcome(['ILLEGAL_NODE'], True), 'ILLEGAL_NODE')
        self.assertEqual(outcome(['WINDOW_CLOSED'], True), 'WINDOW_CLOSED')

    def test_unsent_is_not_server_rejection(self):
        self.assertEqual(outcome([], False), 'OTHER_NOT_SENT')
        self.assertEqual(outcome(['REJECT:FUTURE'], True), 'REJECT:FUTURE')

    def test_exact_fields_and_compensation(self):
        d=fields('CW INPUT seq=172 origin=75 stamp=1460 receivedTick=1462 consumedTick=1463 gate=OK')
        self.assertEqual(int(d['receivedTick'])-int(d['stamp']), 2)
        self.assertEqual(allowance(2, dict(ping='100', cap='4', jitter='1')), 2)
        self.assertEqual(allowance(2, dict(ping='0', cap='4', jitter='1')), 0)
        self.assertEqual(allowance(2, dict(ping='100', cap='0', jitter='0')), 0)

if __name__ == '__main__':
    unittest.main()
