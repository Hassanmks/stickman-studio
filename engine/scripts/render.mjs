// Render a Maven Map project in headless Chromium on the GPU.
//   node render.mjs <project> frames <fps> [fromFrame] [toFrame]   -> <project>/frames/f00000.jpg ...
//   node render.mjs <project> stills 0.5,1.2,3.0 [outdir]          -> <project>/check/t_0.50.jpg ...
//   node render.mjs <project> cams <fps>                           -> <project>/cams.json (camera per frame, for prep.py)
// The page is <project>/web; /data and /cache are served from the skill folder (world borders, globe texture).
import { chromium } from 'playwright'; import http from 'http'; import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const SKILL = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [, , proj, mode, a, b, c] = process.argv;
if (!proj || !mode) { console.log('usage: node render.mjs <project> frames <fps> [from] [to] | stills t1,t2,... [outdir] | cams <fps>'); process.exit(1); }
const root = path.resolve(proj, 'web');
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.ttf': 'font/ttf', '.png': 'image/png', '.jpg': 'image/jpeg' };
let FRAMEDIR = null, written = 0, FORWARD = null;   // FORWARD: stream mode sends every JPEG on to stream.mjs
const srv = http.createServer((q, s) => {
  const u = decodeURIComponent(q.url.split('?')[0]);
  if (q.method === 'POST' && u.startsWith('/__frame/') && FORWARD) {   // v7 stream mode: pass the bytes on, answer once the sink has them (back-pressure)
    const parts = []; q.on('data', d => parts.push(d)); q.on('end', async () => { try { const r = await fetch(FORWARD + '/frame/' + path.basename(u), { method: 'POST', body: Buffer.concat(parts), headers: { 'x-black': q.headers['x-black'] || '0' } }); await r.arrayBuffer(); written++; s.writeHead(204); } catch (e) { console.log('sink error', e.message); s.writeHead(500); } s.end(); }); return;
  }
  if (q.method === 'POST' && u.startsWith('/__frame/') && FRAMEDIR) {   // a finished JPEG from an encoder worker, sent as raw bytes
    const parts = []; q.on('data', d => parts.push(d)); q.on('end', () => { fs.writeFileSync(path.join(FRAMEDIR, path.basename(u)), Buffer.concat(parts)); written++; s.writeHead(204); s.end(); }); return;
  }
  const base = u.startsWith('/data/') || u.startsWith('/cache/') || u.startsWith('/assets/') ? SKILL : root;
  const p = path.join(base, u);
  if (!p.startsWith(base) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { s.writeHead(404); return s.end(); }
  s.writeHead(200, { 'Content-Type': types[path.extname(p)] || 'application/octet-stream' }); fs.createReadStream(p).pipe(s);
}).listen(0);
await new Promise(r => srv.on('listening', r));
const br = await chromium.launch({ args: ['--use-angle=d3d11', '--enable-gpu', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'] });
const pg = await br.newPage({ viewport: { width: 1080, height: 1920 } });
pg.on('pageerror', e => console.log('PAGE ERROR', e.message)); pg.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log('CONSOLE', m.text()); });
await pg.goto(`http://localhost:${srv.address().port}/${process.env.PAGE || 'index.html'}`);
await pg.waitForFunction(() => window.READY || window.ERR, null, { timeout: 180000 });
const lg = await pg.evaluate(() => window.LOGIC || null);   // v6: the logic checklist of the story (map.js logic())
if (lg && (!process.env.QUIET_LOGIC || lg.fail.length)) { console.log('--- logic check (v6: logic in everything)'); for (const s of lg.ok) console.log('  OK   ' + s); if (!process.env.QUIET_LOGIC) for (const s of lg.info) console.log('       ' + s); for (const s of lg.fail) console.log('  FAIL ' + s); }
const err = await pg.evaluate(() => window.ERR); if (err) { console.log('story error:', err); await br.close(); srv.close(); process.exit(1); }
const gpu = await pg.evaluate(() => { const g = DBG.gl, e = g.getExtension('WEBGL_debug_renderer_info'); return e ? g.getParameter(e.UNMASKED_RENDERER_WEBGL) : '?'; });
if (/SwiftShader/i.test(gpu)) console.log('WARNING: rendering on SwiftShader (CPU), it will be slow:', gpu);
const END = await pg.evaluate(() => window.END);
if (mode === 'eval') {   // debugging: node render.mjs <project> eval "<expression>" -> its JSON value
  console.log(JSON.stringify(await pg.evaluate(a), null, 1)); await br.close(); srv.close(); process.exit(0);
}
if (mode === 'places') {   // item 9: test engine/data/places.json against the borders (map.js checkPlaces)
  const R = await pg.evaluate(() => window.DBG.checkPlaces());
  for (const x of R.ok) console.log('  OK   ' + x); for (const x of R.fail) console.log('  FAIL ' + x);
  console.log(`places check: ${R.ok.length} ok, ${R.fail.length} failed`); await br.close(); srv.close(); process.exit(R.fail.length ? 2 : 0);
}
if (mode === 'cams') {
  const cams = await pg.evaluate(f => window.CAMS_AT(f), +(a || 30));
  fs.writeFileSync(path.join(proj, 'cams.json'), JSON.stringify({ end: END, cams })); console.log(`cams.json: ${cams.length} frames, ${END.toFixed(2)} s`);
  // v7: the story's events too, so sound.py / mix.py can run while the frames render (they only need shots.json)
  fs.writeFileSync(path.join(proj, 'shots.json'), JSON.stringify({ end: END, shots: await pg.evaluate(() => window.SHOTLIST), events: await pg.evaluate(() => window.EVENTS) }));
  await br.close(); srv.close(); process.exit(0);
}
if (mode === 'stream') {
  // v7 (speed plan items 5 + 6): take chunks of 60 fps frames from stream.mjs until none are left. Every frame k is the
  // 50/50 blend of the 120 fps sub-frames 2k-1 and 2k (what ffmpeg tmix=frames=2 + fps=60 made of the old files),
  // blended here on the GPU canvas, then JPEG-encoded once by the encoder workers and forwarded to the sink.
  FORWARD = a; const ENC = +(process.env.ENCODERS || 3), Q = +(process.env.JPEGQ || .93); let n = 0; const ts = Date.now();
  await pg.evaluate(([ENC, Q]) => {
    // the black-frame gate runs here, off the main thread, on a 1/8 copy (the rule of blackcheck.py: a lost map is black everywhere)
    const src = `const sc = new OffscreenCanvas(135, 240), sx = sc.getContext('2d', { willReadFrequently: true });
      onmessage = async e => { const { bmp, name } = e.data; const oc = new OffscreenCanvas(bmp.width, bmp.height); oc.getContext('2d').drawImage(bmp, 0, 0);
      sx.drawImage(bmp, 0, 0, 135, 240); bmp.close(); const d = sx.getImageData(0, 0, 135, 240).data; let all = 0, mid = 0, nm = 0;
      for (let i = 0, p = 0; i < d.length; i += 4, p++) { const y = .299 * d[i] + .587 * d[i + 1] + .114 * d[i + 2]; all += y; const r = (p / 135) | 0; if (r >= 108 && r < 144) { mid += y; nm++; } }
      const black = mid / nm < 14 && all / (135 * 240) < 9;
      const blob = await oc.convertToBlob({ type: 'image/jpeg', quality: ${Q} }); await fetch(e.data.origin + '/__frame/' + name, { method: 'POST', body: blob, headers: { 'x-black': black ? '1' : '0' } }); postMessage(1); };`;
    const url = URL.createObjectURL(new Blob([src], { type: 'text/javascript' }));
    const ws = Array.from({ length: ENC }, () => { const w = new Worker(url); w.busy = 0; return w; });
    let wait = null; const done = () => new Promise(r => wait = r);
    ws.forEach(w => w.onmessage = () => { w.busy--; if (wait) { const r = wait; wait = null; r(); } });
    const cv = document.getElementById('c'), X = cv.getContext('2d');
    const sub = k => [Math.max(0, 2 * k - 1) / 120, 2 * k / 120];
    window.__chunk = async (f0, f1, blend) => {
      try {
        if (!blend) {   // default: every 120 fps sub-frame on its own, the same JPEG as frames/f%05d.jpg always was
          const T = []; for (let f = f0; f <= f1; f++) T.push(f / 120); if (window.PLAN) window.PLAN(T);
          for (let f = f0; f <= f1; f++) {
            if (window.NEED) await window.NEED(f / 120); window.seek(f / 120); const bmp = await createImageBitmap(cv);
            while (ws.every(w => w.busy >= 2)) await done();
            const w = ws.reduce((p, q) => q.busy < p.busy ? q : p); w.busy++; w.postMessage({ bmp, name: String(f), origin: location.origin }, [bmp]);
          }
          while (ws.some(w => w.busy > 0)) await done(); return 'ok';
        }
        const T = []; for (let k = f0; k <= f1; k++) T.push(...sub(k)); if (window.PLAN) window.PLAN(T);
        for (let k = f0; k <= f1; k++) {
          const [ta, tb] = sub(k);
          if (window.NEED) await window.NEED(ta); window.seek(ta); const a = await createImageBitmap(cv);
          if (window.NEED) await window.NEED(tb); window.seek(tb);
          X.save(); X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = .5; X.globalCompositeOperation = 'source-over'; X.filter = 'none'; X.drawImage(a, 0, 0); X.restore(); a.close();
          const bmp = await createImageBitmap(cv);
          while (ws.every(w => w.busy >= 2)) await done();
          const w = ws.reduce((p, q) => q.busy < p.busy ? q : p); w.busy++; w.postMessage({ bmp, name: String(k), origin: location.origin }, [bmp]);
        }
        while (ws.some(w => w.busy > 0)) await done();
      } catch (e) { return 'ERR' + e.stack; }
      return 'ok';
    };
  }, [ENC, Q]);
  for (;;) {
    const c = await (await fetch(FORWARD + '/next')).json(); if (c.done) break;
    const r = await pg.evaluate(([f0, f1, bl]) => window.__chunk(f0, f1, bl), [c.f0, c.f1, !!c.blend]);
    if (r !== 'ok') { console.log('render error:', r.slice(3)); process.exit(1); }
    n += c.f1 - c.f0 + 1;
  }
  const pst = await pg.evaluate(() => window.PT_STAT || null);
  if (!process.env.QUIET) fs.writeFileSync(path.join(proj, 'shots.json'), JSON.stringify({ end: END, shots: await pg.evaluate(() => window.SHOTLIST), events: await pg.evaluate(() => window.EVENTS) }));
  console.log(`page done: ${n} frames in ${((Date.now() - ts) / 1000).toFixed(1)} s` + (pst ? `, patches ${pst.loaded} loaded / ${pst.freed} freed / peak ${pst.peak}` : '') + ` (${gpu})`);
  await br.close(); srv.close(); process.exit(0);
}
let jobs = [], outdir;
if (mode === 'stills') { outdir = path.resolve(b || path.join(proj, 'check')); jobs = a.split(',').map(Number).map(t => [t, `t_${t.toFixed(2)}.jpg`]); }
else { outdir = path.resolve(proj, 'frames'); const fps = +a; const f0 = +(b || 0), f1 = c != null ? +c : Math.round(END * fps) - 1; for (let f = f0; f <= f1; f++) jobs.push([f / fps, `f${String(f).padStart(5, '0')}.jpg`]); fs.writeFileSync(path.join(proj, 'render.json'), JSON.stringify({ fps, end: END })); }
fs.mkdirSync(outdir, { recursive: true });
const t0 = Date.now();
if (mode === 'frames' && !process.env.SERIAL) {
  // Pipelined: the page draws frame N, hands a GPU snapshot (ImageBitmap) to a pool of Web Workers and moves on to frame N+1
  // at once; each worker encodes its JPEG on its own thread (same Skia encoder, same quality 0.93) and POSTs the raw bytes
  // to this server. Before (2026-11-16): the page stalled ~41 ms per frame in toDataURL + 10 ms of base64 transfer.
  FRAMEDIR = outdir; const ENC = +(process.env.ENCODERS || 3);
  const r = await pg.evaluate(async ([jobs, ENC]) => {
    const src = `onmessage = async e => { const { bmp, name } = e.data; const oc = new OffscreenCanvas(bmp.width, bmp.height); oc.getContext('2d').drawImage(bmp, 0, 0); bmp.close();
      const blob = await oc.convertToBlob({ type: 'image/jpeg', quality: .93 }); await fetch(e.data.origin + '/__frame/' + name, { method: 'POST', body: blob }); postMessage(1); };`;
    const url = URL.createObjectURL(new Blob([src], { type: 'text/javascript' }));
    const ws = Array.from({ length: ENC }, () => { const w = new Worker(url); w.busy = 0; return w; });
    let wait = null; const done = () => new Promise(r => wait = r);
    ws.forEach(w => w.onmessage = () => { w.busy--; if (wait) { const r = wait; wait = null; r(); } });
    const cv = document.getElementById('c');
    try {
      if (window.PLAN) window.PLAN(jobs.map(j => j[0]));   // v7: this page loads only the satellite patches of its own frames
      for (const [t, name] of jobs) {
        if (window.NEED) await window.NEED(t);
        window.seek(t); const bmp = await createImageBitmap(cv);
        while (ws.every(w => w.busy >= 2)) await done();
        const w = ws.reduce((a, b) => b.busy < a.busy ? b : a); w.busy++; w.postMessage({ bmp, name, origin: location.origin }, [bmp]);
      }
      while (ws.some(w => w.busy > 0)) await done();
    } catch (e) { return 'ERR' + e.stack; }
    return 'ok';
  }, [jobs, ENC]);
  if (r !== 'ok') { console.log('render error:', r.slice(3)); process.exit(1); }
  while (written < jobs.length) await new Promise(r => setTimeout(r, 20));
} else for (const [t, name] of jobs) { const d = await pg.evaluate(async t => { try { if (window.NEED) await window.NEED(t); window.seek(t); } catch (e) { return "ERR" + e.stack; } return document.getElementById("c").toDataURL("image/jpeg", 0.93); }, t); if (d.startsWith("ERR")) { console.log(`frame ${t.toFixed(2)} s:`, d.slice(3)); process.exit(1); } fs.writeFileSync(path.join(outdir, name), Buffer.from(d.split(',')[1], 'base64'));
  if (mode === 'stills') { const n = await pg.evaluate(t => { try { const n = window.DBG && window.DBG.alive ? window.DBG.alive(t) : -1; window.seek(t); return n; } catch { return -1; } }, t); if (n >= 0) fs.writeFileSync(path.join(outdir, name.replace(/\.jpg$/, '.alive')), String(n)); } }   // living map phase A: how many living things the look sheet shows under each moment
const pst = await pg.evaluate(() => window.PT_STAT || null); if (pst) console.log(`satellite patches: ${pst.loaded} loaded, ${pst.freed} freed, at most ${pst.peak} in GPU memory at once`);
console.log(`done ${jobs.length} frames in ${outdir}, ${((Date.now() - t0) / Math.max(1, jobs.length)).toFixed(0)} ms/frame (${gpu})`);
const shots = await pg.evaluate(() => window.SHOTLIST), events = await pg.evaluate(() => window.EVENTS);
fs.writeFileSync(path.join(proj, 'shots.json'), JSON.stringify({ end: END, shots, events }));
console.log('shots', JSON.stringify(shots));
await br.close(); srv.close();
