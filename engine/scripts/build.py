"""Build steps for a Maven Map project.

  python build.py <project> voice [--timing-only] [--from-downloads]
                                                   voice.py: vo/voice.wav + web/timing.json (word timings); --from-downloads
                                                   first moves the newest AI Studio 'Generated Audio*.wav' (last hour) to vo/raw.wav
  python build.py <project> prep                   camera per frame -> download the satellite patches it needs (run after every camera change)
  python build.py <project> stills 0.5,1.4,2.9     render chosen moments to <project>/check and tile check/sheet.jpg
  python build.py <project> look [t1,t2,...]       item 9: the contact sheet after an edit, moments picked from the story (3 pages) -> check/look.jpg
  python build.py <project> draft                  item 9: story draft from script + timing (draft.mjs) -> web/story.draft.js
  python build.py <project> shots                  one still from the middle of every shot -> check/shots.jpg
  python build.py <project> frames [fps]           render every frame (default 120 fps, blended to 60 for motion blur)
  python build.py <project> final                  mix (cues.json) -> master -14 LUFS / -1.8 dBTP -> encode 60 fps -> <project>/<name>.mp4 + check/final.jpg
  python build.py <project> all                    prep + frames + final
  python build.py <project> resume A B             re-render frames for seconds A..B only (after a small fix), then final
  python build.py <project> render                 v7: frames + x264 in ONE pass (stream.mjs), sound in parallel, mux -> <project>/<name>.mp4
  python build.py <project> go [t1,t2,...]         v7: the whole machine part in one command: globe + cams + patches (parallel,
                                                   cached) [+ stills to look at] + render + sound + mux + sheet + timing report;
                                                   frames/ is deleted at the end (KEEP_FRAMES=1 keeps it)

Every run times its stages and writes them to <project>/timing_report.json ("latest" = the newest time of every
stage across runs, "runs" = the last 40 runs) and prints the table at the end (speed plan item 1, 2026-11-17).
"""
import json, os, re, subprocess, sys, shutil, time, atexit, datetime
from pathlib import Path
HERE = Path(__file__).resolve().parent
PY = sys.executable
proj = Path(sys.argv[1]).resolve(); step = sys.argv[2] if len(sys.argv) > 2 else 'all'
cfg = json.loads((proj / 'config.json').read_text(encoding='utf-8')) if (proj / 'config.json').exists() else {}
name = cfg.get('name', proj.name)
if cfg: (proj / 'web' / 'config.json').write_text(json.dumps(cfg, ensure_ascii=False), encoding='utf-8')

# ---------- stage timer -> <project>/timing_report.json ----------
T0 = time.perf_counter(); STAGES = []; DONE = [False]; STEP0 = step
class stage:
    def __init__(s, n): s.n = n
    def __enter__(s): s.t = time.perf_counter(); return s
    def __exit__(s, *a):
        dt = round(time.perf_counter() - s.t, 2); STAGES.append([s.n, dt]); print(f'[time] {s.n}: {dt:.1f} s', flush=True)
def report():
    total = round(time.perf_counter() - T0, 2)
    if not STAGES: return
    f = proj / 'timing_report.json'
    try: R = json.loads(f.read_text(encoding='utf-8'))
    except Exception: R = {}
    lat = R.get('latest', {})
    for n, dt in STAGES: lat[n] = dt
    run = {'step': ' '.join(sys.argv[2:]) or 'all', 'when': datetime.datetime.now().isoformat(timespec='seconds'), 'ok': DONE[0], 'total': total, 'stages': STAGES}
    R = {'latest': lat, 'runs': (R.get('runs', []) + [run])[-40:]}
    f.write_text(json.dumps(R, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'\n--- timing ({run["step"]}, {"ok" if DONE[0] else "FAILED"}) -> {f}')
    for n, dt in STAGES: print(f'  {n:<22s}{dt:8.1f} s')
    print(f'  {"total":<22s}{total:8.1f} s', flush=True)
atexit.register(report)

def sh(cmd, **kw):
    print('>', cmd if isinstance(cmd, str) else ' '.join(map(str, cmd)), flush=True)
    r = subprocess.run(cmd, **kw)
    if r.returncode: sys.exit(r.returncode)
def node_render(*args, page=None):
    env = dict(os.environ); env['PAGE'] = page or cfg.get('page', 'index.html')
    sh(['node', str(HERE / 'render.mjs'), str(proj), *map(str, args)], env=env)
def shotlist():
    out = subprocess.run(['node', str(HERE / 'render.mjs'), str(proj), 'stills', '0', str(proj / 'check' / '_probe')], capture_output=True, text=True, env={**os.environ, 'PAGE': cfg.get('page', 'index.html')}).stdout
    m = re.search(r'shots (\[.*\])', out); return json.loads(m.group(1)) if m else []

if step == 'voice':
    args = sys.argv[3:]
    if '--from-downloads' in args:   # item 9: the AI Studio file just downloaded -> vo/raw.wav (the Downloads copy is moved, not left behind)
        args.remove('--from-downloads'); dl = Path(os.environ.get('MAVEN_DOWNLOADS', Path.home() / 'Downloads'))
        new = sorted((f for f in dl.glob('Generated Audio*.wav') if time.time() - f.stat().st_mtime < 3600), key=lambda f: f.stat().st_mtime)
        if not new: sys.exit(f'no "Generated Audio*.wav" from the last hour in {dl}')
        (proj / 'vo').mkdir(exist_ok=True); raw = proj / 'vo' / 'raw.wav'
        if raw.exists(): raw.replace(proj / 'vo' / 'raw_prev.wav')   # one previous take kept as a fallback
        shutil.move(str(new[-1]), str(raw)); print(f'voice: {new[-1].name} -> {raw}', flush=True)
    with stage('voice'): sh([PY, str(HERE / 'voice.py'), str(proj), *args])
if step in ('prep', 'all'):
    with stage('prep.globe'): sh([PY, str(HERE / 'prep.py')])          # the globe texture, once per computer
    with stage('prep.cams'): node_render('cams', 30)
    with stage('prep.patches'): sh([PY, str(HERE / 'prep.py'), str(proj)])
if step == 'draft':
    with stage('draft'): sh(['node', str(HERE / 'draft.mjs'), str(proj)])
if step == 'look':
    # item 9: one contact sheet after every story edit. With no times, the moments are picked from the story itself: each
    # title, number, region, label, photo, sprite and ruler 0.6 s after it appears (the busiest frames for overlaps), plus
    # every camera arrival. Three pages render them side by side; patches come from the shared cache.
    with stage('look.cams'): node_render('cams', 30)
    with stage('look.patches'): sh([PY, str(HERE / 'prep.py'), str(proj)], stdout=subprocess.DEVNULL)
    if len(sys.argv) > 3: ts = [float(x) for x in sys.argv[3].split(',')]
    else:
        ev = json.loads((proj / 'shots.json').read_text(encoding='utf-8')); end = ev['end']; cand = []
        for e in ev['events']:
            if e['kind'] in ('title', 'number', 'region', 'label', 'photo', 'sprite', 'measure', 'path'): cand.append(e['t'] + .6)
            elif e['kind'] == 'cam': cand.append(e['t1'] - .05)
        cand = sorted(x for x in cand if .2 < x < end - .05); ts = []
        for x in cand:
            if not ts or x - ts[-1] > .9: ts.append(round(x, 2))
        MAXN = int(os.environ.get('LOOK_MAX', 30))
        if len(ts) > MAXN: ts = [ts[round(i * (len(ts) - 1) / (MAXN - 1))] for i in range(MAXN)]
    out = proj / 'check' / 'look'; shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
    with stage('look.render'):
        n = min(int(os.environ.get('WORKERS', 3)), len(ts)); env = {**os.environ, 'PAGE': cfg.get('page', 'index.html'), 'QUIET_LOGIC': '1'}
        ps = [subprocess.Popen(['node', str(HERE / 'render.mjs'), str(proj), 'stills', ','.join(map(str, ts[k::n])), str(out)], env=env, stdout=subprocess.DEVNULL) for k in range(n)]
        if any(q.wait() for q in ps): sys.exit('look: a page failed (run stills for the message)')
    with stage('look.sheet'): sh([PY, str(HERE / 'sheet.py'), str(out), str(proj / 'check' / 'look.jpg'), '6', '300'])
    print(f'look: {len(ts)} moments -> {proj / "check" / "look.jpg"}:', ', '.join(map(str, ts)))
if step == 'stills':
    shutil.rmtree(proj / 'check' / 'stills', ignore_errors=True)
    with stage('stills.render'): node_render('stills', sys.argv[3], proj / 'check' / 'stills')
    with stage('stills.sheet'): sh([PY, str(HERE / 'sheet.py'), str(proj / 'check' / 'stills'), str(proj / 'check' / 'sheet.jpg'), '6', '300'])
elif step == 'shots':
    with stage('shots.render'):
        S = shotlist(); ts = ','.join(f'{(a + b) / 2:.2f}' for a, b in S)
        shutil.rmtree(proj / 'check' / 'shots', ignore_errors=True)
        node_render('stills', ts, proj / 'check' / 'shots')
    with stage('shots.sheet'): sh([PY, str(HERE / 'sheet.py'), str(proj / 'check' / 'shots'), str(proj / 'check' / 'shots.jpg'), '6', '300'])
    for i, (a, b) in enumerate(S): print(f'shot {i + 1:2d}  {a:5.2f} - {b:5.2f}  ({b - a:.2f} s)')
if step in ('frames', 'all'):
    fps = int(sys.argv[3]) if step == 'frames' and len(sys.argv) > 3 else int(cfg.get('fps', 120))
    with stage('frames.clear'): shutil.rmtree(proj / 'frames', ignore_errors=True)
    workers = int(os.environ.get('WORKERS', cfg.get('workers', 4)))   # 4 = measured best since the pipelined JPEG encoders (2026-11-16: ~100 fps); 6 starves RAM, 8 swaps
    if workers <= 1:
        with stage('frames.render'): node_render('frames', fps)
    else:   # split the frame range across parallel headless Chromium pages (the GPU sits idle with one)
        end = json.loads((proj / 'cams.json').read_text())['end']; total = round(end * fps); per = -(-total // workers)
        env = dict(os.environ); env['PAGE'] = cfg.get('page', 'index.html')
        with stage('frames.render'):
            ps = [subprocess.Popen(['node', str(HERE / 'render.mjs'), str(proj), 'frames', str(fps), str(i * per), str(min(total, (i + 1) * per) - 1)], env=env if i == 0 else {**env, 'QUIET_LOGIC': '1'}) for i in range(workers) if i * per < total]
            print(f'> {len(ps)} parallel workers, {total} frames', flush=True)
            if any(p.wait() for p in ps): sys.exit('a render worker failed')
        print(f'[speed] {total / STAGES[-1][1]:.1f} frames/s with {len(ps)} workers', flush=True)
        (proj / 'render.json').write_text(json.dumps({'fps': fps, 'end': end}))
        # quality gate: a worker that loses its GPU context (VRAM full) silently renders the map black -> find and re-render
        for attempt in range(3):   # blackcheck.py decodes JPEGs at 1/8 size on every CPU core, as its own process (a pool inside build.py re-ran the whole build on Windows)
            with stage(f'frames.blackcheck{attempt + 1}'):
                r = subprocess.run([PY, str(HERE / 'blackcheck.py'), str(proj / 'frames'), str(total)], capture_output=True, text=True)
            if r.returncode: print(r.stderr[-1500:]); sys.exit('black-frame scan failed')
            bad = json.loads(r.stdout.strip().splitlines()[-1])
            if not bad: break
            rs = []; s = p = bad[0]
            for b in bad[1:]:
                if b != p + 1: rs.append((s, p)); s = b
                p = b
            rs.append((s, p)); n = max(1, workers // (2 ** (attempt + 1)))
            print(f'> {len(bad)} frames lost the map (GPU memory) - re-rendering {len(rs)} range(s) with {n} worker(s)', flush=True)
            chunks = [(a + k * -(-(b - a + 1) // n), min(b, a + (k + 1) * -(-(b - a + 1) // n) - 1)) for a, b in rs for k in range(n) if a + k * -(-(b - a + 1) // n) <= b]
            with stage(f'frames.rerender{attempt + 1}'):
                for g in range(0, len(chunks), n):
                    qs = [subprocess.Popen(['node', str(HERE / 'render.mjs'), str(proj), 'frames', str(fps), str(a), str(b)], env=env, stdout=subprocess.DEVNULL) for a, b in chunks[g:g + n]]
                    [q.wait() for q in qs]
        else: sys.exit('frames still black after 3 retries - lower "workers" in config.json')
def audio_chain(log):   # sound design + mix + -14 LUFS master (needs only shots.json and the voice), run beside the render
    res = proj / 'result'; res.mkdir(exist_ok=True); env = {**os.environ, 'PYTHONIOENCODING': 'utf-8'}
    def run(n, cmd):
        t = time.perf_counter(); r = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', env=env)
        log.append((n, round(time.perf_counter() - t, 2), r.returncode, (r.stdout or '')[-1500:] + (r.stderr or '')[-1500:]))
        if r.returncode: raise RuntimeError(f'{n} failed:\n{log[-1][3]}')
        return r
    cj = json.loads((proj / 'cues.json').read_text(encoding='utf-8')) if (proj / 'cues.json').exists() else {}
    if not cj.get('manual'): run('audio.sound', [PY, str(HERE / 'sound.py'), str(proj)])
    run('audio.mix', [PY, str(HERE / 'mix.py'), str(proj)])
    tp = cfg.get('true_peak', -1.8); target = cfg.get('lufs', -14)
    o = run('audio.measure', ['ffmpeg', '-hide_banner', '-i', str(res / 'premaster.wav'), '-af', f'loudnorm=I={target}:TP={tp}:LRA=11:print_format=json', '-f', 'null', '-']).stderr
    J = json.loads(o[o.rindex('{'):o.rindex('}') + 1])
    run('audio.master', ['ffmpeg', '-v', 'error', '-y', '-i', str(res / 'premaster.wav'), '-af', f"loudnorm=I={target}:TP={tp}:LRA=11:measured_I={J['input_i']}:measured_TP={J['input_tp']}:measured_LRA={J['input_lra']}:measured_thresh={J['input_thresh']}:offset={J['target_offset']}:linear=true,aresample=48000", '-c:a', 'pcm_s24le', str(res / 'master.wav')])

if step in ('render', 'go'):
    # v7 (speed plan items 5, 6, 8): frames and x264 at the same time (stream.mjs), the sound beside them, then one mux.
    # Same quality bar as frames + final: 120 fps sub-frames blended to 60, x264 CRF 18 preset slow, -14 LUFS master.
    import threading
    if step == 'go':
        with stage('prep.globe'): sh([PY, str(HERE / 'prep.py')])
    with stage('prep.cams'): node_render('cams', 30)           # also writes shots.json (the events the sound is cut to)
    if step == 'go':
        with stage('prep.patches'): sh([PY, str(HERE / 'prep.py'), str(proj)])
        if len(sys.argv) > 3 and sys.argv[3] != '-':          # quick stills to look at first: go <t1,t2,...>
            shutil.rmtree(proj / 'check' / 'stills', ignore_errors=True)
            with stage('stills'):
                node_render('stills', sys.argv[3], proj / 'check' / 'stills')
                sh([PY, str(HERE / 'sheet.py'), str(proj / 'check' / 'stills'), str(proj / 'check' / 'sheet.jpg'), '6', '300'])
    res = proj / 'result'; res.mkdir(exist_ok=True)
    end = json.loads((proj / 'cams.json').read_text())['end']; K = round(end * 60)
    NB, PRE = (K, 'v') if os.environ.get('BLEND') else (round(end * 120), 'f')   # JPEGs the pages send (see stream.mjs)
    alog, aerr = [], []
    def _audio():
        try: audio_chain(alog)
        except Exception as e: aerr.append(e)
    th = threading.Thread(target=_audio); th.start()
    # pages beside x264: measured 2026-11-17 (v6), x264 slow is the floor, more pages starve it ("stream_workers" in config.json)
    workers = int(os.environ.get('WORKERS', cfg.get('stream_workers', 3))); vid = res / 'video.mp4'
    for attempt in range(3):
        with stage('render+encode' if not attempt else f'render+encode.retry{attempt}'):
            sh(['node', str(HERE / 'stream.mjs'), str(proj), str(vid), str(workers)], env={**os.environ, 'PAGE': cfg.get('page', 'index.html')})
        print(f'[speed] {K / STAGES[-1][1]:.1f} frames/s (60 fps output, {2 * K / STAGES[-1][1]:.1f} sub-frames/s) with {workers} pages', flush=True)
        # the quality gate stays: a page that lost its GPU context draws the map black. The pages' encoder threads test every
        # frame with blackcheck.py's rule while they encode (frames/_black.json); the file scan is only the fallback.
        bj = proj / 'frames' / '_black.json'
        if bj.exists(): bad = json.loads(bj.read_text())
        else:
            with stage('blackcheck'):
                r = subprocess.run([PY, str(HERE / 'blackcheck.py'), str(proj / 'frames'), str(NB), PRE], capture_output=True, text=True)
            if r.returncode: print(r.stderr[-1500:]); sys.exit('black-frame scan failed')
            bad = json.loads(r.stdout.strip().splitlines()[-1])
        print(f'[gate] black frames: {len(bad)}', flush=True)
        if not bad: break
        workers = max(1, workers // 2); print(f'> {len(bad)} frames lost the map (GPU memory) - rendering again with {workers} pages', flush=True)
    else: sys.exit('frames still black after 3 tries - lower "workers" in config.json')
    with stage('audio.wait'): th.join()
    for n, dt, rc, _ in alog: STAGES.append([n + ' (parallel)', dt])
    if aerr: print(aerr[0]); sys.exit('the sound failed')
    out = proj / f'{name}.mp4'
    with stage('final.mux'):
        sh(['ffmpeg', '-v', 'error', '-y', '-i', str(vid), '-i', str(res / 'master.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '256k', '-fflags', '+bitexact', '-t', str(end), str(out)])
    with stage('final.verify'):
        m = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(out), '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
        I = re.findall(r'I:\s+(-?[\d.]+) LUFS', m)[-1]; P = re.findall(r'Peak:\s+(-?[\d.]+) dBFS', m)[-1]
    with stage('final.sheet'):
        fin = proj / 'check' / 'final'; shutil.rmtree(fin, ignore_errors=True); fin.mkdir(parents=True)
        sh(['ffmpeg', '-v', 'error', '-i', str(out), '-vf', 'fps=3', '-q:v', '3', str(fin / 'f%03d.jpg')])
        sh([PY, str(HERE / 'sheet.py'), str(fin), str(proj / 'check' / 'final.jpg'), '10', '200'])
    if step == 'go' and os.environ.get('KEEP_FRAMES') != '1':
        with stage('cleanup'): shutil.rmtree(proj / 'frames', ignore_errors=True); vid.unlink(missing_ok=True)
    print('CREDIT for the description: Satellite imagery: EOxCloudless 2016 by EOX IT Services GmbH (contains modified Copernicus Sentinel data 2016), CC BY 4.0 - https://cloudless.eox.at  |  Borders: Natural Earth (public domain)')
    print(f'\nDONE {out}\n  loudness {I} LUFS, true peak {P} dBTP, {end:.2f} s, 1080x1920 @ 60 fps (from 120 fps, 2-frame blend, encoded while rendering)')
if step == 'resume':
    fps = json.loads((proj / 'render.json').read_text())['fps']; a, b = float(sys.argv[3]), float(sys.argv[4])
    with stage('resume.render'): node_render('frames', fps, int(a * fps), int(b * fps))
    step = 'final'
if step in ('final', 'all'):
    res = proj / 'result'; res.mkdir(exist_ok=True)
    rj = json.loads((proj / 'render.json').read_text()); fps = rj['fps']; end = rj['end']; blend = max(1, fps // 60)
    cj = json.loads((proj / 'cues.json').read_text(encoding='utf-8')) if (proj / 'cues.json').exists() else {}
    if not cj.get('manual'):
        with stage('final.sound'): sh([PY, str(HERE / 'sound.py'), str(proj)])   # automatic sound design unless cues.json says "manual": true
    with stage('final.mix'): sh([PY, str(HERE / 'mix.py'), str(proj)])
    tp = cfg.get('true_peak', -1.8); target = cfg.get('lufs', -14)
    with stage('final.loudnorm'):
        o = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(res / 'premaster.wav'), '-af', f'loudnorm=I={target}:TP={tp}:LRA=11:print_format=json', '-f', 'null', '-'], capture_output=True, text=True).stderr
        J = json.loads(o[o.rindex('{'):o.rindex('}') + 1])
        sh(['ffmpeg', '-v', 'error', '-y', '-i', str(res / 'premaster.wav'), '-af', f"loudnorm=I={target}:TP={tp}:LRA=11:measured_I={J['input_i']}:measured_TP={J['input_tp']}:measured_LRA={J['input_lra']}:measured_thresh={J['input_thresh']}:offset={J['target_offset']}:linear=true,aresample=48000", '-c:a', 'pcm_s24le', str(res / 'master.wav')])
    out = proj / f'{name}.mp4'
    vf = (f"tmix=frames={blend}:weights={' '.join(['1'] * blend)}," if blend > 1 else '') + 'fps=60,format=yuv420p'
    with stage('final.encode'):
        sh(['ffmpeg', '-v', 'error', '-y', '-framerate', str(fps), '-i', str(proj / 'frames' / 'f%05d.jpg'), '-i', str(res / 'master.wav'), '-vf', vf, '-c:v', 'libx264', '-crf', '18', '-preset', 'slow', '-c:a', 'aac', '-b:a', '256k', '-fflags', '+bitexact', '-t', str(end), str(out)])
    with stage('final.verify'):
        m = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(out), '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
        I = re.findall(r'I:\s+(-?[\d.]+) LUFS', m)[-1]; P = re.findall(r'Peak:\s+(-?[\d.]+) dBFS', m)[-1]
    with stage('final.sheet'):
        fin = proj / 'check' / 'final'; shutil.rmtree(fin, ignore_errors=True); fin.mkdir(parents=True)
        sh(['ffmpeg', '-v', 'error', '-i', str(out), '-vf', 'fps=3', '-q:v', '3', str(fin / 'f%03d.jpg')])
        sh([PY, str(HERE / 'sheet.py'), str(fin), str(proj / 'check' / 'final.jpg'), '10', '200'])
    print('CREDIT for the description: Satellite imagery: EOxCloudless 2016 by EOX IT Services GmbH (contains modified Copernicus Sentinel data 2016), CC BY 4.0 - https://cloudless.eox.at  |  Borders: Natural Earth (public domain)')
    print(f'\nDONE {out}\n  loudness {I} LUFS, true peak {P} dBTP, {end:.2f} s, 1080x1920 @ 60 fps (from {fps} fps, {blend}-frame blend)')
DONE[0] = True
