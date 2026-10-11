"""One page: all twelve fixed combo runs, including every failed sequence."""
import json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/p0/plot-python'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
folder=ROOT/'docs/p0/evidence/combo-compensation';data=json.loads(folder.joinpath('summary.json').read_text(encoding='utf-8'))
rows=data['runs'];x=np.arange(12);colors=['#2377b4' if r['variant']=='A' else '#dc731c' for r in rows]
labels=[f"{r['rtt_ms']}ms\n{r['block']}{r['variant']}" for r in rows]
fig,axes=plt.subplots(3,1,figsize=(12,10),gridspec_kw={'height_ratios':[1,1,1.4]})
fig.suptitle('Combo compensation | frozen A/B, fixed ABABAB at 0 and 100 ms\nA: 542ebab  /  B behavior: 81b4b86 (format-only build 17b0f2d)',fontsize=14)
ax=axes[0];ax.bar(x,[r['completed'] for r in rows],color=colors)
for i,r in enumerate(rows):ax.text(i,r['completed']+.4,f"{r['completed']}/20",ha='center',fontsize=9)
ax.set(title='Completed sequences (failures remain failures)',xticks=x,xticklabels=labels,ylim=(0,23),ylabel='count');ax.grid(axis='y',alpha=.2)
ax=axes[1]
for i,r in enumerate(rows):
    s=r['compensation_ticks']
    if s:ax.errorbar(i,s['mean'],yerr=[[s['mean']-s['min']],[s['max']-s['mean']]],color=colors[i],marker='o',capsize=4)
ax.set(title='Accepted derivatives: reconstructed compensation, mean/min/max',xticks=x,xticklabels=labels,ylabel='ticks');ax.grid(axis='y',alpha=.2)
ax=axes[2];ax.axis('off')
table=[['Run','failed / unresolved','failure reasons','accepted links','ping ms min-max','S08 / rotation']]
for r in rows:
    ping=r['server_ping_ms'];p=f"{ping['min']}-{ping['max']}" if ping else '-'
    reasons=', '.join(f'{k}:{v}' for k,v in r['failure_reasons'].items()) or 'none'
    table.append([f"{r['rtt_ms']}ms {r['block']}{r['variant']}",f"{r['failed']} / {r['unresolved']}",reasons,str(r['accepted_derivative_count']),p,f"{r['s08']} / {r['packet_rotation_changes']}"])
t=ax.table(cellText=table,loc='center',cellLoc='center',colWidths=[.13,.16,.34,.12,.15,.1]);t.auto_set_font_size(False);t.set_fontsize(7.5);t.scale(1,1.45)
for c in range(6):t[0,c].set_facecolor('#e7ebef')
j=data['judgment'];fig.text(.08,.015,f"100ms candidate all20: {j['candidate_100ms_pass']} | causal support: {j['causal_effect_supported']} | both normal/indistinguishable: {j['both_100ms_normal_cannot_distinguish']}\nSame read-only fixture; no failed run retried. Compensation inferred from observed receipt age, ping and frozen formula.",fontsize=9)
fig.tight_layout(rect=[0,.055,1,.94]);fig.savefig(folder/'comparison.png',dpi=150);print(folder/'comparison.png')
