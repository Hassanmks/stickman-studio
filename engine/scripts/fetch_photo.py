"""Fetch a REAL photo of a person / landmark / place from Wikidata + Wikimedia Commons,
remove its background and save it as a project image.

  python3 fetch_photo.py "Hassan II of Morocco" projects/<slug>/img13.png
  python3 fetch_photo.py "Peace Palace" projects/<slug>/img08.png --keep-bg
  python3 fetch_photo.py "Francisco Franco" projects/<slug>/img06.png --pick 2     # next-best photo
  python3 fetch_photo.py --qid Q1140634 "Alcala Gate" projects/<slug>/img26.png --keep-bg

--keep-bg   keep the photo's own background (rounded card): best for buildings, landscapes, events.
default     background removed (rembg): best for people and single objects.
--pick N    use the Nth image of the entity (P18 image, then P373 category images are not used).
Prints the Commons page of the file (credit) and appends it to <project>/sources.md.
Wikimedia rate-limits shared servers, so every request retries with back-off.
"""
import argparse
import hashlib
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

UA = "MavenMapStudio/1.0 (educational map videos)"
WD = "https://www.wikidata.org/w/api.php"
IMG_PROPS = ["P18", "P41", "P94", "P242", "P154", "P117"]  # image, flag, coat of arms, locator map, logo, line drawing


def get(url, binary=False, tries=14):
    delay = 2.0
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            return data if binary else json.loads(data)
        except Exception:  # 429 / JSON error from a rate-limit page / network hiccup
            if k == tries - 1:
                raise
            time.sleep(delay)
            delay = min(delay * 1.6, 30)


def find_entity(query, lang="en"):
    q = urllib.parse.quote(query)
    for lg in (lang, "ar", "fr"):
        d = get(f"{WD}?action=wbsearchentities&search={q}&language={lg}&uselang=en&limit=5&format=json")
        if d.get("search"):
            return d["search"]
    return []


# Words in a Wikidata description that mean "named after the thing", not the thing itself
# (Franklin D. Roosevelt once gave a Paris metro station).
NAMESAKE = ("station", "metro", "ship", "film", "album", "song", "street", "avenue", "boulevard", "school",
            "airport", "stadium", "bridge", "disambiguation", "family name", "given name", "painting", "novel",
            "episode", "band", "asteroid", "genus", "species", "video game", "television", "award", "statue")
HUMAN, PLACE_PROPS = "Q5", ("P625", "P131", "P17")


def _year(claim_list):
    for c in claim_list or []:
        t = c.get("mainsnak", {}).get("datavalue", {}).get("value", {})
        if isinstance(t, dict) and t.get("time"):
            try:
                return int(t["time"][1:5]) * (-1 if t["time"].startswith("-") else 1)
            except ValueError:
                pass
    return None


def choose_entity(query, kind="", year=0):
    """Rank the search hits instead of taking the first: right kind (a human for a person, something with a
    place for a place), not a namesake, alive or standing at the right year, famous (many Wikipedia
    languages), and with a picture. Returns [(score, qid, label, description, why)] best first."""
    hits = find_entity(query)[:7]
    if not hits:
        return []
    ids = "|".join(h["id"] for h in hits)
    ents = get(f"{WD}?action=wbgetentities&ids={ids}&props=claims|sitelinks|labels|descriptions&languages=en&format=json")["entities"]
    ql = query.lower()
    out = []
    for rank, h in enumerate(hits):
        e = ents.get(h["id"], {})
        cl = e.get("claims", {})
        desc = (h.get("description") or "").lower()
        p31 = {c.get("mainsnak", {}).get("datavalue", {}).get("value", {}).get("id") for c in cl.get("P31", [])}
        score, why = 10 - rank * 1.5, []
        links = len(e.get("sitelinks", {}))
        score += min(links, 150) / 10
        why.append(f"{links} wikis")
        if any(p in cl for p in IMG_PROPS):
            score += 4
        else:
            score -= 12
            why.append("no image")
        if any(w in desc and w not in ql for w in NAMESAKE):
            score -= 15
            why.append("namesake?")
        if kind == "person":
            if HUMAN in p31:
                score += 12
            else:
                score -= 20
                why.append("not a person")
            born, died = _year(cl.get("P569")), _year(cl.get("P570"))
            if year and born and (year < born or (died and year > died + 1)):
                score -= 10
                why.append(f"lived {born}-{died or ''}")
        elif kind == "place":
            if HUMAN in p31:
                score -= 20
                why.append("a person")
            elif any(p in cl for p in PLACE_PROPS):
                score += 6
        out.append((round(score, 1), h["id"], h.get("label", ""), h.get("description", ""), ", ".join(why)))
    return sorted(out, key=lambda r: -r[0])


def files_of(qid):
    d = get(f"{WD}?action=wbgetentities&ids={qid}&props=claims|labels&languages=en&format=json")
    ent = d["entities"][qid]
    out = []
    for prop in IMG_PROPS:
        for c in ent.get("claims", {}).get(prop, []):
            v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
            if isinstance(v, str):
                out.append(v)
    return ent.get("labels", {}).get("en", {}).get("value", qid), out


def commons_url(fname, width):
    name = fname.replace(" ", "_")
    h = hashlib.md5(name.encode()).hexdigest()
    base = f"https://upload.wikimedia.org/wikipedia/commons/{h[0]}/{h[:2]}/{urllib.parse.quote(name)}"
    ext = name.rsplit(".", 1)[-1].lower()
    if ext in ("svg", "tif", "tiff"):
        return f"https://upload.wikimedia.org/wikipedia/commons/thumb/{h[0]}/{h[:2]}/{urllib.parse.quote(name)}/{width}px-{urllib.parse.quote(name)}.png"
    return f"https://upload.wikimedia.org/wikipedia/commons/thumb/{h[0]}/{h[:2]}/{urllib.parse.quote(name)}/{width}px-{urllib.parse.quote(name)}"


def remove_bg(im):
    from rembg import new_session, remove
    sess = new_session("u2net")
    return remove(im, session=sess, alpha_matting=False)


def rounded_card(im, radius_frac=0.06, border=10):
    im = im.convert("RGB")
    w, h = im.size
    m = Image.new("L", (w * 2, h * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * 2 - 1, h * 2 - 1], radius=int(min(w, h) * radius_frac * 2), fill=255)
    m = m.resize((w, h), Image.LANCZOS)
    pad = border
    out = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    frame = Image.new("L", out.size, 0)
    ImageDraw.Draw(frame).rounded_rectangle([0, 0, out.width - 1, out.height - 1],
                                            radius=int(min(w, h) * radius_frac) + pad, fill=255)
    out.paste((255, 255, 255, 255), (0, 0), frame)
    out.paste(im, (pad, pad), m)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("query")
    ap.add_argument("out")
    ap.add_argument("--qid", help="Wikidata id, skips the search (e.g. Q7757)")
    ap.add_argument("--pick", type=int, default=1, help="which image of the entity (1 = main)")
    ap.add_argument("--keep-bg", action="store_true", help="keep background as a rounded photo card")
    ap.add_argument("--file", help="exact Commons file name, skips Wikidata")
    ap.add_argument("--width", type=int, default=960, help="Wikimedia thumbnail size: 500, 960, 1280, 1920")
    ap.add_argument("--crop-top", type=float, default=0, help="for people: keep only the top fraction (bust), e.g. 0.6")
    ap.add_argument("--kind", choices=["person", "place", "thing", ""], default="",
                    help="what is wanted (person is implied by --crop-top, place by --keep-bg): ranks the search hits")
    ap.add_argument("--year", type=int, default=0, help="the year of the story: a person must be alive then")
    args = ap.parse_args()

    if args.file:
        label, fname = args.query, args.file
    else:
        qid = args.qid
        if not qid:
            kind = args.kind or ("person" if args.crop_top else "place" if args.keep_bg else "")
            res = choose_entity(args.query, kind, args.year)
            if not res:
                sys.exit(f"no Wikidata entity for '{args.query}'")
            for r in res[:3]:
                print(f"  candidate {r[1]} (score {r[0]}): {r[2]} | {r[3]} [{r[4]}]")
            qid = res[0][1]
            if len(res) > 1 and res[0][0] - res[1][0] < 3:
                print(f"  doubt: {res[0][1]} and {res[1][1]} score close; check the photo, or pass --qid")
            print(f"entity: {qid} | {res[0][2]} | {res[0][3]}")
        label, files = files_of(qid)
        if not files:
            sys.exit(f"{qid} ({label}) has no image on Wikidata; try --qid of a related entity or --file")
        fname = files[min(args.pick, len(files)) - 1]
        print(f"using {qid} ({label}), image {min(args.pick, len(files))}/{len(files)}")
    print("file:", fname)
    # Flags, arms, emblems, maps and logos are flat artwork: background removal erases their white parts
    # (the French flag lost its white band), so they always keep their own background.
    if not args.keep_bg and any(k in (args.query + " " + fname).lower().replace("_", " ")
                                for k in ("flag", "coat of arms", "emblem", "map", "logo", "drapeau", "seal of")):
        args.keep_bg = True
        print("  flat artwork (flag/arms/map/logo): keeping its background")
    raw = None
    for w in (args.width, 1280, 500, 330, 0):  # allowed thumb sizes, then the original file
        try:
            raw = get(commons_url(fname, w) if w else commons_url(fname, 0).split("/thumb/")[0] + "/" + commons_url(fname, 0).split("/thumb/")[1].rsplit("/", 1)[0], binary=True, tries=5)
            break
        except Exception as e:
            print(f"  width {w} failed: {str(e)[:60]}")
    if raw is None:
        sys.exit("could not download the image")
    im = Image.open(io.BytesIO(raw))
    im = im.convert("RGBA") if im.mode in ("RGBA", "LA", "P") else im.convert("RGB")
    if args.crop_top:
        im = im.crop((0, 0, im.width, int(im.height * args.crop_top)))
    if args.keep_bg:
        out = rounded_card(im.convert("RGB"))
    else:
        out = remove_bg(im.convert("RGB")) if im.mode != "RGBA" or (np.asarray(im.getchannel("A")) > 250).mean() > 0.98 else im
        bb = out.getbbox()
        if bb:
            out = out.crop(bb)
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    from PIL.PngImagePlugin import PngInfo
    meta = PngInfo()
    meta.add_text("source", "wikimedia:" + fname)   # the renderer styles real photos differently
    out.save(args.out, pnginfo=meta)
    page = "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(fname.replace(" ", "_"))
    print(f"saved {args.out} {out.size}\ncredit: {page}")
    proj = os.path.dirname(args.out) or "."
    with open(os.path.join(proj, "sources.md"), "a", encoding="utf-8") as f:
        f.write(f"- صورة حقيقية ({label}) → {os.path.basename(args.out)}: {page}\n")


if __name__ == "__main__":
    main()
