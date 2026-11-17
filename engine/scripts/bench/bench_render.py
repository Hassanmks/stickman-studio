"""Render a frame range with N workers while sampling GPU, CPU and RAM. usage: bench_render.py <project> <workers> <f0> <f1> [encoders]"""
import os, sys, time, subprocess, threading, shutil, json
from pathlib import Path
import psutil
proj = Path(sys.argv[1]); N = int(sys.argv[2]); f0, f1 = int(sys.argv[3]), int(sys.argv[4]); enc = sys.argv[5] if len(sys.argv) > 5 else '3'
HERE = Path(r'C:\Users\Android\Desktop\Maven-Map-Studio\engine\scripts')
shutil.rmtree(proj / 'frames', ignore_errors=True)
S = []; stop = False
def sample():
    psutil.cpu_percent(None)
    while not stop:
        time.sleep(.5)
        g = subprocess.run(['nvidia-smi', '--query-gpu=utilization.gpu,memory.used,utilization.encoder,clocks.gr,power.draw', '--format=csv,noheader,nounits'], capture_output=True, text=True).stdout.strip().split(', ')
        vm = psutil.virtual_memory()
        S.append((psutil.cpu_percent(None), (vm.total - vm.available) / 2**30, vm.available / 2**30, *[float(v) if v.replace('.', '').isdigit() else -1 for v in g]))
th = threading.Thread(target=sample, daemon=True); th.start()
total = f1 - f0 + 1; per = -(-total // N)
env = {**os.environ, 'PAGE': 'index.html', 'QUIET_LOGIC': '1', 'ENCODERS': enc}
t0 = time.time()
ps = [subprocess.Popen(['node', str(HERE / 'render.mjs'), str(proj), 'frames', '120', str(f0 + i * per), str(min(f1, f0 + (i + 1) * per - 1))], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) for i in range(N)]
outs = [p.communicate()[0] for p in ps]
dt = time.time() - t0; stop = True; th.join()
import statistics as st
col = list(zip(*S))
names = ['cpu %', 'ram used GB', 'ram free GB', 'gpu %', 'vram MB', 'nvenc %', 'gpu MHz', 'gpu W']
print(f'workers {N} encoders {enc}: {total} frames in {dt:.1f} s = {total / dt:.1f} fps (includes ~page start-up)')
for n, c in zip(names, col): print(f'  {n:12s} mean {st.mean(c):8.1f}  max {max(c):8.1f}  min {min(c):8.1f}')
for o in outs:
    for l in o.splitlines():
        if 'ms/frame' in l or 'ERROR' in l or 'error' in l: print('  ', l[:110])
size = sum(f.stat().st_size for f in (proj / 'frames').glob('*.jpg')); print(f'  jpeg avg {size / total / 1024:.0f} KB')
