"""Check engine/data/places.json (speed plan item 9).

  py -3 engine/scripts/places.py          every place against the border data (logic() item 5), every lane on water and
                                          every pipeline on land (logic() item 2, smoothed exactly like path() draws it)
  py -3 engine/scripts/places.py list     the keys, Arabic names and spans, to pick from while writing a story

The check runs the real engine (map.js landAt / coastKm / smooth) in headless Chromium on a throwaway page, so it can
never disagree with the render's own logic gate.
"""
import json, shutil, subprocess, sys, tempfile
from pathlib import Path
HERE = Path(__file__).resolve().parent; ENG = HERE.parent
try: sys.stdout.reconfigure(encoding='utf-8')
except Exception: pass
D = json.loads((ENG / 'data' / 'places.json').read_text(encoding='utf-8'))
if len(sys.argv) > 1 and sys.argv[1] == 'list':
    for k, p in D['places'].items(): print(f"{k:<22s}{p['ar']:<28s}{p['in']:<5s}span {p['span']:<5}{p['pt']}")
    for g in ('lanes', 'pipelines'):
        for k, l in D.get(g, {}).items(): print(f"{g[:-1]:<9s}{k:<24s}{l['ar']}  ({len(l['pts'])} points)")
    sys.exit(0)
tmp = Path(tempfile.mkdtemp(prefix='maven_places_'))
try:
    shutil.copytree(ENG / 'engine', tmp / 'web')
    (tmp / 'web' / 'story.js').write_text("import { cam, end, start } from './map.js';\ncam(0, { lon: 50, lat: 25, span: 40 }); end(1); start();\n", encoding='utf-8')
    r = subprocess.run(['node', str(HERE / 'render.mjs'), str(tmp), 'places'], capture_output=True, text=True, encoding='utf-8', errors='replace')
    print(r.stdout[-20000:], r.stderr[-3000:])
    sys.exit(r.returncode)
finally: shutil.rmtree(tmp, ignore_errors=True)
