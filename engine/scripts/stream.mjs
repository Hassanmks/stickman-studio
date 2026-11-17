// Speed plan items 5 + 6 (2026-11-17): render and encode at the same time.
//   node stream.mjs <project> <video_out.mp4> [workers]
// - N headless pages (render.mjs <project> stream <sink>) take small chunks of the film from one shared queue, so every
//   page works near the same moment and the frames arrive almost in order.
// - This process puts the JPEGs back in order and pipes them straight into ONE ffmpeg, and also writes them to
//   <project>/frames/ (black-frame gate, resume, fallback).
// - Default (zero-loss by construction): the pages send every 120 fps sub-frame as the same JPEG as before (quality .93,
//   f%05d.jpg) and ffmpeg runs the very same filter and encoder as the approved path: tmix=frames=2 + fps=60 +
//   yuv420p + x264 CRF 18 preset slow. x264 therefore sees exactly the input it saw before.
//   BLEND=1 (measured, not used): the page blends the two sub-frames itself and sends one JPEG per 60 fps frame. Half
//   the JPEGs, but the extra JPEG pass of the blend cost ~0.001 SSIM against the source, so it is off (user rule: no loss).
// Prints "STREAM {json}" with the counts and times at the end.
import http from 'http'; import fs from 'fs'; import path from 'path'; import { spawn } from 'child_process'; import { fileURLToPath } from 'url';
const HERE = path.dirname(fileURLToPath(import.meta.url));
const [, , proj, out, wArg] = process.argv;
const cfg = fs.existsSync(path.join(proj, 'config.json')) ? JSON.parse(fs.readFileSync(path.join(proj, 'config.json'), 'utf8')) : {};
const END = JSON.parse(fs.readFileSync(path.join(proj, 'cams.json'), 'utf8')).end;
const BLEND = !!process.env.BLEND, FPS = BLEND ? 60 : 120, PRE = BLEND ? 'v' : 'f';
const K = Math.round(END * FPS);                                 // frames the pages make (120 fps: 10723 for v6, as frames/ had)
const WORKERS = +(wArg || process.env.WORKERS || cfg.workers || 3), CHUNK = +(process.env.CHUNK || (BLEND ? 30 : 60));
const LIMIT = +(process.env.BUFFER || 480);                       // frames held out of order before the queue waits (~300 MB)
const fdir = path.join(proj, 'frames'); fs.rmSync(fdir, { recursive: true, force: true }); fs.mkdirSync(fdir, { recursive: true });
const black = [];                                                 // frames the pages' encoder threads found black (the gate of blackcheck.py)
const t0 = Date.now(); let next = 0, flushed = 0, got = 0, maxBuf = 0, waits = 0, writing = 0; const buf = new Map(), pend = [];
const enc = BLEND ? ['-vf', 'format=yuv420p'] : ['-vf', 'tmix=frames=2:weights=1 1,fps=60,format=yuv420p', '-t', String(END)];
const ff = spawn('ffmpeg', ['-hide_banner', '-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
  ...(process.env.NOENC ? ['-f', 'null', '-'] :                  // NOENC=1: measure the render alone (benchmarks only)
  [...enc, '-c:v', 'libx264', '-crf', '18', '-preset', 'slow', '-fflags', '+bitexact', '-an', out])], { stdio: ['pipe', 'inherit', 'inherit'] });
let ffDone = new Promise(r => ff.on('close', c => r(c)));
ff.stdin.on('error', () => {});                                   // ffmpeg stops reading at -t END: the last sub-frame may be refused
let drainWait = null; ff.stdin.on('drain', () => { if (drainWait) { const r = drainWait; drainWait = null; r(); } });
let flushing = false;
async function flush() {
  if (flushing) return; flushing = true;
  while (buf.has(flushed)) {
    const b = buf.get(flushed); buf.delete(flushed); flushed++;
    if (!ff.stdin.destroyed && !ff.stdin.write(b)) await new Promise(r => { drainWait = r; ff.once('close', r); });
    while (pend.length && buf.size < LIMIT) pend.shift()();
  }
  flushing = false;
  if (flushed === K && !ff.stdin.destroyed) ff.stdin.end();
}
function nextChunk() {
  if (next >= K) return null;
  const c = [next, Math.min(K, next + CHUNK) - 1]; next = c[1] + 1; return c;
}
const srv = http.createServer((q, s) => {
  const u = q.url;
  if (u === '/next') {
    const give = () => { const c = nextChunk(); s.writeHead(200, { 'Content-Type': 'application/json' }); s.end(JSON.stringify(c ? { f0: c[0], f1: c[1], blend: BLEND } : { done: true })); };
    if (buf.size >= LIMIT && next < K) { waits++; pend.push(give); } else give();
    return;
  }
  const m = u.match(/^\/frame\/(\d+)$/);
  if (q.method === 'POST' && m) {
    const k = +m[1], parts = []; q.on('data', d => parts.push(d)); if (q.headers['x-black'] === '1') black.push(k);
    q.on('end', () => {
      const b = Buffer.concat(parts); buf.set(k, b); got++; maxBuf = Math.max(maxBuf, buf.size);
      writing++; fs.writeFile(path.join(fdir, `${PRE}${String(k).padStart(5, '0')}.jpg`), b, () => writing--);
      s.writeHead(204); s.end(); flush();
    }); return;
  }
  s.writeHead(404); s.end();
}).listen(0);
await new Promise(r => srv.on('listening', r));
const SINK = `http://localhost:${srv.address().port}`;
console.log(`stream: ${K} frames at ${FPS} fps (${END.toFixed(2)} s${BLEND ? ', blended in the page' : ', tmix to 60 in ffmpeg'}), ${WORKERS} pages, chunks of ${CHUNK}`);
const ws = Array.from({ length: WORKERS }, (_, i) => spawn('node', [path.join(HERE, 'render.mjs'), proj, 'stream', SINK], { stdio: 'inherit', env: { ...process.env, ...(i ? { QUIET_LOGIC: '1', QUIET: '1' } : {}) } }));
const prog = setInterval(() => console.log(`PROGRESS ${got}/${K}`), 2000);   // TaleSpark host: render progress for the phone
const codes = await Promise.all(ws.map(w => new Promise(r => w.on('close', r))));
clearInterval(prog);
const tRender = (Date.now() - t0) / 1000;
if (codes.some(c => c)) { console.log('a render page failed'); ff.stdin.destroy(); ff.kill(); srv.close(); process.exit(1); }
if (got !== K) { console.log(`only ${got} of ${K} frames arrived`); ff.kill(); srv.close(); process.exit(1); }
const code = await ffDone; srv.close();
while (writing > 0) await new Promise(r => setTimeout(r, 10));   // every JPEG is on disk before we report
const tAll = (Date.now() - t0) / 1000;
black.sort((x, y) => x - y); fs.writeFileSync(path.join(fdir, '_black.json'), JSON.stringify(black));
console.log('STREAM ' + JSON.stringify({ black: black.length, frames: K, fps_in: FPS, prefix: PRE, render_s: +tRender.toFixed(1), total_s: +tAll.toFixed(1), out_fps: +(Math.round(END * 60) / tAll).toFixed(1), max_buffer: maxBuf, queue_waits: waits, ffmpeg: code }));
process.exit(code ? 1 : 0);
