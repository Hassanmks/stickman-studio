# Speed benchmarks (2026-11-17 baseline in PROJECT_STATE.md)
- `py -3 -I bench_whisper.py <wav> torchdll|none cuda|cpu large-v3-turbo` : transcription time, GPU vs CPU
- `py -3 bench_render.py <project> <workers> <f0> <f1> [encoders]` : renders frames f0..f1 into <project>/frames while sampling CPU, RAM, GPU, VRAM (needs psutil). Delete <project>/frames afterwards.
- `py -3 bench_enc.py [<frames pattern>]` : JPEG decode vs x264 slow vs split encodes vs NVENC, plus SSIM (needs frames 6000..8399 from bench_render). Outputs go to a temp folder.
