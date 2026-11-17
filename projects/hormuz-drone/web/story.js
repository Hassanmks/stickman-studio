// Written by engine/scripts/director.mjs (TaleSpark 2.0 automatic director, 2026-11-17T21:22)
// from script.txt + web/timing.json. Every item has an id (// dN); the logic gate's rejects go to director.drop.json.
import { T, cam, captions, end, flagPin, label, measure, number, punch, region, shake, spot, sprite, start, title } from './map.js';

// ---- camera: one shot per line, arriving early in it, then a hold key at its end (cam(t) = the camera ARRIVES at t)
cam(0, { lon: 123.667, lat: 12.966, span: 170, cy: .46 });   // opening: the Earth from orbit
cam(2.2, { lon: 53.667, lat: 32.415, span: 40, ease: 'io', dive: true });   // the dive from orbit onto the first place
cam(T('هذا+0.6'), { lon: 53.667, lat: 32.415, span: 22, ease: 'io' });   // HORMUZ
cam(T('الجنوب.end'), { lon: 53.667, lat: 32.415, span: 20.46, ease: 'sine' });   // hold
cam(T('بين+0.51'), { lon: 56.435, lat: 26.68, span: 0.96, ease: 'io', dive: true, drone: true, heading: 0 });   // LARAK (drone: orbit)
cam(T('كيلومتراً#2.end'), { lon: 56.435, lat: 26.68, span: 0.89, ease: 'sine', drone: true, heading: -32 });   // hold
cam(T('للخروج.end'), { lon: 56.435, lat: 26.68, span: 0.74, ease: 'sine', drone: true, heading: -64 });   // hold
cam(T('كله#3.end'), { lon: 56.435, lat: 26.68, span: 0.62, ease: 'sine', drone: true, heading: -96 });   // hold
cam(T('النفط#2+0.02'), { lon: 53.81, lat: 26.02, span: 6.86, ease: 'io' });   // hop: up over the map (544 km)
cam(T('خُمْسُ#2+0.43'), { lon: 51.184, lat: 25.36, span: 2.4, ease: 'io', dive: true });   // QAT
cam(T('نفسه.end'), { lon: 51.184, lat: 25.36, span: 2.23, ease: 'sine' });   // hold
cam(T('السعودية+0.35'), { lon: 57.003, lat: 26.182, span: 3.84, ease: 'io', drone: true, heading: -47.1 });   // HORMUZ (drone: along)
cam(T('هُرمز#2.end'), { lon: 55.997, lat: 27.018, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('والمشكلة+0.9'), { lon: 56.06, lat: 26.983, span: 1.6, ease: 'io', drone: true, heading: -47.1 });   // BANDAR_ABBAS (drone: orbit)
cam(T('قِشم.end'), { lon: 56.06, lat: 26.983, span: 1.49, ease: 'sine', drone: true, heading: -15.1 });   // hold
cam(T('تعرّضت+0.38'), { lon: 53.667, lat: 32.415, span: 22, ease: 'io' });   // IRN
cam(T('المضيق#2.end'), { lon: 53.667, lat: 32.415, span: 20.46, ease: 'sine' });   // hold
cam(T('فستكون+0.54'), { lon: 111.065, lat: 30.158, span: 95, ease: 'io' });   // CHN+IND+JPN
cam(T('آسيا.end'), { lon: 111.065, lat: 30.158, span: 88.35, ease: 'sine' });   // hold
cam(T('أنابيب+0.64'), { lon: 47.195, lat: 24.62, span: 22.42, ease: 'io' });   // YANBU
cam(T('صغيراً.end'), { lon: 47.195, lat: 24.62, span: 20.85, ease: 'sine' });   // hold
cam(T('الأسطول+0.16'), { lon: 50.61, lat: 26.21, span: 0.96, ease: 'io', dive: true, drone: true, heading: 0 });   // NSA_BAHRAIN (drone: orbit)
cam(T('نفسه#2.end'), { lon: 50.61, lat: 26.21, span: 0.89, ease: 'sine', drone: true, heading: -32 });   // hold
cam(T('لأن#2+0.86'), { lon: 53.555, lat: 26.405, span: 7.4, ease: 'io' });   // hop: up over the map (587 km)
cam(T('هُرمز#3+0.12'), { lon: 57.003, lat: 26.182, span: 3.84, ease: 'io', dive: true, drone: true, heading: -47.1 });   // HORMUZ (drone: along)
cam(T('كله#5.end'), { lon: 55.997, lat: 27.018, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold

// ---------------------------------------------------------------- 0-7.35 s  «هل تعلم أن خُمْسَ نفطِ العالم كله، يعبر ممراً لا يتجاوز عرضه تسعة وثلاثين كيلومت»
number(null, '# كم', { at: T('تسعة'), until: T('هذا'), count: { to: 39, dur: 0.95 }, color: 'yellow' });   // d1
punch(T('كيلومتراً'), { amt: .1 });   // d2

// ---------------------------------------------------------------- 7.35-12.73 s  «هذا هو مضيق هُرمز: إيران من الشمال، وسلطنة عُمان من الجنوب.»
region('IRN', { at: T('إيران'), flag: true, label: 'إيران', labelPill: '#111a2b', say: 'إيران', until: T('هنا') });   // d3
region('OMN', { at: T('وسلطنة'), flag: true, label: 'سلطنة عُمان', labelPill: '#111a2b', say: 'وسلطنة', until: T('هنا') });   // d4
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('مضيق'), pill: '#111a2b', color: '#ffd23f', until: T('هنا'), anim: 'bubble', in: 'sea' });   // d5

// ---------------------------------------------------------------- 12.73-21.9 s  «هنا بالضبط، بين جزيرة لارَكْ الإيرانية، وجزر السلامة العُمانية، لا يتجاوز عرض ال»
label(spot('LARAK'), 'جزيرة لارك', { at: T('جزيرة'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', in: 'IRN', lead: [0, -110] });   // d6
label(spot('QUOIN'), 'جزر السلامة', { at: T('وجزر'), pill: '#111a2b', color: '#ffd23f', until: T('لكنّ'), anim: 'bubble', lead: [0, 110] });   // d7
measure(spot('LARAK_S'), spot('QUOIN'), { at: T('تسعة#2'), dur: 1.0, to: 39, unit: 'كم', color: '#ffd23f', until: T('لكنّ') });   // d8
punch(T('كيلومتراً#2'), { amt: .1 });   // d9

// ---------------------------------------------------------------- 21.9-33.33 s  «لكنّ الناقلات العملاقة، لا تستخدم هذا العرض كله! هناك ممرّان فقط، عرض كلٍّ منهما»
number(null, '# كم', { at: T('أربعة'), until: T('كل#2'), count: { to: 4, dur: 0.95 }, color: 'yellow' });   // d10
punch(T('كيلومترات'), { amt: .1 });   // d11

// ---------------------------------------------------------------- 33.33-41.5 s  «كل يوم، يعبر من هنا نحو عشرين مليون برميل من النفط؛ أي قرابة عشرين في المئة من ا»
number(null, '# مليون', { at: T('عشرين'), until: T('عشرين#2-0.15'), count: { to: 20, dur: 0.95 }, color: 'yellow', sub: 'برميل من النفط' });   // d12
punch(T('مليون'), { amt: .1 });   // d13
number(null, '#%', { at: T('عشرين#2'), until: T('وليس'), count: { to: 20, dur: 0.95 }, color: 'yellow', sub: 'من استهلاك العالم كله' });   // d14
punch(T('المئة'), { amt: .1 });   // d15

// ---------------------------------------------------------------- 41.5-48.88 s  «وليس النفط وحده: خُمْسُ الغازِ المُسال في العالم، يخرج من قطر، ويعبر الممرّ الضي»
title('خُمْسُ الغازِ المُسال', { at: T('وليس'), until: T('قطر-0.1'), color: 'yellow', anim: 'words' });   // d16
region('QAT', { at: T('قطر'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'قطر', labelPill: '#111a2b', say: 'قطر', until: T('السعودية') });   // d17

// ---------------------------------------------------------------- 48.88-58.34 s  «السعودية، والكويت، والعراق، والإمارات… كلها تصدّر من الخليج. والخليج ليس له إلا »
flagPin('SAU', spot('HORMUZ'), { at: T('السعودية'), in: 'sea', say: 'السعودية', until: T('والمشكلة') });   // d18
region('ARE', { at: T('والإمارات'), flag: true, label: 'الإمارات', labelPill: '#111a2b', say: 'والإمارات', until: T('والمشكلة') });   // d19
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('هُرمز#2'), pill: '#111a2b', color: '#ffd23f', until: T('والمشكلة'), anim: 'bubble', in: 'sea' });   // d20

// ---------------------------------------------------------------- 58.34-64.54 s  «والمشكلة؟ الساحل الشمالي كله إيراني، من بندر عباس، حتى جزيرة قِشم.»
label(spot('BANDAR_ABBAS'), 'بندر عباس', { at: T('بندر'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, -110] });   // d21
label(spot('QESHM'), 'جزيرة قشم', { at: T('جزيرة#2'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك'), anim: 'bubble', in: 'IRN', lead: [0, 110] });   // d22

// ---------------------------------------------------------------- 64.54-71.28 s  «لذلك، كلما تعرّضت إيران للتهديد، لوّحت بورقة واحدة: إغلاق المضيق!»
region('IRN', { at: T('إيران#2'), style: 'danger', label: 'إيران', labelPill: '#111a2b', say: 'إيران', until: T('وإذا') });   // d23
shake(T('للتهديد'), { amt: 1.1 });   // d24

// ---------------------------------------------------------------- 71.28-81.67 s  «وإذا أُغلق؟ فستكون الصين، والهند، واليابان، أول المتضرّرين؛ لأن أربعة وثمانين في»
region('CHN', { at: T('الصين'), style: 'danger', label: 'الصين', labelPill: '#111a2b', say: 'الصين', until: T('والبديل') });   // d25
region('IND', { at: T('والهند'), flag: true, label: 'الهند', labelPill: '#111a2b', say: 'والهند', until: T('والبديل') });   // d26
region('JPN', { at: T('واليابان'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'اليابان', labelPill: '#111a2b', say: 'واليابان', until: T('والبديل') });   // d27
number(null, '#%', { at: T('أربعة#2'), until: T('والبديل'), count: { to: 84, dur: 0.95 }, color: 'fire', sub: 'من هذا النفط' });   // d28
punch(T('المئة#2'), { amt: .1 });   // d29
shake(T('أُغلق'), { amt: 1.1 });   // d30

// ---------------------------------------------------------------- 81.67-90.69 s  «والبديل؟ أنابيب سعودية وإماراتية، تلتفّ حول المضيق إلى ينبع والفجيرة… لكنها لا ت»
label(spot('YANBU'), 'ينبع', { at: T('ينبع'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'SAU', lead: [0, -110] });   // d31
label(spot('FUJAIRAH'), 'الفجيرة', { at: T('والفجيرة'), pill: '#111a2b', color: '#ffd23f', until: T('لذلك#2'), anim: 'bubble', in: 'ARE', lead: [0, 110] });   // d32
sprite('pipeline', spot('YANBU'), { at: T('أنابيب'), until: T('لذلك#2'), in: 'SAU' });   // «أنابيب»   // d33

// ---------------------------------------------------------------- 90.69-96.23 s  «لذلك، يرابط الأسطول الخامس الأمريكي في البحرين، داخل الخليج نفسه.»
flagPin('USA', spot('NSA_BAHRAIN'), { at: T('الأمريكي'), in: 'BHR', say: 'الأمريكي', until: T('لأن#2') });   // d34
region('BHR', { at: T('البحرين'), style: 'vivid', color: '#ffd23f', opacity: .5, label: 'البحرين', labelPill: '#111a2b', say: 'البحرين', until: T('لأن#2') });   // d35
label(spot('NSA_BAHRAIN'), 'مقر الأسطول الخامس الأمريكي', { at: T('الخامس'), pill: '#111a2b', color: '#ffd23f', until: T('لأن#2'), anim: 'bubble', in: 'BHR' });   // d36

// ---------------------------------------------------------------- 96.23-101.71 s  «لأن من يمسك هُرمز… يمسك صمّام النفط في العالم كله!»
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('هُرمز#3'), pill: '#111a2b', color: '#ffd23f', until: T('كله#5.end+0.6'), anim: 'bubble', in: 'sea' });   // d37
title('يمسك صمّام النفط في العالم كله', { at: T('يمسك'), until: T('كله#5.end+0.5'), color: 'yellow', anim: 'words', mag: 1.5 });   // d38
punch(T('يمسك'), { amt: .14 });   // d39

captions({ style: 'line', emph: ["تسعة","أربعة","عشرين","خُمْسُ","الغازِ","المُسال"] });
end(T('كله#5.end+1.2'));
start();
