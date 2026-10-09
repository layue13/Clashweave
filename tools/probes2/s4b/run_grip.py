"""Fresh dedicated server/client, native framebuffers, no world reuse."""
import argparse, hashlib, json, shutil, subprocess, time
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--label',required=True)
    p.add_argument('--accept-eula',action='store_true',required=True)
    a=p.parse_args()
    if not a.label.replace('-','').replace('_','').isalnum(): p.error('invalid label')
    root=Path(__file__).resolve().parents[3]
    work=root/'run/probes2'/a.label
    evidence=root/'docs/probes/round2/evidence'/a.label
    if work.exists() or evidence.exists(): p.error('choose a fresh label')
    jar=root/'build/probes2/s4b/s4b-experiment.jar'
    processes=[]
    def launch(task,folder):
        folder.mkdir(parents=True)
        (folder/'mods').mkdir()
        shutil.copy2(jar,folder/'mods'/jar.name)
        j=json.loads((root/'build/probes2/s4b'/f'{task}.json').read_text())
        vm=[v for v in j['jvmArgs'] if not v.startswith('-Xmx') and not v.startswith('-Xms')]
        args=list(j['args'])
        if task=='runServer':
            (folder/'eula.txt').write_text('eula=true\n')
            (folder/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25574\nonline-mode=false\nlevel-type=FLAT\ngenerate-structures=false\nspawn-protection=0\ndifficulty=0\n')
            args.append('nogui');vm.append('-verbose:class')
        else:
            (folder/'options.txt').write_text('soundCategory_master:0.0\nsoundCategory_music:0.0\nfullscreen:false\npauseOnLostFocus:false\n')
            args.extend(['--username','GripProbe','--width','1280','--height','900'])
        command=[j['java'],*vm,'-Xmx1G','-cp',j['classpath'],j['main'],*args]
        stream=(folder/'runtime.log').open('w',encoding='utf8')
        proc=subprocess.Popen(command,cwd=folder,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT)
        processes.append((proc,stream,command))
        return proc
    try:
        server=launch('runServer',work/'server')
        deadline=time.time()+100
        while time.time()<deadline:
            text=(work/'server/runtime.log').read_text(errors='replace')
            if 'Done (' in text: break
            if server.poll()!=None: raise RuntimeError('server exited')
            time.sleep(.5)
        else: raise RuntimeError('server startup timeout')
        server.stdin.write(b'time set 6000\n');server.stdin.flush()
        client=launch('runClient',work/'client')
        code=client.wait(timeout=120)
        server.stdin.write(b'stop\n');server.stdin.flush()
        server.wait(timeout=30)
        text=(work/'client/runtime.log').read_text(errors='replace')
        if code!=0 or 'COMPLETE screenshots=4' not in text: raise RuntimeError('client incomplete')
        shots=list((work/'client/screenshots').glob('s4b-grip-*.png'))
        if len(shots)!=4: raise RuntimeError('missing native screenshots')
        evidence.mkdir(parents=True)
        for s in shots: shutil.copy2(s,evidence/s.name)
        for side in ['client','server']:
            raw=(work/side/'runtime.log').read_text(errors='replace')
            (evidence/(side+'.log')).write_text('\n'.join(v for v in raw.splitlines() if not v.startswith('[Loaded '))+'\n',encoding='utf8')
        raw=(work/'server/runtime.log').read_text(errors='replace')
        loads=[v for v in raw.splitlines() if v.startswith('[Loaded ') and any(n in v for n in ['net.minecraft.client.','org.lwjgl.opengl.','experiments.grip.GripClient','experiments.grip.GripRendering'])]
        result={'server_exit':server.returncode,'client_exit':code,'screenshots':len(shots),'dedicated_client_class_loads':len(loads),'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'commands':[x[2] for x in processes]}
        (evidence/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps({k:v for k,v in result.items() if k!='commands'}))
        print('\n'.join(v for v in text.splitlines() if 'CW2 S4B' in v))
    finally:
        for proc,stream,_ in processes:
            if proc.poll()==None:
                proc.terminate();proc.wait(timeout=30)
            stream.close()
if __name__=='__main__': main()
