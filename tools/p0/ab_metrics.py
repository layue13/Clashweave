"""Aggregate all six fixed A/B blocks. Raw frames stay under ignored build/."""
import collections, hashlib, json, math, pathlib, re, statistics, subprocess, sys, xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/p0/evidence/ab-100ms';OUT.mkdir(parents=True,exist_ok=True)
def fields(line):return dict(re.findall(r'(\w+)=([^ ]+)',line))
def text(label,role):return (ROOT/'build/p0/runs'/label/role/'process.log').read_text(encoding='utf-8',errors='replace').splitlines()
def stats(xs):return {'mean':statistics.mean(xs),'min':min(xs),'max':max(xs),'range':max(xs)-min(xs)}
blocks=[]
for index,variant in enumerate('ABABAB',1):
    ml=f'ab-{index}-{variant}-motion';vl=f'ab-{index}-{variant}-view'
    subprocess.run([sys.executable,str(ROOT/'tools/p0/camera_bounds_metrics.py'),'--label',ml],check=True)
    subprocess.run([sys.executable,str(ROOT/'tools/p0/playfix_metrics.py'),'--label',vl],check=True,stdout=subprocess.DEVNULL)
    motion=json.loads((ROOT/'build/p0/runs'/ml/'camera-bounds-summary.json').read_text())['phases']['MOTION_90']
    events=[];all_events=[];phase='';begin=0;counts=collections.Counter();all_counts=collections.Counter()
    pending=None;last_nano=None;raw_x=raw_z=raw_vx=raw_vz=raw_age=0;raw_ready=False
    for line in text(ml,'P0A'):
        d=fields(line)
        if 'P0_COMPOSITION_PHASE name=' in line and 'receivedNano' in d:phase=d['name'];begin=int(d['receivedNano'])
        if 'P0_DIAG_CAMERA ' in line:
            for counter in [all_counts]+([counts] if phase=='MOTION_90' else []):
                if d['branch']!='NONE':counter[d['branch']]+=1
                if d['historyLost']=='true':counter['HISTORY_LOST_subset_of_NOT_READY']+=1
                if d['stall']=='true':counter['STALL']+=1
            event={'phase':phase,'seconds_from_phase_start':(int(d['nano'])-begin)/1e9,**{k:d[k] for k in ['branch','historyLost','stall','residual','change','threshold','realSeconds','suppressed','beforeVelocity','afterVelocity']}}
            all_events.append(event)
            pending=event
            if phase=='MOTION_90':events.append(event)
        if 'CW_THIRD_FRAME ' in line:
            nano=int(d['nano']);real=0 if last_nano is None else (nano-last_nano)/1e9;last_nano=nano
            raw_age+=real
            if raw_age>.5:raw_vx=raw_vz=0
            dx=float(d['rawX'])-raw_x;dz=float(d['rawZ'])-raw_z;moved=math.hypot(dx,dz)>1e-6
            residual=math.hypot(dx-raw_vx*raw_age,dz-raw_vz*raw_age)
            if pending is not None:
                pending['raw_sample_context']={'reconstructed_sample_age_seconds':raw_age,'raw_position_delta_blocks':math.hypot(dx,dz),
                    'previous_raw_speed_blocks_per_second':math.hypot(raw_vx,raw_vz),'predicted_delta_blocks':math.hypot(raw_vx,raw_vz)*raw_age,
                    'reconstructed_position_residual':residual,'logged_position_residual':float(pending['residual']),
                    'reconstruction_matches_log':abs(residual-float(pending['residual']))<1e-5}
                pending=None
            if moved and raw_ready and raw_age>0:raw_vx=dx/raw_age;raw_vz=dz/raw_age
            if moved or not raw_ready:raw_x=float(d['rawX']);raw_z=float(d['rawZ']);raw_age=0
            raw_ready=True
    view=json.loads((ROOT/'build/p0/runs'/vl/'view-metrics.json').read_text())
    presses={};begins=[];completed=[];last_nano=0;view_events=[];view_counts=collections.Counter()
    for line in text(vl,'P0A'):
        d=fields(line)
        if 'P0_DIAG_PRESS ' in line and d['actor']=='P0A':presses[int(d['sequence'])]=d
        if 'P0_VIEW_SEQUENCE ' in line:
            if 'begin' in d:begins.append((int(d['nano']),int(d['begin'])))
            if 'completed' in d:completed.append(int(d['completed']))
        if 'CW_SOULS_FRAME ' in line:last_nano=max(last_nano,int(d['nano']))
        if 'P0_DIAG_CAMERA ' in line:
            if d['branch']!='NONE':view_counts[d['branch']]+=1
            if d['historyLost']=='true':view_counts['HISTORY_LOST_subset_of_NOT_READY']+=1
            if d['stall']=='true':view_counts['STALL']+=1
            view_events.append(d)
    def combo_at(nano):return max((seq for t,seq in begins if t<=nano),default=0)
    inputs={};facing={};expired=[];acks=[]
    for line in text(vl,'server'):
        d=fields(line)
        if 'P0_DIAG_INPUT ' in line and d['actor']=='P0A':inputs[int(d['sequence'])]=d
        if 'CW FACING player=P0A ' in line:facing[int(d['seq'])]=d
        if 'P0_DIAG_EXPIRED ' in line and d['actor']=='P0A':expired.append(d)
        if 'CW ACK player=P0A ' in line:acks.append(d)
    requests={seq:d for seq,d in inputs.items() if int(d['kind']) in (0,1,2)}
    combo_requests={seq:d for seq,d in requests.items() if seq in presses and combo_at(int(presses[seq]['nano']))}
    rejected=collections.Counter(d['result'] for d in acks if int(d['seq']) in combo_requests and d['result'] not in ('START','OK'))
    diagnostics=[]
    for d in expired:
        seq=int(d['sequence']);p=presses.get(seq,{});i=inputs.get(seq,{});f=facing.get(seq,{})
        elapsed=int(d['tick'])-int(d['currentStart']);changed=d['changed']=='true';wait_hit=d.get('edge_hit')=='true' and d['confirmedHit']=='false'
        classification='STATE_CHANGED' if changed else 'BUFFER_EXPIRED_WAITING_FOR_HIT' if wait_hit else 'BUFFER_EXPIRED'
        diagnostics.append({'sequence':seq,'combo':combo_at(int(p.get('nano',0))),'classification':classification,
            'client_press':{k:v for k,v in p.items() if k!='nano'},'server_consume':{k:v for k,v in i.items() if k!='nano'},
            'scheduler_expiry':{k:v for k,v in d.items() if k!='nano'},'elapsed_at_expiry':elapsed,
            'facing_validation':f,'observed_origin_matches_on_arrival':p.get('origin')==i.get('currentOrigin'),
            'state_revision_matches_on_arrival':p.get('observed_revision')==i.get('revision'),
            'network_stamp_stale':any(x['result']=='REJECT:STALE' and int(x['seq'])==seq for x in acks),
            'fixture_combo_completed':combo_at(int(p.get('nano',0))) in completed,
            'client_idle_after_failed_combo':any('P0_SOULS_VIEW_READY ' in line and 'action=idle' in line for line in text(vl,'P0A')),
            'note':'Scheduler expiry predicate has no yaw argument; unconfirmed hit is a possible indirect path, not proof of camera causality.'})
    first=begins[0][0] if begins else None;packets=[]
    for line in text(vl,'P0A'):
        if 'CW_VIEW packet=' in line:
            d=fields(line)
            if first is not None and first<=int(d['nano'])<=last_nano:packets.append(d)
    snapshot=json.loads((ROOT/'build/p0/runs'/ml/'runtime-snapshot.json').read_text())
    blocks.append({'block':index,'variant':variant,'motion_label':ml,'view_label':vl,
        'runtime_hashes':snapshot,
        'view_runtime_hashes':json.loads((ROOT/'build/p0/runs'/vl/'runtime-snapshot.json').read_text()),
        'motion':{'projection_frames':motion['all']['frames'],**motion['all']['target_x_abs_deviation'],
            **motion['prediction_audit'],'jump_branch_counts':dict(counts),'trace_events':events,
            'all_motion_branch_counts':dict(all_counts),'all_motion_branch_events':all_events},
        'view':{'completed_combos':view['complete_sequences'],'attempted_combos':len(begins),'planned_combos':20,
            'action_requests':len(combo_requests),'rejections':dict(rejected),'expired_diagnostics':diagnostics,
            'input_received_age_ticks':stats([int(d['receivedAge']) for d in combo_requests.values()]),
            'input_consumed_effective_age_ticks':stats([int(d['effectiveAge']) for d in combo_requests.values()]),
            'observed_origin_changes_on_arrival':sum(presses[seq]['origin']!=d['currentOrigin'] for seq,d in combo_requests.items()),
            'observed_revision_changes_on_arrival':sum(presses[seq]['observed_revision']!=d['revision'] for seq,d in combo_requests.items()),
            'facing_results':dict(collections.Counter(facing.get(seq,{}).get('reason','MISSING') for seq in combo_requests)),
            'facing_fallbacks':sum(facing.get(seq,{}).get('accepted')!='true' for seq in combo_requests),
            's08_during_combo_period':len(packets),'packet_caused_rotation_changes':sum(abs(float(d['yawDelta']))>1e-6 or abs(float(d['pitchDelta']))>1e-6 for d in packets),
            'max_packet_yaw_delta':max((abs(float(d['yawDelta'])) for d in packets),default=0),
            'max_packet_pitch_delta':max((abs(float(d['pitchDelta'])) for d in packets),default=0),
            'pre_combo_s08':len(view['all_position_packets'])-len(packets),
            'action_starts':view['action_starts']},
        'view_branch_counts':dict(view_counts),
        'view_branch_events':[{'seconds_from_combo_start':(int(d['nano'])-first)/1e9 if first else None,**{k:v for k,v in d.items() if k!='nano'}} for d in view_events],
        'process_summary':{scene:{k:v for k,v in json.loads((ROOT/'build/p0/runs'/label/'summary.json').read_text()).items() if k in ('server_exit','client_class_loads','complete')} for scene,label in [('motion',ml),('view',vl)]}})
aggregate={}
for variant in 'AB':
    rows=[b for b in blocks if b['variant']==variant]
    aggregate[variant]={key:stats([b[scene][key] for b in rows]) for scene,key in [('motion','p95'),('motion','median'),('motion','zero_lead_frames'),('motion','longest_zero_lead_seconds'),('view','completed_combos'),('view','s08_during_combo_period'),('view','packet_caused_rotation_changes')]}
    aggregate[variant]['reject_counts']=dict(sum((collections.Counter(b['view']['rejections']) for b in rows),collections.Counter()))
    aggregate[variant]['branch_counts']=dict(sum((collections.Counter(b['motion']['jump_branch_counts']) for b in rows),collections.Counter()))
out={'design':'Six alternating A/B blocks, isolated motion then view subprocesses in each block. No completed or partial run retries.',
    'rtt_ms':100,'motion_scope':'MOTION_90 all GLU frames, including transition; nearest-rank quantiles; zero duration is first-to-last zero-lead timestamp.',
    'view_scope':'P0A LIGHT/HEAVY/SHEATHE after first combo begin; S08 through last camera frame, including incomplete combos. Login/setup S08 excluded and separately counted.',
    'history_lost_note':'An annotation of !ready with lostHistory in B; not a fourth independent OR term. Counts overlap NOT_READY.',
    'runs':blocks,'aggregate':aggregate,
    'mean_p95_B_minus_A':aggregate['B']['p95']['mean']-aggregate['A']['p95']['mean']}
for name in ['p0-validation.jar','p0-diagnostics.jar']:
    hashes={entry['sha256'] for b in blocks for snapshots in [b['runtime_hashes'],b['view_runtime_hashes']] for entry in snapshots if pathlib.Path(entry['snapshot']).name==name}
    if len(hashes)!=1:raise RuntimeError('Fixture or trace observer changed between runs: '+name)
    out[name+'_sha256']=hashes.pop()
out['execution_manifest']=json.loads((ROOT/'build/p0/ab-manifest.json').read_text())
verification=ROOT/'build/p0/diagnostics-check.json'
if verification.exists():out['observer_verification']=json.loads(verification.read_text())
checks=ROOT/'build/p0/ab-final-checks.log'
if checks.exists():
    log=checks.read_text(encoding='utf-8',errors='replace')
    if 'BUILD SUCCESSFUL' not in log:raise RuntimeError('Checks did not pass')
    suites=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
    out['checks']={'junit':{k:sum(int(s.attrib.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']},
        'test_task_executed':'> Task :test\n' in log,'spotless':'PASS','checkstyleMain':'PASS','checkstyleTest':'PASS',
        'production_source_changes':bool(subprocess.check_output(['git','diff','--name-only','--','src/main','src/test'],cwd=ROOT).strip())}
(OUT/'summary.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(aggregate,indent=2))
