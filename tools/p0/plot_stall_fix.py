"""One-page stall regression chart, using only aggregate time buckets."""
import argparse
import json
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / 'build/p0/plot-python'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

p = argparse.ArgumentParser()
p.add_argument('--summary', required=True)
p.add_argument('--output', required=True)
a = p.parse_args()
runs = json.loads(pathlib.Path(a.summary).read_text(encoding='utf-8'))['runs']
plt.rcParams.update({'font.size': 9, 'axes.spines.top': False})
fig, axes = plt.subplots(3, 2, figsize=(10, 11.7), sharex=True, constrained_layout=True)
for column, delay in enumerate([0, 100]):
    series = [(runs[f'stall-fix-pause-{delay}']['phases']['PAUSE_ONLY'], '#186aaa', 'Pause only'),
              (runs[f'stall-fix-pause-{delay}']['phases']['SWITCH_PAUSE'], '#b66823', 'Switch + pause'),
              (runs[f'stall-fix-stress-{delay}']['phases']['MOTION_90'], '#238354', 'Original screenshot stress'),
              (runs[f'baseline-stress-{delay}']['phases']['MOTION_90'], '#888888', 'Old screenshot stress')]
    for row, key in enumerate(['x', 'error', 'feedSpeed']):
        ax = axes[row, column]
        for item, color, label in series:
            bins = item['curve_bins']
            ax.plot([b['meanTimeMillis']/1000 for b in bins], [b[key]['mean'] for b in bins],
                    color=color, lw=1.5, ls='--' if label.startswith('Old') else '-', label=label)
        ax.axhline(0, color='#444444', lw=.6)
        ax.axvspan(0, .2, color='#e6d3aa', alpha=.3)
        if row == 1: ax.axhline(-3, color='#a33333', lw=.7, ls=':')
        ax.grid(alpha=.2)
        ax.set_xlim(-.5, 2.5)
        ax.set_ylabel(['Signed target X - 0.5W', 'Actual bearing - yaw (degrees)', 'Prediction speed (degrees/s)'][row])
        if row == 0: ax.set_title(f'{delay}ms RTT')
        if row == 2: ax.set_xlabel('Seconds from pause start')
        ax.legend(fontsize=7, loc='best')
fig.suptitle('Real-interval estimation and stall recovery\n100ms buckets; raw extrema and all-phase percentiles are in the summary', fontsize=13)
fig.savefig(a.output, dpi=150)
print(a.output)
