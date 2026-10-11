"""Fixed four alternating sessions per latency, five probes per k per session, no retries."""
import argparse,hashlib,json,pathlib,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--resume-startup-failure',action='store_true');args=parser.parse_args()
manifest={'order':list('ABAB'),'rtt_ms':[0,100],'repeats_per_k_per_session':5,'k_from_until':[-7,1],'runs':[]}
if args.resume_startup_failure:
    manifest=json.loads(ROOT.joinpath('build/p0/edge-manifest.json').read_text())
    failed=manifest['runs'][-1]
    if not failed['exit']:raise SystemExit('No startup failure to retain')
    if 'P0_EDGE_PLAN ' in ROOT.joinpath('build/p0/runs',failed['label'],'P0A/process.log').read_text(errors='replace'):raise SystemExit('Do not rerun a measured failure')
    manifest.setdefault('failed_startups',[]).append(manifest['runs'].pop())
for rtt in (0,100):
    for index,variant in enumerate('ABAB',1):
        if any(r['rtt_ms']==rtt and r['block']==index for r in manifest['runs']):continue
        label=f'edge-{rtt}-{index}-{variant}'
        if any(r['rtt_ms']==rtt and r['block']==index for r in manifest.get('failed_startups',[])):label+='-startup2'
        if ROOT.joinpath('build/p0/runs',label).exists():raise SystemExit('Existing label: '+label)
        jar=ROOT/'build/p0'/('baseline-542ebab.jar' if variant=='A' else 'comp-candidate.jar')
        cmd=[sys.executable,str(ROOT/'tools/p0/dual.py'),'--label',label,'--rtt',str(rtt),'--mod-snapshot',str(jar),'--snapshot-coremod','--accept-eula','--edge-boundary']
        entry={'rtt_ms':rtt,'block':index,'variant':variant,'label':label,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'command':cmd,'started_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
        print('BEGIN',label,flush=True)
        with ROOT.joinpath('build/p0',label+'-driver.log').open('w',encoding='utf-8') as log:
            entry['exit']=subprocess.run(cmd,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
        entry['finished_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime());print('END',label,'exit',entry['exit'],flush=True)
        manifest['runs'].append(entry);ROOT.joinpath('build/p0/edge-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        if entry['exit']:raise SystemExit('Retain failed attempt; never silently replace it.')
