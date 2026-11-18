// constantinople-1453 — the fall of Constantinople (siege 6 April - 29 May 1453). Written by hand on the director's rules
// (orbit opening, dive, drone orbit / along by place kind, satellite for a whole route and the pull-out), 2026-11-18.
// Times are seconds from web/timing.json (Riko, AI Studio). Year 1453: only period craft (galleys, carracks), no modern traffic.
import { T, banner, cam, captions, dateBadge, end, label, lane, mapStyle, measure, number, path, photo, pipeline, punch, ruler, shake, spot, sprite, start, title, atlasName } from './map.js';

const OPEN = { lon: 28.975, lat: 41.03, span: 1.6 };
const G = (pts, o) => sprite('galley', null, { along: pts, early: 0, fit: false, ...o });

// ================================================================ camera
cam(0, { lon: 98.97, lat: 41.0, span: 170, cy: .46 });                                                   // opening: the Earth from orbit
cam(2.2, { ...OPEN, ease: 'io', dive: true });                                                          // dive onto the city and its seas
cam(6.2, { lon: 28.968, lat: 41.018, span: .42, ease: 'io', drone: true, heading: -24 });              // CONSTANTINOPLE (drone: orbit)
cam(11.3, { lon: 28.968, lat: 41.018, span: .38, ease: 'sine', drone: true, heading: 8 });             // hold
cam(13.2, { lon: 28.962, lat: 41.016, span: .3, ease: 'io', drone: true, heading: 20 });               // the city between three seas
cam(18.9, { lon: 28.936, lat: 41.017, span: .085, ease: 'io', drone: true, heading: 52 });              // the land walls (drone: orbit)
cam(23.6, { lon: 28.936, lat: 41.017, span: .08, ease: 'sine', drone: true, heading: 84 });            // hold
cam(25.4, { lon: 29.052, lat: 41.09, span: .2, ease: 'io', drone: true, heading: -20 });               // RUMELI_HISARI on the Bosporus (drone: along the strait)
cam(35.4, { lon: 29.058, lat: 41.1, span: .19, ease: 'sine', drone: true, heading: -20 });             // hold
cam(38.6, { lon: 27.74, lat: 41.36, span: 2.15, ease: 'io' });                                          // the whole road Edirne -> the walls (satellite: a route)
cam(44.5, { lon: 27.74, lat: 41.36, span: 2.05, ease: 'sine' });                                        // hold
cam(45.6, { lon: 28.92, lat: 41.02, span: .065, ease: 'io', dive: true, drone: true, heading: 76 });    // dive onto the walls: the first shot
cam(48.7, { lon: 28.92, lat: 41.02, span: .062, ease: 'sine', drone: true, heading: 92 });             // hold
cam(50.2, { lon: 28.913, lat: 41.02, span: .09, ease: 'io', drone: true, heading: 92 });               // the siege lines (drone: orbit)
cam(57.4, { lon: 28.913, lat: 41.02, span: .085, ease: 'sine', drone: true, heading: 64 });             // hold
cam(58.6, { lon: 28.921, lat: 41.019, span: .06, ease: 'io', drone: true, heading: 96 });              // the bombardment
cam(64.6, { lon: 28.921, lat: 41.019, span: .058, ease: 'sine', drone: true, heading: 124 });          // hold
cam(66.2, { lon: 28.983, lat: 41.022, span: .075, ease: 'io', drone: true, heading: -60 });            // GOLDEN_HORN mouth and the chain
cam(71.9, { lon: 28.983, lat: 41.022, span: .07, ease: 'sine', drone: true, heading: -30 });           // hold
cam(73.6, { lon: 28.984, lat: 41.034, span: .085, ease: 'io', drone: true, heading: -84 });            // Galata between the Bosporus and the Horn
cam(78.8, { lon: 28.984, lat: 41.035, span: .08, ease: 'sine', drone: true, heading: -96 });           // hold
cam(81.0, { lon: 28.986, lat: 41.036, span: .075, ease: 'io', drone: true, heading: -90 });            // the overland road (drone: along it, westward)
cam(92.3, { lon: 28.972, lat: 41.036, span: .075, ease: 'sine', drone: true, heading: -90 });          // hold
cam(93.8, { lon: 28.958, lat: 41.033, span: .1, ease: 'io', drone: true, heading: -130 });             // the fleet inside the Horn (orbit)
cam(98.2, { lon: 28.96, lat: 41.031, span: .1, ease: 'sine', drone: true, heading: -160 });            // hold
cam(99.8, { lon: 28.915, lat: 41.02, span: .075, ease: 'io', drone: true, heading: 90 });               // the final assault
cam(105.6, { lon: 28.918, lat: 41.02, span: .07, ease: 'sine', drone: true, heading: 100 });            // hold
cam(107.6, { lon: 28.935, lat: 41.017, span: .09, ease: 'io', drone: true, heading: 106 });            // into the city
cam(109.9, { lon: 28.966, lat: 41.014, span: .3, ease: 'io', drone: true, heading: -20 });             // over the fallen capital (orbit)
cam(116.3, { lon: 28.966, lat: 41.014, span: .28, ease: 'sine', drone: true, heading: 12 });           // hold
cam(118.4, { lon: 28.968, lat: 41.02, span: .5, ease: 'io' });                                          // Istanbul
cam(121.9, { lon: 29.0, lat: 38.5, span: 50, ease: 'io' });                                             // pull out: three continents (satellite)
cam(124.6, { lon: 29.0, lat: 38.5, span: 47, ease: 'sine' });                                           // hold
cam(125.8, { ...OPEN, ease: 'io', dive: true });                                                        // loop: back to the opening frame

// ================================================================ 1  0-11.6  «ربيعُ عامِ 1453: سلطانٌ عثمانيٌّ في الـ21 … القسطنطينية!»
number(null, '#', { at: .96, until: 2.7, count: { to: 1453, dur: 1.2 }, color: 'yellow' });
photo('mehmed_ii', { at: 2.84, until: 7.2, frame: 'hex', size: 210, y: 1260 });
number(null, '# عاماً', { at: 5.5, until: 7.3, count: { to: 21, dur: .6 }, color: 'yellow', say: 'والعشرينَ' });
title('القسطنطينية!', { at: 10.07, until: 11.5, color: 'yellow', anim: 'words', mag: 1.5 });
label(spot('CONSTANTINOPLE'), 'القسطنطينية', { at: 9.6, until: 23.9, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
banner('byzantine', spot('HAGIA_SOPHIA'), { at: 9.0, until: 23.9, size: 1.1 });
G([[28.70, 40.93], [28.82, 40.95], [28.92, 40.97], [28.975, 40.99], [28.995, 41.008]], { at: 2.4, travel: 22, scale: .8, until: 23.9 });   // an Ottoman galley rows in from the Sea of Marmara
sprite('carrack', null, { at: 2.4, early: 0, fit: false, along: [[29.03, 41.06], [29.012, 41.042], [29.0, 41.028], [28.99, 41.02]], travel: 12, scale: .8, until: 23.9 });   // a merchant carrack sails down the Bosporus
punch(10.07, { amt: .12 });

// ================================================================ 2  11.6-23.9  «عاصمةُ الروم منذ 1100 عام … وسورٌ مضاعف 5.5 كم»
number(null, 'أكثر من #', { at: 14.03, until: 16.2, count: { to: 1100, dur: 1.0 }, color: 'yellow', sub: 'عام' });
label([28.955, 40.995], 'بحر مرمرة', { at: 16.4, until: 18.6, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
label(spot('GOLDEN_HORN'), 'القرن الذهبي', { at: 16.7, until: 18.6, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
label([29.008, 41.03], 'البوسفور', { at: 17.0, until: 18.6, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
path(pipeline('THEODOSIAN_LINE'), { at: 17.5, dur: 1.6, color: '#ffd23f', width: 11, until: 23.9 });
label([28.9228, 40.9934], 'يدي قله', { at: 18.6, until: 23.9, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
label([28.946, 41.042], 'أيوان سراي', { at: 18.8, until: 23.9, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
measure([28.9228, 40.9934], [28.946, 41.042], { at: 20.0, until: 23.9, to: 5.5, unit: 'كم', say: 'خمسةِ' });
const def0 = sprite('defenders', null, { at: 18.9, early: 0, along: [[28.928, 41.002], [28.927, 41.01], [28.9255, 41.018]], travel: 6, until: 23.9 });
banner('byzantine', def0, { at: 19.3, until: 23.9 });

// ================================================================ 3  23.9-35.7  «قبل الحصار بعام، روملي حصار على البوسفور، أقل من 5 أشهر …»
dateBadge([['1452', 24.0]], { until: 35.7 });
label(spot('RUMELI_HISARI'), 'قلعة روملي حصار', { at: 27.42, until: 35.7, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
sprite('castle', spot('RUMELI_HISARI'), { at: 27.42, size: 120, until: 35.7, in: 'TUR' });
photo('rumeli_hisari', { at: 28.2, until: 31.6, frame: 'card', size: 190, y: 1300 });
number(null, 'أقل من # أشهر', { at: 31.17, until: 33.0, count: { to: 5, dur: .5 }, color: 'yellow' });
banner('ottoman', spot('RUMELI_HISARI'), { at: 28.0, until: 35.7, dy: 110 });
const relief = sprite('carrack', null, { at: 25.6, early: 0, fit: false, along: [[29.078, 41.165], [29.064, 41.14], [29.07, 41.118], [29.064, 41.098]], travel: 10, until: 35.7 });   // a relief ship from the Black Sea
sprite('stone_ball', spot('RUMELI_HISARI'), { to: relief, arrive: 34.1, travel: .5, hit: { until: 37.30, plume: .9, size: 1.1 } });   // the castle guns stop it (Nov 1452 a Venetian ship was sunk here)
ruler([29.0563, 41.0847], [29.0668, 41.0836], { at: 32.3, until: 35.7, color: 'red' });   // the strait is closed

// ================================================================ 4  35.8-48.8  «مدفع عملاق 8 أمتار، جرّه 60 ثوراً من أدرنة، كرة 270 كغ»
path(pipeline('EDIRNE_ROAD'), { at: 38.9, dur: 1.4, color: '#ffd23f', width: 8, dash: [18, 14], until: 45.0 });
label(spot('EDIRNE'), 'أدرنة', { at: 38.9, until: 45.0, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
label([28.905, 41.02], 'القسطنطينية', { at: 39.1, until: 45.0, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
const gun = sprite('bombard', null, { at: 38.9, early: 0, along: pipeline('EDIRNE_ROAD'), travel: 6.0, until: 45.0 });
banner('ottoman', gun, { at: 39.2, until: 45.0 });
number(null, '# أمتار', { at: 40.93, until: 42.0, count: { to: 8, dur: .5 }, color: 'yellow' });
number(null, '# ثوراً', { at: 42.81, until: 44.6, count: { to: 60, dur: .6 }, color: 'yellow' });
sprite('bombard', [28.904, 41.0205], { at: 45.4, view: 'top', heading: 270, until: 64.8, in: 'TUR' });
sprite('stone_ball', [28.9055, 41.0205], { to: [28.9208, 41.0197], arrive: 46.5, travel: .5, hit: { until: 49.70, size: 1.2 } });
number(null, '# كغ', { at: 46.92, until: 48.8, count: { to: 270, dur: .8 }, color: 'fire' });
sprite('stone_ball', [28.9055, 41.0205], { to: [28.9215, 41.016], arrive: 48.5, travel: .5, hit: { until: 51.70, size: 1.0 } });

// ================================================================ 5  48.8-57.6  «6 أبريل … 80 ألف جندي عثماني، أمام 7 آلاف مدافع»
dateBadge([['6 أبريل', 49.6]], { until: 57.6 });
path(pipeline('THEODOSIAN_LINE'), { at: 49.0, dur: 1.2, color: '#ffd23f', width: 9, until: 64.8 });
label([28.9228, 40.9934], 'يدي قله', { at: 49.3, until: 64.8, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
label([28.946, 41.042], 'أيوان سراي', { at: 49.4, until: 64.8, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
const j1 = sprite('janissaries', null, { at: 49.6, early: 0, along: [[28.872, 41.02], [28.907, 41.0205]], travel: 6.5, until: 64.8 });
const j2 = sprite('janissaries', null, { at: 50.0, early: 0, along: [[28.874, 41.008], [28.909, 41.009]], travel: 6.5, until: 64.8 });
sprite('janissaries', null, { at: 50.4, early: 0, along: [[28.876, 41.031], [28.91, 41.031]], travel: 6.5, until: 64.8 });
sprite('cavalry', null, { at: 50.8, early: 0, along: [[28.865, 41.0], [28.905, 40.999]], travel: 3.4, until: 64.8 });
banner('ottoman', j1, { at: 50.0, until: 64.8 });
banner('ottoman', j2, { at: 50.4, until: 64.8, phase: 1.3 });
const d1 = sprite('defenders', null, { at: 54.3, early: 0, along: [[28.927, 41.012], [28.9265, 41.0175]], travel: 4, until: 64.8 });
sprite('defenders', null, { at: 54.6, early: 0, along: [[28.931, 41.025], [28.933, 41.0285]], travel: 4, until: 64.8 });
banner('byzantine', d1, { at: 54.6, until: 64.8 });
number(null, '#', { at: 52.4, until: 54.3, count: { to: 80000, dur: 1.0 }, color: 'fire', sub: 'جندي عثماني', say: 'ثمانينَ' });
number(null, '#', { at: 54.96, until: 57.6, count: { to: 7000, dur: .8 }, color: 'yellow', sub: 'مدافع', say: 'سبعةِ' });

// ================================================================ 6  57.6-64.8  «ودوّت المدافع أسابيع … يرممون في الليل ما يهدمه النهار»
for (const [k, tt] of [[0, 58.4], [1, 59.5], [2, 60.4], [3, 62.0]]) {
  const tgt = [[28.9209, 41.0193], [28.9214, 41.0165], [28.9235, 41.0235], [28.9211, 41.0205]][k];
  sprite('stone_ball', [28.9055, 41.0205 - k * .0012], { to: tgt, arrive: tt, travel: .5, hit: { until: tt + 2.6, size: .9, shake: k === 0 ? 1.3 : .8 } });
}
sprite('defenders', null, { at: 61.3, early: 0, along: [[28.929, 41.0165], [28.924, 41.0195]], travel: 3.0, until: 64.8 });
mapStyle('night', 62.84);
mapStyle('satellite', 64.0);

// ================================================================ 7  64.8-72.1  «سلسلة حديدية ضخمة على مدخل القرن الذهبي … عجز الأسطول»
ruler([28.9775, 41.0168], [28.9762, 41.0222], { at: 66.66, until: 72.1, color: '#d9dde3' });
label([28.9772, 41.0195], 'السلسلة', { at: 67.0, until: 72.1, pill: '#111a2b', color: '#fff', anim: 'bubble' });
label(spot('GOLDEN_HORN'), 'القرن الذهبي', { at: 68.85, until: 72.1, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
G([[29.006, 41.031], [28.996, 41.026], [28.9855, 41.0228]], { at: 66.4, travel: 4.4, scale: .75, until: 72.1 });
G([[29.012, 41.0155], [29.0, 41.0115], [28.9905, 41.0118]], { at: 66.9, travel: 4.4, scale: .75, until: 72.1 });
const bc = sprite('carrack', null, { at: 66.2, early: 0, fit: false, along: [[28.966, 41.0265], [28.971, 41.0238]], travel: 5.5, scale: .75, until: 72.1 });
banner('byzantine', bc, { at: 66.6, until: 72.1 });

// ================================================================ 8  72.2-78.9  «فجاءت الفكرة … فلتسر السفن على البر!»
const g8a = G([[28.998, 41.024], [29.002, 41.033], [29.0045, 41.0398]], { at: 72.4, travel: 3.2, scale: .7, until: 92.5 });
G([[29.008, 41.022], [29.011, 41.03], [29.0135, 41.0355]], { at: 73.6, travel: 3.2, scale: .7, until: 83.6 });
banner('ottoman', g8a, { at: 72.8, until: 92.5 });
path(pipeline('OVERLAND_ROAD'), { at: 76.93, dur: 1.6, color: '#ffd23f', width: 8, dash: [16, 12], until: 98.4 });
label([29.006, 41.042], 'البوسفور', { at: 76.9, until: 98.4, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
label([28.9605, 41.0345], 'القرن الذهبي', { at: 77.1, until: 98.4, pill: '#0f2b46', color: '#7fe3ff', anim: 'bubble' });
title('السفن على البرّ!', { at: 77.0, until: 78.9, color: 'yellow', anim: 'words', mag: 1.5 });

// ================================================================ 9  79-92.5  «ليلة 22 أبريل، نحو 70 سفينة فوق ألواح مدهونة … تلال غلطة … القرن الذهبي»
mapStyle('night', 79.0);
dateBadge([['22 أبريل', 79.8]], { until: 92.5 });
label(spot('GALATA'), 'غلطة', { at: 89.0, until: 92.5, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
number(null, 'نحو # سفينة', { at: 83.3, until: 86.0, count: { to: 70, dur: .7 }, color: 'yellow' });
const HAUL = [79.6, 82.4, 85.0], HT = 12;
HAUL.forEach((t0, i) => {
  const h = sprite('galley_hauled', null, { at: t0, early: 0, along: pipeline('OVERLAND_ROAD'), travel: HT, until: t0 + HT + .05 });
  if (i === 0) banner('ottoman', h, { at: t0 + .3, until: t0 + HT });
});
G([[29.006, 41.019], [29.01, 41.028], [29.0135, 41.0355]], { at: 84.0, travel: 1.8, scale: .7, until: 92.5 });   // more galleys keep coming to the road

// ================================================================ 10  92.6-98.4  «أفاق المدافعون صباحاً … الأسطول داخل مينائهم، خلف السلسلة!»
mapStyle('satellite', 92.59);
const IN = lane('GOLDEN_HORN_IN');
const g1 = G(IN, { at: HAUL[0] + HT, travel: 3.5, scale: .72, until: 98.6 });
G([...IN.slice(0, 3), [28.9435, 41.042]], { at: HAUL[1] + HT, travel: 2.5, scale: .72, until: 98.6 });
G(IN.slice(0, 3), { at: HAUL[2] + HT, travel: 1.2, scale: .72, until: 98.6 });
banner('ottoman', g1, { at: 93.0, until: 98.4 });
sprite('defenders', null, { at: 97.05, early: 0, along: [[28.956, 41.0258], [28.962, 41.0246]], travel: 1.3, until: 98.4 });
ruler([28.9775, 41.0168], [28.9762, 41.0222], { at: 96.9, until: 98.4, color: 'red' });
title('خلف السلسلة!', { at: 97.32, until: 98.4, color: 'red', anim: 'glitch' });

// ================================================================ 11  98.4-108.4  «بعد 53 يوماً، فجر 29 مايو، الهجوم الأخير … ودخل العثمانيون المدينة»
number(null, '# يوماً', { at: 99.45, until: 101.0, count: { to: 53, dur: .6 }, color: 'yellow' });
dateBadge([['29 مايو', 101.03]], { until: 108.4 });
path(pipeline('THEODOSIAN_LINE'), { at: 99.6, dur: 1.0, color: '#ffd23f', width: 9, until: 108.4 });
label([28.9228, 40.9934], 'يدي قله', { at: 99.8, until: 108.4, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
label([28.946, 41.042], 'أيوان سراي', { at: 99.9, until: 108.4, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
label(spot('ST_ROMANUS_GATE'), 'بوابة القديس رومانوس', { at: 101.8, until: 103.4, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
sprite('stone_ball', [28.9055, 41.0205], { to: [28.9208, 41.0197], arrive: 103.5, travel: .4, hit: { until: 106.70, size: 1.3, shake: 1.4 } });
sprite('stone_ball', [28.9055, 41.0195], { to: [28.9212, 41.0185], arrive: 104.6, travel: .4, hit: { until: 107.80, size: 1.0, shake: .8 } });
const ATT = [[28.893, 41.0197], [28.912, 41.0197], [28.9208, 41.0197], [28.93, 41.0188], [28.942, 41.0172]];
const a1 = sprite('janissaries', null, { at: 103.4, early: 0, along: ATT, travel: 6.0, until: 108.4, sfx: 'battle' });
sprite('janissaries', null, { at: 103.9, early: 0, along: ATT.map(p => [p[0] - .004, p[1] - .0028]), travel: 6.0, until: 108.4 });
sprite('janissaries', null, { at: 104.4, early: 0, along: ATT.map(p => [p[0] - .008, p[1] + .0026]), travel: 6.0, until: 108.4 });
sprite('cavalry', null, { at: 104.6, early: 0, along: [[28.885, 41.028], [28.912, 41.0285], [28.933, 41.0275]], travel: 3.2, until: 108.4 });
banner('ottoman', a1, { at: 103.8, until: 108.4, size: 1.15 });
const lastDef = sprite('defenders', null, { at: 100.2, early: 0, along: [[28.9255, 41.0195], [28.94, 41.0182]], travel: 7.0, until: 106.0 });
banner('byzantine', lastDef, { at: 100.5, until: 105.5 });
path([[28.893, 41.0197], [28.9208, 41.0197], [28.945, 41.017]], { at: 105.0, dur: 1.2, color: 'red', width: 12, arrow: true, until: 108.4 });
label([28.945, 41.017], 'المدينة', { at: 105.6, until: 108.4, pill: '#111a2b', color: '#ff8a95', anim: 'bubble', in: 'TUR' });
label([28.893, 41.0197], 'المعسكر العثماني', { at: 105.0, until: 108.4, pill: '#111a2b', color: '#fff', anim: 'bubble', in: 'TUR' });
shake(103.53, { amt: 1.3 });

// ================================================================ 12  108.4-116.6  «سقطت الإمبراطورية البيزنطية بعد أكثر من 1000 عام … محمد الفاتح»
banner('ottoman', spot('HAGIA_SOPHIA'), { at: 109.0, until: 118.4, size: 1.4 });
label(spot('HAGIA_SOPHIA'), 'آيا صوفيا', { at: 110.0, until: 116.6, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
number(null, 'أكثر من #', { at: 112.33, until: 114.0, count: { to: 1000, dur: .7 }, color: 'yellow', sub: 'عام' });
photo('mehmed_ii', { at: 113.95, until: 116.5, frame: 'hex', size: 210, y: 1260 });
title('محمد الفاتح', { at: 114.97, until: 116.6, color: 'yellow', anim: 'words', mag: 1.5 });
const ga = G([[28.86, 40.955], [28.93, 40.975], [28.975, 40.995]], { at: 109.2, travel: 6.5, scale: .8, until: 118.4 });
banner('ottoman', ga, { at: 109.6, until: 118.4 });
sprite('janissaries', null, { at: 109.2, early: 0, along: [[28.935, 41.0175], [28.955, 41.013], [28.972, 41.0095]], travel: 6.0, until: 116.6 });

// ================================================================ 13  116.6-124.6  «وصارت القسطنطينية إسطنبول، عاصمة دولة … ثلاث قارات قروناً طويلة!»
title('إسطنبول', { at: 118.61, until: 120.4, color: 'yellow', anim: 'words', mag: 1.5 });
label(spot('CONSTANTINOPLE'), 'إسطنبول', { at: 118.7, until: 120.4, pill: '#111a2b', color: '#ffd23f', anim: 'bubble', in: 'TUR' });
atlasName([16, 49], 'أوروبا', { at: 122.0, until: 124.7, size: 88 });
atlasName([46, 40], 'آسيا', { at: 122.3, until: 124.7, size: 88 });
atlasName([22, 24], 'إفريقيا', { at: 122.6, until: 124.7, size: 88 });
number(null, '# قارات', { at: 122.07, until: 124.6, count: { to: 3, dur: .5 }, color: 'yellow' });

captions({ style: 'line', emph: ['القسطنطينية!', 'ألفٍ#2', 'رُومَلي', 'مدفعٌ', 'ثمانينَ', 'سلسلةٌ', 'البرّ!', 'سبعينَ', 'السلسلة!', 'الأسوار…', 'الفاتح.', 'إسطنبول،'] });
end(126.0);
start();
