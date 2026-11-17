"""TaleSpark host («مَدار», TaleSpark 2.0 §9, item 6, 2026-11-17).

The phone talks to this PC directly (on the home Wi-Fi, or from anywhere through Tailscale) and the whole film is made
here, on the RTX 4060, with no cloud and no Claude: Gemini's reply (script + shot plan) and the voice come from the phone,
then voice timing -> pronunciation check -> automatic director + logic gate -> contact sheet -> render -> the video.

  py -3 host/host.py              serve on port 8765 (HOST_PORT), print the pairing code
  py -3 host/host.py --pair       print the pairing link again (talespark://pair?...)

It runs in the logged-in session on purpose: Chromium outside the user session cannot reach the GPU (100x slower).
Only the paired phone is served: every request carries the token (header X-Token). Jobs run one at a time.

HTTP (JSON unless said otherwise):
  GET  /status                         -> {ok, version, busy, job, gpu, queue}
  POST /prompt  {topic, seconds, angle} -> {prompt}                  the Gemini prompt (same as the app's own)
  POST /check   {reply, seconds}        -> {report}                  the script check of a Gemini reply
  POST /fix     {reply, seconds}        -> {prompt}                  the Gemini prompt that asks for the fixes
  POST /jobs    {topic, reply, seconds, mode: auto|review}  -> {id, report}   a new film from a Gemini reply
  PUT  /jobs/<id>/voice                 raw audio body (wav/mp3/m4a)  -> {ok}
  PUT  /jobs/<id>/sheet?names=a,b       raw PNG body (a ChatGPT top-view sheet) -> {sprites}
  POST /jobs/<id>/run   {force}         start (or restart) the pipeline
  POST /jobs/<id>/approve               after the contact sheet (mode review): render
  GET  /jobs/<id>                       -> the job: stage, progress 0..1, log tail, reports
  GET  /jobs/<id>/look.jpg | /jobs/<id>/video.mp4 | /jobs/<id>/saycheck | /jobs/<id>/check
  GET  /jobs                            -> the last 20 jobs
"""
import json, os, re, secrets, shutil, socket, subprocess, sys, threading, time, ctypes, datetime, queue
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs
try: sys.stdout.reconfigure(encoding='utf-8')
except Exception: pass

VERSION = '2.0.0'
ROOT = Path(__file__).resolve().parent.parent; ENG = ROOT / 'engine'; S = ENG / 'scripts'; PROJ = ROOT / 'projects'; OUT = ROOT / 'output'
PY = sys.executable; PORT = int(os.environ.get('HOST_PORT', 8765))
PAIR = Path(__file__).resolve().parent / 'pairing.json'
STAGES = ['check', 'voice', 'saycheck', 'director', 'look', 'review', 'render', 'done']
AR = {'check': 'فحص السكربت', 'voice': 'توقيت الصوت', 'saycheck': 'فحص النطق', 'director': 'الإخراج الآلي', 'look': 'ورقة اللقطات',
      'review': 'بانتظار موافقتك', 'render': 'الرسم والترميز', 'done': 'جاهز', 'failed': 'توقف', 'queued': 'في الانتظار', 'new': 'جديد'}

def pairing():
    if PAIR.exists(): return json.loads(PAIR.read_text(encoding='utf-8'))
    p = {'token': secrets.token_urlsafe(24), 'created': datetime.datetime.now().isoformat(timespec='seconds')}
    PAIR.write_text(json.dumps(p), encoding='utf-8'); return p
TOKEN = pairing()['token']

def addresses():
    ips = set()
    try:
        for i in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET): ips.add(i[4][0])
    except Exception: pass
    try:   # Tailscale's own address when it is installed (100.x.y.z)
        r = subprocess.run(['tailscale', 'ip', '-4'], capture_output=True, text=True, timeout=4)
        for l in r.stdout.split(): ips.add(l.strip())
    except Exception: pass
    return sorted(i for i in ips if not i.startswith('127.'))
def pair_link():
    a = addresses(); return f'talespark://pair?hosts={",".join(a)}&port={PORT}&token={TOKEN}', a

# ---------------------------------------------------------------- jobs
LOCK = threading.Lock(); Q = queue.Queue(); CUR = {'id': None}
def jdir(i): return PROJ / i
def jload(i):
    f = jdir(i) / 'job.json'; return json.loads(f.read_text(encoding='utf-8')) if f.exists() else None
def jsave(j):
    j['updated'] = datetime.datetime.now().isoformat(timespec='seconds'); j['stage_ar'] = AR.get(j['stage'], j['stage'])
    with LOCK: (jdir(j['id']) / 'job.json').write_text(json.dumps(j, ensure_ascii=False, indent=1), encoding='utf-8')
def log(j, line):
    with open(jdir(j['id']) / 'host.log', 'a', encoding='utf-8') as f: f.write(line.rstrip() + '\n')
def tail(i, n=30):
    f = jdir(i) / 'host.log'; return f.read_text(encoding='utf-8', errors='replace').splitlines()[-n:] if f.exists() else []

def keep_awake(on):   # no sleep while a film is being made (only this process asks; nothing in the system settings changes)
    try: ctypes.windll.kernel32.SetThreadExecutionState(0x80000000 | (0x00000001 if on else 0))
    except Exception: pass

def run(j, args, stage, weight=(0, 1)):
    """one build.py step, its output in host.log; PROGRESS lines (stream.mjs) move the bar inside this stage"""
    j['stage'] = stage; jsave(j); log(j, f'--- {stage}: build.py {" ".join(args)}')
    p = subprocess.Popen([PY, str(S / 'build.py'), str(jdir(j['id'])), *args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', env={**os.environ, 'PYTHONIOENCODING': 'utf-8'})
    last = 0
    for line in p.stdout:
        m = re.match(r'PROGRESS (\d+)/(\d+)', line)
        if m:
            if time.time() - last > 1.5: j['progress'] = round(weight[0] + (weight[1] - weight[0]) * int(m[1]) / max(1, int(m[2])), 3); jsave(j); last = time.time()
            continue
        if line.startswith('  OK ') or line.startswith('place:'): continue
        log(j, line)
    return p.wait()

def pipeline(i):
    j = jload(i); CUR['id'] = i; keep_awake(True); P = jdir(i)
    try:
        j['error'] = None; j['progress'] = 0
        if not (P / 'vo' / 'raw.wav').exists(): raise RuntimeError('لا يوجد صوت بعد: استورد الصوت أولاً.')
        rep = json.loads((P / 'check.json').read_text(encoding='utf-8')) if (P / 'check.json').exists() else {'ok': False}
        if not rep.get('ok') and not j.get('force'): raise RuntimeError('السكربت فيه أخطاء: أصلحها في Gemini أو اختر «أنتج رغم ذلك».')
        if run(j, ['voice'], 'voice', (0, .08)): raise RuntimeError('فشل توقيت الصوت.')
        j['progress'] = .08
        code = run(j, ['saycheck'], 'saycheck', (.08, .14)); sc = json.loads((P / 'saycheck.json').read_text(encoding='utf-8')) if (P / 'saycheck.json').exists() else None
        j['saycheck'] = sc and {'ok': sc['ok'], 'problems': sc['problems'], 'lines_to_redo': sc['lines_to_redo']}
        if code and not j.get('force'): raise RuntimeError('في النطق أخطاء: أعد توليد السطر ' + '، '.join(map(str, sc['lines_to_redo'])) + ' ثم استورد الصوت من جديد.')
        if run(j, ['director'], 'director', (.14, .18)): raise RuntimeError('المخرج الآلي لم يجتز بوابة المنطق.')
        if run(j, ['look'], 'look', (.18, .24)): raise RuntimeError('فشلت ورقة اللقطات.')
        j['progress'] = .24
        if j.get('mode') == 'review' and not j.get('approved'):
            j['stage'] = 'review'; jsave(j); log(j, '--- waiting for the approval on the phone'); return
        if run(j, ['go'], 'render', (.24, .97)): raise RuntimeError('فشل الرسم.')
        cfg = json.loads((P / 'config.json').read_text(encoding='utf-8')); mp4 = P / f'{cfg["name"]}.mp4'
        if not mp4.exists(): raise RuntimeError('لم يُنتَج الفيديو.')
        OUT.mkdir(exist_ok=True); dst = OUT / mp4.name
        if dst.exists(): dst = OUT / f'{mp4.stem}-{datetime.datetime.now():%H%M}.mp4'   # never overwrite a film
        shutil.copy2(mp4, dst); j['video'] = str(dst); j['stage'] = 'done'; j['progress'] = 1
        tr = P / 'timing_report.json'
        if tr.exists(): j['timing'] = json.loads(tr.read_text(encoding='utf-8')).get('latest')
        jsave(j); log(j, f'--- done: {dst}')
    except Exception as e:
        j['stage'] = 'failed'; j['error'] = str(e); jsave(j); log(j, f'--- failed: {e}')
    finally:
        keep_awake(False); CUR['id'] = None
def worker():
    while True:
        i = Q.get()
        try: pipeline(i)
        except Exception as e: print('worker:', e, flush=True)

def node(*args, stdin=None):
    r = subprocess.run(['node', str(S / 'script_check.mjs'), *args], capture_output=True, text=True, encoding='utf-8', errors='replace', input=stdin)
    return r.returncode, r.stdout
def reply_report(reply, seconds):
    tmp = ROOT / 'host' / 'state'; tmp.mkdir(exist_ok=True); f = tmp / f'reply-{threading.get_ident()}.txt'; f.write_text(reply, encoding='utf-8')
    code, _ = node('check', str(f), *(['--seconds', str(seconds)] if seconds else []))
    # the report as JSON: parse into a scratch project folder of the host
    d = tmp / f'chk-{threading.get_ident()}'; node('parse', str(f), str(d), *(['--seconds', str(seconds)] if seconds else []))
    rep = json.loads((d / 'check.json').read_text(encoding='utf-8')); shutil.rmtree(d, ignore_errors=True); f.unlink(missing_ok=True)
    return rep

def new_job(body):
    i = 'md-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'); P = jdir(i)
    r = subprocess.run([PY, str(S / 'new.py'), str(P)], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode: raise RuntimeError(r.stdout + r.stderr)
    cfg = json.loads((P / 'config.json').read_text(encoding='utf-8'))
    cfg.update({'name': i, 'engine': 'file', 'pauses': 'punct', 'topic': body.get('topic', ''), 'seconds': int(body.get('seconds') or 90)})
    cfg.pop('voice', None); cfg.pop('workers', None)
    (P / 'config.json').write_text(json.dumps(cfg, indent=1, ensure_ascii=False), encoding='utf-8')
    (P / 'reply.txt').write_text(body['reply'], encoding='utf-8')
    j = {'id': i, 'topic': body.get('topic', ''), 'mode': body.get('mode', 'auto'), 'stage': 'new', 'progress': 0, 'created': datetime.datetime.now().isoformat(timespec='seconds')}
    jsave(j)
    rc = run(j, ['check'], 'check'); rep = json.loads((P / 'check.json').read_text(encoding='utf-8'))
    j['check'] = rep; j['stage'] = 'new'; jsave(j)
    return j

def gpu():
    try:
        r = subprocess.run(['nvidia-smi', '--query-gpu=name,utilization.gpu,memory.used,memory.total', '--format=csv,noheader,nounits'], capture_output=True, text=True, timeout=5)
        n, u, m, t = [x.strip() for x in r.stdout.strip().split(',')]; return {'name': n, 'util': int(u), 'mem_mb': int(m), 'total_mb': int(t)}
    except Exception: return None

# ---------------------------------------------------------------- HTTP
class H(BaseHTTPRequestHandler):
    server_version = 'TaleSparkHost/' + VERSION
    def log_message(self, f, *a): pass
    def send(self, code, obj=None, ctype='application/json; charset=utf-8', body=None):
        b = body if body is not None else json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code); self.send_header('Content-Type', ctype); self.send_header('Content-Length', str(len(b))); self.end_headers(); self.wfile.write(b)
    def file(self, f, ctype):
        if not f or not Path(f).exists(): return self.send(404, {'error': 'غير موجود بعد'})
        size = Path(f).stat().st_size; rng = self.headers.get('Range'); a, b = 0, size - 1
        if rng and (m := re.match(r'bytes=(\d*)-(\d*)', rng)): a = int(m[1] or 0); b = int(m[2]) if m[2] else size - 1
        self.send_response(206 if rng else 200); self.send_header('Content-Type', ctype); self.send_header('Accept-Ranges', 'bytes'); self.send_header('Content-Length', str(b - a + 1))
        if rng: self.send_header('Content-Range', f'bytes {a}-{b}/{size}')
        self.end_headers()
        with open(f, 'rb') as fh:
            fh.seek(a); left = b - a + 1
            while left > 0:
                c = fh.read(min(1 << 20, left))
                if not c: break
                self.wfile.write(c); left -= len(c)
    def ok_token(self):
        q = parse_qs(urlparse(self.path).query); t = self.headers.get('X-Token') or (q.get('t') or [''])[0]
        if secrets.compare_digest(t, TOKEN): return True
        self.send(401, {'error': 'الهاتف غير مقترن بهذا الحاسوب'}); return False
    def body(self):
        n = int(self.headers.get('Content-Length') or 0); return self.rfile.read(n) if n else b''
    def js(self):
        b = self.body(); return json.loads(b.decode('utf-8')) if b else {}
    def do_GET(self):
        if not self.ok_token(): return
        u = urlparse(self.path); parts = [x for x in u.path.split('/') if x]
        if u.path == '/status': return self.send(200, {'ok': True, 'version': VERSION, 'busy': CUR['id'] is not None, 'job': CUR['id'], 'queue': Q.qsize(), 'gpu': gpu(), 'name': socket.gethostname()})
        if u.path == '/jobs':
            js = sorted((jload(p.name) for p in PROJ.glob('md-*') if (p / 'job.json').exists()), key=lambda j: j['created'], reverse=True)[:20]
            return self.send(200, {'jobs': js})
        if len(parts) >= 2 and parts[0] == 'jobs':
            j = jload(parts[1]); P = jdir(parts[1])
            if not j: return self.send(404, {'error': 'لا توجد مهمة بهذا الرقم'})
            if len(parts) == 2: return self.send(200, {**j, 'log': tail(parts[1])})
            if parts[2] == 'look.jpg': return self.file(P / 'check' / 'look.jpg', 'image/jpeg')
            if parts[2] == 'video.mp4': return self.file(j.get('video'), 'video/mp4')
            if parts[2] in ('saycheck', 'check'): f = P / f'{parts[2]}.json'; return self.send(200, json.loads(f.read_text(encoding='utf-8')) if f.exists() else {})
        self.send(404, {'error': 'not found'})
    def do_PUT(self):
        if not self.ok_token(): return
        u = urlparse(self.path); parts = [x for x in u.path.split('/') if x]
        if len(parts) == 3 and parts[0] == 'jobs' and jload(parts[1]):
            j = jload(parts[1]); P = jdir(parts[1]); data = self.body()
            if parts[2] == 'voice':   # any audio the phone has -> vo/raw.wav (48 kHz mono, as voice.py expects)
                (P / 'vo').mkdir(exist_ok=True); src = P / 'vo' / 'upload.bin'; src.write_bytes(data)
                if (P / 'vo' / 'raw.wav').exists(): (P / 'vo' / 'raw.wav').replace(P / 'vo' / 'raw_prev.wav')
                r = subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(src), '-ar', '48000', '-ac', '1', str(P / 'vo' / 'raw.wav')], capture_output=True, text=True)
                src.unlink(missing_ok=True)
                if r.returncode: return self.send(400, {'error': 'ملف الصوت غير مقروء: ' + r.stderr[-300:]})
                j['voice'] = True; jsave(j); return self.send(200, {'ok': True})
            if parts[2] == 'sheet':   # a ChatGPT sheet of new moving objects seen from above -> engine/assets/sprites/top/<name>.png
                names = [n for n in (parse_qs(u.query).get('names') or [''])[0].split(',') if re.fullmatch(r'[a-z_]+', n)]
                if not names: return self.send(400, {'error': 'أسماء المجسمات مفقودة'})
                sh = P / 'sheets'; sh.mkdir(exist_ok=True); f = sh / f'sheet-{int(time.time())}.png'; f.write_bytes(data); cut = sh / (f.stem + '-cut')
                r = subprocess.run([PY, str(S / 'slice_sheet.py'), str(f), str(cut), '--n', str(len(names))], capture_output=True, text=True, encoding='utf-8', errors='replace')
                got = sorted(cut.glob('img*.png'))
                if r.returncode or len(got) != len(names): return self.send(400, {'error': f'لم أجد {len(names)} مجسمات منفصلة في اللوحة ({len(got)})'})
                # objects.json says which are moving (drawn from above -> sprites/top/) and which stand still (3/4 view -> sprites/objects/)
                kinds = {o['name']: o['kind'] for o in json.loads((ENG / 'data' / 'objects.json').read_text(encoding='utf-8'))['objects']}
                for n, g in zip(names, got):
                    d = ENG / 'assets' / 'sprites' / ('top' if kinds.get(n, 'move') == 'move' else 'objects'); d.mkdir(exist_ok=True); shutil.copy2(g, d / f'{n}.png')
                subprocess.run([PY, str(S / 'asset_index.py')], capture_output=True)
                return self.send(200, {'sprites': names, 'check': str(cut / '_sheet_check.png')})
        self.send(404, {'error': 'not found'})
    def do_POST(self):
        if not self.ok_token(): return
        u = urlparse(self.path); parts = [x for x in u.path.split('/') if x]
        try:
            b = self.js()
            if u.path == '/prompt':
                code, out = node('prompt', b.get('topic', ''), '--seconds', str(b.get('seconds') or 90), *(['--angle', b['angle']] if b.get('angle') else []))
                return self.send(200, {'prompt': out.strip()})
            if u.path == '/check': return self.send(200, {'report': reply_report(b.get('reply', ''), b.get('seconds'))})
            if u.path == '/fix':
                tmp = ROOT / 'host' / 'state'; tmp.mkdir(exist_ok=True); f = tmp / 'fix.txt'; f.write_text(b.get('reply', ''), encoding='utf-8')
                code, out = node('fix', str(f), *(['--seconds', str(b['seconds'])] if b.get('seconds') else [])); return self.send(200, {'prompt': out.strip()})
            if u.path == '/jobs':
                if not b.get('reply'): return self.send(400, {'error': 'ردّ Gemini فارغ'})
                j = new_job(b); return self.send(200, {'id': j['id'], 'report': j['check']})
            if len(parts) == 3 and parts[0] == 'jobs' and jload(parts[1]):
                j = jload(parts[1])
                if parts[2] == 'run':
                    if CUR['id'] == j['id']: return self.send(409, {'error': 'المهمة تعمل الآن'})
                    j['force'] = bool(b.get('force')); j['approved'] = False; j['stage'] = 'queued'; j['progress'] = 0
                    if b.get('mode'): j['mode'] = b['mode']
                    jsave(j); Q.put(j['id']); return self.send(200, {'ok': True, 'queue': Q.qsize()})
                if parts[2] == 'approve':
                    if j['stage'] != 'review': return self.send(409, {'error': 'لا شيء بانتظار الموافقة'})
                    j['approved'] = True; j['stage'] = 'queued'; jsave(j); Q.put(j['id']); return self.send(200, {'ok': True})
        except Exception as e:
            return self.send(500, {'error': str(e)})
        self.send(404, {'error': 'not found'})

def main():
    link, ips = pair_link()
    if '--pair' in sys.argv: print(link); return
    (ROOT / 'host' / 'state').mkdir(exist_ok=True)
    threading.Thread(target=worker, daemon=True).start()
    srv = ThreadingHTTPServer(('0.0.0.0', PORT), H)
    print(f'TaleSpark host {VERSION} on port {PORT}: ' + ', '.join(f'http://{a}:{PORT}' for a in ips), flush=True)
    print(f'pairing link (open it on the phone, or let the setup push it by adb):\n  {link}', flush=True)
    srv.serve_forever()
if __name__ == '__main__': main()
