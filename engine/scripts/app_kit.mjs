// TaleSpark 2.0: the offline kit of the Android app (2026-11-17).
//   node app_kit.mjs [out.json]   (default: android/app/src/main/assets/madar_kit.json)
// The app writes the Gemini prompt and checks the reply with no internet and no PC, so it carries the same template,
// rules, place / lane keys, country codes, number words and sprite list as script_check.mjs. Run it after any change to
// engine/prompts, engine/data/script_rules.json, places.json or the sprites, then rebuild the app (build_app does it).
import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
import { prompt, CRAFT_AR } from './script_check.mjs';
const ENG = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const J = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const PL = J(path.join(ENG, 'data', 'places.json')), RULES = J(path.join(ENG, 'data', 'script_rules.json')), WORLD = J(path.join(ENG, 'data', 'world.json'));
const MAP = fs.readFileSync(path.join(ENG, 'engine', 'map.js'), 'utf8');
const NUMW = Function('return ' + MAP.match(/const NUMW = (\{[\s\S]*?\});/)[1])();
const TOP = fs.readdirSync(path.join(ENG, 'assets', 'sprites', 'top')).filter(f => f.endsWith('.png')).map(f => f.slice(0, -4));
// the prompt with every list filled; the app replaces only the topic, the angle, the seconds and the word range
const tpl = prompt('{{TOPIC}}', { seconds: 999, angle: '{{ANGLE}}' }).replace('نحو 999 ثانية', 'نحو {{SECONDS}} ثانية')
  .replace(/بين \d+ و\d+ كلمة/, 'بين {{WORDS_MIN}} و{{WORDS_MAX}} كلمة').replace('الزاوية: {{ANGLE}}', '{{ANGLE}}');
const kit = {
  version: new Date().toISOString().slice(0, 10), prompt: tpl,
  fix: fs.readFileSync(path.join(ENG, 'prompts', 'gemini_fix.txt'), 'utf8'),
  sheet: fs.readFileSync(path.join(ENG, 'prompts', 'chatgpt_sheet.txt'), 'utf8'),
  rules: RULES, places: Object.keys(PL.places), lanes: [...Object.keys(PL.lanes), ...Object.keys(PL.pipelines || {})],
  a3: WORLD.countries.map(c => c.a3), numw: Object.keys(NUMW).filter(k => /[؀-ۿ]/.test(k)), top: TOP, craft_ar: CRAFT_AR,
};
const out = process.argv[2] || path.join(ENG, '..', 'android', 'app', 'src', 'main', 'assets', 'madar_kit.json');
fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, JSON.stringify(kit));
console.log(`app kit: ${(fs.statSync(out).size / 1024).toFixed(1)} KB, ${kit.places.length} places, ${kit.lanes.length} lanes, ${kit.top.length} sprites -> ${out}`);
