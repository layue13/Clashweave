"""Camera-only evidence: aggregates, bounded excerpts and native screenshots, never full logs."""
import argparse
import collections
import json
import pathlib
import re
import shutil

p = argparse.ArgumentParser()
p.add_argument('--label', required=True)
p.add_argument('--screenshots', action='store_true')
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[2]
run = root / 'build/p0/runs' / a.label
out = root / 'docs/p0/evidence/camera-bounds' / a.label
out.mkdir(parents=True, exist_ok=True)
summary = json.loads((run / 'summary.json').read_text(encoding='utf-8'))
summary.pop('performance', None)
summary['run_capture_count'] = len(summary.pop('captures', []))
summary['archived_captures'] = []
summary['runtime'] = json.loads((run / 'runtime-snapshot.json').read_text(encoding='utf-8'))
summary['snapshots_note'] = 'Frozen per run; original logs remain in ignored build/p0/runs'
for name in ['camera-bounds-summary.json']:
    if (run / name).exists(): shutil.copyfile(run / name, out / name)
if (run / 'view-metrics.json').exists():
    v = json.loads((run / 'view-metrics.json').read_text(encoding='utf-8'))
    summary['view'] = {k: value for k, value in v.items() if k not in ['sequences', 'all_position_packets']}
    summary['view']['packet_rotation_changes_in_sequences'] = sum(
        abs(float(p.get('yawDelta', 0))) > 1e-6 or abs(float(p.get('pitchDelta', 0))) > 1e-6
        for p in v['all_position_packets'] if any(s['complete'] and s['began'] <= int(p['nano']) <= s['ended'] for s in v['sequences']))
if (run / 'metrics.json').exists():
    v = json.loads((run / 'metrics.json').read_text(encoding='utf-8'))
    summary['combat_regression'] = {k: v[k] for k in ['action_execution', 'guard_violations',
        'deadline_violations', 'prediction', 'aim']}
    summary['combat_regression']['input_gate_reject_counts'] = dict(collections.Counter(r['gate'] for r in v['input_gate_rejects']))
    summary['combat_regression']['action_gate_reject_counts'] = dict(collections.Counter(r['gate'] for r in v['input_gate_rejects'] if int(r['kind']) >= 0))

for role in ['server', 'P0A', 'P0B']:
    if not (run / role / 'process.log').exists(): continue
    lines = (run / role / 'process.log').read_text(encoding='utf-8', errors='replace').splitlines()
    selected = collections.defaultdict(list)
    phase = ''
    sweep = {}
    for line in lines:
        d = dict(re.findall(r'(\w+)=([^ ]+)', line))
        if 'P0_COMPOSITION_PHASE name=' in line:
            phase = d['name']
        if role == 'server' and phase.startswith('AUDIT_'):
            entry = sweep.setdefault(phase, {'contacts': 0, 'successful_contacts': 0})
            if 'CW COMMIT ' in line:
                entry['contacts'] += 1
                entry['successful_contacts'] += d.get('hit') == 'true'
        patterns = ['P0_CAMERA_ENTITY', 'P0_CAMERA_POSITION', 'P0_COMPOSITION_PHASE', 'P0_CAMERA_SWEEP_INPUT',
                    'P0_VIEW_SEQUENCE', 'P0_NETWORK COMPLETE', 'P0_PERF', 'CW VIEW', 'CW_VIEW packet=',
                    'CW COMMIT ', 'CW_SOULS_FRAME', 'P0_SOULS_PROJECTION', 'CW_THIRD_FRAME', 'CW_LOCK_FOLLOW']
        for pattern in patterns:
            if pattern in line:
                key = pattern + ':' + (phase if pattern != 'P0_PERF' else '')
                if len(selected[key]) < 2: selected[key].append(line)
                elif pattern in ['CW_SOULS_FRAME', 'P0_SOULS_PROJECTION', 'CW_THIRD_FRAME', 'CW_LOCK_FOLLOW', 'P0_VIEW_SEQUENCE']:
                    selected[key][-1] = line
                break
    (out / (role + '-excerpt.log')).write_text(
        '# Bounded key excerpts; source line count: ' + str(len(lines)) + '\n' +
        '\n'.join(l for group in selected.values() for l in group) + '\n', encoding='utf-8')
    if sweep: summary['sweep_contacts'] = sweep
    if role == 'P0A':
        weak = [dict(re.findall(r'(\w+)=([^ ]+)', l)) for l in lines if 'CW_LOCK_FOLLOW mode=WEAK ' in l]
        if weak:
            summary['first_person'] = {'samples': len(weak), 'first_yaw': float(weak[0]['before']),
                'last_yaw': float(weak[-1]['after']), 'max_tick_yaw_change': max(abs(float(r['after']) - float(r['before'])) for r in weak),
                'reasons': dict(collections.Counter(r['reason'] for r in weak))}
if a.screenshots:
    for role in ['P0A', 'P0B']:
        for path in (run / role / 'screenshots').glob('*.png'):
            if path.name in ['p0-souls-.png', 'p0-souls-wait.png']: continue
            folder = out / role
            folder.mkdir(exist_ok=True)
            shutil.copyfile(path, folder / path.name)
            summary['archived_captures'].append(str((folder / path.name).relative_to(out)).replace('\\','/'))
(out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print('Archived bounded camera evidence:', a.label)
