"""Audit actual production commitments and per-tick camera output from lock support replay."""
import argparse,json,pathlib,re,statistics
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
f=pathlib.Path(__file__).resolve().parents[2]/'build/p0/runs'/a.label
def values(line):return dict(re.findall(r'(\w+)=([^ ]+)',line))
def diff(a,b):return (float(a)-float(b)+180)%360-180
server=(f/'server/process.log').read_text(encoding='utf-8',errors='replace').splitlines()
client=(f/'P0A/process.log').read_text(encoding='utf-8',errors='replace').splitlines()
starts={};seqs={};aims={};hits=set();last=None;inputs=[];facing={}
for line in server:
 r=values(line)
 if ' CW START ' in line and r.get('player')=='P0A': last=r['id'];starts[last]=r
 if ' CW LOCK_AIM ' in line and r.get('player')=='P0A':aims[r['id']]=r
 if ' CW ACK ' in line and r.get('player')=='P0A' and r.get('result')=='START':seqs[r['seq']]=last
 if ' CW COMMIT ' in line and r.get('player')=='P0A' and r.get('hit')=='true' and aims.get(r['id'],{}).get('target')==r.get('target'):hits.add(r['id'])
 if ' CW INPUT ' in line:inputs.append(r)
 if ' CW FACING ' in line and r.get('player')=='P0A':facing[r['seq']]=r
attacks=[];markers=[];follows=[];mouse=[];switches=[];phase='';camera=[]
for line in client:
 r=values(line)
 if 'P0_LOCK_PHASE ' in line and 'receivedNano=' in line:phase=r['name']
 if 'P0_LOCK_ATTACK ' in line:
  if 'seq' not in r:continue
  id=seqs.get(r['seq']);aim=aims.get(id)
  r.update(instance=id,aim=aim,hit=id in hits,facing=facing.get(r['seq']))
  if aim:
   r.update(error_before=abs(diff(aim['targetYaw'],aim['before'])),error_after=abs(diff(aim['targetYaw'],aim['after'])))
  attacks.append(r)
 if 'P0_LOCK_MARKER ' in line:markers.append(r)
 if 'CW_LOCK_FOLLOW ' in line:r['phase']=phase;follows.append(r)
 if 'P0_LOCK_MOUSE ' in line:mouse.append(r)
 if 'P0_LOCK_F5 ' in line:switches.append(r)
 if 'P0_LOCK_CAMERA ' in line:camera.append(r)
summary={}
for mode in ['WEAK','STRONG']:
 rows=[r for r in follows if r['mode']==mode and r['phase'] in ['WEAK','STRONG']]
 speeds=[abs(diff(r['after'],r['before']))/min(.05,float(r['dt'])) for r in rows if float(r['dt'])>0]
 cap=180 if mode=='WEAK' else 720
 summary[mode]={'samples':len(rows),'peak_yaw_degrees_per_second':max(speeds,default=None),
 'speed_violations':sum(v>cap+.01 for v in speeds),'behind_stops':sum(r['reason']=='BEHIND' for r in rows),
 'committed_stops':sum(r['reason']=='COMMITTED' for r in rows),'active_follow_samples':sum(r['instance']!='0' and r['reason']==mode for r in rows)}
for mode,cap in [('WEAK',180),('STRONG',720)]:
 pairs=[(x,y) for x,y in zip(follows,follows[1:]) if x['phase']==y['phase'] and x['mode']==y['mode']==mode and y['phase'] in ['WEAK','STRONG']]
 speeds=[abs(diff(y['after'],x['after']))/((int(y['nano'])-int(x['nano']))/1e9) for x,y in pairs if int(y['nano'])>int(x['nano'])]
 summary[mode]['full_view_peak_degrees_per_second']=max(speeds,default=None)
 summary[mode]['full_view_speed_violations']=sum(v>cap+.01 for v in speeds)
commitments={}
for r in follows:
 if r['instance']!='0':commitments.setdefault(r['instance'],set()).add(r['committed'])
mouse_results=[]
for m in mouse:
 row=next((r for r in follows if int(r['nano'])>=int(m['nano']) and r['phase']==m['phase']),None)
 mouse_results.append({'input':m,'next':row,'response_ms':(int(row['nano'])-int(m['nano']))/1e6 if row else None})
f5=[]
for s in switches:
 row=next((r for r in follows if int(r['nano'])>=int(s['nano'])),None)
 f5.append({'input':s,'next':row,'yaw_delta':abs(diff(row['after'],row['before'])) if row else None})
def groups(phase):
 selected=[r for r in attacks if r['phase']==phase]
 result={}
 for key in sorted(set((r['aim']['enabled'] if phase=='AIM' else r['phase']) for r in selected if r['aim'])):
  rows=[r for r in selected if r['aim'] and (r['aim']['enabled'] if phase=='AIM' else r['phase'])==key]
  result[key]={'count':len(rows),'hits':sum(r['hit'] for r in rows),'hit_rate':sum(r['hit'] for r in rows)/len(rows),
   'before_mean':statistics.mean(r['error_before'] for r in rows),'after_mean':statistics.mean(r['error_after'] for r in rows),
   'max_correction':max(float(r['aim']['correction']) for r in rows),
   'angles':{str(angle):{'count':len(g:=[r for r in rows if abs(r['error_before']-angle)<.1]),'before_mean':statistics.mean(r['error_before'] for r in g) if g else None,'after_mean':statistics.mean(r['error_after'] for r in g) if g else None,'hits':sum(r['hit'] for r in g)} for angle in [10,30,60]}}
 return result
result={'attacks':attacks,'markers':markers,'marker_violations':sum(r['expected']!=r['actual'] for r in markers),
 'assistance':groups('AIM'),'camera_off':groups('CAMERA_OFF'),'camera_strong':groups('CAMERA_STRONG'),
 'camera':summary,'mouse':mouse_results,'view_switches':f5,
 'committed_yaw_changes':sum(len(v)!=1 for v in commitments.values()),
 'gui_stop_samples':sum(r['phase']=='GUI' and r['reason']=='INACTIVE' for r in follows),
 'correction_violations':sum(float(r['correction'])>15.0001 for r in aims.values()),
 'angle_history_fallbacks':sum(r['accepted']!='true' for r in facing.values()),
 'unmatched_attacks':sum(not r['aim'] for r in attacks),
 'input_gate_rejections':[r for r in inputs if r['gate']!='OK'],
 'occluded_attacks':[r for r in attacks if r['phase']=='OCCLUDED'],
 'occluded_stop_samples':sum(r['phase']=='OCCLUDED' and r['reason']=='OCCLUDED' for r in follows),
 'follows':[r for r in follows if r['phase'] in ['WEAK','STRONG','OCCLUDED','GUI']],
 'camera_samples':[r for r in camera if r['phase'] in ['WEAK','STRONG']]}
(f/'lock-support-metrics.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in ['attacks','follows','camera_samples','input_gate_rejections']},indent=2))
