"""Fresh local validation runtime; exact JVM commands/logs retained under build/p0/runs."""
import argparse
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--label', required=True)
parser.add_argument('--server-check', action='store_true')
parser.add_argument('--accept-eula', action='store_true')
args = parser.parse_args()
if not args.accept_eula:
    raise SystemExit('EULA must be accepted explicitly')
folder = ROOT / 'build/p0/runs' / args.label
folder.mkdir(parents=True, exist_ok=False)
(folder / 'mods').mkdir()
(folder / 'config').mkdir()
(folder / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
(folder / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=25580\nonline-mode=false\nlevel-type=FLAT\n'
    'generate-structures=false\nspawn-protection=0\nview-distance=3\nmax-players=4\n', encoding='utf-8')
import shutil
shutil.copyfile(ROOT / 'build/p0/p0-validation.jar', folder / 'mods/p0-validation.jar')
launch = json.loads((ROOT / 'build/p0/runServer.json').read_text(encoding='utf-8'))
command = [launch['java'], '-Xms256M', '-Xmx1G', '-verbose:class', '-Dfile.encoding=UTF-8',
           '-Dfml.ignoreInvalidMinecraftCertificates=true', '-Dcw.p0.serverCheck=true',
           '-cp', launch['classpath'], launch['main'], 'nogui']
(folder / 'command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
with (folder / 'server.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT, timeout=150)
lines = (folder / 'server.log').read_text(encoding='utf-8', errors='replace').splitlines()
leaks = [line for line in lines if line.startswith('[Loaded ') and
         ('net.minecraft.client.' in line or 'org.lwjgl.opengl.' in line or 'clashweave.client.' in line)]
summary = dict(exit=result.returncode, class_load_leaks=leaks,
               assertions=[line for line in lines if 'P0_ASSERT' in line],
               complete=any('P0_SERVER_CHECK COMPLETE' in line for line in lines))
(folder / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary, indent=2))
if result.returncode or leaks or not summary['complete']:
    raise SystemExit(1)
