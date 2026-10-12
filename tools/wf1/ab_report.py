"""Tables for the report from the retained sessions of the main batch: nothing is re-run, no run is left out.

usage: ab_report.py <output.json>    (reads build/wf1c/main-status.json for the label list)
"""
import json
import pathlib
import statistics
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/wf1'))
import summarize  # noqa: E402

JARS = {'base': 'baseline (origin/work 743ae6f)', 'ctrl': 'art control (9ab1aef)', 'cand': 'candidate (WF-1c)'}


def main():
    out = pathlib.Path(sys.argv[1])
    status = json.loads((ROOT / 'build/wf1c/main-status.json').read_text(encoding='utf-8'))
    status = status + json.loads((ROOT / 'build/wf1c/render-status.json').read_text(encoding='utf-8')) if (ROOT / 'build/wf1c/render-status.json').exists() else status
    labels = [row['label'] for row in status if row['label'].startswith('ab-')]
    metrics = {label: summarize.metrics(label) for label in labels}
    report = {'runs': metrics, 'perf': {}, 'render': {}, 'sessions': {row['label']: {'returncode': row['returncode'], 'seconds': row['seconds']} for row in status}}
    for jar in JARS:
        for rtt in ('0', '100'):
            runs = [metrics[f'ab-{jar}-{rtt}-{r}'] for r in (1, 2, 3) if f'ab-{jar}-{rtt}-{r}' in metrics]
            if not runs:
                continue
            means = [r['perf_mean_ms'] for r in runs]
            maxima = [r['perf_max_ms'] for r in runs]
            report['perf'][f'{jar}-{rtt}'] = {
                'runs': [r['label'] for r in runs], 'samples': [r['perf_samples'] for r in runs],
                'mean_ms': means, 'mean_median': statistics.median(means), 'mean_range': [min(means), max(means)],
                'max_ms': maxima, 'max_median': statistics.median(maxima), 'max_range': [min(maxima), max(maxima)],
                'combo': [{role: [c['combo_completed'], c['combo_attempted']] for role, c in r['clients'].items() if c['combo_attempted']} for r in runs],
                's08': [{role: c['s08'] for role, c in r['clients'].items()} for r in runs],
                'packet_yaw_max': [{role: c['packet_yaw_max'] for role, c in r['clients'].items()} for r in runs],
                'packet_pitch_max': [{role: c['packet_pitch_max'] for role, c in r['clients'].items()} for r in runs],
                'rejections': [r['rejections'] for r in runs], 'corrections': [r['movement_corrections'] for r in runs],
                'server_exit': [r['server_exit'] for r in runs], 'client_class_loads': [r['client_class_loads'] for r in runs],
            }
    # Render: per jar (0 ms sessions only carry the three-jar comparison), pooled over both clients, per key.
    for jar in JARS:
        pooled = {}
        for r in (1, 2, 3):
            label = f'ab-{jar}-0-{r}'
            if label not in metrics:
                continue
            for role, client in metrics[label]['clients'].items():
                for key, row in client.get('render', {}).items():
                    pooled.setdefault(key, []).append({'run': label, 'client': role, **row})
        report['render'][jar] = {}
        for key, rows in sorted(pooled.items()):
            total = sum(x['n'] for x in rows)
            report['render'][jar][key] = {
                'n': total,
                'mean_us_weighted': sum(x['meanUs'] * x['n'] for x in rows) / total,
                'p95_us_median_of_sessions': statistics.median(x['p95Us'] for x in rows),
                'max_us_range': [min(x['maxUs'] for x in rows), max(x['maxUs'] for x in rows)],
                'sessions': len(rows),
            }
    # Supplementary interleaved ctrl/cand pairs (runs 4-6, 0 ms): per-session pooled-client means for the render keys.
    report['render_sessions'] = {}
    for jar in ('ctrl', 'cand'):
        per = {}
        for r in range(1, 7):
            label = f'ab-{jar}-0-{r}'
            if label not in metrics:
                continue
            for role, client in metrics[label]['clients'].items():
                for key, row in client.get('render', {}).items():
                    per.setdefault(key, {}).setdefault(label, []).append((row['meanUs'], row['n']))
        report['render_sessions'][jar] = {
            key: {label: sum(m * n for m, n in rows) / sum(n for _, n in rows) for label, rows in sessions.items()}
            for key, sessions in per.items()}
    extra = {}
    for jar in ('ctrl', 'cand'):
        runs = [metrics[f'ab-{jar}-0-{r}'] for r in range(1, 7) if f'ab-{jar}-0-{r}' in metrics]
        extra[jar] = {'labels': [r['label'] for r in runs], 'mean_ms': [r['perf_mean_ms'] for r in runs], 'max_ms': [r['perf_max_ms'] for r in runs],
                      'combo': [{role: [c['combo_completed'], c['combo_attempted']] for role, c in r['clients'].items() if c['combo_attempted']} for r in runs],
                      's08': [{role: c['s08'] for role, c in r['clients'].items()} for r in runs]}
    report['ctrl_cand_six'] = extra
    out.write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name, row in report['perf'].items():
        print(name, 'mean', row['mean_ms'], 'max', row['max_ms'], 'combo', row['combo'], 's08', row['s08'])


if __name__ == '__main__':
    main()
