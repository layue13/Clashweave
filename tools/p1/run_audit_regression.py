"""Fresh sessions against the frozen prior jar and the review-fix jar; no retry or overwrites."""
import pathlib
import subprocess
import sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
CASES=[
 ('p1-audit-repro-old','candidate-impulse-fix.jar',['--launcher','review']),
 ('p1-audit-repro-new','candidate-audit.jar',['--launcher','review']),
]
for rtt in (0,100):
    for scenario,args in [('player',['--launcher','player']),('combo',['--souls-view'])]:
        for version,jar in [('old','candidate-impulse-fix.jar'),('new','candidate-audit.jar')]:
            CASES.append((f'p1-audit-{scenario}-{version}-{rtt}',jar,args+['--rtt',str(rtt)]))
def main():
    for label,_,_ in CASES:
        if (ROOT/'build/p0/runs'/label).exists() or (ROOT/'build/p1'/f'{label}.log').exists():
            raise FileExistsError(f'Choose fresh labels before running: {label}')
    for label,jar,args in CASES:
        print('BEGIN',label,flush=True)
        with (ROOT/'build/p1'/f'{label}.log').open('x',encoding='utf-8') as output:
            result=subprocess.run([sys.executable,str(ROOT/'tools/p0/dual.py'),*args,'--snapshot-coremod',
                '--mod-snapshot',f'build/p1/{jar}','--label',label,'--accept-eula'],cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
        print('RESULT',label,result.returncode,flush=True)
        if result.returncode:raise SystemExit(result.returncode)

if __name__=='__main__':main()
