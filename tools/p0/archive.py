"""Copy reproducible logs and framebuffer evidence, excluding worlds and generated runtimes."""
import argparse
import hashlib
import pathlib
import shutil

ROOT = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('labels', nargs='+')
args = parser.parse_args()
for label in args.labels:
    source = ROOT / 'build/p0/runs' / label
    target = ROOT / 'docs/p0/evidence' / label
    target.mkdir(parents=True, exist_ok=True)
    for name in ['command.json', 'commands.json', 'summary.json', 'metrics.json', 'per-contact.json',
                 'requests.json', 'feedback.json', 'alignments.json', 'engagement-metrics.json', 'server.log', 'latency.log']:
        path = source / name
        if path.exists():
            shutil.copyfile(path, target / name)
    for role in ['server', 'P0A', 'P0B']:
        path = source / role
        if not path.exists():
            continue
        (target / role).mkdir(exist_ok=True)
        shutil.copyfile(path / 'process.log', target / role / 'process.log')
        screenshots = path / 'screenshots'
        if screenshots.exists():
            (target / role / 'screenshots').mkdir(exist_ok=True)
            for image in screenshots.glob('p0-*.png'):
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
