"""Run after live measurements so compilation does not add load to an A/B block."""
import json,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
launch=json.loads((ROOT/'build/p0/runClient.json').read_text())
cp=launch['classpath']+';'+str(ROOT/'build/p0/p0-diagnostics.jar')
classes=ROOT/'build/p0/diagnostic-check';classes.mkdir(exist_ok=True)
command=[r'C:\Program Files\Zulu\zulu-25\bin\javac.exe','--release','8','-cp',cp,'-d',str(classes),str(ROOT/'tools/p0-diagnostics/ReadOnlyCheck.java')]
subprocess.run(command,check=True)
results=[]
for variant,jar in [('A','baseline-a72ad97.jar'),('B','baseline-dabfb96.jar')]:
    log=ROOT/'build/p0'/('diagnostics-check-'+variant+'.log')
    command=[launch['java'],'-cp',str(classes)+';'+cp,'ReadOnlyCheck',str(ROOT/'build/p0'/jar)]
    with log.open('w',encoding='utf-8') as output:code=subprocess.run(command,stdout=output,stderr=subprocess.STDOUT).returncode
    lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
    result={'variant':variant,'exit':code,'summary':[line for line in lines if line.startswith('READ_ONLY_CHECK ')]};results.append(result);print(result)
    if code:raise SystemExit('Read-only check failed; inspect '+str(log))
(ROOT/'build/p0/diagnostics-check.json').write_text(json.dumps(results,indent=2)+'\n')
