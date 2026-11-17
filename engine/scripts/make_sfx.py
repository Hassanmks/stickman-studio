"""Synthesise the v5 sounds the library lacks (plan item C2) -> engine/sfx/gen/*.wav (48 kHz stereo, our own, no licence).

    py -3 engine/scripts/make_sfx.py

shutter (a camera snap for photo cards), paper (a page/map turn for sepia and dates), flap (cloth for flags),
horn (a distant ship horn), missile (a falling whistle with air). Deterministic (fixed seed).
"""
from pathlib import Path
import numpy as np, soundfile as sf
from scipy.signal import butter, sosfilt

SR = 48000; OUT = Path(__file__).resolve().parent.parent / 'sfx' / 'gen'; OUT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(11)
t_ = lambda d: np.arange(int(SR * d)) / SR
bp = lambda x, lo, hi: sosfilt(butter(4, [lo, hi], 'band', fs=SR, output='sos'), x)
lp = lambda x, f: sosfilt(butter(4, f, 'low', fs=SR, output='sos'), x)
hp = lambda x, f: sosfilt(butter(4, f, 'high', fs=SR, output='sos'), x)


def save(name, x, width=.25):
    x = x / (np.abs(x).max() + 1e-9) * .89
    d = int(SR * .0007); r = np.concatenate([np.zeros(d), x[:-d]])   # a hair of stereo width
    st = np.stack([x, (1 - width) * x + width * r], 1)
    f = int(SR * .004); st[:f] *= np.linspace(0, 1, f)[:, None]; st[-f * 4:] *= np.linspace(1, 0, f * 4)[:, None]
    sf.write(str(OUT / f'{name}.wav'), st.astype(np.float32), SR, subtype='PCM_24'); print('gen/' + name + '.wav', round(len(x) / SR, 2), 's')


def click(d, f0, f1, decay):
    t = t_(d); n = rng.standard_normal(len(t)); return bp(n, f0, f1) * np.exp(-t / decay)


# shutter: curtain open (bright tick) + 55 ms later the close (a little lower, with a body thump)
x = np.zeros(int(SR * .32)); a = click(.05, 2500, 9000, .006); b = click(.08, 1200, 6000, .012)
body = np.sin(2 * np.pi * 180 * t_(.06)) * np.exp(-t_(.06) / .01) * .5
x[:len(a)] += a; o = int(SR * .055); x[o:o + len(b)] += b; x[o:o + len(body)] += body
save('shutter', x, .1)

# paper: crackly band noise in a few grains (a page turned over)
d = .62; t = t_(d); n = rng.standard_normal(len(t)); env = np.zeros(len(t))
for c, w, g in [(.06, .05, 1), (.18, .09, .8), (.33, .12, .9), (.48, .08, .5)]: env += g * np.exp(-((t - c) / w) ** 2)
crk = (rng.random(len(t)) > .985) * rng.standard_normal(len(t)) * 3
save('paper', bp(n * (1 + crk), 1800, 9000) * env + .35 * lp(n, 900) * env)

# flap: cloth in wind, a 7-9 Hz flutter on low/mid noise, swelling and fading
d = 1.1; t = t_(d); n = rng.standard_normal(len(t)); fl = .55 + .45 * np.abs(np.sin(2 * np.pi * (7.2 * t + .8 * t * t)))
save('flap', (lp(n, 1400) * .8 + bp(n, 1500, 4000) * .25) * fl ** 3 * np.sin(np.pi * t / d) ** .7)

# horn: a distant ship horn, two detuned saw-ish tones at 98 Hz with harmonics, slow attack, low-passed
d = 2.4; t = t_(d); env = np.minimum(1, t / .25) * np.minimum(1, (d - t) / .7) ** 1.5
tone = sum((1 / k) * (np.sin(2 * np.pi * 98 * k * t) + .7 * np.sin(2 * np.pi * 98.6 * k * t + .3)) for k in range(1, 9))
save('horn', lp(tone * env, 1100) + .02 * lp(rng.standard_normal(len(t)), 300) * env, .35)

# missile: a falling whistle (2600 -> 700 Hz) over rushing air, getting louder
d = 1.3; t = t_(d); f = 2600 * (700 / 2600) ** (t / d); ph = 2 * np.pi * np.cumsum(f) / SR; env = (t / d) ** 1.6
air = bp(rng.standard_normal(len(t)), 500, 5000) * env
save('missile', np.sin(ph) * env * .55 + air * .45 + .25 * np.sin(2 * ph) * env, .3)
