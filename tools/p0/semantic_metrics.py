"""Check authority event ordering, actual observer delivery and unchanged default cue counts."""
import argparse,json,pathlib,re
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
f=pathlib.Path(__file__).resolve().parents[2]/'build/p0/runs'/a.label
text=(f/'server/process.log').read_text(encoding='utf-8',errors='replace')
events=[dict(re.findall(r'(\w+)=([^ ]+)',line.split('CW_EVENT ')[1])) for line in text.splitlines() if 'CW_EVENT id=' in line]
contacts=json.loads((f/'per-contact.json').read_text());metrics=json.loads((f/'metrics.json').read_text());ids=metrics['identities'];violations=[]
for row in contacts:
    actor=ids[row['player']];target=row['target'];instance=row['id'];tick=row['commitTick'];guard=int(row['guard']);hit=row['hit']=='true'
    actual=[e['kind'] for e in events if e['instance']==instance and e['tick']==tick and ((e['actor']==actor and e['target']==target and e['kind']=='HIT')or(e['actor']==target and e['target']==actor and e['kind'] in ['HURT','BLOCK','PARRY']))]
    expected=(['PARRY' if guard==2 else 'BLOCK'] if guard else [])+(['HIT','HURT'] if hit else [])
    if actual!=expected:violations.append(dict(row=row,expected=expected,actual=actual))
starts=[]
for line in text.splitlines():
    if ' CW START ' in line:
        r=dict(re.findall(r'(\w+)=([^ ]+)',line.split(' CW START ')[1]));actor=ids[r['player']]
        values=[e for e in events if e['actor']==actor and e['instance']==r['id'] and e['kind'] in ['DRAW','SWING','SHEATHE']]
        if r['action']!='sheathe' and [e['kind'] for e in values]!=(['DRAW','SWING'] if r['action']=='iai' else ['SWING']):violations.append(dict(start=r,events=values))
        if r['action']=='sheathe' and any(int(e['tick'])!=int(r['tick'])+8 for e in values):violations.append(dict(start=r,events=values))
        starts.append(r)
observers={}
for role,entity in ids.items():
    client=(f/role/'process.log').read_text(encoding='utf-8',errors='replace')
    received=[int(i) for i in re.findall(r'CW_PRESENT id=(\d+)',client)]
    expected=sum((int(r['guard'])>0 and (ids[r['player']]==entity or r['target']==entity))or(int(r['guard'])==0 and r['hit']=='true' and ids[r['player']]==entity) for r in contacts)
    actual=len(re.findall(r'P0_AUDIO source=',client))
    observers[role]={'received':len(received),'received_order_violations':sum(y<=x for x,y in zip(received,received[1:])), 'expected_cues':expected,'actual_cues':actual,'cue_difference':actual-expected}
result={'events':len(events),'contacts':len(contacts),'order_violations':len(violations),'violations':violations,'observers':observers}
(f/'semantic-metrics.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');(f/'events.json').write_text(json.dumps(events,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
