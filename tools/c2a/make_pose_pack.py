"""Builds tools/c2a/pose-pack/pack.json: the katana's actions and style under the posetest namespace, with every edge
free of the confirmed-hit requirement, so a pose-capture run can play the whole chain (light_2, light_3, heavy, ...)
without a target in reach. Test content only; never shipped. Run from the repository root."""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parents[2]
base = root / 'src/main/resources/assets/clashweave/content/katana'
actions = json.loads((base / 'actions.json').read_text(encoding='utf-8'))['actions']
style = json.loads((base / 'style.json').read_text(encoding='utf-8'))['styles'][0]


def ns(text):
    return text.replace('katana:', 'posetest:', 1) if isinstance(text, str) and text.startswith('katana:') else text


def renamed(node):
    if isinstance(node, dict):
        return {k: renamed(v) for k, v in node.items()}
    if isinstance(node, list):
        return [renamed(v) for v in node]
    return ns(node)


out_actions = []
for action in renamed(actions):
    for edge in action.get('edges', []):
        edge['hit'] = False
        edge.pop('damageHit', None)
    out_actions.append(action)
pack = {
    'schemaVersion': 1,
    'namespace': 'posetest',
    'weapons': [{'id': 'posetest:katana', 'style': 'posetest:default', 'resource': 'katana:default',
                 'animation': 'katana:track', 'damageMultiplier': 1, 'poiseMultiplier': 1, 'costMultiplier': 1,
                 'saSlots': [], 'effectSlots': []}],
    'styles': [renamed(style)],
    'actions': out_actions,
}
target = root / 'tools/c2a/pose-pack/pack.json'
target.write_text(json.dumps(pack, indent=2) + '\n', encoding='utf-8')
print('wrote', target)
