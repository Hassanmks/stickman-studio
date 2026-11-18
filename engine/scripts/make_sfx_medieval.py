"""Synthesise the medieval battle sounds (siege of Constantinople 1453, 2026-11-18) -> engine/sfx/gen/*.wav
cannon (black-powder bombard: crack + deep body + rumble tail), march (an army on the move: many feet + drum),
gallop (horses), oars (galley oars dipping in rhythm), haul (wood creak + rollers + heave), battle (clash + roar).
Built from our own synthesis + Kenney CC0 one-shots already in the library. 48 kHz stereo."""
import numpy as np, soundfile as sf
from pathlib import Path
from scipy.signal import butter, sosfilt, resample_poly
SR = 48000; L = Path(__file__).resolve().parent.parent / 'sfx'; OUT = L / 'gen'; rng = np.random.default_rng(1453)
def lp(x, f): return sosfilt(butter(4, f, 'low', fs=SR, output='sos'), x)
def hp(x, f): return sosfilt(butter(2, f, 'high', fs=SR, output='sos'), x)
def bp(x, a, b): return sosfilt(butter(2, [a, b], 'band', fs=SR, output='sos'), x)
def env(n, a, d): t = np.arange(n) / SR; return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)
def rd(f):
    a, sr = sf.read(str(L / f), always_2d=True); a = a.mean(1)
    return resample_poly(a, SR, sr) if sr != SR else a
def put(buf, x, t, g=1.0):
    i = max(0, int(t * SR)); n = min(len(x), len(buf) - i)
    if n > 0: buf[i:i + n] += x[:n] * g
def save(name, x, width=.3):
    x = x / (np.abs(x).max() + 1e-9) * .89; d = int(width * 0.012 * SR) or 1
    st = np.stack([x, np.concatenate([np.zeros(d), x[:-d]])], 1)
    sf.write(str(OUT / f'{name}.wav'), st.astype(np.float32), SR, subtype='PCM_24'); print('gen/' + name, round(len(x) / SR, 2), 's')
N = lambda s: rng.standard_normal(int(s * SR))
# cannon: a sharp crack, a deep pressure body at ~45 Hz falling, a long rolling rumble
n = int(3.2 * SR); t = np.arange(n) / SR
crack = hp(N(3.2), 900) * env(n, .001, .035)
body = np.sin(2 * np.pi * (55 * t - 6 * t * t)) * env(n, .004, .32) * 1.4 + lp(N(3.2), 180) * env(n, .003, .45) * 2.2
rumble = lp(N(3.2), 120) * env(n, .2, 1.1) * .9
save('cannon', crack * .9 + body + rumble)
# march: ~40 soldiers, steps at ~2 per second each with jitter, on dirt, and a slow war drum
D = 6.0; m = np.zeros(int(D * SR)); steps = [rd(f'impact/kenney_footstep_grass_00{i}.wav') for i in range(5)]
for k in range(40):
    ph = rng.uniform(0, .5)
    for s in np.arange(ph, D, .52 + rng.uniform(-.02, .02)): put(m, steps[rng.integers(5)], s + rng.normal(0, .015), rng.uniform(.25, .6))
drum = lp(N(.5), 140) * env(int(.5 * SR), .002, .14) * 3
for s in np.arange(0, D, 1.04): put(m, drum, s, 1.0)
m += lp(N(D), 400) * .05
save('march', lp(m, 5000), .5)
# gallop: horses, a 3-beat gait (ta-ta-tam) ~ 1.6 strides/s, many riders, dull hoof thumps
g = np.zeros(int(D * SR)); hoof = lp(N(.12), 500) * env(int(.12 * SR), .001, .03) * 3 + bp(N(.12), 900, 2500) * env(int(.12 * SR), .001, .01)
for k in range(9):
    ph = rng.uniform(0, .6)
    for s in np.arange(ph, D, .62):
        for o, a in ((0, .7), (.09, .8), (.19, 1.0)): put(g, hoof, s + o + rng.normal(0, .006), a * rng.uniform(.5, 1))
save('gallop', g + lp(N(D), 300) * .03, .5)
# oars: a galley rows in rhythm: the dip (splash), the pull (water swirl), a wood knock, every 1.7 s
o = np.zeros(int(D * SR)); spl = rd('impact/Glacial_Lake_-_Large_Size_Rock_Splashes_-_5_Meters_Water_Spl.wav')
spl = spl[:int(.7 * SR)] * env(int(.7 * SR), .005, .18); knock = rd('impact/kenney_impactWood_light_001.wav')
for s in np.arange(.1, D, 1.7):
    for j in range(6): put(o, bp(spl, 300, 4000), s + rng.normal(0, .025), .5)
    put(o, bp(N(.9), 200, 1200) * env(int(.9 * SR), .2, .3), s + .15, .25); put(o, knock, s + .02, .35)
save('oars', o, .4)
# haul: a ship dragged over greased logs: deep wood creaks, rollers rumbling, men heaving
h = np.zeros(int(D * SR)); h += lp(N(D), 90) * .7
for s in np.arange(.2, D, .9):
    f0 = rng.uniform(70, 120); nn = int(.7 * SR); tt = np.arange(nn) / SR
    cr = np.sign(np.sin(2 * np.pi * (f0 + 30 * np.sin(2 * np.pi * 3 * tt)) * tt)) * env(nn, .15, .25)
    put(h, bp(cr, 150, 1800), s, .35); put(h, rd(f'impact/kenney_impactWood_heavy_00{rng.integers(5)}.wav'), s + .3, .5)
for s in np.arange(0, D, 1.8): put(h, bp(N(.5), 150, 700) * env(int(.5 * SR), .08, .15), s, .6)   # the heave
save('haul', h, .5)
# battle: steel clashes over a low roar of many voices (band-passed noise with slow swells)
D = 7.0; b = np.zeros(int(D * SR)); tt = np.arange(len(b)) / SR
roar = bp(N(D), 180, 900) * (0.6 + .4 * np.sin(2 * np.pi * .23 * tt) ** 2) * .9
b += roar
bells = [rd(f'impact/kenney_impactBell_heavy_00{i}.wav') for i in range(5)]
for s in np.sort(rng.uniform(0, D - .5, 34)): put(b, hp(bells[rng.integers(5)], 1500), s, rng.uniform(.15, .45))
for s in np.arange(.3, D, .96): put(b, lp(N(.5), 140) * env(int(.5 * SR), .002, .14) * 2.5, s, .8)
save('battle', b, .6)
