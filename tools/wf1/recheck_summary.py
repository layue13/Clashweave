"""Summarize every recheck session, including failures; never select or rerun sessions."""
import argparse
import hashlib
import json
import pathlib
import re
import statistics

import summarize
from collect_summary import checks, lines

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('batches', nargs='+')
    args = parser.parse_args()
    batches = {b: json.loads((ROOT / f'build/wf1c/{b}-status.json').read_text(encoding='utf-8'))
               for b in args.batches}
    document = {'batches': batches, 'runs': [], 'perf_groups': {}, 'jars': {}, 'checks': {}, 'key_evidence': {}}
    for entries in batches.values():
        for entry in entries:
            label = entry['label']
            metrics = summarize.metrics(label)
            for role, client in metrics['clients'].items():
                client['combo_failed_sequences'] = [summarize.p1.fields(line) for line in lines(label, role)
                    if 'P0_VIEW_RESULT ' in line and 'status=COMPLETE' not in line]
            loading = {}
            for role in ('P0A', 'P0B'):
                client_lines = lines(label, role)
                errors = [line for line in client_lines if re.search(
                    r'ModelFormatException|OBJ.*(?:error|fail)|(?:Unable|Failed|Couldn.t).*load.*(?:texture|model)|FileNotFoundException.*(?:\.obj|\.png)',
                    line, re.IGNORECASE)]
                loading[role] = {'models_loaded': sum('CW_RENDER models loaded' in line for line in client_lines),
                                 'model_texture_error_count': len(errors), 'error_excerpts': errors[:5]}
            metrics['resource_loading'] = loading
            document['runs'].append(metrics)
            if entry['jar'] not in document['jars']:
                jar = ROOT / entry['jar']
                document['jars'][entry['jar']] = hashlib.sha256(jar.read_bytes()).hexdigest()
            server = lines(label)
            if any('WF1C_CHECK ' in line for line in server):
                document['checks'][label] = checks(label)
            evidence = {}
            for role in ('server', 'P0A', 'P0B'):
                selected = []
                for line in lines(label, role):
                    if any(token in line for token in (
                        'WF1C_FALLBACK ', 'WF1C_BAD_SYNC ', 'P0_VIEW COMPLETE ',
                        'negotiated rules differ', 'invalid server content sync',
                        'server content sync rejected', 'joined with negotiated rules',
                        'presentation content only', 'refusing', 'Clashweave content:',
                        'ART_CAPTURE name=sheathed-first', 'WF1C_COMPLETE ',
                        'WF1C_CHECK step=teleport-mid-action', 'CW_EFFECT LAUNCH')):
                        # Key excerpts only, not a copy of a process log.
                        selected.append(line)
                evidence[role] = selected[:20]
            document['key_evidence'][label] = evidence
    for rtt in (0, 100):
        for variant in ('base', 'review'):
            rows = [r for r in document['runs'] if r['label'].startswith(f'rev-ab-{variant}-{rtt}-')]
            if not rows:
                continue
            group = {'labels': [r['label'] for r in rows]}
            for field in ('perf_mean_ms', 'perf_max_ms'):
                values = [r[field] for r in rows]
                group[field] = {'values': values, 'mean': statistics.mean(values),
                                'median': statistics.median(values), 'min': min(values), 'max': max(values)}
            document['perf_groups'][f'{variant}-{rtt}'] = group
    pathlib.Path(args.output).write_text(json.dumps(document, indent=1, ensure_ascii=False), encoding='utf-8')
    print(args.output, 'sessions', len(document['runs']))


if __name__ == '__main__':
    main()
