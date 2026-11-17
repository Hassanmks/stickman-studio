"""Contact sheet of every flag next to its ISO code and country name -> engine/assets/flags/_check.png (look at it before renders).

    py -3 engine/scripts/flag_check.py
Renders the SVGs with headless Chromium (playwright), the same engine that draws them in the video.
"""
import asyncio, base64, json
from pathlib import Path
from playwright.async_api import async_playwright
A = Path(__file__).resolve().parent.parent
W = json.loads((A / 'data' / 'world.json').read_text(encoding='utf-8'))
ISO2 = {'ye': 'YEM', 'dj': 'DJI', 'er': 'ERI', 'sa': 'SAU', 'eg': 'EGY', 'so': 'SOM', 'us': 'USA', 'cn': 'CHN', 'fr': 'FRA', 'jp': 'JPN', 'it': 'ITA', 'in': 'IND', 'gb': 'GBR', 'ir': 'IRN', 'ae': 'ARE', 'om': 'OMN', 'ma': 'MAR'}
name = {c['iso']: (c['en'], c['ar']) for c in W['countries']}
cells = ''
for f in sorted((A / 'assets' / 'flags').glob('*.svg')):
    iso3 = ISO2.get(f.stem, '?'); en, ar = name.get(iso3, ('?', '?'))
    cells += f'<div class=c><img src="data:image/svg+xml;base64,{base64.b64encode(f.read_bytes()).decode()}"><b>{f.stem} / {iso3}</b><span>{en} - {ar}</span></div>'
html = f'<html><body style="margin:0;background:#222;font:20px sans-serif;color:#fff;display:flex;flex-wrap:wrap;width:1400px">{cells}<style>.c{{width:340px;padding:10px;display:flex;flex-direction:column;align-items:center}}.c img{{width:240px;height:180px;border:2px solid #fff}}</style></body></html>'
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(); pg = await b.new_page(viewport={'width': 1400, 'height': 400})
        await pg.set_content(html); await pg.wait_for_timeout(500)
        await pg.screenshot(path=str(A / 'assets' / 'flags' / '_check.png'), full_page=True); await b.close()
asyncio.run(main()); print(A / 'assets' / 'flags' / '_check.png')
