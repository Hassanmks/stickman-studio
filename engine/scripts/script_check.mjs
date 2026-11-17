// TaleSpark 2.0, item 4: the Gemini prompt, the reply parser and the script checker (2026-11-17).
//   node script_check.mjs prompt "<topic>" [--seconds 90] [--angle "<the angle in one line>"]   -> the Gemini prompt (stdout)
//   node script_check.mjs parse <reply.txt> <project> [--seconds 90]   -> <project>/script.txt, sources.txt, notes.txt,
//                                                                        shotplan.json, check.json; exit 2 on any error
//   node script_check.mjs check <reply.txt|project> [--seconds 90]     -> the report only (nothing written)
//   node script_check.mjs fix <reply.txt|project>                      -> the Gemini prompt that asks for the fixes only
//   node script_check.mjs sheet "<object 1>" "<object 2>" ...          -> the ChatGPT prompt for a top-view sprite sheet
// Rules live in engine/data/script_rules.json (the Android app reads the same file); prompts in engine/prompts/.
// Places, lanes and number words come from the engine itself (places.json, map.js NUMW), so what passes here passes draft/director.
import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const ENG = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const J = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const RULES = J(path.join(ENG, 'data', 'script_rules.json')), PL = J(path.join(ENG, 'data', 'places.json'));
const WORLD = J(path.join(ENG, 'data', 'world.json'));
const MAP = fs.readFileSync(path.join(ENG, 'engine', 'map.js'), 'utf8');
const grab = n => { const m = MAP.match(new RegExp(`const ${n} = (\\{[\\s\\S]*?\\});`)); return Function('return ' + m[1])(); };
const NUMW = grab('NUMW');
export const CRAFT_AR = { tanker: 'ناقلة نفط', lng_carrier: 'ناقلة غاز مسال', container_ship: 'سفينة حاويات', destroyer: 'مدمّرة', carrier: 'حاملة طائرات',
  dhow: 'مركب شراعي تقليدي', tanker_burning: 'ناقلة نفط مشتعلة', missile: 'صاروخ', drone: 'طائرة مسيّرة', fighter_jet: 'طائرة مقاتلة' };
const TOP = fs.readdirSync(path.join(ENG, 'assets', 'sprites', 'top')).filter(f => f.endsWith('.png')).map(f => f.slice(0, -4));
const A3 = new Set(WORLD.countries.map(c => c.a3));

const HARAKA = /[ً-ْٰ]/g, HAS_H = /[ً-ْٰ]/;
export const bare = w => String(w).replace(HARAKA, '').replace(/ـ/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/[^\p{L}\p{N}]/gu, '');
const stems = w => { const s = bare(w), o = new Set([s]); for (const x of [...o]) if (x.length > 3 && 'وفبكل'.includes(x[0])) o.add(x.slice(1)); for (const x of [...o]) if (x.length > 4 && (x.startsWith('ال') || x.startsWith('لل'))) o.add(x.slice(2)); return o; };
const numWord = w => [...stems(w)].some(s => NUMW[s] != null);

// ---------------------------------------------------------------- the prompt
export function prompt(topic, o = {}) {
  const sec = +o.seconds || 90, wps = RULES.words_per_second;
  const places = Object.entries(PL.places).filter(([, p]) => p.kind !== 'point').map(([k, p]) => `${k}: ${p.ar}`).join('\n');
  const lanes = [...Object.entries(PL.lanes).map(([k, l]) => `${k}: ${l.ar} (مسار بحري)`), ...Object.entries(PL.pipelines || {}).map(([k, l]) => `${k}: ${l.ar} (أنبوب)`)].join('\n');
  const craft = TOP.filter(k => CRAFT_AR[k]).map(k => `${k}: ${CRAFT_AR[k]}`).join('\n');
  return fs.readFileSync(path.join(ENG, 'prompts', 'gemini_script.txt'), 'utf8')
    .replace('{{TOPIC}}', topic).replace('{{ANGLE}}', o.angle ? `الزاوية: ${o.angle}` : '').replace('{{SECONDS}}', sec)
    .replace('{{WORDS_MIN}}', Math.round(sec * wps * .9)).replace('{{WORDS_MAX}}', Math.round(sec * wps * 1.08))
    .replace('{{PLACES}}', places).replace('{{LANES}}', lanes).replace('{{CRAFT}}', craft).replace(/\n{3,}/g, '\n\n');
}

// ---------------------------------------------------------------- the reply
const HEAD = new RegExp(`^[\\s#*_>\\-]*\\[?\\s*(${RULES.sections.join('|')})\\s*\\]?[\\s*_:：]*$`);
export function parse(text) {
  const S = {}; let cur = null;
  for (const raw of String(text).replace(/\r/g, '').split('\n')) {
    const m = raw.trim().match(HEAD); if (m) { cur = m[1]; S[cur] = S[cur] || []; continue; }
    if (cur) S[cur].push(raw);
  }
  const lines = k => (S[k] || []).map(l => l.trim()).filter(Boolean);
  const script = lines('السكربت').map(l => l.replace(/^\s*(?:[-*•]\s+|\d+[.)]\s+)/, '').replace(/\*\*/g, '').replace(/\.{3}/g, '…').trim()).filter(Boolean);
  const plan = lines('خطة اللقطات').filter(l => l.includes('|')).map(planLine);
  return { found: RULES.sections.filter(k => S[k]), script, sources: lines('المصادر'), notes: lines('ملاحظات بصرية'), plan };
}
// one shot-plan line: "3 | مكان: HORMUZ | دول: IRN، OMN | رقم: 39 كم | مجسم: tanker | مسار: HORMUZ_GATE | مؤثر: غوص | عنوان: …"
const KEYS = { 'مكان': 'place', 'دول': 'countries', 'الدول': 'countries', 'رقم': 'number', 'مجسم': 'craft', 'مسار': 'lane', 'مؤثر': 'fx', 'عنوان': 'title' };
export function planLine(l) {
  const parts = l.replace(/\*\*/g, '').split('|').map(s => s.trim()), o = { raw: l };
  const n = parts[0].match(/\d+/); if (n) o.n = +n[0];
  for (const p of parts.slice(1)) {
    const m = p.match(/^([^:：]+)[:：]\s*(.*)$/); if (!m) continue; const k = KEYS[m[1].trim()]; if (!k) continue;
    let v = m[2].trim(); if (!v || v === '-' || v === '—' || v === 'لا يوجد' || v === 'لا شيء') continue;
    if (k === 'countries') v = v.split(/[،,\s]+/).map(s => s.trim().toUpperCase()).filter(Boolean);
    else if (k === 'place') { const nw = v.match(/^جديد\s*\((.+)\)$/); if (nw) { const [ar, lat, lon, ...src] = nw[1].split(/[؛;]/).map(s => s.trim()); v = { new: true, ar, lat: +lat, lon: +lon, src: src.join('; ') }; } }
    else if (k === 'craft' || k === 'lane') v = v.replace(/[`'"]/g, '').trim();
    o[k] = v;
  }
  return o;
}

// ---------------------------------------------------------------- the checks
export function check(R, o = {}) {
  const P = [], add = (level, line, text) => P.push({ level, line, text });
  for (const k of RULES.sections) if (!R.found.includes(k)) add('error', 0, `القسم [${k}] غير موجود في الرد.`);
  const sc = R.script, words = sc.join(' ').split(/\s+/).filter(Boolean), nW = words.length, est = nW / RULES.words_per_second;
  if (o.seconds) { const sec = +o.seconds; if (est < sec * .8 || est > sec * 1.2) add('warn', 0, `الطول ${nW} كلمة، أي نحو ${Math.round(est)} ثانية، والمطلوب نحو ${sec} ثانية (بين ${Math.round(sec * RULES.words_per_second * .9)} و${Math.round(sec * RULES.words_per_second * 1.08)} كلمة).`); }
  if (!sc.length) return finish(P, R, nW, est);
  const lowered = sc.map(l => l.replace(HARAKA, ''));
  sc.forEach((l, i) => {
    const L = i + 1, d = l.match(/[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?%?/g);
    if (d) add('error', L, `السطر ${L}: الرقم ${d.join('، ')} مكتوب بالأرقام؛ اكتبه كلمات كما يُنطق.`);
    if (!/[.!?؟…!]["»”)]*$/.test(l)) add('error', L, `السطر ${L} لا ينتهي بعلامة وقف (. أو ؟ أو ! أو …).`);
    for (const s of l.split(/(?<=[.!?؟…])\s+/)) { const n = s.split(/\s+/).filter(Boolean).length; if (n > RULES.max_words_per_sentence) add('warn', L, `السطر ${L}: جملة من ${n} كلمة؛ قسّمها (الحد ${RULES.max_words_per_sentence}).`); }
    for (const run of l.split(/[،,؛;:.!?؟…]/)) { const n = run.split(/\s+/).filter(Boolean).length; if (n > RULES.max_words_without_comma) add('warn', L, `السطر ${L}: ${n} كلمة دون أي علامة وقف («${run.trim().split(/\s+/).slice(0, 4).join(' ')} …»)؛ أضف فاصلة عند موضع النَّفَس.`); }
    for (const w of l.split(/\s+/)) {
      const st = [...stems(w)].find(s => RULES.ambiguous[s]);
      if (st && !HAS_H.test(w)) add(RULES.ambiguous_error.includes(st) ? 'error' : 'warn', L, `السطر ${L}: «${w.replace(/[^\p{L}\p{M}]/gu, '')}» تحتمل أكثر من نطق؛ شكّلها: ${RULES.ambiguous[st]}.`);
      if (RULES.dialect.some(x => stems(w).has(bare(x)))) add('error', L, `السطر ${L}: «${w}» كلمة عامية؛ استعمل الفصحى.`);
    }
    for (const f of RULES.forbidden) if (lowered[i].includes(f.find.replace(HARAKA, ''))) add('error', L, `السطر ${L}: «${f.find}» (${f.why})؛ البديل: ${f.say}.`);
  });
  // the hook and the closed loop
  const first = sc[0], last = sc[sc.length - 1];
  if (!/[?؟!]/.test(first) && !first.split(/\s+/).some(numWord)) add('warn', 1, 'السطر الأول ليس خطّافاً: اجعله سؤالاً أو حقيقة فيها رقم أو مكان محدد.');
  const content = s => new Set(s.split(/\s+/).map(w => bare(w)).map(w => w.replace(/^(و|ف|ب|ل)?(ال)?/, '')).filter(w => w.length >= 4));
  const A = content(first), B = content(last); if (![...B].some(w => A.has(w))) add('warn', sc.length, 'السطر الأخير لا يعيد صدى الخطّاف؛ الحلقة المغلقة تجعل الإعادة متصلة (كرّر كلمة مفتاحية من السطر الأول).');
  // the shot plan
  const pl = R.plan;
  if (R.found.includes('خطة اللقطات')) {
    if (pl.length !== sc.length) add('error', 0, `خطة اللقطات فيها ${pl.length} سطراً والسكربت ${sc.length} سطراً؛ يلزم سطر لكل سطر.`);
    pl.forEach((p, i) => {
      const L = p.n || i + 1;
      if (p.place && !p.place.new && !PL.places[p.place]) add('warn', L, `خطة اللقطات ${L}: المكان ${p.place} غير موجود في قاعدة الأماكن؛ سيُؤطَّر على الدول بدلاً منه (أو اكتبه بصيغة جديد(…)).`);
      if (p.place && p.place.new && !(Math.abs(p.place.lat) <= 90 && Math.abs(p.place.lon) <= 180)) add('error', L, `خطة اللقطات ${L}: إحداثيات المكان الجديد «${p.place.ar}» غير صالحة.`);
      for (const c of p.countries || []) if (!A3.has(c)) add('warn', L, `خطة اللقطات ${L}: الرمز ${c} ليس رمز دولة معروفاً (ISO الثلاثي).`);
      if (p.craft && !TOP.includes(p.craft)) add('warn', L, `خطة اللقطات ${L}: المجسم ${p.craft} ليس جاهزاً؛ يلزم لوحة ChatGPT (منظر علوي) قبل الإنتاج.`);
      if (p.lane && !PL.lanes[p.lane] && !(PL.pipelines || {})[p.lane]) add('warn', L, `خطة اللقطات ${L}: المسار ${p.lane} غير موجود؛ سيُرسم بلا مسار.`);
      if (p.fx && !RULES.fx.includes(p.fx)) add('warn', L, `خطة اللقطات ${L}: المؤثر «${p.fx}» غير معروف (${RULES.fx.join('، ')}).`);
      if (p.title && p.title.split(/\s+/).length > 4) add('warn', L, `خطة اللقطات ${L}: العنوان «${p.title}» أطول من أربع كلمات.`);
    });
  }
  if (!R.sources.length && R.found.includes('المصادر')) add('warn', 0, 'قسم المصادر فارغ: كل رقم يحتاج مصدراً.');
  return finish(P, R, nW, est);
}
function finish(P, R, nW, est) {
  const need = [...new Set(R.plan.map(p => p.craft).filter(c => c && !TOP.includes(c)))];
  return { ok: !P.some(p => p.level === 'error'), words: nW, seconds: Math.round(est), lines: R.script.length, sheets_needed: need, problems: P };
}
export function fixPrompt(rep) {
  const lst = rep.problems.map(p => `- ${p.text}`).join('\n');
  return fs.readFileSync(path.join(ENG, 'prompts', 'gemini_fix.txt'), 'utf8').replace('{{PROBLEMS}}', lst);
}
export function sheetPrompt(objs) {
  const n = objs.length, cols = n <= 2 ? n : n <= 4 ? 2 : 3, rows = Math.ceil(n / cols);
  return fs.readFileSync(path.join(ENG, 'prompts', 'chatgpt_sheet.txt'), 'utf8').replace('{{N}}', n).replace('{{COLS}}', cols).replace('{{ROWS}}', rows)
    .replace('{{OBJECTS}}', objs.map((o, i) => `${i + 1}. ${o}`).join('\n'));
}
const say = rep => { console.log(`${rep.ok ? 'سليم' : 'يحتاج إصلاحاً'}: ${rep.lines} سطراً، ${rep.words} كلمة، نحو ${rep.seconds} ثانية.`); for (const p of rep.problems) console.log(`  ${p.level === 'error' ? 'خطأ ' : 'تنبيه'} ${p.text}`); if (rep.sheets_needed.length) console.log(`  لوحات مطلوبة: ${rep.sheets_needed.join('، ')}`); };

// ---------------------------------------------------------------- command line
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [, , cmd, ...rest] = process.argv; const opt = {}, pos = [];
  for (let i = 0; i < rest.length; i++) if (rest[i].startsWith('--')) opt[rest[i].slice(2)] = rest[++i]; else pos.push(rest[i]);
  const reply = p => { const f = fs.existsSync(p) && fs.statSync(p).isDirectory() ? path.join(p, 'reply.txt') : p; return fs.readFileSync(f, 'utf8'); };
  if (cmd === 'prompt') console.log(prompt(pos[0], opt));
  else if (cmd === 'sheet') console.log(sheetPrompt(pos));
  else if (cmd === 'check' || cmd === 'fix') { const rep = check(parse(reply(pos[0])), opt); if (cmd === 'fix') console.log(fixPrompt(rep)); else { say(rep); process.exit(rep.ok ? 0 : 2); } }
  else if (cmd === 'parse') {
    const R = parse(reply(pos[0])), proj = path.resolve(pos[1]), rep = check(R, opt); fs.mkdirSync(proj, { recursive: true });
    if (path.resolve(pos[0]) !== path.join(proj, 'reply.txt')) fs.writeFileSync(path.join(proj, 'reply.txt'), reply(pos[0]));
    fs.writeFileSync(path.join(proj, 'script.txt'), R.script.join('\n') + '\n');
    fs.writeFileSync(path.join(proj, 'sources.txt'), R.sources.join('\n') + '\n');
    fs.writeFileSync(path.join(proj, 'notes.txt'), R.notes.join('\n') + '\n');
    fs.writeFileSync(path.join(proj, 'shotplan.json'), JSON.stringify(R.plan, null, 1));
    fs.writeFileSync(path.join(proj, 'check.json'), JSON.stringify(rep, null, 1));
    say(rep); process.exit(rep.ok ? 0 : 2);
  } else { console.log(fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').slice(1, 8).join('\n')); process.exit(1); }
}
