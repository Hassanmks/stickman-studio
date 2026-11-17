"""Run a command while sampling CPU, RAM, GPU and VRAM every 0.5 s (needs psutil and nvidia-smi).
    py -3 sample.py <command ...>
Prints the command's own output, then mean / max / min of every measure and the wall time."""
import subprocess, sys, threading, time, statistics as st
import psutil
S = []; stop = False
def sample():
    psutil.cpu_percent(None)
    while not stop:
        time.sleep(.5)
        g = subprocess.run(['nvidia-smi', '--query-gpu=utilization.gpu,memory.used', '--format=csv,noheader,nounits'], capture_output=True, text=True).stdout.strip().split(', ')
        vm = psutil.virtual_memory()
        S.append((psutil.cpu_percent(None), vm.available / 2**30, *[float(v) if v.replace('.', '').isdigit() else -1 for v in g]))
th = threading.Thread(target=sample, daemon=True); th.start()
t0 = time.time(); rc = subprocess.run(sys.argv[1:]).returncode; dt = time.time() - t0; stop = True; th.join()
print(f'--- {dt:.1f} s, exit {rc}')
for n, c in zip(['cpu %', 'ram free GB', 'gpu %', 'vram MB'], zip(*S)):
    print(f'  {n:12s} mean {st.mean(c):8.1f}  max {max(c):8.1f}  min {min(c):8.1f}')
sys.exit(rc)
