import { cam, start, label, spot, end } from './map.js';
cam(0, { lon: -5.6, lat: 35.95, span: 4 });
cam(2, { lon: -5.6, lat: 35.95, span: 1.2, drone: true, heading: 0 });
cam(4, { lon: -5.6, lat: 35.95, span: .6, drone: true, heading: 80 });
cam(6, { lon: -5.35, lat: 36.13, span: .35, drone: true, heading: 200 });
label(spot('GIBRALTAR'), 'مضيق جبل طارق', { at: 0, pill: '#111a2b', color: '#ffd23f', until: 12 });
end(12);
start();
