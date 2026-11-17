// Maven Map: Rub' al Khali (الربع الخالي)
// Voiceover: Gemini 3.8 Flash TTS (Riko voice), 51.5 s
import { cam, region, ghost, atlasName, title, icon, ping, label, number, captions, cut, end, T, start } from './map.js';

const SHAYBAH = [53.98, 22.52];      // Shaybah mega oil field in the heart of Rub' al Khali
const MOREEB = [53.78, 22.97];       // Moreeb Dune / Liwa giant dune (~250-300m)
const CENTER = [51.0, 21.0];

// --- CAMERA CHOREOGRAPHY ---
// 1. Hook: Start from wide Arabian Peninsula view, diving towards Rub' al Khali
cam(0, { lon: 47.0, lat: 21.5, span: 48 });
cam(T('مساحته'), { lon: 50.0, lat: 21.0, span: 24, ease: 'sine' });

// 2. The Question: Push into the center of the Empty Quarter
cam(T('شنو'), { lon: 50.5, lat: 20.8, span: 17.5, ease: 'io' });

// 3. The 4 countries & massive expanse
cam(T('السعودية'), { lon: 50.0, lat: 21.5, span: 22.0, ease: 'sine' });
cam(T('650'), { lon: 50.8, lat: 20.6, span: 15.0, ease: 'io' });

// 4. France size comparison
cam(T('خريطة'), { lon: 50.5, lat: 20.8, span: 17.0, ease: 'sine' });

// 5. Deep dive into the towering sand dunes
cam(T('الكثبان'), { lon: 53.5, lat: 22.8, span: 4.8, ease: 'io' });

// 6. Desert heat expanse
cam(T('ودرجات'), { lon: 51.5, lat: 21.5, span: 11.0, ease: 'sine' });

// 7. Payoff: Shaybah field & energy reservoir
cam(T('الصدمة'), { lon: 52.8, lat: 22.8, span: 10.5, ease: 'io' });
cam(T('العالم'), { lon: 50.5, lat: 21.0, span: 18.0, ease: 'io' });

// --- VISUAL ELEMENTS ---

// Hook: Zero inhabitants
number({ x: 540, y: 340 }, '0', { at: T('صفر'), until: T('شنو') - 0.1, size: 190, color: 'red', sub: 'سكان دائمون' });

// The Question
title('ليش فارغ؟', { at: T('السر'), until: T('الربع') - 0.1, y: 330, size: 160 });

// The Name on the land
atlasName(CENTER, 'الربع الخالي', { at: T('الربع'), size: 110, color: '#ffe494', until: T('الكثبان') });

// Highlighting the 4 countries
region('SAU', { at: T('السعودية'), style: 'tint', fade: [T('يبتلع'), 0.35] });
region('OMN', { at: T('عمان'), style: 'solid', fade: [T('يبتلع'), 0.35] });
region('ARE', { at: T('الإمارات'), style: 'teal', fade: [T('يبتلع'), 0.35] });
region('YEM', { at: T('واليمن'), style: 'orange', fade: [T('يبتلع'), 0.35] });

// Massive area number
number({ x: 540, y: 330 }, '650,000 كم²', { at: T('650'), until: T('ولتعرف') - 0.1, size: 130, color: 'yellow', sub: 'أكبر بحر رمال بالعالم' });

// France overlay comparison
ghost('FRA', { only: [-5.5, 41.2, 9.8, 51.2], to: [50.5, 20.8], at: T('فرنسا#2') - 0.2, dur: 1.2, style: 'blue', label: 'فرنسا', until: T('الكثبان') + 0.2 });

// Giant Dunes: 250m
icon(MOREEB, 'mountain', { at: T('الكثبان'), badge: 'hex', color: '#e3901e', size: 1.2, until: T('ودرجات') });
number({ x: 540, y: 330 }, '250 م', { at: T('250'), until: T('ودرجات') - 0.1, size: 160, color: 'yellow', sub: 'ارتفاع الكثبان' });

// Heat: +50°C
number({ x: 540, y: 330 }, '+50°C', { at: T('50'), until: T('لكن') - 0.1, size: 170, color: 'fire', sub: 'حرارة الصيف' });
label({ x: 540, y: 440 }, 'أمطار أقل من 35 ملم', { at: T('أمطار'), pill: '#111a2b', color: '#ffffff', size: 46, until: T('لكن') });

// Payoff: The giant oil & gas wealth underneath
cut(T('لكن'));
ping(SHAYBAH, { at: T('مخزون'), color: '#ffd23f', until: T('العالم.end+1.2') });
label(SHAYBAH, 'حقل شيبة', { at: T('مخزون'), pill: '#111a2b', color: 'yellow', size: 48, dy: -70, until: T('العالم.end+1.2') });
title('أضخم خزان طاقة', { at: T('ينام'), y: 320, size: 125, color: '#ffd23f', until: T('العالم.end+1.2') });

captions({ style: 'clean', size: 62 });
end(T('العالم.end+1.2'));
start();
