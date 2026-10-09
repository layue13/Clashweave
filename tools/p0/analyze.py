"""Independent reconstruction of guard spans, dual deadlines and client feedback timestamps."""
import argparse
import json
import pathlib
import re

ROOT=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--label',required=True)
args=parser.parse_args()
folder=ROOT/'build/p0/runs'/args.label
events=[]
for line in (folder/'server/process.log').read_text(encoding='utf-8',errors='replace').splitlines():
    match=re.search(r' CW (INPUT|ACK|START|FREEZE|COMMIT) (.*)',line)
    if match:
        event=dict(re.findall(r'(\w+)=([^ ]+)',match[2]))
        event['type']=match[1]
        events.append(event)
inputs={(e['player'],e['seq']):e for e in events if e['type']=='INPUT'}
guards={}
for event in events:
    if event['type']!='ACK': continue
    source=inputs.get((event['player'],event['seq']))
    if not source: continue
    if event['result']=='GUARD':
        guards.setdefault(event['player'],[]).append(dict(stamp=int(source['stamp']),arrival=int(source['arrival']),release=10**18))
    elif event['result']=='RELEASE' and guards.get(event['player']):
        guards[event['player']][-1]['release']=int(source['stamp'])
text=(folder/'server/process.log').read_text(encoding='utf-8',errors='replace')
identities={name:entity for name,entity in re.findall(r'(P0[AB])\[/[^\n]+?entity id (\d+)',text)}
if not identities:
    identities={name:entity for name,entity in re.findall(r'P0_NETWORK login=(\w+) entity=(\d+)',text)}
rows=[]
for event in events:
    if event['type']!='COMMIT': continue
    hit=int(event['hitTick']); cutoff=int(event['cutoff']); frozen=int(event['frozen'])
    defender=next((name for name,entity in identities.items() if entity==event['target']),None)
    spans=[s for s in guards.get(defender,[]) if s['arrival']<cutoff and s['stamp']<=hit<s['release']]
    span=spans[-1] if spans else None
    expected=0 if span is None else 2 if hit-span['stamp']<5 else 1
    if event.get('facing')=='false': expected=0
    # Fixed scenario defenders face their opponent. General geometry must be tested separately.
    actual=int(event['guard'])
    rows.append(dict(event,defender=defender,guard_stamp=span['stamp'] if span else None,
                     guard_arrival=span['arrival'] if span else None,expected_guard=expected,
                     deadline_violation=(event.get('targetPlayer',str(defender is not None)).lower()=='true') and (int(event['commitTick'])<hit+3 or cutoff-frozen<150_000_000),
                     extra_ticks=int(event['commitTick'])-hit,
                     guard_violation=defender is not None and expected!=actual,
                     freeze_to_commit_ms=(cutoff-frozen)/1e6))
feedback=[]
for name in ['P0A','P0B']:
    for line in (folder/name/'process.log').read_text(encoding='utf-8',errors='replace').splitlines():
        if 'CW_FRAME frozen=' in line or 'P0_AUDIO source=' in line or 'CW_FEEDBACK player=' in line:
            fields=dict(re.findall(r'(\w+)=([^ ]+)',line))
            if not fields.get('frozen') or int(fields['frozen'])==0: continue
            endpoint=fields.get('frame') or fields.get('nano') or fields.get('receive')
            feedback.append(dict(player=name,kind='frame' if 'frame' in fields else 'audio_source' if 'source' in fields else 'receive',
                                 frozen=int(fields['frozen']),endpoint=int(endpoint),delay_ms=(int(endpoint)-int(fields['frozen']))/1e6))
def limits(values):
    return dict(count=len(values),minimum=min(values),maximum=max(values),mean=sum(values)/len(values)) if values else dict(count=0)
summary=dict(identities=identities,contacts=len(rows),
             mob_extra_ticks=limits([r['extra_ticks'] for r in rows if r['defender'] is None]),
             mob_commit_ms=limits([r['freeze_to_commit_ms'] for r in rows if r['defender'] is None]),guard_violations=sum(r['guard_violation'] for r in rows),
             deadline_violations=sum(r['deadline_violation'] for r in rows),
             freeze_to_commit=limits([r['freeze_to_commit_ms'] for r in rows]),
             feedback={kind:limits([r['delay_ms'] for r in feedback if r['kind']==kind]) for kind in ['receive','frame','audio_source']},
             input_gate_rejects=[e for e in events if e['type']=='INPUT' and e['gate']!='OK'],
             acknowledgements=[e for e in events if e['type']=='ACK'])
mob_frozen={int(r['frozen']) for r in rows if r['defender'] is None}
summary['mob_feedback']={kind:limits([r['delay_ms'] for r in feedback if r['kind']==kind and r['frozen'] in mob_frozen]) for kind in ['receive','frame','audio_source']}
aim=[]
for line in text.splitlines():
    if ' CW AIM ' in line: aim.append(dict(re.findall(r'(\w+)=([^ ]+)',line)))
summary['aim']={'samples':len(aim),'accepted':sum(r['accepted']=='true' for r in aim),
    'known_yaw_deviation':limits([abs(float(r['beforeDelta'])) for r in aim]),
    'selected_yaw_deviation':limits([abs(float(r['afterDelta'])) for r in aim]),
    'old_horizontal_pitch_deviation':limits([abs(float(r['beforePitchDelta'])) for r in aim if 'beforePitchDelta' in r]),
    'selected_pitch_deviation':limits([abs(float(r['afterPitchDelta'])) for r in aim if 'afterPitchDelta' in r])}
local=[];local_audio=[]
for name in ['P0A','P0B']:
    local_text=(folder/name/'process.log').read_text(encoding='utf-8',errors='replace')
    source_times=[int(t) for t in re.findall(r'P0_LOCAL_AUDIO nano=(\d+)',local_text)]
    for line in local_text.splitlines():
        if 'CW_LOCAL_SWING ' in line:
            r=dict(re.findall(r'(\w+)=([^ ]+)',line));local.append((int(r['feedback'])-int(r['press']))/1e6)
            matched=[t for t in source_times if int(r['press'])<=t<=int(r['feedback'])+50_000_000]
            if matched:local_audio.append((min(matched)-int(r['press']))/1e6)
summary['local_swing_dispatch_ms']=limits(local)
summary['local_audio_source_ms']=limits(local_audio)
catalog=json.loads((folder/'server/config/clashweave/katana.json').read_text(encoding='utf-8'))
definitions={a['id']:a for a in catalog['actions']}
confirmed={}
for e in events:
    if e['type']=='COMMIT' and (e['hit']=='true' or int(e['guard'])>0):
        key=(e['player'],e['id'])
        confirmed[key]=min(confirmed.get(key,10**18),int(e['commitTick']))
executed={(e['player'],e['seq']) for e in events if e['type']=='ACK' and e['result']=='START'}
current={}
sheathed={}
requests=[]
for e in events:
    name=e['player']
    if e['type']=='START':
        current[name]=e
        if e['action']!='sheathe': sheathed[name]=False
    if e['type']!='INPUT' or e['gate']!='OK' or int(e['kind']) not in (0,1,2): continue
    tick=int(e.get('consumedTick',int(e['receivedTick'])+1))
    origin=current.get(name)
    if origin:
        definition=definitions[origin['action']]
        duration=sum(definition[k] for k in ('startup','active','recovery'))
        # Inputs are consumed before advance closes an instance at its duration boundary.
        if tick>int(origin['tick'])+duration:
            if origin['action']=='sheathe': sheathed[name]=True
            origin=None
            current.pop(name,None)
    intent=('LIGHT','HEAVY','SHEATHE')[int(e['kind'])]
    earliest=tick
    deadline=tick+catalog['bufferTicks']
    target=None
    origin_matches=int(e.get('origin',origin['id'] if origin else 0))==int(origin['id'] if origin else 0)
    if not origin_matches:
        target=None
    elif not origin:
        if intent=='LIGHT': target='iai' if sheathed.get(name,True) else 'light_1'
        elif not sheathed.get(name,True): target='heavy' if intent=='HEAVY' else 'sheathe'
    else:
        definition=definitions[origin['action']]
        edges=[edge for edge in definition['edges'] if edge['input']==intent and tick-int(origin['tick'])<edge['until']]
        if edges:
            edge=edges[0]
            target=edge['target']
            deadline=min(deadline,int(origin['tick'])+edge['until'])
            # Commit follows scheduler advancement; a newly confirmed hit becomes usable next tick.
            earliest=max(earliest,int(origin['tick'])+edge['from'],confirmed.get((name,origin['id']),10**18)+1 if edge['hit'] else 0)
    legal=target is not None and earliest<deadline
    requests.append(dict(player=name,seq=e['seq'],intent=intent,origin=origin['id'] if origin else None,target=target,
                         consumedTick=tick,earliest=earliest,deadline=deadline,legal=legal,executed=(name,e['seq']) in executed))
legal=[r for r in requests if r['legal']]
summary['action_execution']=dict(legal=len(legal),executed=sum(r['executed'] for r in legal),
    rate=sum(r['executed'] for r in legal)/len(legal) if legal else None,
    invalid=sum(not r['legal'] for r in requests),invalid_executed=sum(r['executed'] and not r['legal'] for r in requests))
alignments=[]
corrections=[]
for name in ['P0A','P0B']:
    client=(folder/name/'process.log').read_text(encoding='utf-8',errors='replace')
    for line in client.splitlines():
        if 'CW_ALIGN player=' in line:
            fields=dict(re.findall(r'(\w+)=([^ ]+)',line))
            alignments.append(dict(fields,delta=int(fields['authoritative'])-int(fields['predicted'])))
    corrections.extend(int(x) for x in re.findall(r'corrections=(\d+)',client))
summary['prediction']=dict(alignments=len(alignments),backwards=sum(a['delta']<0 for a in alignments),
    forwards=sum(a['delta']>0 for a in alignments),same=sum(a['delta']==0 for a in alignments),
    hard_corrections_max=max(corrections,default=0))
(folder/'alignments.json').write_text(json.dumps(alignments,indent=2),encoding='utf-8')
(folder/'requests.json').write_text(json.dumps(requests,indent=2),encoding='utf-8')
(folder/'per-contact.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
(folder/'feedback.json').write_text(json.dumps(feedback,indent=2),encoding='utf-8')
(folder/'metrics.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k!='acknowledgements'},indent=2))
