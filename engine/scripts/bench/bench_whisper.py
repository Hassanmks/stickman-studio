import os, sys, time
torchlib = r'C:\Users\Android\AppData\Local\Programs\Python\Python311\Lib\site-packages\torch\lib'
use_torch_dlls = sys.argv[2] == 'torchdll'
if use_torch_dlls:
    os.add_dll_directory(torchlib); os.environ['PATH'] = torchlib + os.pathsep + os.environ['PATH']
import numpy as np, soundfile as sf
from scipy.signal import resample_poly
from faster_whisper import WhisperModel
x, sr = sf.read(sys.argv[1]); x = x.mean(1) if x.ndim > 1 else x
if sr != 16000: x = resample_poly(x, 16000, sr)
x = x.astype(np.float32); dev = sys.argv[3]; name = sys.argv[4]
t0 = time.time()
m = WhisperModel(name, device=dev, compute_type='float16' if dev == 'cuda' else 'int8')
t1 = time.time()
segs, _ = m.transcribe(x, language='ar', word_timestamps=True); segs = list(segs)
t2 = time.time()
n = sum(len(s.words) for s in segs)
print(f'{name} on {dev}: load {t1 - t0:.1f} s, transcribe {t2 - t1:.1f} s for {len(x) / 16000:.1f} s audio, {n} words')
