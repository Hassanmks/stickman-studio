"""Automatic sound design for a Maven Map project (v4: a curated house kit, a hierarchy, and room to breathe).

  python sound.py <project>

Reads <project>/shots.json (events exported by the story) and writes <project>/cues.json + <project>/result/bed.wav.
mix.py does the rest. A story can force a kit sound with sfx:'hit' (any KIT name) or silence an element with sfx:false.

Why it works the way it does (the 2026-11-16 lesson: random library picks on every event sounded noisy and messy):
  * ONE KIT. About ten hand-picked sounds from the pro libraries (picked by analysis: a deep sub boom, a short mid hit,
    one cinematic swoosh family, one soft low click...). The same kit in every video = a channel signature, and the
    ear stops noticing it as "effects". No rocks, splashes, doors, game bleeps or error beeps, ever.
  * THE VOICE IS THE STAR. Effects are quiet support. Big hits only on real story beats (a number lands, a danger
    title, the X, the last line), never more than one every ~4.5 s. Most map events (regions, labels, routes, ships,
    pings) make no sound at all: the picture is enough.
  * SPACE. Every cue claims a window; a weaker cue that falls inside a stronger one is dropped. The bed dips just
    before the big reveal so the hit lands in near silence.
"""
import json, sys, random
from pathlib import Path
import numpy as np, soundfile as sf
from scipy.signal import butter, sosfilt, resample_poly

HERE = Path(__file__).resolve().parent; LIB = HERE.parent / 'sfx'
proj = Path(sys.argv[1]).resolve(); res = proj / 'result'; res.mkdir(exist_ok=True)
SJ = json.loads((proj / 'shots.json').read_text(encoding='utf-8')); EV = SJ.get('events', []); END = SJ['end']
cfg = json.loads((proj / 'config.json').read_text(encoding='utf-8')) if (proj / 'config.json').exists() else {}
rnd = random.Random(cfg.get('seed', 7))

# ---- the house kit (alternates within a slot so no file plays twice in a row)
KIT = {
    'boom':   ['impact/med_dark_001.wav', 'impact/Impact_Drum_Hit_03.wav'],                 # story beats: deep, cinematic
    'hit':    ['impact/short_med_003.wav', 'impact/Signum_source_composite_impact_2.wav'],  # titles: short, mid, clean
    'swoosh': ['whoosh/glow_med_L_to_R_002.wav', 'whoosh/whistle_med_R_to_L_001.wav'],     # camera travel
    'dive':   ['whoosh/CHT2_Cinematic_Transition_105.wav'],                                 # the big dives / pull-outs
    'snap':   ['whoosh/pang_short_R_to_L_003.wav'],                                         # punch-in
    'rise':   ['riser/Bluezone-BC0212-rise-001.wav'],                                       # only into the biggest number
    'tick':   ['ui/kenney_select_006.wav'],                                                 # title words, soft and low
    'reel':   ['ui/kenney_switch_007.wav'],                                                 # a slot reel locking
    'pop':    ['ui/kenney_drop_003.wav'],                                                   # a pin / a capsule on a leader line
    'glitch': ['tension/Malfunction_Glitch_Short_BRSound-106.wav'],                         # danger titles only
    # v5 (engine/scripts/make_sfx.py, synthesised, our own)
    'shutter': ['gen/shutter.wav'],   # a real photo card
    'paper':   ['gen/paper.wav'],     # sepia world / a date change
    'flap':    ['gen/flap.wav'],      # a flag inside the borders
    'horn':    ['gen/horn.wav'],      # a big ship arrives
    'missile': ['gen/missile.wav'],   # a missile / drone flies in
    # 2026-11-18 medieval battle kit (engine/scripts/make_sfx_medieval.py, our own synthesis + Kenney CC0)
    'cannon': ['gen/cannon.wav'],     # a black-powder bombard fires (stone_ball leaves)
    'march':  ['gen/march.wav'],      # infantry on the move (janissaries, defenders)
    'gallop': ['gen/gallop.wav'],     # cavalry
    'oars':   ['gen/oars.wav'],       # a galley rows
    'haul':   ['gen/haul.wav'],       # a ship / the great cannon dragged over land
    'battle': ['gen/battle.wav'],     # the final assault: clash and roar
}
for k, fs in KIT.items():
    for f in fs:
        if not (LIB / f).exists(): sys.exit(f'sound.py: kit sound missing: {f}')
TURN = {k: 0 for k in KIT}
def kit(k): f = KIT[k][TURN[k] % len(KIT[k])]; TURN[k] += 1; return f

# ---- candidate cues: (time, slot, gain dB, align, max length, fade, priority, private window s, note)
CAND = []
def c(t, slot, gain, pri, win, align='attack', mx=2.0, fade=.25, note='', off=0):
    if 0 <= t <= END: CAND.append(dict(t=round(t, 3), slot=slot, gain=gain, pri=pri, win=win, align=align, max=mx, fade=fade, note=note, off=off))

for ev in EV:
    k, t = ev['kind'], ev['t']; s = ev.get('sfx'); mag = ev.get('mag') or 1
    if s is False: continue
    if isinstance(s, str) and s in KIT: c(t, s, -14 if s not in ('battle', 'march', 'gallop', 'haul', 'oars') else -21, 5, .6, mx=2.0 if s not in ('battle', 'march', 'gallop', 'haul', 'oars') else 6.5, fade=.25 if s not in ('battle', 'march', 'gallop', 'haul', 'oars') else 1.5, note=f'{k} forced {s}'); continue
    if k == 'number':
        st = ev.get('settle') or t + .3; stops = ev.get('stops') or []
        for r in stops[:-1]: c(r, 'reel', -31, 1, 0, mx=.25, fade=.05, note='reel lock')
        if stops:
            c(st, 'boom', -11 if mag >= 1 else -15, 9, 1.4, mx=2.6, fade=.9, note='number lands')
            if len(stops) >= 3: c(st, 'rise', -24, 4, 0, align='peak', mx=.05, fade=.05, note='into the big number', off=3.4)
        else: c(st - .25, 'hit', -15, 6, .9, mx=1.4, fade=.6, note='headline number')
    elif k == 'title':
        if t < .05: c(0, 'hit', -15, 7, .8, mx=1.6, fade=.7, note='opening question')
        elif mag >= 1.5: c(t, 'boom', -11, 9, 1.4, mx=3.0, fade=1.2, note='payoff title')
        else: c(t, 'hit', -15, 6, .9, mx=1.4, fade=.6, note='title')
        for w in (ev.get('ticks') or [])[1:]: c(w, 'tick', -31, 1, 0, mx=.2, fade=.04, note='title word tick')
    elif k == 'icon' and ev.get('name') == 'x':
        c(t, 'boom', -10, 10, 1.6, mx=2.8, fade=1.0, note='the X'); c(t + .02, 'glitch', -23, 3, 0, mx=.8, fade=.3, note='X glitch')
    elif k == 'shake' and mag >= 1.15: c(t, 'boom', -12, 8, 1.4, mx=2.4, fade=.9, note='impact moment')
    elif k == 'punch': c(t - .03, 'snap', -22, 3, .45, align='peak', mx=.5, fade=.15, note='punch-in')
    elif k == 'cam':
        m, d = ev['mag'], ev['dur']
        if ev.get('dive') or (m > 3 and d < 4.5): c(ev['t1'] - .1, 'dive', -19, 5, 1.2, align='peak', mx=1.2, fade=.6, note='big camera move')
        elif m > 1.6 and d < 3: c(t + d * .5, 'swoosh', -24, 2, .9, align='peak', mx=.9, fade=.4, note='camera travel')
    elif k == 'pin': c(t + .05, 'pop', -25, 2, .5, mx=.4, fade=.1, note='pin')
    # v5 elements
    elif k == 'photo':
        c(t, 'shutter', -20, 4, .5, mx=.4, fade=.08, note='photo card')
        if ev.get('name') == 'full': c(t, 'swoosh', -22, 3, .8, align='peak', mx=.9, fade=.4, note='full photo wipe', off=0)
    elif k == 'style': c(t, 'paper' if ev.get('name') == 'sepia' else 'swoosh', -22, 3, .8, mx=.9, fade=.3, note='map world change')
    elif k == 'date':
        for w in (ev.get('ticks') or []): c(w, 'paper', -26, 2, .5, mx=.6, fade=.2, note='date flips')
    elif k == 'region' and ev.get('name') == 'flag': c(t, 'flap', -24, 2, .6, mx=1.1, fade=.4, note='flag')
    elif k == 'sprite':
        n = ev.get('name') or ''
        if n in ('explosion', 'tanker_burning'): c(t, 'boom', -12, 8, 1.4, mx=2.4, fade=.9, note='explosion')
        elif n in ('missile', 'drone'): c(t, 'missile', -21, 4, .9, mx=1.3, fade=.3, note='missile in')
        elif n in ('tanker', 'container_ship', 'carrier'): c(t, 'horn', -27, 1, 3.0, mx=2.4, fade=.8, note='ship horn')
        elif n == 'stone_ball': c(t, 'cannon', -13, 7, 1.2, mx=3.0, fade=1.0, note='bombard fires')
        elif n in ('janissaries', 'defenders'): c(t, 'march', -25, 2, 2.5, mx=5.0, fade=1.2, note='army marching')
        elif n == 'cavalry': c(t, 'gallop', -25, 2, 2.5, mx=5.0, fade=1.2, note='cavalry')
        elif n in ('galley', 'carrack'): c(t, 'oars', -28, 1, 3.0, mx=5.0, fade=1.2, note='oars / sails')
        elif n in ('galley_hauled', 'bombard'): c(t, 'haul', -23, 3, 2.5, mx=5.5, fade=1.4, note='dragged over land')
    elif k == 'crack': c(t, 'glitch', -25, 3, .6, mx=.7, fade=.3, note='border cracks')
    # regions, plain labels, routes, ships, pings, rulers, flashes, cuts: silent on purpose

# ---- glitch titles (red = danger) get a soft glitch under their hit
for ev in EV:
    if ev['kind'] == 'title' and ev.get('name') == 'glitch' and ev.get('sfx') is not False: c(ev['t'], 'glitch', -25, 2, 0, mx=.7, fade=.3, note='danger title glitch')

# ---- resolve: strongest first; a cue is dropped if it lands inside the window of a stronger accepted one
CAND.sort(key=lambda x: (-x['pri'], x['t'])); ACC = []
for q in CAND:
    if q['win'] == 0:   # companions (ticks, reel locks, the riser, the glitch layer) never block and are never blocked by hits...
        if q['slot'] in ('tick', 'reel') and any(abs(q['t'] - a['t']) < .08 and a['pri'] >= 6 for a in ACC): continue   # ...except a click under a boom
        if any(a['slot'] == q['slot'] and abs(q['t'] - a['t']) < .1 for a in ACC): continue
        ACC.append(q); continue
    if any(abs(q['t'] - a['t']) < max(q['win'], a['win']) for a in ACC if a['win'] > 0): continue
    if q['slot'] == 'boom' and any(a['slot'] == 'boom' and abs(q['t'] - a['t']) < 4.5 for a in ACC):   # boom budget
        q = {**q, 'slot': 'hit', 'gain': -15, 'note': q['note'] + ' (boom budget -> hit)'}
    ACC.append(q)
ACC.sort(key=lambda x: x['t'])
# the last line: one long boom and the bed falls away
last = END - 1.15
if not any(a['slot'] == 'boom' and abs(a['t'] - last) < 2 for a in ACC): ACC.append(dict(t=round(last, 3), slot='boom', gain=-10, align='attack', max=3.2, fade=1.4, note='ending'))
out = [{'t': a['t'], 'file': 'LIB:' + kit(a['slot']), 'gain': a['gain'], 'align': a['align'], 'max': a['max'], 'fade': a['fade'], 'note': a['note'], 'off': a.get('off', 0)} for a in sorted(ACC, key=lambda x: x['t'])]

# ---- the bed: ONE low drone, low-passed, with a breath of near-silence before each boom (the hit lands in space)
SR = 48000; N = int(SR * (END + .5))
def rd(f):
    a, sr = sf.read(str(LIB / f), always_2d=True); a = a[:, :2] if a.shape[1] > 1 else np.repeat(a, 2, 1)
    return resample_poly(a, SR, sr, axis=0) if sr != SR else a
src = [rd('drone/low_drone_001_1.wav'), rd('drone/low_drone_001_2.wav')]; bed = np.zeros((N, 2)); pos = 0; xf = int(3 * SR); i = 0
while pos < N:
    a = src[i % 2].copy(); i += 1; L = min(len(a), N - pos); a = a[:L]; f = min(xf, L // 3)
    a[:f] *= np.linspace(0, 1, f)[:, None]; a[-f:] *= np.linspace(1, 0, f)[:, None]; bed[pos:pos + L] += a; pos += max(L - xf, SR)
bed = sosfilt(butter(4, 900, 'low', fs=SR, output='sos'), bed, axis=0)
g = np.ones(N)
for a in out:
    if 'boom' in a['note'] or a['file'].endswith(tuple(KIT['boom'])):
        t0 = int((a['t'] - .45) * SR); t1 = int(a['t'] * SR); t2 = int((a['t'] + 1.2) * SR)
        if t0 > 0 and t2 < N: g[t0:t1] = np.minimum(g[t0:t1], np.linspace(1, .35, t1 - t0)); g[t1:t2] = np.minimum(g[t1:t2], np.linspace(.35, 1, t2 - t1))
fi = int(1.2 * SR); g[:fi] *= np.linspace(.4, 1, fi)
bed *= g[:, None]; bed /= np.abs(bed).max() + 1e-9
sf.write(str(res / 'bed.wav'), (bed * .9).astype(np.float32), SR, subtype='PCM_24')

C = {'duck_db': -18, 'sfx_gain_db': -2, 'music': {'file': 'result/bed.wav', 'lufs': cfg.get('bed_lufs', -33), 'start': 0, 'fade_out': 2.5, 'duck_db': -5},
     'auto': True, 'kit': KIT, 'cues': out}
(proj / 'cues.json').write_text(json.dumps(C, ensure_ascii=False, indent=1), encoding='utf-8')
from collections import Counter
print(f'sound.py: {len(out)} cues from a {sum(len(v) for v in KIT.values())}-sound kit '
      f'({dict(Counter(o["note"].split(" (")[0] for o in out))}) -> cues.json')
