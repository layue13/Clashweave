"""Start the real dedicated server once per deliberately broken pack and keep what its log says.

Each case mutates the valid test pack (src/test/resources/wf1test/glaive.json) in one way, drops it into a fresh
config directory and boots the production jar. The server must either refuse to start with an explicit message that
names the file and JSON path, or start; it must never hang or crash without saying why.
"""
import argparse
import json
import pathlib
import re
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = ROOT / 'src/test/resources/wf1test/glaive.json'

CASES = {
    'missing-field': lambda t: t.replace('"damage": 4, "reach": 3.6, "arc": 40, "distance": 0.5, "speed": 0.15,', '"reach": 3.6, "arc": 40, "distance": 0.5, "speed": 0.15,', 1),
    'unknown-action': lambda t: t.replace('"actions": ["wf1test:thrust", "wf1test:thrust_b", "wf1test:sweep"]', '"actions": ["wf1test:thrust", "wf1test:thrust_b", "wf1test:sweep", "wf1test:ghost"]', 1),
    'misspelled-entry-field': lambda t: t.replace('"ready": { "LIGHT"', '"reedy": { "LIGHT"', 1),
    'duplicate-id': lambda t: t.replace('"id": "wf1test:thrust_b",', '"id": "wf1test:thrust",', 1),
    'invalid-json': lambda t: t.replace('"actions": [', '"actions": [,', 1),
    'unknown-edge-target': lambda t: t.replace('"target": "wf1test:sweep", "from": 6', '"target": "wf1test:nowhere", "from": 6', 1),
}


def run(label, mutate, jar, timeout):
    folder = ROOT / 'build/wf1c/bad' / label
    folder.mkdir(parents=True)
    (folder / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    (folder / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25590\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nview-distance=3\n', encoding='utf-8')
    target = folder / 'config/clashweave/content/wf1test'
    target.mkdir(parents=True)
    (target / 'glaive.json').write_text(mutate(PACK.read_text(encoding='utf-8')), encoding='utf-8')
    launch = json.loads((ROOT / 'build/p0/runServer.json').read_text(encoding='utf-8'))
    classpath = []
    for entry in launch['classpath'].split(';'):
        classpath.append(str(ROOT / jar) if pathlib.Path(entry).name == 'clashweave-0.1.0-dev-dev.jar' else entry)
    command = [launch['java'], '-Xms256M', '-Xmx1G', '-Dfile.encoding=UTF-8', '-Dfml.ignoreInvalidMinecraftCertificates=true',
               '-Dfml.coreMods.load=com.layue13.clashweave.coremod.CameraLoadingPlugin',
               '-cp', ';'.join(classpath), launch['main'], 'nogui']
    log = (folder / 'process.log').open('w', encoding='utf-8')
    start = time.perf_counter()
    process = subprocess.Popen(command, cwd=folder, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
    outcome = 'TIMEOUT'
    while time.perf_counter() - start < timeout:
        if process.poll() is not None:
            outcome = 'EXITED'
            break
        text = (folder / 'process.log').read_text(encoding='utf-8', errors='replace')
        if 'Done (' in text:
            outcome = 'STARTED'
            process.stdin.write(b'stop\n')
            process.stdin.flush()
            process.wait(timeout=30)
            break
        time.sleep(.5)
    if process.poll() is None:
        process.kill()
    log.close()
    elapsed = time.perf_counter() - start
    lines = (folder / 'process.log').read_text(encoding='utf-8', errors='replace').splitlines()
    errors = [re.sub(r'^\[[^\]]*\] \[[^\]]*\] \[[^\]]*\]: ', '', x) for x in lines if 'Clashweave content error' in x]
    refusal = [re.sub(r'^\[[^\]]*\] \[[^\]]*\] \[[^\]]*\]: ', '', x) for x in lines if 'Clashweave refuses to start' in x]
    return dict(label=label, outcome=outcome, exit=process.returncode, seconds=round(elapsed, 1),
                crash_report=any('---- Minecraft Crash Report ----' in x for x in lines),
                content_errors=errors, refusal=refusal[:1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', required=True)
    parser.add_argument('--prefix', required=True)
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--cases', nargs='*', default=list(CASES))
    args = parser.parse_args()
    results = [run(f'{args.prefix}-{name}', CASES[name], args.jar, args.timeout) for name in args.cases]
    out = ROOT / f'build/wf1c/bad-{args.prefix}.json'
    out.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps(results, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
