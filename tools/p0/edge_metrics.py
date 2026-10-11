"""Join each predeclared boundary attempt to its exact server input and acknowledgment."""
import collections
import json
import pathlib
import re
import statistics

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/p0/evidence/combo-boundary'

def fields(line):
    return dict(re.findall(r'(\w+)=([^ ]+)', line))

def allowance(age, sample):
    import math
    ping, cap, jitter = (int(sample[k]) for k in ('ping', 'cap', 'jitter'))
    return 0 if age <= 0 or cap <= 0 or ping <= 0 else max(0, min(age, cap, math.ceil(ping / 100) + min(jitter, 4)))

def outcome(acks, sent):
    if not sent:
        return 'OTHER_NOT_SENT'
    if 'START' in acks:
        return 'ACCEPTED'
    failures = [a for a in acks if a not in ('OK', 'MOVE_READY')]
    return failures[-1] if failures else 'OTHER_NO_FINAL_ACK'

def stats(values):
    return dict(mean=statistics.mean(values), min=min(values), max=max(values)) if values else None

def read_run(entry):
    folder = ROOT / 'build/p0/runs' / entry['label']
    client = folder.joinpath('P0A/process.log').read_text(encoding='utf-8', errors='replace').splitlines()
    server = folder.joinpath('server/process.log').read_text(encoding='utf-8', errors='replace').splitlines()
    trials, plan, packets = {}, None, []
    for line in client:
        d = fields(line)
        if 'P0_EDGE_PLAN ' in line:
            plan = d
        for marker, key in (('P0_EDGE_BEGIN ', 'begin'), ('P0_EDGE_ORIGIN ', 'origin'), ('P0_EDGE_PRESS ', 'press'), ('P0_EDGE_FINISH ', 'finish')):
            if marker in line:
                trials.setdefault(int(d['trial']), {})[key] = d
        if 'CW_VIEW packet=' in line:
            packets.append(d)
    inputs, acks, samples, starts = {}, {}, {}, {}
    for line in server:
        d = fields(line)
        if 'CW INPUT player=P0A ' in line:
            inputs[int(d['seq'])] = d
        if 'CW ACK player=P0A ' in line:
            acks.setdefault(int(d['seq']), []).append(d['result'])
        if 'P0_COMP_SAMPLE tick=' in line:
            samples[int(d['tick'])] = d
        if 'CW START player=P0A ' in line:
            starts[int(d['id'])] = d
    if plan is None:
        raise RuntimeError('No plan: ' + entry['label'])
    rows = []
    for trial in range(1, 46):
        t = trials.get(trial, {})
        p = t.get('press')
        row = {'trial': trial, 'k': int(plan['until']) - 7 + (trial - 1) % 9,
               'fixture_reason': t.get('finish', {}).get('reason', 'UNFINISHED')}
        seq = int(p['seq']) if p else None
        results = acks.get(seq, [])
        row.update(sequence=seq, ack_results=results, outcome=outcome(results, p is not None))
        if p:
            i = inputs.get(seq)
            if i is None:
                raise RuntimeError('Missing exact input: ' + entry['label'] + '/' + str(seq))
            consume, receive, start = int(i['consumedTick']), int(i['receivedTick']), int(p['start'])
            sample = samples.get(consume)
            if sample is None:
                raise RuntimeError('Missing consume-tick sample: ' + entry['label'] + '/' + str(seq))
            age = max(0, receive - int(i['effectiveStamp']))
            row.update(client_action_age=float(p['clientAge']), instance_id=int(p['instance']),
                       origin_start_tick=start, stamp=int(i['stamp']), effective_stamp=int(i['effectiveStamp']),
                       stamped_action_age=int(i['effectiveStamp'])-start,
                       received_tick=receive, consumed_tick=consume, receipt_age_ticks=age,
                       server_ping_ms=int(sample['ping']), compensation_ticks_reconstructed=allowance(age, sample),
                       action_age_at_arrival=receive-start, action_age_at_consume=consume-start, gate=i['gate'])
            if int(i['origin']) != row['instance_id'] or starts[row['instance_id']]['action'] != 'light_1':
                raise RuntimeError('Origin mismatch')
            row['valid_age_sample'] = row['k'] <= row['client_action_age'] < row['k'] + 1
        else:
            row['valid_age_sample'] = False
        rows.append(row)
    begin = min(int(t['begin']['nano']) for t in trials.values() if 'begin' in t)
    end = max(int(t['finish']['nano']) for t in trials.values() if 'finish' in t)
    packets = [p for p in packets if begin <= int(p['nano']) <= end]
    runtime = json.loads(folder.joinpath('runtime-snapshot.json').read_text())
    jar = 'baseline-542ebab.jar' if entry['variant'] == 'A' else 'comp-candidate.jar'
    frozen = [r for r in runtime if pathlib.Path(r['source']).name == jar]
    if len(frozen) != 1 or frozen[0]['sha256'].lower() != entry['jar_sha256'].lower():
        raise RuntimeError('Frozen jar mismatch')
    process = json.loads(folder.joinpath('summary.json').read_text())
    return {**entry, 'until': int(plan['until']), 'requests': rows,
            'outcomes': dict(collections.Counter(r['outcome'] for r in rows)),
            'invalid_age_or_unsent': sum(not r['valid_age_sample'] for r in rows),
            's08': len(packets), 'packet_rotation_changes': sum(abs(float(p['yawDelta'])) > 1e-6 or abs(float(p['pitchDelta'])) > 1e-6 for p in packets),
            'runtime_sha256': runtime, 'process': {k: process[k] for k in ('complete', 'server_exit', 'client_class_loads')}}

def main():
    manifest = json.loads(ROOT.joinpath('build/p0/edge-manifest.json').read_text())
    if len(manifest['runs']) != 8:
        raise RuntimeError('Retain incomplete matrix; do not finalize')
    runs = [read_run(e) for e in manifest['runs']]
    aggregate = []
    boundaries = {}
    for rtt in (0, 100):
        for variant in 'AB':
            subset = [r for run in runs if run['rtt_ms'] == rtt and run['variant'] == variant for r in run['requests']]
            groups = []
            for k in range(4, 13):
                rows = [r for r in subset if r['k'] == k]
                counts = dict(collections.Counter(r['outcome'] for r in rows))
                groups.append({'rtt_ms': rtt, 'variant': variant, 'k': k, 'attempts': len(rows),
                               'accepted': counts.get('ACCEPTED', 0), 'outcomes': counts,
                               'invalid_age_or_unsent': sum(not r['valid_age_sample'] for r in rows),
                               **{key: stats([r[key] for r in rows if key in r]) for key in ('receipt_age_ticks', 'server_ping_ms', 'compensation_ticks_reconstructed', 'action_age_at_arrival', 'action_age_at_consume')}})
            aggregate.extend(groups)
            boundaries[f'{rtt}-{variant}'] = {'last_all_accepted_k': max((g['k'] for g in groups if g['accepted'] == 10), default=None),
                                           'last_any_accepted_k': max((g['k'] for g in groups if g['accepted']), default=None),
                                           'accepted_at_or_after_until': sum(g['accepted'] for g in groups if g['k'] >= 11)}
    fixture_hashes = {r['sha256'] for run in runs for r in run['runtime_sha256'] if pathlib.Path(r['snapshot']).name == 'p0-validation.jar'}
    if len(fixture_hashes) != 1:
        raise RuntimeError('Fixture changed')
    shifts = {str(rtt): {key: boundaries[f'{rtt}-B'][key]-boundaries[f'{rtt}-A'][key]
                       if boundaries[f'{rtt}-B'][key] is not None and boundaries[f'{rtt}-A'][key] is not None else None
                       for key in ('last_all_accepted_k', 'last_any_accepted_k')} for rtt in (0,100)}
    failed_startups=[]
    for failed in manifest.get('failed_startups',[]):
        driver=ROOT.joinpath('build/p0',failed['label']+'-driver.log').read_text(encoding='utf-8',errors='replace')
        failed_startups.append({**failed,'reason': 'First client login timeout' if 'First client login timeout' in driver else 'See retained local log',
                                'measurement_started': False, 'discarded_or_overwritten': False})
    result = {'baseline': '542ebab', 'candidate': '02b68d8', 'candidate_production_behavior': '81b4b86',
              'candidate_production_build': '17b0f2d', 'until': 11, 'manifest': manifest,
              'boundary_definition': 'Report both last k with 10/10 accepted and last k with any accepted; retain all mixed/invalid samples.',
              'compensation_note': 'Reconstructed from receipt/effective stamps and read-only same-consume-tick ping/config; no production trace branch added.',
              'predeclared_criteria': {'same_boundary': 'Compensation not effective', 'B_later_than_A_over_2_ticks': 'Exceeds design',
                                       'k_at_or_after_until': 'Both versions must reject; unsent attempts do not establish rejection',
                                       'no_tuning_or_selected_reruns': True},
              'boundary_shifts_B_minus_A': shifts, 'failed_startups': failed_startups,
              'validation_jar_sha256': fixture_hashes.pop(), 'boundaries': boundaries, 'aggregate': aggregate, 'runs': runs}
    OUT.mkdir(parents=True, exist_ok=True)
    OUT.joinpath('summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'boundaries': boundaries, 'runs': [{'label': r['label'], 'outcomes': r['outcomes'], 'invalid': r['invalid_age_or_unsent']} for r in runs]}, indent=2))

if __name__ == '__main__':
    main()
