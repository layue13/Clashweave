"""Join production request/ACK traces; no fabricated results or direct acquisition calls."""
import argparse
import collections
import json
import pathlib
import re
import statistics

p = argparse.ArgumentParser()
p.add_argument('--label', required=True)
p.add_argument('--policy', choices=['baseline', 'split'], required=True)
a = p.parse_args()
run = pathlib.Path(__file__).resolve().parents[2] / 'build/p0/runs' / a.label
def fields(line): return dict(re.findall(r'(\w+)=([^ ]+)', line))
geometry, expected, inputs, acks = {}, {}, {}, {}
selected = -1
for line in (run / 'server/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    d = fields(line)
    if 'P0_LOW_LOCK_PHASE ' in line: expected[d['name']] = int(d['target'])
    if 'P0_LOW_LOCK_GEOMETRY ' in line: geometry[int(d['tick'])] = d
    if 'CW INPUT player=P0A ' in line: inputs[int(d['seq'])] = d
    if 'CW LOCK_TARGET player=P0A ' in line: selected = int(d['target'])
    if 'CW ACK player=P0A ' in line: acks[int(d['seq'])] = (d['result'], selected)
groups = {}
for line in (run / 'P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    if 'P0_LOW_LOCK_REQUEST ' not in line: continue
    d = fields(line); seq = int(d['seq']); phase = d['phase']; request = inputs.get(seq, {})
    tick = int(request.get('consumedTick', -1)); g = geometry.get(tick - 1, geometry.get(tick, {}))
    result, identity = acks.get(seq, ('NO_ACK', -1))
    success = result == 'LOCK' and identity == expected.get(phase)
    reason = 'ACCEPT' if success else result
    if request.get('gate') != 'OK': reason = 'INPUT_GATE:' + request.get('gate', 'MISSING')
    elif result == 'LOCK' and not success: reason = 'OTHER_TARGET'
    elif result == 'REJECT:LOCK' and g:
        if g['visible'] != 'true': reason = 'VISIBILITY'
        elif a.policy == 'baseline' and float(g['threeD']) > 30 + 1e-5: reason = 'THREE_D_CONE'
        elif float(g['horizontal']) > 30 + 1e-5: reason = 'HORIZONTAL_CONE'
        elif abs(float(g['centerDy'])) > 8 + 1e-5: reason = 'VERTICAL_DISTANCE'
        elif float(g['verticalCenter']) > 60 + 1e-5: reason = 'VERTICAL_CONE'
    group = groups.setdefault(phase, {'requests': 0, 'accepted': 0, 'reasons': collections.Counter(), 'geometry': []})
    group['requests'] += 1; group['accepted'] += success; group['reasons'][reason] += 1
    if g and g['phase'] == phase: group['geometry'].append(g)
for phase, group in groups.items():
    gg = group.pop('geometry'); group['geometry_samples'] = len(gg)
    group['rate'] = group['accepted'] / group['requests']; group['reasons'] = dict(group['reasons'])
    group['actual'] = {key: {'mean': statistics.mean(float(g[key]) for g in gg),
        'min': min(float(g[key]) for g in gg), 'max': max(float(g[key]) for g in gg)}
        for key in ['range', 'height', 'playerEye', 'targetEye', 'horizontal', 'verticalEye', 'threeD', 'verticalCenter', 'centerDy', 'yaw', 'pitch']} if gg else {}
summary = json.loads((run / 'summary.json').read_text(encoding='utf-8'))
out = {'label': a.label, 'policy': a.policy, 'server_exit': summary['server_exit'],
       'complete': summary['complete'], 'client_class_loads': summary['client_class_loads'],
       'runtime': json.loads((run / 'runtime-snapshot.json').read_text(encoding='utf-8')), 'reason_provenance': 'Input gate and ACK are recorded; cone/visibility cause inferred from matched actual geometry and reviewed eligibility code, isolated single candidate', 'groups': groups}
(run / 'low-lock-summary.json').write_text(json.dumps(out, indent=2) + '\n', encoding='utf-8')
print(a.label, [(k, v['accepted'], v['requests'], v['reasons']) for k, v in groups.items()])
