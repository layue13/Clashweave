"""P1-b: per-cycle statistics from a --chase run's server log (CW_EFFECT lines) and the client's P1B lines."""
import re, sys, json, pathlib

folder = pathlib.Path(sys.argv[1])
server = (folder / 'server/process.log').read_text(encoding='utf-8', errors='replace').splitlines()
client = (folder / 'P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines()
cycles, current = [], None
for line in server:
    m = re.search(r'CW_EFFECT (LAUNCH|SLAM) target=\d+ accepted=(\w+) .*?tick=(\d+) chain=(\d+)', line)
    if not m: continue
    kind, accepted, tick, chain = m.group(1), m.group(2) == 'true', int(m.group(3)), int(m.group(4))
    vy = float(re.search(r'vy=(-?[\d.]+)', line).group(1))
    prot = int(re.search(r'protectionUntil=(\d+)', line).group(1))
    if kind == 'LAUNCH' and accepted and chain == 1:
        current = {'start': tick, 'launches': [], 'slam': None}
        cycles.append(current)
    if current is None: continue
    if kind == 'LAUNCH': current['launches'].append({'tick': tick, 'accepted': accepted, 'chain': chain, 'vy': vy, 'protectedUntil': prot})
    else: current['slam'] = {'tick': tick, 'accepted': accepted}
actions = [re.search(r'action=(\w+)', l).group(1) for l in client if 'P1B_ACTION' in l]
done = any('P1B_COMPLETE' in l for l in client)
n = len(cycles)
chains = [sum(1 for l in c['launches'] if l['accepted']) for c in cycles]
slammed = sum(1 for c in cycles if c['slam'] and c['slam']['accepted'])
over = sum(1 for c in cycles for l in c['launches'] if l['accepted'] and l['tick'] < l['protectedUntil'] - 40 and False)
summary = {
    'cyclesWithLauncher': n,
    'complete': done,
    'acceptedLaunchesPerCycle': {str(k): chains.count(k) for k in sorted(set(chains))},
    'maxChain': max(chains) if chains else 0,
    'slamAccepted': slammed,
    'slamMissing': n - slammed,
    'rejectedLaunches': sum(1 for c in cycles for l in c['launches'] if not l['accepted']),
    'firstLaunchVy': sorted({round(c['launches'][0]['vy'], 3) for c in cycles}),
    'decayedVy': sorted({round(l['vy'], 4) for c in cycles for l in c['launches'] if l['chain'] > 1}),
    'actionCounts': {a: actions.count(a) for a in sorted(set(actions))},
}
print(json.dumps(summary, indent=1))
(folder / 'p1b-stats.json').write_text(json.dumps(summary, indent=1), encoding='utf-8')
