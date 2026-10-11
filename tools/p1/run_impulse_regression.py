"""Validate the corrected finite-credit lifecycle and compare original movement fixtures."""
import pathlib
import subprocess
import sys
import time
ROOT=pathlib.Path(__file__).resolve().parents[2]
CASES=[
 ('p1-fixed-player-0','candidate-impulse-fix.jar',['--launcher','player']),
 ('p1-fixed-player-100','candidate-impulse-fix.jar',['--launcher','player','--launcher-audit','--rtt','100']),
 ('p1-fixed-regression-0','candidate-impulse-fix.jar',['--souls-view']),
 ('p1-fixed-regression-100','candidate-impulse-fix.jar',['--souls-view','--rtt','100']),
 ('p1-move-base-0','baseline-ce32fce.jar',['--movement']),
 ('p1-move-fixed-0','candidate-impulse-fix.jar',['--movement']),
 ('p1-move-base-100','baseline-ce32fce.jar',['--movement','--rtt','100']),
 ('p1-move-fixed-100','candidate-impulse-fix.jar',['--movement','--rtt','100']),
 ('p1-low-base','baseline-ce32fce.jar',['--movement','--low-tps']),
 ('p1-low-fixed','candidate-impulse-fix.jar',['--movement','--low-tps']),
]
for label,jar,args in CASES:
    print('BEGIN',label,flush=True)
    command=[sys.executable,str(ROOT/'tools/p0/dual.py'),*args,'--snapshot-coremod','--mod-snapshot',f'build/p1/{jar}','--label',label,'--accept-eula']
    with (ROOT/'build/p1'/f'{label}.log').open('w',encoding='utf-8') as out:
        result=subprocess.run(command,cwd=ROOT,stdout=out,stderr=subprocess.STDOUT)
    print('RESULT',label,result.returncode,flush=True)
    if result.returncode:raise SystemExit(result.returncode)
    time.sleep(5)
