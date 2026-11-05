#!/usr/bin/env python3
"""Four plain faces with the basics (zaher, 2026-11-05), from the proposal page:

  watchface-c-big     «الساعة الكبيرة»   digital, a large HH:MM
  watchface-c-stack   «السطران»          digital, hours over minutes
  watchface-c-four    «الأرقام الأربعة»  hands, large 12 / 3 / 6 / 9
  watchface-c-twelve  «العدّادان»        hands, twelve numerals, battery and date sub-dials

Each shows the prayer row (passed | next prayer | left), where reading stopped, the
battery, today's date, and taps to the bookmarks and the surah index. Every data slot
can be changed from the watch's editor, and the accent colour is a ColorConfiguration
(«اللون»). The always-on screen keeps the date, the time and the prayer row.

Format rules learned on the watch (SM-L500): at most 8 slots, only at the top of the
Scene, never inside a ListOption; arcs on the rim take a BoundingArc.

  python3 scripts/wff/make_basic_faces.py
"""
import math
import os
import shutil

import kit
import make_custom_face as mcf
from make_row_face import BOLD, SEMI, slot, text

A = "[CONFIGURATION.accent.0]"          # the chosen accent colour
ACCENTS = [("green", "أخضر", "#FF2FBF7F"), ("gold", "ذهبي", "#FFE0B252"), ("blue", "أزرق", "#FF5AA9E6"),
           ("coral", "مرجاني", "#FFEF7A6B"), ("white", "أبيض", "#FFF2F2F2")]
WHITE, SOFT, DIM = "#FFFFFFFF", "#FFAAB3AE", "#FF2A312D"
KUFI = "reem_kufi_bold"
HIDE = kit.HIDE_IN_AMBIENT
FACES = {
    "big": ("الساعة الكبيرة", "DIGITAL", "#FF0B0F0D", "green"),
    "stack": ("السطران", "DIGITAL", "#FF101418", "gold"),
    "four": ("الأرقام الأربعة", "ANALOG", "#FF0D0F12", "gold"),
    "twelve": ("العدّادان", "ANALOG", "#FF0F1210", "green"),
}


def pol(r, deg):
    a = math.radians(deg - 90)
    return 225 + r * math.cos(a), 225 + r * math.sin(a)


def active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450">{HIDE}{inner}</Group>'


def draw(inner):
    return f'<PartDraw x="0" y="0" width="450" height="450">{inner}</PartDraw>'


def background(color):
    return active("bg", draw(f'<Rectangle x="0" y="0" width="450" height="450"><Fill color="{color}"/></Rectangle>'))


def label(x, y, w, h, body, size, color=WHITE, family=BOLD, angle=None):
    t = text(x, y, w, h, body, size, color, family)
    if angle is not None:
        t = t.replace("<PartText ", f'<PartText pivotX="0.5" pivotY="0.5" angle="{angle}" ', 1)
    return t


# ── slots ────────────────────────────────────────────────────────────────────
def short_slot(sid, box, provider, size, color=WHITE, family=SEMI, system="EMPTY", types="SHORT_TEXT"):
    """A small text slot (date, Hijri date...): any short text the wearer picks."""
    x, y, w, h = box
    body = f'<Complication type="SHORT_TEXT">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), size, color, family)}</Complication>'
    return slot(sid, x, y, w, h, types, provider, "SHORT_TEXT", f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body,
                system=system, ambient=False)


def reading_pill(sid, box, size=16, pill=True, ambient=True):
    """Where reading stopped («السورة · الآية»), on a pill of the accent colour."""
    x, y, w, h = box
    back = (f'<PartDraw x="0" y="0" width="{w}" height="{h}"><RoundRectangle x="0" y="0" width="{w}" height="{h}" '
            f'cornerRadiusX="{h / 2:.0f}" cornerRadiusY="{h / 2:.0f}"><Fill color="{A}"/></RoundRectangle></PartDraw>') if pill else ""
    back = back.replace("<PartDraw ", '<PartDraw alpha="41" ', 1)
    long_ = f'<Complication type="LONG_TEXT">{back}{text(0, 0, w, h, ("%s", ["[COMPLICATION.TITLE]"]), size, WHITE, SEMI)}</Complication>'
    short = f'<Complication type="SHORT_TEXT">{back}{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), size, WHITE, SEMI)}</Complication>'
    return slot(sid, x, y, w, h, "LONG_TEXT SHORT_TEXT", "ContinueReadingComplicationService", "LONG_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', long_ + short, ambient=ambient)


def tap_label(sid, box, provider, size=15):
    """A tap written as its own name («الإشارات», «السور»), in the accent colour."""
    x, y, w, h = box
    body = (f'<Complication type="SHORT_TEXT">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), size, A, SEMI)}</Complication>'
            f'<Complication type="SMALL_IMAGE"><PartImage x="{(w - h) // 2}" y="0" width="{h}" height="{h}">'
            '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
    return slot(sid, x, y, w, h, "SHORT_TEXT SMALL_IMAGE", provider, "SHORT_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body)


def chord(sid, right, provider):
    """A band on the side of the rim with its name along it: surahs right, bookmarks left."""
    a0, a1 = (68, 112) if right else (248, 292)
    mid = (a0 + a1) / 2
    band = (f'<PartDraw x="0" y="0" width="450" height="450" alpha="46"><Arc centerX="225" centerY="225" width="420" height="420" '
            f'startAngle="{a0}" endAngle="{a1}"><Stroke color="{A}" thickness="30" cap="BUTT"/></Arc></PartDraw>')
    cx, cy = pol(210, mid)
    name = label(round(cx - 50), round(cy - 12), 100, 24, ("%s", ["[COMPLICATION.TEXT]"]), 15, A, SEMI, angle=90 if right else -90)
    body = f'<Complication type="SHORT_TEXT">{band}{name}</Complication>'
    body += f'<Complication type="SMALL_IMAGE">{band}<PartImage x="{round(cx - 13)}" y="{round(cy - 13)}" width="26" height="26"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>'
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{a0}" endAngle="{a1}" thickness="40"/>'
    return slot(sid, 0, 0, 450, 450, "SHORT_TEXT SMALL_IMAGE", provider, "SHORT_TEXT", bound, body)


def battery_rim(sid, a0=-60, a1=60, r=208):
    """The battery as an arc on the rim at the top, its level in the accent colour."""
    frac = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
            "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")
    d = 2 * r
    track = f'<Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="{a0}" endAngle="{a1}"><Stroke color="{DIM}" thickness="8" cap="ROUND"/></Arc>'
    level = (f'<Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="{a0}" endAngle="{a0}">'
             f'<Transform target="endAngle" value="({a0} + ({a1} - {a0}) * {frac})"/><Stroke color="{A}" thickness="8" cap="ROUND"/></Arc>')
    txt = text(150, 50, 150, 24, ("%s", ["[COMPLICATION.TEXT]"]), 16, "#FFCCDDD5", SEMI)
    body = (f'<Complication type="RANGED_VALUE">{draw(track + level)}{txt}</Complication>'
            f'<Complication type="SHORT_TEXT">{draw(track)}{txt}</Complication>')
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{a0}" endAngle="{a1}" thickness="40"/>'
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE", bound, body, system="WATCH_BATTERY", ambient=True)


def battery_pill(sid, box):
    """A battery drawn as a pill that fills, the percentage under it."""
    x, y, w, h = box
    frac = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
            "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")
    pw, ph = w - 16, 30
    shell = (f'<PartDraw x="8" y="4" width="{pw}" height="{ph}"><RoundRectangle x="1" y="1" width="{pw - 2}" height="{ph - 2}" cornerRadiusX="14" cornerRadiusY="14">'
             f'<Stroke color="{A}" thickness="2"/></RoundRectangle></PartDraw>')
    fill = (f'<PartDraw x="12" y="8" width="{pw - 8}" height="{ph - 8}"><RoundRectangle x="0" y="0" width="{pw - 8}" height="{ph - 8}" cornerRadiusX="11" cornerRadiusY="11">'
            f'<Transform target="width" value="(max(4, {pw - 8} * {frac}))"/><Fill color="{A}"/></RoundRectangle></PartDraw>')
    pct = text(0, ph + 10, w, h - ph - 10, ("%s", ["[COMPLICATION.TEXT]"]), 16, "#FFCCDDD5", SEMI)
    body = (f'<Complication type="RANGED_VALUE">{shell}{fill}{pct}</Complication>'
            f'<Complication type="SHORT_TEXT">{shell}{pct}</Complication>')
    return slot(sid, x, y, w, h, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE", f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body,
                system="WATCH_BATTERY")


def battery_dial(sid, cx, cy, r=46):
    """A sub-dial: the battery as a ring that fills, the percentage in its middle."""
    frac = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
            "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")
    s = 2 * r + 8
    ring = (f'<PartDraw x="0" y="0" width="{s}" height="{s}"><Arc centerX="{s / 2}" centerY="{s / 2}" width="{2 * r}" height="{2 * r}" startAngle="0" endAngle="360">'
            f'<Stroke color="{DIM}" thickness="6"/></Arc></PartDraw>')
    level = (f'<PartDraw x="0" y="0" width="{s}" height="{s}"><Arc centerX="{s / 2}" centerY="{s / 2}" width="{2 * r}" height="{2 * r}" startAngle="0" endAngle="0">'
             f'<Transform target="endAngle" value="(360 * {frac})"/><Stroke color="{A}" thickness="6" cap="ROUND"/></Arc></PartDraw>')
    pct = text(0, 0, s, s, ("%s", ["[COMPLICATION.TEXT]"]), 21, WHITE, SEMI)
    body = (f'<Complication type="RANGED_VALUE">{ring}{level}{pct}</Complication>'
            f'<Complication type="SHORT_TEXT">{ring}{pct}</Complication>')
    return slot(sid, round(cx - s / 2), round(cy - s / 2), s, s, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE",
                f'<BoundingOval x="0" y="0" width="{s}" height="{s}"/>', body, system="WATCH_BATTERY")


def date_dial(sid, cx, cy, r=46):
    s = 2 * r + 8
    ring = (f'<PartDraw x="0" y="0" width="{s}" height="{s}"><Arc centerX="{s / 2}" centerY="{s / 2}" width="{2 * r}" height="{2 * r}" startAngle="0" endAngle="360">'
            f'<Stroke color="{DIM}" thickness="2"/></Arc></PartDraw>')
    body = f'<Complication type="SHORT_TEXT">{ring}{text(0, 0, s, s, ("%s", ["[COMPLICATION.TEXT]"]), 24, WHITE, SEMI)}</Complication>'
    return slot(sid, round(cx - s / 2), round(cy - s / 2), s, s, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                f'<BoundingOval x="0" y="0" width="{s}" height="{s}"/>', body, ambient=False)


# ── clock pieces ─────────────────────────────────────────────────────────────
def digital(x, y, w, h, fmt, size, color, when="active", name="t"):
    body = (f'<DigitalClock x="{x}" y="{y}" width="{w}" height="{h}"><TimeText format="{fmt}" hourFormat="24" align="CENTER" '
            f'x="0" y="0" width="{w}" height="{h}"><Font family="{KUFI}" size="{size}" color="{color}"/></TimeText></DigitalClock>')
    alpha, amb = ("255", "0") if when == "active" else ("0", "255")
    return (f'<Group name="{name}_{when}" x="0" y="0" width="450" height="450" alpha="{alpha}">'
            f'<Variant mode="AMBIENT" target="alpha" value="{amb}"/>{body}</Group>')


def ticks(r_out=218):
    out = []
    for i in range(60):
        big = i % 5 == 0
        (x0, y0), (x1, y1) = pol(r_out, i * 6), pol(r_out - (16 if big else 8), i * 6)
        out.append(f'<Line startX="{x0:.1f}" startY="{y0:.1f}" endX="{x1:.1f}" endY="{y1:.1f}"><Stroke color="{"#FFCCDDDD" if big else "#FF444455"}" thickness="{3 if big else 1.5}"/></Line>')
    return active("ticks", draw("".join(out)))


def hands():
    def hand(name, length, width, color, angle, tail=0, hide=False):
        return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">{HIDE if hide else ""}'
                f'<Transform target="angle" value="{angle}"/><PartDraw x="0" y="0" width="450" height="450">'
                f'<Line startX="225" startY="{225 + tail}" endX="225" endY="{225 - length}"><Stroke color="{color}" thickness="{width}" cap="ROUND"/></Line></PartDraw></Group>')
    return (hand("hour", 108, 12, WHITE, "([HOUR_0_23] % 12) * 30 + [MINUTE] * 0.5")
            + hand("minute", 170, 7, WHITE, "[MINUTE] * 6 + [SECOND] * 0.1")
            + hand("second", 185, 2.5, A, "[SECOND] * 6", tail=24, hide=True)
            + '<Group name="hub" x="0" y="0" width="450" height="450">'
            + draw(f'<Ellipse x="216" y="216" width="18" height="18"><Fill color="{A}"/></Ellipse>') + '</Group>')


def numeral(x, y, value, size, color=WHITE):
    w, h = round(size * 1.8), round(size * 1.3)
    return text(round(x - w / 2), round(y - h / 2), w, h, value, size, color, KUFI)


def prayer(sid, box, tint=False):
    x, y, w, h = box
    back = active("prayerBox", draw(f'<RoundRectangle x="{x}" y="{y}" width="{w}" height="{h}" cornerRadiusX="16" cornerRadiusY="16"><Fill color="{A}"/></RoundRectangle>')
                  ).replace("<PartDraw ", '<PartDraw alpha="26" ', 1) if tint else ""
    return back + kit.prayer_row(sid, box=box, when="always")


# ── the four faces ───────────────────────────────────────────────────────────
def face_big():
    p = [background(FACES["big"][2]), battery_rim(1),
         short_slot(2, (92, 92, 130, 30), "GregorianDateComplicationService", 19, "#FFAABBC0"),
         short_slot(3, (228, 92, 130, 30), "HijriDateComplicationService", 19, "#FFAABBC0"),
         digital(0, 128, 450, 126, "hh:mm", 121, WHITE, "active", "time"),
         digital(0, 128, 450, 126, "hh:mm", 112, "#FFCCCCCC", "ambient", "time"),
         prayer(4, (66, 258, 318, 84)),
         reading_pill(5, (122, 350, 206, 41)),
         chord(6, True, "SurahIndexShortcutComplicationService"),
         chord(7, False, "BookmarksShortcutComplicationService"),
         kit.time_tap(8, 70, 132, 310, 118)]
    return p


def face_stack():
    p = [background(FACES["stack"][2]),
         reading_pill(1, (128, 30, 194, 30), 15, pill=False),
         digital(117, 50, 280, 150, "hh", 154, WHITE, "active", "hours"),
         digital(117, 176, 280, 150, "mm", 154, A, "active", "minutes"),
         digital(0, 150, 450, 120, "hh:mm", 108, "#FFCCCCCC", "ambient", "time"),
         short_slot(2, (60, 128, 92, 70), "GregorianDateComplicationService", 28, WHITE),
         battery_pill(3, (64, 222, 88, 70)),
         prayer(4, (82, 318, 286, 72)),
         tap_label(5, (124, 398, 84, 28), "BookmarksShortcutComplicationService"),
         tap_label(6, (242, 398, 84, 28), "SurahIndexShortcutComplicationService"),
         kit.time_tap(7, 170, 70, 230, 240)]
    return p


def face_four():
    nums = active("numerals", numeral(225, 74, "12", 66) + numeral(382, 230, "3", 66) + numeral(68, 230, "9", 66) + numeral(225, 392, "6", 41))
    p = [background(FACES["four"][2]), ticks(), nums,
         short_slot(1, (128, 126, 100, 26), "GregorianDateComplicationService", 17, "#FFAABBC0"),
         short_slot(2, (232, 126, 90, 26), None, 17, "#FFAABBC0", system="WATCH_BATTERY", types="SHORT_TEXT RANGED_VALUE"),
         reading_pill(3, (138, 158, 174, 41)),
         prayer(4, (82, 276, 286, 78), tint=True),
         chord(5, True, "SurahIndexShortcutComplicationService"),
         chord(6, False, "BookmarksShortcutComplicationService"),
         kit.time_tap(7, 160, 200, 130, 60),
         hands()]
    return p


def face_twelve():
    nums = "".join(numeral(*pol(186, h * 30), str(h), 41) for h in range(1, 13))
    p = [background(FACES["twelve"][2]), active("numerals", nums),
         battery_dial(1, 134, 225), date_dial(2, 316, 225),
         reading_pill(3, (160, 104, 130, 28), 14, pill=False),
         prayer(4, (100, 278, 250, 66)),
         tap_label(5, (122, 362, 84, 28), "BookmarksShortcutComplicationService", 14),
         tap_label(6, (244, 362, 84, 28), "SurahIndexShortcutComplicationService", 14),
         kit.time_tap(7, 180, 150, 90, 50),
         hands()]
    return p


BUILD = {"big": face_big, "stack": face_stack, "four": face_four, "twelve": face_twelve}


def config(default):
    opts = "".join(f'<ColorOption id="{i}" displayName="accent_{i}" colors="{c}"/>' for i, _, c in ACCENTS)
    return f'<UserConfigurations><ColorConfiguration id="accent" displayName="accent_label" defaultValue="{default}">{opts}</ColorConfiguration></UserConfigurations>'


def write(fid):
    name, kind, _, default = FACES[fid]
    module = f"watchface-c-{fid}"
    mcf.ensure_module(module, name)
    root = os.path.join(kit.ROOT, module)
    shutil.copy(os.path.join(mcf.FONT_DIR, KUFI + ".ttf"), os.path.join(root, "src/main/res/font", KUFI + ".ttf"))
    strings = [f'    <string name="face_name">القرآن الكريم · {name}</string>', '    <string name="accent_label">اللون</string>']
    strings += [f'    <string name="accent_{i}">{n}</string>' for i, n, _ in ACCENTS]
    open(os.path.join(root, "src/main/res/values/strings.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(strings) + "\n</resources>\n")
    parts = BUILD[fid]()
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f'<!-- «{name}». Generated by scripts/wff/make_basic_faces.py: edit the script, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           f'<Metadata key="CLOCK_TYPE" value="{kind}"/>\n<Metadata key="PREVIEW_TIME" value="10:08:36"/>\n'
           + config(default) + '\n<Scene backgroundColor="#FF000000">\n' + "\n".join(parts) + "\n</Scene>\n</WatchFace>\n")
    out = os.path.join(root, "src/main/res/raw/watchface.xml")
    open(out, "w", encoding="utf-8").write(xml)
    n = xml.count("<ComplicationSlot ")
    ids = [s.split('"')[0] for s in xml.split('slotId="')[1:]]
    assert n <= 8 and len(ids) == len(set(ids)), (fid, n, ids)
    print(f"wrote {out} ({n} slots)")


if __name__ == "__main__":
    for fid in FACES:
        write(fid)
