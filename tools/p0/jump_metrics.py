"""Fixed six-block jump trial; aggregate existing production trace, never load old hooks."""
import collections, hashlib, json, pathlib, re, statistics, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/p0/evidence/jump-windows';OUT.mkdir(parents=True,exist_ok=True)
def fields(line):return dict(re.findall(r'(\w+)=([^ ]+)',line))
def lines(label,role):return (ROOT/'build/p0/runs'/label/role/'process.log').read_text(encoding='utf-8',errors='replace').splitlines()
def stats(xs):return {'mean':statistics.mean(xs),'min':min(xs),'max':max(xs),'range':max(xs)-min(xs)}
runs=[]
for index,variant in enumerate('ABABAB',1):
    ml=f'jump-{index}-{variant}-motion';vl=f'jump-{index}-{variant}-view'
    subprocess.run([sys.executable,str(ROOT/'tools/p0/camera_bounds_metrics.py'),'--label',ml],check=True)
    subprocess.run([sys.executable,str(ROOT/'tools/p0/playfix_metrics.py'),'--label',vl],check=True,stdout=subprocess.DEVNULL)
    motion=json.loads((ROOT/'build/p0/runs'/ml/'camera-bounds-summary.json').read_text())['phases']['MOTION_90']
    phase='';begin=0;previous=None;episode=None;episodes=[]
    for line in lines(ml,'P0A'):
        d=fields(line)
        if 'P0_COMPOSITION_PHASE name=' in line and 'receivedNano' in d:phase=d['name'];begin=int(d['receivedNano'])
        if phase!='MOTION_90' or 'CW_THIRD_FRAME ' not in line:continue
        nano=int(d['nano']);interval=0 if previous is None else (nano-previous)/1e6;previous=nano
        if float(d['lead'])==0:
            if episode is None:episode={'start_seconds':(nano-begin)/1e9,'frames':0,'duration_seconds':0,'max_observed_interval_millis':0}
            episode['frames']+=1;episode['duration_seconds']=(nano-begin)/1e9-episode['start_seconds']
            episode['max_observed_interval_millis']=max(episode['max_observed_interval_millis'],interval)
        elif episode is not None:episodes.append(episode);episode=None
    if episode is not None:episodes.append(episode)
    view=json.loads((ROOT/'build/p0/runs'/vl/'view-metrics.json').read_text())
    client=lines(vl,'P0A');server=lines(vl,'server');requests=[];revisions=[];begins=[];last_nano=0
    for line in client:
        d=fields(line)
        if 'P0_REQUEST intent=' in line and d['intent'] in ('LIGHT','HEAVY','SHEATHE'):requests.append(d)
        if 'CW_ENGAGEMENT player=P0A ' in line:revisions.append(d)
        if 'P0_VIEW_SEQUENCE begin=' in line:begins.append((int(d['nano']),int(d['begin'])))
        if 'CW_SOULS_FRAME ' in line:last_nano=max(last_nano,int(d['nano']))
    inputs=[];acks=[];facing={};starts={}
    for line in server:
        d=fields(line)
        if 'CW INPUT player=P0A ' in line and int(d['kind']) in (0,1,2):inputs.append(d)
        if 'CW ACK player=P0A ' in line:acks.append(d)
        if 'CW FACING player=P0A ' in line:facing[int(d['seq'])]=d
        if 'CW START player=P0A ' in line:starts[int(d['id'])]=d
    if len(requests)!=len(inputs):raise RuntimeError(f'{vl}: input/press count mismatch {len(inputs)}/{len(requests)}')
    first=begins[0][0] if begins else None
    paired={int(i['seq']):(i,p) for i,p in zip(inputs,requests) if first is not None and int(p['nano'])>=first}
    rejected=collections.Counter(a['result'] for a in acks if int(a['seq']) in paired and a['result'] not in ('START','OK'))
    rejected_details=[]
    for a in acks:
        seq=int(a['seq'])
        if a['result'] in ('START','OK') or seq not in paired:continue
        i,p=paired[seq];rev=[d for d in revisions if int(d['receive'])<=int(p['nano'])]
        current=rev[-1] if rev else None
        rejected_details.append({'result':a['result'],'sequence':seq,'stamp':int(i['stamp']),'instance_id':int(i['origin']),
            'revision':int(current['revision']) if current else None,
            'revision_source':'last client CW_ENGAGEMENT at/before P0_REQUEST; revision is not carried by InputMessage',
            'request_pairing':'FIFO order of action-only CW INPUT and P0_REQUEST, with equal counts verified',
            'received_tick':int(i['receivedTick']),'consumed_tick':int(i['consumedTick']),
            'stamp_age_at_receive':int(i['receivedTick'])-int(i['stamp']),
            'client_yaw':float(p['yaw']),'client_pitch':float(p['pitch']),
            'combo':max((n for t,n in begins if t<=int(p['nano'])),default=0),
            'gate':i['gate'],'facing_validation':facing.get(seq),
            'origin_start':starts.get(int(i['origin'])),
            'origin_elapsed_at_consume':int(i['consumedTick'])-int(starts[int(i['origin'])]['tick']) if int(i['origin']) in starts else None})
    incomplete=[]
    for sequence in view['sequences']:
        if sequence['complete']:continue
        sent=[{'sequence':seq,'stamp':int(i['stamp']),'instance_id':int(i['origin']),'gate':i['gate']}
            for seq,(i,p) in paired.items() if int(p['nano'])>=sequence['began']
            and max((n for t,n in begins if t<=int(p['nano'])),default=0)==sequence['sequence']]
        incomplete.append({'combo':sequence['sequence'],'observed_action_starts':len(sequence['starts']),
            'started_instances':[int(d['instance']) for d in sequence['starts']],
            'sent_requests':sent,'note':'Incomplete sequence retained; absence of a follow-up request is not a server rejection.'})
    packets=[d for d in view['all_position_packets'] if first is not None and first<=int(d['nano'])<=last_nano]
    run={'block':index,'variant':variant,'motion_label':ml,'view_label':vl,
        'motion':{'median':motion['all']['target_x_abs_deviation']['median'],
            'p95':motion['all']['target_x_abs_deviation']['p95'],'projection_frames':motion['all']['frames'],
            **motion['prediction_audit'],'zero_episode_summaries':episodes},
        'view':{'completed_combos':view['complete_sequences'],'planned_combos':20,'attempted_combos':len(begins),
            'action_requests':len(paired),'action_starts':view['action_starts'],'rejections':dict(rejected),
            'expired_requests':[d for d in rejected_details if d['result']=='EXPIRED_OR_CHANGED'],
            'rejected_requests':rejected_details,'incomplete_combos':incomplete,'s08_in_combo_period':len(packets),
            'packet_rotation_changes':sum(abs(float(p['yawDelta']))>1e-6 or abs(float(p['pitchDelta']))>1e-6 for p in packets),
            'max_packet_yaw_delta':max((abs(float(p['yawDelta'])) for p in packets),default=0),
            'max_packet_pitch_delta':max((abs(float(p['pitchDelta'])) for p in packets),default=0),
            'pre_combo_s08':len(view['all_position_packets'])-len(packets),
            'facing_results':dict(collections.Counter(facing.get(seq,{}).get('reason','MISSING') for seq in paired))},
        'runtime':{}}
    for scene,label in [('motion',ml),('view',vl)]:
        folder=ROOT/'build/p0/runs'/label
        snapshot=json.loads((folder/'runtime-snapshot.json').read_text())
        if any('p0-diagnostics.jar' in e['snapshot'] for e in snapshot):raise RuntimeError('Old diagnostic observer loaded')
        run['runtime'][scene]={'hashes':snapshot,'process_summary':{k:v for k,v in json.loads((folder/'summary.json').read_text()).items() if k in ('server_exit','complete','client_class_loads')}}
    runs.append(run)
aggregate={}
for variant in 'AB':
    rr=[r for r in runs if r['variant']==variant]
    aggregate[variant]={key:stats([r[scene][key] for r in rr]) for scene,key in [('motion','median'),('motion','p95'),('motion','zero_lead_frames'),('motion','longest_zero_lead_seconds'),('view','completed_combos'),('view','s08_in_combo_period'),('view','packet_rotation_changes')]}
    aggregate[variant]['reject_counts']=dict(sum((collections.Counter(r['view']['rejections']) for r in rr),collections.Counter()))
a_ok=all(r['motion']['p95']<=.05 and r['motion']['zero_lead_frames']==0 for r in runs if r['variant']=='A')
b_ok=all(r['motion']['p95']<=.05 and r['motion']['zero_lead_frames']==0 for r in runs if r['variant']=='B')
combo_ok=all(r['view']['completed_combos']==20 for r in runs)
status='THIS_BATCH_CANNOT_DISTINGUISH' if a_ok and b_ok else 'CANDIDATE_MET_PREDECLARED_MOTION_CRITERIA' if b_ok else 'CANDIDATE_NOT_MET'
out={'baseline':'dabfb96','candidate':'2bd3b68','rtt_ms':100,'order':list('ABABAB'),
    'criterion_set_before_runs':{'p95_limit_W':.05,'candidate_all_zero_lead_frames':0,'ten_zero_frames_requires_other_trigger_report':10,'every_view_complete':20,'both_versions_normal':'THIS_BATCH_CANNOT_DISTINGUISH'},
    'judgment':{'motion':status,'baseline_motion_all_normal':a_ok,'candidate_motion_all_normal':b_ok,
        'candidate_runs_at_least_ten_zero_frames':[r['block'] for r in runs if r['variant']=='B' and r['motion']['zero_lead_frames']>=10],
        'all_combo_runs_complete':combo_ok},
    'scope':'MOTION_90 all frames incl transition, nearest-rank quantiles. Zero duration first-to-last consecutive zero timestamp. View S08 from first combo to last camera frame incl incomplete sequence.',
    'runs':runs,'aggregate':aggregate,
    'builds':{v:json.loads((ROOT/f'build/p0/jump-{name}-build.json').read_text()) for v,name in [('A','baseline'),('B','candidate')]},
    'execution_manifest':json.loads((ROOT/'build/p0/jump-manifest.json').read_text())}
hashes={e['sha256'] for r in runs for runtime in r['runtime'].values() for e in runtime['hashes'] if pathlib.Path(e['snapshot']).name=='p0-validation.jar'}
if len(hashes)!=1:raise RuntimeError('Validation fixture changed between runs')
out['validation_jar_sha256']=hashes.pop()
(OUT/'summary.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'judgment':out['judgment'],'aggregate':aggregate},indent=2))
