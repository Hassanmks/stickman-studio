// TaleSpark 2.0, item 5: the automatic director (2026-11-17).
//   node director.mjs <project>     -> <project>/web/story.js (the previous one kept as web/story.prev.js) + director.json
// Reads script.txt, web/timing.json and shotplan.json (the [خطة اللقطات] Gemini wrote, parsed by script_check.mjs; optional)
// and writes a complete story with the rules learned on hormuz-v2:
//  - one shot per script line, framed on what the plan and the words name (place > places together > countries > lane);
//    the move ARRIVES early in the line, then a hold key at its end keeps a slow push (a long move never drifts away)
//  - the plan's effect: غوص dives from the previous frame, ابتعاد pulls out, مقارنة frames two places, تتبع frames the lane,
//    ضربة = punch + shake, خطر = red title + shake + night map, ليل = night map for the line
//  - regions only for countries that are SPOKEN (picture = speech), at most two flags per line (the rest in colour), labels
//    for places from engine/data/places.json, sea lanes / pipelines from the plan, moving sprites only along a known lane
//  - numbers with their unit (the engine's own number words), titles for the hook, short questions, the plan's titles and
//    the payoff, and a closed loop: the last frame goes back to the first one
// The logic gate runs on the result (build.py director); what it rejects is removed and reported, never forced.
import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const ENG = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const proj = path.resolve(process.argv[2] || ''); if (!process.argv[2]) { console.log('usage: node director.mjs <project>'); process.exit(1); }
const J = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const TIM = J(path.join(proj, 'web', 'timing.json')), WORDS = TIM.words;
const WORLD = J(path.join(ENG, 'data', 'world.json')), PL = J(path.join(ENG, 'data', 'places.json'));
const PLAN = fs.existsSync(path.join(proj, 'shotplan.json')) ? J(path.join(proj, 'shotplan.json')) : [];
const DROP = new Set(fs.existsSync(path.join(proj, 'director.drop.json')) ? J(path.join(proj, 'director.drop.json')) : []);   // items the logic gate rejected
const MAP = fs.readFileSync(path.join(ENG, 'engine', 'map.js'), 'utf8');
const grab = n => { const m = MAP.match(new RegExp(`const ${n} = (\\{[\\s\\S]*?\\});`)); if (!m) throw new Error('map.js: ' + n + ' not found'); return Function('return ' + m[1])(); };
const NUMW = grab('NUMW'), ALIAS = grab('ALIAS'), ISO2 = grab('ISO2'), CRAFT = grab('CRAFT');
const TOPS = new Set(fs.readdirSync(path.join(ENG, 'assets', 'sprites', 'top')).map(f => f.replace(/\.png$/, '')));
// TaleSpark 2.2: objects named in the words (engine/data/objects.json) and the still sprites we have (3/4 view, any set but top/)
const OBJS = J(path.join(ENG, 'data', 'objects.json')).objects;
const STILLS = new Set(fs.readdirSync(path.join(ENG, 'assets', 'sprites'), { withFileTypes: true }).filter(d => d.isDirectory() && d.name !== 'top')
  .flatMap(d => fs.readdirSync(path.join(ENG, 'assets', 'sprites', d.name)).filter(f => f.endsWith('.png')).map(f => f.replace(/\.png$/, ''))));
const objectsAt = (a, b) => { const out = [];   // [{o, i}] first word of each object named between word a and b
  for (const o of OBJS) for (const kw of o.ar) { const k = kw.split(/\s+/).map(nw); let hit = -1;
    for (let i = a; i <= b && hit < 0; i++) if (k.every((t, j) => i + j <= b && (j === 0 ? forms(WORDS[i][0]).has(t) : forms(WORDS[i + j][0]).has(t)))) hit = i;
    if (hit >= 0) { out.push({ o, i: hit }); break; } }
  return out; };
const shownObj = new Set();

// ---- words (the engine's normaliser), anchors T('word#n'), lines of the script -> word ranges
const nw = w => String(w).toLowerCase().replace(/[ً-ْـ]/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/[^\p{L}\p{N}]/gu, '');
const forms = w => { const S = new Set([nw(w)]); for (const x of [...S]) if (x.length > 3 && 'وفبكل'.includes(x[0])) S.add(x.slice(1)); for (const x of [...S]) if (x.length > 4 && (x.startsWith('ال') || x.startsWith('لل'))) S.add(x.slice(2)); return S; };
const clean = w => w.replace(/[^\p{L}\p{M}\p{N}]/gu, '');
const N = WORDS.map(w => nw(w[0]));
const r2 = x => Math.round(x * 100) / 100, r3 = x => Math.round(x * 1000) / 1000;
const A = (i, end = false, off = 0) => { const n = N.slice(0, i + 1).filter(x => x === N[i]).length;
  return `T('${clean(WORDS[i][0])}${n > 1 ? '#' + n : ''}${end ? '.end' : ''}${off ? (off > 0 ? '+' : '') + r2(off) : ''}')`; };
const ts = i => WORDS[i][1], te = i => WORDS[i][2];
const script = fs.readFileSync(path.join(proj, 'script.txt'), 'utf8').split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#'));
const LINES = []; { let k = 0; for (const l of script) { const n = l.split(/\s+/).filter(Boolean).length; LINES.push([k, Math.min(WORDS.length, k + n) - 1]); k += n; } }
if (LINES.length && LINES[LINES.length - 1][1] !== WORDS.length - 1) console.log(`director: warning, script.txt has ${LINES[LINES.length - 1][1] + 1} words, timing.json ${WORDS.length}`);

// ---- what a word names (same rules as draft.mjs)
const SHORT = { OMN: ['عمان'], ARE: ['الامارات'], SAU: ['السعوديه'], USA: ['امريكا', 'الامريكيه', 'الامريكي', 'الولايات المتحده'] };
const CTR = WORLD.countries.map(c => ({ c, seqs: [c.ar.split(/\s+/).map(nw), ...[...(ALIAS[c.iso] || []), ...(SHORT[c.iso] || [])].map(a => a.split(/\s+/).map(nw))] }));
const countryAt = i => { for (const { c, seqs } of CTR) for (const q of seqs) if (q.every((tk, j) => i + j < WORDS.length && (j === 0 ? forms(WORDS[i][0]).has(tk) : N[i + j] === tk))) return { c, len: q.length }; return null; };
const PLK = []; for (const [k, p] of Object.entries(PL.places)) for (const a of new Set([...(p.aka || []), p.ar])) PLK.push({ k, toks: a.split(/\s+/).map(nw) });
PLK.sort((a, b) => b.toks.length - a.toks.length);
const placeAt = i => { for (const P of PLK) if (P.toks.every((tk, j) => i + j < WORDS.length && (j === 0 ? forms(WORDS[i][0]).has(tk) : N[i + j] === tk))) return { ...P, len: P.toks.length }; return null; };
const numv = w => { let s = nw(w); if (/^\d+$/.test(s)) return +s; if (NUMW[s] == null && s.length > 2 && 'وفبل'.includes(s[0])) s = s.slice(1); if (NUMW[s] == null && s.startsWith('ال')) s = s.slice(2); if (NUMW[s] == null && s.length > 3 && s.endsWith('ا') && NUMW[s.slice(0, -1)] != null) s = s.slice(0, -1); return NUMW[s]; };
const SCALE = { 1000: 'ألف', 1e6: 'مليون', 1e9: 'مليار' };
const DANGER = ['اغلاق', 'اغلق', 'يغلق', 'تغلق', 'صاروخ', 'صواريخ', 'هجوم', 'حرب', 'تهديد', 'انفجار', 'ضربه', 'يقصف', 'مسيره', 'الحرب', 'الخطر', 'خطر', 'تهاجم', 'هاجم'];
const CNAME = c => ({ ARE: 'الإمارات', OMN: 'سلطنة عُمان', USA: 'الولايات المتحدة', GBR: 'بريطانيا' }[c.a3] || c.ar);

// ---- geometry
const kx = lat => Math.cos(lat * Math.PI / 180);
const spanOfBox = ([w, s, e, n], min = 1.2) => Math.min(95, Math.max(min, (e - w) * kx((s + n) / 2) * 1.35, (n - s) * 1.35 * 9 / 16));
const boxOf = pts => [Math.min(...pts.map(p => p[0])), Math.min(...pts.map(p => p[1])), Math.max(...pts.map(p => p[0])), Math.max(...pts.map(p => p[1]))];
const merge = (a, b) => [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])];
const frame = (bb, min) => ({ lon: r3((bb[0] + bb[2]) / 2), lat: r3((bb[1] + bb[3]) / 2), span: r2(spanOfBox(bb, min)) });
const hav = (a, b) => { const R = Math.PI / 180, dl = (b[1] - a[1]) * R, dn = (b[0] - a[0]) * R, h = Math.sin(dl / 2) ** 2 + Math.cos(a[1] * R) * Math.cos(b[1] * R) * Math.sin(dn / 2) ** 2; return 12742 * Math.asin(Math.sqrt(h)); };
const far = (a, b) => Math.hypot((a.lon - b.lon) * kx(a.lat), a.lat - b.lat) / Math.min(a.span, b.span) + Math.abs(Math.log(a.span / b.span));
const onLand = ([x, y]) => { const X = x * 1e4, Y = y * 1e4;
  for (const c of WORLD.countries) { if (x < c.bb[0] || x > c.bb[2] || y < c.bb[1] || y > c.bb[3]) continue; let ins = false;
    for (const r of c.r) for (let i = 0, j = r.length - 2; i < r.length; j = i, i += 2) { const xi = r[i], yi = r[i + 1], xj = r[j], yj = r[j + 1]; if ((yi > Y) !== (yj > Y) && X < (xj - xi) * (Y - yi) / (yj - yi) + xi) ins = !ins; }
    if (ins) return true; } return false; };
// a short approach through open water to a place (a fleet coming to its base): the first of 16 headings that stays at sea
const seaApproach = (pt, span) => { const L = span * .45;
  for (let k = 0; k < 16; k++) { const a = (k * 67.5 + 45) * Math.PI / 180, end = [pt[0] + Math.cos(a) * L * .25 / kx(pt[1]), pt[1] + Math.sin(a) * L * .25], st = [pt[0] + Math.cos(a) * L / kx(pt[1]), pt[1] + Math.sin(a) * L];
    const pts = Array.from({ length: 9 }, (_, i) => [r3(st[0] + (end[0] - st[0]) * i / 8), r3(st[1] + (end[1] - st[1]) * i / 8)]);
    if (pts.every(q => !onLand(q))) return [pts[0], pts[4], pts[8]]; } return null; };
const ROUTE = k => PL.lanes[k] ? { kind: 'lane', pts: PL.lanes[k].pts, call: `lane('${k}')` } : (PL.pipelines || {})[k] ? { kind: 'pipe', pts: PL.pipelines[k].pts, call: `pipeline('${k}')` } : null;

const out = [], cams = [], used = new Set(['cam', 'T', 'start', 'end', 'captions']), report = { lines: [], dropped: [], notes: [] };
const use = (...n) => n.forEach(x => used.add(x));
let id = 0; report.items = [];
const item = (src, why, t = null) => { const k = `d${++id}`; report.items.push({ id: k, why, t: t == null ? null : r2(t) }); if (DROP.has(k)) { report.dropped.push({ id: k, why, src }); return; } out.push(`${src}   // ${k}`); };
const emph = new Set(); let prev = null, firstFrame = null, night = false, lastTitleEnd = -1;
const numbersSeen = [];
const COLS = ['#ffd23f', '#2fd6c8', '#ff8a3d', '#b48cff', '#5fd36b'];

LINES.forEach(([a, b], li) => {
  const p = PLAN.find(x => x.n === li + 1) || PLAN[li] || {}, L0 = ts(a), L1 = te(b), nextA = li + 1 < LINES.length ? LINES[li + 1][0] : null;
  const UNTIL = nextA != null ? A(nextA) : A(b, true, .6), words = WORDS.slice(a, b + 1).map(w => w[0]);
  out.push(`\n// ---------------------------------------------------------------- ${r2(L0)}-${r2(L1)} s  «${words.join(' ').slice(0, 80)}»`);
  // ---- what the line names
  const ents = [];
  for (let i = a; i <= b; i++) {
    const pl = placeAt(i); if (pl) { ents.push({ kind: 'place', i, k: pl.k, p: PL.places[pl.k] }); i += pl.len - 1; continue; }
    const cm = countryAt(i); if (cm) { if (!ents.some(e => e.kind === 'country' && e.c.a3 === cm.c.a3)) ents.push({ kind: 'country', i, c: cm.c }); i += cm.len - 1; continue; }
    const v = numv(WORDS[i][0]);
    if (v != null) {
      let j = i, tot = 0, cur = 0; const vals = [];
      while (j <= b && numv(WORDS[j][0]) != null && (j === i || N[j].startsWith('و') || numv(WORDS[j][0]) >= 100 || (numv(WORDS[j][0]) === 10 && numv(WORDS[j - 1][0]) < 10)) && !(j > i && /[،,؛;:.!?؟…]$/.test(WORDS[j - 1][0]))) { const x = numv(WORDS[j][0]); if (x >= 1000) { tot += (cur || 1) * x; cur = 0; } else if (x === 100) cur = (cur || 1) * 100; else cur += x; vals.push(x); j++; }
      const val = tot + cur, last = vals[vals.length - 1], nxt = j <= b ? N[j] : '', nxt2 = j + 1 <= b ? N[j + 1] : '';
      const unitKm = nxt.startsWith('كيلو') || nxt === 'كم', pct = (nxt === 'في' && /^(ال)?(مئه|ميه|مائه)$/.test(nxt2)) || /^(بال|ال)?(مئه|ميه|مائه)$/.test(nxt);
      const planned = p.number && String(p.number).match(/[\d.]+/) && +String(p.number).match(/[\d.]+/)[0];
      if (vals.length === 1 && val <= 10 && !unitKm && !pct && planned !== val) { i = j - 1; continue; }   // a lone small word is not a figure
      let to = val, fmt = '#', k = j;
      // a big number reads in its biggest scale: «اثني عشر مليارا وستمئة وخمسين مليون» = 12.65 مليار (not 12,650 مليون, not 50 مليون)
      const big = Math.max(0, ...vals.filter(x => x === 1e6 || x === 1e9)); let say = null, dec = 0;
      if (big && val >= big && vals.length > 1) { to = +(val / big).toFixed(2); fmt = `# ${SCALE[big]}`; dec = Number.isInteger(to) ? 0 : String(to).split('.')[1].length;
        if (dec) say = clean(WORDS[i + vals.lastIndexOf(big)][0]); }   // 12.65 is never spoken as such: tie it to its scale word
      else if (SCALE[last] && vals.length > 1) { to = val / last; fmt = `# ${SCALE[last]}`; }
      if (unitKm) { fmt = '# كم'; k = j + 1; } else if (pct) { fmt = '#%'; k = nxt === 'في' ? j + 2 : j + 1; }
      const sub = []; if (!/[،,؛;:.!?؟…]$/.test(WORDS[k - 1][0])) for (let q = k; q <= b && sub.length < 5; q++) {   // a pause right after the number or its unit ends it: the next clause is not its sub-line
        sub.push(clean(WORDS[q][0])); if (/[،,؛;:.!?؟…]$/.test(WORDS[q][0])) break; }
      const dup = numbersSeen.some(x => x.to === to && x.fmt === fmt); numbersSeen.push({ to, fmt });
      ents.push({ kind: 'number', i, to, fmt, sub: sub.join(' '), dup, end: k - 1, dec, say }); i = k - 1; continue;
    }
    if (DANGER.some(d => forms(WORDS[i][0]).has(d))) ents.push({ kind: 'danger', i });
  }
  const places = ents.filter(e => e.kind === 'place'), ctrs = ents.filter(e => e.kind === 'country'), nums = ents.filter(e => e.kind === 'number');
  // the plan's own place (a key, or a new place with its coordinates) comes first
  let main = null;
  if (p.place && typeof p.place === 'string' && PL.places[p.place]) main = { k: p.place, p: PL.places[p.place] };
  else if (p.place && p.place.new) main = { new: true, p: { ar: p.place.ar, pt: [p.place.lon, p.place.lat], span: 1.5 } };
  else if (places.length) main = places[0];
  const route = p.lane ? ROUTE(p.lane) : null;
  const fx = p.fx || null;
  // ---- the frame of the line
  let F = null, why = '';
  const planC = (p.countries || []).map(c => WORLD.countries.find(x => x.a3 === c)).filter(Boolean);
  if (fx === 'تتبع' && route) {   // the whole route, with the place it leaves and the country that holds it (Qatar + its LNG lane)
    let bb = boxOf(route.pts); if (main) bb = merge(bb, [main.p.pt[0], main.p.pt[1], main.p.pt[0], main.p.pt[1]]);
    for (const e of ctrs) if (main && e.c.bb[0] <= main.p.pt[0] && main.p.pt[0] <= e.c.bb[2] && e.c.bb[1] <= main.p.pt[1] && main.p.pt[1] <= e.c.bb[3]) bb = merge(bb, e.c.bb);
    F = frame(bb, 2); F.span = r2(F.span * 1.08); why = 'the route' + (main ? ' from ' + (main.k || main.p.ar) : ''); }
  else if (fx === 'مقارنة' && places.length > 1) { F = frame(boxOf(places.map(e => e.p.pt)), 1.5); F.span = r2(Math.max(F.span * 1.25, ...places.map(e => e.p.span * 1.4))); why = 'two places'; }
  else if (main && (ctrs.length || planC.length) && fx === 'ابتعاد') {
    const cs = ctrs.length ? ctrs.map(e => e.c) : planC, U = frame(cs.map(c => c.bb).reduce(merge), 2.4);
    const B = merge(cs.map(c => c.bb).reduce(merge), [main.p.pt[0], main.p.pt[1], main.p.pt[0], main.p.pt[1]]), G = frame(B, 2.4);   // the countries AND the place, centred between them
    F = { lon: G.lon, lat: G.lat, span: r2(Math.max(main.p.span * 4, Math.min(G.span * .8, 26))) }; why = (main.k || main.p.ar) + ' + ' + cs.map(c => c.a3).join('+');
  } else if (main) {
    const pts = [main.p.pt, ...places.filter(e => e.k !== main.k).map(e => e.p.pt)];
    F = pts.length > 1 ? frame(boxOf(pts), main.p.span * 1.6) : { lon: r3(main.p.pt[0]), lat: r3(main.p.pt[1]), span: r2(main.p.span * 1.6) };
    const home = ctrs.map(e => e.c).find(c => c.bb[0] <= main.p.pt[0] && main.p.pt[0] <= c.bb[2] && c.bb[1] <= main.p.pt[1] && main.p.pt[1] <= c.bb[3]);
    if (home && spanOfBox(home.bb) < F.span * 6) F = frame(merge(home.bb, boxOf(pts)), F.span);   // Ras Laffan + the whole of Qatar
    if (home && route) F.span = r2(F.span * 1.5);   // room for the ship leaving it (the lane is not framed whole: the place stays in the middle)
    else if (route && route.kind === 'lane' && fx !== 'غوص') { const R = frame(boxOf(route.pts), 1); if (R.span < F.span * 3) { F = frame(merge(boxOf(pts), boxOf(route.pts)), F.span); } }
    why = main.k || main.p.ar;
  } else if (ctrs.length || planC.length) {
    const cs = ctrs.length ? ctrs.map(e => e.c) : planC; let bb = cs.map(c => c.bb).reduce(merge);
    if (main) bb = merge(bb, [main.p.pt[0], main.p.pt[1], main.p.pt[0], main.p.pt[1]]);
    F = frame(bb, 2.4); why = cs.map(c => c.a3).join('+');
    if (fx === 'ابتعاد') F.span = r2(Math.min(95, F.span * 1.25));
  } else if (route) { F = frame(boxOf(route.pts), 2); why = 'the route'; }
  if (!F && prev) { F = { ...prev, span: r2(prev.span * .9) }; why = 'slow push'; }
  if (F) {
    // where it arrives: early in the line, after its first named thing at the latest; big moves get more time
    const firstNamed = ents.find(e => e.kind !== 'danger'), big = prev ? far(prev, F) : 0;
    let arrive = li === 0 ? 0 : Math.min(L0 + Math.min(1.8, .6 + .35 * big), firstNamed ? ts(firstNamed.i) + .25 : L1 - .4);
    arrive = Math.max(arrive, li === 0 ? 0 : L0 + .35);
    const k = (() => { let best = a; for (let i = a; i <= b; i++) if (ts(i) <= arrive) best = i; return best; })();
    const at = li === 0 ? '0' : A(k, false, arrive - ts(k));
    if (li === 0) { firstFrame = { ...F }; cams.push(`cam(0, { lon: ${F.lon}, lat: ${F.lat}, span: ${r2(F.span * 1.25)} });   // opening: ${why}, a little wider`); }
    else if (!prev || far(prev, F) > .18) cams.push(`cam(${at}, { lon: ${F.lon}, lat: ${F.lat}, span: ${F.span}, ease: 'io'${fx === 'غوص' || (prev && F.span < prev.span / 4) ? ', dive: true' : ''} });   // ${why}`);
    // the hold key: the camera stays on what is being described and keeps a slow push until the line ends
    cams.push(`cam(${A(b, true)}, { lon: ${F.lon}, lat: ${F.lat}, span: ${r2(F.span * .93)}, ease: 'sine' });   // hold`);
    prev = { ...F, span: r2(F.span * .93) };
  }
  // ---- the look of the line
  if ((fx === 'ليل' || fx === 'خطر') && !night) { use('mapStyle'); item(`mapStyle('night', ${A(a)});`, 'night'); night = true; }
  else if (night && fx !== 'ليل' && fx !== 'خطر') { use('mapStyle'); item(`mapStyle('satellite', ${A(a)});`, 'day'); night = false; }
  // ---- titles: the hook (from the first frame), the plan's title, a short question, the payoff
  const q = /[?؟]$/.test(words[words.length - 1]), red = fx === 'خطر' || ents.some(e => e.kind === 'danger');
  const firstAt = ents.length ? ts(ents[0].i) : L1;
  let tt = p.title || (q && words.length <= 6 ? words.join(' ') : null);
  const FR = ['خمس', 'ربع', 'ثلث', 'نصف', 'عشر'], fi = WORDS.slice(a, b + 1).findIndex(w => FR.includes(nw(w[0]).replace(/^(و|ف)/, '')));
  if (!tt && fi >= 0 && !nums.length && li > 0) { const ws = []; for (let q = a + fi; q <= b && ws.length < 3; q++) { ws.push(clean(WORDS[q][0])); if (/[،,؛:.!?؟…]$/.test(WORDS[q][0])) break; } tt = ws.join(' '); }
  if (li === LINES.length - 1) tt = null;   // the payoff is made below
  if (tt && nums.length && li !== 0) tt = null;   // a number already holds the top of the screen
  if (tt) { use('title'); tt = tt.replace(/'/g, '’');
    if (li === 0) item(`title('${tt}', { at: 0, dur: .01, until: ${nums.length ? A(nums[0].i, false, -.25) : UNTIL}, color: '${red ? 'red' : 'yellow'}', anim: 'wipe' });`, 'hook title');
    else { const until = nums.length ? A(nums[0].i, false, -.15) : places.length || ctrs.length ? A((places[0] || ctrs[0]).i, false, -.1) : UNTIL;
      if (!(nums.length || places.length || ctrs.length) || ts((nums[0] || places[0] || ctrs[0]).i) - L0 > .7) item(`title('${tt}', { at: ${A(a)}, until: ${until}, color: '${red ? 'red' : 'yellow'}'${red ? '' : `, anim: 'words'`} });`, 'title'); }
    for (const w of tt.split(/\s+/)) if (w.length > 3) emph.add(w.replace(/[!؟?.،]/g, '')); }
  // ---- countries: spoken ones only, two flags at most per line, the rest in colour
  let flags = 0, col = 0;
  for (const e of ctrs) {
    const fl = !!ISO2[e.c.iso] && flags < 2 && fx !== 'خطر'; if (fl) flags++; use('region');
    const style = fx === 'خطر' || (red && e === ctrs[0]) ? `style: 'danger', label: '${CNAME(e.c)}'` : fl ? `flag: true, label: '${CNAME(e.c)}'` : `style: 'vivid', color: '${COLS[col++ % COLS.length]}', opacity: .5, label: '${CNAME(e.c)}'`;
    const fb = F ? [F.lon - F.span * .6 / kx(F.lat), F.lat - F.span * .95, F.lon + F.span * .6 / kx(F.lat), F.lat + F.span * .95] : null, bb = e.c.bb;
    const outside = fb && (e.c.lx < fb[0] || e.c.lx > fb[2] || e.c.ly < fb[1] || e.c.ly > fb[3]) && (bb[2] - bb[0] > (fb[2] - fb[0]) * 2 || bb[2] < fb[0] || bb[0] > fb[2] || bb[3] < fb[1] || bb[1] > fb[3]);
    if (outside && main && !main.new && ISO2[e.c.iso]) { flags--; use('flagPin', 'spot'); item(`flagPin('${e.c.a3}', spot('${main.k}'), { at: ${A(e.i)}, in: '${main.p.in}', say: '${clean(WORDS[e.i][0])}', until: ${UNTIL} });`, `country ${e.c.a3}`, ts(e.i)); continue; }
    if (outside) { report.notes.push(`السطر ${li + 1}: ${CNAME(e.c)} خارج الإطار، لم تُلوَّن.`); continue; }
    item(`region('${e.c.a3}', { at: ${A(e.i)}, ${style}, labelPill: '#111a2b', say: '${clean(WORDS[e.i][0])}', until: ${UNTIL} });`, `country ${e.c.a3}`, ts(e.i));
  }
  // ---- places: a label for every named place, alternately above and below so two never stack
  places.forEach((e, k) => { use('label', 'spot');
    item(`label(spot('${e.k}'), '${e.p.ar}', { at: ${A(e.i)}, pill: '#111a2b', color: '${red ? '#ff8a95' : '#ffd23f'}', until: ${UNTIL}, anim: 'bubble'${e.p.off_map ? '' : `, in: '${e.p.in}'`}${places.length > 1 ? `, lead: [0, ${k % 2 ? 110 : -110}]` : ''} });`, `place ${e.k}`, ts(e.i)); });
  if (main && main.new) { use('label'); const at = A(a, false, .3);
    item(`label([${main.p.pt}], '${main.p.ar}', { at: ${at}, pill: '#111a2b', color: '#ffd23f', until: ${UNTIL}, anim: 'bubble' });   // new place: ${p.place.src || 'no source'}`, 'new place'); report.notes.push(`مكان جديد في السطر ${li + 1}: ${main.p.ar} (${main.p.pt}) — ${p.place.src || 'بلا مصدر'}`); }
  // ---- the route and what moves on it
  if (route) { use('path', route.kind === 'lane' ? 'lane' : 'pipeline'); const at = A(main && places.length ? places[0].i : a, false, main && places.length ? 0 : .2);
    item(route.kind === 'lane' ? `path(${route.call}, { at: ${at}, dur: 1.2, color: '${red ? '#ff4757' : '#4fd2ff'}', width: 8, sea: true, until: ${UNTIL} });`
      : `path(${route.call}, { at: ${at}, dur: 1.2, color: '#ffd23f', width: 12, until: ${UNTIL} });`, `route ${p.lane}`); }
  if (p.craft) {
    const c = p.craft, K = CRAFT[c];
    if (!TOPS.has(c)) report.notes.push(`السطر ${li + 1}: المجسم ${c} بلا منظر علوي، يلزم لوحة ChatGPT؛ لم يُرسم.`);
    else if (K && K.m === 'sea' && route && route.kind === 'lane') { use('sprite', 'lane'); const dur = Math.max(4, L1 - L0 + 1.2);
      const n = /ملايين|مليون|يوم|كل/.test(words.join(' ')) && c === 'tanker' ? 4 : 1;
      for (let s = 0; s < n; s++) item(`sprite('${c}', null, { at: ${A(a)}, early: 0, along: ${route.call}, ${n > 1 ? `speed: ${r3(.9 / dur / 3)}, phase: ${r2(s / n + .05)}` : `travel: ${r2(dur * 1.6)}`}${s ? ', sfx: false' : ''}, until: ${UNTIL} });`, `craft ${c}`, L0); }
    else if (K && K.m === 'air' && main && fx === 'ضربة') { use('sprite'); const [x, y] = main.p.pt, d = Math.max(.6, (F ? F.span : 2) * .35);
      item(`sprite('${c}', [${r3(x + d)}, ${r3(y + d * .7)}], { to: [${r3(x)}, ${r3(y)}], at: ${A(a)}, travel: ${r2(Math.min(2.2, Math.max(1.2, (L1 - L0) * .4)))}, hit: { explode: true, shake: true }, until: ${UNTIL} });`, `craft ${c}`, L0); }
    else if (K && K.m === 'air' && main && !main.new) { use('icon', 'spot'); const d = ents.find(e => e.kind === 'danger');
      item(`icon(spot('${main.k}'), 'x', { at: ${d ? A(d.i, false, -.2) : A(a, false, .3)}, size: 2.0, color: 'red', until: ${UNTIL} });`, 'danger sign', d ? ts(d.i) : L0); }
    else if (K && K.m === 'sea' && main && seaApproach(main.p.pt, F ? F.span : 1.5)) { use('sprite'); const R = seaApproach(main.p.pt, F ? F.span : 1.5), dur = Math.max(4, L1 - L0 + 1);
      item(`sprite('${c}', null, { at: ${A(a)}, early: 0, along: ${JSON.stringify(R)}, travel: ${r2((L1 - L0) * .9)}, until: ${UNTIL} });   // an approach through open water, at the berth before the line ends`, `craft ${c}`, L0);
      if (c === 'carrier' && TOPS.has('destroyer')) item(`sprite('destroyer', null, { at: ${A(a, false, .6)}, early: 0, along: ${JSON.stringify(R)}, travel: ${r2((L1 - L0) * .9)}, sfx: false, until: ${UNTIL} });   // its escort, behind it`, 'craft destroyer', L0); }
    else report.notes.push(`السطر ${li + 1}: المجسم ${c} يحتاج مساراً معروفاً (بحرياً للسفن) أو مكاناً؛ لم يُرسم.`);
  }
  // ---- objects the words name (2.2): a still one stands on the line's place, a moving one runs along the place's lane
  for (const { o, i } of objectsAt(a, b)) {
    if (shownObj.has(o.name) || p.craft === o.name || !main || main.new) continue;
    const ln = route && route.kind === 'lane' ? route.call : (k => k ? `lane('${k}')` : null)(Object.keys(PL.lanes).find(l => l.startsWith(main.k + '_')));
    if (o.kind === 'still' && (main.p.in === 'sea') !== (o.name === 'oil_rig')) continue;   // logic: barrels never float in a strait, an oil rig stands only at sea
    if (o.name === 'pipeline' && route) continue;   // the pipeline itself is already drawn
    if (o.kind === 'still' && STILLS.has(o.name)) { use('sprite', 'spot'); shownObj.add(o.name);
      item(`sprite('${o.name}', spot('${main.k}'), { at: ${A(i)}, until: ${UNTIL}${main.p.off_map ? '' : `, in: '${main.p.in}'`} });   // «${clean(WORDS[i][0])}»`, `object ${o.name}`, ts(i)); }
    else if (o.kind === 'move' && TOPS.has(o.name) && ln && (CRAFT[o.name] || {}).m === 'sea') { use('sprite', 'lane'); shownObj.add(o.name);
      item(`sprite('${o.name}', null, { at: ${A(i)}, early: 0, along: ${ln}, travel: ${r2(Math.max(4, L1 - L0 + 1.2) * 1.6)}, until: ${UNTIL} });   // «${clean(WORDS[i][0])}»`, `craft ${o.name}`, ts(i)); }
  }
  // ---- numbers: one at a time at the top, each until the next one
  const ends = k => [[k, PL.places[k].pt], ...Object.entries(PL.places).filter(([q, v]) => q.startsWith(k + '_') && v.kind === 'point').map(([q, v]) => [q, v.pt])];
  nums.forEach(e => { if (e.fmt !== '# كم' || places.length < 2) return; let best = null;
    for (const [ka, pa] of ends(places[0].k)) for (const [kb, pb] of ends(places[1].k)) { const d = hav(pa, pb), err = Math.abs(d - e.to) / e.to; if (!best || err < best.err) best = { ka, kb, err }; }
    if (best && best.err < .1) { use('measure', 'spot'); e.measured = true;
      item(`measure(spot('${best.ka}'), spot('${best.kb}'), { at: ${A(e.i)}, dur: 1.0, to: ${e.to}, unit: 'كم', color: '#ffd23f', until: ${UNTIL} });`, `measure ${e.to}`, ts(e.i)); item(`punch(${A(e.end)}, { amt: .1 });`, 'punch'); } });
  nums.forEach((e, k) => { if (e.dup || e.measured) return; use('number', 'punch');
    const until = k + 1 < nums.length ? A(nums[k + 1].i, false, -.15) : UNTIL, fire = red || e.fmt === '#%' && e.to >= 50;
    item(`number(null, '${e.fmt}', { at: ${A(e.i)}, until: ${until}, count: { to: ${e.to}, dur: ${e.to >= 100 ? 1.1 : .95}${e.dec ? `, fmt: n => n.toFixed(${e.dec})` : ''} }${e.say ? `, say: '${e.say}'` : ''}, color: '${fire ? 'fire' : 'yellow'}'${e.sub ? `, sub: '${e.sub.replace(/'/g, '’')}'` : ''} });`, `number ${e.to}`, ts(e.i));
    item(`punch(${A(e.end)}, { amt: .1 });`, 'punch'); emph.add(clean(WORDS[e.i][0])); });
  // ---- impact
  if (fx === 'ضربة' || fx === 'خطر' || ents.some(e => e.kind === 'danger')) { const d = ents.find(e => e.kind === 'danger') || nums[0] || places[0] || ctrs[0];
    use('shake'); item(`shake(${A(d ? d.i : a)}, { amt: ${fx === 'ضربة' ? 1.3 : 1.1} });`, 'shake'); if (d && d.kind === 'danger') emph.add(clean(WORDS[d.i][0])); }
  report.lines.push({ line: li + 1, frame: F && why, ents: ents.map(e => e.kind + ':' + (e.k || (e.c && e.c.a3) || e.to || clean(WORDS[e.i][0]))), plan: p.raw || null });
});

// ---- payoff + closed loop: the last line ends on a title, and the camera goes back to the first frame
{ const [a, b] = LINES[LINES.length - 1], words = WORDS.slice(a, b + 1).map(w => w[0]);
  const cut = words.findIndex(w => /[،,…:]$/.test(w)), ila = words.findIndex((w, q) => q > 1 && nw(w) === 'الي');
  // the payoff phrase: after the last pause mark, else what the line turns INTO («… إلى جسرٍ أطلسيٍّ حيويٍّ»), else its last words; 6 words at most
  let tail = cut >= 0 && cut + 1 < words.length - 1 ? words.slice(cut + 1) : ila >= 0 && ila + 2 < words.length ? words.slice(ila + 1, ila + 4) : words.slice(Math.max(0, words.length - 4));
  if (tail.length > 6) tail = tail.slice(-6);
  const p = PLAN[LINES.length - 1] || {}, tt = (p.title || tail.join(' ')).replace(/[.!؟?…]+$/, '').replace(/'/g, '’'), at = a + Math.max(0, words.indexOf(tail[0]));
  use('title', 'punch');
  item(`title('${tt}', { at: ${A(at)}, until: ${A(b, true, .5)}, color: 'yellow', anim: 'words', mag: 1.5 });`, 'payoff');
  item(`punch(${A(at)}, { amt: .14 });`, 'payoff punch');
  for (const w of tt.split(/\s+/)) if (w.length > 3) emph.add(w);
  const p0 = PLAN[0] || {};
  if (p0.craft && p0.lane && PL.lanes[p0.lane] && TOPS.has(p0.craft) && (CRAFT[p0.craft] || {}).m === 'sea') { use('sprite', 'lane');
    item(`sprite('${p0.craft}', null, { at: ${A(a, false, 1.0)}, early: 0, along: lane('${p0.lane}'), travel: ${r2(Math.max(4, te(b) - ts(a) + 2.5) * 1.6)}, sfx: false });   // the loop: the opening ship again`, `craft ${p0.craft}`, ts(a));
    use('path'); item(`path(lane('${p0.lane}'), { at: ${A(at)}, dur: .6, color: '#ff4757', width: 11, sea: true });`, 'loop route', ts(at)); }
  if (firstFrame) cams.push(`cam(${A(b, true, 1.0)}, { lon: ${firstFrame.lon}, lat: ${firstFrame.lat}, span: ${r2(firstFrame.span * 1.25)}, ease: 'io' });   // loop: back to the opening frame`); }

const src = `// Written by engine/scripts/director.mjs (TaleSpark 2.0 automatic director, ${new Date().toISOString().slice(0, 16)})
// from script.txt + web/timing.json${PLAN.length ? ' + shotplan.json (Gemini)' : ''}. Every item has an id (// dN); the logic gate's rejects go to director.drop.json.
import { ${[...used].sort().join(', ')} } from './map.js';

// ---- camera: one shot per line, arriving early in it, then a hold key at its end (cam(t) = the camera ARRIVES at t)
${cams.join('\n')}
${out.join('\n')}

captions({ style: 'line', emph: ${JSON.stringify([...emph].slice(0, 6))} });
end(${A(WORDS.length - 1, true, 1.2)});
start();
`;
const story = path.join(proj, 'web', 'story.js');
if (fs.existsSync(story) && !/Written by engine\/scripts\/director\.mjs/.test(fs.readFileSync(story, 'utf8'))) fs.copyFileSync(story, path.join(proj, 'web', 'story.prev.js'));
fs.writeFileSync(story, src);
fs.writeFileSync(path.join(proj, 'director.json'), JSON.stringify(report, null, 1));
console.log(`director: ${LINES.length} lines, ${cams.length} camera keys, ${out.filter(x => !x.startsWith('\n')).length} items${report.dropped.length ? `, ${report.dropped.length} dropped by the logic gate` : ''} -> ${story}`);
for (const n of report.notes) console.log('  ' + n);
