"""Audit view samples and every intercepted S08 using monotonic execution timestamps."""
import argparse,json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
f=root/'build/p0/runs'/a.label
text=(f/'P0A/process.log').read_text(encoding='utf-8',errors='replace')
starts=[];begins={};ends={};packets=[]
for line in text.splitlines():
 if 'P0_VIEW_START ' in line:
  starts.append(dict(re.findall(r'(\w+)=([^ ]+)',line.split('P0_VIEW_START ')[1])))
 if 'P0_VIEW_SEQUENCE ' in line:
  row=dict(re.findall(r'(\w+)=([^ ]+)',line.split('P0_VIEW_SEQUENCE ')[1]))
  if 'begin' in row:begins[int(row['begin'])]=int(row['nano'])
  if 'completed' in row:ends[int(row['completed'])]=int(row['nano'])
 if 'CW_VIEW packet=' in line:
  packets.append(dict(re.findall(r'(\w+)=([^ ]+)',line.split('CW_VIEW ')[1])))
sequences=[]
for key,start in sorted(begins.items()):
 end=ends.get(key)
 rows=[r for r in starts if int(r['sequence'])==key]
 inside=[r for r in packets if end is not None and start<=int(r['nano'])<=end]
 sequences.append({'sequence':key,'complete':end is not None,'began':start,'ended':end,'starts':rows,
  'position_packets':len(inside),'max_yaw_delta':max([abs(float(r['yawDelta'])) for r in rows+inside],default=0),
  'max_pitch_delta':max([abs(float(r['pitchDelta'])) for r in rows+inside],default=0)})
result={'sequences':sequences,'complete_sequences':sum(r['complete'] for r in sequences),'action_starts':len(starts),
 'position_packets_in_sequences':sum(r['position_packets'] for r in sequences),
 'max_start_yaw_delta':max([abs(float(r['yawDelta'])) for r in starts],default=None),
 'max_start_pitch_delta':max([abs(float(r['pitchDelta'])) for r in starts],default=None),
 'all_position_packets':packets,
 'position_packet_view_violations':sum((r.get('protected','true')=='true') and (abs(float(r['yawDelta']))>1e-6 or abs(float(r['pitchDelta']))>1e-6) for r in packets),
 'all_position_packet_rotation_changes':sum(abs(float(r['yawDelta']))>1e-6 or abs(float(r['pitchDelta']))>1e-6 for r in packets),
 'unmarked_rotating_packets':sum(r.get('protected')=='false' and (abs(float(r['yawDelta']))>1e-6 or abs(float(r['pitchDelta']))>1e-6) for r in packets)}
(f/'view-metrics.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in ['sequences','all_position_packets']},indent=2))
