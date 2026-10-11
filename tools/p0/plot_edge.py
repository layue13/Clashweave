"""One-page boundary evidence, including unsent probes and all sessions."""
import json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/p0/plot-python'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
OUT=ROOT/'docs/p0/evidence/combo-boundary'
d=json.loads(OUT.joinpath('summary.json').read_text(encoding='utf-8'))
fig,axes=plt.subplots(1,2,figsize=(12,7))
for ax,rtt in zip(axes,(0,100)):
    for variant,color in [('A','#777777'),('B','#1478b5')]:
        rows=[r for r in d['aggregate'] if r['rtt_ms']==rtt and r['variant']==variant]
        ax.plot([r['k'] for r in rows],[r['accepted']/r['attempts'] for r in rows],'-o',color=color,label=variant)
        missed=[r for r in rows if r['invalid_age_or_unsent']]
        ax.scatter([r['k'] for r in missed],[r['accepted']/r['attempts'] for r in missed],marker='x',s=120,color='#d44',zorder=5)
    ax.axvline(11,color='#d44',linestyle='--',label='until=11 (exclusive)')
    ax.set(title=f'{rtt} ms RTT',xlabel='Client action age k (ticks)',ylabel='Accepted / 10 attempts',ylim=(-.05,1.05),xticks=range(4,13))
    ax.grid(alpha=.2);ax.legend(fontsize=9)
lines=['A = 542ebab; B = 02b68d8 frozen candidate. ABAB at each RTT; 5/k/session, 10/k/version.',
       'Red x: fixture did not send in the requested age interval; retained in denominator.']
for r in d['runs']:
    lines.append(f"{r['label']}: {r['outcomes']}; S08={r['s08']}, packet rotation={r['packet_rotation_changes']}")
for key,b in d['boundaries'].items():
    lines.append(f"{key}: last all={b['last_all_accepted_k']}, last any={b['last_any_accepted_k']}, accepted k>=11={b['accepted_at_or_after_until']}")
lines.append(f"Retained failed startups: {len(d['manifest'].get('failed_startups',[]))}; no measured attempt replaced.")
fig.text(.04,.02,'\n'.join(lines),fontsize=8,family='monospace',va='bottom')
fig.suptitle('Late combo window: complete fixed-run boundary scan')
fig.tight_layout(rect=(0,.37,1,.95))
fig.savefig(OUT/'boundary.png',dpi=160)
