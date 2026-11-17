# Maven Map Studio - rules for every session

**Language (ABSOLUTE user rule, 2026-11-16, re-stated 2026-11-17 three times, the last after a reply slipped into English):** every word the user sees is Modern Standard Arabic (فصحى), always and forever, whatever language the user or a tool result or a pasted text uses: replies, short answers, technical explanations, progress notes between tool calls, task-list items, tool-call descriptions, wrap_up and questions. Never English, never dialect. Before sending any reply, check it is fully in فصحى. Only code, commands, file paths and identifiers stay as they are.

**START HERE:** read `PROJECT_STATE.md` first. It holds where the work stopped, the user's confirmed decisions (Western digits, Cairo + Alexandria only, the 12 approved typography upgrades for v4), the file map and the next step. Update it at the end of every session so the next conversation can resume from it.

## Smart camera in every film (strict user rule, 2026-11-17)
The user approved the satellite + drone camera (orbit opening, hops, 3D drone over real terrain, drone move chosen by the kind of place) and made it mandatory: **every future video uses it, without exception.** Never set `"drone": false` or `"opening": false` in a project, never fall back to the flat camera, and keep the director's rules (phases 1-4 below) as the default for every new film. Improvements are welcome; removing or weakening it is not.

## Render performance (strict, user rule 2026-11-16)
Always use this PC's full power for every render. Never fall back to the slow single-page path.

Hardware: NVIDIA RTX 4060 Laptop (8 GB VRAM), Intel i7-12650H (10 cores / 16 threads), 15.7 GB RAM.

**v7 pipeline (speed plan items 1-8, done 2026-11-17). Use it for every new video:**
- `py -3 engine/scripts/build.py <p> voice` (whisper on the GPU: 17.8 s instead of 108.9 s), write the story, then
  `py -3 engine/scripts/build.py <p> go [t1,t2,...]` = globe + cams + patches (6 parallel downloads, shared cache `engine/cache/patches/`) [+ stills] + frames and x264 in ONE pass (`stream.mjs`, 3 pages, sound/mix/-14 LUFS in parallel) + mux + sheet + `timing_report.json`; frames/ deleted at the end.
- Zero loss by construction: the pages send the same 120 fps JPEGs (q .93) and ffmpeg runs the same `tmix=frames=2,fps=60,format=yuv420p` + x264 CRF 18 preset slow. Measured against the uncompressed source on 14 moments: SSIM 0.97588 (approved v6: 0.97602). The engine change itself (GPU land mask): SSIM 0.99969 / 61 dB before compression. In-page blending (`BLEND=1`) was faster (133 s) but cost ~0.001 SSIM (extra JPEG pass), so it is off.
- Measured v6 (89 s film): old path ~1410 s machine time (voice 109 + downloads 550 on a slow EOX day + frames 200 + black scan 56 + encode 442); v7 ~330 s (voice 18 + go 312 incl. 104 s of first-time downloads), ~226 s when the patches are cached.
- The floor is x264 slow itself (~43 fps alone at 1080x1920). Pages beside it (`stream_workers` in config.json, default 3): 2 = 138 s, 3 = 133 s, 4 = 212 s (starves x264). Render alone is now ~160 sub-frames/s (64 s for v6), 3x the old 53.7.
- Land mask on the GPU (stencil fan fill, 8x MSAA, `LMK` in map.js): ~0.06 ms instead of 14 ms average / 76 ms on globe shots. Patches load on demand (`PLAN`/`NEED`, LRU by last planned use): VRAM ~4-6 GB for 3 pages instead of 7.9 GB. The black-frame gate now runs in the encoder threads (same rule as blackcheck.py), so no 50 s file scan.
- Quality checks: `engine/scripts/bench/compare.py` (SSIM against another encode: two CRF 18 encodes of slightly different inputs land near 0.987 with no visible change, so judge against the source), `bench_mask.mjs` (GPU vs CPU mask), `sample.py` (CPU/RAM/GPU while a command runs).
- The old steps (`frames` + `final`) still work unchanged for old projects.

**Item 9 (2026-11-17): topic to film. Use this order for every new video:**
1. `py -3 engine/scripts/new.py projects/<name>` (config gets `"pauses": "punct"`; set `"engine": "file"`), write `script.txt` in **Modern Standard Arabic only** (strict user rule) with full punctuation (، . : ؛ ؟ ! …), facts from a source, tashkeel on any word a voice could misread (خُمْسَ not خمس = five, لارَكْ).
2. Voice (Claude in Chrome, aistudio.google.com/generate-speech, Gemini TTS): Speaker voice **Riko**, Style = custom description: "A live, engaged human documentary narrator speaking pure Modern Standard Arabic (fusha) with perfect classical pronunciation, no dialect at all. Warm, natural and expressive like a real person telling a gripping story, rich vocal modulation, never robotic or flat. Respect every punctuation mark fully: a short breath at each comma, a clear stop at each period, a suspenseful pause at each colon and ellipsis, a rising questioning tone at each question mark, and real energy and emphasis at each exclamation mark. Lean on the key numbers and names." Click inside the text box before ctrl+a (else the page is selected and the old text stays; check `document.querySelector('textarea').value`), type the script, Run, Download.
3. `build.py <p> voice --from-downloads` (moves the newest "Generated Audio*.wav" to vo/raw.wav, whisper on the GPU, pauses kept by punctuation: each pause is only capped by its mark, comma .38 s ... ellipsis .75 s, breaths without a mark at maxGap). Then check pronunciation: transcribe with large-v3 and diff against the script; a wrong word = fix its spelling/tashkeel and generate again.
4. `build.py <p> draft` (0.2 s): `web/story.draft.js` from script + timing, a complete renderable story (camera per sentence, flags, labels from places.json, numbers with units, titles, every anchor with its correct `#n`). The raw Hormuz draft passed all 8 logic checks with no edit.
5. Refine `web/story.js` with `spot('KEY')`, `lane('KEY')`, `pipeline('KEY')` from `engine/data/places.json` (never type coordinates that are already there; add new places there with a source and run `py -3 engine/scripts/places.py`). After every edit: `build.py <p> look` (moments picked from the story, 3 pages, ~8 s; patches reused from the shared cache when they cover the view) and read `check/look.jpg`. Every long camera move needs a hold key, or the shot drifts away while it is being described.
6. `build.py <p> go`.

**TaleSpark 2.0 «مَدار» (2026-11-17): the phone path. Gemini writes, the PC makes the film.**
- Prompt: `node engine/scripts/script_check.mjs prompt "<topic>" --seconds 90` (template `engine/prompts/gemini_script.txt`; the reply has 4 sections incl. `[خطة اللقطات]`, one plan line per script line).
- `build.py <p> check` (reply.txt -> script.txt, sources, notes, shotplan.json, check.json), voice as before, `build.py <p> saycheck` (whisper large-v3 vs the script), `build.py <p> director` (shotplan -> story.js + the logic gate, rejects removed automatically), `look`, `go`.
- The PC host: `py -3 host/host.py` (port 8765, token in host/pairing.json, `--pair` prints the phone link); it runs check -> voice -> saycheck -> director -> look [-> wait for approval] -> go for each job from the app.
- The app: `android/` (TaleSpark 2.0.0, same package + key as 1.3.8). After changing prompts, rules, places or sprites run `node engine/scripts/app_kit.mjs`, then `cd android && ./gradlew assembleRelease`.
- The channel logo «مَدار / MD» is drawn by map.js on every film (`"logo": false` hides it).

**Smart camera «satellite + drone» (phases 1-4, 2026-11-17):**
- Phase 1, the orbit opening: every director film starts on the whole Earth and dives onto its first place by 2.2 s (`"opening": false` turns it off).
- Phase 2, hops: `director.mjs` reaches a place farther than 3 frame widths (and outside a frame that holds both) by a hop: a satellite key at the midpoint at continent height, then a dive (the `dive` sound comes from sound.py). The loop back to the opening frame hops too when it is far.
- Phase 3, the drone (`map.js`): `cam(t, {..., drone: true, heading: deg, pitch: deg})`. One camera family: rays leave the image plane through the target (`setCam`, `projRel`, `unproject`); pitch 0 + no drone is the old orthographic globe, measured pixel-identical (difference 0 on hormuz-auto), so the satellite never cuts to the drone. `drone:true` tilts with the height (`autoPitch`: 0 above 7 deg of frame, 34 at 2, 49 at .6, 60 at .2) and moves the eye to a 64 deg lens (`"droneFov"`). Terrain: AWS Terrain Tiles (terrarium, `engine/cache/dem/`, credit printed by prep.py) resampled to plate carree by `prep.py` per drone shot (`web/img/dems.json`, zoom from the closest frame of the shot), raymarched in the shader with exaggeration 1.5 (`"exag"`), hillshade lit by the same sun as the sprite shadows, haze by distance, sky at the horizon. `P()` puts labels, routes and pins on the terrain and returns a 4th value (depth scale, used for sprite sizes). prep.py downloads near ground sharp and far ground coarse (it melts into the haze). Honest limits: EOX is ~10 m a pixel, so frames below ~0.2 deg (~20 km) get soft; no buildings.
- Phase 4, the director picks the drone move from the place kind (places.json `kind`): strait / canal = fly along its lane, port / base / city / island / capital / point = a slow orbit (32 deg a line, the next place turns the other way), mountain / peak / range = rise over the crest (pitch 22 -> 56), two neighbouring countries and no place = fly along the border; a list of countries, a whole route (تتبع), a comparison (مقارنة), a pull-out (ابتعاد) or any frame wider than 6.5 deg stays the satellite. `"drone": false` in config.json turns the drone off.
- On SwiftShader (no GPU) a drone frame costs ~3-4 s, the satellite ~0.7 s; not yet measured on the RTX 4060: time the first drone `go` there and write the number here.

**Old path (v6 and before), kept for reference:**

- Frames: `py -3 engine/scripts/build.py <project> frames 120`. It runs **4 parallel headless Chromium workers** by default (`workers` in config.json, or env `WORKERS`), each with **3 JPEG encoder Web Workers** (env `ENCODERS`). The page draws a frame (~16 ms), hands an ImageBitmap to an encoder thread and draws the next one at once; encoders POST raw JPEG bytes to render.mjs's server (no toDataURL, no base64). Output is pixel-identical to the old path (`SERIAL=1` still runs it).
- Measured 2026-11-16 (30 s / 3600 frames, YouTube open in a browser): 4 workers ~82-104 fps, 3 workers ~94, 6 workers ~74 (free RAM 0.4 GB), 8 workers ~25 (swapping). The ceiling is now system RAM, not the GPU. Before the pipeline it was ~45 fps with 6 workers. Close heavy apps (browser video) before a final render for the most speed.
- Quality gate after frames: the black-frame scan runs on every CPU core (`engine/scripts/blackcheck.py`, JPEG decoded at 1/8): ~6 s for 10,700 frames (was ~136 s on one core).
- Quality: always render at **120 fps**, then blend 2 frames to 60 fps for natural motion blur. Never render final videos at 60 fps directly.
- Encode: libx264 CRF 18 preset slow (CPU), the best quality per size. Keep it. NVENC only for quick previews.
- Audio: master at -14 LUFS, true peak -1.5 dBTP.
- Before declaring a video done, check frames at key moments for overlapping labels/text.
- If hardware changes, re-measure: raise `workers` until fps stops rising or free RAM < 1 GB, then keep the best value here.

- **Measured 2026-11-17 (v6, 21 patches), the baseline for the approved speed plan in PROJECT_STATE.md:** 4 workers fell to ~44 fps (RAM free 0.2 GB, VRAM 7.9/8 GB); 1 worker 36, 2 workers 42.5, 3 workers 44.4. The page's `landMask()` on the CPU costs 73 ms/frame on globe shots (0.2 ms without it). Final encode: JPEG decode + tmix is 70% of it (single-threaded). faster-whisper runs on the GPU (5.7 s instead of 128.6 s) once `torch/lib` is on the DLL path. Split x264 encodes were slower; NVENC final is rejected (lower SSIM, +30% size).
- VRAM: each worker loads every imagery patch. With ~17 patches 6 workers can overflow 8 GB and a worker silently renders the map black. build.py now scans every frame after a parallel render and re-renders black ranges with fewer workers. Never skip that gate.

## Sound (automatic)
- **No background music (user decision 2026-11-17).** Keep the sound exactly as in v6: the house kit + the low drone bed from `sound.py`. Never propose music again.
- `build.py final` runs `sound.py` (auto cue sheet from the story's exported events + a drone bed) unless cues.json has `"manual": true`, then `mix.py`.
- The library is `engine/sfx/` (606 sounds, index.json; Sonniss GDC 2016 + Kenney CC0, commercial use OK, no attribution). mix.py stops the build if any cue file is missing.
- Targets: voice vs effects median >= 18 dB, worst >= 8 dB; -14 LUFS.

## Engine
- The canonical engine is `engine/engine/` (map.js, index.html, fonts). new.py copies it. After changing it, copy map.js into the project being rendered.
- Fonts: Cairo = titles, numbers, captions. Alexandria = geographic labels, pills, rulers, sub-lines.
- Land palette: gold / danger / ally / soft region styles. Blue is only for water and routes.
- Layout: labels and pills claim boxes per frame and avoid titles, numbers, captions and the TikTok/Shorts safe area.
- Camera: `punch(t)` and `shake(t)` for impact moments. `cam(t, ...)` means the camera ARRIVES at t, so add a hold key to keep a shot.
- Word anchors: a repeated word needs `#n` (e.g. T('إفريقيا#2')). Check every anchor against timing.json.

## Output
- A new render gets a new name (e.g. `<project>-v2.mp4`), never overwrites the previous video.
- After the encode, delete `<project>/frames/` (heavy intermediate).

## Flags and places (strict user rule, 2026-11-16)
Never a wrong flag or a wrong place: viewers notice everything. A flag is only drawn through `region(code, {flag:true})` (or `flagPin(code)`), which takes the flag from the country's own ISO code; the engine throws if a flag code does not match its country. Before any render, check every flag file by eye (`engine/assets/flags/_check.png` from `engine/scripts/flag_check.py`) and every coordinate of a base, island, city or label against a real map.

## Logic in everything (strict user rule, 2026-11-17)
The user's words: logic in everything, in every video (a ship must sail bow first, never sideways). `map.js` runs `logic()` in `start()` and stops the render on any violation; render.mjs prints the list. Never bypass it; fix the story.
1. Direction: anything that moves is drawn from above (`engine/assets/sprites/top/`, nose up) and turned to its heading. A new moving object needs a top view first.
2. Medium: ships on water only (routes are tested against the coastlines every ~4 km; `path(..., {sea:true})` for sea lanes), land vehicles on land.
3. Speeds: missile > drone > ship on screen while they move together. 4. Sizes: real size order; a ship is never wider than ~45 % of the narrowest water on its route (zoom the camera in instead of enlarging it).
5. Flags and places: `flagPin(code, pt, {in:'DJI'})` asserts where the spot is; a `measure` of N km must span about N km; a flight can assert `from:'YEM'`.
6. Cause and effect: `sprite('missile', from, {to: targetSprite, arrive: T('word'), travel, hit:{...}})` makes the explosion, fire and jolt at the arrival; a fire needs `cause`.
7. Picture = speech: every number and named/flagged country is spoken near it (`say:'word'` ties it to its word). 8. Physics: one wind (`wind()`), one sun for all shadows.
