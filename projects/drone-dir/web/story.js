// Written by engine/scripts/director.mjs (TaleSpark 2.0 automatic director, 2026-11-17T21:23)
// from script.txt + web/timing.json. Every item has an id (// dN); the logic gate's rejects go to director.drop.json.
import { T, cam, captions, end, label, punch, spot, sprite, start, title } from './map.js';

// ---- camera: one shot per line, arriving early in it, then a hold key at its end (cam(t) = the camera ARRIVES at t)
cam(0, { lon: 64.4, lat: 14.38, span: 170, cy: .46 });   // opening: the Earth from orbit
cam(2.2, { lon: -5.6, lat: 35.95, span: 3, ease: 'io', dive: true });   // the dive from orbit onto the first place
cam(T('وإفريقيا.end'), { lon: -6.074, lat: 35.963, span: 2.23, ease: 'sine', drone: true, heading: -88.07 });   // hold
cam(T('ننزل+0.16'), { lon: -11.007, lat: 29.834, span: 20.9, ease: 'io' });   // hop: up over the map (1657 km)
cam(T('إلى+0.32'), { lon: -15.94, lat: 23.705, span: 2.24, ease: 'io', dive: true, drone: true, heading: 0 });   // DAKHLA (drone: orbit)
cam(T('كبيراً.end'), { lon: -15.94, lat: 23.705, span: 2.08, ease: 'sine', drone: true, heading: -32 });   // hold
cam(T('وبعيداً+0.52'), { lon: 20.28, lat: 25.153, span: 75, ease: 'io' });   // hop: up over the map (7184 km)
cam(T('الشرق+0.74'), { lon: 57.003, lat: 26.182, span: 3.84, ease: 'io', dive: true, drone: true, heading: -47.1 });   // HORMUZ (drone: along)
cam(T('هُرمز.end'), { lon: 55.997, lat: 27.018, span: 3.57, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('التجارة.end'), { lon: 55.575, lat: 27.368, span: 2.99, ease: 'sine', drone: true, heading: -47.1 });   // hold
cam(T('التجارة.end+0.45'), { lon: 24.987, lat: 31.659, span: 72.71, ease: 'io' });   // hop: the loop goes home over the map
cam(T('التجارة.end+1'), { lon: -5.6, lat: 35.95, span: 3, ease: 'io', dive: true });   // loop: back to the opening frame

// ---------------------------------------------------------------- 0.2-5.81 s  «من مضيق جبل طارق، تعبر السفن كل يوم بين أوروبا وإفريقيا.»
label(spot('GIBRALTAR'), 'مضيق جبل طارق', { at: T('مضيق'), pill: '#111a2b', color: '#ffd23f', until: T('ثم'), anim: 'bubble', in: 'sea' });   // d1

// ---------------------------------------------------------------- 6.26-12.89 s  «ثم ننزل جنوباً إلى الداخلة، المدينة التي تبني اليوم ميناءً أطلسياً كبيراً.»
label(spot('DAKHLA'), 'الداخلة', { at: T('الداخلة'), pill: '#111a2b', color: '#ffd23f', until: T('وبعيداً'), anim: 'bubble', in: 'MAR' });   // d2
sprite('port_crane', spot('DAKHLA'), { at: T('ميناءً'), until: T('وبعيداً'), in: 'MAR' });   // «ميناءً»   // d3

// ---------------------------------------------------------------- 13.34-18.41 s  «وبعيداً في الشرق، يمر خُمْسُ نفط العالم عبر مضيق هُرمز.»
title('خُمْسُ نفط العالم', { at: T('وبعيداً'), until: T('مضيق#2-0.1'), color: 'yellow', anim: 'words' });   // d4
label(spot('HORMUZ'), 'مضيق هرمز', { at: T('مضيق#2'), pill: '#111a2b', color: '#ffd23f', until: T('ثلاثة'), anim: 'bubble', in: 'sea' });   // d5

// ---------------------------------------------------------------- 18.86-22.34 s  «ثلاثة ممرات، وقصة واحدة: التجارة.»
title('وقصة واحدة: التجارة', { at: T('وقصة'), until: T('التجارة.end+0.5'), color: 'yellow', anim: 'words', mag: 1.5 });   // d6
punch(T('وقصة'), { amt: .14 });   // d7

captions({ style: 'line', emph: ["خُمْسُ","العالم","وقصة","واحدة:","التجارة"] });
end(T('التجارة.end+1.2'));
start();
