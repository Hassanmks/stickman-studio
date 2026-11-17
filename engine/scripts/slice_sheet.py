"""Cut one ChatGPT "sprite sheet" (all objects in a grid) into projects/<slug>/img01.png, img02.png, ...

  python slice_sheet.py <sheet.png> projects/<slug> [--n 13]

Objects are found by the empty gaps between them (rows first, then columns inside each row) and numbered
left-to-right, top-to-bottom. A labelled contact sheet is written to projects/<slug>/_sheet_check.png so the
order can be verified by eye; fix any swap by renaming the imgNN files.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

def cutout(im, tol=22):
    """Images without transparency (e.g. a plain white/grey background from an image generator):
    flood-fill the background from the borders and make it transparent, then crop to the object."""
    a = np.asarray(im.getchannel("A"))
    # Already-transparent corners (e.g. a rounded photo card from fetch_photo --keep-bg) mean the
    # background is handled; flood-filling would eat white areas touching the edge (French flag).
    corners_clear = max(a[0, 0], a[0, -1], a[-1, 0], a[-1, -1]) < 20
    if (a < 250).mean() < 0.02 and not corners_clear:
        rgb = im.convert("RGB")
        w, h = rgb.size
        probe = rgb.copy()
        for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)):
            if probe.getpixel((x, y)) != (255, 0, 255):
                ImageDraw.floodfill(probe, (x, y), (255, 0, 255), thresh=tol)
        bg = np.all(np.asarray(probe) == (255, 0, 255), axis=-1) & ~np.all(np.asarray(rgb) == (255, 0, 255), axis=-1)
        if 0.05 < bg.mean() < 0.95:
            mask = Image.fromarray(np.where(bg, 0, 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3))
            mask = mask.filter(ImageFilter.GaussianBlur(1))
            im = im.copy()
            im.putalpha(mask)
    box = im.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
    return im.crop(box) if box else im



def foreground_mask(im):
    a = np.asarray(im.getchannel("A")).astype(np.int16)
    if (a < 200).mean() > 0.05:                      # real transparency
        return a > 40
    rgb = np.asarray(im.convert("RGB")).astype(np.int16)
    border = np.concatenate([rgb[:4].reshape(-1, 3), rgb[-4:].reshape(-1, 3),
                             rgb[:, :4].reshape(-1, 3), rgb[:, -4:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    return np.abs(rgb - bg).sum(axis=-1) > 36


def bands(profile, length, min_gap, min_size):
    on = profile > 0
    out, start = [], None
    for i, v in enumerate(on):
        if v and start is None:
            start = i
        elif not v and start is not None:
            out.append([start, i])
            start = None
    if start is not None:
        out.append([start, len(on)])
    merged = []
    for b in out:
        if merged and b[0] - merged[-1][1] < min_gap:
            merged[-1][1] = b[1]
        else:
            merged.append(b)
    return [b for b in merged if b[1] - b[0] >= min_size]


def detect(mask):
    H, W = mask.shape
    rows = bands((mask.sum(axis=1) > W * 0.004).astype(int), H, H * 0.012, H * 0.03)
    tiles = []
    for y0, y1 in rows:
        sub = mask[y0:y1]
        cols = bands((sub.sum(axis=0) > (y1 - y0) * 0.01).astype(int), W, W * 0.012, W * 0.03)
        for x0, x1 in cols:
            m = mask[y0:y1, x0:x1]
            ys, xs = np.where(m)
            if len(ys) < 0.002 * W * H:
                continue
            tiles.append((x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1))
    return tiles


def _tight(mask, x0, y0, x1, y1):
    ys, xs = np.where(mask[y0:y1, x0:x1])
    if not len(ys):
        return None
    return (x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1)


def _split(mask, t):
    """Cut one box holding two touching objects at the emptiest column of its middle half."""
    x0, y0, x1, y1 = t
    prof = mask[y0:y1, x0:x1].sum(axis=0).astype(float)
    k = max(3, (x1 - x0) // 40)
    prof = np.convolve(prof, np.ones(k) / k, mode="same")
    lo, hi = int((x1 - x0) * 0.25), int((x1 - x0) * 0.75)
    cut = x0 + lo + int(np.argmin(prof[lo:hi])) if hi > lo else (x0 + x1) // 2
    return [b for b in (_tight(mask, x0, y0, cut, y1), _tight(mask, cut, y0, x1, y1)) if b]


def smart_tiles(mask, n, cols):
    """Rows first; inside each row we know how many objects to expect (cols, the last row the rest), so
    touching objects are split at their emptiest column and stray fragments are merged into their neighbour.
    Returns None when the rows themselves do not match the expected grid."""
    H, W = mask.shape
    rows = bands((mask.sum(axis=1) > W * 0.004).astype(int), H, H * 0.012, H * 0.03)
    want_rows = -(-n // cols)
    if len(rows) != want_rows:
        return None
    tiles = []
    for r, (y0, y1) in enumerate(rows):
        want = cols if r < want_rows - 1 else n - cols * (want_rows - 1)
        sub = mask[y0:y1]
        cols_ = bands((sub.sum(axis=0) > (y1 - y0) * 0.01).astype(int), W, W * 0.012, W * 0.01)
        row = [b for b in (_tight(mask, x0, y0, x1, y1) for x0, x1 in cols_) if b]
        while len(row) > want:                       # a fragment: merge the closest pair
            gaps = [row[i + 1][0] - row[i][2] for i in range(len(row) - 1)]
            i = int(np.argmin(gaps))
            a, b = row[i], row[i + 1]
            row[i:i + 2] = [(min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]))]
        guard = 0
        while len(row) < want and guard < 6:         # touching objects: split the widest box
            guard += 1
            i = max(range(len(row)), key=lambda j: row[j][2] - row[j][0])
            parts = _split(mask, row[i])
            if len(parts) < 2:
                break
            row[i:i + 1] = parts
        if len(row) != want:
            return None
        tiles += row
    return tiles


def label(mask):
    """4-connected component labels (pure numpy + python flood fill; mask is small)."""
    H, W = mask.shape
    lab = np.zeros((H, W), np.int32)
    n = 0
    for y0, x0 in zip(*np.nonzero(mask)):
        if lab[y0, x0]:
            continue
        n += 1
        stack = [(y0, x0)]
        lab[y0, x0] = n
        while stack:
            y, x = stack.pop()
            for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= yy < H and 0 <= xx < W and mask[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n
                    stack.append((yy, xx))
    return lab, n


def grid_components(mask, n, cols, k=4):
    """Even grid, but each blob goes whole to the cell holding its centre of mass: objects that
    spill over a cell edge stay intact and neighbours' fragments are dropped."""
    H, W = mask.shape
    small = mask[: H // k * k, : W // k * k].reshape(H // k, k, W // k, k).max(axis=(1, 3))
    small = np.asarray(Image.fromarray((small * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 0
    lab, m = label(small)
    rows = -(-n // cols)
    sh, sw = small.shape
    cells = [np.zeros(small.shape, bool) for _ in range(n)]
    for c in range(1, m + 1):
        ys, xs = np.nonzero(lab == c)
        if len(ys) < 0.0004 * sh * sw:
            continue
        cy, cx = ys.mean(), xs.mean()
        idx = int(cy / sh * rows) * cols + int(cx / sw * cols)
        if idx < n:
            cells[idx] |= lab == c
    out = []
    for cm in cells:
        full = np.asarray(Image.fromarray((cm * 255).astype(np.uint8)).resize((W // k * k, H // k * k), Image.NEAREST))
        big = np.zeros((H, W), np.uint8)
        big[: full.shape[0], : full.shape[1]] = full
        ys, xs = np.nonzero(big)
        if not len(ys):
            out.append(None)
            continue
        out.append(((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1), big))
    return out


def drop_edge_specks(obj):
    """Remove tiny pieces of a neighbour (an arrow tip, a flag corner) that touch the edge of the crop."""
    a = np.asarray(obj.getchannel("A")) > 40
    k = max(1, min(a.shape) // 160)
    small = a[: a.shape[0] // k * k, : a.shape[1] // k * k].reshape(a.shape[0] // k, k, a.shape[1] // k, k).max(axis=(1, 3))
    lab, m = label(small)
    if m < 2:
        return obj
    sizes = np.bincount(lab.ravel())[1:]
    big = sizes.max()
    kill = np.zeros(small.shape, bool)
    for c in range(1, m + 1):
        if sizes[c - 1] > 0.01 * big:
            continue
        comp = lab == c
        if comp[0].any() or comp[-1].any() or comp[:, 0].any() or comp[:, -1].any():
            kill |= comp
    if not kill.any():
        return obj
    full = np.zeros(a.shape, bool)
    full[: small.shape[0] * k, : small.shape[1] * k] = np.kron(kill, np.ones((k, k), bool))
    arr = np.asarray(obj).copy()
    arr[full, 3] = 0
    return Image.fromarray(arr)


def grid_fallback(W, H, n, cols):
    rows = -(-n // cols)
    return [(int(c * W / cols), int(r * H / rows), int((c + 1) * W / cols), int((r + 1) * H / rows))
            for r in range(rows) for c in range(cols)][:n]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sheet")
    ap.add_argument("project")
    ap.add_argument("--n", type=int, default=0, help="expected number of objects")
    ap.add_argument("--cols", type=int, default=3, help="columns, only used if detection fails")
    ap.add_argument("--start", type=int, default=1, help="first slot number")
    args = ap.parse_args()

    im = Image.open(args.sheet).convert("RGBA")
    W, H = im.size
    mask = foreground_mask(im)
    tiles = detect(mask)
    if args.n and len(tiles) != args.n:
        smart = smart_tiles(mask, args.n, args.cols)
        if smart:
            print(f"note: {len(tiles)} boxes seen, {args.n} wanted: touching objects split and fragments merged inside the rows")
            tiles = smart
    if args.n and len(tiles) != args.n:
        print(f"warning: found {len(tiles)} objects, expected {args.n}; assigning blobs to an even {args.cols}-column grid")
        comps = grid_components(foreground_mask(im), args.n, args.cols)
        tiles, masks = [], []
        for c in comps:
            if c is None:
                tiles.append(None)
            else:
                tiles.append(c[0])
                masks.append(c[1])
    else:
        masks = None
    os.makedirs(args.project, exist_ok=True)
    pad = int(min(W, H) * 0.01)
    outs = []
    mi = 0
    for k, tile in enumerate(tiles):
        if tile is None:
            print(f"warning: slot {args.start + k} is empty")
            continue
        x0, y0, x1, y1 = tile
        box = (max(0, x0 - pad), max(0, y0 - pad), min(W, x1 + pad), min(H, y1 + pad))
        crop = im.crop(box)
        if masks is not None:  # paint neighbours' fragments with the background colour, so cutout drops them
            keep = np.asarray(Image.fromarray(masks[mi]).crop(box).filter(ImageFilter.MaxFilter(9))) > 0
            mi += 1
            rgb = np.asarray(im.convert("RGB"))
            bgc = np.median(np.concatenate([rgb[:4].reshape(-1, 3), rgb[:, :4].reshape(-1, 3)]), axis=0)
            arr = np.asarray(crop).copy()
            arr[~keep, :3] = bgc.astype(np.uint8)
            crop = Image.fromarray(arr)
        obj = drop_edge_specks(cutout(crop))
        name = os.path.join(args.project, f"img{args.start + k:02d}.png")
        obj.save(name)
        outs.append((name, obj))
    # contact sheet for checking the order
    cell = 220
    cols = 5
    rows = -(-len(outs) // cols)
    sheet = Image.new("RGB", (cols * cell, rows * cell), (235, 232, 226))
    d = ImageDraw.Draw(sheet)
    f = ImageFont.load_default()
    for k, (name, obj) in enumerate(outs):
        t = obj.copy()
        t.thumbnail((cell - 30, cell - 30))
        x, y = (k % cols) * cell, (k // cols) * cell
        sheet.paste(t, (x + (cell - t.width) // 2, y + 24 + (cell - 30 - t.height) // 2), t)
        d.text((x + 6, y + 4), os.path.basename(name), fill=(200, 20, 20), font=f)
    sheet.save(os.path.join(args.project, "_sheet_check.png"))
    print(f"{len(outs)} images -> {args.project}/img*.png (check {args.project}/_sheet_check.png)")


if __name__ == "__main__":
    main()
