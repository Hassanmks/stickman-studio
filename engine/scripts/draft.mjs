// Story draft generator (speed plan item 9, 2026-11-17).
//   node draft.mjs <project>          -> <project>/web/story.draft.js (+ story.js when it is still the template)
// Reads the script and web/timing.json and finds, word by word: spoken numbers (the engine's own number words, so the
// logic gate's "picture = speech" check agrees), countries (world.json Arabic names + the engine's aliases), places and
// their sea lanes (engine/data/places.json), questions, danger words and sentence boundaries. It writes a complete,
// renderable story: one camera move per sentence framed on what that sentence names, regions (flag when the engine has
// it), place labels, count-up numbers with their unit and sub-line, titles for short questions and the payoff, punches,
// captions, end. Every anchor is T('word#n') with the right occurrence number, so repeated words are never mis-timed.
// It is a first draft: Claude (or you) refines it instead of writing from zero.
import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const ENG = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const proj = path.resolve(process.argv[2] || ''); if (!process.argv[2]) { console.log('usage: node draft.mjs <project>'); process.exit(1); }
const J = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const TIM = J(path.join(proj, 'web', 'timing.json')), WORDS = TIM.words;
const WORLD = J(path.join(ENG, 'data', 'world.json')), PL = J(path.join(ENG, 'data', 'places.json'));
const MAP = fs.readFileSync(path.join(ENG, 'engine', 'map.js'), 'utf8');
const grab = n => { const m = MAP.match(new RegExp(`const ${n} = (\\{[\\s\\S]*?\\});`)); if (!m) throw new Error('map.js: ' + n + ' not found'); return Function('return ' + m[1])(); };
const NUMW = grab('NUMW'), ALIAS = grab('ALIAS'), ISO2 = grab('ISO2');   // one source of truth: the engine's own tables

// ---- the engine's word normaliser and prefix forms (same as map.js nw / forms)
const nw = w => String(w).toLowerCase().replace(/[ً-ْـ]/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/[^\p{L}\p{N}]/gu, '');
const forms = w => { const S = new Set([nw(w)]); for (const x of [...S]) if (x.length > 3 && 'وفبكل'.includes(x[0])) S.add(x.slice(1)); for (const x of [...S]) if (x.length > 4 && (x.startsWith('ال') || x.startsWith('لل'))) S.add(x.slice(2)); return S; };
const clean = w => w.replace(/[^\p{L}\p{M}\p{N}]/gu, '');
const N = WORDS.map(w => nw(w[0]));
// T('word#n'): the n-th time this exact (normalised) word is spoken
const anchor = (i, end = false, off = 0) => { const n = N.slice(0, i + 1).filter(x => x === N[i]).length, tot = N.filter(x => x === N[i]).length;
  return `T('${clean(WORDS[i][0])}${n > 1 || (tot > 1 && n > 1) ? '#' + n : ''}${end ? '.end' : ''}${off ? (off > 0 ? '+' : '') + off : ''}')`; };
const t = i => WORDS[i][1];
const r2 = x => Math.round(x * 100) / 100, r3 = x => Math.round(x * 1000) / 1000;

// ---- sentences (a question, an exclamation, a full stop or an ellipsis closes one)
const SENT = []; { let s = 0; WORDS.forEach((w, i) => { if (/[.!?؟…]["»”)]*$/.test(w[0]) || i === WORDS.length - 1) { SENT.push([s, i]); s = i + 1; } }); }

// ---- countries
// a country is its whole Arabic name ('جنوب السودان' needs both words, so 'الجنوب' alone is not South Sudan), or one of the
// engine's spoken aliases, or the short form people say ('عمان' for 'سلطنة عمان', 'الإمارات' for the long UAE name)
const SHORT = { OMN: ['عمان'], ARE: ['الامارات'], SAU: ['السعوديه'] };
const CTR = WORLD.countries.map(c => ({ c, seqs: [c.ar.split(/\s+/).map(nw), ...[...(ALIAS[c.iso] || []), ...(SHORT[c.iso] || [])].map(a => a.split(/\s+/).map(nw))] }));
const countryAt = i => { for (const { c, seqs } of CTR) for (const q of seqs) if (q.every((tk, j) => i + j < WORDS.length && (j === 0 ? forms(WORDS[i][0]).has(tk) : N[i + j] === tk))) return { c, len: q.length }; return null; };
// ---- places (multi-word names first)
const PLK = []; for (const [k, p] of Object.entries(PL.places)) for (const a of new Set([...(p.aka || []), p.ar])) PLK.push({ k, toks: a.split(/\s+/).map(nw) });
PLK.sort((a, b) => b.toks.length - a.toks.length);
const placeAt = i => { for (const P of PLK) { if (P.toks.every((tk, j) => i + j < WORDS.length && (j === 0 ? forms(WORDS[i][0]).has(tk) : N[i + j] === tk))) return { ...P, len: P.toks.length }; } return null; };
// ---- numbers (the engine's spoken() rule, plus the unit the next words give it)
const numv = w => { let s = nw(w); if (/^\d+$/.test(s)) return +s; if (NUMW[s] == null && s.length > 2 && 'وفبل'.includes(s[0])) s = s.slice(1); if (NUMW[s] == null && s.startsWith('ال')) s = s.slice(2); if (NUMW[s] == null && s.length > 3 && s.endsWith('ا') && NUMW[s.slice(0, -1)] != null) s = s.slice(0, -1); return NUMW[s]; };
const SCALE = { 1000: 'ألف', 1e6: 'مليون', 1e9: 'مليار' };
const DANGER = ['اغلاق', 'اغلق', 'يغلق', 'تغلق', 'صاروخ', 'صواريخ', 'هجوم', 'حرب', 'تهديد', 'انفجار', 'ضربه', 'يقصف', 'مسيره', 'الحرب', 'الخطر', 'خطر'];

const out = { cams: [], items: [], notes: [] }, used = new Set(['cam', 'T', 'start', 'end', 'captions']);
const use = (...n) => n.forEach(x => used.add(x));
const span = (c, min = 2.4) => { const [w, s, e, n] = c.bb; const wd = (e - w) * Math.cos(((s + n) / 2) * Math.PI / 180), ht = n - s; return Math.min(90, Math.max(min, wd * 1.35, ht * 1.35 * 9 / 16)); };
const COLS = ['#ffd23f', '#2fd6c8', '#ff8a3d', '#b48cff', '#5fd36b'];
let lastCam = null, firstFocus = null, nC = 0;
const numbersSeen = [];

SENT.forEach(([a, b], si) => {
  const ents = [];
  for (let i = a; i <= b; i++) {
    const p = placeAt(i); if (p) { ents.push({ kind: 'place', i, k: p.k, p: PL.places[p.k] }); i += p.len - 1; continue; }
    const cm = countryAt(i); if (cm) { ents.push({ kind: 'country', i, c: cm.c }); i += cm.len - 1; continue; }
    const v = numv(WORDS[i][0]);
    if (v != null) {   // a run of number words joined by و
      let j = i, tot = 0, cur = 0; const vals = [];
      // a compound number goes on with 'و' (تسعة وثلاثين) or a scale word (عشرين مليون); anything else starts a new run
      while (j <= b && numv(WORDS[j][0]) != null && (j === i || N[j].startsWith('و') || [100, 1000, 1e6, 1e9].includes(numv(WORDS[j][0])))) { const x = numv(WORDS[j][0]); if (x >= 1000) { tot += (cur || 1) * x; cur = 0; } else if (x === 100) cur = (cur || 1) * 100; else cur += x; vals.push(x); j++; }
      const val = tot + cur, last = vals[vals.length - 1], nxt = j <= b ? N[j] : '', nxt2 = j + 1 <= b ? N[j + 1] : '';
      if (vals.length === 1 && val <= 10 && !nxt.startsWith('كيلو') && nxt !== 'كم' && !(nxt === 'في' && /مئه|ميه|مائه/.test(nxt2))) { i = j - 1; continue; }   // a lone small word ('واحد', 'خُمس') is not a figure
      let to = val, fmt = '#', sub = [], k = j;
      if (SCALE[last] && vals.length > 1) { to = val / last; fmt = `# ${SCALE[last]}`; }
      if (nxt.startsWith('كيلو') || nxt === 'كم') { fmt = '# كم'; k = j + 1; }
      else if ((nxt === 'في' && /^(ال)?(مئه|ميه|مائه)$/.test(nxt2)) || /^(بال|ال)?(مئه|ميه|مائه)$/.test(nxt)) { fmt = '#%'; k = nxt === 'في' ? j + 2 : j + 1; }
      for (let q = k; q <= b && sub.length < 4; q++) { sub.push(clean(WORDS[q][0])); if (/[،,؛;:.!?؟…]$/.test(WORDS[q][0])) break; }
      const dup = numbersSeen.find(x => x.to === to && x.fmt === fmt);
      ents.push({ kind: 'number', i, to, fmt, sub: sub.join(' '), dup: !!dup }); numbersSeen.push({ to, fmt });
      i = k - 1; continue;   // the unit words (كيلومتر, في المئة) are part of the number
    }
    if (DANGER.some(d => [...forms(WORDS[i][0])].includes(d))) ents.push({ kind: 'danger', i });
  }
  const s0 = a, nextT = si + 1 < SENT.length ? anchor(SENT[si + 1][0]) : anchor(b, true, .6);
  // camera: the first place of the sentence, else the countries it names together, else a slow push on the last shot
  const pl = ents.find(e => e.kind === 'place'), cs = ents.filter(e => e.kind === 'country').map(e => e.c);
  let C = null;
  const pls = ents.filter(e => e.kind === 'place');
  if (pls.length > 1) { const xs = pls.map(e => e.p.pt[0]), ys = pls.map(e => e.p.pt[1]), w = Math.min(...xs), e2 = Math.max(...xs), s2 = Math.min(...ys), n2 = Math.max(...ys);
    C = { lon: (w + e2) / 2, lat: (s2 + n2) / 2, span: Math.max(span({ bb: [w, s2, e2, n2] }, 0) * 1.15, ...pls.map(e => e.p.span * 1.3)), why: pls.map(e => e.k).join('+') }; }
  else if (pl) C = { lon: pl.p.pt[0], lat: pl.p.pt[1], span: pl.p.span * 1.6, why: pl.k };
  else if (cs.length) { const bb = cs.reduce((m, c) => [Math.min(m[0], c.bb[0]), Math.min(m[1], c.bb[1]), Math.max(m[2], c.bb[2]), Math.max(m[3], c.bb[3])], [999, 999, -999, -999]);
    C = { lon: (bb[0] + bb[2]) / 2, lat: (bb[1] + bb[3]) / 2, span: span({ bb }), why: cs.map(c => c.a3).join('+') }; }
  if (!C && lastCam) C = { ...lastCam, span: lastCam.span * .88, why: 'slow push', ease: 'sine' };
  if (C) { if (!firstFocus) firstFocus = C; C.span = r2(C.span); C.lon = r3(C.lon); C.lat = r3(C.lat);
    out.cams.push(`cam(${si === 0 ? 0 : anchor(s0)}, { lon: ${C.lon}, lat: ${C.lat}, span: ${C.span}${si ? `, ease: '${C.ease || 'io'}'` : ''} });   // ${C.why}`); lastCam = C; }
  out.items.push(`\n// ---------------------------------------------------------------- ${r2(t(a))}-${r2(WORDS[b][2])} s  «${WORDS.slice(a, b + 1).map(w => w[0]).join(' ').slice(0, 90)}»`);
  // a short question (or the hook) becomes a title; the last sentence is the payoff
  const words = WORDS.slice(a, b + 1).map(w => w[0]), q = /[?؟]$/.test(words[words.length - 1]);
  if (q && words.length <= 6) { use('title'); out.items.push(`title('${words.join(' ').replace(/'/g, '’')}', { at: ${anchor(a)}, until: ${nextT}, color: '${ents.some(e => e.kind === 'danger') ? 'red' : 'yellow'}' });`); }
  if (si === SENT.length - 1) { const tail = words.slice(Math.max(0, words.findIndex(w => /[،,…]$/.test(w)) + 1)); use('title', 'punch');
    out.items.push(`title('${tail.join(' ').replace(/[.!؟?…]+$/, '')}', { at: ${anchor(b - tail.length + 1)}, until: ${anchor(b, true, .5)}, color: 'yellow', anim: 'words' });`, `punch(${anchor(b - tail.length + 1)}, { amt: .14 });`); }
  let k = 0;
  for (const e of ents) {
    if (e.kind === 'country') { const fl = !!ISO2[e.c.iso]; use('region');
      out.items.push(fl ? `region('${e.c.a3}', { at: ${anchor(e.i)}, flag: true, label: '${e.c.ar.replace('الإمارات العربية المتحدة', 'الإمارات')}', labelPill: '#111a2b', until: ${nextT} });`
        : `region('${e.c.a3}', { at: ${anchor(e.i)}, style: 'vivid', color: '${COLS[k++ % COLS.length]}', opacity: .5, label: '${e.c.ar}', until: ${nextT} });   // no flag file for ${e.c.iso}`); }
    if (e.kind === 'place') { use('label', 'spot');
      out.items.push(`label(spot('${e.k}'), '${e.p.ar}', { at: ${anchor(e.i)}, pill: '#111a2b', color: '#ffd23f', until: ${nextT}, anim: 'bubble'${e.p.off_map ? '' : `, in: '${e.p.in}'`} });`);
      const ln = Object.keys(PL.lanes).find(l => l.startsWith(e.k + '_')); if (ln && !out.notes.includes(ln)) { out.notes.push(ln); use('path', 'lane', 'sprite');
        out.items.push(`path(lane('${ln}'), { at: ${anchor(e.i)}, dur: 1.2, color: '#4fd2ff', width: 8, sea: true, until: ${nextT} });`,
          `sprite('tanker', null, { at: ${anchor(e.i)}, along: lane('${ln}'), travel: 6, until: ${nextT} });`); } }
    if (e.kind === 'number') { use('number', 'punch');
      out.items.push(`${e.dup ? '// (already shown) ' : ''}number(null, '${e.fmt}', { at: ${anchor(e.i)}, until: ${nextT}, count: { to: ${e.to}, dur: 1.0 }, color: 'yellow'${e.sub ? `, sub: '${e.sub}'` : ''} });`, `${e.dup ? '// ' : ''}punch(${anchor(e.i)}, { amt: .1 });`); }
    if (e.kind === 'danger') { use('shake'); out.items.push(`shake(${anchor(e.i)}, { amt: 1.1 });`); }
  }
});
// the opening frame: the film's main place (the first one named), a little wider, so the first move is a push in
if (!out.cams[0].startsWith('cam(0,') && firstFocus) out.cams.unshift(`cam(0, { lon: ${firstFocus.lon}, lat: ${firstFocus.lat}, span: ${r2(firstFocus.span * 1.3)} });   // opening: ${firstFocus.why}, wider`);
const imports = [...used].sort();
const src = `// DRAFT written by engine/scripts/draft.mjs from script.txt + timing.json (${new Date().toISOString().slice(0, 16)}).
// Refine it: tighten the shots, pick colours, add sprites/photos/mapStyle, check every anchor. The logic gate runs before any frame.
import { ${imports.join(', ')} } from './map.js';

// ---- camera: one move per sentence, framed on what it names (cam(t) = the camera ARRIVES at t)
${out.cams.join('\n')}
${out.items.join('\n')}

captions({ style: 'line' });
end(${anchor(WORDS.length - 1, true, 1.2)});
start();
`;
fs.writeFileSync(path.join(proj, 'web', 'story.draft.js'), src);
const story = path.join(proj, 'web', 'story.js'); let wrote = false;
if (!fs.existsSync(story) || /Maven Map v2 demo/.test(fs.readFileSync(story, 'utf8'))) { fs.writeFileSync(story, src); wrote = true; }
console.log(`draft: ${SENT.length} sentences, ${out.cams.length} camera keys, ${out.items.filter(x => !x.startsWith('\n')).length} items -> ${path.join(proj, 'web', 'story.draft.js')}${wrote ? ' (also story.js)' : ''}`);
