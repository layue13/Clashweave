"""Summarize real GLU projections; no reconstruction substitutes for measurements."""
import argparse
import json
import pathlib
import re
import statistics

parser = argparse.ArgumentParser()
parser.add_argument('--label', required=True)
args = parser.parse_args()
run = pathlib.Path(__file__).resolve().parents[2] / 'build/p0/runs' / args.label
phases, all_phases, began, cameras = {}, {}, {}, {}
phase = ""
for line in (run / 'P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines():
    row = dict(re.findall(r'(\w+)=([^ ]+)', line))
    if 'P0_COMPOSITION_PHASE ' in line and 'receivedNano' in row:
        began[row['name']] = int(row['receivedNano'])
        phase = row['name']
    if 'CW_SOULS_FRAME ' in line:
        cameras.setdefault(phase, []).append(row)
    if 'P0_SOULS_PROJECTION ' in line:
        age = int(row['nano']) - began.get(row['phase'], int(row['nano']))
        all_phases.setdefault(row['phase'], []).append(row)
        if age > 2_000_000_000:
            phases.setdefault(row['phase'], []).append(row)
result = {}
for phase, rows in phases.items():
    all_rows = all_phases[phase]
    item = {'samples_after_2s': len(rows), 'all_samples': len(all_rows)}
    for key in ['playerX', 'playerY', 'targetX', 'targetY', 'headY', 'targetHeight', 'distance', 'pitch', 'fraction']:
        values = [float(row[key]) for row in rows]
        item[key] = {'mean': statistics.mean(values), 'min': min(values), 'max': max(values)}
    for key in ['headCovered', 'torsoCovered', 'insideBlock', 'hidden']:
        item[key + '_fraction'] = sum(row.get(key) == 'true' for row in rows) / len(rows)
    for key in ['headCovered', 'torsoCovered']:
        item[key + '_rendered_fraction'] = sum(row[key] == 'true' and row.get('hidden') != 'true' for row in rows) / len(rows)
    camera_rows = cameras.get(phase, [])
    def solid(x, y, z):
        if phase == 'SOULS_WALL':
            return -7 <= x < 8 and 64 <= y < 69 and -3 <= z < -2
        if phase == 'SOULS_CEILING':
            return -7 <= x < 8 and 66 <= y < 67 and -9 <= z < -1
        return False
    def cube_intersects(row):
        # Actual camera coordinates against the fixture's explicitly placed block volumes.
        x, y, z = [float(row[key]) for key in ['cameraX', 'cameraY', 'cameraZ']]
        return any(solid(x + dx, y + dy, z + dz) for dx in [-.12, 0, .12] for dy in [-.12, 0, .12] for dz in [-.12, 0, .12])
    item['collision_fraction_speed_cap_violations'] = sum(abs(float(row['change'])) > 4 * float(row['dt']) + 1e-6 for row in camera_rows if float(row['dt']) > 0)
    item['camera_cube_samples'] = len(camera_rows)
    item['camera_cube_block_intersections'] = sum(cube_intersects(row) for row in camera_rows)
    item['insideBlock_all_frames'] = sum(row['insideBlock'] == 'true' for row in all_rows)
    item['max_collision_fraction_step_after_2s'] = max(abs(float(row['collisionDelta'])) for row in rows)
    item['max_collision_fraction_step_all_frames'] = max(abs(float(row['collisionDelta'])) for row in all_rows)
    item['player_screen_y_error'] = abs(item['playerY']['mean'] - .65)
    item['target_screen_y_error'] = abs(item['targetY']['mean'] - .38)
    result[phase] = item
(run / 'souls-metrics.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
