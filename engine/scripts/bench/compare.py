"""Quality gate of the speed plan: compare a new render with the approved one.

    py -3 compare.py <approved.mp4> <new.mp4> [outdir]

- SSIM of every frame (ffmpeg ssim filter): mean, minimum, the 10 worst frames and the worst per second.
- loudness (EBU R128 integrated) and true peak of both.
- side-by-side JPEGs of the 6 worst frames in <outdir> (default: next to new.mp4) to look at by eye.
Pass: mean SSIM >= 0.995 and no visible difference in the worst frames; same loudness (+-0.1 LU) and peak.
"""
import re, subprocess, sys, json
from pathlib import Path

a, b = Path(sys.argv[1]), Path(sys.argv[2])
out = Path(sys.argv[3]) if len(sys.argv) > 3 else b.parent / (b.stem + '_cmp')
out.mkdir(parents=True, exist_ok=True)
stats = out / 'ssim.txt'

def probe(p):
    r = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-count_packets', '-show_entries', 'stream=nb_read_packets,r_frame_rate,width,height:format=duration', '-of', 'json', str(p)], capture_output=True, text=True)
    return json.loads(r.stdout)

def loud(p):
    m = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(p), '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
    return float(re.findall(r'I:\s+(-?[\d.]+) LUFS', m)[-1]), float(re.findall(r'Peak:\s+(-?[\d.]+) dBFS', m)[-1])

pa, pb = probe(a), probe(b)
print('approved:', pa['streams'][0], pa['format']['duration'])
print('new     :', pb['streams'][0], pb['format']['duration'])
# the stats file path goes through the filter-graph parser: run in its folder with a bare name
subprocess.run(['ffmpeg', '-hide_banner', '-v', 'error', '-i', str(b.resolve()), '-i', str(a.resolve()), '-lavfi', '[0:v][1:v]ssim=stats_file=ssim.txt', '-f', 'null', '-'], cwd=str(out), check=True)
S = []
for line in stats.read_text().splitlines():
    m = re.search(r'n:(\d+).*All:([\d.]+)', line)
    if m: S.append((int(m.group(1)), float(m.group(2))))
vals = [s for _, s in S]; mean = sum(vals) / len(vals)
worst = sorted(S, key=lambda x: x[1])[:10]
print(f'\nSSIM over {len(S)} frames: mean {mean:.5f}, min {worst[0][1]:.5f} (frame {worst[0][0]}, {worst[0][0] / 60:.2f} s)')
print('10 worst:', ', '.join(f'{n / 60:.2f}s={s:.4f}' for n, s in worst))
per = {}
for n, s in S: per.setdefault((n - 1) // 60, []).append(s)
low = sorted(((min(v), k) for k, v in per.items()))[:8]
print('worst seconds:', ', '.join(f'{k}s={v:.4f}' for v, k in low))
for n, s in worst[:6]:
    t = (n - 1) / 60
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{t:.4f}', '-i', str(a), '-ss', f'{t:.4f}', '-i', str(b), '-frames:v', '1', '-filter_complex', '[0:v][1:v]hstack,scale=1080:-1', '-q:v', '3', str(out / f'worst_{t:06.2f}s_{s:.4f}.jpg')])
la, lb = loud(a), loud(b)
print(f'loudness approved {la[0]} LUFS / {la[1]} dBTP   new {lb[0]} LUFS / {lb[1]} dBTP')
ok = mean >= 0.995 and abs(la[0] - lb[0]) <= 0.1 and abs(la[1] - lb[1]) <= 0.15
print('RESULT', 'PASS' if ok else 'CHECK', f'(side-by-side worst frames in {out})')
(out / 'compare.json').write_text(json.dumps({'mean': mean, 'min': worst[0], 'worst': worst, 'loud_a': la, 'loud_b': lb, 'pass': ok}))
