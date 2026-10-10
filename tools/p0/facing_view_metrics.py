"""Audit actual fast-turn requests and scoped S08 handling. No fabricated acceptance samples."""
import argparse,json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
f=root/'build/p0/runs'/a.label
def rows(path,marker):
 result=[]
 for line in path.read_text(encoding='utf-8',errors='replace').splitlines():
  if marker in line: result.append(dict(re.findall(r'(\w+)=([^ ]+)',line.split(marker,1)[1])))
 return result
turns=rows(f/'P0A/process.log','P0_FAST_TURN ')
faces=[r for r in rows(f/'server/process.log','CW FACING ') if r['player']=='P0A']
starts=[r for r in rows(f/'server/process.log','CW AIM ') if r['player']=='P0A']
inputs=rows(f/'server/process.log','CW INPUT ')
gate_rejected=[r for r in inputs if r['player']=='P0A' and r.get('kind')=='0' and r.get('gate')!='OK']
packets=rows(f/'P0A/process.log','CW_VIEW ')
marked=[r for r in packets if r.get('protected')=='true']
plain=[r for r in packets if r.get('protected')=='false']
result={'turns':turns,'requests':faces,'action_starts':starts,'fast_turn_count':len(turns),
 'accepted':sum(r['accepted']=='true' for r in faces),'fallback':sum(r['accepted']!='true' for r in faces),
 'input_gate_rejections':gate_rejected,
 'all_input_gate_rejections':[r for r in inputs if r.get('gate')!='OK'],
 'input_gate_totals':{gate:sum(r.get('gate')==gate for r in inputs) for gate in ['OK','SESSION','REPLAY','FUTURE','STALE','RATE']},
 'clamped_inputs':[r for r in inputs if r.get('gate')=='OK' and int(r.get('effectiveStamp',r['stamp']))<int(r['stamp'])],
 'effective_stamp_violations':sum(r.get('gate')=='OK' and int(r.get('effectiveStamp',r['stamp']))>int(r['receivedTick']) for r in inputs),
 'overall_acceptance_rate':sum(r['accepted']=='true' for r in faces)/len(turns) if turns else None,
 'evaluated_acceptance_rate':sum(r['accepted']=='true' for r in faces)/len(faces) if faces else None,
 'counts_by_angle':{angle:sum(r['turn']==angle for r in turns) for angle in ['45','90','180']},
 'all_submitted_accounted_for':len(turns)==len(faces)+len(gate_rejected) and len(faces)==len(starts) if turns else None,
 'marked_packets':marked,'unmarked_packets':plain,
 'marked_view_violations':sum(abs(float(r['yawDelta']))>1e-6 or abs(float(r['pitchDelta']))>1e-6 for r in marked),
 'unmarked_vanilla_violations':sum(abs(float(r['yawDelta'])-float(r['requestedYawDelta']))>1e-6 or abs(float(r['pitchDelta'])-float(r['requestedPitchDelta']))>1e-6 for r in plain),
 'empty_hand':rows(f/'P0A/process.log','P0_CORRECTION_CLIENT ')}
(f/'facing-view-metrics.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in ['turns','requests','action_starts','marked_packets','unmarked_packets','all_input_gate_rejections','clamped_inputs']},indent=2))
