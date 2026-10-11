"""One-page aggregate figure, no frame-level output."""
import json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/p0/plot-python'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
folder=ROOT/'docs/p0/evidence/ab-100ms';data=json.loads((folder/'summary.json').read_text())
rows=data['runs'];labels=[str(r['block'])+' '+r['variant'] for r in rows];colors=['#2377b4' if r['variant']=='A' else '#dc731c' for r in rows]
fig,axes=plt.subplots(3,1,figsize=(10,9),gridspec_kw={'height_ratios':[1.3,1,.9]})
fig.suptitle('100 ms FIFO proxy | fixed order A B A B A B\nA: a72ad97  /  B: dabfb96',fontsize=16)
x=np.arange(6)
ax=axes[0];ax.plot(x,[r['motion']['p95'] for r in rows],color='#888',lw=1)
ax.scatter(x,[r['motion']['p95'] for r in rows],c=colors,s=65,label='P95')
ax.scatter(x,[r['motion']['median'] for r in rows],c=colors,marker='x',s=50,label='median')
for i,r in enumerate(rows):ax.annotate(f"{r['motion']['p95']:.5f}",(i,r['motion']['p95']),xytext=(0,7),textcoords='offset points',ha='center',fontsize=9)
ax.set(title='MOTION_90: absolute target X deviation / screen width',xticks=x,xticklabels=labels,ylabel='W')
ax.legend(loc='upper right');ax.grid(axis='y',alpha=.2);ax.margins(y=.18)
ax=axes[1];ax.bar(x,[r['view']['completed_combos'] for r in rows],color=colors)
for i,r in enumerate(rows):ax.text(i,r['view']['completed_combos']+.4,str(r['view']['completed_combos'])+'/20',ha='center')
ax.set(title='Completed combos (no retry of partial scenarios)',xticks=x,xticklabels=labels,ylim=(0,23),ylabel='count');ax.grid(axis='y',alpha=.2)
ax=axes[2];ax.axis('off')
table=[['Run','zero-lead frames','longest zero (s)','jump branches','action rejects','combo S08 / rotation changes']]
for label,r in zip(labels,rows):
    m,v=r['motion'],r['view'];table.append([label,str(m['zero_lead_frames']),f"{m['longest_zero_lead_seconds']:.3f}",str(sum(n for k,n in m['jump_branch_counts'].items() if k in ('NOT_READY','POSITION_RESIDUAL','ANGLE_JUMP'))),str(sum(v['rejections'].values())),f"{v['s08_during_combo_period']} / {v['packet_caused_rotation_changes']}"])
t=ax.table(cellText=table,loc='center',cellLoc='center',colWidths=[.09,.16,.16,.14,.16,.29]);t.auto_set_font_size(False);t.set_fontsize(8);t.scale(1,1.7)
for col in range(6):t[0,col].set_facecolor('#e7ebef')
fig.text(.08,.025,'All six observations retained. Same trace observer in both frozen jars.\nSix blocks; each block runs isolated motion and view scenarios. Not a proof of acceptance.',fontsize=9)
fig.tight_layout(rect=[0,.055,1,.94]);fig.savefig(folder/'comparison.png',dpi=150);print(folder/'comparison.png')
