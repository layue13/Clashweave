"""Sequential, interleaved A/B sessions of the dual-client harness: no retries, no overwrites, failures recorded.

usage: run_ab.py <batch-name> <spec.json>
spec: [{"label": "...", "jar": "build/wf1c/x.jar", "args": ["--souls-view", "--rtt", "100"]}, ...]
"""
import json
import pathlib
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    batch, spec_path = sys.argv[1:3]
    specs = json.loads(pathlib.Path(spec_path).read_text(encoding='utf-8'))
    for spec in specs:
        label = spec['label']
        if (ROOT / 'build/p0/runs' / label).exists() or (ROOT / 'build/wf1c/logs' / f'{label}.log').exists():
            raise FileExistsError(f'choose a fresh label: {label}')
    (ROOT / 'build/wf1c/logs').mkdir(parents=True, exist_ok=True)
    status = ROOT / 'build/wf1c' / f'{batch}-status.json'
    results = []
    for spec in specs:
        label = spec['label']
        print('BEGIN', label, flush=True)
        begin = time.time()
        with (ROOT / 'build/wf1c/logs' / f'{label}.log').open('x', encoding='utf-8') as output:
            result = subprocess.run([sys.executable, str(ROOT / 'tools/p0/dual.py'), *spec['args'], '--snapshot-coremod',
                                     '--mod-snapshot', spec['jar'], '--label', label, '--accept-eula'],
                                    cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        results.append(dict(label=label, jar=spec['jar'], args=spec['args'], returncode=result.returncode,
                            seconds=round(time.time() - begin, 1)))
        status.write_text(json.dumps(results, indent=2), encoding='utf-8')
        print('RESULT', label, result.returncode, flush=True)


if __name__ == '__main__':
    main()
