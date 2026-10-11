"""GLU projection and conservative projected-bounding-box occlusion proxy, no comfort claim."""
import argparse,json,pathlib,re,statistics
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args();f=pathlib.Path(__file__).resolve().parents[2]/'build/p0/runs'/a.label
ph='';elapsed=0;rows={}
for l in (f/'P0A/process.log').read_text(encoding='utf-8',errors='replace').splitlines():
 d=dict(re.findall(r'(\w+)=([^ ]+)',l))
 if 'P0_COMPOSITION_PHASE ' in l and 'receivedNano=' in l:ph=d['name'];elapsed=0
 if 'P0_COMPOSITION_FRAME ' in l:elapsed=int(d['elapsed'])
 if 'CAMERA_PROBE_FRAME ' in l and elapsed>40:rows.setdefault(ph,[]).append(d)
result={}
for ph,rr in rows.items():
 goal= .382 if ph.startswith('GOLDEN') else 1/3 if ph.startswith('THIRDS') else .5
 stats={}
 for key,target in [('playerX',goal),('targetX',1-goal),('horizon',goal)]:
  v=[float(x[key]) for x in rr];stats[key]={'minimum':min(v),'maximum':max(v),'mean':statistics.mean(v),'maximum_line_error':max(abs(x-target) for x in v),'violations':sum(abs(x-target)>.03 for x in v)}
 result[ph]={'samples':len(rr),'active':sum(x['active']=='true' for x in rr),'view':rr[0]['view'],'projection':stats,'projected_boxes_overlap_fraction':sum(x['boxesOverlap']=='true' for x in rr)/len(rr),'max_abs_roll':max(abs(float(x.get('roll','nan'))) for x in rr),'inactive_native_matrix_max_error':max(float(x.get('nativeMatrixError','nan')) for x in rr if x['active']=='false') if any(x['active']=='false' for x in rr) else None,'configuration':{k:rr[-1].get(k) for k in ['lateral','height','distance','yawAdjust','pitchAdjust']}}
(f/'camera-probe-metrics.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
