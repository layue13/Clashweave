"""Real dedicated server plus two Minecraft clients, isolated worlds and process logs."""
import argparse
import json
import pathlib
import shutil
import hashlib
import subprocess
import time
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--label', required=True)
parser.add_argument('--accept-eula', action='store_true')
parser.add_argument('--render-only', action='store_true')
parser.add_argument('--supplement', action='store_true')
parser.add_argument('--manual', action='store_true')
parser.add_argument('--lock-support', action='store_true')
parser.add_argument('--follow-only', action='store_true')
parser.add_argument('--movement', action='store_true')
parser.add_argument('--low-tps', action='store_true')
parser.add_argument('--engagement', action='store_true')
parser.add_argument('--view-test', action='store_true')
parser.add_argument('--lock-test', action='store_true')
parser.add_argument('--sweep-test', action='store_true')
parser.add_argument('--fast-turn', action='store_true')
parser.add_argument('--correction-test', action='store_true')
parser.add_argument('--rtt', type=int, default=0)
args = parser.parse_args()
if args.follow_only: args.lock_support=True
if not args.accept_eula:
    raise SystemExit('Explicit EULA acceptance required')
folder = ROOT / 'build/p0/runs' / args.label
folder.mkdir(parents=True, exist_ok=False)
processes = []
logs = []
commands = {}
runtime=folder/'runtime';runtime.mkdir()
snapshots={}
def snapshot(source):
    source=pathlib.Path(source)
    if source not in snapshots:
        destination=runtime/source.name
        shutil.copyfile(source,destination)
        snapshots[source]=destination
    return snapshots[source]
validation=snapshot(ROOT/'build/p0/p0-validation.jar')


def spawn(role):
    directory = folder / role
    directory.mkdir()
    (directory/'mods').mkdir()
    (directory/'config').mkdir()
    shutil.copyfile(validation,directory/'mods/p0-validation.jar')
    server = role == 'server'
    launch = json.loads((ROOT/'build/p0'/('runServer.json' if server else 'runClient.json')).read_text(encoding='utf-8'))
    command = [launch['java'], '-Xms256M', '-Xmx1G', '-Dfile.encoding=UTF-8',
               '-Dfml.ignoreInvalidMinecraftCertificates=true', '-Dclashweave.trace=true', '-Dcw.p0.network=true']
    if args.view_test:
        command += ['-Dcw.p0.viewTest=true']
    if args.sweep_test:
        command += ['-Dcw.p0.sweepTest=true']
    if args.fast_turn:
        command += ['-Dcw.p0.fastTurn=true']
    if args.correction_test:
        command += ['-Dcw.p0.correctionTest=true']
    if args.lock_test:
        command += ['-Dcw.p0.lockTest=true']
    if args.render_only:
        command += ['-Dcw.p0.renderOnly=true']
    if args.supplement:
        command += ['-Dcw.p0.supplement=true']
    if args.follow_only:
        command += ['-Dcw.p0.followOnly=true']
    if args.lock_support:
        command += ['-Dcw.p0.lockSupport=true']
    if args.manual:
        command += ['-Dcw.p0.manual=true']
    if args.movement:
        command += ['-Dcw.p0.movement=true', '-Dcw.p0.renderOnly=true']
    if args.low_tps:
        command += ['-Dcw.p0.lowTps=true']
    if args.engagement:
        command += ['-Dcw.p0.engagement=true']
    if server:
        command += ['-verbose:class']
        (directory/'eula.txt').write_text('eula=true\n',encoding='utf-8')
        (directory/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25580\nonline-mode=false\nlevel-type=FLAT\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nmax-players=4\n',encoding='utf-8')
    else:
        command += ['-Dcw.p0.port='+('25581' if args.rtt else '25580')]
        command += ['-Djava.library.path='+str(ROOT/'run/natives/lwjgl2')]
        (directory/'options.txt').write_text('pauseOnLostFocus:false\nrenderDistance:3\nmaxFps:60\nmusic:0.0\nsound:1.0\nfancyGraphics:false\n',encoding='utf-8')
    entries=[]
    for entry in launch['classpath'].split(';'):
        path=pathlib.Path(entry)
        entries.append(str(snapshot(path)) if path.is_relative_to(ROOT/'build') and path.suffix=='.jar' else entry)
    command += ['-cp',';'.join(entries),launch['main']]
    command += ['nogui'] if server else ['--username',role,'--width','960','--height','540','--gameDir',str(directory)]
    commands[role] = command
    log = (directory/'process.log').open('w',encoding='utf-8')
    logs.append(log)
    process = subprocess.Popen(command,cwd=directory,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
    processes.append(process)
    return process


try:
    server = spawn('server')
    deadline = time.perf_counter()+80
    while time.perf_counter()<deadline:
        text=(folder/'server/process.log').read_text(encoding='utf-8',errors='replace')
        if 'Done (' in text: break
        if server.poll() is not None: raise RuntimeError('Server startup failed')
        time.sleep(.25)
    else: raise RuntimeError('Server startup timeout')
    proxy = None
    if args.rtt:
        proxy_log=(folder/'latency.log').open('w',encoding='utf-8')
        logs.append(proxy_log)
        proxy=subprocess.Popen([sys.executable,str(ROOT/'tools/p0/latency.py'),'--half-ms',str(args.rtt/2)],stdout=proxy_log,stderr=subprocess.STDOUT)
        processes.append(proxy)
        time.sleep(.5)
    clients = [spawn('P0A')]
    deadline = time.perf_counter()+60
    while time.perf_counter()<deadline:
        if 'P0_NETWORK login=P0A' in (folder/'server/process.log').read_text(encoding='utf-8',errors='replace'): break
        if clients[0].poll() is not None: raise RuntimeError('First client startup failed')
        time.sleep(.2)
    else: raise RuntimeError('First client login timeout')
    clients.append(spawn('P0B'))
    deadline = time.perf_counter()+(600 if args.manual else 300 if args.lock_support else 160)
    while server.poll() is None and time.perf_counter()<deadline:
        if any(client.poll() is not None for client in clients):
            server.stdin.write(b'stop\n')
            server.stdin.flush()
            server.wait(timeout=20)
            if args.manual: break
            raise RuntimeError('Client exited before scenario completed')
        time.sleep(.5)
    if server.poll() is None:
        server.stdin.write(b'stop\n')
        server.stdin.flush()
        server.wait(timeout=20)
        if not args.manual: raise RuntimeError('Scenario timeout')
    print('server_exit',server.returncode)
    time.sleep(2)
finally:
    for process in processes:
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill()
    for log in logs: log.close()
    (folder/'runtime-snapshot.json').write_text(json.dumps([{ 'source':str(source.relative_to(ROOT)), 'snapshot':str(destination.relative_to(folder)), 'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()} for source,destination in snapshots.items()],indent=2),encoding='utf-8')
    (folder/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8')
    lines=(folder/'server/process.log').read_text(encoding='utf-8',errors='replace').splitlines()
    leaks=[line for line in lines if line.startswith('[Loaded ') and ('net.minecraft.client.' in line or 'org.lwjgl.opengl.' in line or 'clashweave.client.' in line or 'validation.ClientReplay' in line)]
    summary=dict(server_exit=processes[0].returncode,client_class_loads=len(leaks),
                 complete=any('P0_NETWORK COMPLETE' in line or 'P0_SUPPLEMENT COMPLETE' in line or 'P0_MOVEMENT COMPLETE' in line or 'P0_ENGAGEMENT COMPLETE' in line or 'P0_SWEEP COMPLETE' in line for line in lines),
                 captures=[str(p.relative_to(folder)) for p in folder.rglob('p0-*.png')],
                 performance=[line for line in lines if 'P0_PERF' in line])
    (folder/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary,indent=2))
if not args.manual and (summary['server_exit'] or not summary['complete'] or summary['client_class_loads']):
    raise SystemExit(1)
