"""Archive aggregate measurements, test counts and acceptance deltas, never process/frame logs."""
import argparse
import json
import pathlib
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument('--output', required=True)
p.add_argument('--prefix', default='stall-fix')
p.add_argument('--attempt-summary')
a = p.parse_args()
out = {'runs': {}, 'motion': [], 'pause': [], 'stress': [], 'tests': {}}
if a.attempt_summary:
    attempt = json.loads((root/a.attempt_summary).read_text(encoding='utf-8'))
    out['first_implementation'] = {k:attempt[k] for k in ['motion','pause','stress']}
    out['first_implementation']['note'] = 'Rejected initialization treatment suppressed prediction even for the first target; retained rather than replaced by the revised run.'
out['startup_failure'] = {'label':'stall-final-pause-0','complete':False,'server_exit':1,
    'game_samples':0,'reason':'First client EXCEPTION_ACCESS_VIOLATION in OpenAL64.dll+0x11066 before login; distinct retry labels retain the failure.'}
baseline = json.loads((root/'docs/p0/evidence/lock-acquisition-diagnosis/pause-curves-summary.json').read_text(encoding='utf-8'))['runs']
for delay in [0, 100]:
    out['runs'][f'baseline-stress-{delay}'] = baseline[f'bounds-motion-clock-{delay}']
    for kind in ['pause', 'motion', 'stress']:
        label = f'{a.prefix}-{kind}-{delay}'
        folder = root/'build/p0/runs'/label
        run = json.loads((folder/'summary.json').read_text(encoding='utf-8'))
        if not run['complete'] or run['server_exit'] != 0 or run['client_class_loads'] != 0:
            raise RuntimeError(f'Incomplete scenario: {label}')
        summary = json.loads((folder/('pause-summary.json' if kind != 'motion' else 'camera-bounds-summary.json')).read_text(encoding='utf-8'))
        summary['run'] = {k:run[k] for k in ['complete','server_exit','client_class_loads']}
        summary['runtime'] = json.loads((folder/'runtime-snapshot.json').read_text(encoding='utf-8'))
        # Plot names stay stable when a revised implementation is tested under a fresh label.
        out['runs'][f'stall-fix-{kind}-{delay}'] = summary
        if kind == 'pause':
            for phase in ['PAUSE_ONLY','SWITCH_ONLY','SWITCH_PAUSE']:
                item = summary['phases'][phase]
                out['pause'].append({'rtt':delay,'phase':phase,'reverse_lead':item['post_reverse_lead_max_degrees'],
                                     'within_3_degrees':item['post_reverse_lead_max_degrees']<=3,
                                     'peak_feed_speed':item.get('post_all_feed_speed_max', item['post_feed_speed_max'])})
        else:
            motion = summary if kind == 'motion' else json.loads((folder/'camera-bounds-summary.json').read_text(encoding='utf-8'))
            baseline_label = 'bounds-motion-isolated' if kind == 'motion' else 'bounds-motion-clock'
            previous = json.loads((root/f'docs/p0/evidence/camera-bounds/{baseline_label}-{delay}/camera-bounds-summary.json').read_text(encoding='utf-8'))
            for phase in ['MOTION_45','MOTION_90','MOTION_STRAFE']:
                item = motion['phases'][phase]['all']['target_x_abs_deviation']
                old = previous['phases'][phase]['all']['target_x_abs_deviation']['p95']
                out[kind].append({'rtt':delay,'phase':phase,**item,'baseline_p95':old,'baseline_label':f'{baseline_label}-{delay}',
                                  'p95_delta':item['p95']-old,'within_005W':item['p95']<=.05,
                                  'no_worse_than_a72ad97':item['p95']<=old})
    view = root/f'build/p0/runs/{a.prefix}-view-{delay}/view-metrics.json'
    if view.exists():
        metrics = json.loads(view.read_text(encoding='utf-8'))
        view_folder = view.parent
        status = json.loads((view_folder/'summary.json').read_text(encoding='utf-8'))
        out.setdefault('view',[]).append({'rtt':delay,
            'expected_complete_sequences':20,'complete_20_sequences':metrics['complete_sequences']==20,
            'started_sequences':len(metrics['sequences']),
            'run':{k:status[k] for k in ['complete','server_exit','client_class_loads']},
            'runtime':json.loads((view_folder/'runtime-snapshot.json').read_text(encoding='utf-8')),
            **{k:v for k,v in metrics.items() if k not in ['sequences','all_position_packets']}})
for path in (root/'build/test-results/test').glob('TEST-*.xml'):
    suite = ET.parse(path).getroot()
    for key in ['tests','failures','errors','skipped']:
        out['tests'][key] = out['tests'].get(key,0)+int(suite.get(key,0))
    if suite.get('name').endswith('CameraBearingTest'):
        out['tests']['replay_summary'] = suite.findtext('system-out','').strip().splitlines()
destination = root/a.output
destination.parent.mkdir(parents=True,exist_ok=True)
destination.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in out.items() if k!='runs'},indent=2))
