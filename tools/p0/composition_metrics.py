"""Per-phase production frame dynamics, true GLU horizon and server flick audit."""
import argparse,json,pathlib,re,statistics,math
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
f=pathlib.Path(__file__).resolve().parents[2]/'build/p0/runs'/a.label
values=lambda l:dict(re.findall(r'(\w+)=([^ ]+)',l))
difference=lambda a,b:(float(a)-float(b)+180)%360-180
phase='';elapsed=0;frames={};world={};mouse=[];cases=[];weak={}
for l in (f/'P0A/process.log').read_text(errors='replace',encoding='utf-8').splitlines():
 d=values(l)
 if 'P0_COMPOSITION_PHASE ' in l and 'receivedNano=' in l:phase=d['name'];elapsed=0
 if 'P0_COMPOSITION_FRAME ' in l:elapsed=int(d['elapsed']);world.setdefault(phase,[]).append(d)
 if 'CW_THIRD_FRAME ' in l:frames.setdefault(phase,[]).append(dict(d,elapsed=elapsed))
 if 'CW_THIRD_MOUSE ' in l:mouse.append(dict(d,phase=phase))
 if 'P0_FLICK_CASE ' in l:cases.append(d)
 if 'CW_LOCK_FOLLOW ' in l and phase=='FIRST':weak.setdefault(d['reason'],[]).append(d)
def pp(v):return max(v)-min(v) if v else None
def peak(v):return max(v) if v else None
def summary(rows,phase):
 rows=[d for d in rows if int(d['elapsed'])>40]
 vy=[];vp=[];ay=[];ap=[];last=None
 for d in rows:
  dt=float(d['dt']);now=int(d['nano']);
  if dt<=0:continue
  y=difference(d['after'],d['before'])/dt;q=(float(d['pitchAfter'])-float(d['pitchBefore']))/dt
  vy.append(abs(y));vp.append(abs(q))
  if last and now>last[0]:ay.append(abs(y-last[1])/((now-last[0])/1e9));ap.append(abs(q-last[2])/((now-last[0])/1e9))
  last=(now,y,q)
 raw=[math.degrees(math.atan2(-float(d['rawX']),float(d['rawZ']))) for d in rows]
 filtered=[float(d['goalYaw']) for d in rows];output=[float(d['after']) for d in rows];pitch=[float(d['pitchAfter']) for d in rows]
 return dict(samples=len(rows),yaw_peak_speed=peak(vy),pitch_peak_speed=peak(vp),yaw_peak_acceleration=peak(ay),pitch_peak_acceleration=peak(ap),yaw_pp=pp(output),raw_target_yaw_pp=pp(raw),filtered_target_yaw_pp=pp(filtered),pitch_pp=pp(pitch),jitter_attenuation=(pp(output)/pp(raw) if phase=='JITTER' and rows and pp(raw)>0 else None),speed_violations=sum(v>720.01 for v in vy+vp))
result={'phases':{k:summary(v,k) for k,v in frames.items()},'horizon':{},'mouse':mouse,'flick_cases':cases,'first_person':{k:{'count':len(v),'max_written_delta':max(abs(difference(d['after'],d['before'])) for d in v),'yaw_first':v[0]['before'],'yaw_last':v[-1]['after']} for k,v in weak.items()}}
for name,rows in world.items():
 rows=[r for r in rows if int(r['elapsed'])>40 and int(r['view'])==1]
 if rows:
  vals=[float(r['horizon']) for r in rows];result['horizon'][name]={'count':len(vals),'minimum':min(vals),'maximum':max(vals),'median':statistics.median(vals),'max_golden_error':max(abs(v-.382) for v in vals),'golden_tolerance_violations':sum(abs(v-.382)>.03 for v in vals)}
seq=None;arrival=None;switches=[]
for l in (f/'server/process.log').read_text(encoding='utf-8',errors='replace').splitlines():
 d=values(l)
 if ' CW INPUT ' in l and d.get('player')=='P0A' and d.get('kind')=='10':seq=d.get('seq');arrival=d.get('arrival')
 if ' CW LOCK_SWITCH ' in l:switches.append(dict(d,seq=seq,arrival=arrival))
result['server_switches']=switches
result['stationary_trials']=sum(d.get('kind')=='stationary' for d in cases);result['small_trials']=sum(d.get('kind')=='small' for d in cases)
result['client_flicks']=[d for d in mouse if d.get('switch')!='0'];result['small_or_stationary_false_triggers']=sum(float(d['delta'])<25 and d['switch']!='0' for d in mouse)
(f/'composition-metrics.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
