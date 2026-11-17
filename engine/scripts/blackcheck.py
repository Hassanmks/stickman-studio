"""Black-frame test for build.py's quality gate (a module of its own so worker processes can import it)."""
from PIL import Image
def black(f):
    im = Image.open(f); im.draft('L', (im.width // 8, im.height // 8)); im = im.convert('L').reduce(2)   # decode at 1/8 size: fast
    w, h = im.size; c = im.crop((0, int(h * .45), w, int(h * .6)))
    if sum(c.histogram()[i] * i for i in range(256)) / (c.width * c.height) >= 14: return False
    # v6: a night scene over open sea is dark in the middle band too. A lost map is black everywhere, so the whole frame decides.
    return sum(im.histogram()[i] * i for i in range(256)) / (w * h) < 9


if __name__ == '__main__':   # python blackcheck.py <frames dir> <total>  -> JSON list of black frame indexes (run as its own process: Windows spawn would re-run build.py otherwise)
    import sys, os, json
    from concurrent.futures import ProcessPoolExecutor
    d, total = sys.argv[1], int(sys.argv[2]); pre = sys.argv[3] if len(sys.argv) > 3 else 'f'   # v7 stream frames are v00000.jpg
    with ProcessPoolExecutor(os.cpu_count()) as ex: flags = list(ex.map(black, [f'{d}/{pre}{i:05d}.jpg' for i in range(total)], chunksize=64))
    print(json.dumps([i for i, x in enumerate(flags) if x]))
