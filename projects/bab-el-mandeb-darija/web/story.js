// Maven Map: Bab el-Mandeb v4 (Moroccan Darija voice; same camera/elements as the Egyptian v4, only the word anchors and Egyptian titles changed) - the v3 story and camera with the v4 type system: slot counters, engraved titles that enter
// and leave the same way, glass cards, leader-line labels, chapter progress, big-channel captions, a fixed opening question
import { cam, region, path, ships, measure, pin, ping, label, number, title, icon, cut, end, flash, punch, shake, captions, chapters, T, start } from './map.js';

const MANDAB = [43.38, 12.6];
const PERIM = [43.41, 12.65];
const YEM_COAST = [43.435, 12.685];
const DJI_COAST = [43.32, 12.48];
const SUEZ = [32.56, 29.92];
const DJIBOUTI = [43.15, 11.59];
const LANE = [[45.8, 11.8], [44.5, 12.2], [43.55, 12.55], [43.32, 12.72], [42.7, 13.6], [41.2, 16.0], [38.0, 20.8], [34.2, 27.2], [32.56, 29.9]];
const CAPE = [[45.8, 11.8], [51.5, 11.5], [51.0, 3.0], [44.0, -12.0], [33.0, -28.0], [22.0, -35.0], [18.0, -34.8], [11.0, -22.0], [-2.0, -5.0], [-15.0, 12.0], [-12.0, 30.0], [-8.0, 37.0]];
const NEAR = { lon: 43.4, lat: 12.62, span: 3.4 };   // the opening frame, also the last one (the video loops)

// ---------------------------------------------------------------- 0-4.5 s  HOOK: the question is on screen from frame 1
cam(0, { ...NEAR, span: 4.6 });
cam(T('عشرين'), { ...NEAR, ease: 'io' });
title('علاش هاد الممر مخيف؟', { at: 0, dur: .01, dock: T('عرضو') - .3, until: T('مضيق') - .45, color: 'yellow', anim: 'wipe' });   // docks up, stays until the answer
ships(LANE, { at: 0, count: 5, speed: .045, size: 1.15, color: 'yellow', until: T('التجارة') });
ping(MANDAB, { at: .2, color: '#ffd23f', until: T('عشرين') });
measure(YEM_COAST, DJI_COAST, { at: T('عرضو'), dur: 1.1, to: 20, unit: 'كم', color: '#ffd23f', until: T('التجارة') });
number(null, '# كم', { at: T('عرضو') + .1, until: T('لو') - .1, count: { to: 20, dur: .9 }, color: 'yellow', sub: 'عرض الممر بالكامل' });
punch(T('عشرين'), { amt: .12 });
title('لو تسدّ؟', { at: T('تسد'), until: T('التجارة') + 1.6, color: 'red' });
shake(T('تسد'), { amt: 1.2 });

// ---------------------------------------------------------------- 5-13 s  STAKES: pull out to the whole world, oil burns
cam(T('التجارة'), { ...NEAR, span: 6 });
cam(T('كلها'), { lon: 40, lat: 18, span: 58, ease: 'io' });
path(LANE, { at: T('العالمية'), dur: 1.4, color: '#4fd2ff', width: 7, glow: true, until: T('شنو') });
number(null, 'النفط ↑', { at: T('البترول'), until: T('شنو') - .1, color: 'fire', sub: 'الأسعار تشتعل في لحظات' });
shake(T('تشعل'), { amt: .8 });
cam(T('شنو'), { lon: 40, lat: 18, span: 50 });
cam(T('مخيف'), { lon: 43.4, lat: 12.7, span: 7, dive: true });

// ---------------------------------------------------------------- 13-23 s  REVEAL: name, nickname, three countries, two continents
title('مضيق باب المندب', { at: T('مضيق'), until: T('بوابة') - .1, color: 'yellow', anim: 'words' });
punch(T('المندب'), { amt: .1 });
cam(T('الدموع'), { lon: 43.4, lat: 12.7, span: 5.2, ease: 'sine' });   // slow push while the nickname lands
punch(T('بوابة'), { amt: .08 });
title('«بوابة الدموع»', { at: T('بوابة'), until: T('الممر') - .05, color: 'red', anim: 'blur' });
cam(T('الممر'), { lon: 43.4, lat: 12.7, span: 6.5, ease: 'io' });
cam(T('اليمن'), { lon: 43.2, lat: 13.6, span: 13, ease: 'io' });
region('YEM', { at: T('اليمن'), style: 'gold', label: 'اليمن', labelAt: [45.2, 15.0], until: T('المضيق') + .4 });
label([47.5, 17.8], 'آسيا', { at: T('آسيا'), color: '#ffd23f', until: T('المضيق'), anim: 'bubble' });
region('DJI', { at: T('وجيبوتي'), style: 'ally', label: 'جيبوتي', labelAt: [42.5, 11.6], until: T('المضيق') + .4 });
region('ERI', { at: T('وإريتريا'), style: 'soft', label: 'إريتريا', labelAt: [38.8, 15.6], until: T('المضيق') + .4 });
label([39.2, 9.4], 'إفريقيا', { at: T('إفريقيا'), color: '#2fd6c8', until: T('المضيق'), anim: 'bubble' });

// ---------------------------------------------------------------- 23-31 s  THE ARTERY: Red Sea to Suez
cam(T('المضيق'), { lon: 40.5, lat: 19, span: 26, ease: 'io' });
path(LANE, { at: T('البوابة'), dur: 2.4, color: '#4fd2ff', width: 8, glow: true, until: T('بريم') });
label([38.6, 20.5], 'البحر الأحمر', { at: T('للبحر'), color: '#bfe8ff', until: T('بقناة') });
cam(T('بقناة'), { lon: 33.2, lat: 28.6, span: 9.5, ease: 'io' });
pin(SUEZ, { at: T('السويس'), label: 'قناة السويس', color: '#ffd23f', until: T('كل') });
punch(T('السويس'), { amt: .1 });
cam(T('وأوروبا.end'), { lon: 33.0, lat: 28.8, span: 8.6, ease: 'sine' });   // hold on Suez until the sentence ends

// ---------------------------------------------------------------- 31-49 s  THE NUMBERS: slot counters, a punch on every one
cam(T('كل'), { lon: 43.6, lat: 13.2, span: 5.6, ease: 'io' });
ships(LANE, { at: T('كل'), count: 9, speed: .05, size: 1.25, color: 'yellow', until: T('بريم') });
number(null, '+#', { at: T('واحد'), until: T('يعني') - .15, count: { to: 21000, dur: 1.4 }, color: 'yellow', sub: 'باخرة تجارية كل سنة' });
punch(T('ألف'), { amt: .1 });
number(null, '#%', { at: T('طناش'), until: T('وماشي') - .15, count: { to: 12, dur: 1.0 }, color: 'yellow', sub: 'من تجارة العالم كلها' });
cam(T('الخرم'), { lon: 43.45, lat: 12.65, span: 3.6, ease: 'io' });
punch(T('الخرم'), { amt: .16 });
number(null, '# مليون', { at: T('ستة'), until: T('ولكن') - .15, count: { to: 6, dur: .95 }, color: 'fire', sub: 'برميل نفط كل يوم' });
label([44.5, 14.8], 'إلى أوروبا ↑', { at: T('أوروبا'), pill: '#111a2b', color: '#ffd23f', until: T('ولكن'), anim: 'bubble', lead: false });

// ---------------------------------------------------------------- 49-62 s  THE WEAK POINT: Perim, 3 km, a cheap drone
cut(T('بريم'));
cam(T('ولكن'), { lon: 43.45, lat: 12.65, span: 2.6 });
cam(T('بريم'), { lon: 43.43, lat: 12.67, span: .85, ease: 'io' });
flash(T('بريم'), .3);
pin(PERIM, { at: T('جزيرة'), label: 'جزيرة بريم', color: '#ff4757', until: T('الشرقي') });
cam(T('الشرقي'), { lon: 43.425, lat: 12.668, span: .55, ease: 'sine' });
measure(PERIM, YEM_COAST, { at: T('الشرقي'), dur: 1.0, to: 3, unit: 'كم', color: '#ff4757', until: T('صاروخ') });
number(null, '# كم فقط', { at: T('تلاتة'), until: T('صاروخ') - .1, count: { to: 3, dur: .95 }, color: 'red', sub: 'عرض الممر الشرقي' });
punch(T('تلاتة'), { amt: .18 }); shake(T('كيلومتر#2'), { amt: .9 });
cam(T('صاروخ'), { lon: 43.6, lat: 12.9, span: 5.4, ease: 'io' });
title('صاروخ… ولا درون رخيص', { at: T('صاروخ'), until: T('يقفل') - .1, level: 'sub', color: 'red' });
region('YEM', { at: T('سواحل'), style: 'danger', until: T('وهادشي'), label: false });
cam(T('فثواني.end'), { lon: 43.55, lat: 12.8, span: 4.8, ease: 'sine' });
icon(MANDAB, 'x', { at: T('يقفل'), size: 2.2, color: 'red', until: T('ولهادشي') });
shake(T('يقفل'), { amt: 1.4 }); flash(T('يقفل'), .25);
title('فثواني!', { at: T('فثواني'), until: T('وهادشي') - .05, color: 'red' });

// ---------------------------------------------------------------- 63-73 s  THE DETOUR: the whole of Africa, drawn in full
cam(T('وهادشي'), { lon: 43.6, lat: 12.8, span: 6 });
cam(T('إفريقيا#2'), { lon: 22, lat: 2, span: 82, ease: 'io' });
cam(T('ولهادشي') - .02, { lon: 21, lat: 1, span: 78, ease: 'sine' });   // hold the whole detour on screen, then hard cut
title('الهروب حول إفريقيا', { at: T('تهرب'), until: T('جوج') - .1, level: 'sub', color: 'yellow', anim: 'wipe' });
path(CAPE, { at: T('رأس'), dur: 2.8, color: '#ff4757', width: 9, dash: [20, 14], flow: 40, until: T('ولهادشي') });
ships(CAPE, { at: T('الرجاء'), count: 4, speed: .03, size: 1.3, color: 'red', until: T('ولهادشي') });
number(null, '+# يوم', { at: T('جوج'), until: T('ملايير') - .1, count: { to: 14, dur: .95 }, color: 'fire', sub: 'سفر إضافي لكل باخرة' });
title('ملايير الدولارات', { at: T('ملايير'), until: T('ولهادشي') - .05, color: 'fire', anim: 'blur' });
punch(T('ملايير'), { amt: .12 });

// ---------------------------------------------------------------- 73-81 s  THE POWERS: who sits next to the gate
cut(T('ولهادشي'));
cam(T('ولهادشي'), { lon: 43.2, lat: 11.9, span: 4.2 });
cam(T('القوى'), { lon: 43.25, lat: 11.95, span: 3.2, ease: 'io' });
region('DJI', { at: T('ولهادشي'), style: 'ally', label: false, until: T('حيت') });
icon(DJIBOUTI, 'star', { at: T('القواعد'), badge: 'hex', color: '#ffd23f', size: 1.2, until: T('حيت') });
label([42.55, 11.05], 'قاعدة أمريكية', { at: T('أمريكا'), pill: '#111a2b', color: '#7fc8ff', until: T('حيت'), anim: 'bubble', lead: [-60, 120] });
label([43.75, 11.2], 'قاعدة صينية', { at: T('للصين'), pill: '#111a2b', color: '#ff7a7a', until: T('حيت'), anim: 'bubble', lead: [60, 120] });
label([43.0, 12.25], 'خطوات من الممر', { at: T('خطوات'), pill: '#111a2b', color: '#ffd23f', until: T('حيت'), anim: 'bubble', lead: [0, -110] });
punch(T('خطوات'), { amt: .1 });

// ---------------------------------------------------------------- 81-89 s  PAYOFF + LOOP back to the opening frame
cam(T('حيت'), { lon: 43.3, lat: 12.2, span: 4.4 });
cam(T('فمفتاح'), { ...NEAR, ease: 'io' });
title('مفتاح الاقتصاد العالمي', { at: T('فمفتاح'), until: T('كامل.end+1.2'), color: 'yellow', anim: 'words', mag: 1.5 });
punch(T('فمفتاح'), { amt: .14 }); shake(T('كامل'), { amt: 1.1 });
ships(LANE, { at: T('حيت'), count: 5, speed: .045, size: 1.15, color: 'yellow' });

chapters([['الموقع', T('باب')], ['الأرقام', T('كل')], ['الخطر', T('بريم')], ['البديل', T('وهادشي')], ['القوى', T('ولهادشي')]]);
captions({ emph: ['ملايير', 'عملاقة'], danger: ['تسد', 'تسدّ', 'يقفل', 'مخيف', 'تشعل', 'توقف', 'درون', 'تهرب', 'الدموع'], nums: ['طناش', 'تلاتة', 'ستة', 'جوج', 'سيمانات', 'واحد', 'عشرين', 'ألف', 'مليون', 'ملايير', 'فالمية'] });
end(T('كامل.end+1.2'));
start();
