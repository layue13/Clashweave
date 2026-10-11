"""Summarize retained process logs; no raw logs or per-frame data enter the repository."""
import argparse
import collections
import hashlib
import json
import pathlib
import re
import xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[2]

def fields(line):
    return dict(re.findall(r'(\w+)=([^\s]+)', line))
def number(data, key, default=0):
    return float(data.get(key, default))
def nano(line):
    return int(fields(line).get('nano', 0))
def intervals(lines, begin, end):
    opened = None
    result = []
    for line in lines:
        if begin in line:
            opened = nano(line)
        elif end in line and opened is not None:
            result.append((opened, nano(line)))
            opened = None
    return result

def summarize(label):
    folder = ROOT/'build/p0/runs'/label
    lines = (folder/'server/process.log').read_text(encoding='utf-8',errors='replace').splitlines()
    result = {'label': label, 'launcher_cases': [], 'clients': {}, 'rejections': dict(collections.Counter(
        re.search(r'result=(REJECT:[^\s]+)', x).group(1) for x in lines if re.search(r'result=(REJECT:[^\s]+)',x))),
        'movement_corrections': sum('CW MOVE_CORRECT' in x for x in lines),
        'dedicated': json.loads((folder/'summary.json').read_text())}
    opened = None
    cases = []
    for line in lines:
        if 'P1_BEGIN ' in line:
            opened = fields(line); cases = []
        elif 'P1_RESULT ' in line:
            row = fields(line)
            row['effects'] = [fields(x) for x in cases if 'CW_EFFECT ' in x]
            row['commits'] = [fields(x) for x in cases if 'CW COMMIT ' in x]
            row['air_budget_starts'] = sum('P1_AIR_BUDGET' in x for x in cases)
            row['request_rejections'] = dict(collections.Counter(
                re.search(r'result=(REJECT:[^\s]+)', x).group(1)
                for x in cases if re.search(r'result=(REJECT:[^\s]+)', x)))
            if opened: row['begin'] = opened
            result['launcher_cases'].append(row); opened = None
        elif opened is not None:
            cases.append(line)
    result['movement']=[fields(x) for x in lines if 'P0_MOVEMENT ' in x]
    result['runtime_jars']=json.loads((folder/'runtime-snapshot.json').read_text())
    result['performance_last']=next((fields(x) for x in reversed(lines) if 'P0_PERF samples=' in x),None)
    # Commit invariants are computed from actual frozen/commit values, not expected fixture ticks.
    commits = [fields(x) for x in lines if 'CW COMMIT ' in x]
    result['commit_invariants'] = {
        'player_dual_deadline_violations': sum(number(x,'commitTick')<number(x,'hitTick')+3 or number(x,'cutoff')<number(x,'frozen')+150_000_000 for x in commits if x.get('targetPlayer')=='true'),
        'mob_extra_tick_max': max((number(x,'commitTick')-number(x,'hitTick') for x in commits if x.get('targetPlayer')=='false'),default=None),
        'player_commits': sum(x.get('targetPlayer')=='true' for x in commits),
        'mob_commits': sum(x.get('targetPlayer')=='false' for x in commits)}
    a_lines=(folder/'P0A/process.log').read_text(encoding='utf-8',errors='replace').splitlines()
    shared_periods=intervals(a_lines,'P0_VIEW_SEQUENCE begin=', 'P0_VIEW_RESULT')
    for role in ('P0A','P0B'):
        text = (folder/role/'process.log').read_text(encoding='utf-8',errors='replace').splitlines()
        periods = intervals(text,'P1_CLIENT_BEGIN','P1_CLIENT_END') or shared_periods
        inside = lambda x: any(a<=nano(x)<=b for a,b in periods)
        packets = [fields(x) for x in text if 'CW_VIEW packet=' in x and inside(x)]
        result['clients'][role] = {
            'measured_intervals':len(periods), 's08':len(packets) if periods else None,
            'packet_yaw_delta_max': max((abs(number(x,'yawDelta')) for x in packets),default=0) if periods else None,
            'packet_pitch_delta_max':max((abs(number(x,'pitchDelta')) for x in packets),default=0) if periods else None,
            's12': sum('P1_PACKET type=S12' in x and inside(x) for x in text) if periods else None,
            's12_scope':'all observed entities, not exclusive to the launched player',
            'combo_complete': [fields(x) for x in text if 'P0_VIEW COMPLETE' in x],
            'combo_results': [fields(x) for x in text if 'P0_VIEW_RESULT ' in x],
        }
    result['dedicated'].pop('performance',None)
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('labels',nargs='+');parser.add_argument('--output',required=True);args=parser.parse_args()
    document={'runs':[summarize(label) for label in args.labels]}
    document['junit']={'tests':0,'failures':0,'errors':0,'skipped':0}
    for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        node=ET.parse(p).getroot()
        for key in document['junit']:document['junit'][key]+=int(node.get(key,0))
    document['frozen_jars']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'build/p1').glob('*.jar')}
    output=ROOT/args.output;output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(document,ensure_ascii=False,indent=2),encoding='utf-8')
    print('saved',output)
if __name__=='__main__':main()
