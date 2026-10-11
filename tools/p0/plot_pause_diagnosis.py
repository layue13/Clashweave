"""One-page chart of aggregate time bins; input contains no individual-frame arrays."""
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
plt.rcParams.update({'font.size': 10, 'axes.spines.top': False, 'axes.spines.right': False})
fig, axes = plt.subplots(3, 2, figsize=(10, 11.7), sharex=True, constrained_layout=True)
for column, delay in enumerate([0, 100]):
    old = runs['bounds-motion-clock-' + str(delay)]['phases']['MOTION_90']
    current = runs['pause-diagnosis-' + str(delay)]['phases']['SWITCH_PAUSE']
    for row, key in enumerate(['x', 'error', 'feedSpeed']):
        ax = axes[row, column]
        for series, color, label in [(old, '#176aab', 'Original screenshot stall'),
                                      (current, '#a3a3a3', 'New switch + 200ms pause')]:
            bins = series['curve_bins']
            ax.plot([b['meanTimeMillis'] / 1000 for b in bins], [b[key]['mean'] for b in bins],
                    color=color, lw=1.8, label=label)
        if row == 1:
            bins = old['curve_bins']
            ax.plot([b['meanTimeMillis'] / 1000 for b in bins], [b['filteredError']['mean'] for b in bins],
                    color='#176aab', ls='--', lw=1, label='Original filtered bearing - yaw')
        if delay == 0:
            off = runs['pause-diagnosis-no-feed']['phases']['SWITCH_PAUSE']['curve_bins']
            ax.plot([b['meanTimeMillis'] / 1000 for b in off], [b[key]['mean'] for b in off],
                    color='#9a5d37', ls=':', lw=1.8, label='Experimental feed OFF (0ms only)')
        ax.axhline(0, color='#454545', lw=.6)
        ax.axvspan(0, old['pause']['durationMillis'] / 1000, color='#ecd6ab', alpha=.5)
        ax.grid(alpha=.2)
        ax.set_xlim(-.5, 2.5)
        if row == 0: ax.set_title(str(delay) + 'ms RTT')
        ax.set_ylabel(['Signed target X - 0.5W', 'Bearing - damped yaw (degrees)', 'Feed angular speed (degrees/s)'][row])
        if row == 2: ax.set_xlabel('Seconds from pause/event start')
        ax.legend(fontsize=7, loc='best')
fig.suptitle('Post-stall diagnosis: lag, reverse lead, settling\n100ms bins; extrema and all-frame counts remain in the summary', fontsize=13)
fig.savefig(a.output, dpi=150)
print('Wrote one-page chart:', a.output)
