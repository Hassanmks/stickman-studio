// Written by engine/scripts/director.mjs (TaleSpark 2.0 automatic director, 2026-11-17T22:42)
// from script.txt + web/timing.json + shotplan.json (Gemini). Every item has an id (// dN); the logic gate's rejects go to director.drop.json.
import { T, cam, captions, end, flagPin, icon, label, lane, mapStyle, measure, number, path, pipeline, punch, region, shake, spot, sprite, start, title } from './map.js';

// ---- camera: one shot per line, arriving early in it, then a hold key at its end (cam(t) = the camera ARRIVES at t)
cam(0, { lon: 126.5, lat: 10.64, span: 170, cy: .46 });   // opening: the Earth from orbit
cam(2.2, { lon: 56.5, lat: 26.6, span: 4.8, ease: 'io', dive: true });   // the dive from orbit onto the first place
cam(T('كيلومتراً.end'), { lon: 55.997, lat: 27.018, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('مضيق+0.25'), { lon: 53.667, lat: 28.207, span: 18.38, ease: 'io' });   // HORMUZ + IRN+OMN
cam(T('الجنوب.end'), { lon: 53.667, lat: 28.207, span: 17.09, ease: 'sine' });   // hold
cam(T('بين+0.51'), { lon: 56.435, lat: 26.68, span: 0.96, ease: 'io', dive: true, drone: true, heading: 0 });   // LARAK (drone: orbit)
cam(T('كيلومتراً#2.end'), { lon: 56.435, lat: 26.68, span: 0.89, ease: 'sine', drone: true, heading: -32 });   // hold
cam(T('الناقلات+0.35'), { lon: 56.45, lat: 26.045, span: 2.48, ease: 'io' });   // the route from HORMUZ
cam(T('للخروج.end'), { lon: 56.45, lat: 26.045, span: 2.31, ease: 'sine' });   // hold
cam(T('كل#2+0.78'), { lon: 56.951, lat: 25.627, span: 3.84, ease: 'io', drone: true, heading: -47.1 });   // HORMUZ (drone: along)
cam(T('كله#3.end'), { lon: 55.949, lat: 26.463, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('وحده+0.02'), { lon: 54.676, lat: 25.6, span: 10.32, ease: 'io' });   // the route from RAS_LAFFAN
cam(T('نفسه.end'), { lon: 54.676, lat: 25.6, span: 9.6, ease: 'sine' });   // hold
cam(T('السعودية+0.35'), { lon: 45.536, lat: 26.873, span: 21.12, ease: 'io' });   // HORMUZ + SAU+KWT+IRQ+ARE
cam(T('هُرمز#2.end'), { lon: 45.536, lat: 26.873, span: 19.64, ease: 'sine' });   // hold
cam(T('الساحل+0.5'), { lon: 56.06, lat: 26.983, span: 1.6, ease: 'io', dive: true, drone: true, heading: 0 });   // BANDAR_ABBAS (drone: orbit)
cam(T('قِشم.end'), { lon: 56.06, lat: 26.983, span: 1.49, ease: 'sine', drone: true, heading: 32 });   // hold
cam(T('تعرّضت+0.38'), { lon: 53.667, lat: 32.415, span: 22, ease: 'io' });   // HORMUZ
cam(T('المضيق#2.end'), { lon: 53.667, lat: 32.415, span: 20.46, ease: 'sine' });   // hold
cam(T('فستكون+0.4'), { lon: 111.065, lat: 30.158, span: 40, ease: 'io' });   // list CHN+IND+JPN
cam(T('آسيا.end'), { lon: 111.065, lat: 30.158, span: 37.2, ease: 'sine' });   // hold
cam(T('أنابيب+0.48'), { lon: 47.195, lat: 24.62, span: 22.42, ease: 'io' });   // YANBU
cam(T('صغيراً.end'), { lon: 47.195, lat: 24.62, span: 20.85, ease: 'sine' });   // hold
cam(T('الأسطول+0.16'), { lon: 50.61, lat: 26.21, span: 0.96, ease: 'io', dive: true, drone: true, heading: 0 });   // NSA_BAHRAIN (drone: orbit)
cam(T('نفسه#2.end'), { lon: 50.61, lat: 26.21, span: 0.89, ease: 'sine', drone: true, heading: -32 });   // hold
cam(T('لأن#2+0.86'), { lon: 53.555, lat: 26.405, span: 7.4, ease: 'io' });   // hop: up over the map (587 km)
cam(T('هُرمز#3+0.12'), { lon: 57.003, lat: 26.182, span: 3.84, ease: 'io', dive: true, drone: true, heading: -47.1 });   // HORMUZ (drone: along)
cam(T('كله#5.end'), { lon: 55.997, lat: 27.018, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('كله#5.end+1'), { lon: 56.5, lat: 26.6, span: 4.8, ease: 'io', dive: true });   // loop: back to the opening frame

// ---------------------------------------------------------------- 0-7.35 s  «هل تعلم أن خُمْسَ نفطِ العالم كله، يعبر ممراً لا يتجاوز عرضه تسعة وثلاثين كيلومت»
title('خُمس نفط العالم!', { at: 0, dur: .01, until: T('تسعة-0.25'), color: 'yellow', anim: 'wipe' });   // d1
path(lane('HORMUZ_GATE'), { at: T('هل+0.2'), dur: 1.2, color: '#4fd2ff', width: 8, sea: true, until: T('هذا') });   // d2
sprite('tanker', null, { at: T('هل'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.035, phase: 0.05, until: T('هذا') });   // d3
sprite('tanker', null, { at: T('هل'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.035, phase: 0.3, sfx: false, until: T('هذا') });   // d4
sprite('tanker', null, { at: T('هل'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.035, phase: 0.55, sfx: false, until: T('هذا') });   // d5
sprite('tanker', null, { at: T('هل'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.035, phase: 0.8, sfx: false, until: T('هذا') });   // d6
number(null, '# كم', { at: T('تسعة'), until: T('هذا'), count: { to: 39, dur: 0.95 }, color: 'yellow' });   // d7
punch(T('كيلومتراً'), { amt: .1 });   // d8

// ---------------------------------------------------------------- 7.35-12.73 s  «هذا هو مضيق هُرمز: إيران من الشمال، وسلطنة عُمان من الجنوب.»
region('IRN', { at: T('إيران'), flag: true, label: 'إيران', labelPill: '#111a2b', say: 'إيران', until: T('هنا') });   // d9
region('OMN', { at: T('وسلطنة'), flag: true, label: 'سلطنة عُمان', labelPill: '#111a2b', say: 'وسلطنة', until: T('هنا') });   // d10
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('مضيق'), pill: '#111a2b', color: '#ffd23f', until: T('هنا'), anim: 'bubble', in: 'sea' });   // d11

// ---------------------------------------------------------------- 12.73-21.9 s  «هنا بالضبط، بين جزيرة لارَكْ الإيرانية، وجزر السلامة العُمانية، لا يتجاوز عرض ال»
label(spot('LARAK'), 'جزيرة لارك', { at: T('جزيرة'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', in: 'IRN', lead: [0, -110] });   // d12
label(spot('QUOIN'), 'جزر السلامة', { at: T('وجزر'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', lead: [0, 110] });   // d13
sprite('tanker', null, { at: T('هنا'), early: 0, along: [[56.142,26.588],[56.28,26.638],[56.43,26.628],[56.56,26.591],[56.61,26.445],[56.66,26.3],[56.746,26.175],[56.831,26.05],[56.922,25.928],[57.03,25.82],[57.052,25.798]], travel: 17.63, scale: .85, sfx: false, until: T('لكنّ') });   // traffic on HORMUZ_GATE   // d14
sprite('tanker', null, { at: T('هنا+1.6'), early: 0, along: [[56.142,26.588],[56.28,26.638],[56.43,26.628],[56.56,26.591],[56.61,26.445],[56.66,26.3],[56.746,26.175],[56.831,26.05],[56.922,25.928],[57.03,25.82],[57.052,25.798]], travel: 17.63, scale: .85, sfx: false, until: T('لكنّ') });   // the next ship on HORMUZ_GATE   // d15
measure(spot('LARAK_S'), spot('QUOIN'), { at: T('تسعة#2'), dur: 1.0, to: 39, unit: 'كم', color: '#ffd23f', until: T('لكنّ') });   // d16
punch(T('كيلومتراً#2'), { amt: .1 });   // d17

// ---------------------------------------------------------------- 21.9-33.33 s  «لكنّ الناقلات العملاقة، لا تستخدم هذا العرض كله! هناك ممرّان فقط، عرض كلٍّ منهما»
path(lane('HORMUZ_GATE'), { at: T('لكنّ+0.2'), dur: 1.2, color: '#4fd2ff', width: 8, sea: true, until: T('كل#2') });   // d18
sprite('tanker', null, { at: T('لكنّ'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.024, phase: 0.05, until: T('كل#2') });   // d19
sprite('tanker', null, { at: T('لكنّ'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.024, phase: 0.3, sfx: false, until: T('كل#2') });   // d20
sprite('tanker', null, { at: T('لكنّ'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.024, phase: 0.55, sfx: false, until: T('كل#2') });   // d21
sprite('tanker', null, { at: T('لكنّ'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.024, phase: 0.8, sfx: false, until: T('كل#2') });   // d22
number(null, '# كم', { at: T('أربعة'), until: T('كل#2'), count: { to: 4, dur: 0.95 }, color: 'yellow' });   // d23
punch(T('كيلومترات'), { amt: .1 });   // d24

// ---------------------------------------------------------------- 33.33-41.5 s  «كل يوم، يعبر من هنا نحو عشرين مليون برميل من النفط؛ أي قرابة عشرين في المئة من ا»
path(lane('HORMUZ_GATE'), { at: T('كل#2+0.2'), dur: 1.2, color: '#4fd2ff', width: 8, sea: true, until: T('وليس') });   // d25
sprite('tanker', null, { at: T('كل#2'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.032, phase: 0.05, until: T('وليس') });   // d26
sprite('tanker', null, { at: T('كل#2'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.032, phase: 0.3, sfx: false, until: T('وليس') });   // d27
sprite('tanker', null, { at: T('كل#2'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.032, phase: 0.55, sfx: false, until: T('وليس') });   // d28
sprite('tanker', null, { at: T('كل#2'), early: 0, along: lane('HORMUZ_GATE'), speed: 0.032, phase: 0.8, sfx: false, until: T('وليس') });   // d29
number(null, '# مليون', { at: T('عشرين'), until: T('عشرين#2-0.15'), count: { to: 20, dur: 0.95 }, color: 'yellow', sub: 'برميل من النفط' });   // d30
punch(T('مليون'), { amt: .1 });   // d31
number(null, '#%', { at: T('عشرين#2'), until: T('وليس'), count: { to: 20, dur: 0.95 }, color: 'yellow', sub: 'من استهلاك العالم كله' });   // d32
punch(T('المئة'), { amt: .1 });   // d33

// ---------------------------------------------------------------- 41.5-48.88 s  «وليس النفط وحده: خُمْسُ الغازِ المُسال في العالم، يخرج من قطر، ويعبر الممرّ الضي»
title('خُمْسُ الغازِ المُسال', { at: T('وليس'), until: T('قطر-0.1'), color: 'yellow', anim: 'words' });   // d34
region('QAT', { at: T('قطر'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'قطر', labelPill: '#111a2b', say: 'قطر', until: T('السعودية') });   // d35
path(lane('RAS_LAFFAN_OUT'), { at: T('وليس+0.2'), dur: 1.2, color: '#4fd2ff', width: 8, sea: true, until: T('السعودية') });   // d36
sprite('lng_carrier', null, { at: T('وليس'), early: 0, along: lane('RAS_LAFFAN_OUT'), travel: 13.73, until: T('السعودية') });   // d37

// ---------------------------------------------------------------- 48.88-58.34 s  «السعودية، والكويت، والعراق، والإمارات… كلها تصدّر من الخليج. والخليج ليس له إلا »
title('باب واحد!', { at: T('السعودية'), until: T('هُرمز#2-0.1'), color: 'yellow', anim: 'words' });   // d38
region('SAU', { at: T('السعودية'), flag: true, label: 'السعودية', labelPill: '#111a2b', say: 'السعودية', until: T('والمشكلة') });   // d39
region('KWT', { at: T('والكويت'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'الكويت', labelPill: '#111a2b', say: 'والكويت', until: T('والمشكلة') });   // d40
region('IRQ', { at: T('والعراق'), style: 'vivid', color: '#2fd6c8', opacity: .5, label: 'العراق', labelPill: '#111a2b', say: 'والعراق', until: T('والمشكلة') });   // d41
region('ARE', { at: T('والإمارات'), flag: true, label: 'الإمارات', labelPill: '#111a2b', say: 'والإمارات', until: T('والمشكلة') });   // d42
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('هُرمز#2'), pill: '#111a2b', color: '#ffd23f', until: T('والمشكلة'), anim: 'bubble', in: 'sea' });   // d43

// ---------------------------------------------------------------- 58.34-64.54 s  «والمشكلة؟ الساحل الشمالي كله إيراني، من بندر عباس، حتى جزيرة قِشم.»
label(spot('BANDAR_ABBAS'), 'بندر عباس', { at: T('بندر'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, -110] });   // d44
label(spot('QESHM'), 'جزيرة قشم', { at: T('جزيرة#2'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, 110] });   // d45
sprite('tanker', null, { at: T('والمشكلة'), early: 0, along: [[56.613,26.437],[56.55,26.62],[56.35,26.633],[56.163,26.598],[55.988,26.513],[55.811,26.432],[55.633,26.357],[55.456,26.273],[55.281,26.162],[55.1,26.087],[55.05,26.094]], travel: 12.58, scale: .85, sfx: false, until: T('لذلك') });   // traffic on HORMUZ_TO_RAS_TANURA   // d46
sprite('container_ship', null, { at: T('والمشكلة'), early: 0, along: [[55.9,26.47],[56.075,26.555],[56.25,26.64],[56.45,26.627],[56.581,26.529],[56.644,26.346],[56.75,26.169],[56.87,25.994],[57.007,25.843],[57.079,25.771]], travel: 12.58, scale: .85, sfx: false, until: T('لذلك') });   // traffic on HORMUZ_GATE   // d47

// ---------------------------------------------------------------- 64.54-71.28 s  «لذلك، كلما تعرّضت إيران للتهديد، لوّحت بورقة واحدة: إغلاق المضيق!»
mapStyle('night', T('لذلك'));   // d48
title('إغلاق المضيق!', { at: T('لذلك'), until: T('إيران#2-0.1'), color: 'red' });   // d49
region('IRN', { at: T('إيران#2'), style: 'danger', label: 'إيران', labelPill: '#111a2b', say: 'إيران', until: T('وإذا') });   // d50
icon(spot('HORMUZ'), 'x', { at: T('للتهديد-0.2'), size: 2.0, color: 'red', until: T('وإذا') });   // d51
shake(T('للتهديد'), { amt: 1.1 });   // d52

// ---------------------------------------------------------------- 71.28-81.67 s  «وإذا أُغلق؟ فستكون الصين، والهند، واليابان، أول المتضرّرين؛ لأن أربعة وثمانين في»
mapStyle('satellite', T('وإذا'));   // d53
region('CHN', { at: T('الصين'), style: 'danger', label: 'الصين', labelPill: '#111a2b', say: 'الصين', until: T('والبديل') });   // d54
region('IND', { at: T('والهند'), flag: true, label: 'الهند', labelPill: '#111a2b', say: 'والهند', until: T('والبديل') });   // d55
region('JPN', { at: T('واليابان'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'اليابان', labelPill: '#111a2b', say: 'واليابان', until: T('والبديل') });   // d56
path(lane('HORMUZ_TO_EAST_ASIA'), { at: T('وإذا+0.2'), dur: 1.2, color: '#ff4757', width: 8, sea: true, until: T('والبديل') });   // d57
sprite('tanker', null, { at: T('وإذا'), early: 0, along: lane('HORMUZ_TO_EAST_ASIA'), travel: 18.54, until: T('والبديل') });   // d58
number(null, '#%', { at: T('أربعة#2'), until: T('والبديل'), count: { to: 84, dur: 0.95 }, color: 'fire', sub: 'من هذا النفط' });   // d59
punch(T('المئة#2'), { amt: .1 });   // d60
shake(T('أُغلق'), { amt: 1.1 });   // d61

// ---------------------------------------------------------------- 81.67-90.69 s  «والبديل؟ أنابيب سعودية وإماراتية، تلتفّ حول المضيق إلى ينبع والفجيرة… لكنها لا ت»
label(spot('YANBU'), 'ينبع', { at: T('ينبع'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'SAU', lead: [0, -110] });   // d62
label(spot('FUJAIRAH'), 'الفجيرة', { at: T('والفجيرة'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'ARE', lead: [0, 110] });   // d63
path(pipeline('EAST_WEST'), { at: T('ينبع'), dur: 1.2, color: '#ffd23f', width: 12, until: T('لذلك#2') });   // d64

// ---------------------------------------------------------------- 90.69-96.23 s  «لذلك، يرابط الأسطول الخامس الأمريكي في البحرين، داخل الخليج نفسه.»
flagPin('USA', spot('NSA_BAHRAIN'), { at: T('الأمريكي'), in: 'BHR', say: 'الأمريكي', until: T('لأن#2') });   // d65
region('BHR', { at: T('البحرين'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'البحرين', labelPill: '#111a2b', say: 'البحرين', until: T('لأن#2') });   // d66
label(spot('NSA_BAHRAIN'), 'مقر الأسطول الخامس الأمريكي', { at: T('الخامس'), pill: '#111a2b', color: '#ffd23f', until: T('لأن#2'), anim: 'bubble', in: 'BHR' });   // d67
sprite('carrier', null, { at: T('لذلك#2'), early: 0, along: [[50.95,26.515],[50.823,26.401],[50.695,26.286]], travel: 4.99, until: T('لأن#2') });   // an approach through open water, at the berth before the line ends   // d68
sprite('destroyer', null, { at: T('لذلك#2+0.6'), early: 0, along: [[50.95,26.515],[50.823,26.401],[50.695,26.286]], travel: 4.99, sfx: false, until: T('لأن#2') });   // its escort, behind it   // d69

// ---------------------------------------------------------------- 96.23-101.71 s  «لأن من يمسك هُرمز… يمسك صمّام النفط في العالم كله!»
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('هُرمز#3'), pill: '#111a2b', color: '#ffd23f', until: T('كله#5.end+0.6'), anim: 'bubble', in: 'sea' });   // d70
sprite('tanker', null, { at: T('لأن#2'), early: 0, along: [[55.7,26.385],[56.075,26.555],[56.45,26.627],[56.66,26.3],[56.9,25.95],[57.233,25.617],[57.6,25.308],[58,25.025],[58.4,24.742],[58.6,24.6]], travel: 11.36, scale: .85, sfx: false, until: T('كله#5.end+0.6') });   // traffic on HORMUZ_TO_RAS_TANURA   // d71
sprite('container_ship', null, { at: T('لأن#2'), early: 0, along: [[56.075,26.555],[56.25,26.64],[56.45,26.627],[56.587,26.513],[56.66,26.3],[56.78,26.125],[56.9,25.95],[57.067,25.783],[57.233,25.617],[57.4,25.45]], travel: 11.36, scale: .85, sfx: false, until: T('كله#5.end+0.6') });   // traffic on HORMUZ_GATE   // d72
title('صمّام النفط', { at: T('يمسك'), until: T('كله#5.end+0.5'), color: 'yellow', anim: 'words', mag: 1.5 });   // d73
punch(T('يمسك'), { amt: .14 });   // d74
sprite('tanker', null, { at: T('لأن#2+1'), early: 0, along: lane('HORMUZ_GATE'), travel: 12.77, sfx: false });   // the loop: the opening ship again   // d75
path(lane('HORMUZ_GATE'), { at: T('يمسك'), dur: .6, color: '#ff4757', width: 11, sea: true });   // d76

captions({ style: 'line', emph: ["خُمس","العالم","تسعة","أربعة","عشرين","خُمْسُ"] });
end(T('كله#5.end+1.2'));
start();
