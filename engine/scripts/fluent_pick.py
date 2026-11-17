"""Fluent 3D emoji chosen by MEANING (1.1.4).

Measured at night (1.1.3): with only an alphabetical list of names the agent put a rocket on «2015» and the
same candle in several shots. Here every cached Fluent name sits in a category of meanings, with the Darija /
Arabic word stems that call it. Used by:
  - the script task (the catalogue below is printed into the prompt, grouped by meaning),
  - cloud/fix_motion.py (a new shot for a long still picks its picture from the words it covers),
  - gate 4 (a Fluent picture repeated too often is replaced by another of the same meaning).

Only names already in assets/emoji/ are used, so nothing is downloaded in the cloud."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "assets", "emoji")  # engine/assets/emoji

# category: (Fluent names, best first), (word stems in Darija / Arabic, matched inside words without tashkeel)
CATALOG = {
    "حرب ومعارك": (["Crossed swords", "Military helmet", "Shield", "Dagger", "Bomb", "Collision", "Horse"],
                   ["حرب", "معرك", "جيش", "جنود", "جندي", "عسكر", "سلاح", "قتال", "قاتل", "مقاتل", "هجوم", "هاجم", "غزو",
                    "احتل", "استعمار", "مقاوم", "ثور", "سيف", "بندق", "مدفع", "قنبل", "انفجار", "دم", "جهاد", "كتيب", "فرق"]),
    "سلام واتفاق": (["Handshake", "Dove", "Crossed flags", "Page with curl", "Fountain pen"],
                    ["سلام", "صلح", "اتفاق", "معاهد", "تفاوض", "مفاوض", "هدن", "توقيع", "وقعو", "حلف", "تحالف", "علاق"]),
    "حكم وسلطة": (["Classical building", "Castle", "Crown", "Balance scale", "Scroll", "Ballot box with ballot"],
                  ["سلطان", "ملك", "عرش", "حكم", "دول", "مخزن", "حكوم", "برلمان", "وزير", "قانون", "دستور", "قرار",
                   "ظهير", "انتخاب", "سياس", "سلط", "عاصم", "قصر", "محكم", "عدال", "قاضي"]),
    "زمن وتاريخ": (["Hourglass not done", "Spiral calendar", "Scroll", "Alarm clock", "Stopwatch", "Hourglass done", "Headstone"],
                   ["عام", "سنة", "سنين", "قرن", "تاريخ", "زمان", "وقت", "ساع", "دقيق", "نهار", "ليل", "شهر", "يوم",
                    "عصر", "مرحل", "بدا", "نهاي", "قديم", "تقليد", "ماضي", "مستقبل", "مات", "وفا", "توفى"]),
    "مال واقتصاد": (["Money bag", "Coin", "Bank", "Chart increasing", "Chart decreasing", "Money with wings", "Shopping cart", "Gem stone"],
                    ["فلوس", "مال", "درهم", "دولار", "اورو", "ريال", "ثمن", "سعر", "غلا", "رخص", "تجار", "سوق", "بنك",
                     "اقتصاد", "ضريب", "ميزاني", "مليار", "مليون", "ربح", "خسار", "دين", "قرض", "كنز", "ذهب", "فض"]),
    "ناس ومجتمع": (["Busts in silhouette", "People hugging", "Person raising hand", "Old man", "Old woman", "Man", "Woman"],
                   ["ناس", "شعب", "مغارب", "مواطن", "سكان", "عائل", "عايل", "ولاد", "بنات", "رجال", "نساء", "عيالات",
                    "جد", "جدة", "مجتمع", "قبيل", "قبائل", "جماع", "شباب", "اطفال", "دراري"]),
    "مدن وأماكن": (["Cityscape", "Houses", "Mosque", "Classical building", "Castle", "Building construction", "World map", "Globe with meridians"],
                   ["مدين", "مدن", "مراكش", "فاس", "رباط", "مكناس", "طنج", "كازا", "بيضاء", "حي", "دار", "ديور", "سور",
                    "باب", "قصب", "جامع", "مسجد", "بلاد", "بلدان", "عالم", "دولي", "خريط", "حدود", "ارض"]),
    "طبيعة": (["Mountain", "Desert", "Water wave", "Palm tree", "Sun", "Droplet", "Seedling", "National park"],
              ["جبل", "جبال", "اطلس", "صحرا", "رمل", "بحر", "محيط", "واد", "نهر", "ما", "شتا", "مطر", "شمس", "سد",
               "غاب", "شجر", "نخل", "فلاح", "زرع", "ارض", "واح", "ثلج", "برد", "سخون", "حرار"]),
    "طريق وسفر": (["Sailboat", "Ship", "Camel", "Horse", "Locomotive", "Airplane", "Compass", "Footprints", "Motorway"],
                  ["سفر", "طريق", "رحل", "قافل", "مرس", "ميناء", "سفين", "بابور", "قطار", "تران", "طيار", "مشا", "وصل",
                   "هاجر", "هجر", "حج", "بحار", "جمل", "عود", "خيل"]),
    "معرفة وأفكار": (["Light bulb", "Books", "Scroll", "Magnifying glass tilted left", "Brain", "Graduation cap", "Telescope", "Newspaper"],
                     ["فكر", "فكرة", "علم", "عرف", "كتاب", "كتب", "قرا", "مدرس", "جامع", "دراس", "بحث", "سر", "حقيق",
                      "اكتشف", "خبر", "جريد", "صحاف", "ذكي", "عقل", "مخ", "سؤال", "علاش", "كيفاش", "شنو"]),
    "مشاعر ومفاجأة": (["Face with open mouth", "Thinking face", "Face with raised eyebrow", "Red question mark", "Eyes", "Red heart", "Broken heart"],
                      ["غريب", "صدم", "عجب", "مفاج", "تخيل", "خوف", "خاف", "حب", "قلب", "حزن", "فرح", "بكا", "ضحك",
                       "ولكن", "واش", "سؤال", "غموض", "مشكل", "اشكال"]),
    "نجاح وقوة": (["Trophy", "Sports medal", "Flexed biceps", "Raised fist", "Chequered flag", "Star", "Fire", "Rocket"],
                  ["ربح", "انتصر", "نصر", "فوز", "فاز", "بطل", "بطول", "قو", "قوي", "نجح", "نجاح", "انجاز", "رقم قياسي",
                   "كاس", "ميدال", "اول", "تفوق", "صعد", "طلع", "زاد"]),
    "خطر ومنع": (["Warning", "No entry", "Prohibited", "Skull", "Chains", "Locked", "Broken chain"],
                 ["خطر", "منع", "ممنوع", "حرام", "سجن", "حبس", "قيد", "سلاسل", "عبود", "حرير", "تحرر", "استقلال",
                  "اعدام", "موت", "قتل", "مرض", "وبا", "جوع", "مجاع", "ازم"]),
    "تواصل وإعلام": (["Megaphone", "Studio microphone", "Television", "Mobile phone", "Envelope", "Speech balloon", "Satellite antenna"],
                     ["خطاب", "قال", "صرح", "اعلن", "اعلان", "اذاع", "راديو", "تلفز", "تيليفون", "هاتف", "رسال", "بريد",
                      "كلام", "هضر", "صوت", "نداء", "دعا", "بلاغ"]),
    "دين وروحانيات": (["Mosque", "Crescent moon", "Night with stars", "Candle"],
                      ["دين", "اسلام", "صلا", "جامع", "رمضان", "عيد", "هلال", "زاوي", "ولي", "صوف", "قران", "حج"]),
    "أكل وبيت": (["Teapot", "Bread", "Hot beverage", "Tomato", "Red apple", "House", "Bed"],
                 ["اتاي", "شاي", "خبز", "ماكل", "اكل", "طعام", "قهو", "دار", "بيت", "سرير", "نعس", "نوم", "صباح", "فطور"]),
    "علوم وتقنية": (["Satellite", "Rocket", "Mobile phone", "High voltage", "Hammer and wrench", "Stethoscope", "Construction"],
                    ["تكنولوج", "تقني", "صاروخ", "فضا", "قمر صناعي", "كهرب", "طاق", "مصنع", "صناع", "بنا", "مشروع",
                     "طب", "طبيب", "سبيطار", "مستشف", "دوا", "انترنت"]),
}

# a picture that says nothing wrong about any sentence: the last resort, in this order
NEUTRAL = ["Sparkles", "Light bulb", "Magnifying glass tilted left", "Compass", "Pushpin", "Bullseye", "Eyes", "Puzzle piece"]

TASHKEEL = re.compile("[ً-ْـٰ]")
_FOLD = str.maketrans("أإآٱىةؤئ", "اااايهوي")


def norm(t):
    return TASHKEEL.sub("", t or "").translate(_FOLD)


def _have(name):
    return os.path.exists(os.path.join(CACHE, re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") + ".png"))


def categories(text):
    """Categories called by the words of `text`, strongest first (number of words that call each one)."""
    words = [w for w in re.split(r"[^\w]+", norm(text)) if len(w) > 1]
    score = {}
    for cat, (_names, stems) in CATALOG.items():
        for w in words:
            for st in stems:
                s = norm(st)
                # a short stem must start the word (or follow و / ف / ب / ل / ال), a long one may sit inside it
                if (len(s) <= 3 and re.match(r"^(و|ف|ب|ل|ك)?(ال)?" + re.escape(s), w)) or (len(s) > 3 and s in w):
                    score[cat] = score.get(cat, 0) + (2 if len(s) > 3 else 1)
                    break
    return [c for c, _ in sorted(score.items(), key=lambda kv: -kv[1])]


def pick(text, avoid=(), used=None, context=""):
    """The best Fluent name for `text` (falls back to `context`, then a neutral picture). `avoid`: names that
    must not be used (the neighbouring shots); `used`: {name: times already in the video} to spread them."""
    used = used or {}
    avoid = {a.lower() for a in avoid if a}
    for src in (text, context):
        for cat in categories(src):
            names = [n for n in CATALOG[cat][0] if _have(n) and n.lower() not in avoid]
            if names:
                return min(names, key=lambda n: (used.get(n.lower(), 0), CATALOG[cat][0].index(n)))
    names = [n for n in NEUTRAL if _have(n) and n.lower() not in avoid]
    return min(names, key=lambda n: (used.get(n.lower(), 0), NEUTRAL.index(n))) if names else "Sparkles"


def catalogue_md():
    """The catalogue for the script prompt: one line per meaning."""
    lines = []
    for cat, (names, _stems) in CATALOG.items():
        have = [n for n in names if _have(n)]
        if have:
            lines.append(f"- {cat}: " + ", ".join(have))
    return "\n".join(lines)


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        print(categories(" ".join(sys.argv[1:])), "->", pick(" ".join(sys.argv[1:])))
    else:
        print(catalogue_md())
