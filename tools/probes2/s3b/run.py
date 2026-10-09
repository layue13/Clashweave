"""Fresh real Forge server and two clients, FIFO latency fixture; no user worlds."""
import argparse, asyncio, hashlib, json, re, shutil, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def content(p):return p.read_text(errors='replace',encoding='utf-8') if p.exists() else ''
class Proxy:
    def __init__(self,rtt):self.rtt=rtt;self.delays=[];self.tasks=set();self.server=None
    async def pipe(self,r,w):
        q=asyncio.Queue()
        async def read():
            prior=0
            while chunk:=await r.read(65536):
                now=time.perf_counter();due=max(prior,now+self.rtt/2000);prior=due;await q.put((due,now,chunk))
            await q.put(None)
        async def send():
            while (item:=await q.get()) is not None:
                due,now,chunk=item
                # Python 3.12 Windows asyncio uses GetTickCount64 (15.625ms) and may wake early.
                # Keep its coarse timer out of the latency gate: QPC plus a waitable-timer worker.
                def wait_due():
                    while (remaining:=due-time.perf_counter())>0:time.sleep(remaining)
                await asyncio.to_thread(wait_due)
                w.write(chunk);await w.drain();self.delays.append(1000*(time.perf_counter()-now))
            w.close()
        try:await asyncio.gather(read(),send())
        except (OSError,ConnectionError):w.close()
    async def conn(self,r,w):
        try:
            sr,sw=await asyncio.open_connection('127.0.0.1',25572)
            await asyncio.gather(self.pipe(r,sw),self.pipe(sr,w))
        except OSError:w.close()
    async def start(self):self.server=await asyncio.start_server(self.conn,'127.0.0.1',25573)
    async def close(self):self.server.close();await self.server.wait_closed()
def launch(kind,cwd,log,role,rtt,reps,legacy,window,agecap):
    data=json.loads((ROOT/f'build/probes2/s3b/{kind}.json').read_text())
    cwd.mkdir();(cwd/'mods').mkdir();shutil.copy2(ROOT/'build/probes2/s3b/s3b-experiment.jar',cwd/'mods')
    jvm=[a for a in data['jvmArgs'] if not a.startswith(('-Xms','-Xmx'))]+['-Xms256M','-Xmx1G',f'-Dcw.s3b.role={role}',f'-Dcw.s3b.rtt={rtt}',f'-Dcw.s3b.repetitions={reps}',f'-Dcw.s3b.legacy={legacy}',f'-Dcw.s3b.window={window}',f'-Dcw.s3b.ageCap={agecap}']
    if kind=='runServer':
        jvm+=['-verbose:class'];extra=['nogui'];(cwd/'eula.txt').write_text('eula=true\n')
        (cwd/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25572\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nspawn-monsters=false\nspawn-animals=false\nview-distance=3\nmax-players=2\ndifficulty=1\n')
    else:
        extra=['--username','GuardA' if role=='attacker' else 'GuardB','--width','800','--height','450','--gameDir',str(cwd)]
        (cwd/'options.txt').write_text('soundCategory_master:0.0\nsoundCategory_music:0.0\nrenderDistance:3\nmaxFps:60\n')
    command=[data['java'],*jvm,'-cp',data['classpath'],data['main'],*data['args'],*extra]
    with log.open('wb') as stream:proc=subprocess.Popen(command,cwd=cwd,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    return proc,command
async def scenario(args,rtt):
    label=f'{args.label}-rtt{rtt}';work=ROOT/'run/probes2'/label;ev=ROOT/'docs/probes/round2/evidence'/label
    if work.exists() or ev.exists():raise ValueError('Choose new label; existing folders protected')
    work.mkdir(parents=True);proxy=Proxy(rtt);procs=[];commands=[]
    legacy=args.legacy if rtt==0 else 0
    try:
        s,c=launch('runServer',work/'server',work/'server.log','',rtt,args.repetitions,legacy,args.window,args.age_cap);procs.append(s);commands.append(c)
        deadline=time.monotonic()+180
        while 'Done (' not in content(work/'server.log'):
            if s.poll() is not None or time.monotonic()>deadline:raise RuntimeError('server startup failed')
            await asyncio.sleep(1)
        await proxy.start()
        b,c=launch('runClient',work/'defender',work/'defender.log','defender',rtt,args.repetitions,legacy,args.window,args.age_cap);procs.append(b);commands.append(c)
        deadline=time.monotonic()+150
        while 'LOGIN name=GuardB' not in content(work/'server.log'):
            if b.poll() is not None or time.monotonic()>deadline:raise RuntimeError('defender startup failed')
            await asyncio.sleep(1)
        a,c=launch('runClient',work/'attacker',work/'attacker.log','attacker',rtt,args.repetitions,legacy,args.window,args.age_cap);procs.append(a);commands.append(c)
        deadline=time.monotonic()+240;update=0
        while 'CLIENT_COMPLETE' not in content(work/'attacker.log'):
            if a.poll() is not None or s.poll() is not None or time.monotonic()>deadline:raise RuntimeError('incomplete matrix')
            if time.monotonic()>update:print(json.dumps({'rtt':rtt,'results':content(work/'server.log').count('CW3 RESULT')}),flush=True);update=time.monotonic()+20
            await asyncio.sleep(1)
        s.stdin.write(b'stop\n');s.stdin.flush()
        for _ in range(30):
            if s.poll() is not None:break
            await asyncio.sleep(.5)
        ev.mkdir(parents=True)
        text=content(work/'server.log');loads=[x for x in text.splitlines() if x.startswith('[Loaded ') and any(n in x for n in ('net.minecraft.client.','org.lwjgl.opengl.','experiment.s3b.Client'))]
        for name in ('server','attacker','defender'):
            lines=content(work/f'{name}.log').splitlines()
            (ev/f'{name}.log').write_text('\n'.join(x for x in lines if not x.startswith('[Loaded '))+'\n',encoding='utf-8')
            (ev/f'{name}-measurements.log').write_text('\n'.join(x for x in lines if 'CW3 ' in x)+'\n',encoding='utf-8')
        sourcepaths=list((ROOT/'tools/probes2/s3b').rglob('*.java'))+[ROOT/'tools/probes2/s3b/experiment.gradle',Path(__file__)]
        summary={'rtt_ms':rtt,'one_way_ms':rtt/2,'window':args.window,'age_cap':args.age_cap,'repetitions_per_lead':args.repetitions,'legacy_trials':legacy,'server_exit':s.poll(),'client_classloads':loads,'commands':commands,'jar_sha256':hashlib.sha256((ROOT/'build/probes2/s3b/s3b-experiment.jar').read_bytes()).hexdigest(),'sources_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sourcepaths},'proxy_chunks':len(proxy.delays),'delay_mean_ms':sum(proxy.delays)/len(proxy.delays),'delay_min_ms':min(proxy.delays),'delay_max_ms':max(proxy.delays)}
        (ev/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps({k:summary[k]for k in ('rtt_ms','server_exit','jar_sha256','delay_mean_ms','delay_min_ms','delay_max_ms')}),flush=True)
    finally:
        if proxy.server:await proxy.close()
        for p in procs:
            if p.poll() is None:p.terminate();p.wait(timeout=20)
async def main():
    p=argparse.ArgumentParser();p.add_argument('--accept-eula',action='store_true',required=True);p.add_argument('--label',required=True);p.add_argument('--rtts',default='0,50,100,200');p.add_argument('--repetitions',type=int,default=10);p.add_argument('--legacy',type=int,default=20);p.add_argument('--window',type=int,default=5);p.add_argument('--age-cap',type=int,default=8);args=p.parse_args()
    if not re.fullmatch('[A-Za-z0-9_-]+',args.label):p.error('invalid label')
    for rtt in map(int,args.rtts.split(',')):await scenario(args,rtt)
if __name__=='__main__':asyncio.run(main())
