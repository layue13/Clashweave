"""Aggregate every measured frame into time bins; never archive individual-frame arrays."""
import argparse
import collections
import json
import math
import pathlib
import re
import statistics

p = argparse.ArgumentParser()
p.add_argument('--label', required=True)
p.add_argument('--original-pressure', action='store_true')
a = p.parse_args()
run = pathlib.Path(__file__).resolve().parents[2] / 'build/p0/runs' / a.label
def fields(line): return dict(re.findall(r'(\w+)=([^ ]+)', line))
def wrap(value): return (value + 180) % 360 - 180
def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered)-1, math.ceil(len(ordered)*fraction)-1)]
groups, events, pauses = collections.defaultdict(list), {}, {}
latest = None
for line in (run / 'P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    d = fields(line)
    if a.original_pressure:
        if 'CW_THIRD_FRAME ' in line: latest = d
        if 'P0_SOULS_PROJECTION ' in line and d['phase'] == 'MOTION_90' and latest:
            groups['MOTION_90'].append({'nano': int(d['nano']), 'x': float(d['targetX']) - .5,
                'feedSpeed': float(latest['lead']) / .25, 'error': float(d['bearingError']),
                'filteredError': wrap(float(latest['goalYaw']) - float(latest['after']))})
    else:
        if 'P0_PAUSE_EVENT phase=' in line: events[d['phase']] = int(d['nano'])
        if 'P0_PAUSE_BEGIN ' in line: pauses.setdefault(d['phase'], {})['begin'] = int(d['nano'])
        if 'P0_PAUSE_END ' in line: pauses.setdefault(d['phase'], {}).update(end=int(d['nano']), durationMillis=int(d['durationNanos']) / 1e6)
        if 'P0_PAUSE_FRAME ' in line:
            groups[d['phase']].append({k: int(d[k]) if k == 'nano' else float(d[k])
                for k in ['nano', 'x', 'feedSpeed', 'error', 'filteredError']})
if a.original_pressure:
    rr = groups['MOTION_90']; start = rr[0]['nano']
    pairs = [(i, (rr[i]['nano'] - rr[i-1]['nano']) / 1e6) for i in range(1, len(rr))
             if 4 <= (rr[i]['nano'] - start) / 1e9 <= 6]
    index, gap = max(pairs, key=lambda item: item[1])
    events['MOTION_90'] = rr[index-1]['nano']
    pauses['MOTION_90'] = {'begin': rr[index-1]['nano'], 'end': rr[index]['nano'],
        'durationMillis': gap, 'note': 'Largest observed projection-frame gap in phase seconds 4-6; screenshot occurs in this interval, not an instrumented sleep'}
out = {'label': a.label, 'bin_millis': 100, 'individual_frames_archived': False,
       'method': 'All frames in [-.5,+2.5]s; signed actual and filtered bearing minus damped yaw; zero crossing threshold .5 degrees', 'phases': {}}
for phase, rr in groups.items():
    origin = pauses.get(phase, {}).get('begin', events.get(phase, 0))
    if not origin: continue
    selected = [r for r in rr if -.5 <= (r['nano'] - origin) / 1e9 < 2.5]
    bins = collections.defaultdict(list)
    for row in selected: bins[math.floor((row['nano'] - origin) / 100_000_000)].append(row)
    curve = []
    for index, bb in sorted(bins.items()):
        entry = {'startMillis': index * 100, 'meanTimeMillis': statistics.mean((r['nano']-origin)/1e6 for r in bb), 'count': len(bb)}
        for key in ['x', 'feedSpeed', 'error', 'filteredError']:
            vv = [r[key] for r in bb]; entry[key] = {'mean': statistics.mean(vv), 'min': min(vv), 'max': max(vv)}
        curve.append(entry)
    end = pauses.get(phase, {}).get('end', events.get(phase, origin))
    post = [r for r in selected if r['nano'] >= end]
    post_all = [r for r in rr if r['nano'] >= end]
    signs = [1 if r['error'] > .5 else -1 if r['error'] < -.5 else 0 for r in post]
    nonzero = [s for s in signs if s]
    reversals = sum(s != nonzero[i-1] for i,s in enumerate(nonzero) if i)
    out['phases'][phase] = {'frames': len(selected), 'pause': pauses.get(phase, {}), 'eventNano': events.get(phase),
        'all_phase_frames': len(rr),
        'all_phase_x_abs_median': percentile([abs(r['x']) for r in rr], .5),
        'all_phase_x_abs_p95': percentile([abs(r['x']) for r in rr], .95),
        'post_reverse_lead_max_degrees': max(0, -min(r['error'] for r in post_all)),
        'post_all_frames': len(post_all),
        'post_all_feed_speed_max': max(r['feedSpeed'] for r in post_all),
        'reverse_lead_acceptance_uses': 'Every frame after event/pause through phase end, not just the plotted window',
        'pre_error_mean': statistics.mean(r['error'] for r in selected if -.5 <= (r['nano']-origin)/1e9 < -.1),
        'late_error_mean': statistics.mean(r['error'] for r in post if (r['nano']-origin)/1e9 >= 2),
        'post_signed_bearing_min': min(r['error'] for r in post), 'post_signed_bearing_max': max(r['error'] for r in post),
        'post_zero_crossings_above_half_degree': reversals,
        'first_post_error': post[0]['error'], 'last_post_error': post[-1]['error'],
        'post_feed_speed_min': min(r['feedSpeed'] for r in post), 'post_feed_speed_max': max(r['feedSpeed'] for r in post),
        'peak_forecast_residual': max(r['filteredError'] + r['feedSpeed'] * .25 - r['error'] for r in post),
        'most_leading_bin_source': {k:v for k,v in min(post,key=lambda r:r['error']).items() if k != 'nano'},
        'post_max_abs_x': max(abs(r['x']) for r in post), 'curve_bins': curve}
summary = json.loads((run / 'summary.json').read_text(encoding='utf-8'))
out['run'] = {k: summary[k] for k in ['complete', 'server_exit', 'client_class_loads']}
out['runtime'] = json.loads((run / 'runtime-snapshot.json').read_text(encoding='utf-8'))
(run / 'pause-summary.json').write_text(json.dumps(out, indent=2) + '\n', encoding='utf-8')
print(a.label, [(k, {name:value for name,value in v.items() if name != 'curve_bins'}) for k,v in out['phases'].items()])
