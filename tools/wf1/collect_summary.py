"""Collect the retained evidence behind WF-1c-REPORT.md into one JSON document (no raw logs or per-frame data)."""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNS = ROOT / 'build/p0/runs'
sys.path.insert(0, str(ROOT / 'tools/wf1'))
import summarize  # noqa: E402


def lines(label, role='server'):
    return (RUNS / label / role / 'process.log').read_text(encoding='utf-8', errors='replace').splitlines()


def fields(line):
    return dict(re.findall(r'(\w+)=([^\s\]]+)', line))


def checks(label):
    rows = []
    for line in lines(label):
        if 'WF1C_CHECK ' in line:
            text = line.split('WF1C_CHECK ', 1)[1]
            rows.append({'step': re.search(r'step=(\S+)', text).group(1), 'ok': 'ok=true' in text,
                         'detail': re.search(r'detail=(.*?) before=\[', text).group(1)})
    done = next((x.split('WF1C_COMPLETE ', 1)[1] for x in lines(label) if 'WF1C_COMPLETE ' in x), None)
    return {'checks': rows, 'complete': done}


def p1_row(label):
    metrics = summarize.metrics(label)
    text = lines(label)
    return {'label': label, 'launch_cases_accepted': metrics['launch_cases_accepted'], 'cases': metrics['launcher_cases'],
            'condition_changed': sum('result=CONDITION_CHANGED' in x for x in text), 'rejections': metrics['rejections'],
            'movement_corrections': metrics['movement_corrections'],
            's08': {r: c['s08'] for r, c in metrics['clients'].items()},
            'packet_yaw_max': {r: c['packet_yaw_max'] for r, c in metrics['clients'].items()},
            'packet_pitch_max': {r: c['packet_pitch_max'] for r, c in metrics['clients'].items()},
            'perf_mean_ms': metrics['perf_mean_ms'], 'perf_max_ms': metrics['perf_max_ms'], 'server_exit': metrics['server_exit'],
            'client_class_loads': metrics['client_class_loads']}


def main():
    out = pathlib.Path(sys.argv[1])
    ab = json.loads((ROOT / 'build/wf1c/ab-report.json').read_text(encoding='utf-8'))
    document = {
        'ab': {'perf': ab['perf'], 'render': ab['render']},
        'p1_float_regression': [p1_row(l) for l in ['p1f-base-0', 'p1f-cand-0', 'p1f-base-100', 'p1f-cand-100']
                                + [f'p1f-{v}-100-r{r}' for r in (2, 3, 4) for v in ('base', 'cand')]],
        'lifecycle': {l: checks(l) for l in ('life-1', 'life-2', 'life-3', 'life-4')},
        'floating': {l: checks(l) for l in ('float-death-1', 'float-dimension-1', 'float-disconnect-1',
                                            'float-death-2', 'float-dimension-2', 'float-disconnect-2')},
        'weapon2': {l: checks(l) for l in ('w2-1', 'w2-2', 'w2-3', 'w2-4')},
        'handshake': {l: {'server': [re.sub(r'^\[[^\]]*\] \[[^\]]*\] \[[^\]]*\]: ', '', x) for x in lines(l)
                          if 'Clashweave: ' in x and ('presentation content only' in x or 'refusing' in x or 'matching' in x)]}
                      for l in ('hs-presonly', 'hs-gamediff')},
        'bad_packs': json.loads((ROOT / 'build/wf1c/bad-b1-reparsed.json').read_text(encoding='utf-8')),
        'batches': {n: json.loads((ROOT / f'build/wf1c/{n}-status.json').read_text(encoding='utf-8')) for n in ('main', 'diag', 'render', 'func-1')},
    }
    out.write_text(json.dumps(document, indent=1, ensure_ascii=False), encoding='utf-8')
    print(out, out.stat().st_size)


if __name__ == '__main__':
    main()
