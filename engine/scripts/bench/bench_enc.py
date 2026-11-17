import subprocess, time, sys, re
from pathlib import Path
import tempfile
S = Path(tempfile.mkdtemp(prefix='maven_bench_')); F = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Android\Desktop\Maven-Map-Studio\projects\bab-el-mandeb-v6\frames\f%05d.jpg'   # needs frames f06000..f08399 rendered first
VF = 'tmix=frames=2:weights=1 1,fps=60,format=yuv420p'
def ff(start, n, out, codec):
    return ['ffmpeg', '-v', 'error', '-y', '-framerate', '120', '-start_number', str(start), '-i', F, '-frames:v', str(n), '-vf', VF] + codec + [out]
X264 = ['-c:v', 'libx264', '-crf', '18', '-preset', 'slow']
def run(label, cmds):
    t = time.time(); ps = [subprocess.Popen(c) for c in cmds]; [p.wait() for p in ps]; print(f'{label}: {time.time() - t:.1f} s', flush=True)
run('decode + tmix only (20 s of video)', [ff(6000, 2400, '-', ['-f', 'null'])[:-1] + ['-f', 'null', '-']])
run('x264 slow CRF18, one process (20 s of video)', [ff(6000, 2400, str(S / 'x.mp4'), X264)])
run('x264 slow CRF18, 2 halves in parallel', [ff(6000, 1200, str(S / 'a.mp4'), X264), ff(7200, 1200, str(S / 'b.mp4'), X264)])
run('x264 slow CRF18, 4 quarters in parallel', [ff(6000 + 600 * i, 600, str(S / f'q{i}.mp4'), X264) for i in range(4)])
run('hevc_nvenc p7 (reference only)', [ff(6000, 2400, str(S / 'nv.mp4'), ['-c:v', 'hevc_nvenc', '-preset', 'p7', '-tune', 'hq', '-rc', 'vbr', '-cq', '16', '-b:v', '0'])])
# quality of the chunked encode vs the single one (SSIM against the same reference, both as 60 fps)
for name in ['x.mp4', 'nv.mp4']:
    o = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(S / name), '-framerate', '120', '-start_number', '6000', '-i', F, '-frames:v', '1200', '-lavfi', f'[1:v]{VF}[r];[0:v][r]ssim', '-f', 'null', '-'], capture_output=True, text=True).stderr
    print(name, 'SSIM vs source', re.findall(r'All:([\d.]+)', o)[-1:], 'size', round((S / name).stat().st_size / 2**20, 1), 'MB')
