"""Per-session numbers for the WF-1c report, read from retained process logs (nothing is re-run or filtered)."""
import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/p1'))
import summarize as p1  # noqa: E402


def render_stats(folder, role):
    path = folder / role / 'process.log'
    rows = {}
    for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
        if 'WF1C_RENDER ' in line:
            f = p1.fields(line)
            rows[f['key']] = {k: float(f[k]) if k != 'key' and k != 'client' else f[k] for k in f if k not in ('client',)}
    return rows


def metrics(label):
    folder = ROOT / 'build/p0/runs' / label
    base = p1.summarize(label)
    summary = base['dedicated']
    perf = base['performance_last'] or {}
    row = {
        'label': label,
        'server_exit': summary['server_exit'],
        'complete': summary['complete'],
        'client_class_loads': summary['client_class_loads'],
        'perf_samples': int(float(perf.get('samples', 0))) if perf else None,
        'perf_mean_ms': round(float(perf['meanNanos']) / 1e6, 6) if perf else None,
        'perf_max_ms': round(float(perf['maxNanos']) / 1e6, 4) if perf else None,
        'rejections': base['rejections'],
        'movement_corrections': base['movement_corrections'],
        'launcher_cases': len(base['launcher_cases']),
        'launch_cases_accepted': sum(any(e.get('accepted') == 'true' for e in c['effects']) for c in base['launcher_cases']),
        'clients': {},
    }
    for role, data in base['clients'].items():
        complete = data['combo_complete'][-1] if data['combo_complete'] else {}
        row['clients'][role] = {
            'combo_attempted': int(complete.get('attempted', 0)) if complete else None,
            'combo_completed': int(complete.get('completed', 0)) if complete else None,
            's08': data['s08'], 'packet_yaw_max': data['packet_yaw_delta_max'],
            'packet_pitch_max': data['packet_pitch_delta_max'],
        }
        stats = render_stats(folder, role) if (folder / role / 'process.log').exists() else {}
        if stats:
            row['clients'][role]['render'] = stats
    return row


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('labels', nargs='+')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    out = [metrics(label) for label in args.labels]
    pathlib.Path(args.output).write_text(json.dumps(out, indent=2), encoding='utf-8')
    for row in out:
        print(row['label'], row['perf_mean_ms'], row['perf_max_ms'], {r: (c['combo_completed'], c['combo_attempted'], c['s08']) for r, c in row['clients'].items()}, 'launch', row['launch_cases_accepted'], '/', row['launcher_cases'])


if __name__ == '__main__':
    main()
