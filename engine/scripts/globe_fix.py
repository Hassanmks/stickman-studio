"""Root fix 5 (2026-11-17): the base globe engine/cache/globe-8k.jpg (EOX s2cloudless 2016) has hazy cloud strips (southern
China and elsewhere) that a danger tint lights up as white bands. This script mends them from NASA Blue Marble Next
Generation (August 2004, public domain, assets.science.nasa.gov/.../world.200408.3x21600x10800.jpg):
  1. Blue Marble is resized to 8192x4096 and colour-matched to EOX (per-channel histogram matching on clear land),
  2. a haze mask marks EOX pixels that are much lighter AND greyer than the matched Blue Marble (clouds, not deserts or ice),
  3. only those pixels are replaced, with a feathered edge, so EOX stays everywhere it is clear.
Usage: py -3 engine/scripts/globe_fix.py <bluemarble.jpg>   (keeps the original as globe-8k.eox2016.jpg, writes check images)
"""
import sys, shutil
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage

Image.MAX_IMAGE_PIXELS = None
CACHE = Path(__file__).resolve().parents[1] / 'cache'
BM = CACHE / 'bluemarble-200408-21600.jpg'   # the public-domain source, kept next to the globe
def lum(a): return a @ np.array([.299, .587, .114], np.float32)
def sat(a): mx, mn = a.max(2), a.min(2); return (mx - mn) / np.maximum(mx, 1)
_BMI = None
def mend_patch(path):
    """Mend one EOX detail patch (<w>_<s>_<e>_<n>_WxH.jpg) in place: hazy cloud strips are replaced by matched Blue Marble."""
    global _BMI
    path = Path(path); w, s_, e, n = map(float, path.stem.split('_')[:4])
    if _BMI is None: _BMI = Image.open(BM).convert('RGB')
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float32); h, wd, _ = a.shape
    BW, BH = _BMI.size; box = ((w + 180) / 360 * BW, (90 - n) / 180 * BH, (e + 180) / 360 * BW, (90 - s_) / 180 * BH)
    b = np.asarray(_BMI.resize((wd, h), Image.BICUBIC, box=box), dtype=np.float32)
    La, Sa = lum(a), sat(a); latc = (n + s_) / 2
    land = (La > 18)
    if abs(latc) > 62 or land.mean() < .02: return 0.0
    clear = land & (Sa > .12)
    if clear.sum() < 500: return 0.0
    m = np.empty_like(b)
    for c in range(3):
        qa = np.percentile(b[..., c][clear], np.linspace(0, 100, 257)); qb = np.percentile(a[..., c][clear], np.linspace(0, 100, 257)); m[..., c] = np.interp(b[..., c], qa, qb)
    sc = max(1, wd / 1024)
    haze = np.clip((La - lum(m) - 22) / 30, 0, 1) * np.clip((sat(m) - Sa - .03) / .08, 0, 1) * land
    haze = ndimage.grey_closing(haze, size=int(5 * sc)); haze = ndimage.gaussian_filter(haze, 3.0 * sc); haze = np.clip(haze * 1.6, 0, 1)
    k = float((haze > .5).mean())
    if k < .002: return k
    # keep the EOX texture (detail) inside the mended zones: Blue Marble gives the colour, EOX the high-frequency relief
    det = a - ndimage.gaussian_filter(a, (2 * sc, 2 * sc, 0)); fill = m + det * .5
    out = a * (1 - haze[..., None]) + fill * haze[..., None]
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(path, quality=93, subsampling=0)
    return k

def mend_all(paths):
    done_f = CACHE / 'patches' / 'mended.json'; import json
    done = set(json.loads(done_f.read_text())) if done_f.exists() else set()
    todo = [Path(p) for p in paths if Path(p).name not in done]
    if not todo: return
    from concurrent.futures import ProcessPoolExecutor
    with ProcessPoolExecutor(8) as ex:
        for p, k in zip(todo, ex.map(mend_patch, todo)):
            done.add(p.name)
            if k >= .002: print(f'  mended {k * 100:.1f} % of {p.name}')
    done_f.write_text(json.dumps(sorted(done)))

def main():
    src = CACHE / 'globe-8k.eox2016.jpg'
    if not src.exists(): shutil.copy2(CACHE / 'globe-8k.jpg', src)
    eox = np.asarray(Image.open(src).convert('RGB'), dtype=np.float32)
    H, W, _ = eox.shape
    if len(sys.argv) > 1 and sys.argv[1] != 'patches' and not BM.exists(): shutil.copy2(sys.argv[1], BM)
    bm = np.asarray(Image.open(BM).convert('RGB').resize((W, H), Image.LANCZOS), dtype=np.float32)

    lat = np.linspace(90, -90, H)[:, None] * np.ones((1, W))
    Le, Se = lum(eox), sat(eox)
    land = (Le > 18) & (np.abs(lat) < 62)   # rough: not the dark ocean, not polar ice

    # 1. histogram matching of Blue Marble to EOX on the clear land (EOX's look is the reference)
    clear = land & (Se > .12)
    m = np.empty_like(bm)
    for c in range(3):
        a, b = bm[..., c][clear], eox[..., c][clear]
        qa = np.percentile(a, np.linspace(0, 100, 257)); qb = np.percentile(b, np.linspace(0, 100, 257))
        m[..., c] = np.interp(bm[..., c], qa, qb)
    Lm, Sm = lum(m), sat(m)

    # 2. haze: lighter by > 22 levels and clearly greyer than the matched Blue Marble, on land, away from the poles
    d = Le - Lm
    haze = np.clip((d - 22) / 30, 0, 1) * np.clip((Sm - Se - .03) / .08, 0, 1) * land
    haze = ndimage.grey_closing(haze, size=5)
    haze = ndimage.gaussian_filter(haze, 3.0)
    haze = np.clip(haze * 1.6, 0, 1)[..., None]

    out = eox * (1 - haze) + m * haze
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(CACHE / 'globe-8k.jpg', quality=93, subsampling=0)
    print(f'globe_fix: {float((haze > .5).mean() * 100):.2f} % of the globe mended from Blue Marble -> {CACHE / "globe-8k.jpg"}')

    # check images: southern China (the strips of hormuz-drone-v2) and India, before | after | mask
    chk = CACHE.parent / 'cache' / 'globe_fix_check.jpg'
    def crop(a, lon0, lon1, lat1, lat0):
        x0, x1 = int((lon0 + 180) / 360 * W), int((lon1 + 180) / 360 * W); y0, y1 = int((90 - lat1) / 180 * H), int((90 - lat0) / 180 * H); return a[y0:y1, x0:x1]
    rows = []
    for box in [(97, 123, 33, 18), (68, 92, 30, 8), (20, 60, 40, 10)]:
        e, o, k = crop(eox, *box), crop(out, *box), crop(np.repeat(haze, 3, 2) * 255, *box)
        rows.append(np.concatenate([e, o, k], 1))
    w = min(r.shape[1] for r in rows)
    Image.fromarray(np.clip(np.concatenate([r[:, :w] for r in rows], 0), 0, 255).astype(np.uint8)).save(chk, quality=88)
    print('check:', chk)

if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'patches': mend_all(sorted((CACHE / 'patches').glob('*_*x*.jpg')))
    else: main(); mend_all(sorted((CACHE / 'patches').glob('*_*x*.jpg')))
