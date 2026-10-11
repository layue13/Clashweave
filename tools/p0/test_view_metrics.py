"""Synthetic fixture-summary regression: failures must not swallow subsequent sequences."""
import json, pathlib, subprocess, sys, tempfile, unittest

ROOT=pathlib.Path(__file__).resolve().parents[2]

class ViewMetricsTest(unittest.TestCase):
    def test_failure_then_completion_and_unresolved_are_separate(self):
        # Run a standalone copy in a temporary repository layout; no historical run is touched.
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);script=root/'tools/p0/playfix_metrics.py'
            script.parent.mkdir(parents=True);script.write_bytes((ROOT/'tools/p0/playfix_metrics.py').read_bytes())
            folder=root/'build/p0/runs/synthetic/P0A';folder.mkdir(parents=True)
            folder.joinpath('process.log').write_text(
                'P0_VIEW_SEQUENCE begin=1 nano=100\n'
                'P0_VIEW_RESULT sequence=1 status=FAILED reason=ILLEGAL_NODE nano=200\n'
                'P0_VIEW_SEQUENCE begin=2 nano=300\n'
                'P0_VIEW_SEQUENCE completed=2 nano=400\n'
                'P0_VIEW_RESULT sequence=2 status=COMPLETE reason=OK nano=400\n'
                'P0_VIEW_SEQUENCE begin=3 nano=500\n'
                'P0_VIEW_RESULT sequence=3 status=FAILED reason=NO_DERIVATIVE_REQUEST nano=600\n'
                'P0_VIEW_SEQUENCE begin=4 nano=700\n',encoding='utf-8')
            subprocess.run([sys.executable,str(script),'--label','synthetic'],check=True,stdout=subprocess.DEVNULL)
            result=json.loads(folder.parent.joinpath('view-metrics.json').read_text())
            self.assertEqual(result['complete_sequences'],1)
            self.assertEqual(result['failed_sequences'],2)
            self.assertEqual(result['unresolved_sequences'],1)
            self.assertEqual(result['failure_reasons'],{'ILLEGAL_NODE':1,'NO_DERIVATIVE_REQUEST':1})
            self.assertFalse(result['sequences'][0]['complete'])
            self.assertTrue(result['sequences'][1]['complete'])

if __name__=='__main__':unittest.main()
