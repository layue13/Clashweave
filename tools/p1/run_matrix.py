"""Fresh Windows server/two-client sessions; retains every labelled run, no retries."""
import pathlib
import socket
import subprocess
import sys
import time
ROOT = pathlib.Path(__file__).resolve().parents[2]
# The preliminary native and player runs are retained; release runs exercise the final ordered-effect code.
SCENARIOS = [
    ['--launcher', 'enderman', '--label', 'p1-night-control'],
    ['--launcher', 'extras', '--label', 'p1-guards-multi'],
    ['--launcher', 'player', '--label', 'p1-player-release-0'],
    ['--launcher', 'player', '--rtt', '100', '--label', 'p1-player-release-100'],
    ['--souls-view', '--label', 'p1-regression-0'],
    ['--souls-view', '--rtt', '100', '--label', 'p1-regression-100'],
    ['--launcher', 'control', '--label', 'p1-enderman-helmet-control'],
]
def busy():
    with socket.socket() as probe:
        probe.settimeout(.2)
        return probe.connect_ex(('127.0.0.1', 25580)) == 0
while busy():
    time.sleep(1)
time.sleep(5)
for scenario in SCENARIOS:
    label = scenario[scenario.index('--label') + 1]
    command = [sys.executable, str(ROOT/'tools/p0/dual.py'), *scenario,
               '--snapshot-coremod', '--mod-snapshot', 'build/p1/candidate-release.jar', '--accept-eula']
    print('BEGIN', label, flush=True)
    with (ROOT/'build/p1'/f'{label}.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    print('RESULT', label, result.returncode, flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
    time.sleep(5)
