# TaleSpark 2.0 · استوديو «مَدار» (MD)

استوديو فيديوهات الخرائط العمودية (1080×1920، 60 إطاراً): صور أقمار حقيقية، وكرة أرضية تغوص من الفضاء إلى المكان، ودول تُلوَّن في مكانها، ومسارات وسفن ومجسمات، وأرقام تُعدّ، وتعليق بالفصحى بتوقيت كل كلمة، وصوت مؤثرات حقيقي بجهارة -14 LUFS.

## المسار
1. **المواضيع والسكربت:** التطبيق يكتب أمراً مفصلاً ويفتح Gemini والأمر ملصوق. الرد بأربعة أقسام: `[السكربت]` و`[المصادر]` و`[ملاحظات بصرية]` و`[خطة اللقطات]`.
2. **الصوت:** يصنعه المستخدم في ElevenLabs أو AI Studio ثم يستورده في التطبيق، فيُفحص نطقه مقابل السكربت.
3. **اللوحات** (لجسم متحرك جديد فقط): أمر ChatGPT جاهز، ثم الاستيراد.
4. **الإنتاج آلياً:** التوقيت (whisper) ← المسودة ← المخرج الآلي ← بوابة المنطق ← الرسم 120 إطاراً مدموجة إلى 60 ← x264 CRF 18 slow ← الفيديو إلى الهاتف.

## أين يُرسم
| الوضع | الرسم | الإخراج |
|---|---|---|
| حاسوبي من أي مكان | الحاسوب (RTX 4060) عبر Tailscale | خطة Gemini + المخرج الآلي |
| حاسوبي + Claude | الحاسوب | Claude Code |
| سحابة GitHub | خوادم GitHub بالتوازي | الوكيل السحابي |

## على الحاسوب
```
py -3 engine/scripts/new.py projects/<name>
py -3 engine/scripts/build.py projects/<name> voice --from-downloads
py -3 engine/scripts/build.py projects/<name> draft
py -3 engine/scripts/build.py projects/<name> look
py -3 engine/scripts/build.py projects/<name> go
```
التفاصيل في `CLAUDE.md` و`PROJECT_STATE.md`، والخطة في `TALESPARK_MAPS_PLAN.md`.

## المجلدات
- `engine/engine/` المحرك (`map.js`) والخطوط.
- `engine/scripts/` أدوات البناء (الصوت، المسودة، الرسم، المزج).
- `engine/data/` الحدود والأنهار وقاعدة الأماكن `places.json`.
- `engine/assets/` الأعلام والمجسمات والصور، و`engine/sfx/` طقم الصوت (20 ملفاً).
- `projects/<name>/` مصادر كل فيلم (السكربت، الإعدادات، القصة، التوقيت).
- `android/` تطبيق TaleSpark.

## الاعتمادات
صور الأقمار: EOxCloudless 2016 من EOX IT Services GmbH (بيانات Copernicus Sentinel معدّلة 2016)، CC BY 4.0. الحدود: Natural Earth (ملكية عامة). المؤثرات: Sonniss GDC 2016 وKenney CC0. الخطوط: رخصة OFL.
