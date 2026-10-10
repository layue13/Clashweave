"""Copy reproducible logs and framebuffer evidence, excluding worlds and generated runtimes."""
import argparse
import hashlib
import pathlib
import shutil
import re
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[2]
def excerpt(path):
    lines=path.read_text(encoding='utf-8',errors='replace').splitlines()
    counts=Counter();kept=[]
    for line in lines:
        match=re.search(r'(P0_[A-Z_]+|LATENCY_[A-Z_]+|CW_[A-Z_]+|CAMERA_[A-Z_]+| CW [A-Z]+|ERROR|Exception|BUILD SUCCESSFUL)',line)
        if not match: continue
        key=match[0];counts[key]+=1
        view_change = 'CW_VIEW ' in line and any(float(value) != 0 for value in re.findall(r'(?:yawDelta|pitchDelta)=([-0-9.eE]+)',line))
        if view_change or 'protected=true' in line or counts[key]<=12 or 'ASSERT' in line or re.search(r'corrected=[1-9][0-9]*',line) or 'inject=' in line or 'COMPLETE' in line or 'success=false' in line or '=false' in line and 'ASSERT' in line:
            kept.append(line)
    return '# Log excerpt; complete raw log retained locally under build/p0/runs.\n# Original lines: '+str(len(lines))+'\n# Marker counts: '+str(dict(counts))+'\n'+ '\n'.join(kept)+'\n'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('labels', nargs='+')
    args = parser.parse_args()
    for label in args.labels:
        source = ROOT / 'build/p0/runs' / label
        target = ROOT / 'docs/p0/evidence' / label
        target.mkdir(parents=True, exist_ok=True)
        for name in ['command.json', 'runtime-snapshot.json', 'commands.json', 'summary.json', 'metrics.json', 'events.json', 'semantic-metrics.json', 'per-contact.json',
                     'requests.json', 'view-metrics.json', 'facing-view-metrics.json', 'lock-support-metrics.json', 'composition-metrics.json', 'camera-probe-metrics.json', 'feedback.json', 'alignments.json', 'engagement-metrics.json', 'server.log', 'latency.log']:
            path = source / name
            if path.exists():
                (target/name).write_text(excerpt(path),encoding='utf-8') if path.suffix=='.log' else shutil.copyfile(path,target/name)
        for role in ['server', 'P0A', 'P0B']:
            path = source / role
            if not path.exists():
                continue
            (target / role).mkdir(exist_ok=True)
            (target/role/'process.log').write_text(excerpt(path/'process.log'),encoding='utf-8')
            screenshots = path / 'screenshots'
            if screenshots.exists():
                (target / role / 'screenshots').mkdir(exist_ok=True)
                for image in screenshots.glob('p0-*.png'):
                    if image.name == 'p0-composition-.png': continue
                    shutil.copyfile(image, target / role / 'screenshots' / image.name)
        entries = []
        for path in sorted(target.rglob('*')):
            if path.is_file() and path.name != 'SHA256.txt':
                data = path.read_bytes()
                if path.suffix != '.png':
                    data = data.replace(b'\r\n', b'\n')
                    path.write_bytes(data)
                entries.append(hashlib.sha256(data).hexdigest() + '  ' + path.relative_to(target).as_posix())
        (target / 'SHA256.txt').write_text('\n'.join(entries) + '\n', encoding='utf-8')
        print(label, len(entries), 'evidence files')

if __name__ == "__main__":
    main()
