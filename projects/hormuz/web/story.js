// Maven Map: the Strait of Hormuz (v1, 2026-11-17). First film of the item-9 pipeline: written from the draft that
// engine/scripts/draft.mjs made from script.txt + timing.json, every place and sea lane from engine/data/places.json.
// Narration in Modern Standard Arabic (user rule), Riko voice with every punctuation pause kept (voice.py "pauses": "punct").
// Facts: EIA 2025 (about 20 million b/d in 2024, ~20% of world petroleum liquids consumption, ~1/5 of world LNG mainly from
// Qatar, 84% of the crude going to Asia); Wikipedia (21 nmi / 39 km between Larak and Great Quoin, two 2-nmi lanes).
import { cam, region, path, measure, label, number, title, icon, flash, punch, shake, captions, T, start, end,
  sprite, flagPin, mapStyle, drift, leads, spot, lane, pipeline } from './map.js';

const GATE = lane('HORMUZ_GATE');                 // Gulf of Oman -> between the Quoins and Larak -> towards the Gulf
const NEAR = { lon: 56.45, lat: 26.6, span: 2.6 }; // the opening frame, also the last one (the video loops)
// the two traffic lanes (2 nmi each, 2 nmi apart): the middle line moved ~3.5 km to each side
function side(pts, km) {
  return pts.map((p, i) => { const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], kx = Math.cos(p[1] * Math.PI / 180);
    const dx = (b[0] - a[0]) * kx, dy = b[1] - a[1], L = Math.hypot(dx, dy) || 1, d = km / 111.2; return [p[0] + (-dy / L) * d / kx, p[1] + (dx / L) * d]; });
}
const IN = side(GATE, 3.5), OUT = side(GATE, -3.5).reverse();

drift(.018);
leads({ number: .6, photo: .6, sprite: .3, label: .3 });

// ---------------------------------------------------------------- 0-7.3 s  HOOK: one fact, a tanker already in the gate
cam(0, { ...NEAR, span: 3.3 });
cam(T('تسعة'), { ...NEAR, ease: 'io' });
title('خُمس نفط العالم!', { at: 0, dur: .01, until: T('تسعة') - .25, color: 'yellow', anim: 'wipe' });
sprite('tanker', null, { at: -.1, early: 0, dur: .01, along: GATE, speed: .014, phase: .30 - .0014, scale: 1.1, until: T('هنا') });
path(GATE, { at: .15, dur: .7, color: '#ff4757', width: 11, sea: true, until: T('هذا') });
number(null, '# كم', { at: T('تسعة'), until: T('هذا') - .1, count: { to: 39, dur: 1.0 }, color: 'yellow', sub: 'عرض المضيق كله' });
punch(T('وثلاثين'), { amt: .12 });

// ---------------------------------------------------------------- 7.3-12.7 s  the strait between two countries
cam(T('هذا'), { lon: 55.6, lat: 25.6, span: 24, ease: 'io' });
cam(T('الجنوب.end'), { lon: 55.6, lat: 25.4, span: 22, ease: 'sine' });
title('مضيق هُرمز', { at: T('مضيق'), until: T('إيران') - .1, color: 'yellow', anim: 'words' });
punch(T('هُرمز'), { amt: .1 });
region('IRN', { at: T('إيران'), flag: true, label: 'إيران', labelPill: '#111a2b', labelAt: [54.5, 32.0], until: T('هنا') + .3 });
region('OMN', { at: T('وسلطنة'), flag: true, label: 'سلطنة عُمان', labelPill: '#111a2b', labelAt: [56.8, 20.5], until: T('هنا') + .3 });

// ---------------------------------------------------------------- 12.7-21.9 s  the narrowest point: Larak to the Quoin islands, 39 km
cam(T('جزيرة'), { lon: 56.43, lat: 26.66, span: 1.15, ease: 'io', dive: true });
label(spot('LARAK'), 'جزيرة لارَك', { at: T('لارَكْ'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', in: 'IRN', lead: [0, -110] });
label(spot('QUOIN'), 'جزر السلامة', { at: T('وجزر'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', lead: [0, 110] });
measure(spot('LARAK_S'), spot('QUOIN'), { at: T('عرض'), dur: 1.0, to: 39, unit: 'كم', color: '#ffd23f', until: T('لكنّ') });
punch(T('تسعة#2'), { amt: .1 });

// ---------------------------------------------------------------- 21.9-33.3 s  only two lanes, one in and one out
cam(T('لكنّ'), { lon: 56.48, lat: 26.5, span: 1.5, ease: 'io' });
cam(T('هناك'), { lon: 56.48, lat: 26.5, span: 1.35, ease: 'sine' });
path(IN, { at: T('ممرّان'), dur: 1.0, color: '#4fd2ff', width: 9, sea: true, arrow: true, until: T('كل#2') });
path(OUT, { at: T('ممرّان') + .25, dur: 1.0, color: '#4fd2ff', width: 9, sea: true, arrow: true, until: T('كل#2') });
sprite('tanker', null, { at: T('الناقلات'), early: 0, along: IN, speed: .05, phase: .05, until: T('كل#2') });
sprite('tanker', null, { at: T('الناقلات'), early: 0, along: OUT, speed: .05, phase: .35, until: T('كل#2') });
number(null, '# كم', { at: T('أربعة'), until: T('كل#2') - .1, count: { to: 4, dur: .9 }, color: 'yellow', sub: 'عرض كل ممر تقريباً' });
label(IN[2], 'دخول', { at: T('للدخول'), pill: '#111a2b', color: '#bfe8ff', until: T('كل#2'), anim: 'bubble', in: 'sea' });
label(OUT[1], 'خروج', { at: T('للخروج'), pill: '#111a2b', color: '#bfe8ff', until: T('كل#2'), anim: 'bubble', in: 'sea' });

// ---------------------------------------------------------------- 33.3-41.5 s  the numbers: a stream of tankers into the Gulf
cam(T('كل#2'), { lon: 54.9, lat: 26.4, span: 6.5, ease: 'io' });
for (let i = 0; i < 7; i++) sprite('tanker', null, { at: T('كل#2'), early: 0, along: lane('HORMUZ_TO_RAS_TANURA'), speed: .012, phase: i / 7 + .04, sfx: i ? false : undefined, until: T('وليس') });
number(null, '# مليون', { at: T('عشرين'), until: T('عشرين#2') - .15, count: { to: 20, dur: 1.1 }, color: 'yellow', sub: 'برميل نفط كل يوم' });
punch(T('مليون'), { amt: .1 });
number(null, '#%', { at: T('عشرين#2'), until: T('وليس') - .1, count: { to: 20, dur: .9 }, color: 'fire', sub: 'من استهلاك العالم كله' });

// ---------------------------------------------------------------- 41.5-48.9 s  and the gas: Qatar's LNG through the same gate
cam(T('وليس'), { lon: 53.3, lat: 26.0, span: 7.2, ease: 'io' });
cam(T('نفسه.end'), { lon: 53.3, lat: 26.0, span: 6.8, ease: 'sine' });   // hold Qatar while the gas sails out
title('خُمس الغاز المُسال', { at: T('خُمْسُ#2'), until: T('قطر') - .1, color: 'yellow', anim: 'blur' });
region('QAT', { at: T('قطر'), style: 'vivid', color: '#b48cff', opacity: .55, label: 'قطر', labelPill: '#111a2b', until: T('السعودية') });
path(lane('RAS_LAFFAN_OUT'), { at: T('قطر'), dur: 1.4, color: '#4fd2ff', width: 8, sea: true, until: T('السعودية') });
sprite('lng_carrier', null, { at: T('قطر'), along: lane('RAS_LAFFAN_OUT'), travel: 6.5, scale: 1.4, until: T('السعودية') });   // sheet 5: a real LNG carrier (spherical tanks)

// ---------------------------------------------------------------- 48.9-58.3 s  the whole Gulf has one door
cam(T('السعودية'), { lon: 50.6, lat: 27.0, span: 17, ease: 'io' });
region('SAU', { at: T('السعودية'), flag: true, label: 'السعودية', labelPill: '#111a2b', labelAt: [45.5, 24.0], until: T('والخليج') });
region('KWT', { at: T('والكويت'), style: 'vivid', color: '#ffd23f', opacity: .55, label: 'الكويت', labelPill: '#111a2b', labelAt: [47.6, 29.4], until: T('والخليج') });
region('IRQ', { at: T('والعراق'), style: 'vivid', color: '#2fd6c8', opacity: .5, label: 'العراق', labelPill: '#111a2b', labelAt: [44.0, 32.3], until: T('والخليج') });
region('ARE', { at: T('والإمارات'), flag: true, label: 'الإمارات', labelPill: '#111a2b', labelAt: [54.3, 23.6], until: T('والخليج') });
cam(T('والخليج'), { lon: 52.5, lat: 27.0, span: 14, ease: 'sine' });
cam(T('هُرمز#2'), { lon: 56.45, lat: 26.55, span: 3.2, ease: 'io', dive: true });
title('بابٌ واحد', { at: T('باب'), until: T('والمشكلة') - .1, color: 'yellow', anim: 'words' });
path(GATE, { at: T('هُرمز#2') - .1, dur: .6, color: '#ff4757', width: 12, sea: true, until: T('الساحل') });
punch(T('هُرمز#2'), { amt: .14 }); flash(T('هُرمز#2'), .25);

// ---------------------------------------------------------------- 58.3-72.5 s  the threat: the map goes night, the north shore is Iran's
mapStyle('night', T('والمشكلة'));
title('والمشكلة؟', { at: T('والمشكلة'), until: T('الساحل') - .05, color: 'red' });
cam(T('الساحل'), { lon: 56.1, lat: 26.85, span: 2.3, ease: 'io' });
region('IRN', { at: T('الساحل'), style: 'danger', label: false, until: T('فستكون') });
label(spot('BANDAR_ABBAS'), 'بندر عباس', { at: T('بندر'), pill: '#111a2b', color: '#ff8a95', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, -100] });
label(spot('QESHM'), 'جزيرة قِشم', { at: T('قِشم'), pill: '#111a2b', color: '#ff8a95', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, 100] });
cam(T('لذلك'), { lon: 56.3, lat: 26.7, span: 3.0, ease: 'io' });
title('إغلاق المضيق!', { at: T('إغلاق'), until: T('وإذا') - .1, color: 'red' });
icon(spot('HORMUZ'), 'x', { at: T('إغلاق') - .2, size: 2.0, color: 'red', until: T('فستكون') });
shake(T('إغلاق'), { amt: 1.3 });
title('وإذا أُغلق؟', { at: T('وإذا'), until: T('فستكون') - .05, color: 'red' });
cam(T('وإذا'), { lon: 56.3, lat: 26.7, span: 2.7, ease: 'sine' });   // a held breath before the pull-out
cam(T('أُغلق.end'), { lon: 56.3, lat: 26.7, span: 2.6, ease: 'sine' });

// ---------------------------------------------------------------- 72.5-81.7 s  who pays: Asia
mapStyle('satellite', T('فستكون'));
cam(T('الصين'), { lon: 97, lat: 30, span: 92, ease: 'io' });
cam(T('آسيا.end'), { lon: 98, lat: 30, span: 86, ease: 'sine' });
region('CHN', { at: T('الصين'), flag: true, label: 'الصين', labelPill: '#111a2b', until: T('والبديل') });
region('IND', { at: T('والهند'), flag: true, label: 'الهند', labelPill: '#111a2b', until: T('والبديل') });
region('JPN', { at: T('واليابان'), flag: true, label: 'اليابان', labelPill: '#111a2b', until: T('والبديل') });
path(lane('HORMUZ_TO_EAST_ASIA'), { at: T('الصين'), dur: 1.8, color: '#4fd2ff', width: 7, sea: true, until: T('والبديل') });
path(lane('HORMUZ_TO_INDIA'), { at: T('والهند'), dur: 1.0, color: '#4fd2ff', width: 7, sea: true, until: T('والبديل') });
number(null, '#%', { at: T('أربعة#2'), until: T('والبديل') - .1, count: { to: 84, dur: 1.1 }, color: 'fire', sub: 'من نفط هُرمز يتّجه إلى آسيا' });
punch(T('وثمانين'), { amt: .12 });

// ---------------------------------------------------------------- 81.7-90.7 s  the way around: two pipelines, too small
cam(T('أنابيب'), { lon: 47.4, lat: 24.6, span: 20, ease: 'io' });
cam(T('صغيراً.end'), { lon: 47.6, lat: 24.7, span: 18.5, ease: 'sine' });
title('والبديل؟', { at: T('والبديل'), until: T('أنابيب') - .05, color: 'yellow' });
region('SAU', { at: T('سعودية'), style: 'soft', label: false, until: T('لذلك#2') });
region('ARE', { at: T('وإماراتية'), style: 'soft', label: false, until: T('لذلك#2') });
path(pipeline('EAST_WEST'), { at: T('سعودية'), dur: 1.4, color: '#ffd23f', width: 12, until: T('لذلك#2') });
path(pipeline('HABSHAN_FUJAIRAH'), { at: T('وإماراتية'), dur: 1.0, color: '#ffd23f', width: 12, until: T('لذلك#2') });
label(spot('YANBU'), 'ينبع', { at: T('ينبع'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'SAU' });
label(spot('FUJAIRAH'), 'الفجيرة', { at: T('والفجيرة'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'ARE' });
title('جزء صغير فقط', { at: T('جزءاً'), until: T('لذلك#2') - .05, level: 'sub', color: 'red' });

// ---------------------------------------------------------------- 90.7-96.2 s  the US Fifth Fleet inside the Gulf
cam(T('يرابط'), { lon: 50.56, lat: 26.08, span: 1.7, ease: 'io' });
cam(T('نفسه#2.end'), { lon: 50.56, lat: 26.08, span: 1.55, ease: 'sine' });
region('BHR', { at: T('البحرين'), style: 'ally', label: 'البحرين', labelPill: '#111a2b', labelAt: [50.5, 25.85], until: T('لأن#2') });
flagPin('USA', spot('NSA_BAHRAIN'), { at: T('الأمريكي'), in: 'BHR', say: 'الأمريكي', x: 760, y: 1000, until: T('لأن#2') });
label(spot('NSA_BAHRAIN'), 'الأسطول الخامس', { at: T('الخامس'), pill: '#111a2b', color: '#ffd23f', until: T('لأن#2'), anim: 'bubble', in: 'BHR', lead: [0, -120] });
punch(T('الخامس'), { amt: .1 });
// a carrier group coming in towards Manama from the north-east, the escort destroyer behind it (real size order: 333 m > 155 m)
const FLEET = [[51.2, 26.62], [51.0, 26.48], [50.82, 26.36]];
sprite('carrier', null, { at: T('يرابط'), early: 0, along: FLEET, speed: .09, phase: .12, until: T('لأن#2') });
sprite('destroyer', null, { at: T('يرابط'), early: 0, along: FLEET, speed: .09, phase: .0, sfx: false, until: T('لأن#2') });

// ---------------------------------------------------------------- 96.2-end  PAYOFF + LOOP: back to the opening frame, a tanker comes to the gate again
// it reaches at the last frame the place the first tanker has in the first frame (phase .30 of GATE), so the loop is seamless
cam(T('يمسك'), { lon: 56.45, lat: 26.6, span: 4.2, ease: 'io' });
cam(T('يمسك#2'), { ...NEAR, span: 3.3, ease: 'io' });
title('صمّام نفط العالم', { at: T('يمسك#2'), until: T('كله#5.end+.5'), color: 'yellow', anim: 'words', mag: 1.5 });
punch(T('صمّام'), { amt: .14 }); shake(T('كله#5'), { amt: 1.0 });
const LAST = T('كله#5.end+1.2');
sprite('tanker', null, { at: T('لأن#2'), early: 0, along: GATE, speed: .30 / (LAST - T('لأن#2')), phase: 0, scale: 1.1 });
path(GATE, { at: T('صمّام'), dur: .6, color: '#ff4757', width: 11, sea: true });

captions({ style: 'line', emph: ['إغلاق', 'خُمْسَ', 'صمّام'] });
end(LAST);
start();
