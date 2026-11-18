"""Fetch the real satellite imagery a Maven Map project needs.

    python prep.py <project>            (run after the story's camera is written; build.py runs it for you)

1. The globe: cache/globe-8k.jpg, the whole Earth (8192x4096, plate carree), downloaded once per computer.
2. Detail patches: render.mjs writes the camera of every frame (cams.json); wherever the camera is closer than the
   globe texture can show sharply, a patch (up to 4096 px a side) is downloaded around that view at 1.6x the needed
   resolution, so one patch serves until the camera has zoomed in about 2x further. They go to <project>/web/img/.

Imagery: EOxCloudless 2016 (https://cloudless.eox.at) by EOX IT Services GmbH, contains modified Copernicus Sentinel
data 2016, licensed CC BY 4.0. Put the credit in the video description (build.py prints it). Later years of this
layer are non-commercial, so the skill only uses 2016.
"""
import sys
import json, math, os, subprocess, sys, time, urllib.request, io, shutil
from pathlib import Path
from PIL import Image
Image.MAX_IMAGE_PIXELS = None
SKILL = Path(__file__).resolve().parent.parent
WMS = 'https://tiles.maps.eox.at/wms?service=WMS&request=GetMap&version=1.1.1&layers=s2cloudless&styles=&srs=EPSG:4326&format=image/jpeg'
CREDIT = 'Satellite imagery: EOxCloudless 2016 by EOX IT Services GmbH (contains modified Copernicus Sentinel data 2016), CC BY 4.0 - https://cloudless.eox.at'
BASE_D = 8192 / (2 * math.pi)       # globe texture pixels per radian
MAXPX = 4096


def get(bbox, w, h, tries=4):
    url = f'{WMS}&bbox={bbox[0]:.5f},{bbox[1]:.5f},{bbox[2]:.5f},{bbox[3]:.5f}&width={w}&height={h}'
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'maven-map'}), timeout=120) as r:
                data = r.read()
            im = Image.open(io.BytesIO(data))
            if im.size == (w, h): return im.convert('RGB')
        except Exception as e:
            print('  retry', i + 1, e)
        time.sleep(2 + 3 * i)
    sys.exit(f'could not download imagery for {bbox}')


def globe():
    out = SKILL / 'cache' / 'globe-8k.jpg'
    if out.exists(): return
    out.parent.mkdir(exist_ok=True); print('downloading the globe (2 x 4096 px, once per computer)')
    g = Image.new('RGB', (8192, 4096))
    for i, x0 in enumerate((-180, 0)):
        g.paste(get((x0, -90, x0 + 180, 90), 4096, 4096), (i * 4096, 0))
    g.save(out, quality=92); print('  ', out)


# ---- smart camera phase 3 (2026-11-17): what a tilted / perspective camera sees (the same rays as setCam/unproject in map.js)
HFOV = 64.0
def drone_cam(lon, lat, k, cy, pitch, head, dr, hfov=HFOV):
    pt, hd = math.radians(pitch), math.radians(head)
    sh, ch, sp, cp = math.sin(hd), math.cos(hd), math.sin(pt), math.cos(pt)
    return {'l0': math.radians(lon), 'p0': math.radians(lat), 'k': k, 'cy': cy * 1920, 'R': (ch, -sh, 0.), 'F': (sp * sh, sp * ch, -cp), 'U': (cp * sh, cp * ch, sp),
            'invD': dr * math.tan(math.radians(hfov / 2)) / (540 / k)}
def unproject(C, px, py):
    sx, sy = (px - 540) / C['k'], (C['cy'] - py) / C['k']; R, F, U, iD = C['R'], C['F'], C['U'], C['invD']
    q = (R[0] * sx + U[0] * sy, R[1] * sx + U[1] * sy, U[2] * sy); d = tuple(F[i] + q[i] * iD for i in range(3)); O = (q[0], q[1], q[2] + 1)
    a = sum(v * v for v in d); b = sum(O[i] * d[i] for i in range(3)); c = sum(v * v for v in q) + 2 * q[2]; disc = b * b - a * c
    if disc < 0: return None
    sq = math.sqrt(disc); s = c / (-b + sq) if -b + sq > 0 else (-b - sq) / a
    if iD > 0 and s * iD <= -1: return None
    X = tuple(O[i] + d[i] * s for i in range(3)); spp, cpp = math.sin(C['p0']), math.cos(C['p0'])
    x, y, z = cpp * X[2] - X[1] * spp, X[0], spp * X[2] + X[1] * cpp
    dd = 1 + s * iD if iD > 0 else 1.0                                    # the eye's distance ratio: ground scale on screen = 1/dd of the centre's
    return (math.degrees(C['l0'] + math.atan2(y, x)), math.degrees(math.atan2(z, math.hypot(x, y))), s * math.sqrt(a), max(dd, 1e-3))
def footprint(cam):
    """the ground a drone frame shows, as boxes by distance: [(bbox, need px/rad), ...] near first; far ground melts into haze"""
    t, lon, lat, k, cy, pitch, head, dr = cam[:8]; C = drone_cam(lon, lat, k, cy, pitch, head, dr); vw = 1080 / k
    near, far, sharp = [], [], 0
    for i in range(13):
        for j in range(23):
            g = unproject(C, 1080 * i / 12, 1920 * j / 22)
            if not g or g[2] > vw * 7: continue
            lo = g[0]
            while lo - lon > 180: lo -= 360
            while lo - lon < -180: lo += 360
            (near if g[3] < 2.2 else far).append((lo, g[1])); sharp = max(sharp, 1 / g[3])
    box = lambda pts: (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))
    out = []
    if near: out.append((box(near), k * sharp))
    if far: out.append((box(near + far), k * .22))
    return out

TERRAIN = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'
TCREDIT = 'Terrain: AWS Terrain Tiles (Mapzen / Linux Foundation; SRTM, GMTED2010, ETOPO1, Copernicus and others) - https://registry.opendata.aws/terrain-tiles/'
def tile(z, x, y):
    f = SKILL / 'cache' / 'dem' / str(z) / str(x) / f'{y}.png'
    if not f.exists():
        f.parent.mkdir(parents=True, exist_ok=True)
        for i in range(4):
            try:
                with urllib.request.urlopen(urllib.request.Request(TERRAIN.format(z=z, x=x, y=y), headers={'User-Agent': 'maven-map'}), timeout=60) as r: data = r.read()
                tmp = f.with_suffix('.part'); tmp.write_bytes(data); tmp.replace(f); break
            except Exception as e:
                print('  dem retry', i + 1, e); time.sleep(2 + 3 * i)
        else: sys.exit(f'could not download terrain tile {z}/{x}/{y}')
    return f
def dem(bb, need_m):
    """terrain heights over bb (lon/lat box) at about need_m metres a pixel -> (PIL RGB terrarium image in plate carree, bbox, hmax km)"""
    import numpy as np
    lat0 = (bb[1] + bb[3]) / 2
    z = max(5, min(12, round(math.log2(156543.03 * math.cos(math.radians(lat0)) / need_m))))
    def tx(lo, z): return (lo + 180) / 360 * 2 ** z
    def ty(la, z): la = math.radians(max(-85, min(85, la))); return (1 - math.log(math.tan(la) + 1 / math.cos(la)) / math.pi) / 2 * 2 ** z
    while z > 5 and (int(tx(bb[2], z)) - int(tx(bb[0], z)) + 1) * (int(ty(bb[1], z)) - int(ty(bb[3], z)) + 1) > 144: z -= 1
    x0, x1, y0, y1 = int(tx(bb[0], z)), int(tx(bb[2], z)), int(ty(bb[3], z)), int(ty(bb[1], z))
    from concurrent.futures import ThreadPoolExecutor
    jobs = [(z, x % 2 ** z, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]
    with ThreadPoolExecutor(6) as ex: files = list(ex.map(lambda j: tile(*j), jobs))
    M = np.zeros(((y1 - y0 + 1) * 256, (x1 - x0 + 1) * 256), np.float32); it = iter(files)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            a = np.asarray(Image.open(next(it)).convert('RGB'), np.float32)
            M[(y - y0) * 256:(y - y0 + 1) * 256, (x - x0) * 256:(x - x0 + 1) * 256] = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
    # resample web mercator -> plate carree over bb, about as many pixels as the source had
    W_ = int(min(3072, (tx(bb[2], z) - tx(bb[0], z)) * 256)) + 2; H_ = int(min(3072, (ty(bb[1], z) - ty(bb[3], z)) * 256)) + 2
    lons = np.linspace(bb[0], bb[2], W_); lats = np.linspace(bb[3], bb[1], H_)
    sx = np.array([tx(v, z) for v in lons]) * 256 - x0 * 256 - .5; sy = np.array([ty(v, z) for v in lats]) * 256 - y0 * 256 - .5
    sx = np.clip(sx, 0, M.shape[1] - 1.001); sy = np.clip(sy, 0, M.shape[0] - 1.001)
    ix, iy = sx.astype(int), sy.astype(int); fx, fy = sx - ix, sy - iy
    A = M[iy][:, ix]; B = M[iy][:, ix + 1]; Cc = M[iy + 1][:, ix]; D = M[iy + 1][:, ix + 1]
    h = (A * (1 - fx) + B * fx) * (1 - fy)[:, None] + (Cc * (1 - fx) + D * fx) * fy[:, None]
    h = np.maximum(h, 0)                                                  # the sea is flat
    v = h + 32768; r = np.floor(v / 256); g = np.floor(v - r * 256); b = np.floor((v - r * 256 - g) * 256)
    img = Image.fromarray(np.stack([r, g, b], -1).astype(np.uint8), 'RGB')
    return img, [round(bb[0], 5), round(bb[1], 5), round(bb[2], 5), round(bb[3], 5)], round(float(h.max()) / 1000, 3), z

def dems(proj, cams):
    """one terrain grid per drone shot (consecutive frames over the same ground share it) -> web/img/dems.json"""
    img = proj / 'web' / 'img'; out = []; t0 = time.time()
    for f in img.glob('dem*.png'): f.unlink()
    groups = []
    for c in cams:
        if len(c) < 8 or c[7] < .05: continue
        fp = footprint(c)
        if not fp: continue
        nb, fb = fp[0][0], fp[-1][0]; cx_, cy_, hw, hh = (nb[0] + nb[2]) / 2, (nb[1] + nb[3]) / 2, (nb[2] - nb[0]) * 1.0, (nb[3] - nb[1]) * 1.0
        bb = [max(fb[0], cx_ - hw), max(fb[1], cy_ - hh), min(fb[2], cx_ + hw), min(fb[3], cy_ + hh)]   # the near ground x3, not the whole haze
        vkm = 1080 / c[3] * 6371
        g = groups[-1] if groups else None
        if g and g['bb'][0] <= c[1] <= g['bb'][2] and g['bb'][1] <= c[2] <= g['bb'][3] and vkm > g['vkm0'] * .45:
            g['bb'] = [min(g['bb'][0], bb[0]), min(g['bb'][1], bb[1]), max(g['bb'][2], bb[2]), max(g['bb'][3], bb[3])]; g['vkm'] = min(g['vkm'], vkm)
        else: groups.append({'bb': list(bb), 'vkm': vkm, 'vkm0': vkm, 't': c[0]})
    for i, g in enumerate(groups):
        im, bb, hmax, z = dem(g['bb'], g['vkm'] * 1000 / 700)
        name = f'dem{i}.png'; im.save(img / name); out.append({'src': name, 'bb': bb, 'hmax': hmax})
        print(f"terrain {i}: t={g['t']:.1f}s  z{z}  {im.size[0]}x{im.size[1]}  bbox {bb}  highest {hmax} km", flush=True)
    (img / 'dems.json').write_text(json.dumps(out, indent=1))
    if out: print(f'{len(out)} terrain grid(s) in {time.time() - t0:.1f} s'); print('CREDIT for the description:', TCREDIT)


def patches(proj):
    cj = proj / 'cams.json'
    if not cj.exists(): sys.exit('no cams.json: run  node render.mjs <project> cams 30  first (build.py prep does both)')
    cams = json.loads(cj.read_text())['cams']
    img = proj / 'web' / 'img'; img.mkdir(parents=True, exist_ok=True)
    old = json.loads((img / 'patches.json').read_text()) if (img / 'patches.json').exists() else []
    P = []
    q = float(json.loads((proj / 'config.json').read_text(encoding='utf-8')).get('quality', 1.0)) if (proj / 'config.json').exists() else 1.0

    def covers(p, bb, need):
        return p['bb'][0] <= bb[0] and p['bb'][2] >= bb[2] and p['bb'][1] <= bb[1] and p['bb'][3] >= bb[3] and p['d'] >= .8 * need

    # item 9 (2026-11-17): a story edit moves the cameras a little and every patch bbox with them, which used to mean a
    # fresh download (80-130 s) for each look at the story. Now any patch already in the shared cache that covers the
    # view at the same resolution rule (>= 0.8 x the need, as for the patches of this run) is used as it is.
    cache = SKILL / 'cache' / 'patches'; cache.mkdir(parents=True, exist_ok=True); CACHED = []
    for f in cache.glob('*.jpg'):
        try:
            *b, wh = f.stem.split('_'); b = [float(v) for v in b]; w, h = (int(v) for v in wh.split('x'))
            if len(b) != 4 or f.name.endswith('.part.jpg'): continue
            clm = max(.15, math.cos(math.radians(0 if b[1] < 0 < b[3] else min(abs(b[1]), abs(b[3])))))   # the most generous latitude: a lower bound of the resolution
            CACHED.append({'bb': b, 'w': w, 'h': h, 'd': round(min(w / (math.radians(b[2] - b[0]) * clm), h / math.radians(b[3] - b[1]))), 'key': f})
        except Exception: continue
    reused = 0
    views = []
    for c in cams:
        t, lon, lat, k, cy = c[:5]
        if len(c) >= 8 and (c[5] > .01 or abs(c[6]) > .01 or c[7] > .001):   # the drone: near ground sharp, far ground coarser (it melts into the haze)
            for bb, need in footprint(c): views.append((t, (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2, need, (max(-85, bb[1]), bb[3]), bb))
            continue
        cl = max(.15, math.cos(math.radians(lat)))
        hx = math.degrees(540 / k) / cl; hy = math.degrees(1920 / k)
        bb = (lon - hx, lat - hy * cy * 1.1, lon + hx, lat + hy * (1 - cy) * 1.1)
        views.append((t, lon, lat, k, None, bb))
    for t, lon, lat, k, drone, bb in views:
        need = k * q
        if need <= BASE_D * 1.25: continue
        cl = max(.15, math.cos(math.radians(lat)))
        bb = (bb[0], max(-85, bb[1]), bb[2], min(85, bb[3]))
        if drone:   # a drone view: as sharp as one patch can be over it (else no patch would ever be enough), and a margin for the next frames
            fit = min(MAXPX / max(1e-6, math.radians(bb[2] - bb[0]) * cl), MAXPX / max(1e-6, math.radians(bb[3] - bb[1]))) / 1.3
            need = min(need, fit); mx, my = (bb[2] - bb[0]) * .12, (bb[3] - bb[1]) * .12
            if not any(covers(p, bb, need) for p in P): bb = (bb[0] - mx, max(-85, bb[1] - my), bb[2] + mx, min(85, bb[3] + my))
        if any(covers(p, bb, need) for p in P): continue
        hit = [c for c in CACHED if covers(c, bb, need)] if os.environ.get('PATCH_REUSE', '1') != '0' else []
        if hit:
            c = min(hit, key=lambda c: (c['bb'][2] - c['bb'][0]) * (c['bb'][3] - c['bb'][1])); P.append({**c, 't': round(t, 2)}); reused += 1; continue
        d = 1.6 * need                                   # pixels per radian of latitude (and of ground distance)
        ex = math.degrees(MAXPX / (d * cl)); ey = math.degrees(MAXPX / d)
        ex = max(ex, (bb[2] - bb[0]) * 1.05); ey = max(ey, (bb[3] - bb[1]) * 1.05)
        cy0 = (bb[1] + bb[3]) / 2
        pb = [round(lon - ex / 2, 5), round(max(-85, cy0 - ey / 2), 5), round(lon + ex / 2, 5), round(min(85, cy0 + ey / 2), 5)]
        w = min(MAXPX, round(math.radians(pb[2] - pb[0]) * d * cl)); h = min(MAXPX, round(math.radians(pb[3] - pb[1]) * d))
        dd = min(w / (math.radians(pb[2] - pb[0]) * cl), h / math.radians(pb[3] - pb[1]))
        P.append({'bb': pb, 'w': w, 'h': h, 'd': round(dd), 't': round(t, 2)})
    # speed plan item 7 (2026-11-17): download the missing patches in parallel (6 at once, polite to the EOX server) through a cache shared by every
    # project, keyed by the exact bbox and size, so a region fetched once is never fetched again. Same bytes as before.
    from concurrent.futures import ThreadPoolExecutor
    out, todo = [], []
    if reused: print(f'{reused} view(s) served by patches already in the shared cache')
    for i, p in enumerate(P):
        name = f"p{i}_{p['bb'][0]:.3f}_{p['bb'][1]:.3f}_{p['w']}x{p['h']}.jpg"
        key = p.get('key') or cache / ('_'.join(f'{v:.5f}' for v in p['bb']) + f"_{p['w']}x{p['h']}.jpg")
        if not (img / name).exists(): todo.append((i, p, name, key))
        out.append({'src': name, 'bb': p['bb'], 'd': p['d']})
    def fetch(job):
        i, p, name, key = job
        if not key.exists():
            print(f"patch {i}: t={p['t']}s  bbox {p['bb']}  {p['w']}x{p['h']} px  ({p['d']} px/rad)", flush=True)
            tmp = key.with_suffix('.part.jpg'); get(p['bb'], p['w'], p['h']).save(tmp, quality=92); tmp.replace(key)
        else: print(f'patch {i}: from the shared cache', flush=True)
    t0 = time.time()
    with ThreadPoolExecutor(6) as ex: list(ex.map(fetch, todo))
    # PARALLEL_PIPELINE section 4 (2026-11-18): every new patch is mended from NASA Blue Marble right after its download (no cloud strips)
    try:
        sys.path.insert(0, str(Path(__file__).parent)); import globe_fix; globe_fix.mend_all([k for _, _, _, k in todo])
    except Exception as e: print('globe_fix skipped:', e)
    for _, _, name, key in todo: shutil.copyfile(key, img / name)
    if todo: print(f'{len(todo)} patches ready in {time.time() - t0:.1f} s')
    keep = {o['src'] for o in out}
    for f in img.glob('p*.jpg'):
        if f.name not in keep: f.unlink()
    (img / 'patches.json').write_text(json.dumps(out, indent=1))
    print(f'{len(out)} detail patches for {len(cams)} frames ->', img)
    print('CREDIT for the description:', CREDIT)
    dems(proj, cams)


if __name__ == '__main__':
    globe()
    if len(sys.argv) > 1: patches(Path(sys.argv[1]).resolve())
