// Maven Map: Bab el-Mandeb v5 - the v4 voice and script, rebuilt on the reference-video plan (REFERENCE_ANALYSIS_PLAN.md s.7):
// clean screen (one small caption line that yields to titles), flags inside borders, real objects (ChatGPT sprites),
// real photos, map worlds instead of chapter text (sepia history, night danger), globe hops, a closed loop.
import { cam, region, path, ships, measure, label, number, title, icon, cut, end, flash, punch, shake, captions, T, start,
  sprite, photo, flagPin, mapStyle, smoke, drift, leads, crack } from './map.js';

const MANDAB = [43.38, 12.6];
const PERIM = [43.42, 12.66];
const YEM_COAST = [43.435, 12.685];
const DJI_COAST = [43.32, 12.48];
const SUEZ = [32.56, 29.92];
const CAMP = [43.155, 11.547];      // Camp Lemonnier (US), next to Djibouti airport
const PLA = [43.05, 11.59];         // the Chinese base at Doraleh
const LANE = [[45.8, 11.8], [44.5, 12.2], [43.55, 12.55], [43.32, 12.72], [42.7, 13.6], [41.2, 16.0], [38.0, 20.8], [34.2, 27.2], [32.56, 29.9]];
const STRAIT = [[44.2, 12.25], [43.55, 12.55], [43.32, 12.72], [42.9, 13.2]];
const ASIA = [[43.4, 12.6], [48.5, 12.4], [56, 13.5], [66, 11], [74, 7.5], [80, 5.6], [88, 5.8], [95, 5.6], [100.5, 3], [103.6, 1.3]];
const MED = [[32.56, 29.9], [32.3, 31.3], [28, 33.4], [20, 34.6], [12, 37.2], [5, 37.4], [-1, 36.4], [-5.6, 35.95], [-9.5, 38.5]];
const CAPE = [[45.8, 11.8], [51.5, 11.5], [51.0, 3.0], [44.0, -12.0], [33.0, -28.0], [22.0, -35.0], [18.0, -34.8], [11.0, -22.0], [-2.0, -5.0], [-15.0, 12.0], [-12.0, 30.0], [-8.0, 37.0]];
const NEAR = { lon: 43.4, lat: 12.62, span: 3.4 };   // the opening frame, also the last one (the video loops)

drift(.018);
leads({ number: .6, photo: .6, sprite: .3, label: .3 });

// ---------------------------------------------------------------- 0-4.5 s  HOOK: one question, a giant tanker in the gate
cam(0, { ...NEAR, span: 4.2 });
cam(T('عشرين'), { ...NEAR, ease: 'io' });
title('ليه الممر ده مرعب؟', { at: 0, dur: .01, until: T('عرضه') - .2, color: 'yellow', anim: 'wipe' });
sprite('tanker', null, { at: 0, early: 0, along: LANE, speed: .018, phase: .17, size: 200, until: T('التجارة') });
path(STRAIT, { at: .15, dur: .6, color: '#ff4757', width: 13, until: T('عشرين') });
measure(YEM_COAST, DJI_COAST, { at: T('عرضه'), dur: 1.1, to: 20, unit: 'كم', color: '#ffd23f', until: T('التجارة') });
number(null, '# كم', { at: T('عرضه'), until: T('لو') - .1, count: { to: 20, dur: .9 }, color: 'yellow' });
punch(T('عشرين'), { amt: .12 });
title('لو اتقفل؟', { at: T('اتقفل'), until: T('كلها'), color: 'red' });
shake(T('اتقفل'), { amt: 1.2 });

// ---------------------------------------------------------------- 5-10 s  STAKES: globe pull-out, trade lines light up, oil burns
cam(T('التجارة'), { ...NEAR, span: 6 });
cam(T('كلها'), { lon: 42, lat: 20, span: 64, ease: 'io' });
path(ASIA, { at: T('العالمية'), dur: 1.3, color: '#4fd2ff', width: 7, until: T('إيه') });
path(LANE, { at: T('العالمية') + .3, dur: 1.1, color: '#4fd2ff', width: 7, until: T('إيه') });
path(MED, { at: T('العالمية') + .9, dur: 1.2, color: '#4fd2ff', width: 7, until: T('إيه') });
sprite('barrels', [46.5, 24.5], { at: T('البترول'), size: 210, until: T('مرعب') });
smoke([46.5, 24.2], { at: T('هتولع') - .15, size: 1.3, until: T('مرعب') });
shake(T('هتولع'), { amt: .9 });
cam(T('إيه'), { lon: 42, lat: 20, span: 56 });
cam(T('وليه'), { lon: 42, lat: 19.5, span: 54, ease: 'sine' });   // a held breath...
cam(T('مرعب'), { lon: 43.4, lat: 12.7, span: 7, dive: true });   // ...then the dive
flash(T('مرعب'), .35);

// ---------------------------------------------------------------- 13-18 s  CHAPTER 1, history: the map turns sepia, a dhow sails the gate
title('مضيق باب المندب', { at: T('مضيق'), until: T('بوابة') - .3, color: 'yellow', anim: 'words' });
punch(T('المندب'), { amt: .1 });
mapStyle('sepia', T('تاريخياً') - .2);
cam(T('الدموع'), { lon: 43.4, lat: 12.7, span: 5.2, ease: 'sine' });
sprite('dhow', null, { at: T('تاريخياً'), along: [[44.1, 12.3], [43.55, 12.55], [43.3, 12.78]], travel: 3.2, ease: 'linear', size: 250, until: T('الممر') });
title('«بوابة الدموع»', { at: T('بوابة'), until: T('الممر') - .05, color: 'red', anim: 'blur' });
punch(T('بوابة'), { amt: .08 });

// ---------------------------------------------------------------- 18-23 s  back to satellite: three flags, two continents
mapStyle('satellite', T('الممر'));
cam(T('الممر'), { lon: 43.4, lat: 12.7, span: 6.5, ease: 'io' });
cam(T('اليمن'), { lon: 44.0, lat: 14.0, span: 17, ease: 'io' });
region('YEM', { at: T('اليمن'), flag: true, label: 'اليمن', labelPill: '#111a2b', labelAt: [46.2, 15.6], until: T('المضيق') + .4 });
region(['SAU', 'OMN'], { at: T('آسيا'), style: 'vivid', color: '#ffd23f', opacity: .5, until: T('المضيق') + .4 });
label([46.2, 20.2], 'آسيا', { at: T('آسيا'), color: '#ffd23f', until: T('المضيق'), anim: 'bubble' });
region('DJI', { at: T('وجيبوتي'), flag: true, label: 'جيبوتي', labelPill: '#111a2b', labelAt: [42.6, 11.5], until: T('المضيق') + .4 });
region('ERI', { at: T('وإريتريا'), flag: true, label: 'إريتريا', labelPill: '#111a2b', labelAt: [38.9, 15.4], until: T('المضيق') + .4 });
region(['ETH', 'SDN', 'SOM', 'SOL'], { at: T('إفريقيا'), style: 'vivid', color: '#2fd6c8', opacity: .45, until: T('المضيق') + .4 });
label([38.8, 9.2], 'إفريقيا', { at: T('إفريقيا'), color: '#2fd6c8', until: T('المضيق'), anim: 'bubble' });

// ---------------------------------------------------------------- 23-31 s  THE ARTERY: Red Sea, Suez, Europe (a long hop north)
cam(T('المضيق'), { lon: 40.5, lat: 19, span: 26, ease: 'io' });
path(LANE, { at: T('البوابة'), dur: 2.2, color: '#4fd2ff', width: 8, until: T('كل') });
sprite('container_ship', null, { at: T('البوابة'), along: LANE.slice(2), travel: 6.5, ease: 'linear', size: 150, until: T('بقناة') });
label([38.6, 20.5], 'البحر الأحمر', { at: T('للبحر'), color: '#bfe8ff', until: T('بقناة') });
cam(T('بقناة'), { lon: 31.5, lat: 30, span: 15, ease: 'io' });
label(SUEZ, 'قناة السويس', { at: T('السويس'), pill: '#111a2b', color: '#ffd23f', until: T('كل'), anim: 'bubble', lead: [-40, -120] });
punch(T('السويس'), { amt: .1 });
path(MED, { at: T('السويس') + .2, dur: 1.0, color: '#4fd2ff', width: 8, until: T('كل') });
cam(T('وأوروبا.end'), { lon: 26, lat: 33, span: 30, ease: 'io' });

// ---------------------------------------------------------------- 31-49 s  CHAPTER 2, the numbers: a stream of ships, barrels, slot counters
cam(T('كل'), { lon: 43.6, lat: 13.0, span: 5.6, ease: 'io' });
ships(LANE, { at: T('كل'), count: 10, speed: .05, size: 1.0, color: 'yellow', until: T('بريم') });
sprite('container_ship', null, { at: T('كل'), along: LANE.slice(0, 5), speed: .05, phase: .1, size: 135, until: T('بس#3') });
sprite('tanker', null, { at: T('سفينة'), along: LANE.slice(0, 5), speed: .05, phase: .55, size: 135, until: T('بس#3') });
number(null, '+#', { at: T('واحد'), until: T('يعني') - .15, count: { to: 21000, dur: 1.4 }, color: 'yellow', sub: 'سفينة تجارية كل سنة' });
punch(T('ألف'), { amt: .1 });
number(null, '#%', { at: T('اثنا'), until: T('ومش') - .15, count: { to: 12, dur: 1.0 }, color: 'yellow', sub: 'من تجارة العالم كلها' });
cam(T('الخرم'), { lon: 43.45, lat: 12.65, span: 3.6, ease: 'io' });
punch(T('الخرم'), { amt: .16 });
number(null, '# مليون', { at: T('ستة'), until: T('بس#3') - .15, count: { to: 6, dur: .95 }, color: 'fire', sub: 'برميل نفط كل يوم' });
sprite('barrels', [43.78, 12.42], { at: T('برميل'), size: 170, until: T('بس#3') });
label([44.5, 14.8], 'إلى أوروبا ↑', { at: T('أوروبا'), pill: '#111a2b', color: '#ffd23f', until: T('بس#3'), anim: 'bubble', lead: false });

// ---------------------------------------------------------------- 49-56 s  CHAPTER 3, danger: the map goes night, Perim from space
cut(T('بريم'));
mapStyle('night', T('بص'));
cam(T('بس#3'), { lon: 43.45, lat: 12.65, span: 2.6 });
cam(T('بريم'), { lon: 43.43, lat: 12.67, span: .85, ease: 'io' });
flash(T('بريم'), .3);
photo('perim_nasa', { at: T('جزيرة'), frame: 'hex', pt: PERIM, y: 700, size: 210, color: '#ff4757', until: T('الشرقي') });
label(PERIM, 'جزيرة بريم', { at: T('بريم'), pill: '#111a2b', color: '#ff8a95', until: T('الشرقي'), anim: 'bubble', lead: [0, 150] });
cam(T('الشرقي'), { lon: 43.425, lat: 12.668, span: .55, ease: 'sine' });
measure(PERIM, YEM_COAST, { at: T('الشرقي'), dur: 1.0, to: 3, unit: 'كم', color: '#ff4757', until: T('صاروخ') });
number(null, '# كم فقط', { at: T('تلاتة'), until: T('صاروخ') - .1, count: { to: 3, dur: .95 }, color: 'red', sub: 'عرض الممر الشرقي' });
punch(T('تلاتة'), { amt: .18 }); shake(T('بس#4'), { amt: .9 });

// ---------------------------------------------------------------- 57-63 s  the threat: a real warship firing, then missile + drone hit a tanker
photo('uss_carney', { at: T('صاروخ'), early: .1, frame: 'full', until: T('رخيصة'), caption: 'هجوم حقيقي في البحر الأحمر' });
title('صاروخ… أو مسيّرة رخيصة', { at: T('صاروخ'), until: T('تقفل') - .1, level: 'sub', color: 'red' });
cam(T('صاروخ'), { lon: 43.6, lat: 12.85, span: 4.6, ease: 'io' });
sprite('tanker', [43.32, 12.6], { at: T('رخيصة'), early: 0, size: 190, bob: true, until: T('تقفل') });
sprite('drone', [44.25, 13.25], { at: T('رخيصة'), to: [43.4, 12.66], travel: 2.1, size: 120, until: T('تقفل') });
region('YEM', { at: T('سواحل'), style: 'danger', until: T('وده'), label: false });
sprite('missile', [43.9, 13.35], { at: T('سواحل'), to: [43.34, 12.62], travel: 1.05, size: 135, until: T('تقفل') });
cam(T('تقفل') - .35, { lon: 43.55, lat: 12.8, span: 4.4, ease: 'sine' });   // a frozen beat before the hit
sprite('tanker_burning', [43.32, 12.6], { at: T('تقفل'), early: 0, size: 190, until: T('وده') + .3 });
sprite('explosion', [43.33, 12.63], { at: T('تقفل'), early: 0, size: 230, until: T('تقفل') + .9 });
smoke([43.33, 12.62], { at: T('تقفل') + .2, size: 1.1, until: T('وده') + .3 });
icon(MANDAB, 'x', { at: T('ثواني') - .25, size: 2.0, color: 'red', until: T('وده') });
shake(T('تقفل'), { amt: 1.4 }); flash(T('تقفل'), .28);
title('في ثواني!', { at: T('ثواني'), until: T('وده') - .05, color: 'red' });

// ---------------------------------------------------------------- 63-73 s  CHAPTER 4, the detour: satellite again, a hop to all of Africa
mapStyle('satellite', T('وده'));
cam(T('وده'), { lon: 43.6, lat: 12.8, span: 6 });
cam(T('إفريقيا#2'), { lon: 22, lat: 2, span: 82, ease: 'io' });
cam(T('عشان#2') - .02, { lon: 21, lat: 1, span: 78, ease: 'sine' });
path(CAPE, { at: T('رأس'), dur: 2.8, color: '#ff4757', width: 9, dash: [20, 14], flow: 40, until: T('عشان#2') });
sprite('container_ship', null, { at: T('الرجاء'), along: CAPE, travel: 4.4, ease: 'linear', size: 130, until: T('عشان#2') });
number(null, '+# يوم', { at: T('أسبوعين'), until: T('مليارات') - .1, count: { to: 14, dur: .95 }, color: 'fire', sub: 'سفر إضافي لكل سفينة' });
title('مليارات الدولارات', { at: T('مليارات'), until: T('عشان#2') - .05, color: 'fire', anim: 'blur' });
sprite('money_pallet', [20, 2], { at: T('مليارات'), size: 230, until: T('عشان#2') });
punch(T('مليارات'), { amt: .12 });

// ---------------------------------------------------------------- 73-81 s  CHAPTER 5, the powers: flags on the real bases, a real photo
cut(T('عشان#2'));
cam(T('عشان#2'), { lon: 43.15, lat: 11.75, span: 3.2 });
cam(T('القوى'), { lon: 43.15, lat: 11.75, span: 2.4, ease: 'io' });
region('DJI', { at: T('عشان#2'), style: 'ally', label: false, until: T('لأن') });
flagPin('USA', CAMP, { at: T('أمريكا'), x: 760, y: 1120, until: T('لأن') });
flagPin('CHN', PLA, { at: T('للصين'), x: 300, y: 1120, until: T('لأن') });
photo('camp_lemonnier', { at: T('قواعدهم'), frame: 'card', y: 640, size: 170, caption: 'قاعدة أمريكية في جيبوتي', until: T('خطوات') - .15 });
label([43.35, 12.05], 'خطوات من الممر', { at: T('خطوات'), pill: '#111a2b', color: '#ffd23f', until: T('لأن'), anim: 'bubble', lead: [0, -90] });
punch(T('خطوات'), { amt: .1 });

// ---------------------------------------------------------------- 81-89 s  PAYOFF + LOOP: back to the opening frame, the tanker sails again
cam(T('لأن'), { lon: 43.3, lat: 12.2, span: 4.4 });
cam(T('مفتاح'), { ...NEAR, span: 4.2, ease: 'io' });
title('مفتاح الاقتصاد العالمي', { at: T('مفتاح'), until: T('كله.end+.5'), color: 'yellow', anim: 'words', mag: 1.5 });
punch(T('مفتاح'), { amt: .14 }); shake(T('كله'), { amt: 1.1 });
sprite('tanker', null, { at: T('لأن'), early: 0, along: LANE, speed: .018, phase: .1, size: 200 });
path(STRAIT, { at: T('كله'), dur: .6, color: '#ff4757', width: 13 });

captions({ style: 'line', emph: ['مليارات', 'عملاقة'] });
end(T('كله.end+1.2'));
start();
