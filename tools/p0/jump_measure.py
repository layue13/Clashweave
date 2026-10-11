"""Fixed alternating blocks; never retry a completed, poor, or partial measurement."""
import argparse, hashlib, json, pathlib, subprocess, sys, time
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--after-first-motion',action='store_true');a=p.parse_args()
manifest={'order':list('ABABAB'),'rtt_ms':100,'runs':[]}
for index,variant in enumerate('ABABAB',1):
    jar=ROOT/'build/p0'/('baseline-dabfb96.jar' if variant=='A' else 'jump-candidate.jar')
    for scene in ['motion','view']:
        label=f'jump-{index}-{variant}-{scene}'
        command=[sys.executable,str(ROOT/'tools/p0/dual.py'),'--label',label,'--rtt','100','--mod-snapshot',str(jar),'--snapshot-coremod','--accept-eula']
        command+=['--camera-suite','motion'] if scene=='motion' else ['--souls-view']
        entry={'block':index,'variant':variant,'scene':scene,'label':label,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'command':command}
        if a.after_first_motion and index==1 and scene=='motion':
            entry['existing_first_run']=True
            summary=json.loads((ROOT/'build/p0/runs'/label/'summary.json').read_text())
            entry['exit']=0 if summary['complete'] and summary['server_exit']==0 else 1
        else:
            entry['started_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
            print('BEGIN',label,flush=True)
            with (ROOT/'build/p0'/f'{label}-driver.log').open('w',encoding='utf-8') as log:
                entry['exit']=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
            entry['finished_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
            print('END',label,'exit',entry['exit'],flush=True)
        manifest['runs'].append(entry)
        (ROOT/'build/p0/jump-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        if entry['exit']:
            raise SystemExit('Run failed: retain attempt and inspect; do not silently replace it.')
