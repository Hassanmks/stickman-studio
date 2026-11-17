"""Index engine/assets for map.js: sprite / photo / flag names -> file paths (relative to engine/assets).

    py -3 engine/scripts/asset_index.py      (run after adding any sprite, photo or flag)

ChatGPT sprites win over Fluent emoji with the same name (user rule: ChatGPT is the first choice, Fluent the fallback).
Keys: 'tanker' and 'sea/tanker' for sprites ('top/tanker' = seen from above, for motion), 'uss_carney' for photos, 'ye' for flags.
"""
import json
from pathlib import Path

A = Path(__file__).resolve().parent.parent / 'assets'
sp, ph, fl = {}, {}, {}
for f in sorted((A / 'emoji').glob('*.png')): sp[f.stem] = f'emoji/{f.name}'
for f in sorted((A / 'sprites').glob('*/*.png')):
    rel = f.relative_to(A).as_posix(); sp[f'{f.parent.name}/{f.stem}'] = rel
    # v6: sprites/top/ holds the straight-from-above views (nose up) that map.js turns to the heading of anything that MOVES.
    # They never take the bare name: 'tanker' stays the 3/4 view for a still object, 'top/tanker' is the moving one.
    if f.parent.name != 'top': sp[f.stem] = rel
for f in sorted((A / 'photos').glob('*.*')):
    if f.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp'): ph[f.stem] = f'photos/{f.name}'
for f in sorted((A / 'flags').glob('*.svg')): fl[f.stem] = f'flags/{f.name}'
(A / 'index.json').write_text(json.dumps({'sprites': sp, 'photos': ph, 'flags': fl}, ensure_ascii=False, indent=0), encoding='utf-8')
print(f'{len(sp)} sprite keys, {len(ph)} photos, {len(fl)} flags -> {A / "index.json"}')
