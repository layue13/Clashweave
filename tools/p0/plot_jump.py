"""One-page view of every fixed jump-trial observation, no frame arrays."""
import json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/p0/plot-python'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
folder=ROOT/'docs/p0/evidence/jump-windows';data=json.loads((folder/'summary.json').read_text(encoding='utf-8'))
rows=data['runs'];labels=[str(r['block'])+' '+r['variant'] for r in rows];colors=['#2377b4' if r['variant']=='A' else '#dc731c' for r in rows]
fig,axes=plt.subplots(3,1,figsize=(10,9),gridspec_kw={'height_ratios':[1.3,1,.9]})
fig.suptitle('100 ms FIFO proxy | fixed order A B A B A B\nA: dabfb96  /  B: 2bd3b68',fontsize=16)
x=np.arange(6);ax=axes[0]
ax.plot(x,[r['motion']['p95'] for r in rows],color='#888',lw=1)
ax.scatter(x,[r['motion']['p95'] for r in rows],c=colors,s=65,label='P95')
ax.scatter(x,[r['motion']['median'] for r in rows],c=colors,marker='x',s=50,label='median')
ax.axhline(.05,color='#9b2226',lw=1,ls='--',label='P95 limit 0.05 W')
for i,r in enumerate(rows):ax.annotate(f"{r['motion']['p95']:.5f}",(i,r['motion']['p95']),xytext=(0,7),textcoords='offset points',ha='center',fontsize=9)
ax.set(title='MOTION_90: all-frame absolute target X deviation',xticks=x,xticklabels=labels,ylabel='screen width W')
ax.legend(loc='upper right',fontsize=8);ax.grid(axis='y',alpha=.2);ax.margins(y=.2)
ax=axes[1];ax.bar(x,[r['view']['completed_combos'] for r in rows],color=colors)
for i,r in enumerate(rows):ax.text(i,r['view']['completed_combos']+.4,str(r['view']['completed_combos'])+'/20',ha='center')
ax.set(title='Completed combos (all runs retained)',xticks=x,xticklabels=labels,ylim=(0,23),ylabel='count');ax.grid(axis='y',alpha=.2)
ax=axes[2];ax.axis('off')
table=[['Run','zero-lead frames','longest zero (s)','action rejects','combo S08 / rotation changes']]
for label,r in zip(labels,rows):
    m,v=r['motion'],r['view'];table.append([label,str(m['zero_lead_frames']),f"{m['longest_zero_lead_seconds']:.3f}",str(sum(v['rejections'].values())),f"{v['s08_in_combo_period']} / {v['packet_rotation_changes']}"])
t=ax.table(cellText=table,loc='center',cellLoc='center',colWidths=[.1,.22,.22,.16,.3]);t.auto_set_font_size(False);t.set_fontsize(8);t.scale(1,1.7)
for col in range(5):t[0,col].set_facecolor('#e7ebef')
fig.text(.08,.025,'Judgment: '+data['judgment']['motion']+'\nSix isolated motion/view blocks; old diagnostic observer disabled.',fontsize=9)
fig.tight_layout(rect=[0,.055,1,.94]);fig.savefig(folder/'comparison.png',dpi=150);print(folder/'comparison.png')
