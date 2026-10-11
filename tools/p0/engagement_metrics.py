"""Match actual server revision emission to its client application on the same host clock."""
import argparse
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--label', required=True)
args = parser.parse_args()
folder = ROOT / 'build/p0/runs' / args.label
sent = {}
for line in (folder / 'server/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    if 'CW ENGAGEMENT player=' in line:
        e = dict(re.findall(r'(\w+)=([^ ]+)', line))
        sent[(e['player'], e['revision'])] = e
rows = []
for name in ['P0A', 'P0B']:
    for line in (folder / name / 'process.log').read_text(encoding='utf-8', errors='replace').splitlines():
        if 'CW_ENGAGEMENT player=' not in line:
            continue
        e = dict(re.findall(r'(\w+)=([^ ]+)', line))
        source = sent.get((e['player'], e['revision']))
        if source:
            rows.append(dict(player=name, revision=e['revision'], serverTick=source['tick'],
                             engaged=e['engaged'], delay_ms=(int(e['receive']) - int(source['sent'])) / 1e6))
summary = dict(samples=len(rows), maximum_ms=max((r['delay_ms'] for r in rows), default=0), rows=rows)
(folder / 'engagement-metrics.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary, indent=2))
