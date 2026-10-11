"""Aggregate actual GLU measurements; retain no frame arrays in the deliverable."""
import argparse
import json
import math
import pathlib
import re
import statistics

p = argparse.ArgumentParser()
p.add_argument('--label', required=True)
a = p.parse_args()
run = pathlib.Path(__file__).resolve().parents[2] / 'build/p0/runs' / a.label
rows, cameras, began, predictors = {}, {}, {}, {}
phase = ''
for line in (run / 'P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    d = dict(re.findall(r'(\w+)=([^ ]+)', line))
    if 'P0_COMPOSITION_PHASE ' in line and 'receivedNano' in d:
        phase = d['name']
        began[phase] = int(d['receivedNano'])
    if 'CW_SOULS_FRAME ' in line:
        cameras.setdefault(phase, []).append(d)
    if 'CW_THIRD_FRAME ' in line:
        predictors.setdefault(phase, []).append(d)
    if 'P0_SOULS_PROJECTION ' in line:
        rows.setdefault(d['phase'], []).append(d)


def quantile(values, q):
    values = sorted(v for v in values if math.isfinite(v))
    return values[min(len(values) - 1, math.ceil(q * len(values)) - 1)] if values else None


def measures(rr):
    result = {'frames': len(rr)}
    for key in ['playerX', 'playerY', 'targetX', 'targetY', 'headX', 'headY',
                'bearingError', 'actualRange', 'feetDelta', 'targetHeight', 'distance', 'pitch', 'fraction', 'fov']:
        values = [float(d[key]) for d in rr if key in d and math.isfinite(float(d[key]))]
        result[key] = {'mean': statistics.mean(values), 'min': min(values), 'max': max(values)} if values else None
    for key in ['headCovered', 'torsoCovered', 'hidden', 'insideBlock', 'active']:
        result[key + '_fraction'] = sum(d.get(key) == 'true' for d in rr) / len(rr)
    deviations = [abs(float(d['targetX']) - .5) for d in rr]
    result['target_x_abs_deviation'] = {'median': quantile(deviations, .5), 'p95': quantile(deviations, .95)}
    bearing = [abs(float(d['bearingError'])) for d in rr if 'bearingError' in d]
    result['bearing_abs_error_degrees'] = {'median': quantile(bearing, .5), 'p95': quantile(bearing, .95)}
    result['max_fraction_change'] = max(abs(float(d['collisionDelta'])) for d in rr)
    result['max_fraction_shrink'] = max(max(0, -float(d['collisionDelta'])) for d in rr)
    return result


results = {}
for phase, rr in rows.items():
    settled = [d for d in rr if int(d['nano']) - began.get(phase, int(d['nano'])) > 2_000_000_000]
    results[phase] = {'all': measures(rr), 'after_2s': measures(settled) if settled else None}
    cr = cameras.get(phase, [])
    def solid(x, y, z):
        if phase == 'SOULS_WALL': return -7 <= x < 8 and 64 <= y < 69 and -3 <= z < -2
        if phase == 'SOULS_CEILING': return -7 <= x < 8 and 66 <= y < 67 and -9 <= z < -1
        return False
    def collision(d):
        x, y, z = [float(d[k]) for k in ['cameraX', 'cameraY', 'cameraZ']]
        return any(solid(x + dx, y + dy, z + dz) for dx in [-.12, 0, .12]
                   for dy in [-.12, 0, .12] for dz in [-.12, 0, .12])
    results[phase]['collision'] = {'cube_samples': len(cr), 'cube_intersections': sum(collision(d) for d in cr)}
    pr = predictors.get(phase, [])
    gaps = [(int(pr[i]['nano'])-int(pr[i-1]['nano']))/1e9 for i in range(1,len(pr))]
    longest = 0
    zero_start = None
    for item in pr:
        nano = int(item['nano'])
        if float(item['lead']) == 0:
            if zero_start is None: zero_start = nano
            longest = max(longest,(nano-zero_start)/1e9)
        else: zero_start = None
    results[phase]['prediction_audit'] = {'frames':len(pr),
        'real_intervals_above_100ms':sum(g>.1 for g in gaps),
        'max_real_interval_millis':max(gaps,default=0)*1000,
        'zero_lead_frames':sum(float(d['lead'])==0 for d in pr),
        'longest_zero_lead_seconds':longest,
        'note':'Derived from consecutive CW_THIRD_FRAME wall timestamps and lead; zero lead does not identify which guard branch fired.'}

out = {'label': a.label, 'measurement': 'GLU projection of rendered bounding boxes; conservative rectangle coverage',
       'settling_seconds': 2, 'motion_acceptance_uses': 'all frames, not only settled frames', 'phases': results}
(run / 'camera-bounds-summary.json').write_text(json.dumps(out, indent=2) + '\n', encoding='utf-8')
print(a.label, len(results), 'phases')
