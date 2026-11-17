"""TaleSpark 2.0, item 4: does the voice say the script? (pronunciation check, 2026-11-17)
usage:  py -3 saycheck.py <project> [audio.wav]     (default audio: <project>/vo/raw.wav)
Transcribes the voice with faster-whisper large-v3 on the GPU WITHOUT the script as a prompt (so it writes what it really
hears), aligns it to script.txt word by word and reports every word that was said differently, with its line number.
A number word or a word that needs tashkeel (engine/data/script_rules.json "ambiguous") must match exactly: «خُمْس» heard
as «خمسة» is an error even though the letters are close. Writes <project>/saycheck.json; exit 2 when there is an error.
"""
import json, os, re, sys, difflib, time, importlib.util
from pathlib import Path
try: sys.stdout.reconfigure(encoding='utf-8')
except Exception: pass
ENG = Path(__file__).resolve().parent.parent
proj = Path(sys.argv[1]).resolve(); audio = Path(sys.argv[2]) if len(sys.argv) > 2 else proj / 'vo' / 'raw.wav'
RULES = json.loads((ENG / 'data' / 'script_rules.json').read_text(encoding='utf-8'))
MAP = (ENG / 'engine' / 'map.js').read_text(encoding='utf-8')
NUMV = {k: float(v) for k, v in re.findall(r'([؀-ۿ]+): ([\d.e]+)', re.search(r'const NUMW = \{([\s\S]*?)\};', MAP).group(1))}; NUMW = set(NUMV)
lines = [l.strip() for l in (proj / 'script.txt').read_text(encoding='utf-8').splitlines() if l.strip() and not l.startswith('#')]

def norm(w):
    w = re.sub(r'[ً-ْٰـ]', '', w.lower()); w = re.sub('[أإآ]', 'ا', w).replace('ة', 'ه').replace('ى', 'ي')
    return re.sub(r'[^\w]', '', w)
def stems(w):
    s = norm(w); o = {s}
    for x in list(o):
        if len(x) > 3 and x[0] in 'وفبكل': o.add(x[1:])
    for x in list(o):
        if len(x) > 4 and (x.startswith('ال') or x.startswith('لل')): o.add(x[2:])
    return o
def numval(ws):   # the value of the number words in a block (تسعة وثلاثين -> 39, عشرين مليون -> 20000000), None if there are none
    tot = cur = 0; seen = False
    for w in ws:
        v = next((NUMV[x] for x in stems(w) if x in NUMV), None)
        if v is None: continue
        seen = True
        if v >= 1000: tot += (cur or 1) * v; cur = 0
        elif v == 100: cur = (cur or 1) * 100
        else: cur += v
    return tot + cur if seen else None
strict = lambda w: bool(stems(w) & (NUMW | set(RULES['ambiguous'])))

S, L = [], []   # script words and their line numbers
for i, l in enumerate(lines):
    for w in l.split():
        if norm(w): S.append(w); L.append(i + 1)

# faster-whisper on the GPU (same DLL setup as voice.py, speed plan item 2)
for mod, sub in (('torch', 'lib'), ('nvidia.cublas', 'bin'), ('nvidia.cudnn', 'bin')):
    try: sp = importlib.util.find_spec(mod)
    except Exception: sp = None
    for d in (Path(p) / sub for p in (list(sp.submodule_search_locations or []) if sp and sp.submodule_search_locations else [])):
        if d.is_dir():
            try: os.add_dll_directory(str(d))
            except Exception: pass
            os.environ['PATH'] = str(d) + os.pathsep + os.environ.get('PATH', '')
from faster_whisper import WhisperModel
t0 = time.perf_counter()
try: model = WhisperModel('large-v3', device='cuda', compute_type='float16'); dev = 'GPU'
except Exception: model = WhisperModel('large-v3', device='cpu', compute_type='int8'); dev = 'CPU'
segs, _ = model.transcribe(str(audio), language='ar', word_timestamps=True, beam_size=5, condition_on_previous_text=False)
H = [[w.word.strip(), round(w.start, 2), round(w.end, 2)] for sg in segs for w in sg.words if norm(w.word)]
print(f'whisper large-v3 on the {dev}: {time.perf_counter() - t0:.1f} s, {len(H)} words heard, {len(S)} in the script', flush=True)

# numbers become one token on both sides before the alignment: «أربعة وثمانين» -> #84, and whisper's «84%» -> #84
PCT = re.compile(r'(بال|لل|ال)(مئه|ميه|مائه)')   # «بالمئة» / «المئة» = per cent, never ×100
def canon(ws):
    out, k = [], 0
    while k < len(ws):
        w = ws[k]; d = re.sub(r'[^\d]', '', w.translate(str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')))
        if d: out.append(('#' + d, k, k + 1)); out += [('%', k, k + 1)] if '%' in w or '٪' in w else []; k += 1; continue
        if re.fullmatch(r'(بال|ال)?(مئه|ميه|مائه)', norm(w)): out.append(('%', k, k + 1)); k += 1; continue   # «بالمئة»
        if norm(w) == 'في' and k + 1 < len(ws) and re.fullmatch(r'(ال)?(مئه|ميه|مائه)', norm(ws[k + 1])): out.append(('%', k, k + 2)); k += 2; continue   # «في المئة"
        if numval([w]) is not None and not (numval([w]) <= 10 and w.strip('،,.؛:!؟?…') != w and False):
            e = k + 1
            while e < len(ws) and numval([ws[e]]) is not None and not PCT.fullmatch(norm(ws[e])) and (norm(ws[e]).startswith('و') or numval([ws[e]]) >= 100): e += 1
            out.append(('#' + str(int(numval(ws[k:e]))), k, e)); k = e; continue
        out.append((norm(w), k, k + 1)); k += 1
    return out
CS, CH = canon(S), canon([h[0] for h in H])
sn, hn = [c[0] for c in CS], [c[0] for c in CH]
P = []
def add(level, ci, heard, t):
    i = CS[ci][1]; word = ' '.join(S[CS[ci][1]:CS[ci][2]])
    P.append({'level': level, 'line': L[i], 'word': word, 'heard': heard, 't': t,
              'text': (f'السطر {L[i]}: «{word}» نُطقت «{heard}»' + (f' (عند {t:.1f} ث)' if t is not None else '')) if heard else f'السطر {L[i]}: «{word}» لم تُسمع'})
hw = lambda cj: ' '.join(h[0] for h in H[CH[cj][1]:CH[cj][2]])
isnum = lambda tok: tok.startswith('#')
for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, sn, hn, autojunk=False).get_opcodes():
    if tag == 'equal': continue
    if tag == 'delete':
        for ci in range(i1, i2): add('error' if isnum(sn[ci]) or strict(S[CS[ci][1]]) else 'warn', ci, '', None)
        continue
    if tag == 'insert':
        if j2 - j1 > 2: ci = min(i1, len(CS) - 1); P.append({'level': 'warn', 'line': L[CS[ci][1]], 'word': '', 'heard': ' '.join(hw(j) for j in range(j1, j2)), 't': H[CH[j1][1]][1], 'text': f'السطر {L[CS[ci][1]]}: كلمات زائدة لم تُكتب: «{" ".join(hw(j) for j in range(j1, j2))}»'})
        continue
    if ''.join(sn[i1:i2]) == ''.join(hn[j1:j2]): continue   # «وثلاثين» written «و ثلاثين»
    for k, ci in enumerate(range(i1, i2)):
        cj = j1 + min(k, j2 - j1 - 1); heard = hw(cj) if i2 - i1 == j2 - j1 else ' '.join(hw(j) for j in range(j1, j2)); t = H[CH[cj][1]][1]
        w = S[CS[ci][1]]
        if isnum(sn[ci]) or isnum(hn[cj]): add('error', ci, heard, t)   # a number said differently
        elif strict(w) and not set(stems(heard)) & stems(w) and sn[ci] not in norm(heard): add('error', ci, heard, t)
        elif difflib.SequenceMatcher(None, sn[ci], norm(heard)).ratio() < .6: add('warn', ci, heard, t)
        if i2 - i1 != j2 - j1: break   # one report per uneven block
bad = [p for p in P if p['level'] == 'error']
rep = {'ok': not bad, 'audio': str(audio), 'heard': len(H), 'script_words': len(S), 'problems': P,
       'lines_to_redo': sorted({p['line'] for p in bad})}
(proj / 'saycheck.json').write_text(json.dumps(rep, ensure_ascii=False, indent=1), encoding='utf-8')
print(('النطق سليم' if rep['ok'] else 'في النطق أخطاء') + f': {len(bad)} خطأ، {len(P) - len(bad)} تنبيه.')
for p in P: print(('  خطأ  ' if p['level'] == 'error' else '  تنبيه ') + p['text'])
if bad: print('  أعد توليد السطر: ' + '، '.join(map(str, rep['lines_to_redo'])))
sys.exit(0 if rep['ok'] else 2)
