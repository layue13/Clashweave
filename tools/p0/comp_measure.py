"""Fixed 0/100 ms alternating combo blocks, never retry or replace a partial run."""
import hashlib, json, pathlib, subprocess, sys, time
ROOT=pathlib.Path(__file__).resolve().parents[2]
manifest={'order':list('ABABAB'),'rtt_ms':[0,100],'runs':[]}
for rtt in [0,100]:
    for index,variant in enumerate('ABABAB',1):
        jar=ROOT/'build/p0'/('baseline-542ebab.jar' if variant=='A' else 'comp-candidate.jar')
        label=f'comp-{rtt}-{index}-{variant}-view'
        if (ROOT/'build/p0/runs'/label).exists():raise SystemExit('Existing label; choose a new prefix, never overwrite: '+label)
        command=[sys.executable,str(ROOT/'tools/p0/dual.py'),'--label',label,'--rtt',str(rtt),'--mod-snapshot',str(jar),'--snapshot-coremod','--accept-eula','--souls-view']
        entry={'block':index,'variant':variant,'rtt_ms':rtt,'label':label,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'command':command}
        entry['started_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
        print('BEGIN',label,flush=True)
        with (ROOT/'build/p0'/f'{label}-driver.log').open('w',encoding='utf-8') as log:
            entry['exit']=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
        entry['finished_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
        print('END',label,'exit',entry['exit'],flush=True)
        manifest['runs'].append(entry)
        (ROOT/'build/p0/comp-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        if entry['exit']:raise SystemExit('Run failed: retain attempt, never silently replace it.')
