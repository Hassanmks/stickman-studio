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
# the compound words («اثنَيْ», «سِتِّمِئَةٍ», «مائتين»…) live in map.js NUMW too (one source of truth, 2026-11-17)
SCALE = lambda v: v is not None and v >= 1000 and v in (1000, 2000, 1e6, 2e6, 1e9, 2e9)
def wval(w):   # the value of ONE token: digits («1650», «84%», «٣٥») or a number word (with its و/ال and a tanween alif: «ملياراً»)
    d = w.translate(str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789'))
    if re.fullmatch(r'\W*\d[\d,.]*\W*', d):
        d = re.sub(r'[^\d.]', '', d.replace(',', ''))
        try: return float(d)
        except ValueError: return None
    for x in stems(w):
        if x in NUMV: return NUMV[x]
        if len(x) > 3 and x.endswith('ا') and x[:-1] in NUMV: return NUMV[x[:-1]]
    return None
def numval(ws):   # the value of the number tokens in a block (تسعة وثلاثين -> 39, اثنا عشر مليارا وستمئة وخمسين مليون -> 12.65e9), None if there are none
    tot = cur = 0; seen = False
    for w in ws:
        v = wval(w)
        if v is None: continue
        seen = True
        if SCALE(v) and not re.search(r'\d', w): tot += (cur or 1) * v; cur = 0
        elif v == 100 and not re.search(r'\d', w): cur = (cur or 1) * 100
        else: cur += v
    return tot + cur if seen else None
strict = lambda w: bool(stems(w) & (NUMW | set(RULES['ambiguous'])))

S, L = [], []   # script words and their line numbers
for i, l in enumerate(lines):
    for w in l.split():
        if norm(w): S.append(w); L.append(i + 1)

# what whisper heard is kept beside the audio (vo/heard.json, keyed by the audio's size and time): a re-check after a fix in
# the checker itself costs 0 s instead of ~17 s of GPU, and does not fight a render for VRAM
HC = audio.with_name('heard.json'); key = f'{audio.stat().st_size}:{int(audio.stat().st_mtime)}'
try: hc = json.loads(HC.read_text(encoding='utf-8'))
except Exception: hc = {}
if hc.get('key') == key and hc.get('words'):
    H, dev, t0 = hc['words'], 'cache', time.perf_counter()
else:
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
    try: HC.write_text(json.dumps({'key': key, 'words': H}, ensure_ascii=False), encoding='utf-8')
    except Exception: pass
print(f'whisper large-v3 on the {dev}: {time.perf_counter() - t0:.1f} s, {len(H)} words heard, {len(S)} in the script', flush=True)

# numbers become one token on both sides before the alignment: «أربعة وثمانين» -> #84, and whisper's «84%» -> #84
PCT = re.compile(r'(بال|لل|ال)(مئه|ميه|مائه)')   # «بالمئة» / «المئة» = per cent, never ×100
STOP = '،,.؛;:!؟?…'
def canon(ws):
    """Numbers become ONE token «#value» on both sides, however they are written: the script's words, whisper's digits,
    or a mix of both («12 مليار و 650 مليون», «1650»). A number goes on while the next token is a number joined by «و»
    (a lone «و» too), a hundred or a scale word, «عشر» after a unit (اثنا عشر), or any number right after a scale word
    (whisper drops the «و»: «مليار 650»). A punctuation mark ends it."""
    out, k = [], 0
    while k < len(ws):
        w = ws[k]
        if re.fullmatch(r'(بال|ال)?(مئه|ميه|مائه)', norm(w)): out.append(('%', k, k + 1)); k += 1; continue   # «بالمئة»
        if norm(w) == 'في' and k + 1 < len(ws) and re.fullmatch(r'(ال)?(مئه|ميه|مائه)', norm(ws[k + 1])): out.append(('%', k, k + 2)); k += 2; continue   # «في المئة»
        v = wval(w)
        if v is None: out.append((norm(w), k, k + 1)); k += 1; continue
        e, prev, pv = k + 1, k, v
        while e < len(ws) and not ws[prev].rstrip().endswith(tuple(STOP)):
            j, waw = e, False
            if norm(ws[j]) == 'و' and j + 1 < len(ws): j, waw = j + 1, True
            nv = wval(ws[j])
            if nv is None or PCT.fullmatch(norm(ws[j])): break
            digit_prev = bool(re.search(r'\d', ws[prev]))
            if not (waw or norm(ws[j]).startswith('و') or nv >= 100 or (nv == 10 and pv < 10) or (SCALE(pv) and not digit_prev)): break
            e, prev, pv = j + 1, j, nv
        val = numval(ws[k:e]); out.append(('#' + str(int(val)), k, e))
        if any(c in ws[e - 1] for c in '%٪'): out.append(('%', e - 1, e))
        k = e
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
numv = lambda tok: float(tok[1:])
def joined(ci1, ci2, cj1, cj2):   # the same value once both sides are read as ONE number (a split the canon missed)
    a = numval(S[CS[ci1][1]:CS[ci2 - 1][2]]); b = numval([h[0] for h in H[CH[cj1][1]:CH[cj2 - 1][2]]])
    return a is not None and a == b
for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, sn, hn, autojunk=False).get_opcodes():
    if tag == 'equal': continue
    if tag == 'delete':
        near = any(isnum(hn[j]) for j in (j1 - 1, j1) if 0 <= j < len(hn))   # a number heard right there: unsure, only a warning
        for ci in range(i1, i2): add('error' if (isnum(sn[ci]) and not near) or (strict(S[CS[ci][1]]) and not isnum(sn[ci])) else 'warn', ci, '', None)
        continue
    if tag == 'insert':
        if j2 - j1 > 2: ci = min(i1, len(CS) - 1); P.append({'level': 'warn', 'line': L[CS[ci][1]], 'word': '', 'heard': ' '.join(hw(j) for j in range(j1, j2)), 't': H[CH[j1][1]][1], 'text': f'السطر {L[CS[ci][1]]}: كلمات زائدة لم تُكتب: «{" ".join(hw(j) for j in range(j1, j2))}»'})
        continue
    if ''.join(sn[i1:i2]) == ''.join(hn[j1:j2]): continue   # «وثلاثين» written «و ثلاثين»
    sN, hN = [x for x in sn[i1:i2] if isnum(x)], [x for x in hn[j1:j2] if isnum(x)]
    if sN and hN and (sorted(map(numv, sN)) == sorted(map(numv, hN)) or sum(map(numv, sN)) == sum(map(numv, hN)) or joined(i1, i2, j1, j2))        and len(sN) == i2 - i1 and len(hN) == j2 - j1: continue   # the same number(s), only cut differently
    if len(sN) == 1 and len(hN) == 1 and numv(sN[0]) != numv(hN[0]) and not joined(i1, i2, j1, j2):   # one number against one number: certain
        ci = i1 + sn[i1:i2].index(sN[0]); cj = j1 + hn[j1:j2].index(hN[0]); add('error', ci, hw(cj), H[CH[cj][1]][1]); continue
    for k, ci in enumerate(range(i1, i2)):
        cj = j1 + min(k, j2 - j1 - 1); heard = hw(cj) if i2 - i1 == j2 - j1 else ' '.join(hw(j) for j in range(j1, j2)); t = H[CH[cj][1]][1]
        w = S[CS[ci][1]]
        if isnum(sn[ci]) or isnum(hn[cj]):
            # a number said differently: an error only when it is certain (one clean number against one clean number, same block shape);
            # a number against a word, or a block cut unevenly, is a warning (whisper often splits or merges big numbers)
            sure = isnum(sn[ci]) and isnum(hn[cj]) and i2 - i1 == j2 - j1 and numv(sn[ci]) != numv(hn[cj])
            add('error' if sure else 'warn', ci, heard, t)
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
