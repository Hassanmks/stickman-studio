// Maven Map engine: a real-satellite globe (orthographic, WebGL2) that dives from space to a street, with map graphics
// drawn on top (country highlights, routes, moving ships, rulers, pins, numbers, Arabic word-timed captions).
// Everything is a pure function of time: seek(t) draws frame t. The story file (web/story.js) only declares things.
export const W = 1080, H = 1920;
const D2R = Math.PI / 180;
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const lerp = (a, b, t) => a + (b - a) * t;
export const EASE = {
  linear: t => t, out: t => 1 - (1 - t) ** 3, in: t => t ** 3, io: t => (t < .5 ? 4 * t ** 3 : 1 - (-2 * t + 2) ** 3 / 2),
  back: t => { const c = 1.9; return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2; }, sine: t => -(Math.cos(Math.PI * t) - 1) / 2,
};
export const COL = { blue: '#4fb3ff', tint: 'rgba(79,179,255,.42)', orange: '#ff9a1f', red: '#ff4d4d', green: '#3fd27a', yellow: '#ffd23f',
  purple: '#b58cff', teal: '#2fd6c8', pink: '#ff7ab6', white: '#ffffff', ink: '#0b1220', fire: '#ff7a1a', sand: '#f6eea0' };
const col = c => COL[c] || c;
const rgba = (hex, a) => { if (!hex.startsWith('#')) return hex; const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16},${n >> 8 & 255},${n & 255},${a})`; };

// ------------------------------------------------------------------ load
const q = new URLSearchParams(location.search);
const getJSON = (u, d) => fetch(u).then(r => r.ok ? r.json() : d).catch(() => d);
const CFG = await getJSON('config.json', {});
const TIMING = await getJSON('timing.json', { words: [], dur: 10 });
const WORLD = await getJSON('/data/world.json', { countries: [] });
const PATCHES = await getJSON('img/patches.json', []);
const RIVERS = await getJSON('/data/rivers.json', { rivers: [] });
const PLACES = await getJSON('/data/places.json', { places: {}, lanes: {}, pipelines: {} });   // speed plan item 9: checked places and sea lanes
// spot('HORMUZ') -> [lon, lat] | lane('HORMUZ_GATE') -> [[lon, lat], ...] (a sea route, use it with path(..., {sea:true}) or sprite along)
// pipeline('EAST_WEST') -> a land route. Unknown keys throw, so a typo never puts a spot in the wrong place.
export function spot(k) { const p = PLACES.places[k]; if (!p) throw new Error(`spot(): '${k}' is not in engine/data/places.json`); return p.pt.slice(); }
export function spotInfo(k) { spot(k); return PLACES.places[k]; }
export function lane(k, a, b) { const l = PLACES.lanes[k]; if (!l) throw new Error(`lane(): '${k}' is not in engine/data/places.json`); return l.pts.slice(a ?? 0, b ?? l.pts.length).map(p => p.slice()); }
export function pipeline(k) { const l = PLACES.pipelines[k]; if (!l) throw new Error(`pipeline(): '${k}' is not in engine/data/places.json`); return l.pts.map(p => p.slice()); }
const LOOK = CFG.look || 'atlas';   // 'atlas': bright satellite, teal sea (the viral map-Shorts look) | 'night': dark sea, dimmed
const LANG = CFG.lang || TIMING.lang || 'ar', RTL = LANG === 'ar';
const loadImg = src => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => no(new Error('image ' + src)); i.src = src; });
const FONTS = [
  ['MapSerif', 'fonts/DMSerifDisplay-Italic.ttf'],
  ['MapLatText', 'fonts/Poppins-SemiBold.ttf'],
  ['MapRuqaa', 'fonts/ArefRuqaa-Bold.ttf'],
  ['MapAr', 'fonts/Alexandria-Bold.ttf'],   // geographic labels, pills, rulers = Alexandria (user rule)
  ['MapArText', 'fonts/Alexandria-Bold.ttf'],
  ['Cairo', 'fonts/Cairo-Bold.ttf'],
  ['Alexandria', 'fonts/Alexandria-Bold.ttf'],
  ['MapLat', 'fonts/Poppins-ExtraBold.ttf']
];
// Cairo and Alexandria are variable fonts (every weight in one file). Each face claims the whole weight range, so
// '900 140px Cairo' picks the real Black axis value and a static font is never synthetically emboldened.
for (const [f, u] of FONTS) { const ff = new FontFace(f, `url(${u})`, { weight: '100 1000' }); await ff.load(); document.fonts.add(ff); }
const BY = {}; for (const c of WORLD.countries) { BY[c.iso] = c; BY[c.a3] = BY[c.a3] || c; BY[c.en.toLowerCase()] = c; BY[c.ar] = c; }
export const country = k => BY[k] || BY[String(k).toLowerCase()] || null;
// v6: a bounding box per ring [w, s, e, n], so a point-on-land test touches only the coastlines near the point
for (const c of WORLD.countries) c.rb = c.r.map(r => { let a = 1e9, b = 1e9, d = -1e9, e = -1e9; for (let i = 0; i < r.length; i += 2) { if (r[i] < a) a = r[i]; if (r[i] > d) d = r[i]; if (r[i + 1] < b) b = r[i + 1]; if (r[i + 1] > e) e = r[i + 1]; } return [a / 1e4, b / 1e4, d / 1e4, e / 1e4]; });

// ------------------------------------------------------------------ time words
const nw = w => String(w).toLowerCase().replace(/[ً-ْـ]/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/[^\p{L}\p{N}]/gu, '');
export const WORDS = TIMING.words || [];
// when(5.2) | when('هرمز') | when('هرمز#2') | when('هرمز+0.2') | when('هرمز.end') (end of the word)
export function when(x) {
  if (typeof x === 'number') return x;
  const m = String(x).match(/^(.+?)(?:#(\d+))?(\.end)?([+-][\d.]+)?$/); if (!m) throw new Error('when(): ' + x);
  const key = nw(m[1]), n = +(m[2] || 1), off = +(m[4] || 0);
  let hits = WORDS.filter(w => nw(w[0]) === key); if (!hits.length) hits = WORDS.filter(w => nw(w[0]).startsWith(key) || key.startsWith(nw(w[0])) && nw(w[0]).length > 2);
  if (hits.length < n) throw new Error(`word not in timing: ${x}`);
  return (m[3] ? hits[n - 1][2] : hits[n - 1][1]) + off;
}
export const T = when;

// ------------------------------------------------------------------ camera
// keys: {t, lon, lat, span (degrees of arc across the frame width), cy (0..1 screen height of the centre), roll, ease, dive}
const CAMS = [];
export function cam(t, o) { CAMS.push({ t: when(t), cy: .42, ease: 'io', ...o }); CAMS.sort((a, b) => a.t - b.t); }
const kOf = span => W / (span * D2R);   // pixels per radian at the centre
export function camAt(t) {
  if (!CAMS.length) return { lon: 0, lat: 20, span: 140, cy: .42, roll: 0 };
  if (t <= CAMS[0].t) return { ...CAMS[0] }; const L = CAMS[CAMS.length - 1]; if (t >= L.t) return { ...L };
  let i = 0; while (CAMS[i + 1].t < t) i++;
  const a = CAMS[i], b = CAMS[i + 1], u = (t - a.t) / (b.t - a.t), e = EASE[b.ease] || EASE.io;
  const z = e(u); let c = z;
  if (b.dive) { c = EASE.out(clamp(u * 1.25)); }      // a dive turns to the target first, then falls in
  let dl = b.lon - a.lon; if (dl > 180) dl -= 360; if (dl < -180) dl += 360;
  const ls = Math.log(a.span), le = Math.log(b.span);
  return { lon: a.lon + dl * c, lat: lerp(a.lat, b.lat, c), span: Math.exp(lerp(ls, le, z)), cy: lerp(a.cy, b.cy, z), roll: lerp(a.roll || 0, b.roll || 0, z) };
}
// punch(t, {amt:.14, dur:.55})  a snap zoom-in that eases back  |  shake(t, {amt:1, dur:.45})  a decaying hand-held jolt
const FX = [];
export function punch(t, o = {}) { FX.push({ kind: 'punch', t: when(t), amt: o.amt ?? .14, dur: o.dur ?? .55, sfx: o.sfx }); }
export function shake(t, o = {}) { FX.push({ kind: 'shake', t: when(t), amt: o.amt ?? 1, dur: o.dur ?? .45, sfx: o.sfx }); }
// drift(.018): v5 - a held shot never dies: a slow breathing zoom and a tiny pan, faded out while the camera travels
let DRIFT = 0; export function drift(amt = .018) { DRIFT = amt; }
export function camFinal(t) {
  const c = camAt(t);
  if (DRIFT) {
    const c2 = camAt(t + .3), mv = Math.abs(Math.log(c2.span / c.span)) + Math.hypot(c2.lon - c.lon, c2.lat - c.lat) / c.span, w = clamp(1 - mv * 14);
    if (w > 0) { c.span *= 1 - DRIFT * w * (.5 + .5 * Math.sin(t * .42)); c.lon += c.span * .006 * w * Math.sin(t * .27); c.lat += c.span * .004 * w * Math.cos(t * .31); }
  }
  for (const f of FX) {
    const u = (t - f.t) / f.dur; if (u < 0 || u >= 1) continue;
    if (f.kind === 'punch') { const e = u < .16 ? EASE.out(u / .16) : 1 - EASE.io((u - .16) / .84); c.span /= 1 + f.amt * e; }
    else { const k = f.amt * (1 - u) ** 2 * c.span * .006; c.lon += k * Math.sin(u * 61 + f.t * 7); c.lat += k * Math.cos(u * 53 + f.t * 5); }
  }
  return c;
}
let C = null;   // the camera of the frame being drawn: {l0, p0, k, cx, cy, sp, cp}
function setCam(c) { C = { l0: c.lon * D2R, p0: c.lat * D2R, k: kOf(c.span), cx: W / 2, cy: c.cy * H, span: c.span }; C.sp = Math.sin(C.p0); C.cp = Math.cos(C.p0); }
// lon/lat -> screen [x, y, front]; points on the far side are pushed to the horizon
export function P(lon, lat) {
  const l = lon * D2R - C.l0, p = lat * D2R, cl = Math.cos(l), cp = Math.cos(p), sp = Math.sin(p);
  const cosc = C.sp * sp + C.cp * cp * cl;
  let x = cp * Math.sin(l), y = C.cp * sp - C.sp * cp * cl;
  if (cosc < 0) { const r = Math.hypot(x, y) || 1; x /= r; y /= r; }
  return [C.cx + x * C.k, C.cy - y * C.k, cosc >= 0];
}
export const pxPerKm = () => C.k / 6371;

// ------------------------------------------------------------------ WebGL globe
const gl = document.getElementById('gl').getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
if (!gl) throw new Error('WebGL2 is not available');
const VS = `#version 300 es
in vec2 p; void main(){ gl_Position = vec4(p, 0., 1.); }`;
const NP = 4;
const FS = `#version 300 es
precision highp float;
uniform sampler2D base; uniform sampler2D p0; uniform sampler2D p1; uniform sampler2D p2; uniform sampler2D p3;
uniform vec4 bb[${NP}]; uniform int np;
uniform vec2 res; uniform vec2 ctr; uniform float R; uniform float l0; uniform float ph0; uniform float t;
uniform float ocean; uniform float dimv; uniform float sat; uniform float roll; uniform sampler2D mask; uniform float look;
uniform vec3 stA; uniform vec3 stB; uniform float stP; uniform float stRtl;   // v5 map worlds (flat, sepia, night) + wipe
out vec4 o;
const float PI = 3.14159265;
float h21(vec2 p){ p = fract(p*vec2(123.34, 456.21)); p += dot(p, p+45.32); return fract(p.x*p.y); }
void main(){
  vec2 f = vec2(gl_FragCoord.x, res.y - gl_FragCoord.y);
  vec2 d = (f - ctr) / R; d = vec2(d.x*cos(roll) - d.y*sin(roll), d.x*sin(roll) + d.y*cos(roll));
  float x = d.x, y = -d.y, rho = length(vec2(x, y));
  // space + atmosphere
  float edge = rho - 1.;
  vec3 sky = mix(vec3(.012,.02,.05), vec3(.03,.06,.14), clamp(1.2 - rho*.35, 0., 1.));
  float st = step(.9975, h21(floor(f/2.))) * (.4 + .6*h21(floor(f/2.)+3.)); sky += st*.8*clamp(edge*2., 0., 1.);
  float atm = exp(-max(edge, 0.) * 9.) * .85;
  if (rho > 1.) { o = vec4(sky + vec3(.32,.58,1.)*atm*smoothstep(0., .02, edge+.02), 1.); return; }
  float cosc = sqrt(max(0., 1. - rho*rho));
  float sp = sin(ph0), cp = cos(ph0);
  float lat = asin(clamp(cosc*sp + y*cp, -1., 1.));
  float lonr = atan(x, cp*cosc - y*sp);            // longitude relative to the centre: continuous on screen
  float lon = l0 + lonr;
  float lonD = lon * 57.2957795, latD = lat * 57.2957795, lonrD = lonr*57.2957795;
  // base texture with gradients from the continuous coordinate (no seam at the date line)
  vec2 ub = vec2(fract((lonD + 180.)/360.), (90. - latD)/180.);
  vec2 uc = vec2(lonrD/360., (90. - latD)/180.);
  vec3 c = textureGrad(base, ub, dFdx(uc), dFdy(uc)).rgb;
  // detail patches in the order drawGlobe chose (screen slopes taken here, outside the branch)
  vec2 gxl = vec2(dFdx(lonrD), dFdx(latD)), gyl = vec2(dFdy(lonrD), dFdy(latD));
  for (int i = 0; i < ${NP}; i++) {
    if (i >= np) break;
    vec4 b = bb[i]; float L = lonD; if (L < b.x - 180.) L += 360.; if (L > b.z + 180.) L -= 360.;
    if (L > b.x && L < b.z && latD > b.y && latD < b.w) {
      vec2 uv = vec2((L - b.x)/(b.z - b.x), (b.w - latD)/(b.w - b.y));
      vec2 sc = vec2(1./(b.z - b.x), -1./(b.w - b.y));
      vec2 gx = gxl*sc, gy = gyl*sc;
      vec3 s = i==0 ? textureGrad(p0, uv, gx, gy).rgb : i==1 ? textureGrad(p1, uv, gx, gy).rgb : i==2 ? textureGrad(p2, uv, gx, gy).rgb : textureGrad(p3, uv, gx, gy).rgb;
      float m = min(min(uv.x, 1.-uv.x), min(uv.y, 1.-uv.y));
      c = mix(c, s, smoothstep(0., .015, m)); break;
    }
  }
  // even out the ocean mosaic: dark bluish water -> one deep sea colour (shallows and land untouched)
  float lu = dot(c, vec3(.299,.587,.114));
  float land = texture(mask, vec2(gl_FragCoord.x/res.x, 1. - gl_FragCoord.y/res.y)).r;   // Natural Earth land, drawn each frame
  float water = (1. - land) * smoothstep(.42, .2, lu);
  vec3 sea = look > .5 ? vec3(.10,.34,.41) * (.55 + 2.9*lu) : vec3(.035,.10,.20) * (.8 + 1.4*lu);
  c = mix(c, sea, water*ocean);
  // grade: a touch more saturation, then dim so graphics pop
  float g = dot(c, vec3(.299,.587,.114)); c = mix(vec3(g), c, sat); c *= dimv;
  if (look > .5) c = pow(c, vec3(1.18)) * .97;   // atlas: calmer desert, a little more contrast
  // v5 worlds: the new style sweeps in across the frame (reading direction) behind a bright edge
  float wx = stRtl > .5 ? 1. - gl_FragCoord.x/res.x : gl_FragCoord.x/res.x;
  float wm = 1. - smoothstep(stP - .035, stP + .035, wx);
  vec3 sw = mix(stA, stB, wm);
  if (sw.x + sw.y + sw.z > .001) {
    float g2 = dot(c, vec3(.299,.587,.114));
    vec3 flatc = mix(vec3(.50,.74,.86)*(.92 + .25*lu), vec3(.96,.90,.76)*(.80 + .45*g2), land);
    vec3 sep = vec3(g2*1.12 + .06, g2*.93 + .035, g2*.70 + .01) * (land > .5 ? 1. : .82);
    vec3 nig = mix(c*vec3(.34,.42,.66)*.8, vec3(.012,.03,.07), (1. - land)*.7);
    c = mix(c, flatc, sw.x); c = mix(c, sep, sw.y); c = mix(c, nig, sw.z);
  }
  float ed = exp(-pow((wx - stP)/.007, 2.)) * step(.001, distance(stA, stB)) * step(-.01, stP) * step(stP, 1.01);
  c += vec3(1., .9, .62) * ed * .85;
  // sphere shading + limb haze
  vec3 n = vec3(x, y, cosc); float lam = .78 + .32*dot(n, normalize(vec3(-.45,.5,.75)));
  float limb = pow(1. - cosc, 3.);
  c = c*mix(1., lam, clamp(1.4 - R/4000., 0., 1.)) + vec3(.3,.55,1.)*limb*.55;
  o = vec4(c, 1.);
}`;
function sh(type, src) { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; }
const prog = gl.createProgram(); gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS)); gl.linkProgram(prog);
if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
gl.useProgram(prog);
const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
const loc = gl.getAttribLocation(prog, 'p'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
const U = n => gl.getUniformLocation(prog, n);
const aniso = gl.getExtension('EXT_texture_filter_anisotropic');
function tex(img, wrap) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, img); gl.generateMipmap(gl.TEXTURE_2D);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, wrap ? gl.REPEAT : gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  if (aniso) gl.texParameterf(gl.TEXTURE_2D, aniso.TEXTURE_MAX_ANISOTROPY_EXT, 8);
  return t;
}
const BASE = tex(await loadImg(CFG.globe || '/cache/globe-8k.jpg'), true);
const PT = PATCHES.map(p => ({ ...p, tex: null }));   // v7: loaded on demand (NEED / PLAN below drawGlobe)
gl.viewport(0, 0, W, H);
const MK = document.createElement('canvas'); MK.width = W / 4; MK.height = H / 4; const MX = MK.getContext('2d');
const MASK = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, MASK);
for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
function landMaskCPU() {   // white land on black, quarter resolution, same projection (the v6 path: 73 ms a frame on globe shots; kept for CFG.mask = 'cpu' and checks)
  MX.setTransform(1, 0, 0, 1, 0, 0); MX.fillStyle = '#000'; MX.fillRect(0, 0, MK.width, MK.height); MX.setTransform(.25, 0, 0, .25, 0, 0); MX.fillStyle = '#fff';
  const lon = C.l0 / D2R, lat = C.p0 / D2R, hx = Math.min(W / C.k, 3.2) / D2R / Math.max(.15, C.cp), hy = Math.min(H / C.k * 1.2, 3.2) / D2R;
  for (const c of WORLD.countries) {
    if (C.span < 60 && (c.bb[2] < lon - hx || c.bb[0] > lon + hx || c.bb[3] < lat - hy || c.bb[1] > lat + hy)) continue;
    MX.fill(countryPath([c], 4).p, 'nonzero');
  }
  gl.activeTexture(gl.TEXTURE7); gl.bindTexture(gl.TEXTURE_2D, MASK); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, MK); gl.uniform1i(U('mask'), 7);
}
// v7 (speed plan item 3, 2026-11-17): the same mask filled on the GPU. Every coastline point (548k) is uploaded once; a vertex
// shader projects it exactly like P() (points behind the globe pushed to the horizon), each ring is drawn as a triangle fan
// into the stencil (INCR on front faces, DECR on back faces = the nonzero rule; all outer rings of world.json share one
// winding and the 19 holes the other, so one pass over all countries equals filling each country on its own), with 8x MSAA
// at quarter resolution for the same soft coverage edge as Canvas2D. Nothing is read back: the resolved texture is the mask.
const LMK = (() => {
  let nv = 0, ni = 0; for (const c of WORLD.countries) for (const r of c.r) { const n = r.length / 2; nv += n; if (n >= 3) ni += (n - 2) * 3; }
  const V = new Float32Array(nv * 2), I = new Uint32Array(ni); let v = 0, k = 0;
  for (const c of WORLD.countries) for (const r of c.r) {
    const n = r.length / 2, b = v; for (let i = 0; i < r.length; i++) V[v * 2 + i] = r[i] / 1e4; v += n;
    for (let i = 1; i < n - 1; i++) { I[k++] = b; I[k++] = b + i; I[k++] = b + i + 1; }
  }
  const mw = W / 4, mh = H / 4;
  const vs = `#version 300 es
  in vec2 ll; uniform float l0d; uniform float p0d; uniform float sp0; uniform float cp0; uniform float k; uniform vec2 ctr; uniform vec2 res;
  float S(float a){ return abs(a) < .05 ? a*(1. - a*a/6.*(1. - a*a/20.)) : sin(a); }   // exact for the tiny angles of close shots
  void main(){
    float l = (ll.x - l0d) * .0174532925199, d = (ll.y - p0d) * .0174532925199, p = ll.y * .0174532925199;
    float cp = cos(p), s2 = S(l * .5), h = 2.*s2*s2;           // 1 - cos(l), without the cancellation
    float x = cp*S(l), y = S(d) + sp0*cp*h;                       // = cos(p0) sin(p) - sin(p0) cos(p) cos(l)
    float cosc = cos(d) - cp0*cp*h;                               // = sin(p0) sin(p) + cos(p0) cos(p) cos(l)
    if (cosc < 0.) { float r = length(vec2(x, y)); if (r > 0.) { x /= r; y /= r; } }
    vec2 s = ctr + vec2(x, -y) * k;                               // screen pixels, as P()
    gl_Position = vec4(s.x / res.x * 2. - 1., s.y / res.y * 2. - 1., 0., 1.);   // row 0 = top, like the canvas upload
  }`;
  const fs = `#version 300 es
  precision mediump float; out vec4 o; void main(){ o = vec4(1.); }`;
  const fvs = `#version 300 es
  in vec2 p; void main(){ gl_Position = vec4(p, 0., 1.); }`;
  const mk = (a, b) => { const q = gl.createProgram(); gl.attachShader(q, sh(gl.VERTEX_SHADER, a)); gl.attachShader(q, sh(gl.FRAGMENT_SHADER, b)); gl.linkProgram(q); if (!gl.getProgramParameter(q, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(q)); return q; };
  const pf = mk(vs, fs), pq = mk(fvs, fs);
  const va = gl.createVertexArray(); gl.bindVertexArray(va);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, V, gl.STATIC_DRAW);
  const la = gl.getAttribLocation(pf, 'll'); gl.enableVertexAttribArray(la); gl.vertexAttribPointer(la, 2, gl.FLOAT, false, 0, 0);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, I, gl.STATIC_DRAW);
  const vq = gl.createVertexArray(); gl.bindVertexArray(vq);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  const qa = gl.getAttribLocation(pq, 'p'); gl.enableVertexAttribArray(qa); gl.vertexAttribPointer(qa, 2, gl.FLOAT, false, 0, 0);
  gl.bindVertexArray(null); gl.bindBuffer(gl.ARRAY_BUFFER, buf);   // the globe's own attribute setup lives in the default vertex array
  const ns = Math.min(8, gl.getParameter(gl.MAX_SAMPLES));
  const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
  const rc = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, rc); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, ns, gl.RGBA8, mw, mh); gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.RENDERBUFFER, rc);
  const rs = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, rs); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, ns, gl.DEPTH24_STENCIL8, mw, mh); gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_STENCIL_ATTACHMENT, gl.RENDERBUFFER, rs);
  if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('land mask framebuffer incomplete');
  const GT = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, GT); gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, mw, mh);
  for (const [a, b] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, a, b);
  const rfb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, rfb); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, GT, 0);
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  const u = n => gl.getUniformLocation(pf, n);
  return { GT, rfb, mw, mh, ns, draw() {
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb); gl.viewport(0, 0, mw, mh);
    gl.clearColor(0, 0, 0, 1); gl.clearStencil(0); gl.stencilMask(0xff); gl.clear(gl.COLOR_BUFFER_BIT | gl.STENCIL_BUFFER_BIT);
    gl.enable(gl.STENCIL_TEST); gl.disable(gl.CULL_FACE); gl.colorMask(false, false, false, false);
    gl.stencilFunc(gl.ALWAYS, 0, 0xff); gl.stencilOpSeparate(gl.FRONT, gl.KEEP, gl.KEEP, gl.INCR_WRAP); gl.stencilOpSeparate(gl.BACK, gl.KEEP, gl.KEEP, gl.DECR_WRAP);
    gl.useProgram(pf); gl.bindVertexArray(va);
    gl.uniform1f(u('l0d'), C.l0 / D2R); gl.uniform1f(u('p0d'), C.p0 / D2R); gl.uniform1f(u('sp0'), C.sp); gl.uniform1f(u('cp0'), C.cp); gl.uniform1f(u('k'), C.k);
    gl.uniform2f(u('ctr'), C.cx, C.cy); gl.uniform2f(u('res'), W, H);
    gl.drawElements(gl.TRIANGLES, I.length, gl.UNSIGNED_INT, 0);
    gl.colorMask(true, true, true, true); gl.stencilFunc(gl.NOTEQUAL, 0, 0xff); gl.stencilOp(gl.KEEP, gl.KEEP, gl.KEEP);
    gl.useProgram(pq); gl.bindVertexArray(vq); gl.drawArrays(gl.TRIANGLES, 0, 3);
    gl.disable(gl.STENCIL_TEST); gl.bindVertexArray(null);
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, fb); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, rfb);   // resolve the 8 samples into the mask texture
    gl.blitFramebuffer(0, 0, mw, mh, 0, 0, mw, mh, gl.COLOR_BUFFER_BIT, gl.NEAREST);
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, null);
    gl.useProgram(prog); gl.viewport(0, 0, W, H);
    gl.activeTexture(gl.TEXTURE7); gl.bindTexture(gl.TEXTURE_2D, GT); gl.uniform1i(U('mask'), 7);
  } };
})();
const landMask = () => CFG.mask === 'cpu' || window.MASK_CPU ? landMaskCPU() : LMK.draw();
function pickPatches() {   // the patches this camera (C) draws with, in the order the shader tries them
  // patches that matter for this frame: those that intersect the view and are sharper than the base, finest first
  const hx = Math.min(W / 2 / C.k, 1.6) / D2R / Math.max(.2, C.cp), hy = Math.min(H / C.k, 1.6) / D2R;
  const lon = C.l0 / D2R, lat = C.p0 / D2R;
  const vis = PT.filter(p => p.bb[0] < lon + hx && p.bb[2] > lon - hx && p.bb[1] < lat + hy && p.bb[3] > lat - hy && p.d > 1304 * 1.1), need = C.k * (CFG.quality || 1);
  // v6: a patch that is sharp enough for this zoom goes first, the widest of them first (it usually covers the whole view);
  // then the best of the too-coarse ones. (Finest-first left most of the frame on the blurry globe once a story had many patches.)
  return vis.filter(p => p.d >= need).sort((a, b) => a.d - b.d).concat(vis.filter(p => p.d < need).sort((a, b) => b.d - a.d)).slice(0, NP);
}
// v7 (speed plan item 4, 2026-11-17): a page loads only the patches its own frames draw with, decoded off the main thread,
// and frees each one after the last frame that uses it. Every worker used to hold all of them (21 x ~85 MB of VRAM with
// mipmaps for v6: 4 workers filled the 8 GB card and RAM fell to 0.2 GB). render.mjs calls PLAN(times) once for its range,
// then awaits NEED(t) before every seek(t). Same JPEG, same texture, same mipmaps: the pixels do not change.
async function loadPatch(p) {
  if (p.tex) return; if (!p.busy) p.busy = (async () => {
    const bm = await createImageBitmap(await (await fetch('img/' + p.src)).blob());
    p.tex = tex(bm, false); bm.close(); p.busy = null; PT_STAT.loaded++; PT_STAT.live = PT.filter(q => q.tex).length; PT_STAT.peak = Math.max(PT_STAT.peak, PT_STAT.live);
  })(); return p.busy;
}
const PT_STAT = { loaded: 0, freed: 0, live: 0, peak: 0 };
window.PLAN = ts => { for (const t of ts) { setCam(camFinal(t)); for (const p of pickPatches()) { p.first = Math.min(p.first ?? 1e9, t); p.last = Math.max(p.last ?? -1, t); } } return PT.filter(p => p.last != null).length; };
window.NEED = async t => {
  setCam(camFinal(t)); const use = pickPatches();
  for (const p of PT) if (p.first != null && p.first <= t + 1 && p.last >= t && !p.tex) loadPatch(p);   // look ahead 1 s, not awaited
  await Promise.all(use.map(loadPatch));
  for (const p of PT) if (p.tex && p.last != null && p.last < t) { gl.deleteTexture(p.tex); p.tex = null; PT_STAT.freed++; }
};
window.PT_STAT = PT_STAT;
function drawGlobe(t) {
  landMask();
  const use = pickPatches();
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, BASE); gl.uniform1i(U('base'), 0);
  const bb = new Float32Array(NP * 4);
  for (const p of use) if (!p.tex) throw new Error(`satellite patch ${p.src} is not loaded at ${t.toFixed(3)} s: await window.NEED(t) before seek(t)`);
  use.forEach((p, i) => { gl.activeTexture(gl.TEXTURE1 + i); gl.bindTexture(gl.TEXTURE_2D, p.tex); bb.set(p.bb, i * 4); });
  for (let i = 0; i < NP; i++) gl.uniform1i(U('p' + i), 1 + i);
  gl.uniform4fv(U('bb'), bb); gl.uniform1i(U('np'), use.length);
  gl.uniform2f(U('res'), W, H); gl.uniform2f(U('ctr'), C.cx, C.cy); gl.uniform1f(U('R'), C.k);
  gl.uniform1f(U('l0'), C.l0); gl.uniform1f(U('ph0'), C.p0); gl.uniform1f(U('t'), t); gl.uniform1f(U('roll'), 0);
  const sty = styleAt(t); gl.uniform3fv(U('stA'), sty.A); gl.uniform3fv(U('stB'), sty.B); gl.uniform1f(U('stP'), sty.p); gl.uniform1f(U('stRtl'), RTL ? 1 : 0);
  gl.uniform1f(U('look'), LOOK === 'atlas' ? 1 : 0); gl.uniform1f(U('ocean'), CFG.ocean ?? (LOOK === 'atlas' ? .9 : .8)); gl.uniform1f(U('dimv'), dimAt(t)); gl.uniform1f(U('sat'), CFG.sat ?? (LOOK === 'atlas' ? .86 : 1.12));
  gl.drawArrays(gl.TRIANGLES, 0, 3);
}

// ------------------------------------------------------------------ 2D layer
const cv = document.getElementById('c'), X = cv.getContext('2d');
const ITEMS = [];   // everything the story adds: {kind, t0, t1, draw(ctx, t)}
const add = (o) => { ITEMS.push(o); return o; };
const DIMS = [];
export function dim(t0, v = .72, t1 = null, dur = .5) { DIMS.push({ t0: when(t0), t1: t1 == null ? 1e9 : when(t1), v, dur }); }
function dimAt(t) { let v = CFG.dim ?? (LOOK === 'atlas' ? .95 : .9); for (const d of DIMS) if (t >= d.t0 && t < d.t1 + d.dur) { const a = clamp((t - d.t0) / d.dur) * (1 - clamp((t - d.t1) / d.dur)); v = lerp(v, d.v, EASE.sine(a)); } return v; }
const life = (o, t) => {   // 0..1 entrance, then 1, then exit 1..0 ; returns [in, out]
  const a = clamp((t - o.t0) / (o.dur || .45)), b = o.t1 == null ? 1 : 1 - clamp((t - o.t1) / (o.outDur || .35)); return [a, b];
};
function strokeText(x, y, s, font, fill, stroke = 'rgba(0,0,0,.6)', lw = 10, align = 'center') {
  X.font = font; X.textAlign = align; X.textBaseline = 'middle'; X.direction = RTL ? 'rtl' : 'ltr'; X.lineJoin = 'round';
  if (stroke) { X.strokeStyle = stroke; X.lineWidth = lw; X.strokeText(s, x, y); } X.fillStyle = fill; X.fillText(s, x, y);
}
// ---- type system (v4): six sizes only, fixed places, fixed weights
export const TYPE = { hero: 190, title: 140, sub: 110, line: 54, label: 50, caption: 78 };
export const TOP = 360, DOCK = 205;   // centre line of titles/numbers, and of the docked opening question
const WT = { Cairo: 900, Alexandria: 600, MapAr: 600, MapArText: 500 };   // Cairo Black titles/numbers, Alexandria SemiBold labels, Medium sub-lines
const fontAr = (px, f = 'MapAr', w) => `${w ?? WT[f] ?? 400} ${px}px ${f}, MapLat`;
const snap = (s, list) => list.reduce((b, v) => Math.abs(v - s) < Math.abs(b - s) ? v : b);
const hh = n => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };   // deterministic noise (frames render in any order)
// engraved text: gradient fill, double dark rim (black outside, a deep tone of the colour inside), deep drop shadow
const GRAD = { '#ffd23f': ['#fff7c9', '#ffd23f', '#e89a00', '#4a2f00'], '#ff4d4d': ['#ffd0d0', '#ff4d4d', '#c0141c', '#45040a'], '#ff4757': ['#ffd0d4', '#ff4757', '#c0141c', '#45040a'],
  '#ff7a1a': ['#ffe3bd', '#ff9a3c', '#d9480f', '#4a1600'], '#ffffff': ['#ffffff', '#f2f5f9', '#c3cedb', '#18212e'], '#2fd6c8': ['#d9fffb', '#2fd6c8', '#0e8f86', '#03302c'] };
const tone = c => GRAD[c] || [c, c, c, 'rgba(0,0,0,.9)'];
function engraved(x, y, s, px, color, fam = 'Cairo', align = 'center', glow = 0, ctx = X) {
  const X = ctx;
  const g = tone(color); X.font = fontAr(px, fam); X.textAlign = align; X.textBaseline = 'middle'; X.direction = RTL ? 'rtl' : 'ltr'; X.lineJoin = 'round';
  X.save(); X.shadowColor = glow ? rgba(g[1].startsWith('#') ? g[1] : '#ffd23f', .9 * glow) : 'rgba(0,0,0,.78)'; X.shadowBlur = glow ? px * .45 * glow + px * .2 : px * .2; X.shadowOffsetY = glow ? 0 : px * .07;
  X.strokeStyle = 'rgba(0,0,0,.92)'; X.lineWidth = px * .15; X.strokeText(s, x, y); X.restore();
  X.strokeStyle = g[3]; X.lineWidth = px * .07; X.strokeText(s, x, y);
  const gr = X.createLinearGradient(0, y - px * .48, 0, y + px * .42); gr.addColorStop(0, g[0]); gr.addColorStop(.5, g[1]); gr.addColorStop(1, g[2]);
  X.fillStyle = gr; X.fillText(s, x, y);
}
// glass card: the picture behind is blurred, a dark tint on top, a light sheen and a thin bright edge. (x, y) = centre, screen pixels.
const GC = document.createElement('canvas'), GX = GC.getContext('2d');
function glass(x, y, w, h, r, tint = 'rgba(12,18,30,.5)', edge = 'rgba(255,255,255,.45)') {
  const pad = 24, x0 = Math.max(0, Math.floor(x - w / 2 - pad)), y0 = Math.max(0, Math.floor(y - h / 2 - pad)), x1 = Math.min(W, Math.ceil(x + w / 2 + pad)), y1 = Math.min(H, Math.ceil(y + h / 2 + pad));
  if (x1 <= x0 || y1 <= y0) return;
  const a = X.globalAlpha; X.save(); X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = a;
  X.save(); X.shadowColor = 'rgba(0,0,0,.42)'; X.shadowBlur = 26; X.shadowOffsetY = 8; rr(x - w / 2, y - h / 2, w, h, r); X.fillStyle = tint; X.fill(); X.restore();
  if (GC.width < x1 - x0 || GC.height < y1 - y0) { GC.width = Math.max(GC.width, x1 - x0); GC.height = Math.max(GC.height, y1 - y0); }
  GX.clearRect(0, 0, GC.width, GC.height); GX.drawImage(cv, x0, y0, x1 - x0, y1 - y0, 0, 0, x1 - x0, y1 - y0);
  rr(x - w / 2, y - h / 2, w, h, r); X.clip(); X.filter = 'blur(12px)'; X.drawImage(GC, 0, 0, x1 - x0, y1 - y0, x0, y0, x1 - x0, y1 - y0); X.filter = 'none';
  X.fillStyle = tint; X.fill();
  const sh = X.createLinearGradient(0, y - h / 2, 0, y + h / 2); sh.addColorStop(0, 'rgba(255,255,255,.2)'); sh.addColorStop(.45, 'rgba(255,255,255,.03)'); sh.addColorStop(1, 'rgba(255,255,255,0)'); X.fillStyle = sh; X.fill();
  X.restore();
  X.save(); X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = a; rr(x - w / 2 + .75, y - h / 2 + .75, w - 1.5, h - 1.5, r); X.lineWidth = 1.6; X.strokeStyle = edge; X.stroke(); X.restore();
}
// ---- layout: every label/pill claims a box per frame; a later one slides up/down or hides instead of overlapping
// titles, numbers and captions reserve their bands first, nothing is drawn outside the safe area (TikTok/Shorts UI)
let BOXES = []; export const SAFE = { l: 30, r: 30, t: 110, b: 330 };
const hitBox = b => BOXES.some(q => b[0] < q[2] && b[2] > q[0] && b[1] < q[3] && b[3] > q[1]);
export function reserve(x0, y0, x1, y1) { const b = [x0, y0, x1, y1]; BOXES.push(b); return b; }   // b.ink = [x0, x1] of the real text (titles, numbers), for the logo
function place(x, y, w, h) {
  if (x < -w / 2 || x > W + w / 2 || y < -h || y > H + h) return null;   // its place is off screen: do not drag it in
  x = clamp(x, SAFE.l + w / 2, W - SAFE.r - w / 2);
  for (const k of [0, -1, 1, -2, 2, -3]) {
    const yy = y + k * h * 1.12; if (yy - h / 2 < SAFE.t || yy + h / 2 > H - SAFE.b) continue;
    const b = [x - w / 2 - 8, yy - h / 2 - 5, x + w / 2 + 8, yy + h / 2 + 5]; if (!hitBox(b)) { BOXES.push(b); return [x, yy]; }
  }
  return null;
}
function rr(x, y, w, h, r) { X.beginPath(); X.moveTo(x + r, y); X.arcTo(x + w, y, x + w, y + h, r); X.arcTo(x + w, y + h, x, y + h, r); X.arcTo(x, y + h, x, y, r); X.arcTo(x, y, x + w, y, r); X.closePath(); }
const pillSize = (s, px) => { X.font = fontAr(px); return [X.measureText(s).width + px * 1.0, px * 1.45]; };
// a glass capsule (v4): blurred map behind, tinted with bg, thin bright edge. Returns its box, or null when there is no room.
function pill(x, y, s, bg, fg = '#fff', px = TYPE.label, a = 1, sc = 1, free = false) {
  const [w, h] = pillSize(s, px);
  if (!free) { const q = place(x, y, w, h); if (!q) return null; [x, y] = q; }
  if (a <= 0 || sc <= 0) return [x, y, w, h];
  X.save(); X.globalAlpha *= a;
  glass(x, y, w * sc, h * sc, h * sc / 2, bg && bg.startsWith('#') ? rgba(bg, .58) : (bg || 'rgba(12,18,30,.5)'));
  X.translate(x, y); X.scale(sc, sc); X.shadowColor = 'rgba(0,0,0,.6)'; X.shadowBlur = 8; X.shadowOffsetY = 2;
  strokeText(0, px * .04, s, fontAr(px), fg, null); X.restore();
  return [x, y, w, h];
}
// leader(): a pulsing dot on the exact spot, a thin line up to the capsule
function leader(ax, ay, px, py, ph, c, a, t) {
  X.save(); X.globalAlpha *= a; X.lineCap = 'round';
  const ey = py + (ay > py ? ph / 2 : -ph / 2), L = Math.hypot(px - ax, ey - ay), e = EASE.out(clamp(a * 1.4));
  X.strokeStyle = 'rgba(0,0,0,.45)'; X.lineWidth = 6; X.beginPath(); X.moveTo(ax, ay); X.lineTo(lerp(ax, px, e), lerp(ay, ey, e)); X.stroke();
  X.strokeStyle = 'rgba(255,255,255,.92)'; X.lineWidth = 2.6; X.stroke();
  for (let k = 0; k < 2; k++) { const u = (t * .8 + k * .5) % 1; X.globalAlpha = a * (1 - u) * .7; X.strokeStyle = c; X.lineWidth = 3.5; X.beginPath(); X.arc(ax, ay, 9 + u * 34, 0, 7); X.stroke(); }
  X.globalAlpha = a; X.shadowColor = c; X.shadowBlur = 14; X.fillStyle = c; X.beginPath(); X.arc(ax, ay, 8 * EASE.back(clamp(a * 2)), 0, 7); X.fill();
  X.shadowBlur = 0; X.fillStyle = '#fff'; X.beginPath(); X.arc(ax, ay, 3.2, 0, 7); X.fill(); X.restore();
  return L;
}

// country rings -> one Path2D in screen space for this frame (points closer than 1.2 px are skipped)
function countryPath(cs, step = 1.2) {
  const p = new Path2D(); let n = 0, x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9, len = 0;
  const vis = [C.l0 / D2R, C.p0 / D2R], hx = Math.min(W / C.k, 3.2) / D2R / Math.max(.15, C.cp), hy = Math.min(H / C.k * 1.2, 3.2) / D2R;
  for (const c of cs) for (const r of c.r) {
    let rx0 = 1e9, rx1 = -1e9, ry0 = 1e9, ry1 = -1e9;
    for (let i = 0; i < r.length; i += 2) { const a = r[i] / 1e4, b = r[i + 1] / 1e4; if (a < rx0) rx0 = a; if (a > rx1) rx1 = a; if (b < ry0) ry0 = b; if (b > ry1) ry1 = b; }
    if (C.span < 60 && (rx1 < vis[0] - hx || rx0 > vis[0] + hx || ry1 < vis[1] - hy || ry0 > vis[1] + hy)) continue;
    let lx = null, ly = null, fx, fy;
    for (let i = 0; i < r.length; i += 2) {
      const [x, y] = P(r[i] / 1e4, r[i + 1] / 1e4);
      if (lx === null) { p.moveTo(x, y); fx = x; fy = y; lx = x; ly = y; continue; }
      const dd = Math.abs(x - lx) + Math.abs(y - ly); if (dd < step && i < r.length - 2) continue;
      p.lineTo(x, y); len += dd; lx = x; ly = y; n++;
      if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
    }
    p.closePath();
  }
  return { p, box: [x0, y0, x1, y1], len, n };
}

// ------------------------------------------------------------------ elements
const STYLE = {
  // hue: recolours the satellite picture (relief stays visible); fill: a light coat on top
  tint: { hue: 'rgba(40,140,255,.75)', fill: 'rgba(40,140,255,.22)', line: 'rgba(255,255,255,.95)', lw: 3 },
  solid: { hue: 'rgba(255,150,30,.9)', fill: 'rgba(255,150,30,.42)', line: '#fff', lw: 3.5 },
  flat: { fill: 'rgba(255,154,31,.9)', line: '#fff', lw: 3.5 },
  glow: { hue: 'rgba(255,120,26,.9)', fill: 'rgba(255,122,26,.55)', line: '#ffd9a8', lw: 3, glow: '#ff7a1a' },
  outline: { fill: null, line: '#ffd23f', lw: 5 },
  zone: { fill: null, line: '#fff', lw: 3 },
  // house palette for land (blue is reserved for water and routes): light coat + glowing edge, the satellite texture stays visible
  gold: { hue: 'rgba(255,210,63,.38)', fill: 'rgba(255,210,63,.09)', line: '#ffd23f', lw: 4, glow: '#ffd23f' },
  danger: { hue: 'rgba(255,71,87,.3)', fill: 'rgba(255,71,87,.08)', line: '#ff4757', lw: 4, glow: '#ff4757' },
  ally: { hue: 'rgba(47,214,200,.28)', fill: 'rgba(47,214,200,.07)', line: '#2fd6c8', lw: 4, glow: '#2fd6c8' },
  soft: { hue: 'rgba(255,255,255,.35)', fill: 'rgba(255,255,255,.08)', line: 'rgba(255,255,255,.9)', lw: 3 },
};
// atlas colours (raised, textured): region(..., {style:'red'}) ; neon zones: style:'neon' (green) / 'neonRed' / {style:'neon', color}
export const ATLAS = { red: '#c8352b', blue: '#2f62d0', green: '#3fae3a', orange: '#e3901e', purple: '#8b5cf6', yellow: '#e8c21e', teal: '#14b8a6', pink: '#ec4899' };
for (const [k, h] of Object.entries(ATLAS)) STYLE[k] = { hue: rgba(h, .9), mul: rgba(h, .62), fill: rgba(h, .16), line: 'rgba(255,255,255,.75)', lw: 2.2, raised: true };
const neon = h => ({ fill: rgba(h, .42), hue: rgba(h, .6), line: h, lw: 5, glow: h, neon: true });
STYLE.neon = neon('#39ff6a'); STYLE.neonRed = neon('#ff2d3c'); STYLE.neonBlue = neon('#38bdf8');
// region(['IRN'], {at:'إيران', style:'tint', reveal:'wipe', label:true, labelAt:[lon,lat], spotlight:.5, until:'...'})
export function region(names, o = {}) {
  const cs = [].concat(names).map(n => { const c = country(n); if (!c) throw new Error('unknown country: ' + n); return c; });
  let st = o.style === 'neon' && o.color ? neon(col(o.color)) : o.style === 'vivid' ? vivid(col(o.color || 'red'), o.opacity) : { ...(STYLE[o.style || 'tint']), ...(o.fill ? { fill: col(o.fill) } : {}), ...(o.color ? { fill: rgba(col(o.color), o.opacity ?? .85) } : {}) };
  // user rule: a flag can never land on the wrong country. flag:true takes the country's own code; an explicit code must match it.
  if (o.flag && o.flag !== true) { const want = ISO2[cs[0].iso]; if (cs.length > 1 || !want || String(o.flag).toLowerCase() !== want) throw new Error(`flag '${o.flag}' does not belong to ${cs.map(c => c.iso).join('+')}`); }
  if (o.flag && cs.length > 1) throw new Error('one flag per region() call: ' + cs.map(c => c.iso).join('+'));
  if (o.flag && !ISO2[cs[0].iso]) throw new Error('no flag mapped for ' + cs[0].iso + ' (add it to ISO2 and engine/assets/flags)');
  const fl = o.flag ? asset(flagSrc(cs[0].iso)) : null;
  if (fl) st = { line: '#ffffff', lw: 4.5, glow: '#ffffff', vivid: true };
  if (st.vivid && !o.reveal) o = { ...o, reveal: 'flash' };
  const it = add({ kind: 'region', codes: cs, shown: !!(fl || (o.label !== false && o.label != null)), say: o.say, flag: fl ? cs[0].iso : null, name: fl ? 'flag' : st.vivid ? 'vivid' : undefined, t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? (o.reveal === 'draw' ? .9 : .6), z: o.z ?? 1, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return;
      const { p, box, len } = countryPath(cs); const e = EASE.out(a);
      if (o.spotlight) { X.save(); X.fillStyle = `rgba(2,6,14,${o.spotlight * e * b})`; const sp = new Path2D(); sp.rect(0, 0, W, H); sp.addPath(p); X.fill(sp, 'evenodd'); X.restore(); }
      X.save(); X.globalAlpha = b;
      const rev = o.reveal || (st.raised ? 'soft' : st.neon ? 'grow' : 'wipe');
      if (rev === 'wipe') { const w = box[2] - box[0] + 40; X.beginPath(); if (RTL) X.rect(box[2] + 20 - w * e, 0, w * e + 1, H); else X.rect(box[0] - 20, 0, w * e, H); X.clip(); }
      if (rev === 'fade' || rev === 'flash') X.globalAlpha *= rev === 'flash' ? clamp(a * 5) : e;
      const sa = rev === 'soft' ? EASE.sine(clamp((t - this.t0) / .9)) : 1;   // soft: colour comes up over 0.9 s
      if (rev === 'grow') { const cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2, s = EASE.back(a); X.globalAlpha *= clamp(a * 3); X.translate(cx, cy); X.scale(s, s); X.translate(-cx, -cy); }
      const wk = rev === 'flash' ? 1 - EASE.out(clamp((t - this.t0 - .1) / .55)) : 0;   // white-hot, then cools to the colour
      if (rev === 'pop') { const cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2, s = lerp(.92, 1, EASE.back(a)); X.globalAlpha *= clamp(a * 3); X.translate(cx, cy); X.scale(s, s); X.translate(-cx, -cy); }
      const fa = rev === 'draw' ? clamp((a - .55) / .45) : sa;
      const fk = o.fade ? lerp(1, o.fade[1], EASE.sine(clamp((t - when(o.fade[0])) / .6))) : 1;   // fade: [time, how much stays]
      if (st.raised) { X.save(); X.shadowColor = 'rgba(0,0,0,.5)'; X.shadowBlur = 26; X.shadowOffsetY = 9; X.globalAlpha *= fa * fk; X.fillStyle = 'rgba(0,0,0,.14)'; X.fill(p, 'evenodd'); X.restore(); }
      if (st.hue) { X.save(); X.globalCompositeOperation = 'color'; X.globalAlpha *= fa * fk * (1 - wk * .9); X.fillStyle = st.hue; X.fill(p, 'evenodd'); X.restore(); }
      if (st.mul) { X.save(); X.globalCompositeOperation = 'multiply'; X.globalAlpha *= fa * fk * (1 - wk); X.fillStyle = st.mul; X.fill(p, 'evenodd'); X.restore(); }
      if (st.fill) { X.save(); if (st.glow) { X.shadowColor = st.glow; X.shadowBlur = 50 * (o.pulse ? (.8 + .2 * Math.sin(t * 5)) : 1); } X.globalAlpha *= fa * fk * (1 - wk * .7); X.fillStyle = st.fill; X.fill(p, 'evenodd'); X.restore(); }
      if (fl && fl.img) { X.save(); X.clip(p, 'evenodd'); X.globalAlpha *= fa * fk * (o.flagAlpha ?? .92); drawFlag(fl, box, t, o.wave !== false); X.restore(); }
      if (wk > 0) { X.save(); X.globalAlpha *= wk * .9; X.fillStyle = '#fff8f0'; X.fill(p, 'evenodd'); X.restore(); }
      if (st.raised) { X.save(); X.clip(p, 'evenodd'); X.globalAlpha *= fa * fk; X.lineWidth = 14; X.strokeStyle = 'rgba(0,0,0,.22)'; X.stroke(p); X.lineWidth = 3; X.strokeStyle = 'rgba(255,255,255,.18)'; X.translate(0, 2); X.stroke(p); X.restore(); }
      X.globalAlpha *= Math.max(fk, .55); X.lineJoin = 'round'; X.lineWidth = o.lineWidth || st.lw; X.strokeStyle = st.line;
      if (st.neon) { X.shadowColor = st.glow; X.shadowBlur = 28; X.stroke(p); X.shadowBlur = 10; X.lineWidth = 2; X.strokeStyle = '#ffffff'; }
      if (st.vivid) { X.shadowColor = 'rgba(255,255,255,.95)'; X.shadowBlur = 22 + 26 * (1 - EASE.out(clamp((t - this.t0) / .8))); }
      if (rev === 'draw' || rev === 'soft') { X.setLineDash([len * clamp(rev === 'soft' ? (t - this.t0) / .7 : a / .7), len + 10]); }
      X.stroke(p); X.restore();
      if (o.label !== false && o.label != null) {
        const c = cs[0], txt = o.label === true ? (RTL ? c.ar : c.en) : o.label;
        const at = o.labelAt || [c.lx, c.ly]; const [x, y, front] = P(at[0], at[1]); if (!front) return;
        const la = clamp((t - this.t0 - (o.labelDelay ?? .25)) / .4); if (la <= 0) return;
        const lb = b * (o.labelUntil != null ? 1 - clamp((t - when(o.labelUntil)) / .3) : 1); if (lb <= 0) return;
        const sc = lerp(.6, 1, EASE.back(la));
        if (o.labelPill) pill(x, y + (o.labelDy || 0), txt, col(o.labelPill), '#fff', o.labelSize || 50, lb, sc);
        else { X.font = fontAr(o.labelSize || TYPE.label); const q = place(x, y + (o.labelDy || 0), X.measureText(txt).width + 24, (o.labelSize || TYPE.label) * 1.25); if (!q) return; X.save(); X.globalAlpha = lb * clamp(la * 2); X.translate(q[0], q[1]); X.scale(sc, sc); strokeText(0, 0, txt, fontAr(o.labelSize || TYPE.label), '#fff', 'rgba(0,0,0,.55)', 12); X.restore(); }
      }
    } });
  return it;
}
// smooth a lon/lat polyline (Catmull-Rom) into n points
export function smooth(pts, n = 80) {
  if (pts.length < 3) return pts; const out = [];
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)], m = Math.ceil(n / (pts.length - 1));
    for (let j = 0; j < m; j++) { const s = j / m, s2 = s * s, s3 = s2 * s;
      out.push([0, 1].map(k => .5 * (2 * p1[k] + (-p0[k] + p2[k]) * s + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * s3))); }
  }
  out.push(pts[pts.length - 1]); return out;
}
function screenLine(pts) { if (pts.length < 2) pts = [pts[0], pts[0]]; const s = pts.map(p => P(p[0], p[1])); let L = 0; const cum = [0]; for (let i = 1; i < s.length; i++) { L += Math.hypot(s[i][0] - s[i - 1][0], s[i][1] - s[i - 1][1]); cum.push(L); } return { s, cum, L }; }
function along(sl, d) { d = clamp(d, 0, sl.L); let i = 1; while (i < sl.cum.length - 1 && sl.cum[i] < d) i++; const a = sl.s[i - 1], b = sl.s[i], u = (d - sl.cum[i - 1]) / Math.max(1e-6, sl.cum[i] - sl.cum[i - 1]); return [lerp(a[0], b[0], u), lerp(a[1], b[1], u), Math.atan2(b[1] - a[1], b[0] - a[0])]; }
// path([[lon,lat],...], {at, dur, color, width, dash, glow, arrow, smooth:true, sea:true (a sea lane: it may not cross land)})
export function path(pts, o = {}) {
  const pp = o.smooth === false ? pts : smooth(pts, o.n || 120);
  return add({ kind: 'path', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? 1.0, z: o.z ?? 2, sfx: o.sfx, pts: pp, sea: !!o.sea, G: o.sea ? geoLine(pp) : null,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const sl = screenLine(pp), e = EASE.io(a), Lp = sl.L * e;
      X.save(); X.globalAlpha = b; X.lineCap = 'round'; X.lineJoin = 'round';
      const trace = () => { X.beginPath(); X.moveTo(sl.s[0][0], sl.s[0][1]); for (let i = 1; i < sl.s.length && sl.cum[i] <= Lp; i++) X.lineTo(sl.s[i][0], sl.s[i][1]); const q = along(sl, Lp); X.lineTo(q[0], q[1]); };
      const w = o.width || 9, c = col(o.color || 'yellow');
      if (o.glow !== false) { X.shadowColor = c; X.shadowBlur = 24; }
      if (o.dash) { X.setLineDash(o.dash); X.lineDashOffset = -t * (o.flow ?? 40); }
      X.strokeStyle = 'rgba(0,0,0,.35)'; X.lineWidth = w + 6; trace(); X.stroke();
      X.strokeStyle = c; X.lineWidth = w; trace(); X.stroke(); X.setLineDash([]); X.shadowBlur = 0;
      if (o.arrow && a > .05) { const [x, y, ang] = along(sl, Lp); X.translate(x, y); X.rotate(ang); X.fillStyle = c; X.beginPath(); X.moveTo(w * 2.4, 0); X.lineTo(-w * 1.2, w * 1.9); X.lineTo(-w * 1.2, -w * 1.9); X.closePath(); X.fill(); }
      X.restore();
    } });
}
// ships moving along a route: ships(pts, {at, count, speed (route lengths per second), size, color, until})
export function ships(pts, o = {}) {
  const pp = o.smooth === false ? pts : smooth(pts, 120), n = o.count || 3, sz = o.size || 1;
  const G = geoLine(pp);
  return add({ kind: 'ships', G, kmps: G.L * (o.speed || .06), t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: .4, z: o.z ?? 3, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const sl = screenLine(pp);
      for (let i = 0; i < n; i++) {
        const u = ((t - this.t0) * (o.speed || .06) + i / n + (o.phase || 0)) % 1, [x, y, ang] = along(sl, u * sl.L), fade = clamp(Math.min(u, 1 - u) * 12) * a * b;
        X.save(); X.globalAlpha = fade; X.translate(x, y); X.rotate(ang); X.scale(sz, sz);
        const g = X.createLinearGradient(-90, 0, -14, 0); g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(1, 'rgba(255,255,255,.55)');   // wake
        X.fillStyle = g; X.beginPath(); X.moveTo(-14, -7); X.lineTo(-90, -15); X.lineTo(-90, 15); X.lineTo(-14, 7); X.closePath(); X.fill();
        X.shadowColor = 'rgba(0,0,0,.6)'; X.shadowBlur = 8; X.shadowOffsetY = 3;
        X.fillStyle = col(o.color || '#c8352b'); X.beginPath(); X.moveTo(34, 0); X.quadraticCurveTo(30, -9, 20, -9); X.lineTo(-24, -9); X.quadraticCurveTo(-27, 0, -24, 9); X.lineTo(20, 9); X.quadraticCurveTo(30, 9, 34, 0); X.fill();   // hull
        X.shadowColor = 'transparent'; X.fillStyle = '#2a313b'; X.beginPath(); X.moveTo(27, 0); X.lineTo(19, -6); X.lineTo(-14, -6); X.lineTo(-14, 6); X.lineTo(19, 6); X.closePath(); X.fill();   // deck
        X.strokeStyle = '#8a929c'; X.lineWidth = 1.6; X.beginPath(); X.moveTo(-10, 0); X.lineTo(22, 0); for (let k = -6; k < 20; k += 7) { X.moveTo(k, -4); X.lineTo(k, 4); } X.stroke();   // pipes
        X.fillStyle = '#f2f2f2'; X.fillRect(-23, -7, 9, 14); X.fillStyle = '#1b1f26'; X.fillRect(-21, -5, 2, 10);   // bridge
        X.restore();
      }
    } });
}
// ruler([lon,lat],[lon,lat], {at, dur, text, color})  a measured line with end ticks and a label
export function ruler(a0, b0, o = {}) {
  return add({ kind: 'ruler', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .8, z: o.z ?? 4, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const A = P(...a0), B = P(...b0), e = EASE.io(a), c = col(o.color || 'white');
      const mx = (A[0] + B[0]) / 2, my = (A[1] + B[1]) / 2, ang = Math.atan2(B[1] - A[1], B[0] - A[0]), L = Math.hypot(B[0] - A[0], B[1] - A[1]);
      X.save(); X.globalAlpha = b; X.translate(mx, my); X.rotate(ang); X.lineCap = 'round';
      X.shadowColor = 'rgba(0,0,0,.6)'; X.shadowBlur = 10; X.strokeStyle = c; X.lineWidth = 6;
      X.beginPath(); X.moveTo(-L / 2 * e, 0); X.lineTo(L / 2 * e, 0); X.stroke();
      const tk = 26 * clamp(a * 3 - 1.4); X.beginPath(); X.moveTo(-L / 2, -tk); X.lineTo(-L / 2, tk); X.moveTo(L / 2, -tk); X.lineTo(L / 2, tk); X.stroke();
      X.fillStyle = c; const dr = 10 * EASE.back(clamp(a * 3 - 1.5)); if (dr > 0) for (const sx of [-L / 2, L / 2]) { X.beginPath(); X.arc(sx, 0, dr, 0, 7); X.fill(); }
      X.restore();
      if (o.text && a > .5) { const la = clamp((a - .5) / .5); const off = o.textOffset || [0, -70]; pill(mx + off[0], my + off[1], o.text, col(o.textBg || '#111a2b'), col(o.textColor || 'yellow'), o.textSize || TYPE.label, b, lerp(.6, 1, EASE.back(la))); }
    } });
}
// pin([lon,lat], {at, label, color})   ping([lon,lat], {at, color, until})
export function pin(pt, o = {}) {
  return add({ kind: 'pin', t0: early(o, 'label'), t1: o.until != null ? when(o.until) : null, dur: .45, z: o.z ?? 5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const [x, y] = P(...pt), s = EASE.back(a) * (o.size || 1), c = col(o.color || 'red');
      X.save(); X.globalAlpha = b; X.translate(x, y - (1 - EASE.out(a)) * 60); X.scale(s, s);
      X.shadowColor = 'rgba(0,0,0,.5)'; X.shadowBlur = 12; X.fillStyle = c; X.beginPath(); X.arc(0, -46, 24, Math.PI * .82, Math.PI * .18); X.lineTo(0, 0); X.closePath(); X.fill();
      X.shadowBlur = 0; X.fillStyle = '#fff'; X.beginPath(); X.arc(0, -46, 9, 0, 7); X.fill(); X.restore();
      if (o.label) pill(x, y - 110 * (o.size || 1) + (o.labelDy || 0), o.label, col(o.labelBg || '#111a2b'), '#fff', o.labelSize || TYPE.label, b * clamp(a * 2), lerp(.6, 1, EASE.back(clamp(a * 1.3))));
    } });
}
export function ping(pt, o = {}) {
  return add({ kind: 'ping', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: .3, z: o.z ?? 5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const [x, y] = P(...pt), c = col(o.color || 'yellow');
      X.save(); X.globalAlpha = b; X.fillStyle = c; X.shadowColor = 'rgba(0,0,0,.4)'; X.shadowBlur = 6; X.beginPath(); X.arc(x, y, 12 * EASE.back(a), 0, 7); X.fill(); X.shadowBlur = 0;
      for (let k = 0; k < 2; k++) { const u = ((t - this.t0) * .9 + k * .5) % 1; X.globalAlpha = b * (1 - u) * .55; X.strokeStyle = c; X.lineWidth = 5; X.beginPath(); X.arc(x, y, 14 + u * 80, 0, 7); X.stroke(); }
      X.restore();
    } });
}
// label(at, text, {at:time, size, color, pill, dy, anim:'ghost'|'bubble'})  at = [lon,lat] or {x,y}
export function label(pos, text, o = {}) {
  return add({ kind: 'label', place: Array.isArray(pos) ? { what: `label '${text}'`, pt: pos, in: o.in } : null, t0: early(o, 'label'), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .4, outDur: o.outDur ?? .35, z: o.z ?? 6, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const [x, y] = Array.isArray(pos) ? P(...pos) : [pos.x, pos.y];
      const anim = o.anim || 'ghost';
      let sc = 1, alpha = 1, dy = o.dy || 0;
      if (anim === 'ghost') {
        sc = lerp(1.2, 1, EASE.out(a));
        alpha = (b ** 1.8) * Math.sin(a * Math.PI * 0.5);
        if (b < 1.0) { dy += -30 * (1 - b); sc = lerp(1, 1.15, 1 - b); }
      } else {
        sc = lerp(.6, 1, EASE.back(a));
        alpha = b * clamp(a * 2);
      }
      const px = o.size || TYPE.label;
      if (o.pill && Array.isArray(pos) && o.lead !== false) {   // a capsule on a leader line, a pulsing dot on the exact spot
        const ld = o.lead || [0, -100], [w, h] = pillSize(text, px), q = place(x + ld[0], y + ld[1] + dy, w, h); if (!q) return;
        const e = clamp(a * 1.6) * b; leader(x, y, q[0], q[1], h, col(o.color || 'yellow'), e, t);
        return pill(q[0], q[1], text, col(o.pill), col(o.color || 'white'), px, alpha * clamp((a - .2) * 2), lerp(.7, 1, EASE.back(clamp((a - .2) * 1.4))), true);
      }
      if (o.pill) return pill(x, y + dy, text, col(o.pill), col(o.color || 'white'), px, alpha, sc);
      X.font = fontAr(px, o.font || 'Alexandria'); const q = place(x, y + dy, X.measureText(text).width + 24, px * 1.25); if (!q) return;
      X.save(); X.globalAlpha = alpha; X.translate(q[0], q[1]); X.scale(sc, sc);
      X.shadowColor = 'rgba(0,0,0,.7)'; X.shadowBlur = 16;
      strokeText(0, 0, text, fontAr(px, o.font || 'Alexandria'), col(o.color || 'white'), 'rgba(0,0,0,.75)', 12);
      X.restore();
    } });
}
// number(pos|null, text, {at, until, color, count:{from,to,dur,fmt}, sub})   v4: a slot-machine counter.
// Every digit is its own reel; the reels stop left to right, then the number pulses with a glow and a gold line is drawn
// under it. pos null (or any y in the top band) = the fixed TOP place, always TYPE.hero; [lon,lat] = a small number on the map.
export function number(pos, text, o = {}) {
  pos = pos || { x: W / 2, y: TOP }; if (!Array.isArray(pos) && pos.y >= 250 && pos.y <= 440) pos = { ...pos, y: TOP };
  const geo = Array.isArray(pos), px = geo ? (o.size || TYPE.label * 1.4) : TYPE.hero, c = col(o.color || 'yellow'), t0 = early(o, 'number');
  let pre = text, run = '', post = '';
  if (o.count) {
    const fmt = o.count.fmt || (n => Math.round(n).toLocaleString('en-US')), i = text.indexOf('#');
    pre = text.slice(0, i).trim(); post = text.slice(i + 1); run = fmt(o.count.to);
    if (post.startsWith('%')) { run += '%'; post = post.slice(1); } post = post.trim();
  }
  const digits = [...run].map((ch, k) => /\d/.test(ch) ? k : -1).filter(k => k >= 0), n = digits.length;
  const D = o.count ? Math.max(.95, o.count.dur || 1.1) : 0, stops = digits.map((k, j) => t0 + D * (.42 + .58 * (j + 1) / n));
  const settle = n ? stops[n - 1] : t0 + .3;
  return add({ kind: 'number', val: o.count ? o.count.to : null, say: o.say, layer: !geo, t0, t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .32, outDur: o.outDur ?? .38, z: o.z ?? 8, sfx: o.sfx, mag: o.mag ?? 1, settle, stops,
    res(t) { const [a, b] = life(this, t); if (a <= 0 || b <= 0 || geo) return; const bx = reserve(0, pos.y - px * .62, W, pos.y + (o.sub ? px * .8 + TYPE.line * .95 : px * .62));
      X.font = fontAr(px, 'Cairo'); const sp = px * .16, m = s => s ? X.measureText(s).width : 0, tot = o.count ? m(pre) + [...run].reduce((s2, ch) => s2 + m(ch), 0) + m(post) + (pre ? sp : 0) + (post ? sp : 0) : m(text);
      const hw = Math.min(tot, W * .9) * 1.07 / 2 + 24, cx = pos.x ?? W / 2; bx.ink = [cx - hw, cx + hw]; },
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const [x, y] = geo ? P(...pos) : [pos.x ?? W / 2, pos.y];
      const out = 1 - b, u = clamp((t - settle) / .6), pulse = t >= settle ? Math.sin(Math.PI * clamp((t - settle) / .32)) : 0, glowK = t >= settle ? (1 - u) ** 1.5 : 0;
      const sc = lerp(.7, 1, EASE.back(a)) * (1 + .07 * pulse) * lerp(1, .94, out);
      X.save(); X.globalAlpha = clamp(a * 3) * b; X.translate(x, y - EASE.in(out) * 50); X.scale(sc, sc);
      if (out > 0) X.filter = `blur(${(out * 14).toFixed(1)}px)`;
      if (!o.count) { engraved(0, 0, text, px, c, 'Cairo', 'center', glowK); X.restore(); this.sub(t, x, y, a, b); return; }
      X.font = fontAr(px, 'Cairo'); const sp = px * .16, cw = [...run].map(ch => X.measureText(ch).width), wRun = cw.reduce((s, v) => s + v, 0);
      const wPre = pre ? X.measureText(pre).width : 0, wPost = post ? X.measureText(post).width : 0;
      const tot = wPre + wRun + wPost + (pre ? sp : 0) + (post ? sp : 0), k = Math.min(1, W * .9 / tot); X.scale(k, k);
      const seq = RTL ? [[post, wPost, 0], [run, wRun, 1], [pre, wPre, 0]] : [[pre, wPre, 0], [run, wRun, 1], [post, wPost, 0]];
      let xl = -tot / 2, r0 = 0;
      for (const [s, w, isRun] of seq) {
        if (!s) continue;
        if (!isRun) { engraved(xl + w / 2, 0, s, px, c, 'Cairo', 'center', glowK); xl += w + sp; continue; }
        r0 = xl; let xc = xl; const LH = px * 1.0;
        [...run].forEach((ch, ci) => {
          const w2 = cw[ci], cx = xc + w2 / 2; xc += w2; const j = digits.indexOf(ci);
          if (j < 0) { engraved(cx, 0, ch, px, c, 'Cairo', 'center', glowK); return; }
          const f = +ch, ts = stops[j], uu = clamp((t - t0) / (ts - t0)), spins = 2 + j;
          const bounce = t > ts ? .2 * Math.sin(Math.PI * clamp((t - ts) / .22)) : 0;   // the reel overshoots a hair, then locks
          const v = f + 10 * spins * (1 - EASE.out(uu)) - bounce, base = Math.floor(v), fr = v - base;
          X.save(); X.beginPath(); X.rect(cx - w2 / 2 - px * .14, -px * .64, w2 + px * .28, px * 1.28); X.clip();
          for (const [d, yy] of [[base, fr * LH], [base + 1, (fr - 1) * LH]]) {
            const al = clamp(1 - Math.abs(yy) / LH * 1.25); if (al <= 0) continue;
            X.globalAlpha *= al; engraved(cx, yy, String(((d % 10) + 10) % 10), px, c, 'Cairo', 'center', t >= ts ? glowK : 0); X.globalAlpha /= al;
          }
          X.restore();
        });
        xl += w + sp;
      }
      // the gold line under the number, drawn in the reading direction once the last reel locks
      const ul = EASE.out(clamp((t - settle) / .38));
      if (ul > 0) {
        const yL = px * .5, L = wRun * ul, xa = RTL ? r0 + wRun - L : r0;
        X.save(); X.lineCap = 'round'; X.shadowColor = '#ffd23f'; X.shadowBlur = 18 + 30 * glowK;
        const g = X.createLinearGradient(r0, 0, r0 + wRun, 0); g.addColorStop(0, '#e89a00'); g.addColorStop(.5, '#fff1a8'); g.addColorStop(1, '#e89a00');
        X.strokeStyle = 'rgba(0,0,0,.6)'; X.lineWidth = 15; X.beginPath(); X.moveTo(xa, yL); X.lineTo(xa + L, yL); X.stroke();
        X.strokeStyle = g; X.lineWidth = 8; X.stroke(); X.restore();
      }
      X.restore(); this.sub(t, x, y, a, b);
    },
    sub(t, x, y, a, b) {   // the explanatory line under the number, on a glass card
      if (!o.sub || geo) return; const sa = clamp((t - this.t0 - .22) / .35); if (sa <= 0) return;
      const fs = TYPE.line; X.save(); X.font = fontAr(fs, 'MapArText'); const w = X.measureText(o.sub).width + fs * 1.3, h = fs * 1.6;
      const yy = y + px * .82 + (1 - EASE.out(sa)) * 26 - EASE.in(1 - b) * 40; X.globalAlpha = clamp(sa * 2) * b;
      glass(x, yy, w, h, 22); X.shadowColor = 'rgba(0,0,0,.55)'; X.shadowBlur = 8; X.shadowOffsetY = 2;
      strokeText(x, yy + fs * .04, o.sub, fontAr(fs, 'MapArText'), '#fff', null); X.restore();
    } });
}
// flash(at): an exposure pop on a cut (not a white frame)
const FLASH = []; export function flash(t, k = .55) { FLASH.push([when(t), k]); }
// captions v4, the big-channel way: words pop in as they are spoken (a little bounce), the spoken word sits on a yellow
// capsule with dark text (the capsule glides from word to word); numbers gold, danger words red, places teal;
// peak words ('!' at the end, or listed in emph) swell while spoken; a new sentence slides up and pushes the old one out.
// captions({emph:['مليارات'], places:[...], danger:[...], nums:[...]})
const CAPW = {
  danger: ['صاروخ', 'صواريخ', 'تقفل', 'اتقفل', 'يقفل', 'مرعب', 'هتولع', 'تولع', 'هتقف', 'الدموع', 'مسيرة', 'مسيره', 'تهرب', 'خطر', 'حرب', 'هجوم', 'تهديد', 'انفجار', 'قصف', 'دمار'],
  num: ['واحد', 'اثنين', 'اتنين', 'اثنا', 'اتناشر', 'عشر', 'عشرة', 'عشرين', 'ثلاثة', 'تلاتة', 'تلاتين', 'اربعة', 'خمسة', 'ستة', 'سبعة', 'تمانية', 'تسعة', 'مية', 'المية', 'ألف', 'آلاف', 'مليون', 'ملايين', 'مليار', 'مليارات', 'أسبوعين', 'يومين'],
  place: ['باب', 'المندب', 'آسيا', 'إفريقيا', 'أفريقيا', 'أوروبا', 'الأحمر', 'السويس', 'بريم', 'الرجاء', 'الصالح', 'الهندي', 'أمريكا', 'الصين', 'جيبوتي', 'إريتريا', 'اليمن', 'هرمز'],
};
const KC = { num: '#ffd23f', danger: '#ff5a68', place: '#5fe6d8', '': '#ffffff' };
// v5 'line' (A1, the clean screen): one small elegant line low in the frame, the spoken word gold, and it steps aside
// (yield) whenever a title or a big number is on screen, so the frame never holds more than two text layers.
let CAP = null; export function captions(o = {}) {
  CAP = o.style === 'clean' ? { max: 3, y: .74 * H, size: 50, ...o } : o.style === 'line' ? { max: 5, y: H - SAFE.b - 78, size: 56, yield: true, ...o } : { max: 3, y: .70 * H, size: TYPE.caption, ...o };
}
function layerBusy(t) {   // 0..1: how much a title / top number holds the screen now
  let k = 0;
  for (const it of ITEMS) if (it.layer) { const a = clamp((t - it.t0 + .12) / .15), b = it.t1 == null ? 1 : 1 - clamp((t - it.t1) / .22); k = Math.max(k, Math.min(a, b)); }
  return k;
}
let KIND = null; const KCACHE = new Map();
function wordKind(w) {
  if (KCACHE.has(w)) return KCACHE.get(w);
  if (!KIND) { const S = l => new Set(l.map(nw)); KIND = { num: S([...CAPW.num, ...(CAP.nums || [])]), danger: S([...CAPW.danger, ...(CAP.danger || [])]),
    place: S([...CAPW.place, ...(CAP.places || []), ...WORLD.countries.map(c => c.ar).filter(s => s && !/\s/.test(s))]) }; }
  const k = nw(w), forms = [k, k.replace(/^و/, ''), k.replace(/^[وف]?[بلك]/, ''), k.replace(/^[وف]?لل/, 'ال')];
  const r = /\d/.test(w) || forms.some(f => KIND.num.has(f)) ? 'num' : forms.some(f => KIND.danger.has(f)) ? 'danger' : forms.some(f => f.length > 2 && KIND.place.has(f)) ? 'place' : '';
  KCACHE.set(w, r); return r;
}
const isEmph = w => /!$/.test(w) || (CAP.emph || []).some(e => nw(e) === nw(w));
function capGroups() {
  const G = []; let g = [];
  WORDS.forEach((w, i) => { g.push(w); const end = /[.!?؟،,:]$/.test(w[0]) || g.length >= CAP.max || (WORDS[i + 1] && WORDS[i + 1][1] - w[2] > .35); if (end) { G.push(g); g = []; } });
  if (g.length) G.push(g); return G;
}
let GROUPS = null;
function drawGroup(g, t, dy, alpha, last) {
  const fs = CAP.size, fam = RTL ? 'Cairo' : 'MapLat', fnt = fontAr(fs, fam, 800); X.font = fnt;
  const words = g.map(w => w[0].replace(/[.،,:؛]+$/, '')), n = words.length;
  const endOf = i => i === n - 1 ? g[i][2] + (last ? .6 : .25) : g[i][2];
  const es = words.map((w, i) => { if (!isEmph(g[i][0]) || t < g[i][1] - .03) return 1; const up = EASE.out(clamp((t - g[i][1] + .03) / .16)), dn = EASE.io(clamp((t - endOf(i)) / .25)); return 1 + .38 * up * (1 - dn); });
  const ws = words.map((w, i) => X.measureText(w).width * es[i]), sp = fs * .3, tot = ws.reduce((s, v) => s + v, 0) + sp * (n - 1), fit = Math.min(1, W * .9 / tot);
  const cx = []; let x = RTL ? tot / 2 : -tot / 2; for (let i = 0; i < n; i++) { cx.push(RTL ? x - ws[i] / 2 : x + ws[i] / 2); x = RTL ? x - ws[i] - sp : x + ws[i] + sp; }
  let ci = -1; for (let i = 0; i < n; i++) if (t >= g[i][1] - .03) ci = i; if (ci < 0) return;
  X.save(); X.translate(W / 2, CAP.y + dy); X.scale(fit, fit); X.globalAlpha = alpha;
  // the yellow capsule glides from the previous word to the spoken one
  const m = EASE.out(clamp((t - g[ci][1] + .03) / .13)), pc = ci > 0 ? cx[ci - 1] : cx[ci], pw = ci > 0 ? ws[ci - 1] : ws[ci] * .6;
  const kx = lerp(pc, cx[ci], m), kw = lerp(pw, ws[ci], m) + fs * .5, kh = fs * 1.24 * (1 + .06 * Math.sin(Math.PI * m)), on = clamp((t - g[0][1] + .03) / .1);
  X.save(); X.globalAlpha *= on; X.shadowColor = 'rgba(0,0,0,.5)'; X.shadowBlur = 18; X.shadowOffsetY = 6;
  const kg = X.createLinearGradient(0, -kh / 2, 0, kh / 2); kg.addColorStop(0, '#ffe680'); kg.addColorStop(.55, '#ffd23f'); kg.addColorStop(1, '#f2b705');
  rr(kx - kw / 2, -kh / 2 + fs * .02, kw, kh, kh * .32); X.fillStyle = kg; X.fill(); X.restore();
  X.textBaseline = 'middle'; X.textAlign = 'center'; X.direction = RTL ? 'rtl' : 'ltr'; X.lineJoin = 'round';
  for (let i = 0; i <= ci; i++) {
    const p = clamp((t - g[i][1] + .03) / .22), s = lerp(.55, 1, EASE.back(p)) * es[i], jump = -Math.sin(Math.PI * clamp(p * 1.3)) * fs * .16;
    X.save(); X.globalAlpha *= clamp(p * 4); X.translate(cx[i], jump + fs * .03); X.scale(s, s); X.font = fnt;
    if (i === ci) { X.fillStyle = '#16110a'; X.fillText(words[i], 0, 0); }
    else { X.shadowColor = 'rgba(0,0,0,.7)'; X.shadowBlur = 10; X.shadowOffsetY = 3; X.strokeStyle = 'rgba(0,0,0,.85)'; X.lineWidth = 13; X.strokeText(words[i], 0, 0);
      X.shadowColor = 'transparent'; X.fillStyle = KC[wordKind(g[i][0])]; X.fillText(words[i], 0, 0); }
    X.restore();
  }
  X.restore();
}
function drawCaptions(t) {
  if (!CAP) return; GROUPS = GROUPS || capGroups();
  const gi = GROUPS.findIndex((g, i) => t >= g[0][1] - .05 && (i === GROUPS.length - 1 ? t < g[g.length - 1][2] + .6 : t < GROUPS[i + 1][0][1] - .05)); if (gi < 0) return;
  const g = GROUPS[gi], a = clamp((t - g[0][1] + .05) / .18);
  const yk = CAP.yield ? 1 - layerBusy(t) : 1; if (yk <= .01) return;
  if (CAP.style === 'line') {
    const fs = CAP.size, fnt = fontAr(fs, RTL ? 'Cairo' : 'MapLatText', 700); X.font = fnt;
    const words = g.map(w => w[0].replace(/[.،,:؛؟?!]+$/, '')), n = words.length, sp = fs * .28, ws = words.map(w => X.measureText(w).width);
    const tot = ws.reduce((s, v) => s + v, 0) + sp * (n - 1), fit = Math.min(1, W * .86 / tot);
    X.save(); X.translate(W / 2, CAP.y + (1 - EASE.out(a)) * 14); X.scale(fit, fit); X.globalAlpha = clamp(a * 1.8) * yk;
    X.textBaseline = 'middle'; X.textAlign = 'center'; X.direction = RTL ? 'rtl' : 'ltr'; X.lineJoin = 'round';
    let x = RTL ? tot / 2 : -tot / 2;
    for (let i = 0; i < n; i++) {
      const cx = RTL ? x - ws[i] / 2 : x + ws[i] / 2; x = RTL ? x - ws[i] - sp : x + ws[i] + sp;
      const on = t >= g[i][1] - .03, cur = on && (i === n - 1 || t < g[i + 1][1] - .03), k = wordKind(g[i][0]);
      X.save(); X.globalAlpha *= on ? 1 : .42; X.shadowColor = 'rgba(0,0,0,.85)'; X.shadowBlur = 12; X.shadowOffsetY = 3;
      X.strokeStyle = 'rgba(0,0,0,.7)'; X.lineWidth = fs * .16; X.strokeText(words[i], cx, 0); X.shadowColor = 'transparent';
      X.fillStyle = cur ? '#ffd23f' : on && k === 'danger' ? '#ff6b78' : on && k === 'num' ? '#ffe27a' : '#ffffff'; X.fillText(words[i], cx, 0); X.restore();
    }
    X.restore(); return;
  }
  if (CAP.style === 'clean') {   // the map-Shorts way: small white words, soft shadow, the map stays the star
    X.save(); X.globalAlpha = clamp(a * 1.6); X.shadowColor = 'rgba(0,0,0,.75)'; X.shadowBlur = 14; X.shadowOffsetY = 3;
    strokeText(W / 2, CAP.y + (1 - EASE.out(a)) * 10, g.map(w => w[0].replace(/[.،,:؟?!]$/, '')).join(' '), fontAr(CAP.size, RTL ? 'MapArText' : 'MapLatText'), '#fff', null); X.restore(); return;
  }
  const e = EASE.out(clamp((t - g[0][1] + .05) / .22)), prev = GROUPS[gi - 1];
  if (prev && e < 1 && g[0][1] - prev[prev.length - 1][2] < .7) drawGroup(prev, t, -e * CAP.size * .75, 1 - e, false);   // the old sentence slides up and out
  drawGroup(g, t, (1 - e) * CAP.size * .6, clamp(e * 1.6), gi === GROUPS.length - 1);
}

// ------------------------------------------------------------------ v2: rivers, atlas names, titles, icons, measures
const pts = flat => { const o = []; for (let i = 0; i < flat.length; i += 2) o.push([flat[i] / 1e4, flat[i + 1] / 1e4]); return o; };
// join river pieces across names (Natural Earth splits rivers by local name: 'Firat', 'Al Furat', 'Euphrates')
export function riverLine(names, tol = .03) {
  const parts = []; let mouth = null;
  for (const n of [].concat(names)) { const r = RIVERS.rivers.find(r => r.name.toLowerCase() === n.toLowerCase()); if (!r) throw new Error('unknown river: ' + n); r.parts.forEach(p => parts.push(pts(p))); const m = pts(r.parts[0]); mouth = m[m.length - 1]; }
  let line = parts.shift(); const near = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]) < tol;
  for (let grew = true; grew;) { grew = false;
    for (let i = 0; i < parts.length; i++) { const q = parts[i], qr = [...q].reverse(), e = line[line.length - 1], s0 = line[0];
      if (near(e, q[0])) line = line.concat(q.slice(1)); else if (near(e, qr[0])) line = line.concat(qr.slice(1));
      else if (near(q[q.length - 1], s0)) line = q.concat(line.slice(1)); else if (near(qr[qr.length - 1], s0)) line = qr.concat(line.slice(1)); else continue;
      parts.splice(i, 1); grew = true; break; } }
  const d = p => Math.hypot(p[0] - mouth[0], p[1] - mouth[1]);
  return d(line[0]) < d(line[line.length - 1]) ? line.reverse() : line;   // source first, mouth last
}
const nearest = (line, p) => line.reduce((b, q, k) => Math.hypot(q[0] - p[0], q[1] - p[1]) < Math.hypot(line[b][0] - p[0], line[b][1] - p[1]) ? k : b, 0);
// river(['Firat','Al Furat','Euphrates'], {at, dur, color, width, flow, reverse, from:[lon,lat], to:[lon,lat], until})  draws on from the source, then flows
export function river(names, o = {}) {
  let line = Array.isArray(names) && Array.isArray(names[0]) ? names : riverLine(names, o.tol); if (o.reverse) line = [...line].reverse();
  if (o.from || o.to) { const i = o.from ? nearest(line, o.from) : 0, j = o.to ? nearest(line, o.to) : line.length - 1; line = i <= j ? line.slice(i, j + 1) : line.slice(j, i + 1).reverse(); }
  return add({ kind: 'river', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? 1.4, z: o.z ?? 2, sfx: o.sfx, line,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const sl = screenLine(line), Lp = sl.L * EASE.io(a), c = col(o.color || '#4fd2ff'), w = o.width || 7;
      const trace = () => { X.beginPath(); X.moveTo(sl.s[0][0], sl.s[0][1]); for (let i = 1; i < sl.s.length && sl.cum[i] <= Lp; i++) X.lineTo(sl.s[i][0], sl.s[i][1]); const q = along(sl, Lp); X.lineTo(q[0], q[1]); };
      X.save(); X.globalAlpha = b; X.lineCap = 'round'; X.lineJoin = 'round';
      X.strokeStyle = 'rgba(0,30,60,.45)'; X.lineWidth = w + 6; trace(); X.stroke();
      X.shadowColor = c; X.shadowBlur = o.glow ?? 6; X.strokeStyle = c; X.lineWidth = w; trace(); X.stroke(); X.shadowBlur = 0;
      if (o.flow !== false && Lp > 20) {   // light pulses running downstream
        X.setLineDash([16, 90]); X.lineDashOffset = -(t - this.t0) * (o.flowSpeed ?? 90); X.strokeStyle = 'rgba(255,255,255,.38)'; X.lineWidth = w * .45; trace(); X.stroke(); X.setLineDash([]);
      }
      if (a < 1) { const [x, y] = along(sl, Lp); X.fillStyle = '#fff'; X.shadowColor = 'rgba(0,0,0,.4)'; X.shadowBlur = 4; X.beginPath(); X.arc(x, y, w * .9, 0, 7); X.fill(); }
      X.restore();
    } });
}
// atlasName([lon,lat], 'دجلة', {at, size, rotate (deg), color, until})  calligraphic name written onto the land (wipes in right to left)
export function atlasName(pt, text, o = {}) {
  return add({ kind: 'name', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .6, z: o.z ?? 6, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const [x, y, f] = P(...pt); if (!f) return; const sz = o.size || 92, fnt = fontAr(sz, o.font || (RTL ? 'Alexandria' : 'MapSerif'));
      X.save(); X.globalAlpha = b; X.translate(x, y); X.rotate((o.rotate || 0) * D2R); X.font = fnt; const w = X.measureText(text).width + 40, e = EASE.out(a);
      X.beginPath(); if (RTL) X.rect(w / 2 - w * e, -sz * 1.2, w * e, sz * 2.4); else X.rect(-w / 2, -sz * 1.2, w * e, sz * 2.4); X.clip();
      X.shadowColor = 'rgba(0,0,0,.75)'; X.shadowBlur = 18; X.shadowOffsetY = 4; strokeText(0, 0, text, fnt, col(o.color || 'white'), null);
      X.restore();
    } });
}
// title(text, {at, until, color, level:'title'|'sub'|'hero', anim:'words'|'wipe'|'blur'|'glitch'|'bubble'|'ghost', dock, pt, x, y})
// v4: Cairo Black, gradient fill, double dark rim, deep shadow; it leaves the way it came in. Red titles glitch by default.
// Arabic is never split letter by letter (that breaks the joined script): the smallest moving unit is a word.
// dock: the moment an opening question shrinks to the subtitle size and moves up to the DOCK line (it stays there).
const TC = document.createElement('canvas'), TX = TC.getContext('2d'), RC = document.createElement('canvas'), RX = RC.getContext('2d'), CC = document.createElement('canvas'), CX = CC.getContext('2d');
export function title(text, o = {}) {
  const c = col(o.color || 'yellow'), px = o.level ? TYPE[o.level] : snap(o.size || TYPE.title, [TYPE.sub, TYPE.title]);
  const anim = o.anim || (c === COL.red || c === '#ff4757' ? 'glitch' : 'words');
  const words = text.split(/\s+/).filter(Boolean), n = words.length, ST = .12, t0 = when(o.at ?? 0), dock = o.dock != null ? when(o.dock) : null;
  let y0 = o.y ?? TOP; if (!o.pt && y0 >= 250 && y0 <= 440) y0 = TOP;
  const dur = o.dur ?? (anim === 'words' ? ST * (n - 1) + .34 : anim === 'wipe' ? .5 : anim === 'glitch' ? .42 : .45);
  const outDur = o.outDur ?? (anim === 'words' ? ST * .6 * (n - 1) + .3 : .35);
  return add({ kind: 'title', layer: !o.pt, name: anim, t0, t1: o.until != null ? when(o.until) : null, dur, outDur, z: o.z ?? 9, sfx: o.sfx, mag: o.mag ?? 1,
    ticks: anim === 'words' && dur > .1 ? words.map((w, i) => +(t0 + i * ST).toFixed(3)) : [],
    geo(t) {
      let [x, y] = o.pt ? P(...o.pt) : [o.x ?? W / 2, y0], k = 1;
      if (dock != null) { const d = EASE.io(clamp((t - dock) / .55)); y = lerp(y, DOCK, d); k = lerp(1, TYPE.sub / px, d); }
      return [x, y, k];
    },
    res(t) { const [a, b] = life(this, t); if (a <= 0 || b <= 0 || o.pt) return; const [x, y, k] = this.geo(t), bx = reserve(0, y - px * k * .7, W, y + px * k * .7);
      X.font = fontAr(px, 'Cairo'); const tot = words.reduce((s, w) => s + X.measureText(w).width, 0) + px * .26 * (n - 1), hw = tot * Math.min(1, W * .88 / tot) * k / 2 + px * .35 * k; bx.ink = [x - hw, x + hw]; },
    draw(t) {
      const tt = t - this.t0, to = this.t1 == null ? -1 : t - this.t1; if (tt < 0 || to > this.outDur) return;
      const ea = clamp(tt / this.dur), ob = this.t1 == null ? 0 : clamp(to / this.outDur);
      const [x, y, kd] = this.geo(t); X.font = fontAr(px, 'Cairo');
      const sp = px * .26, ws = words.map(w => X.measureText(w).width), tot = ws.reduce((s, v) => s + v, 0) + sp * (n - 1), fit = Math.min(1, W * .88 / tot) * kd;
      X.save(); X.translate(x, y); X.scale(fit, fit);
      if (anim === 'words') {   // word by word: each rises out of a light blur; they leave in the same order, upward
        let xx = RTL ? tot / 2 : -tot / 2;
        words.forEach((w, i) => {
          const cx = RTL ? xx - ws[i] / 2 : xx + ws[i] / 2; xx = RTL ? xx - ws[i] - sp : xx + ws[i] + sp;
          const wa = this.dur <= .1 ? 1 : clamp((tt - i * ST) / .34), wb = this.t1 == null ? 0 : clamp((to - i * ST * .6) / .3); if (wa <= 0 || wb >= 1) return;
          X.save(); X.globalAlpha *= clamp(wa * 2.5) * (1 - wb); X.translate(cx, (1 - EASE.out(wa)) * px * .42 - EASE.in(wb) * px * .38); const s = lerp(.86, 1, EASE.back(wa)); X.scale(s, s);
          const bl = (1 - wa) * 8 + wb * 10; if (bl > .3) X.filter = `blur(${bl.toFixed(1)}px)`;
          engraved(0, 0, w, px, c); X.restore();
        });
      } else if (anim === 'wipe') {   // reveal in the reading direction behind a bright edge; leave the same way
        const R = tot / 2 + px * .25, L = -R, span = R - L, e = EASE.io(ea), eb = EASE.io(ob);
        const vis = RTL ? [R - span * e, R - span * eb] : [L + span * eb, L + span * e];
        X.save(); X.beginPath(); X.rect(Math.min(vis[0], vis[1]), -px, Math.abs(vis[1] - vis[0]), px * 2); X.clip(); engraved(0, 0, text, px, c); X.restore();
        const edge = ea < 1 ? vis[0] : ob > 0 ? vis[1] : null, ek = ea < 1 ? Math.sin(Math.PI * ea) : Math.sin(Math.PI * ob);
        if (edge != null && ek > .01) { X.save(); X.globalAlpha *= ek; X.shadowColor = tone(c)[1]; X.shadowBlur = 28; X.fillStyle = '#fff8dc'; X.fillRect(edge - 3, -px * .62, 6, px * 1.24); X.restore(); }
      } else if (anim === 'blur') {   // from blur to sharp with a slide up; leaves upward into blur
        const e = EASE.out(ea), bl = (1 - e) * 22 + ob * 22; X.globalAlpha *= clamp(ea * 1.6) * (1 - ob); X.translate(0, (1 - e) * 50 - EASE.in(ob) * 50);
        if (bl > .3) X.filter = `blur(${bl.toFixed(1)}px)`; engraved(0, 0, text, px, c);
      } else if (anim === 'glitch') {   // danger only: RGB split + sliced bands on entry and exit, a short twitch now and then
        const fr = Math.floor(t * 30), tw = (tt % 1.9) > 1.78 ? .45 : 0, G = Math.max(1 - EASE.out(ea), tw, ob);
        const flick = ea < 1 ? (hh(fr) > .25 + .5 * (1 - ea) ? 1 : .2) : ob > 0 ? (hh(fr + 3) > ob ? 1 - ob * .5 : .1) : 1;
        X.globalAlpha *= flick * clamp(ea * 4);
        if (G < .02) engraved(0, 0, text, px, c);
        else {
          const cw = Math.ceil(tot + px * .8), ch = Math.ceil(px * 1.9);
          for (const [cv2, cx2] of [[TC, TX], [RC, RX], [CC, CX]]) { if (cv2.width < cw || cv2.height < ch) { cv2.width = Math.max(cv2.width, cw); cv2.height = Math.max(cv2.height, ch); } cx2.setTransform(1, 0, 0, 1, 0, 0); cx2.clearRect(0, 0, cv2.width, cv2.height); }
          engraved(cw / 2, ch / 2, text, px, c, 'Cairo', 'center', 0, TX);
          for (const [cx2, tc] of [[RX, '#ff2a4a'], [CX, '#22e6ff']]) { cx2.globalCompositeOperation = 'source-over'; cx2.drawImage(TC, 0, 0); cx2.globalCompositeOperation = 'source-in'; cx2.fillStyle = tc; cx2.fillRect(0, 0, cw, ch); cx2.globalCompositeOperation = 'source-over'; }
          const off = G * px * .14; X.save(); X.globalCompositeOperation = 'screen'; X.globalAlpha *= .75 * G;
          X.drawImage(RC, 0, 0, cw, ch, -cw / 2 - off, -ch / 2, cw, ch); X.drawImage(CC, 0, 0, cw, ch, -cw / 2 + off, -ch / 2, cw, ch); X.restore();
          const B = 7, bh = ch / B;
          for (let k = 0; k < B; k++) { const dx = (hh(fr * 13 + k) - .5) * G * px * .55 * (hh(k * 7 + fr) > .45 ? 1 : .12); X.drawImage(TC, 0, k * bh, cw, bh, -cw / 2 + dx, -ch / 2 + k * bh, cw, bh); }
        }
      } else {   // legacy: bubble / ghost / pop
        let s = 1, al = 1, dy = 0;
        if (anim === 'bubble') { s = ea < .6 ? lerp(.15, 1.35, EASE.out(ea / .6)) : lerp(1.35, 1, EASE.back((ea - .6) / .4)); al = 1 - ob; }
        else if (anim === 'ghost') { s = lerp(1.35, 1, EASE.out(ea)); al = ((1 - ob) ** 1.8) * Math.sin(ea * Math.PI * .5); dy = -45 * ob; s *= lerp(1, 1.25, ob); }
        else { s = lerp(o.from ?? 1.25, 1, EASE.out(ea)); al = clamp(ea * 2.5) * (1 - ob); }
        s = Math.min(s, (W * .94) / (tot * fit)); X.globalAlpha *= al; X.translate(0, dy); X.scale(s, s); engraved(0, 0, text, px, c);
      }
      X.restore();
    } });
}
// chapters([['الموقع', T('مضيق')], ['الأرقام', T('كل')], ...])  a progress line at the top of the frame with a mark per
// chapter; when a chapter starts, a small capsule '2 الأرقام' slides in under the line for 2.4 s (Western digits).
const CH = []; export function chapters(list) { for (const [name, t] of list) CH.push({ name, t: when(t) }); CH.sort((a, b) => a.t - b.t); }
const BAR = { y: 124, x0: 70, x1: W - 70 };
function chapterNow(t) { let i = -1; CH.forEach((c, k) => { if (t >= c.t) i = k; }); return i; }
function drawProgress(t) {
  if (!CH.length) return; const { y, x0, x1 } = BAR, Lw = x1 - x0, p = clamp(t / END), at = v => RTL ? x1 - Lw * v : x0 + Lw * v;
  X.save(); X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = clamp(t / .4);
  rr(x0, y - 3.5, Lw, 7, 3.5); X.fillStyle = 'rgba(10,14,22,.45)'; X.fill(); X.lineWidth = 1.2; X.strokeStyle = 'rgba(255,255,255,.25)'; X.stroke();
  const fw = Math.max(7, Lw * p); rr(RTL ? x1 - fw : x0, y - 3.5, fw, 7, 3.5);
  const g = X.createLinearGradient(at(0), 0, at(1), 0); g.addColorStop(0, '#f2b705'); g.addColorStop(1, '#fff1a8');
  X.save(); X.shadowColor = '#ffd23f'; X.shadowBlur = 14; X.fillStyle = g; X.fill(); X.restore();
  for (const c of CH) { const cx = at(c.t / END), done = t >= c.t; X.beginPath(); X.arc(cx, y, done ? 7 : 5.5, 0, 7); X.fillStyle = done ? '#ffd23f' : 'rgba(255,255,255,.7)'; X.fill(); X.lineWidth = 2; X.strokeStyle = 'rgba(0,0,0,.55)'; X.stroke(); }
  X.beginPath(); X.arc(at(p), y, 6, 0, 7); X.fillStyle = '#fff'; X.shadowColor = '#ffd23f'; X.shadowBlur = 16; X.fill(); X.shadowBlur = 0;
  const i = chapterNow(t);
  if (i >= 0) {
    const u = t - CH[i].t, a = clamp(u / .32), b = 1 - clamp((u - 2.1) / .35);
    if (a > 0 && b > 0) {
      const fs = TYPE.label, num = String(i + 1), name = CH[i].name; X.font = fontAr(fs, 'Cairo'); const wn = X.measureText(num).width; X.font = fontAr(fs, 'MapArText'); const wt = X.measureText(name).width;
      const w = wn + wt + fs * 1.5, h = fs * 1.4, xr = RTL && LOGO ? Math.min(x1, LOGO.x0 - 18) : x1, cxp = RTL ? xr - w / 2 : x0 + w / 2, cyp = y + 18 + h / 2, slide = (1 - EASE.out(a)) * 40 * (RTL ? 1 : -1);
      X.globalAlpha = a * b; glass(cxp + slide, cyp, w, h, h / 2);
      const s0 = RTL ? cxp + slide + w / 2 - fs * .55 : cxp + slide - w / 2 + fs * .55;
      X.textBaseline = 'middle'; X.direction = RTL ? 'rtl' : 'ltr'; X.textAlign = RTL ? 'right' : 'left';
      X.font = fontAr(fs, 'Cairo'); X.fillStyle = '#ffd23f'; X.fillText(num, s0, cyp + fs * .05);
      X.font = fontAr(fs, 'MapArText'); X.fillStyle = '#fff'; X.fillText(name, RTL ? s0 - wn - fs * .4 : s0 + wn + fs * .4, cyp + fs * .05);
    }
  }
  X.restore();
}
function reserveProgress(t) {
  if (!CH.length) return; reserve(0, BAR.y - 14, W, BAR.y + 14);
  const i = chapterNow(t); if (i >= 0 && t - CH[i].t < 2.5) reserve(0, BAR.y, W, BAR.y + 18 + TYPE.label * 1.4 + 8);
}

// ---- channel logo «مَدار» / MD (TaleSpark 2.0 §8, 2026-11-17): top right, under the progress bar, out of the TikTok/Shorts UI.
// One style: a silver glass body, clear water-blue liquid in its depth that turns to liquid gold at its surface. A 12 s loop,
// a pure function of t (frames render in any order): coin with MD -> a 360° spin -> edge-on it becomes the Arabic name, an
// empty glass tube -> it fills right to left (reading direction) with a wave and bubbles -> hold, ripple, a silver sheen ->
// spins back to the coin. config.json: "logo": false hides it, "logo_name" / "logo_mono" change the words.
const LOGO = CFG.logo === false ? null : (() => {
  const name = CFG.logo_name || 'مَدار', mono = CFG.logo_mono || 'MD', fs = 72, R = 50, S = 2;
  X.font = `800 ${fs}px Alexandria`; const nw = X.measureText(name).width;
  const w = Math.max(2 * R, nw + 16), h = fs * 1.5, x1 = W - 44, cx = x1 - w / 2, cy = 214;
  const pad = 30, LW = Math.ceil((w + pad * 2) * S), LH = Math.ceil((h + pad * 2) * S);
  const mk = () => { const c = document.createElement('canvas'); c.width = LW; c.height = LH; return [c, c.getContext('2d')]; };
  const L = { name, mono, fs, R, S, nw, w, h, cx, cy, x0: cx - w / 2, x1, pad, LW, LH, k: 1, P: 12, phase: .7, mk };
  [L.LC, L.LX] = mk(); [L.QC, L.QX] = mk();
  // v2 (speed): everything that does not move is drawn once here (shadows with blur, glass, letter masks, chrome);
  // a frame only draws the liquid, the bubbles, the sheen and the moving light, then a few drawImage calls.
  const ox = LW / 2, oy = LH / 2, F = fs * S, font = `800 ${F}px Alexandria`, top = oy - F * .62, xl = ox - nw * S / 2 - 6 * S, xr = ox + nw * S / 2 + 6 * S;
  const txt = c => { c.font = font; c.textAlign = 'center'; c.textBaseline = 'middle'; c.direction = 'rtl'; c.lineJoin = 'round'; };
  let c; [L.nMask, c] = mk(); txt(c); c.fillStyle = '#000'; c.fillText(name, ox, oy);
  [L.nBase, c] = mk(); txt(c); c.save(); c.shadowColor = 'rgba(0,0,0,.6)'; c.shadowBlur = 16 * S; c.shadowOffsetY = 4 * S; c.fillStyle = 'rgba(0,0,0,.45)'; c.fillText(name, ox, oy); c.restore();
  c.fillStyle = 'rgba(214,228,246,.3)'; c.fillText(name, ox, oy);   // the empty glass
  [L.nTop, c] = mk(); txt(c); const hg = c.createLinearGradient(0, top, 0, oy + F * .1); hg.addColorStop(0, 'rgba(255,255,255,.7)'); hg.addColorStop(.45, 'rgba(255,255,255,.12)'); hg.addColorStop(1, 'rgba(255,255,255,0)');
  c.fillStyle = hg; c.fillText(name, ox, oy); c.lineWidth = 2.4 * S; c.strokeStyle = chrome(c, xl, xr, .35); c.strokeText(name, ox, oy);   // glass highlight + chrome edge of the tube
  const r = R * S;
  [L.cBase, c] = mk(); c.save(); c.shadowColor = 'rgba(0,0,0,.55)'; c.shadowBlur = 16 * S; c.shadowOffsetY = 4 * S; c.beginPath(); c.arc(ox, oy, r, 0, 7); c.fillStyle = 'rgba(0,0,0,.35)'; c.fill(); c.restore();
  const bg = c.createRadialGradient(ox - r * .35, oy - r * .45, r * .1, ox, oy, r); bg.addColorStop(0, 'rgba(255,255,255,.62)'); bg.addColorStop(.55, 'rgba(196,210,230,.32)'); bg.addColorStop(1, 'rgba(110,126,150,.5)');
  c.beginPath(); c.arc(ox, oy, r, 0, 7); c.fillStyle = bg; c.fill();
  [L.cTop, c] = mk(); c.font = `800 ${46 * S}px MapLat`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.direction = 'ltr'; c.lineJoin = 'round';
  c.lineWidth = 5 * S; c.strokeStyle = 'rgba(8,20,40,.55)'; c.strokeText(mono, ox, oy + 2 * S);
  const tg = c.createLinearGradient(0, oy - 24 * S, 0, oy + 24 * S); tg.addColorStop(0, '#ffffff'); tg.addColorStop(.48, '#dfe6ee'); tg.addColorStop(.52, '#9aa6b6'); tg.addColorStop(1, '#f4f7fb');
  c.fillStyle = tg; c.fillText(mono, ox, oy + 2 * S); c.lineWidth = 1 * S; c.strokeStyle = 'rgba(20,30,45,.5)'; c.beginPath(); c.arc(ox, oy, r - 5.5 * S, 0, 7); c.stroke();
  return L;
})();
const LQ_STOPS = [[0, '#fff3b8'], [.16, '#ffd23f'], [.34, '#e8a400'], [.5, '#5fd0e6'], [.72, '#1b8fd0'], [1, '#0a3b8c']];   // surface gold -> deep water
const LQ_NAME = [[0, '#fff6c4'], [.22, '#ffe27a'], [.4, '#ffc21a'], [.5, '#e8a400'], [.6, '#7fd8e0'], [.76, '#1f95d4'], [1, '#0a3b8c']];   // the name: liquid gold on its upper half, water below
function lqGrad(c, y0, y1, st = LQ_STOPS) { const g = c.createLinearGradient(0, y0, 0, y1); for (const [o, s] of st) g.addColorStop(o, s); return g; }
function chrome(c, x0, x1, sh = 0) {   // silver glass rim: bright and dark bands, the bright one travels with sh (0..1)
  const g = c.createLinearGradient(x0, 0, x1, 0), b = .1 + .8 * ((sh % 1 + 1) % 1);
  g.addColorStop(0, '#6d7684'); g.addColorStop(b - .1, '#c9d1db'); g.addColorStop(b, '#ffffff'); g.addColorStop(b + .1, '#aab4c2'); g.addColorStop(1, '#5d6673'); return g;
}
function masked(L, q) { q.save(); q.globalCompositeOperation = 'destination-in'; q.drawImage(L.nMask, 0, 0); q.restore(); L.LX.drawImage(L.QC, 0, 0); }
// the Arabic name as a glass tube; fill 0..1 = liquid front from right to left; t drives the waves; gloss 0..1 = gold flash; sheen = silver sweep 0..1
function logoName(t, fill, gloss, sheen) {
  const L = LOGO, c = L.LX, q = L.QX, S = L.S, ox = L.LW / 2, oy = L.LH / 2, fs = L.fs * S, nw = L.nw * S;
  const top = oy - fs * .62, bot = oy + fs * .5, xr = ox + nw / 2 + 6 * S, xl = ox - nw / 2 - 6 * S;
  c.clearRect(0, 0, L.LW, L.LH); c.drawImage(L.nBase, 0, 0);
  if (fill > 0) {   // the liquid: drawn on the second layer, then kept only inside the letters
    q.clearRect(0, 0, L.LW, L.LH);
    const xf = xr - (xr - xl + 24 * S) * fill, wob = fill < 1 ? 1 : 0, front = y => xf + Math.sin(y / (9 * S) + t * 9) * 5 * S * wob;
    q.beginPath(); q.moveTo(xr + 40 * S, top - 20 * S); q.lineTo(xr + 40 * S, bot + 20 * S);
    for (let y = bot + 20 * S; y >= top - 20 * S; y -= 4 * S) q.lineTo(front(y), y);
    q.closePath(); q.fillStyle = lqGrad(q, top, bot, LQ_NAME); q.fill();
    q.save(); q.beginPath(); q.rect(xf, 0, L.LW, L.LH); q.clip(); q.globalCompositeOperation = 'lighter';
    for (let k = 0; k < 2; k++) {   // caustics: two slow bright ripples in the liquid
      q.beginPath(); for (let x = xl - 10 * S; x <= xr + 10 * S; x += 5 * S) { const y = oy + (k ? 14 : -4) * S + Math.sin(x / (22 * S) + t * (k ? 2.1 : -1.6)) * 4 * S; x === xl - 10 * S ? q.moveTo(x, y) : q.lineTo(x, y); }
      q.lineWidth = 2.2 * S; q.strokeStyle = `rgba(255,255,255,${k ? .16 : .22})`; q.stroke();
    }
    for (let i = 0; i < 16; i++) {   // bubbles rising, only where the liquid is
      const bx = xl + (xr - xl) * hh(i + 3.1), r = (1.6 + 2.6 * hh(i + 9.7)) * S, sp = .55 + .5 * hh(i + 5.3), u = (t * sp + hh(i + 1.9)) % 1, by = bot - (bot - top) * u;
      if (bx < xf + 6 * S) continue; q.beginPath(); q.arc(bx + Math.sin(t * 3 + i) * 2 * S, by, r, 0, 7); q.fillStyle = 'rgba(255,255,255,.18)'; q.fill();
      q.lineWidth = .9 * S; q.strokeStyle = `rgba(255,255,255,${(.65 * Math.sin(Math.PI * u)).toFixed(3)})`; q.stroke();
    }
    q.restore();
    if (wob) { q.beginPath(); for (let y = bot + 8 * S; y >= top - 8 * S; y -= 3 * S) q.lineTo(front(y), y); q.lineWidth = 3 * S; q.strokeStyle = 'rgba(235,250,255,.75)'; q.stroke(); }   // foam on the front
    if (gloss > 0) { q.save(); q.globalCompositeOperation = 'lighter'; const g = q.createLinearGradient(0, top, 0, oy); g.addColorStop(0, `rgba(255,236,150,${(.75 * gloss).toFixed(3)})`); g.addColorStop(1, 'rgba(255,236,150,0)'); q.fillStyle = g; q.fillRect(xl, top, xr - xl, oy - top); q.restore(); }
    masked(L, q);
  }
  c.drawImage(L.nTop, 0, 0);   // glass highlight + chrome edge
  if (sheen != null) {   // the silver sheen crossing the letters
    q.clearRect(0, 0, L.LW, L.LH); const sx = xl - 60 * S + (xr - xl + 120 * S) * sheen; q.save(); q.translate(sx, oy); q.rotate(-.35); const sg = q.createLinearGradient(-26 * S, 0, 26 * S, 0);
    sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(.5, 'rgba(255,255,255,.9)'); sg.addColorStop(1, 'rgba(255,255,255,0)'); q.fillStyle = sg; q.fillRect(-26 * S, -fs, 52 * S, fs * 2); q.restore();
    masked(L, q);
  }
}
function logoCoin(t, spin) {   // the face of the coin (the spin moves the light and the liquid)
  const L = LOGO, c = L.LX, S = L.S, ox = L.LW / 2, oy = L.LH / 2, R = L.R * S;
  c.clearRect(0, 0, L.LW, L.LH); c.drawImage(L.cBase, 0, 0);
  c.save(); c.beginPath(); c.arc(ox, oy, R - 5 * S, 0, 7); c.clip();   // the liquid inside the coin
  const lv = oy + R * .08, tilt = Math.sin(spin) * .25 + Math.sin(t * 1.3) * .05;
  c.beginPath(); c.moveTo(ox - R, oy + R); for (let x = -R; x <= R; x += 3 * S) c.lineTo(ox + x, lv + x * tilt + Math.sin(x / (10 * S) + t * 4) * 2.4 * S); c.lineTo(ox + R, oy + R); c.closePath();
  c.fillStyle = lqGrad(c, lv - 6 * S, oy + R); c.fill();
  for (let i = 0; i < 7; i++) { const u = (t * (.4 + .3 * hh(i + 2.2)) + hh(i + 7.7)) % 1, bx = ox + (hh(i + 4.4) - .5) * R * 1.4, by = oy + R - (R * .9) * u; if (by < lv + 4 * S) continue;
    c.beginPath(); c.arc(bx, by, (1.4 + 2 * hh(i)) * S, 0, 7); c.strokeStyle = 'rgba(255,255,255,.6)'; c.lineWidth = .9 * S; c.stroke(); }
  c.restore();
  c.drawImage(L.cTop, 0, 0);   // MD + the inner line
  c.lineWidth = 5 * S; c.strokeStyle = chrome(c, ox - R, ox + R, .5 + Math.sin(spin) * .4); c.beginPath(); c.arc(ox, oy, R - 2.5 * S, 0, 7); c.stroke();   // chrome rim
  c.save(); c.globalCompositeOperation = 'lighter'; c.beginPath(); c.ellipse(ox - R * .3 + Math.sin(spin) * R * .5, oy - R * .5, R * .42, R * .16, -.5, 0, 7); c.fillStyle = 'rgba(255,255,255,.28)'; c.fill(); c.restore();
}
function reserveLogo(t, n0) {   // a title / number in the top band wins: the logo fades while they overlap, and labels avoid the logo
  if (!LOGO) return; const L = LOGO, b = [L.x0 - 6, L.cy - L.h / 2 - 4, L.x1 + 6, L.cy + L.h / 2 + 4];
  let ov = 0; for (const q of BOXES.slice(n0)) if (b[0] < (q.ink ? q.ink[1] : q[2]) && b[2] > (q.ink ? q.ink[0] : q[0])) ov = Math.max(ov, Math.min(b[3], q[3]) - Math.max(b[1], q[1]));
  L.k = 1 - clamp(ov / 34); if (L.k > .5) reserve(...b);
}
function logoState(t) {
  const L = LOGO, u = ((t + L.phase) % L.P + L.P) % L.P, A = 3.7, B = 1.4, Cf = 1.5, Dh = 4.0;
  if (u < A) return { mode: 'coin', th: 0 };
  if (u < A + B) { const p = EASE.io((u - A) / B) * 3 * Math.PI; return p < 2.5 * Math.PI ? { mode: 'coin', th: p } : { mode: 'name', th: p - 3 * Math.PI, fill: 0 }; }
  if (u < A + B + Cf) return { mode: 'name', th: 0, fill: EASE.io((u - A - B) / Cf) };
  if (u < A + B + Cf + Dh) { const v = u - A - B - Cf; return { mode: 'name', th: 0, fill: 1, gloss: Math.sin(Math.PI * clamp(v / .7)), sheen: v > 1.6 && v < 2.8 ? EASE.io((v - 1.6) / 1.2) : null }; }
  const p = EASE.io((u - A - B - Cf - Dh) / B) * 3 * Math.PI;
  return p < .5 * Math.PI ? { mode: 'name', th: p, fill: 1 } : { mode: 'coin', th: p + Math.PI };
}
function drawLogo(t) {
  if (!LOGO) return; const L = LOGO, a = clamp((t - .15) / .6), vis = L.k * EASE.out(a); if (vis <= .01) return;
  const s = logoState(t), th = s.th; if (s.mode === 'coin') logoCoin(t, th); else logoName(t, s.fill, s.gloss || 0, s.sheen ?? null);
  const cs = Math.cos(th), sn = Math.sin(th), sx = Math.max(.02, Math.abs(cs)), pop = .88 + .12 * EASE.back(a);
  X.save(); X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = vis;
  const cx = s.mode === 'coin' ? L.x1 - L.R : L.cx;   // the coin sits at the right end of the name's place
  if (s.mode === 'coin' && Math.abs(sn) > .02) {   // the coin's edge: a chrome band as thick as the coin, seen while it turns
    const T = 11 * Math.abs(sn), R = L.R * pop, rx = R * sx, d = sn * cs >= 0 ? -1 : 1, g = X.createLinearGradient(0, L.cy - R, 0, L.cy + R);
    g.addColorStop(0, '#8b95a3'); g.addColorStop(.3, '#f3f6fa'); g.addColorStop(.55, '#7d8796'); g.addColorStop(.8, '#d8dee6'); g.addColorStop(1, '#4d5562');
    X.beginPath(); X.ellipse(cx + d * T, L.cy, rx, R, 0, 0, 7); X.fillStyle = g; X.fill(); X.fillRect(Math.min(cx, cx + d * T), L.cy - R, T, 2 * R);
    X.lineWidth = 1; X.strokeStyle = 'rgba(30,38,50,.6)'; X.beginPath(); X.ellipse(cx + d * T, L.cy, rx, R, 0, 0, 7); X.stroke();
  }
  const dw = L.LW / L.S * sx * pop, dh = L.LH / L.S * pop;
  X.drawImage(L.LC, cx - dw / 2, L.cy - dh / 2, dw, dh);
  X.restore();
}

// icons drawn in code (100 px box, centred)
const ICON = {
  x: (c) => { X.lineCap = 'round'; X.shadowColor = 'rgba(0,0,0,.45)'; X.shadowBlur = 10; X.strokeStyle = c; X.lineWidth = 22; X.beginPath(); X.moveTo(-34, -34); X.lineTo(34, 34); X.moveTo(34, -34); X.lineTo(-34, 34); X.stroke(); X.shadowBlur = 0; X.strokeStyle = '#fff'; X.lineWidth = 8; X.stroke(); },
  mountain: (c) => { const mono = c === '#fff'; X.fillStyle = mono ? '#fff' : '#6b5a4a'; X.beginPath(); X.moveTo(-46, 32); X.lineTo(-10, -30); X.lineTo(26, 32); X.fill(); X.fillStyle = mono ? 'rgba(255,255,255,.7)' : '#8a7560'; X.beginPath(); X.moveTo(-6, 32); X.lineTo(20, -12); X.lineTo(48, 32); X.fill();
    if (!mono) { X.fillStyle = '#fff'; X.beginPath(); X.moveTo(-10, -30); X.lineTo(-20, -13); X.lineTo(-12, -9); X.lineTo(-5, -16); X.lineTo(2, -10); X.closePath(); X.fill(); } },
  drop: (c) => { X.fillStyle = c; X.beginPath(); X.moveTo(0, -42); X.bezierCurveTo(22, -12, 32, 6, 32, 16); X.arc(0, 16, 32, 0, Math.PI); X.bezierCurveTo(-32, 6, -22, -12, 0, -42); X.fill(); X.fillStyle = 'rgba(255,255,255,.55)'; X.beginPath(); X.ellipse(-12, 14, 6, 12, .3, 0, 7); X.fill(); },
  city: (c) => { X.fillStyle = '#fff'; X.shadowColor = 'rgba(0,0,0,.6)'; X.shadowBlur = 8; X.beginPath(); X.arc(0, 0, 13, 0, 7); X.fill(); X.shadowBlur = 0; X.fillStyle = c; X.beginPath(); X.arc(0, 0, 7, 0, 7); X.fill(); },
  question: (c) => { strokeText(0, 6, '؟', fontAr(110), c, null); },
  check: (c) => { X.lineCap = 'round'; X.lineJoin = 'round'; X.strokeStyle = c; X.lineWidth = 16; X.beginPath(); X.moveTo(-32, 2); X.lineTo(-8, 26); X.lineTo(34, -24); X.stroke(); },
  star: (c) => { X.fillStyle = c; X.beginPath(); for (let i = 0; i < 10; i++) { const r = i % 2 ? 18 : 42, a = -Math.PI / 2 + i * Math.PI / 5; X.lineTo(Math.cos(a) * r, Math.sin(a) * r); } X.fill(); },
};
function hexPath(r) { X.beginPath(); for (let i = 0; i < 6; i++) { const a = Math.PI / 6 + i * Math.PI / 3; X.lineTo(Math.cos(a) * r, Math.sin(a) * r); } X.closePath(); }
// icon(pt or {x,y}, 'x' | 'mountain' | 'drop' | 'city' | 'question' | 'check' | 'star', {at, until, size, badge:'hex'|'circle', color, anchor, label})
// pops in with overshoot, leaves by shrinking to a point; anchor:true grows with the map (a fast zoom blows it past the lens)
export function icon(pos, kind, o = {}) {
  let k0 = null;
  return add({ kind: 'icon', name: typeof kind === 'string' ? kind : undefined, t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: .4, z: o.z ?? 7, sfx: o.sfx,
    draw(t) {
      const a = clamp((t - this.t0) / .4), out = this.t1 == null ? 0 : clamp((t - this.t1) / .3); if (a <= 0 || out >= 1) return;
      const [x, y] = Array.isArray(pos) ? P(...pos) : [pos.x, pos.y]; if (k0 === null) k0 = kOf(camAt(this.t0).span);
      const zoom = o.anchor ? Math.pow(C.k / k0, .8) : 1;
      const s = (o.size || 1) * EASE.back(a) * (1 - EASE.in(out)) * zoom; if (s <= .001) return;
      X.save(); X.translate(x, y); X.scale(s, s); X.rotate((o.rotate || 0) * D2R);
      const c = col(o.color || (kind === 'x' ? '#ff3046' : kind === 'drop' ? '#38bdf8' : '#ffd23f'));
      if (o.badge) { X.shadowColor = 'rgba(0,0,0,.45)'; X.shadowBlur = 14; X.shadowOffsetY = 4; X.fillStyle = c; if (o.badge === 'hex') hexPath(64); else { X.beginPath(); X.arc(0, 0, 60, 0, 7); } X.fill(); X.shadowBlur = 0; X.lineWidth = 6; X.strokeStyle = 'rgba(255,255,255,.9)'; X.stroke();
        X.fillStyle = 'rgba(255,255,255,.18)'; if (o.badge === 'hex') hexPath(52); else { X.beginPath(); X.arc(0, 0, 50, 0, 7); } X.fill(); X.scale(.62, .62); ICON[kind]('#fff'); }
      else ICON[kind](c);
      X.restore();
      if (o.label) pill(x, y - 100 * (o.size || 1), o.label, col(o.labelBg || '#111a2b'), '#fff', o.labelSize || TYPE.label, 1 - out, lerp(.6, 1, EASE.back(a)));
    } });
}
// measure(a, b, {at, dur, to: 160, unit: 'كم', color})  double arrow that grows from the middle while its label counts up, laid along the line
export function measure(a0, b0, o = {}) {
  return add({ kind: 'measure', val: o.to ?? null, say: o.say, ends: [a0, b0], unit: o.unit || 'كم', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .9, z: o.z ?? 5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const A = P(...a0), B = P(...b0), e = EASE.out(a), c = col(o.color || 'white');
      const mx = (A[0] + B[0]) / 2, my = (A[1] + B[1]) / 2, ang = Math.atan2(B[1] - A[1], B[0] - A[0]), L = Math.hypot(B[0] - A[0], B[1] - A[1]) * e;
      X.save(); X.globalAlpha = b; X.translate(mx, my); X.rotate(ang); X.shadowColor = 'rgba(0,0,0,.6)'; X.shadowBlur = 10; X.strokeStyle = X.fillStyle = c; X.lineWidth = 5; X.lineCap = 'round';
      X.beginPath(); X.moveTo(-L / 2 + 14, 0); X.lineTo(L / 2 - 14, 0); X.stroke();
      for (const d of [-1, 1]) { X.beginPath(); X.moveTo(d * L / 2, 0); X.lineTo(d * (L / 2 - 26), -14); X.lineTo(d * (L / 2 - 26), 14); X.closePath(); X.fill(); }
      if (ang > Math.PI / 2 || ang < -Math.PI / 2) X.rotate(Math.PI);   // keep the label upright
      const v = Math.round(lerp(o.from ?? 0, o.to ?? 0, EASE.out(clamp((t - this.t0) / (this.dur * 1.1))))), txt = o.text || `${v} ${o.unit || 'كم'}`;
      if (o.label !== false) strokeText(0, -(o.size || TYPE.label) * .7, txt, fontAr(o.size || TYPE.label), c, 'rgba(0,0,0,.6)', 10);
      X.restore();
    } });
}


// ghost(code, {to:[lon,lat], at, until, style, only:[w,s,e,n], dur})  a country's real shape picked up and laid somewhere else for a
// size comparison (France on Egypt). East-west is rescaled by latitude, so the shape keeps its true size in km.
export function ghost(code, o = {}) {
  const c = country(code); if (!c) throw new Error('unknown country: ' + code);
  const box = o.only || c.bb, rings = c.r.filter(r => { const x = r[0] / 1e4, y = r[1] / 1e4; return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]; });
  let sx = 0, sy = 0, n = 0; for (const r of rings) for (let i = 0; i < r.length; i += 2) { sx += r[i]; sy += r[i + 1]; n++; }
  const cx = sx / n / 1e4, cy = sy / n / 1e4, k = Math.cos(cy * D2R);
  const st = { ...(STYLE[o.style || 'blue']) };
  return add({ kind: 'ghost', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? 1.0, z: o.z ?? 3, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const e = EASE.io(a);
      const to = o.to || [cx, cy], lat = lerp(cy, to[1], e), lon = lerp(cx, to[0], e), kk = k / Math.cos(lat * D2R);
      const p = new Path2D();
      for (const r of rings) { for (let i = 0; i < r.length; i += 2) { const [x, y] = P(lon + (r[i] / 1e4 - cx) * kk, lat + (r[i + 1] / 1e4 - cy)); i ? p.lineTo(x, y) : p.moveTo(x, y); } p.closePath(); }
      X.save(); X.globalAlpha = b; X.shadowColor = 'rgba(0,0,0,.45)'; X.shadowBlur = 20; X.shadowOffsetY = 8 + 10 * Math.sin(Math.PI * e);
      X.fillStyle = rgba(ATLAS[o.style || 'blue'] || '#2f62d0', .62); X.fill(p, 'evenodd'); X.shadowColor = 'transparent';
      X.lineJoin = 'round'; X.lineWidth = 3; X.strokeStyle = 'rgba(255,255,255,.9)'; X.stroke(p); X.restore();
      if (o.label) { const [x, y] = P(lon, lat); X.save(); X.globalAlpha = b * clamp((a - .6) / .4); strokeText(x, y, o.label, fontAr(o.labelSize || TYPE.label, RTL ? 'MapAr' : 'MapLat'), '#fff', 'rgba(0,0,0,.5)', 10); X.restore(); }
    } });
}
// swarm(line or pts, {count, spread (km), at, dur, size, color, seed})  little people popping up along a line, one after another
export function swarm(line, o = {}) {
  const n = o.count || 60; let seed = o.seed || 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const L = line.length, P0 = [];
  for (let i = 0; i < n; i++) { const u = rnd(), j = Math.min(L - 2, Math.floor(u * (L - 1))), f = u * (L - 1) - j, a = line[j], b = line[j + 1];
    const g = (rnd() + rnd() + rnd() - 1.5) * (o.spread || 8) / 111; P0.push([lerp(a[0], b[0], f) + g * 1.2, lerp(a[1], b[1], f) + (rnd() - .5) * (o.spread || 8) / 111, rnd()]); }
  const order = P0.map((p, i) => [p[1], i]).sort((x, y) => (o.downward ? y[0] - x[0] : x[0] - y[0])).map(x => x[1]);
  return add({ kind: 'swarm', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? 1.6, z: o.z ?? 4, sfx: o.sfx,
    draw(t) {
      const out = this.t1 == null ? 0 : clamp((t - this.t1) / .4); if (t < this.t0 || out >= 1) return; const sz = (o.size || 1) * 11, c = col(o.color || '#ffffff');
      X.save(); X.globalAlpha = 1 - out;
      order.forEach((i, r) => { const ta = this.t0 + this.dur * r / n, a = clamp((t - ta) / .25); if (a <= 0) return; const [x, y] = P(P0[i][0], P0[i][1]), s = sz * EASE.back(a);
        X.fillStyle = 'rgba(0,0,0,.35)'; X.beginPath(); X.ellipse(x, y + s * .95, s * .7, s * .22, 0, 0, 7); X.fill();
        X.fillStyle = P0[i][2] > .5 ? c : rgba(c.startsWith('#') ? c : '#ffffff', .82); X.beginPath(); X.arc(x, y - s * .55, s * .42, 0, 7); X.fill();
        X.beginPath(); X.moveTo(x - s * .55, y + s * .85); X.quadraticCurveTo(x - s * .5, y - s * .1, x, y - s * .05); X.quadraticCurveTo(x + s * .5, y - s * .1, x + s * .55, y + s * .85); X.closePath(); X.fill(); });
      X.restore();
    } });
}


// shade(line, {width (km), at, until, k})  darkens everything except a band along a line (a river valley, a road, a coast)
export function shade(line, o = {}) {
  return add({ kind: 'shade', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .8, z: o.z ?? 1.5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const s = line.map(p => P(p[0], p[1])), hw = (o.width || 20) / 2 * pxPerKm(), L = [], Rr = [];
      for (let i = 0; i < s.length; i++) { const p = s[Math.max(0, i - 1)], q = s[Math.min(s.length - 1, i + 1)]; let nx = -(q[1] - p[1]), ny = q[0] - p[0]; const d = Math.hypot(nx, ny) || 1; nx /= d; ny /= d;
        L.push([s[i][0] + nx * hw, s[i][1] + ny * hw]); Rr.push([s[i][0] - nx * hw, s[i][1] - ny * hw]); }
      const band = new Path2D(); [...L, ...Rr.reverse()].forEach(([x, y], i) => i ? band.lineTo(x, y) : band.moveTo(x, y)); band.closePath();
      const all = new Path2D(); all.rect(0, 0, W, H); all.addPath(band);
      X.save(); X.globalAlpha = b * EASE.sine(a); X.filter = 'blur(6px)'; X.fillStyle = `rgba(8,6,4,${o.k ?? .55})`; X.fill(all, 'evenodd'); X.restore();
    } });
}

// ------------------------------------------------------------------ v5 (reference-video plan A1-A11)
// assets: engine/assets served at /assets (index.json from engine/scripts/asset_index.py; ChatGPT sprites win over Fluent)
const AIDX = await getJSON('/assets/index.json', { sprites: {}, photos: {}, flags: {} });
const PRE = [], IMGC = new Map();   // every image a story asks for is loaded before the first frame (start() waits)
function asset(src) {
  if (!IMGC.has(src)) {
    const e = { img: null, src }; IMGC.set(src, e);
    // an SVG without width/height (flag-icons) has no intrinsic size and draws nothing on a canvas: give it one
    const ld = src.endsWith('.svg') ? fetch(src).then(r => r.text()).then(s => loadImg(URL.createObjectURL(new Blob([s.replace(/<svg\b/, '<svg width="960" height="720" preserveAspectRatio="none"')], { type: 'image/svg+xml' })))) : loadImg(src);
    PRE.push(ld.then(i => { e.img = i; }).catch(() => console.log('asset missing: ' + src)));
  }
  return IMGC.get(src);
}
const ISO2 = { YEM: 'ye', DJI: 'dj', ERI: 'er', SAU: 'sa', EGY: 'eg', SOM: 'so', USA: 'us', CHN: 'cn', FRA: 'fr', JPN: 'jp', ITA: 'it', IND: 'in', GBR: 'gb', IRN: 'ir', ARE: 'ae', OMN: 'om', MAR: 'ma' };
const flagSrc = k => { const c = String(k).length === 3 ? ISO2[String(k).toUpperCase()] : String(k).toLowerCase(); return '/assets/' + (AIDX.flags[c] || `flags/${c}.svg`); };
const spriteSrc = n => '/assets/' + (AIDX.sprites[n] || (n.includes('.') ? n : `emoji/${n}.png`));
const photoSrc = n => n.startsWith('/') ? n : '/assets/' + (AIDX.photos[n] || `photos/${n}`);
// A2 vivid: a saturated coat (85-95 %), a glowing white edge and a white-hot entry flash
function vivid(c, op) { return { fill: rgba(c, op ?? .88), hue: rgba(c, .9), line: '#ffffff', lw: 4.5, vivid: true }; }
// A2 flag inside the borders, optionally waving like cloth (vertical strips shifted on a sine, lit and shaded)
function drawFlag(e, box, t, wave) {
  if (!e.r) { const r = document.createElement('canvas'); r.width = 960; r.height = 720; r.getContext('2d').drawImage(e.img, 0, 0, 960, 720); e.r = r; }
  const r = e.r, cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2, w0 = Math.max(box[2] - box[0], (box[3] - box[1]) * 4 / 3) * 1.12, h0 = w0 * .75, x0 = cx - w0 / 2, y0 = cy - h0 / 2;
  if (!wave) { X.drawImage(r, x0, y0, w0, h0); return; }
  const N = 110;
  for (let i = 0; i < N; i++) {
    const ph = i / N * Math.PI * 3.2 - t * 3.4, dy = Math.sin(ph) * h0 * .022, sx = i / N * r.width, dx = x0 + i / N * w0;
    X.drawImage(r, sx, 0, r.width / N + 1, r.height, dx, y0 + dy, w0 / N + 1.5, h0);
    const sh = Math.cos(ph); X.fillStyle = sh > 0 ? `rgba(255,255,255,${.16 * sh})` : `rgba(0,0,0,${.22 * -sh})`; X.fillRect(dx, y0 + dy, w0 / N + 1.5, h0);
  }
}
// A7 lead: per-type head start (seconds before the word). leads({number:.6, photo:.6, sprite:.3, label:.3}) or config.json "leads".
// Only a word anchor (a string 'at') is moved; a number time stays exact. o.early overrides it per element.
const LEADS = { ...(CFG.leads || {}) }; export function leads(m) { Object.assign(LEADS, m); }
function early(o, kind) { const t = when(o.at ?? 0); return o.early != null ? t - o.early : typeof o.at === 'string' ? t - (LEADS[kind] || 0) : t; }

// ------------------------------------------------------------------ v6: logic in everything (user rule 2026-11-17)
// "The ship sailed sideways": a 3/4 picture can only be mirrored, never turned. So anything that MOVES is drawn from straight
// above (engine/assets/sprites/top/, nose up) and turned to its real heading in every frame; it stays in its own medium
// (a ship on water, a tank on land), keeps its real size order and speed order, and what it causes happens where and when
// it arrives. logic() below checks all of it before a single frame is rendered and stops the render on a violation.
// CRAFT: what an object is. m = where it may be, len = real length in metres (size order), v = real cruise speed km/h (speed order)
const CRAFT = {
  container_ship: { m: 'sea', len: 366, v: 40 }, carrier: { m: 'sea', len: 333, v: 55 }, tanker: { m: 'sea', len: 330, v: 28 }, tanker_burning: { m: 'sea', len: 330, v: 28 },
  lng_carrier: { m: 'sea', len: 295, v: 36 },   // Q-Max class LNG carrier (Qatar): 345 m; Moss type 290-300 m, 19.5 kn
  destroyer: { m: 'sea', len: 155, v: 55 }, submarine: { m: 'sea', len: 110, v: 40 }, dhow: { m: 'sea', len: 25, v: 12 }, patrol_boat: { m: 'sea', len: 20, v: 75 },
  fighter_jet: { m: 'air', len: 17, v: 1500 }, helicopter: { m: 'air', len: 15, v: 250 }, missile: { m: 'air', len: 6, v: 900 }, drone: { m: 'air', len: 3.5, v: 185 },
  missile_launcher: { m: 'land', len: 12, v: 60 }, tank: { m: 'land', len: 10, v: 50 },
};
// length on screen relative to the biggest ship. A true scale would make everything one pixel, so it is symbolic, but it
// keeps the real order (compressed): container ship 1, tanker .96, destroyer .73, dhow .38; aircraft and missiles below the ships.
const AIRREL = { fighter_jet: .5, helicopter: .46, missile: .4, drone: .36 };
const REL = n => { const k = CRAFT[n]; return !k ? 1 : k.m === 'air' ? AIRREL[n] : k.m === 'land' ? .34 : Math.pow(k.len / 366, .36); };
// the size follows the camera: bigger (symbolic) far away, never a giant next to a narrow strait. Length in px of a REL 1 ship.
const sizeCurve = span => clamp(170 * Math.pow(span, -.22), 84, 170);
const SUN = [.55, .83];                                   // one sun: every cast shadow falls this way on the screen
let WIND = .6; export function wind(v) { WIND = v; }      // one wind: every plume and trail leans this way
// waterways that the border polygons show as land (a ship may cross "land" inside these boxes): Suez, Panama, Bosporus, Dardanelles, Kiel
const CANALS = [[32.2, 29.85, 32.7, 31.35], [-80.05, 8.85, -79.45, 9.45], [28.9, 40.95, 29.25, 41.3], [26.1, 39.95, 26.85, 40.5], [9, 53.85, 10.2, 54.45]];
const inCanal = p => CANALS.some(b => p[0] >= b[0] && p[0] <= b[2] && p[1] >= b[1] && p[1] <= b[3]);
// ---- geography in lon/lat and km (independent of the camera)
const hav = (a, b) => { const p1 = a[1] * D2R, p2 = b[1] * D2R, s = Math.sin((p2 - p1) / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin((b[0] - a[0]) * D2R / 2) ** 2; return 12742 * Math.asin(Math.min(1, Math.sqrt(s))); };
function geoLine(pts) { if (pts.length < 2) pts = [pts[0], pts[0]]; const cum = [0]; for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + hav(pts[i - 1], pts[i])); return { pts, cum, L: cum[cum.length - 1] }; }
function geoAt(g, km) {
  km = clamp(km, 0, g.L); let lo = 1, hi = g.cum.length - 1; while (lo < hi) { const m = (lo + hi) >> 1; if (g.cum[m] < km) lo = m + 1; else hi = m; }
  const a = g.pts[lo - 1], b = g.pts[lo], u = (km - g.cum[lo - 1]) / Math.max(1e-9, g.cum[lo] - g.cum[lo - 1]); return [lerp(a[0], b[0], u), lerp(a[1], b[1], u)];
}
function inRing(r, x, y) { let c = false; for (let i = 0, n = r.length, j = n - 2; i < n; j = i, i += 2) { const yi = r[i + 1], yj = r[j + 1]; if ((yi > y) !== (yj > y) && x < (r[j] - r[i]) * (y - yi) / (yj - yi) + r[i]) c = !c; } return c; }
// landAt([lon, lat]) -> the country under that point, or null at sea (rings are even-odd, so a lake inside a country is water)
export function landAt(pt) {
  const x = pt[0] * 1e4, y = pt[1] * 1e4;
  for (const c of WORLD.countries) { let k = false; for (let q = 0; q < c.r.length; q++) { const b = c.rb[q]; if (pt[0] < b[0] || pt[0] > b[2] || pt[1] < b[1] || pt[1] > b[3]) continue; if (inRing(c.r[q], x, y)) k = !k; } if (k) return c; }
  return null;
}
// distance from a point to the nearest coast in km (capped): half the width of the water a ship is in
function clearKm(pt, cap = 60) {
  const kx = Math.cos(pt[1] * D2R) * 111.19, ky = 111.19; let best = cap;
  for (const c of WORLD.countries) for (let q = 0; q < c.r.length; q++) {
    const bb = c.rb[q]; if (pt[0] < bb[0] - best / kx || pt[0] > bb[2] + best / kx || pt[1] < bb[1] - best / ky || pt[1] > bb[3] + best / ky) continue;
    const r = c.r[q], n = r.length; let ax = (r[n - 2] / 1e4 - pt[0]) * kx, ay = (r[n - 1] / 1e4 - pt[1]) * ky;
    for (let i = 0; i < n; i += 2) {
      const bx = (r[i] / 1e4 - pt[0]) * kx, by = (r[i + 1] / 1e4 - pt[1]) * ky;
      if (!((ax > best && bx > best) || (ax < -best && bx < -best) || (ay > best && by > best) || (ay < -best && by < -best))) {
        const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, u = l2 ? clamp(-(ax * dx + ay * dy) / l2) : 0, d = Math.hypot(ax + u * dx, ay + u * dy); if (d < best) best = d;
      }
      ax = bx; ay = by;
    }
  }
  return best;
}
function coastKm(c, pt) { const kx = Math.cos(pt[1] * D2R) * 111.19, ky = 111.19; let best = 1e9;
  for (const r of c.r) { const n = r.length; let ax = (r[n - 2] / 1e4 - pt[0]) * kx, ay = (r[n - 1] / 1e4 - pt[1]) * ky;
    for (let i = 0; i < n; i += 2) { const bx = (r[i] / 1e4 - pt[0]) * kx, by = (r[i + 1] / 1e4 - pt[1]) * ky, dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, u = l2 ? clamp(-(ax * dx + ay * dy) / l2) : 0, d = Math.hypot(ax + u * dx, ay + u * dy); if (d < best) best = d; ax = bx; ay = by; } }
  return best; }
// the narrowest water on a whole route (a sample every ~6 km, canals excluded): one number, so a ship never pulses as it sails
const CLR = new Map();
function clearance(G, pos) {
  if (!G) return Array.isArray(pos) ? clearKm(pos) : null;
  const key = G.pts.length + ':' + G.pts[0] + ':' + G.pts[G.pts.length - 1] + ':' + G.L.toFixed(2); if (CLR.has(key)) return CLR.get(key);
  const N = clamp(Math.ceil(G.L / 6), 40, 700); let best = 60;
  for (let i = 0; i <= N; i++) { const p = geoAt(G, G.L * i / N); if (!inCanal(p)) best = Math.min(best, Math.max(clearKm(p, best), 1.5)); }
  CLR.set(key, best); return best;
}
// a soft black silhouette of a sprite, made once, for its cast shadow
function shadowOf(e) {
  if (!e.sh) { const m = 18, c = document.createElement('canvas'); c.width = e.img.width + m * 2; c.height = e.img.height + m * 2; const x = c.getContext('2d');
    x.filter = 'blur(7px)'; x.drawImage(e.img, m, m); x.filter = 'none'; x.globalCompositeOperation = 'source-in'; x.fillStyle = '#000'; x.fillRect(0, 0, c.width, c.height); e.sh = c; }
  return e.sh;
}
// the white water a ship leaves behind: it lies on the path the ship really sailed (it bends with the turn) and widens as it fades
function wake(G, d, L, beam, al, k = 1) {
  const ppk = pxPerKm(), n = 16, len = L * 1.7 * k, pts = [];
  for (let i = 0; i <= n; i++) { const dd = d - (L * .38 + len * i / n) / ppk; if (dd < 0) break; const q = P(...geoAt(G, dd)); pts.push(q); }
  if (pts.length < 3) return;
  const rib = (w0, w1, a0, m) => {
    const N = Math.max(3, Math.round(pts.length * m)), Lf = [], Rt = [];
    for (let i = 0; i < N; i++) { const p = pts[i], q0 = pts[Math.max(0, i - 1)], q1 = pts[Math.min(N - 1, i + 1)], dx = q1[0] - q0[0], dy = q1[1] - q0[1], l = Math.hypot(dx, dy) || 1, w = lerp(w0, w1, i / (N - 1)) / 2;
      Lf.push([p[0] - dy / l * w, p[1] + dx / l * w]); Rt.push([p[0] + dy / l * w, p[1] - dx / l * w]); }
    const g = X.createLinearGradient(pts[0][0], pts[0][1], pts[N - 1][0], pts[N - 1][1]); g.addColorStop(0, `rgba(255,255,255,${a0})`); g.addColorStop(.4, `rgba(232,246,255,${a0 * .5})`); g.addColorStop(1, 'rgba(232,246,255,0)');
    X.beginPath(); X.moveTo(Lf[0][0], Lf[0][1]); for (const p of Lf) X.lineTo(p[0], p[1]); for (let i = N - 1; i >= 0; i--) X.lineTo(Rt[i][0], Rt[i][1]); X.closePath(); X.fillStyle = g; X.fill();
  };
  X.save(); X.globalAlpha = al; rib(beam * .6, beam * 1.7, .2, 1); rib(beam * .32, beam * .58, .5, .5); X.restore();
}
// what a flying object leaves behind: a missile a smoke trail (puffs fixed where it flew, thinning with age, leaning with the wind),
// a drone a thin exhaust line, a jet two contrails
function trail(name, G, d, L, beam, al) {
  const ppk = pxPerKm(), tail = d - L * .5 / ppk; if (tail <= 0) return;
  X.save();
  if (name === 'missile') {
    const step = G.L / 70, lenK = Math.max(G.L * .34, L * 3 / ppk);
    for (let k = Math.max(0, Math.ceil((tail - lenK) / step)); k * step <= tail; k++) {
      const f = (tail - k * step) / lenK, q = P(...geoAt(G, k * step)), r = beam * (.34 + 1.05 * f), sh = Math.round(200 + 45 * hh(k * 1.7));
      const gx = q[0] + WIND * 16 * f * f + (hh(k) - .5) * beam * .35, gy = q[1] - 9 * f * f, g = X.createRadialGradient(gx, gy, 0, gx, gy, r);
      X.globalAlpha = al * .55 * (1 - f) ** 1.25; g.addColorStop(0, `rgba(${sh},${sh},${sh},.95)`); g.addColorStop(1, `rgba(${sh},${sh},${sh},0)`); X.fillStyle = g; X.beginPath(); X.arc(gx, gy, r, 0, 7); X.fill();
    }
  } else {
    const lenK = Math.max(G.L * .16, L * 2 / ppk), n = 10, offs = name === 'fighter_jet' ? [-beam * .16, beam * .16] : [0]; X.lineCap = 'round';
    for (const off of offs) for (let i = 0; i < n; i++) {
      const d0 = tail - lenK * i / n, d1 = tail - lenK * (i + 1) / n; if (d1 < 0) break; const a = P(...geoAt(G, d0)), b = P(...geoAt(G, d1)), dx = a[0] - b[0], dy = a[1] - b[1], l = Math.hypot(dx, dy) || 1;
      X.globalAlpha = al * .5 * (1 - i / n) ** 1.4; X.strokeStyle = 'rgba(240,244,248,1)'; X.lineWidth = lerp(3.2, 7, i / n);
      X.beginPath(); X.moveTo(a[0] - dy / l * off, a[1] + dx / l * off); X.lineTo(b[0] - dy / l * off, b[1] + dx / l * off); X.stroke();
    }
  }
  X.restore();
}

// sprite('tanker', [lon,lat] | {x,y} | another sprite, {at, until, along:[[lon,lat]...], speed (loops/s) | travel (s, once), to:[lon,lat] | a sprite,
//   arrive (the moment it gets there: it leaves at arrive - travel), view:'top'|'side', heading (deg, a still top view), scale, size, label, on:'sea'|'land', in:'DJI',
//   hit:{explode, smoke, burn, shake, flash, until} (cause and effect: what happens where and when it arrives), miss:true, cause})
// A still object keeps its glossy 3/4 picture. A moving one is seen from above, turned to its heading, with a wake or a trail.
export function sprite(name, pos, o = {}) {
  const K = CRAFT[name] || null, moving = !!(o.along || o.to), topRel = AIDX.sprites['top/' + name];
  if (o.view === 'top' && !topRel) throw new Error(`sprite: no top view of '${name}' (engine/assets/sprites/top/${name}.png)`);
  const top = o.view === 'top' || (o.view !== 'side' && moving && !!topRel);
  const e = asset(top ? '/assets/' + topRel : spriteSrc(name)), burnRel = top ? AIDX.sprites['top/' + name + '_burning'] : null, eb = burnRel ? asset('/assets/' + burnRel) : null;
  const seed = hh(name.length * 7.1 + (o.phase || 0) * 13), tr = o.travel ?? (o.to ? 2 : null), ez = EASE[o.ease || (top ? 'linear' : 'io')];
  let t0 = early(o, 'sprite'), mv0 = o.moveAt != null ? when(o.moveAt) : t0, G = null;
  if (tr && o.arrive != null) { mv0 = when(o.arrive) - tr; if (o.at == null) t0 = mv0 - .12; }
  if (o.along) G = geoLine(o.smooth === false ? o.along : smooth(o.along, o.n || 120));
  else if (o.to) {
    if (!Array.isArray(pos)) throw new Error(`sprite '${name}': {to} needs a start point [lon, lat]`);
    const B = o.to.geoPos ? o.to.geoPos(mv0 + tr) : o.to; G = geoLine(Array.from({ length: 25 }, (_, i) => [lerp(pos[0], B[0], i / 24), lerp(pos[1], B[1], i / 24)]));
  }
  const medium = o.on || (K && K.m) || null, clr = medium === 'sea' && top && o.fit !== false ? clearance(G, pos) : null, air = medium === 'air';
  const it = add({ kind: 'sprite', name, K, top, moving, G, pos0: pos, on: medium, t0, t1: o.until != null ? when(o.until) : null, dur: o.dur ?? (top ? .4 : .5), outDur: .35, z: o.z ?? 6.5, sfx: o.sfx, mag: o.mag, from: o.from,
    cause: o.cause, miss: o.miss, hasTo: !!o.to, mv: [mv0, tr ? mv0 + tr : null], kmps: G ? (tr ? G.L / tr : G.L * (o.speed || .06)) : 0, sized: typeof o.size === 'number',
    place: !G && Array.isArray(pos) && o.in ? { what: 'sprite ' + name, pt: pos, in: o.in } : null,
    // progress along the route 0..1. After a hit (stopT) the ship is dead in the water: it glides to a stop.
    uAt(t) { if (this.stopT != null && t > this.stopT) t = this.stopT + .6 * (1 - Math.exp(-(t - this.stopT) / .6)); return tr ? ez(clamp((t - mv0) / tr)) : (Math.max(0, t - this.t0) * (o.speed || .06) + (o.phase || 0)) % 1; },
    geoPos(t) { return G ? geoAt(G, this.uAt(t) * G.L) : (pos && pos.geoPos ? pos.geoPos(t) : pos); },
    // length on screen (px) for the camera of this frame: the symbolic size by kind, never wider than ~45 % of the narrowest water on its route
    lenAt() {
      let L = (typeof o.size === 'number' ? o.size : sizeCurve(C.span) * REL(name)) * (o.scale ?? 1);
      if (clr != null && clr < 60) L = Math.min(L, Math.max(.62 * L, .9 * clr * pxPerKm() / (e.img ? e.img.width / e.img.height : .27)));
      return L;
    },
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0 || !e.img) return;
      if (!top) return this.side(t, a, b);
      const u = G ? this.uAt(t) : 0, d = G ? u * G.L : 0, gp = this.geoPos(t), [x, y, front] = P(gp[0], gp[1]); if (!front) return;
      const img = e.img, L = this.lenAt(), wI = L * img.width / img.height; this.Lnow = L;
      let ang = ((o.heading ?? 0) - 90) * D2R;
      if (G) { const dk = Math.max(.01, L * .55 / pxPerKm()), A = P(...geoAt(G, d - dk)), B = P(...geoAt(G, d + dk)); ang = Math.atan2(B[1] - A[1], B[0] - A[0]); }   // the chord over about one ship length: a turn is eased
      const al = clamp(a * 2.2) * b * (G && !tr ? clamp(Math.min(u, 1 - u) * 14) : 1); if (al <= 0) return;   // a looping ship fades at the two ends of its route
      const arc = air && o.to ? Math.sin(Math.PI * clamp(u)) : 0, sc = lerp(.84, 1, EASE.out(a)) * (1 - EASE.in(1 - b) * .2) * (1 + .12 * arc);
      const spd = G ? clamp(Math.abs(this.uAt(t) - this.uAt(t - .04)) / .04 / (tr ? 1 / tr : (o.speed || .06))) : 0;   // 0..1 of its cruise speed (0 once it has arrived or stopped)
      if (G && medium === 'sea' && spd > .02) wake(G, d, L * sc, wI * sc, al * spd, name === 'dhow' ? .6 : 1);
      if (G && air) trail(name, G, d, L * sc, wI * sc, al);
      const off = air ? 16 + 24 * arc : 5, sh = shadowOf(e), ks = L / img.height * sc, rot = ang + Math.PI / 2;
      X.save(); X.globalAlpha = al * (air ? .3 : .4); X.translate(x + SUN[0] * off, y + SUN[1] * off); X.rotate(rot); X.scale(ks, ks); X.drawImage(sh, -sh.width / 2, -sh.height / 2); X.restore();
      X.save(); X.translate(x, y); X.rotate(rot); X.scale(sc, sc);
      if (name === 'missile') { const fl = .75 + .25 * Math.sin(t * 61), r = wI * 1.5 * fl, g = X.createRadialGradient(0, L * .46, 0, 0, L * .46, r); X.globalCompositeOperation = 'lighter'; X.globalAlpha = al * .8;
        g.addColorStop(0, 'rgba(255,236,170,1)'); g.addColorStop(.35, 'rgba(255,140,30,.7)'); g.addColorStop(1, 'rgba(255,60,0,0)'); X.fillStyle = g; X.beginPath(); X.arc(0, L * .46, r, 0, 7); X.fill(); X.globalCompositeOperation = 'source-over'; }
      const bk = eb && eb.img && this.burnT != null ? clamp((t - this.burnT) / .16) : 0;
      if (bk < 1) { X.globalAlpha = al; X.drawImage(img, -wI / 2, -L / 2, wI, L); }
      if (bk > 0) { const wb = L * eb.img.width / eb.img.height; X.globalAlpha = al * bk; X.drawImage(eb.img, -wb / 2, -L / 2, wb, L);
        const fl = .7 + .3 * Math.sin(t * 17) * Math.sin(t * 7.3), r = wI * 1.25 * fl, g = X.createRadialGradient(wI * .08, -L * .2, 0, wI * .08, -L * .2, r); X.globalCompositeOperation = 'lighter'; X.globalAlpha = al * bk * .55;
        g.addColorStop(0, 'rgba(255,230,160,1)'); g.addColorStop(.4, 'rgba(255,130,30,.7)'); g.addColorStop(1, 'rgba(255,60,0,0)'); X.fillStyle = g; X.beginPath(); X.arc(wI * .08, -L * .2, r, 0, 7); X.fill(); }
      X.restore();
      if (o.label) pill(x, y - L * .5 - 56, o.label, col(o.labelBg || '#111a2b'), col(o.labelColor || '#fff'), TYPE.label, b * clamp(a * 2), lerp(.6, 1, EASE.back(a)));
    },
    // the glossy 3/4 picture of a still object: it drops in with a bounce and a soft shadow and bobs gently
    side(t, a, b) {
      const gp = this.geoPos(t); let x, y, ang = 0, mvg = false;
      if (G) { const d = this.uAt(t) * G.L, dk = Math.max(.01, 40 / pxPerKm()), A = P(...geoAt(G, d - dk)), B = P(...geoAt(G, d + dk)); [x, y] = P(gp[0], gp[1]); ang = Math.atan2(B[1] - A[1], B[0] - A[0]); mvg = true; }
      else [x, y] = Array.isArray(gp) ? P(gp[0], gp[1]) : [gp.x, gp.y];
      const img = e.img, w = typeof o.size === 'function' ? o.size(t) : (o.size || 170), h = w * img.height / img.width, out = 1 - b;
      const s = EASE.back(a) * (1 - EASE.in(out) * .9), flip = o.flip ?? (mvg && Math.cos(ang) < 0);
      const rot = o.rot != null ? o.rot * D2R : mvg ? clamp(Math.sin(ang), -1, 1) * .22 * (flip ? -1 : 1) : 0;
      const bob = o.bob === false || name === 'explosion' ? 0 : Math.sin(t * 2.1 + seed * 6.28) * 3.2;
      X.save(); X.globalAlpha = clamp(a * 3) * b; X.translate(x, y + bob - (name === 'explosion' ? 0 : (1 - EASE.out(a)) * 46));
      if (name !== 'explosion') { X.save(); X.scale(s, s); X.fillStyle = 'rgba(0,0,0,.3)'; X.filter = 'blur(7px)'; X.beginPath(); X.ellipse(SUN[0] * 8, h * .3 - bob, w * .4, h * .11, 0, 0, 7); X.fill(); X.restore(); }
      X.rotate(rot); X.scale(s * (flip ? -1 : 1), s); X.drawImage(img, -w / 2, -h / 2, w, h); X.restore();
      if (o.label) pill(x, y - h * .5 - 60, o.label, col(o.labelBg || '#111a2b'), col(o.labelColor || '#fff'), TYPE.label, b * clamp(a * 2), lerp(.6, 1, EASE.back(a)));
    } });
  if (o.to && o.to.geoPos) it.aim = () => { const B = o.to.geoPos(mv0 + tr); G = geoLine(Array.from({ length: 25 }, (_, i) => [lerp(pos[0], B[0], i / 24), lerp(pos[1], B[1], i / 24)])); it.G = G; it.kmps = G.L / tr; };
  // cause and effect: what it hits is hit exactly where and when it arrives (the explosion, the fire, the jolt are born from the arrival itself)
  if (o.to && o.hit) {
    const h = o.hit === true ? {} : o.hit, at = mv0 + tr, tgt = o.to.geoPos ? o.to : null, where = tgt || G.pts[G.pts.length - 1];
    it.t1 = at; it.outDur = .06; it.hitT = at;
    if (tgt && h.burn !== false) { tgt.burnT = Math.min(tgt.burnT ?? 1e9, at); tgt.stopT = Math.min(tgt.stopT ?? 1e9, at); }
    if (h.explode !== false) sprite('explosion', where, { at, early: 0, size: () => clamp(tgt && tgt.Lnow ? tgt.Lnow * 1.6 : 190, 150, 300) * (h.size || 1), until: at + (h.dur || .95), cause: it, sfx: h.sfx, z: 7 });
    if (h.smoke !== false) smoke(where, { at: at + .15, size: h.plume || 1, until: h.until, cause: it });
    if (h.shake !== false) shake(at, { amt: h.shake || 1.3 });
    if (h.flash !== false) flash(at, h.flash || .28);
  }
  return it;
}
// A4 photo('uss_carney', {at, until, frame:'full'|'hex'|'circle'|'card', pt:[lon,lat] (leader line), x, y, size, kenburns, grain, caption})
// full: wipes in over the whole frame in the reading direction, slow Ken Burns push, film grain, then hands back to the map
const NZ = document.createElement('canvas'); NZ.width = NZ.height = 256; { const nx = NZ.getContext('2d'), id = nx.createImageData(256, 256); let s = 99; for (let i = 0; i < id.data.length; i += 4) { s = (s * 16807) % 2147483647; const v = s % 256; id.data[i] = id.data[i + 1] = id.data[i + 2] = v; id.data[i + 3] = 255; } nx.putImageData(id, 0, 0); }
function grain(t, k, x0 = 0, y0 = 0, w = W, h = H) { const f = Math.floor(t * 24); X.save(); X.globalCompositeOperation = 'overlay'; X.globalAlpha *= k; X.translate(-hh(f) * 256, -hh(f + 1) * 256); X.fillStyle = X.createPattern(NZ, 'repeat'); X.fillRect(x0, y0, w + 512, h + 512); X.restore(); }
function cover(img, cx, cy, w, h, k = 1) { const s = Math.max(w / img.width, h / img.height) * k; X.drawImage(img, cx - img.width * s / 2, cy - img.height * s / 2, img.width * s, img.height * s); }
export function photo(name, o = {}) {
  const e = asset(photoSrc(name)), fr = o.frame || 'hex';
  return add({ kind: 'photo', name: fr, place: o.pt ? { what: o.what || 'photo ' + name, pt: o.pt, in: o.in } : null, flag: o.flagCode, flagCode: o.flagCode, needIn: o.needIn, say: o.say, t0: early(o, 'photo'), t1: o.until != null ? when(o.until) : null, dur: fr === 'full' ? .42 : .45, outDur: fr === 'full' ? .45 : .35, z: o.z ?? (fr === 'full' ? 8.6 : 7.5), sfx: o.sfx, mag: o.mag ?? (fr === 'full' ? 1.3 : 1),
    geo() { const r = o.size || 230; return { x: o.x ?? W / 2, y: o.y ?? H * .5, r, w: fr === 'card' ? r * 2.6 : r * 2, h: fr === 'card' ? r * 1.7 : r * 2 }; },
    res(t) { if (fr === 'full') return; const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const g = this.geo(); reserve(g.x - g.w / 2, g.y - g.h / 2, g.x + g.w / 2, g.y + g.h / 2); },
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0 || !e.img) return;
      const span = Math.max(1.5, (this.t1 ?? this.t0 + 4) - this.t0), u = clamp((t - this.t0) / span), kb = o.kenburns === false ? 1.04 : 1.04 + .1 * EASE.sine(u);
      if (fr === 'full') {
        const ea = EASE.io(a); X.save(); X.globalAlpha = Math.pow(b, .8); X.beginPath(); if (RTL) X.rect(W * (1 - ea), 0, W * ea + 1, H); else X.rect(0, 0, W * ea, H); X.clip();
        X.fillStyle = '#05070b'; X.fillRect(0, 0, W, H); cover(e.img, W / 2 + (o.pan || 0) * 60 * u, H / 2, W, H, kb);
        if (o.grain !== false) grain(t, .14);
        const v = X.createRadialGradient(W / 2, H / 2, H * .25, W / 2, H / 2, H * .75); v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,.55)'); X.fillStyle = v; X.fillRect(0, 0, W, H);
        X.restore();
        if (ea < 1) { const ex = RTL ? W * (1 - ea) : W * ea; X.save(); X.globalAlpha = Math.sin(Math.PI * ea); X.shadowColor = '#ffd23f'; X.shadowBlur = 40; X.fillStyle = '#fff6d8'; X.fillRect(ex - 4, 0, 8, H); X.restore(); }
        if (o.caption) { const ca = clamp((t - this.t0 - .35) / .35); if (ca > 0) pill(W / 2, H - SAFE.b - 200, o.caption, 'rgba(10,14,22,.55)', '#fff', TYPE.label, ca * b, lerp(.8, 1, EASE.out(ca)), true); }
        return;
      }
      const g = this.geo(), s = EASE.back(a) * (1 - EASE.in(1 - b) * .85), shape = () => { if (fr === 'hex') hexPath(g.r); else if (fr === 'card') rr(-g.w / 2, -g.h / 2, g.w, g.h, 26); else { X.beginPath(); X.arc(0, 0, g.r, 0, 7); } };
      if (o.pt) { const [ax, ay] = P(...o.pt); leader(ax, ay, g.x, g.y, g.h * s, col(o.color || '#ffd23f'), clamp(a * 1.6) * b, t); }
      X.save(); X.globalAlpha = clamp(a * 3) * b; X.translate(g.x, g.y); X.scale(s, s);
      X.save(); X.shadowColor = 'rgba(0,0,0,.55)'; X.shadowBlur = 34; X.shadowOffsetY = 12; shape(); X.fillStyle = '#0b1220'; X.fill(); X.restore();
      X.save(); shape(); X.clip(); cover(e.img, (o.pan || 0) * 30 * u, 0, g.w, g.h, kb); if (o.grain !== false) grain(t, .1, -g.w / 2, -g.h / 2, g.w, g.h); X.restore();
      shape(); X.lineJoin = 'round'; X.lineWidth = 9; X.strokeStyle = '#ffffff'; X.stroke(); X.lineWidth = 3; X.strokeStyle = col(o.color || '#ffd23f'); X.stroke();
      X.restore();
      if (o.caption) pill(g.x, g.y + g.h / 2 * s + 52, o.caption, '#111a2b', '#fff', TYPE.label, clamp((a - .3) * 2) * b, 1);
    } });
}
// flagPin('USA', [lon,lat], {at, until, x, y, size, in:'DJI'}): a round flag on a leader line to an exact spot. The flag always comes
// from the country code (never a free file name), so it cannot land on the wrong country. in: the country the spot itself
// lies in (a US base in Djibouti: in:'DJI'); logic() checks it against the borders, so the pin cannot sit in the wrong place.
export function flagPin(code, pt, o = {}) {
  const c = country(code); if (!c || !ISO2[c.iso]) throw new Error('flagPin: no flag for ' + code);
  return photo(flagSrc(c.iso), { frame: 'circle', size: 62, grain: false, kenburns: false, sfx: false, pt, ...o, what: 'flag ' + c.iso, flagCode: c.iso, needIn: true });
}
// A5 mapStyle('flat'|'sepia'|'night'|'satellite', t, {dur}): the whole map changes world, swept across behind a bright edge
const SV = { satellite: [0, 0, 0], flat: [1, 0, 0], sepia: [0, 1, 0], night: [0, 0, 1] }, STY = [];
export function mapStyle(kind, t, o = {}) { if (!SV[kind]) throw new Error('mapStyle: ' + kind); STY.push({ t: when(t), v: SV[kind], dur: o.dur ?? .75, kind }); STY.sort((a, b) => a.t - b.t); }
function styleAt(t) {
  let A = [0, 0, 0], B = A, p = 1.1;
  for (const s of STY) { if (t >= s.t + s.dur) { A = B = s.v; p = 1.1; } else if (t >= s.t) { B = s.v; p = -.05 + 1.12 * EASE.io((t - s.t) / s.dur); } }
  return { A, B, p };
}
// A6 hop(t0, t1, {lon, lat, span}, {height}): pull back to continent height, then dive onto the next target (between chapters)
export function hop(t0, t1, target, o = {}) {
  const a = when(t0), b = when(t1), s = camAt(a), m = lerp(a, b, o.peakAt ?? .45);
  cam(a, { lon: s.lon, lat: s.lat, span: s.span, cy: s.cy });
  cam(m, { lon: lerp(s.lon, target.lon, .5), lat: lerp(s.lat, target.lat, .5), span: o.height ?? Math.max(48, s.span * 5, target.span * 5), cy: .42, ease: 'io' });
  cam(b, { cy: .42, ...target, ease: 'io', dive: true });
}
// A9 currents(pts, {at, until, color, count}): glowing chevrons drifting along a sea current
export function currents(pts, o = {}) {
  const pp = smooth(pts, 120), n = o.count || 7;
  return add({ kind: 'currents', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: .6, z: o.z ?? 2.5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const sl = screenLine(pp), c = col(o.color || '#7fe3ff'), sz = o.size || 18;
      X.save(); X.lineCap = 'round'; X.lineJoin = 'round'; X.shadowColor = c; X.shadowBlur = 16;
      for (let i = 0; i < n; i++) {
        const u = ((t - this.t0) * (o.speed || .09) + i / n) % 1, [x, y, ang] = along(sl, u * sl.L); X.globalAlpha = a * b * clamp(Math.min(u, 1 - u) * 6) * .9;
        X.save(); X.translate(x, y); X.rotate(ang); X.strokeStyle = c; X.lineWidth = 5;
        for (const d of [0, -sz * .9]) { X.beginPath(); X.moveTo(d - sz * .6, -sz * .7); X.lineTo(d + sz * .4, 0); X.lineTo(d - sz * .6, sz * .7); X.stroke(); }
        X.restore();
      }
      X.restore();
    } });
}
// A9 smoke([lon,lat] | a sprite, {at, until, size, fire:true, cause}): a rising plume (deterministic particles) with a flickering fire at its foot.
// v6: it leans with the one scene wind (wind(v)), it can ride on a sprite, and a fire must have a cause (logic check 6).
export function smoke(pt, o = {}) {
  const N = o.count || 26;
  return add({ kind: 'smoke', cause: o.cause, fire: o.fire !== false, ownWind: o.wind, t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: .8, outDur: .9, z: o.z ?? 6.8, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const gp = pt.geoPos ? pt.geoPos(t) : pt, [x, y] = P(gp[0], gp[1]), k = (o.size || 1), tt = t - this.t0;
      X.save();
      for (let i = 0; i < N; i++) {
        const ag = (tt * .32 + i / N) % 1, r = (16 + 70 * ag) * k, sx = (hh(i * 3.7) - .5) * 22 * k + Math.sin(tt * .8 + i) * 10 * ag * k + ag * 60 * k * WIND;
        X.globalAlpha = a * b * (1 - ag) * Math.min(1, ag * 8) * .5; const g = X.createRadialGradient(x + sx, y - ag * 260 * k, 0, x + sx, y - ag * 260 * k, r);
        const sh = 40 + 30 * hh(i); g.addColorStop(0, `rgba(${sh},${sh},${sh + 4},.95)`); g.addColorStop(1, `rgba(${sh},${sh},${sh},0)`); X.fillStyle = g; X.beginPath(); X.arc(x + sx, y - ag * 260 * k, r, 0, 7); X.fill();
      }
      if (o.fire !== false) {
        X.globalCompositeOperation = 'lighter';
        for (let i = 0; i < 6; i++) { const fl = .7 + .3 * Math.sin(t * 17 + i * 2.1), r = (18 + 10 * i) * k * fl, fx = x + (hh(i) - .5) * 26 * k, fy = y - i * 7 * k;
          X.globalAlpha = a * b * .55; const g = X.createRadialGradient(fx, fy, 0, fx, fy, r); g.addColorStop(0, 'rgba(255,240,180,1)'); g.addColorStop(.4, 'rgba(255,140,30,.8)'); g.addColorStop(1, 'rgba(255,60,0,0)'); X.fillStyle = g; X.beginPath(); X.arc(fx, fy, r, 0, 7); X.fill(); }
      }
      X.restore();
    } });
}
// A9 clouds({at, until, center:[lon,lat], spread (deg), count, k}): soft clouds that ride on the globe (seen when zoomed out)
export function clouds(o = {}) {
  let s = o.seed || 3; const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647, c0 = o.center || [40, 15], sp = o.spread || 25;
  const CL = Array.from({ length: o.count || 22 }, () => [c0[0] + (rnd() - .5) * sp * 2, c0[1] + (rnd() - .5) * sp, .4 + rnd() * .9, rnd()]);
  return add({ kind: 'clouds', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: 1, outDur: 1, z: o.z ?? 1.2, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const vis = clamp((C.span - 8) / 25) * a * b * (o.k ?? .7); if (vis <= .01) return;
      X.save();
      for (const [lo, la, sz, ph] of CL) {
        const [x, y, f] = P(lo + t * .08 * (o.wind ?? 1), la); if (!f) continue; const r = sz * 2.2 * C.k * D2R;
        if (x < -r || x > W + r || y < -r || y > H + r) continue;
        for (let j = 0; j < 4; j++) { const ox = (hh(ph * 9 + j) - .5) * r * 1.4, oy = (hh(ph * 5 + j) - .5) * r * .5, rr2 = r * (.5 + .5 * hh(ph + j * 3));
          const g = X.createRadialGradient(x + ox, y + oy, 0, x + ox, y + oy, rr2); g.addColorStop(0, 'rgba(255,255,255,.55)'); g.addColorStop(1, 'rgba(255,255,255,0)');
          X.globalAlpha = vis; X.fillStyle = g; X.beginPath(); X.arc(x + ox, y + oy, rr2, 0, 7); X.fill(); }
      }
      X.restore();
    } });
}
// A10 dateBadge([['2014', t], ['2023', t2]], {until, x, y}): a small fixed badge at the top; each change rolls the digits like a reel
export function dateBadge(list, o = {}) {
  const L = list.map(([s, t]) => [String(s), when(t)]).sort((a, b) => a[1] - b[1]);
  return add({ kind: 'date', t0: L[0][1], t1: o.until != null ? when(o.until) : null, dur: .4, z: o.z ?? 8.8, sfx: o.sfx, ticks: L.slice(1).map(l => l[1]),
    res(t) { const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const x = o.x ?? (RTL ? W - 150 : 150), y = o.y ?? 205; reserve(x - 120, y - 46, x + 120, y + 46); },
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const x = o.x ?? (RTL ? W - 150 : 150), y = o.y ?? 205, fs = 66;
      let i = 0; L.forEach((l, k) => { if (t >= l[1]) i = k; });
      const u = i > 0 ? clamp((t - L[i][1]) / .38) : 1, prev = i > 0 ? L[i - 1][0] : null;
      X.save(); X.globalAlpha = clamp(a * 2) * b; glass(x, y, 220, 84, 42, 'rgba(10,14,22,.55)', 'rgba(255,210,63,.65)');
      X.beginPath(); X.rect(x - 110, y - 42, 220, 84); X.clip();
      if (prev && u < 1) { X.save(); X.globalAlpha *= 1 - u; engraved(x, y - EASE.io(u) * 70, prev, fs, '#ffd23f'); X.restore(); }
      X.save(); X.globalAlpha *= u; engraved(x, y + (1 - EASE.out(u)) * 70, L[i][0], fs, '#ffd23f'); X.restore();
      X.restore();
    } });
}
// A11 crack('ERI', 'ETH', {at, until, color}): the shared border cracks and glows (tension between two countries)
// A11 lift('YEM', {at, until, color, h}): the key country is raised like a tile with a deep shadow
function sharedEdges(a, b) {
  const key = (x1, y1, x2, y2) => `${x1},${y1},${x2},${y2}`, S = new Set();
  for (const r of b.r) for (let i = 0; i + 3 < r.length; i += 2) S.add(key(r[i], r[i + 1], r[i + 2], r[i + 3]));
  const segs = [];
  for (const r of a.r) for (let i = 0; i + 3 < r.length; i += 2) if (S.has(key(r[i + 2], r[i + 3], r[i], r[i + 1])) || S.has(key(r[i], r[i + 1], r[i + 2], r[i + 3]))) segs.push([r[i] / 1e4, r[i + 1] / 1e4, r[i + 2] / 1e4, r[i + 3] / 1e4]);
  return segs;
}
export function crack(ca, cb, o = {}) {
  const A = country(ca), B = country(cb); if (!A || !B) throw new Error('crack: unknown country'); const segs = sharedEdges(A, B);
  return add({ kind: 'crack', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? 1.1, z: o.z ?? 4.5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0 || !segs.length) return; const n = Math.ceil(segs.length * EASE.io(a)), c = col(o.color || '#ff3b4a'), fl = .75 + .25 * Math.sin(t * 23) * Math.sin(t * 7);
      const p = new Path2D(); for (let i = 0; i < n; i++) { const s = segs[i], A1 = P(s[0], s[1]), B1 = P(s[2], s[3]), j = (hh(i * 1.7) - .5) * 7; p.moveTo(A1[0] + j, A1[1] - j); p.lineTo(B1[0] - j, B1[1] + j); }
      X.save(); X.globalAlpha = b; X.lineCap = 'round'; X.lineJoin = 'round'; X.shadowColor = c; X.shadowBlur = 34 * fl; X.strokeStyle = c; X.lineWidth = 9; X.stroke(p);
      X.shadowBlur = 10; X.strokeStyle = '#fff4e0'; X.lineWidth = 2.6 * fl; X.stroke(p); X.restore();
    } });
}
export function lift(code, o = {}) {
  const cs = [].concat(code).map(k => country(k)), c = col(o.color || '#ffd23f');
  return add({ kind: 'lift', t0: when(o.at ?? 0), t1: o.until != null ? when(o.until) : null, dur: o.dur ?? .7, z: o.z ?? 3.5, sfx: o.sfx,
    draw(t) {
      const [a, b] = life(this, t); if (a <= 0 || b <= 0) return; const { p } = countryPath(cs), e = EASE.back(a) * b, h = (o.h ?? 22) * e;
      X.save(); X.globalAlpha = clamp(a * 3) * b; X.fillStyle = 'rgba(0,0,0,.5)'; X.filter = 'blur(14px)'; X.translate(10 * e, 14 * e); X.fill(p, 'evenodd'); X.restore();
      X.save(); X.globalAlpha = clamp(a * 3) * b; X.translate(0, -h);
      X.save(); X.globalCompositeOperation = 'color'; X.fillStyle = rgba(c, .85); X.fill(p, 'evenodd'); X.restore();
      X.fillStyle = rgba(c, .5); X.fill(p, 'evenodd'); X.lineJoin = 'round'; X.shadowColor = '#fff'; X.shadowBlur = 18; X.lineWidth = 4.5; X.strokeStyle = '#fff'; X.stroke(p); X.restore();
    } });
}

// ------------------------------------------------------------------ v6: the logic checklist (run by start(), before any frame)
// 1 direction  2 medium (water / land)  3 relative speeds  4 relative sizes  5 flags and places  6 cause and effect
// 7 picture = speech (every number and country on screen is spoken)  8 physics (one wind, one sun)
// A failed check sets window.ERR: render.mjs prints the list and stops, so a wrong frame is never rendered.
const NUMW = { صفر: 0, واحد: 1, واحده: 1, وحده: 1, اثنين: 2, اتنين: 2, اثنان: 2, اثنا: 2, اتنا: 2, جوج: 2, ثلاثه: 3, ثلاث: 3, تلاته: 3, تلات: 3, اربعه: 4, اربع: 4, ربعه: 4, خمسه: 5, خمس: 5,
  سته: 6, ست: 6, سبعه: 7, سبع: 7, ثمانيه: 8, ثمان: 8, تمانيه: 8, تمنيه: 8, تمن: 8, تسعه: 9, تسع: 9, تسعود: 9, عشره: 10, عشر: 10, حداشر: 11, احدعشر: 11, اتناشر: 12, اثناعشر: 12, طناش: 12, تلاتاشر: 13, اربعتاشر: 14,
  خمستاشر: 15, ستاشر: 16, سبعتاشر: 17, تمنتاشر: 18, تسعتاشر: 19, عشرين: 20, عشرون: 20, ثلاثين: 30, ثلاثون: 30, تلاتين: 30, اربعين: 40, اربعون: 40, خمسين: 50, خمسون: 50, ستين: 60, ستون: 60, سبعين: 70, سبعون: 70,
  ثمانين: 80, ثمانون: 80, تمانين: 80, تسعين: 90, تسعون: 90, ميه: 100, مئه: 100, مايه: 100, ميتين: 200, مئتين: 200, الف: 1000, الاف: 1000, الفين: 2000, مليون: 1e6, ملايين: 1e6, مليونين: 2e6, مليار: 1e9, مليارات: 1e9,
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12, twenty: 20, thirty: 30, forty: 40, fifty: 50, sixty: 60, seventy: 70, eighty: 80, ninety: 90, hundred: 100, thousand: 1000, million: 1e6, billion: 1e9 };
// spoken names that differ from the atlas name
const ALIAS = { USA: ['امريكا', 'الولايات', 'america'], GBR: ['بريطانيا', 'انجلترا', 'britain'], ARE: ['الامارات'], RUS: ['روسيا'], KOR: ['كوريا'], IRN: ['ايران'], TUR: ['تركيا'], CHN: ['الصين'], SOL: ['ارض الصومال'] };
const forms = w => { const S = new Set([nw(w)]); for (const x of [...S]) if (x.length > 3 && 'وفبكل'.includes(x[0])) S.add(x.slice(1)); for (const x of [...S]) if (x.length > 4 && (x.startsWith('ال') || x.startsWith('لل'))) S.add(x.slice(2)); return S; };
const heard = (name, a, b) => { const want = forms(String(name).split(/\s+/)[0]); return WORDS.some(w => w[1] >= a && w[1] <= b && [...forms(w[0])].some(f => want.has(f))); };
function spoken(a, b) {   // every number that the words between a and b can mean ('واحد وعشرين ألف' -> 1, 21, 21000)
  const ws = WORDS.filter(w => w[1] >= a && w[1] <= b).map(w => { let s = nw(w[0]); if (/^\d+$/.test(s)) return +s; if (NUMW[s] == null && s.length > 2 && 'وفبل'.includes(s[0])) s = s.slice(1); if (NUMW[s] == null && s.startsWith('ال')) s = s.slice(2); return NUMW[s]; });
  const out = new Set();
  for (let i = 0; i < ws.length; i++) { if (ws[i] == null) continue; let tot = 0, cur = 0;
    for (let j = i; j < ws.length && ws[j] != null; j++) { const v = ws[j]; if (v >= 1000) { tot += (cur || 1) * v; cur = 0; } else if (v === 100) cur = (cur || 1) * 100; else cur += v; out.add(tot + cur); } }
  return out;
}
function logic() {
  const R = { ok: [], info: [], fail: [] }, f = (n, s) => R.fail.push(`(${n}) ${s}`), S = ITEMS.filter(i => i.kind === 'sprite'), fx = p => `${(+p[0]).toFixed(3)},${(+p[1]).toFixed(3)}`, ts = t => t.toFixed(2) + ' s';
  // 1 direction: whatever has a nose and moves is seen from above and turned to its heading
  const mv = S.filter(i => i.moving);
  for (const i of mv) if (i.K && !i.top) f(1, `'${i.name}' (${ts(i.t0)}) moves but has no top view, it would travel sideways: add engine/assets/sprites/top/${i.name}.png`);
  R.ok.push(`(1) direction: ${mv.filter(i => i.top).length} moving object(s) seen from above and turned to their heading in every frame`);
  // 2 medium: ships on water, vehicles on land (a sample every ~4 km of every route; canals are allowed)
  let routes = 0, spots = 0;
  const scan = (what, G, want) => { routes++; const n = Math.max(2, Math.ceil(G.L / 4)), runs = []; let inRun = false;
    for (let k = 0; k <= n; k++) { const p = geoAt(G, G.L * k / n), c = landAt(p), bad = want === 'sea' ? !!(c && !inCanal(p)) : !c; if (bad && !inRun) runs.push(fx(p) + (c ? ' ' + c.en : '')); inRun = bad; }
    if (runs.length) f(2, `${what} ${want === 'sea' ? 'crosses land' : 'leaves the land'} ${runs.length} time(s), first points: ${runs.slice(0, 8).join(' | ')}`); };
  for (const i of S) { if (!i.on || i.on === 'air') continue;
    if (i.G) scan(`'${i.name}' route (${ts(i.t0)})`, i.G, i.on);
    else if (Array.isArray(i.pos0)) { spots++; const c = landAt(i.pos0); if (i.on === 'sea' ? c : !c) f(2, `'${i.name}' at ${fx(i.pos0)} is ${c ? 'on land (' + c.en + ')' : 'in the sea'} but belongs ${i.on === 'sea' ? 'on water' : 'on land'}`); } }
  for (const i of ITEMS) if (i.kind === 'ships') scan(`ships() route (${ts(i.t0)})`, i.G, 'sea'); else if (i.kind === 'path' && i.sea) scan(`sea path (${ts(i.t0)})`, i.G, 'sea');
  R.ok.push(`(2) medium: ${routes} route(s) and ${spots} fixed object(s) checked against the coastlines`);
  // 3 relative speeds while two things move at the same time (km of map per second of video)
  const M = S.filter(i => i.moving && i.K && i.kmps > 0).map(i => ({ n: i.name, v: i.K.v, s: i.kmps, a: i.mv[0], b: i.mv[1] ?? i.t1 ?? 1e9 })).concat(ITEMS.filter(i => i.kind === 'ships').map(i => ({ n: 'ships()', v: 33, s: i.kmps, a: i.t0, b: i.t1 ?? 1e9 })));
  for (const p of M) for (const q of M) { if (p.v < q.v * 1.5 || Math.min(p.b, q.b) - Math.max(p.a, q.a) < .3) continue; const need = clamp(Math.sqrt(p.v / q.v), 1.25, 2.2);
    if (p.s < q.s * need) f(3, `${p.n} (${p.s.toFixed(0)} km/s on screen) must be at least ${need.toFixed(1)}x faster than ${q.n} (${q.s.toFixed(0)} km/s) while both move, around ${ts(Math.max(p.a, q.a))}`); }
  R.ok.push(`(3) speeds: ${M.map(m => `${m.n} ${m.s.toFixed(0)}`).join(', ') || 'nothing moves'} (km/s on screen), order follows the real speeds`);
  // 4 relative sizes of two top-view objects on screen together (measured with the camera of the middle of their overlap)
  const TP = S.filter(i => i.top && i.K); let pairs = 0;
  for (const p of TP) for (const q of TP) { if (p === q || p.K.len < q.K.len * 1.3) continue; const a = Math.max(p.t0, q.t0), b = Math.min(p.t1 ?? 1e9, q.t1 ?? 1e9, END); if (b - a < .3) continue;
    setCam(camFinal((a + b) / 2)); const lp = p.lenAt(), lq = q.lenAt(); pairs++; if (lp <= lq * 1.02) f(4, `'${p.name}' (${p.K.len} m) is drawn ${lp.toFixed(0)} px long, not larger than '${q.name}' (${q.K.len} m, ${lq.toFixed(0)} px) around ${ts((a + b) / 2)}`); }
  R.ok.push(`(4) sizes: ${pairs} pair(s) of objects on screen together keep their real size order`);
  // 5 flags and places: a flag only from its own country code; a pinned flag must say which country its spot is in
  let nf = 0;
  for (const i of ITEMS) { if (i.flag) nf++; const pl = i.place; if (!pl) continue; const c = landAt(pl.pt), w = pl.in;
    if (i.needIn && !w) f(5, `${pl.what} at ${fx(pl.pt)}: say where the spot is (in:'DJI' or in:'sea'), the map says ${c ? c.en : 'sea'}`);
    else if (w && !(w === 'sea' ? !c : c ? (c.iso === w || c.a3 === w || w === 'land') : (country(w) && coastKm(country(w), pl.pt) <= 2))) f(5, `${pl.what} at ${fx(pl.pt)} should be in ${w} but the map says ${c ? c.en + ' (' + c.iso + ')' : 'sea'}`);
    else R.info.push(`place: ${pl.what} @ ${fx(pl.pt)} -> ${c ? c.en : w && w !== 'sea' ? 'the coast of ' + country(w).en + ' (' + coastKm(country(w), pl.pt).toFixed(1) + ' km off the simplified border)' : 'sea'}${w ? ' (asserted ' + w + ')' : ''}`); }
  // a flight: where it leaves from and where it lands (from:'YEM' asserts the launch country)
  for (const i of S) if (i.hasTo) { const A = i.G.pts[0], B = i.G.pts[i.G.pts.length - 1], ca = landAt(A), cb = landAt(B);
    if (i.from && !(ca && (ca.iso === i.from || ca.a3 === i.from))) f(5, `'${i.name}' should leave from ${i.from} but ${fx(A)} is ${ca ? ca.en : 'in the sea'}`);
    else R.info.push(`flight: ${i.name} ${fx(A)} (${ca ? ca.en : 'sea'}) -> ${fx(B)} (${cb ? cb.en : 'sea'}), ${i.G.L.toFixed(0)} km in ${(i.mv[1] - i.mv[0]).toFixed(2)} s`); }
  // a ruler that says 20 km must lie between two points that really are about 20 km apart
  for (const i of ITEMS) if (i.kind === 'measure' && i.val && /^(كم|km)$/i.test(i.unit)) { const d = hav(i.ends[0], i.ends[1]); if (Math.abs(d - i.val) > i.val * .2) f(5, `measure says ${i.val} km at ${ts(i.t0)} but its two ends are ${d.toFixed(1)} km apart (${fx(i.ends[0])} - ${fx(i.ends[1])})`); else R.info.push(`measure: ${i.val} km drawn across ${d.toFixed(1)} km`); }
  R.ok.push(`(5) flags and places: ${nf} flag(s), each taken from its own country code; ${ITEMS.filter(i => i.place && i.place.in).length} asserted place(s)`);
  // 6 cause and effect: a missile that arrives does something; a fire or an explosion has a cause at the same place and moment
  let fxn = 0;
  for (const i of S) if (i.hasTo && (i.name === 'missile' || i.name === 'drone') && i.hitT == null && !i.miss) f(6, `'${i.name}' (${ts(i.t0)}) flies in and nothing happens: give it hit:{...} or miss:true`);
  for (const i of ITEMS) if ((i.kind === 'sprite' && i.name === 'explosion') || (i.kind === 'smoke' && i.fire)) { fxn++;
    if (!i.cause) f(6, `${i.kind === 'smoke' ? 'fire/smoke' : 'explosion'} at ${ts(i.t0)} has no cause: let the missile make it (hit:{...}) or state cause:'...'`);
    else if (i.cause.hitT != null && Math.abs(i.t0 - i.cause.hitT) > .25) f(6, `${i.kind} at ${ts(i.t0)} is not at the moment its cause arrives (${ts(i.cause.hitT)})`); }
  R.ok.push(`(6) cause and effect: ${fxn} fire/explosion effect(s), each tied to its cause`);
  // 7 picture = speech: every number and every named/flagged country on screen is spoken close to the moment it appears
  let nn = 0, nc = 0;
  const said = (i, a, b) => [].concat(i.say).every(s => heard(s, a, b));
  for (const i of ITEMS) {
    if ((i.kind === 'number' || i.kind === 'measure') && i.val != null && i.say !== false && WORDS.length) { nn++; const a = i.t0 - 1.6, b = i.t0 + 3.2;
      if (i.say ? !said(i, a - .6, b + .6) : !spoken(a, b).has(i.val)) f(7, `${i.kind} ${i.val} at ${ts(i.t0)}: ${i.say ? `the word '${i.say}' is not spoken near it` : 'no spoken number matches it'} (say:'word' ties it to the word that justifies it, say:false waives it)`); }
    const cs = i.kind === 'region' && i.shown ? i.codes : i.flagCode ? [country(i.flagCode)] : null;
    if (cs && i.say !== false && WORDS.length) for (const c of cs) { nc++; const a = i.t0 - 1.6, b = i.t0 + 2.6;
      if (!(i.say ? said(i, a, b) : [RTL ? c.ar : c.en, ...(ALIAS[c.iso] || [])].some(n => heard(n, a, b)))) f(7, `${c.en} is shown at ${ts(i.t0)} but its name is not spoken near it (say:'word' or say:false)`); }
  }
  R.ok.push(`(7) picture = speech: ${nn} number(s) and ${nc} country name(s)/flag(s) matched to spoken words`);
  // 8 physics: one wind for every plume, one sun for every shadow (both are engine constants; a private wind is refused)
  for (const i of ITEMS) if (i.kind === 'smoke' && i.ownWind != null && i.ownWind !== WIND) f(8, `smoke at ${ts(i.t0)} has its own wind (${i.ownWind}); the scene wind is ${WIND}: use wind(v) once for the whole video`);
  R.ok.push(`(8) physics: one wind (${WIND}) for ${ITEMS.filter(i => i.kind === 'smoke').length} plume(s) and every trail, one sun (${SUN}) for every cast shadow`);
  return R;
}

// cut points for the sound mix ('cut:N') and the contact sheets
const CUTS = []; export function cut(t) { CUTS.push(when(t)); }
export let END = CFG.end || (TIMING.dur ? TIMING.dur + .6 : 10);
export function end(t) { END = when(t); }

// ------------------------------------------------------------------ frame
function frame(t) {
  setCam(camFinal(t)); drawGlobe(t);
  BOXES = []; reserveProgress(t); if (CAP && (!CAP.yield || layerBusy(t) < .99)) reserve(0, CAP.y - CAP.size * .85, W, CAP.y + CAP.size * .85); const n0 = BOXES.length; for (const it of ITEMS) if (it.res) it.res(t); reserveLogo(t, n0);
  X.setTransform(1, 0, 0, 1, 0, 0); X.globalAlpha = 1; X.clearRect(0, 0, W, H); X.drawImage(gl.canvas, 0, 0);
  for (const it of [...ITEMS].sort((a, b) => (a.z - b.z) || (a.t0 - b.t0))) it.draw(t);
  const v = X.createRadialGradient(W / 2, H * .45, H * .32, W / 2, H * .45, H * .78); v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, `rgba(0,0,0,${LOOK === 'atlas' ? .32 : .42})`); X.fillStyle = v; X.fillRect(0, 0, W, H);
  drawCaptions(t); drawProgress(t); drawLogo(t);
  for (const [ft, k] of FLASH) { const u = (t - ft) / .32; if (u >= 0 && u < 1) { X.save(); X.globalCompositeOperation = 'lighter'; X.fillStyle = `rgba(255,236,210,${k * (1 - u) ** 2})`; X.fillRect(0, 0, W, H); X.restore(); } }
}
export function start() {
  window.seek = t => { frame(t); return true; };
  window.END = END; const cs = [...new Set([0, ...CUTS])].sort((a, b) => a - b); window.SHOTLIST = cs.map((c, i) => [c, cs[i + 1] ?? END]);
  window.CAMS_AT = fps => { const o = []; for (let f = 0; f <= Math.round(END * fps); f++) { const c = camFinal(f / fps); o.push([f / fps, c.lon, c.lat, kOf(c.span), c.cy]); } return o; };
  const moves = CAMS.slice(1).map((b, i) => { const a = CAMS[i], d = Math.hypot((b.lon - a.lon) * Math.cos(b.lat * D2R), b.lat - a.lat) / Math.min(a.span, b.span);
    return { kind: 'cam', t: a.t, t1: b.t, dur: b.t - a.t, mag: +(Math.abs(Math.log(b.span / a.span)) + d).toFixed(2), dir: b.span < a.span ? 'in' : 'out', dive: !!b.dive }; });
  window.EVENTS = ITEMS.map(i => ({ kind: i.kind, t: i.t0, t1: i.t1, sfx: i.sfx, name: i.name, mag: i.mag, ticks: i.ticks, settle: i.settle, stops: i.stops }))
    .concat(FLASH.map(f => ({ kind: 'flash', t: f[0] })), FX.map(f => ({ kind: f.kind, t: f.t, mag: f.amt, sfx: f.sfx })), CUTS.map(t => ({ kind: 'cut', t })), STY.map(s => ({ kind: 'style', t: s.t, name: s.kind })), moves).sort((a, b) => a.t - b.t);
  // A1 guard: warn when three text layers would meet (two titles/top numbers at once, the caption yields to them)
  const LY = ITEMS.filter(i => i.layer); for (const p of LY) for (const q of LY) if (p !== q && p.t0 < q.t0 && (p.t1 == null || p.t1 > q.t0 + .05)) console.log(`layers: ${p.kind} ${p.t0.toFixed(2)} still on at ${q.kind} ${q.t0.toFixed(2)}`);
  // v6: the logic checklist. It needs the images (sizes come from their proportions), so it runs once they are loaded.
  for (const i of ITEMS) if (i.aim) i.aim();
  Promise.all(PRE).then(() => { const LG = logic(); window.LOGIC = LG; if (LG.fail.length) window.ERR = 'LOGIC CHECK FAILED (' + LG.fail.length + '): the render is stopped\n  ' + LG.fail.join('\n  '); else window.READY = true; });
}
// item 9: test engine/data/places.json with the same rules as logic() (places: item 5, lanes: item 2 after smooth() as path() draws them)
function checkPlaces() {
  const R = { ok: [], fail: [] }, fx = p => `${(+p[0]).toFixed(3)},${(+p[1]).toFixed(3)}`;
  for (const [k, p] of Object.entries(PLACES.places)) { const c = landAt(p.pt), w = p.in;
    const good = w === 'sea' ? (!c || inCanal(p.pt)) : c ? (c.iso === w || c.a3 === w) : (country(w) && coastKm(country(w), p.pt) <= 2);
    if (p.off_map) R.ok.push(`${k}: off the 1:10m borders (${c ? c.en : 'sea'}), not testable, ${w} by its source`);
    else if (!good) R.fail.push(`place ${k} at ${fx(p.pt)} should be in ${w} but the map says ${c ? c.en + ' (' + c.a3 + ')' : 'sea'}${w !== 'sea' && country(w) ? ', ' + coastKm(country(w), p.pt).toFixed(1) + ' km from its coast' : ''}`);
    else R.ok.push(`${k}: ${c ? c.en : (w === 'sea' ? 'sea' : 'the coast of ' + country(w).en)}`); }
  const scan = (k, pts, want) => { const G = geoLine(smooth(pts, 120)), n = Math.max(2, Math.ceil(G.L / 4)), runs = []; let inRun = false;
    for (let i = 0; i <= n; i++) { const q = geoAt(G, G.L * i / n), c = landAt(q), bad = want === 'sea' ? !!(c && !inCanal(q)) : (!c && i > 0 && i < n); if (bad && !inRun) runs.push(fx(q) + (c ? ' ' + c.en : '')); inRun = bad; }
    if (runs.length) R.fail.push(`${want === 'sea' ? 'lane' : 'pipeline'} ${k} ${want === 'sea' ? 'crosses land' : 'leaves the land'} ${runs.length} time(s): ${runs.slice(0, 8).join(' | ')}`);
    else R.ok.push(`${k}: ${G.L.toFixed(0)} km, all on ${want}`); };
  for (const [k, l] of Object.entries(PLACES.lanes)) scan(k, l.pts, 'sea');
  for (const [k, l] of Object.entries(PLACES.pipelines || {})) scan(k, l.pts, 'land');
  return R;
}
window.DBG = { gl, camAt, ITEMS, WORDS, landAt, clearKm, checkPlaces, logoMs(n = 240) { const t0 = performance.now(); for (let i = 0; i < n; i++) drawLogo(i * 12 / n); X.getImageData(0, 0, 1, 1); return (performance.now() - t0) / n; }, logo: t => { frame(t); return { k: LOGO && LOGO.k, box: LOGO && [LOGO.x0, LOGO.x1], boxes: BOXES.map(b => [...b.map(Math.round), b.ink && b.ink.map(Math.round)]) }; }, coastKm: (a3, pt) => coastKm(country(a3), pt), hav,
  // v7 check of the GPU land mask against the Canvas2D one at time t: pixel difference (0..255) and the time of each path
  maskCmp(t) {
    setCam(camFinal(t));
    let t0 = performance.now(); landMaskCPU(); gl.finish(); const tc = performance.now() - t0; const a = MX.getImageData(0, 0, MK.width, MK.height).data;
    t0 = performance.now(); LMK.draw(); gl.finish(); const tg = performance.now() - t0;
    const b = new Uint8Array(LMK.mw * LMK.mh * 4); gl.bindFramebuffer(gl.FRAMEBUFFER, LMK.rfb); gl.readPixels(0, 0, LMK.mw, LMK.mh, gl.RGBA, gl.UNSIGNED_BYTE, b); gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    let s = 0, m = 0, big = 0, la = 0, lb = 0; const n = a.length / 4;
    for (let i = 0; i < a.length; i += 4) { const d = Math.abs(a[i] - b[i]); s += d; if (d > m) m = d; if (d > 128) big++; la += a[i] > 127; lb += b[i] > 127; }
    return { t, span: +C.span.toFixed(3), cpuMs: +tc.toFixed(2), gpuMs: +tg.toFixed(2), meanDiff: +(s / n).toFixed(4), maxDiff: m, over128: big, landCpu: la, landGpu: lb, samples: LMK.ns };
  } };
