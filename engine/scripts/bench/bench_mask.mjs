// Speed plan item 3: compare the GPU land mask with the Canvas2D one, and time both, at chosen moments of a project.
//   node bench_mask.mjs <project> [t1,t2,...]      (default: every 2 s of the film)
// The project's web/map.js must be the v7 engine (DBG.maskCmp). Prints one JSON line per moment and a summary.
import { chromium } from 'playwright'; import http from 'http'; import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const SKILL = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const [, , proj, list] = process.argv;
const root = path.resolve(proj, 'web');
const types = { '.js': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.ttf': 'font/ttf', '.png': 'image/png', '.jpg': 'image/jpeg' };
const srv = http.createServer((q, s) => {
  const u = decodeURIComponent(q.url.split('?')[0]);
  const base = u.startsWith('/data/') || u.startsWith('/cache/') || u.startsWith('/assets/') ? SKILL : root;
  const p = path.join(base, u);
  if (!p.startsWith(base) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { s.writeHead(404); return s.end(); }
  s.writeHead(200, { 'Content-Type': types[path.extname(p)] || 'application/octet-stream' }); fs.createReadStream(p).pipe(s);
}).listen(0);
await new Promise(r => srv.on('listening', r));
const br = await chromium.launch({ args: ['--use-angle=d3d11', '--enable-gpu', '--ignore-gpu-blocklist'] });
const pg = await br.newPage({ viewport: { width: 1080, height: 1920 } });
pg.on('pageerror', e => console.log('PAGE ERROR', e.message));
await pg.goto(`http://localhost:${srv.address().port}/index.html`);
await pg.waitForFunction(() => window.READY || window.ERR, null, { timeout: 180000 });
const END = await pg.evaluate(() => window.END);
const ts = list ? list.split(',').map(Number) : Array.from({ length: Math.floor(END / 2) + 1 }, (_, i) => i * 2);
await pg.evaluate(() => { for (let i = 0; i < 3; i++) DBG.maskCmp(1); });   // warm-up (shader compile, first upload)
const R = [];
for (const t of ts) { const r = await pg.evaluate(t => DBG.maskCmp(t), t); R.push(r); console.log(JSON.stringify(r)); }
const avg = k => R.reduce((s, r) => s + r[k], 0) / R.length;
console.log(`\n${R.length} moments: CPU ${avg('cpuMs').toFixed(2)} ms, GPU ${avg('gpuMs').toFixed(2)} ms per mask; mean pixel diff ${avg('meanDiff').toFixed(4)}/255, worst ${Math.max(...R.map(r => r.meanDiff)).toFixed(4)}; max over128 ${Math.max(...R.map(r => r.over128))} px of ${270 * 480}`);
await br.close(); srv.close();
