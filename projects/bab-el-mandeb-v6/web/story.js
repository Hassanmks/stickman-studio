// Maven Map: Bab el-Mandeb v6 - "logic in everything" (user rule 2026-11-17). The v5 film (same voice, script and look),
// with every moving object seen from above and turned to its heading, every sea route on water only, real size and speed
// order, the strike tied to the missile's arrival, and every number / flag / ruler checked against the map and the voice.
// The engine's logic() checklist runs before any frame and stops the render on a violation.
import { cam, region, path, measure, label, number, title, icon, cut, end, flash, punch, shake, captions, T, start,
  sprite, photo, flagPin, mapStyle, smoke, drift, leads } from './map.js';

// ---- places (checked against the border data and the satellite picture)
const PERIM = [43.42, 12.653];            // Perim (Mayyun) island, Yemen
const PERIM_E = [43.447, 12.662];         // its east tip
const PERIM_W = [43.39, 12.64];           // its west tip
const YEM_PT = [43.467, 12.685];          // the Yemeni mainland facing Perim (Ras Menheli): 3 km across the east channel
const SIYYAN = [43.313, 12.478];          // Ras Siyyan, Djibouti: 20 km across the main (west) channel
const MANDAB = [43.355, 12.562];          // the middle of the main channel
const SUEZ = [32.56, 29.92];
const CAMP = [43.149, 11.544];            // Camp Lemonnier (US), next to Djibouti airport (11°32'37"N 43°08'55"E)
const PLA = [43.0647, 11.5914];           // the Chinese support base at Doraleh port (11°35'29"N 43°03'53"E)
const INLAND = [43.98, 12.98];            // south-west Yemen, behind the coast east of the strait (missile)
const KHAWKHAH = [43.31, 13.78];          // further up the same coast (drone)
// ---- sea routes: every point is in open water, mid-channel (the engine refuses a route that touches land)
// Gulf of Aden -> the main channel west of Perim -> east of the Hanish islands -> the middle of the Red Sea -> Strait of Gubal -> Gulf of Suez
const LANE = [[46.5, 11.9], [45.2, 12.0], [44.3, 12.2], [43.95, 12.3], [43.62, 12.42], MANDAB, [43.27, 12.82], [43.0, 13.25], [42.98, 13.85], [42.55, 14.55], [42.0, 15.35],
  [41.0, 16.6], [39.8, 18.5], [38.3, 20.6], [37.3, 22.8], [35.9, 25.0], [34.6, 26.95], [34.13, 27.6], [33.75, 27.9], [33.35, 28.3], [32.92, 28.95], [32.56, 29.5], [32.56, 29.9]];
const GATE = LANE.slice(2, 9);            // through the gate itself
const STREAM = LANE.slice(0, 12);         // Gulf of Aden -> southern Red Sea
const ASIA = [MANDAB, [43.62, 12.42], [44.3, 12.2], [48.5, 12.4], [56, 13.5], [66, 11], [74, 7.5], [80.5, 5.4], [88, 5.8], [94.5, 6.3], [97.0, 5.9], [98.8, 4.3], [100.3, 2.9], [102.0, 2.2], [103.0, 1.5], [103.6, 1.17]];
const MED = [[32.56, 29.9], [32.31, 31.27], [32.0, 32.2], [28, 33.4], [20, 34.6], [11.75, 37.38], [9.0, 37.9], [5, 37.6], [-1, 36.3], [-5.6, 35.95], [-7.5, 36.3], [-9.6, 36.8], [-10.2, 38.6]];
const CAPE = [[45.8, 11.8], [48.5, 12.3], [51.8, 12.05], [52.0, 10.4], [51.0, 6.0], [47.5, 1.0], [42.5, -5.0], [41.8, -11.5], [41.2, -17.0], [38.0, -23.5], [33.5, -29.5], [27.5, -34.8], [20.0, -35.6],
  [17.3, -34.2], [12.0, -22.0], [5, -8], [-6, -1], [-13, 4], [-19, 12], [-19.5, 20], [-19.5, 28.3], [-15, 33], [-10.5, 37.0], [-10.2, 38.6]];
const NEAR = { lon: 43.4, lat: 12.62, span: 2.4 };   // the opening frame, also the last one (the video loops)

drift(.018);
leads({ number: .6, photo: .6, sprite: .3, label: .3 });

// ---------------------------------------------------------------- 0-4.5 s  HOOK: one question, a giant tanker sails into the gate, bow first
cam(0, { ...NEAR, span: 3.0 });
cam(T('عشرين'), { ...NEAR, ease: 'io' });
title('ليه الممر ده مرعب؟', { at: 0, dur: .01, until: T('عرضه') - .2, color: 'yellow', anim: 'wipe' });
sprite('tanker', null, { at: -.1, early: 0, dur: .01, along: GATE, speed: .092, phase: .22 - .0092, scale: 1.15, until: T('التجارة') });   // on screen from frame 0, at phase .22
path(GATE.slice(1, 6), { at: .15, dur: .6, color: '#ff4757', width: 13, sea: true, until: T('عشرين') });
measure(PERIM_W, SIYYAN, { at: T('عرضه'), dur: 1.1, to: 20, unit: 'كم', color: '#ffd23f', label: false, until: T('التجارة') });
number(null, '# كم', { at: T('عرضه'), until: T('لو') - .1, count: { to: 20, dur: .9 }, color: 'yellow' });
punch(T('عشرين'), { amt: .12 });
title('لو اتقفل؟', { at: T('اتقفل'), until: T('كلها'), color: 'red' });
shake(T('اتقفل'), { amt: 1.2 });

// ---------------------------------------------------------------- 5-10 s  STAKES: globe pull-out, trade lines light up, oil burns
cam(T('التجارة'), { ...NEAR, span: 6 });
cam(T('كلها'), { lon: 42, lat: 20, span: 64, ease: 'io' });
path(ASIA, { at: T('العالمية'), dur: 1.3, color: '#4fd2ff', width: 7, sea: true, until: T('إيه') });
path(LANE, { at: T('العالمية') + .3, dur: 1.1, color: '#4fd2ff', width: 7, sea: true, until: T('إيه') });
path(MED, { at: T('العالمية') + .9, dur: 1.2, color: '#4fd2ff', width: 7, sea: true, until: T('إيه') });
sprite('barrels', [46.5, 24.5], { at: T('البترول'), size: 210, on: 'land', in: 'SAU', until: T('مرعب') });
smoke([46.5, 24.2], { at: T('هتولع') - .15, size: 1.3, until: T('مرعب'), cause: 'a figure of speech in the script: oil prices catch fire (هتولع)' });
shake(T('هتولع'), { amt: .9 });
cam(T('إيه'), { lon: 42, lat: 20, span: 56 });
cam(T('وليه'), { lon: 42, lat: 19.5, span: 54, ease: 'sine' });   // a held breath...
cam(T('مرعب'), { lon: 43.4, lat: 12.68, span: 4.4, dive: true });   // ...then the dive
flash(T('مرعب'), .35);

// ---------------------------------------------------------------- 13-18 s  CHAPTER 1, history: the map turns sepia, a dhow sails the gate
title('مضيق باب المندب', { at: T('مضيق'), until: T('بوابة') - .3, color: 'yellow', anim: 'words' });
punch(T('المندب'), { amt: .1 });
mapStyle('sepia', T('تاريخياً') - .2);
cam(T('الدموع'), { lon: 43.4, lat: 12.64, span: 2.6, ease: 'sine' });
sprite('dhow', null, { at: T('تاريخياً'), along: [[43.56, 12.445], [43.46, 12.5], MANDAB, [43.31, 12.66]], travel: 2.6, scale: 2, until: T('الممر') });
title('«بوابة الدموع»', { at: T('بوابة'), until: T('الممر') - .05, color: 'red', anim: 'blur' });
punch(T('بوابة'), { amt: .08 });

// ---------------------------------------------------------------- 18-23 s  back to satellite: three flags, two continents
mapStyle('satellite', T('الممر'));
cam(T('الممر'), { lon: 43.4, lat: 12.7, span: 5.5, ease: 'io' });
cam(T('اليمن'), { lon: 44.0, lat: 14.0, span: 17, ease: 'io' });
region('YEM', { at: T('اليمن'), flag: true, label: 'اليمن', labelPill: '#111a2b', labelAt: [46.2, 15.6], until: T('المضيق') + .4 });
region(['SAU', 'OMN'], { at: T('آسيا'), style: 'vivid', color: '#ffd23f', opacity: .5, until: T('المضيق') + .4 });
label([46.2, 20.2], 'آسيا', { at: T('آسيا'), color: '#ffd23f', until: T('المضيق'), anim: 'bubble', in: 'SAU' });
region('DJI', { at: T('وجيبوتي'), flag: true, label: 'جيبوتي', labelPill: '#111a2b', labelAt: [42.6, 11.5], until: T('المضيق') + .4 });
region('ERI', { at: T('وإريتريا'), flag: true, label: 'إريتريا', labelPill: '#111a2b', labelAt: [38.9, 15.4], until: T('المضيق') + .4 });
region(['ETH', 'SDN', 'SOM', 'SOL'], { at: T('إفريقيا'), style: 'vivid', color: '#2fd6c8', opacity: .45, until: T('المضيق') + .4 });
label([38.8, 9.2], 'إفريقيا', { at: T('إفريقيا'), color: '#2fd6c8', until: T('المضيق'), anim: 'bubble', in: 'ETH' });

// ---------------------------------------------------------------- 23-31 s  THE ARTERY: Red Sea, Suez, Europe (a long hop north)
cam(T('المضيق'), { lon: 40.5, lat: 19, span: 26, ease: 'io' });
path(LANE, { at: T('البوابة'), dur: 2.2, color: '#4fd2ff', width: 8, sea: true, until: T('كل') });
sprite('container_ship', null, { at: T('البوابة'), along: LANE.slice(5), travel: 6.5, scale: 1.3, until: T('بقناة') + .9 });
label([38.6, 20.5], 'البحر الأحمر', { at: T('للبحر'), color: '#bfe8ff', until: T('بقناة'), in: 'sea' });
cam(T('بقناة'), { lon: 31.5, lat: 30, span: 15, ease: 'io' });
label(SUEZ, 'قناة السويس', { at: T('السويس'), pill: '#111a2b', color: '#ffd23f', until: T('كل'), anim: 'bubble', lead: [-40, -120] });
punch(T('السويس'), { amt: .1 });
path(MED, { at: T('السويس') + .2, dur: 1.0, color: '#4fd2ff', width: 8, sea: true, until: T('كل') });
cam(T('وأوروبا.end'), { lon: 26, lat: 33, span: 30, ease: 'io' });

// ---------------------------------------------------------------- 31-49 s  CHAPTER 2, the numbers: a stream of real ships, bow first, slot counters
cam(T('كل'), { lon: 43.6, lat: 13.0, span: 5.6, ease: 'io' });
for (let i = 0; i < 9; i++) sprite(i % 2 ? 'tanker' : 'container_ship', null, { at: T('كل'), early: 0, along: STREAM, speed: .03, phase: i / 9 + .03, sfx: i ? false : undefined, until: T('بس#3') });
number(null, '+#', { at: T('واحد'), until: T('يعني') - .15, count: { to: 21000, dur: 1.4 }, color: 'yellow', sub: 'سفينة تجارية كل سنة' });
punch(T('ألف'), { amt: .1 });
number(null, '#%', { at: T('اثنا'), until: T('ومش') - .15, count: { to: 12, dur: 1.0 }, color: 'yellow', sub: 'من تجارة العالم كلها' });
cam(T('الخرم'), { lon: 43.45, lat: 12.65, span: 3.6, ease: 'io' });
punch(T('الخرم'), { amt: .16 });
number(null, '# مليون', { at: T('ستة'), until: T('بس#3') - .15, count: { to: 6, dur: .95 }, color: 'fire', sub: 'برميل نفط كل يوم' });
label([44.5, 14.8], 'إلى أوروبا ↑', { at: T('أوروبا'), pill: '#111a2b', color: '#ffd23f', until: T('بس#3'), anim: 'bubble', lead: false });

// ---------------------------------------------------------------- 49-56 s  CHAPTER 3, danger: the map goes night, Perim from space
cut(T('بريم'));
mapStyle('night', T('بص'));
cam(T('بس#3'), { lon: 43.45, lat: 12.65, span: 2.6 });
cam(T('بريم'), { lon: 43.43, lat: 12.67, span: .85, ease: 'io' });
flash(T('بريم'), .3);
photo('perim_nasa', { at: T('جزيرة'), frame: 'hex', pt: PERIM, in: 'YEM', y: 700, size: 210, color: '#ff4757', until: T('الشرقي') });
label(PERIM, 'جزيرة بريم', { at: T('بريم'), pill: '#111a2b', color: '#ff8a95', until: T('الشرقي'), anim: 'bubble', lead: [0, 150], in: 'YEM' });
cam(T('الشرقي'), { lon: 43.452, lat: 12.674, span: .34, ease: 'sine' });
measure(PERIM_E, YEM_PT, { at: T('الشرقي'), dur: 1.0, to: 3, unit: 'كم', color: '#ff4757', until: T('صاروخ') });
number(null, '# كم فقط', { at: T('تلاتة'), until: T('صاروخ') - .1, count: { to: 3, dur: .95 }, color: 'red', sub: 'عرض الممر الشرقي' });
punch(T('تلاتة'), { amt: .18 }); shake(T('بس#4'), { amt: .9 });

// ---------------------------------------------------------------- 57-63 s  the threat: a real warship firing, then a missile and a drone from the Yemeni coast hit a tanker in the gate
// Cause and effect: the tanker is sailing; the missile flies to where the tanker WILL be and arrives on the word; the explosion,
// the fire and the jolt are made by that arrival; the tanker stops dead and burns. The slow drone left earlier and arrives later.
photo('uss_carney', { at: T('صاروخ'), early: .1, frame: 'full', until: T('رخيصة'), caption: 'هجوم حقيقي في البحر الأحمر' });
title('صاروخ… أو مسيّرة رخيصة', { at: T('صاروخ'), until: T('تقفل') - .1, level: 'sub', color: 'red' });
cam(T('صاروخ'), { lon: 43.4, lat: 13.15, span: 2.6, ease: 'io' });
const victim = sprite('tanker', null, { at: T('رخيصة'), early: 0, along: GATE, speed: .034, phase: .34, until: T('وده') + .3 });
region('YEM', { at: T('سواحل'), style: 'danger', until: T('وده'), label: false });
sprite('drone', KHAWKHAH, { from: 'YEM', to: victim, arrive: T('ثواني'), travel: 3.9, hit: { smoke: false, burn: false, shake: .9, size: .8 } });
sprite('missile', INLAND, { from: 'YEM', to: victim, arrive: T('تقفل'), travel: .95, hit: { until: T('وده') + .3, shake: 1.4 } });
cam(T('تقفل') - .35, { lon: 43.4, lat: 13.12, span: 2.5, ease: 'sine' });   // a frozen beat before the hit
icon(MANDAB, 'x', { at: T('ثواني') - .25, size: 2.0, color: 'red', until: T('وده') });
title('في ثواني!', { at: T('ثواني'), until: T('وده') - .05, color: 'red' });

// ---------------------------------------------------------------- 63-73 s  CHAPTER 4, the detour: satellite again, a hop to all of Africa
mapStyle('satellite', T('وده'));
cam(T('وده'), { lon: 43.6, lat: 12.8, span: 6 });
cam(T('إفريقيا#2'), { lon: 22, lat: 2, span: 82, ease: 'io' });
cam(T('عشان#2') - .02, { lon: 21, lat: 1, span: 78, ease: 'sine' });
path(CAPE, { at: T('رأس'), dur: 2.8, color: '#ff4757', width: 9, dash: [20, 14], flow: 40, sea: true, until: T('عشان#2') });
sprite('container_ship', null, { at: T('الرجاء'), along: CAPE, travel: 4.4, scale: 1.3, until: T('عشان#2') });
number(null, '+# يوم', { at: T('أسبوعين'), until: T('مليارات') - .1, count: { to: 14, dur: .95 }, color: 'fire', sub: 'سفر إضافي لكل سفينة', say: 'أسبوعين' });
title('مليارات الدولارات', { at: T('مليارات'), until: T('عشان#2') - .05, color: 'fire', anim: 'blur' });
sprite('money_pallet', [14.5, -42.5], { at: T('مليارات'), size: 230, until: T('عشان#2') });   // the price of the detour, set on the detour itself (off the Cape)
punch(T('مليارات'), { amt: .12 });

// ---------------------------------------------------------------- 73-81 s  CHAPTER 5, the powers: flags on the real bases, a real photo
cut(T('عشان#2'));
cam(T('عشان#2'), { lon: 43.15, lat: 11.75, span: 3.2 });
cam(T('القوى'), { lon: 43.15, lat: 11.75, span: 2.4, ease: 'io' });
region('DJI', { at: T('عشان#2'), style: 'ally', label: false, until: T('لأن') });
flagPin('USA', CAMP, { at: T('أمريكا'), x: 760, y: 1120, in: 'DJI', until: T('لأن') });
flagPin('CHN', PLA, { at: T('للصين'), x: 300, y: 1120, in: 'DJI', until: T('لأن') });
photo('camp_lemonnier', { at: T('قواعدهم'), frame: 'card', y: 640, size: 170, caption: 'قاعدة أمريكية في جيبوتي', until: T('خطوات') - .15 });
label([43.33, 12.2], 'خطوات من الممر', { at: T('خطوات'), pill: '#111a2b', color: '#ffd23f', until: T('لأن'), anim: 'bubble', lead: [0, -90], in: 'DJI' });
punch(T('خطوات'), { amt: .1 });

// ---------------------------------------------------------------- 81-89 s  PAYOFF + LOOP: back to the opening frame, the tanker comes to the gate again
// It reaches at the last frame exactly the place it has in the first frame (phase .22 of GATE), so the loop is seamless.
cam(T('لأن'), { lon: 43.3, lat: 12.2, span: 4.4 });
cam(T('مفتاح'), { ...NEAR, span: 3.0, ease: 'io' });
title('مفتاح الاقتصاد العالمي', { at: T('مفتاح'), until: T('كله.end+.5'), color: 'yellow', anim: 'words', mag: 1.5 });
punch(T('مفتاح'), { amt: .14 }); shake(T('كله'), { amt: 1.1 });
const LAST = T('كله.end+1.2');
sprite('tanker', null, { at: T('لأن'), early: 0, along: GATE, speed: .22 / (LAST - T('لأن')), phase: 0, scale: 1.15 });
path(GATE.slice(1, 6), { at: T('كله'), dur: .6, color: '#ff4757', width: 13, sea: true });

captions({ style: 'line', emph: ['مليارات', 'عملاقة'] });
end(LAST);
start();
