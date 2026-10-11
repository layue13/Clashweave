"""All fixed combo runs, including failed/unresolved sequences and read-only RTT observations."""
import collections, json, math, pathlib, re, statistics, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/p0/evidence/combo-compensation';OUT.mkdir(parents=True,exist_ok=True)
def fields(line):return dict(re.findall(r'(\w+)=([^ ]+)',line))
def log(label,role):return (ROOT/'build/p0/runs'/label/role/'process.log').read_text(encoding='utf-8',errors='replace').splitlines()
def stats(xs):return {'mean':statistics.mean(xs),'min':min(xs),'max':max(xs)} if xs else None
def allowance(age,sample):
    ping=int(sample['ping']);cap=int(sample['cap']);jitter=int(sample['jitter'])
    return 0 if age<=0 or cap<=0 or ping<=0 else max(0,min(age,cap,math.ceil(ping/100)+min(jitter,4)))
def read_run(rtt,index,variant):
    label=f'comp-{rtt}-{index}-{variant}-view';folder=ROOT/'build/p0/runs'/label
    subprocess.run([sys.executable,str(ROOT/'tools/p0/playfix_metrics.py'),'--label',label],check=True,stdout=subprocess.DEVNULL)
    view=json.loads(folder.joinpath('view-metrics.json').read_text(encoding='utf-8'))
    requests=[]
    for line in log(label,'P0A'):
        if 'P0_REQUEST intent=' in line:requests.append(fields(line))
    inputs=[];acks={};samples={};starts={};facing={}
    for line in log(label,'server'):
        d=fields(line)
        if 'CW INPUT player=P0A ' in line and int(d['kind']) in (0,1,2):inputs.append(d)
        if 'CW ACK player=P0A ' in line:acks.setdefault(int(d['seq']),[]).append(d['result'])
        if 'P0_COMP_SAMPLE tick=' in line:samples[int(d['tick'])]=d
        if 'CW START player=P0A ' in line:starts[int(d['id'])]=d
        if 'CW FACING player=P0A ' in line:facing[int(d['seq'])]=d
    if len(inputs)!=len(requests):raise RuntimeError(f'{label}: request pairing differs {len(inputs)}/{len(requests)}')
    sequences=view['sequences'];first=min(s['began'] for s in sequences)
    last=max(s['ended'] or s['began'] for s in sequences)
    def combo(press):return max((s['sequence'] for s in sequences if s['began']<=press),default=0)
    accepted=[];rejected=[];request_count=0
    for i,p in zip(inputs,requests):
        if int(p['nano'])<first:continue
        request_count+=1;seq=int(i['seq']);tick=int(i['consumedTick']);origin=int(i['origin'])
        sample=samples.get(tick)
        if sample is None:raise RuntimeError(f'{label}: missing exact consume-tick ping sample seq={seq} tick={tick}')
        age=max(0,int(i['receivedTick'])-int(i['effectiveStamp']));comp=allowance(age,sample)
        row={'sequence':seq,'combo':combo(int(p['nano'])),'stamp':int(i['stamp']),
            'effective_stamp':int(i['effectiveStamp']),'received_tick':int(i['receivedTick']),
            'consumed_tick':tick,'receipt_age_ticks':age,'instance_id':origin,
            'server_ping_ms':int(sample['ping']),'configured_cap':int(sample['cap']),
            'jitter_ticks':int(sample['jitter']),'compensation_ticks_reconstructed':comp,
            'origin_start_tick':int(starts[origin]['tick']) if origin in starts else None,
            'action_age_at_consume':tick-int(starts[origin]['tick']) if origin in starts else None,
            'client_yaw':float(p['yaw']),'client_pitch':float(p['pitch']),
            'gate':i['gate'],'ack_results':acks.get(seq,[]),'facing':facing.get(seq)}
        failures=[a for a in row['ack_results'] if a not in ('START','OK','MOVE_READY')]
        if failures:rejected.append(row)
        if origin>0 and 'START' in row['ack_results']:accepted.append(row)
    combos=[]
    for s in sequences:
        failures=[r for r in rejected if r['combo']==s['sequence']]
        reason=s['reason']
        if s['failed'] and failures:reason=failures[-1]['ack_results'][-1]
        combos.append({'sequence':s['sequence'],'complete':s['complete'],'failed':s['failed'],
            'reason':reason,'fixture_reason':s['reason'],'outcome':s['outcome'],
            'started_instances':[int(a['instance']) for a in s['starts']]})
    packets=[p for p in view['all_position_packets'] if first<=int(p['nano'])<=last]
    runtime=json.loads(folder.joinpath('runtime-snapshot.json').read_text(encoding='utf-8'))
    if any('p0-diagnostics.jar' in e['snapshot'] for e in runtime):raise RuntimeError('Old diagnostic observer loaded')
    build_name='baseline' if variant=='A' else 'candidate'
    expected_jar='baseline-542ebab.jar' if variant=='A' else 'comp-candidate.jar'
    build=json.loads(ROOT.joinpath(f'build/p0/comp-{build_name}-build.json').read_text(encoding='utf-8-sig'))
    frozen=[e for e in runtime if pathlib.Path(e['source']).name==expected_jar]
    if len(frozen)!=1 or frozen[0]['sha256'].lower()!=build['sha256'].lower():raise RuntimeError('Frozen production jar mismatch: '+label)
    process=json.loads(folder.joinpath('summary.json').read_text(encoding='utf-8'))
    return {'rtt_ms':rtt,'block':index,'variant':variant,'label':label,
        'attempted':len(sequences),'completed':view['complete_sequences'],'failed':view['failed_sequences'],
        'unresolved':view['unresolved_sequences'],'failure_reasons':dict(collections.Counter(s['reason'] for s in combos if s['failed'])),
        'action_requests':request_count,'action_rejections':dict(collections.Counter(a for r in rejected for a in r['ack_results'] if a not in ('START','OK','MOVE_READY'))),
        'accepted_derivative_count':len(accepted),'accepted_derivatives':accepted,'rejected_requests':rejected,
        'receipt_age_ticks':stats([r['receipt_age_ticks'] for r in accepted]),
        'compensation_ticks':stats([r['compensation_ticks_reconstructed'] for r in accepted]),
        'server_ping_ms':stats([r['server_ping_ms'] for r in accepted]),'sequences':combos,
        's08':len(packets),'packet_rotation_changes':sum(abs(float(p['yawDelta']))>1e-6 or abs(float(p['pitchDelta']))>1e-6 for p in packets),
        'max_packet_yaw_delta':max((abs(float(p['yawDelta'])) for p in packets),default=0),
        'max_packet_pitch_delta':max((abs(float(p['pitchDelta'])) for p in packets),default=0),
        'process':{k:process[k] for k in ('complete','server_exit','client_class_loads')},'runtime_sha256':runtime}

def main():
    runs=[read_run(rtt,index,v) for rtt in (0,100) for index,v in enumerate('ABABAB',1)]
    def subset(rtt,v):return [r for r in runs if r['rtt_ms']==rtt and r['variant']==v]
    b100=subset(100,'B');a100=subset(100,'A');a0=subset(0,'A');b0=subset(0,'B')
    b_pass=all(r['attempted']==20 and r['completed']==20 and not r['action_rejections'].get('ILLEGAL_NODE',0) for r in b100)
    causal=b_pass and any(r['action_rejections'].get('ILLEGAL_NODE',0) for r in a100)
    both_no_fail=all(r['completed']==20 and not r['action_rejections'] for r in a100+b100)
    aggregate={f'{rtt}-{v}':{key:stats([r[key] for r in subset(rtt,v)]) for key in ('completed','failed','unresolved','s08','packet_rotation_changes','accepted_derivative_count')} for rtt in (0,100) for v in 'AB'}
    hashes={e['sha256'] for r in runs for e in r['runtime_sha256'] if pathlib.Path(e['snapshot']).name=='p0-validation.jar'}
    if len(hashes)!=1:raise RuntimeError('Common validation fixture changed across runs')
    result={'baseline':'542ebab','candidate_behavior':'81b4b86','candidate_build':json.loads(ROOT.joinpath('build/p0/comp-candidate-build.json').read_text(encoding='utf-8-sig')),
        'baseline_build':json.loads(ROOT.joinpath('build/p0/comp-baseline-build.json').read_text(encoding='utf-8-sig')),
        'predeclared_criteria':{'100ms_B_every_run_completed':20,'100ms_B_ILLEGAL_NODE':0,
            'causal_support':'A has ILLEGAL_NODE, B has none and completes all runs',
            '0ms':'A/B behavior consistent; compensation zero or small; retain new B failures',
            'all_normal':'Cannot distinguish compensation effect this batch'},
        'judgment':{'candidate_100ms_pass':b_pass,'causal_effect_supported':causal,
            'both_100ms_normal_cannot_distinguish':both_no_fail,
            '0ms_A_all20':all(r['completed']==20 for r in a0),'0ms_B_all20':all(r['completed']==20 for r in b0)},
        'measurement_note':'Compensation is reconstructed from existing CW INPUT receipt/effective stamps and read-only same-consume-tick ping/config samples, using the frozen production formula; not directly printed or sampled from the transient Request object. No production branch added.',
        'view_scope':'First sequence begin to final outcome, including failures, no completed-only filtering.',
        'validation_jar_sha256':hashes.pop(),'manifest':json.loads(ROOT.joinpath('build/p0/comp-manifest.json').read_text()),'runs':runs,'aggregate':aggregate}
    OUT.joinpath('summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'judgment':result['judgment'],'aggregate':aggregate},indent=2))
if __name__=='__main__':main()
