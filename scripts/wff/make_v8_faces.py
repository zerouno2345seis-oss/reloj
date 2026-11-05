#!/usr/bin/env python3
"""Eight faces chosen by zaher on 2026-11-05 from the proposal page
(https://claude.ai/artifact/F1uos3v28X7UExHfwFq8LU), drawn here for the watch:

  orbit (المدار)       digital, a rim ring filling toward the next prayer
  crescent (الهلال)    hands, twelve numerals
  waves (الموجتان)     digital, two rings at the top that can show anything with a level
  dials (الثلاثة عدادات) hands, the three prayer squares as sub-dials
  horizon (الأفق)      hands, the day's arc from sunrise to sunset
  calm (البسيطة)       digital, six sections on the rim
  sundial (المزولة)    hands, the six times on a ring inside the numerals
  squares (المربعات الثلاثة) digital, large prayer squares

On every face, top to bottom: battery and temperature, the date (5/11), the time, the
prayer row (passed on the right, the next prayer and its time in the middle, left on
the left; each square's frame fills with the time), and where reading stopped; under
it taps to the surah index and the mushaf. The accent colour is a setting; the hands'
colour too, with a seconds hand and glowing marks on the rim at the hour, minute and
second. Some faces take a picture behind them, with three strengths of fading. The
always-on screen keeps the date, the time and the prayer row only.

Format rules from the watch (SM-L500): at most 8 slots, only at the top of the Scene,
never inside a ListOption (a face built that way sent the watch into a reboot loop);
a slot draws only inside its bounding shape.

  python3 scripts/wff/make_v8_faces.py
"""
import math
import os
import shutil

import kit
import make_custom_face as mcf
from make_row_face import SEMI, slot, text

K = 450 / 438
C = 225
HIDE = kit.HIDE_IN_AMBIENT
NUM = "changa_bold"                  # Latin digits, a little condensed
A = "[CONFIGURATION.accent.0]"
H = "[CONFIGURATION.hands.0]"
ACCENTS = [("green", "أخضر", "#FF2FBF7F"), ("gold", "ذهبي", "#FFE0B252"), ("blue", "أزرق", "#FF5AA9E6"),
           ("coral", "مرجاني", "#FFEF7A6B"), ("violet", "بنفسجي", "#FFB59CFF"), ("white", "أبيض", "#FFF2F2F2")]
HANDS = [("white", "أبيض", "#FFFFFFFF"), ("gold", "ذهبي", "#FFE0B252"), ("silver", "فضي", "#FFB8C2BD"),
         ("green", "أخضر", "#FF7DFFB0"), ("blue", "أزرق", "#FF8CC8FF")]
BACKS = [("none", "بلا صورة", None), ("mosques", "المسجد", "bg_mosques"), ("ba", "مسجد بوينس آيرس", "bg_user"),
         ("pattern", "الزخرفة", "bg_pattern"), ("haram", "المسجد الحرام", "bg_haram"),
         ("nabawi", "المسجد النبوي", "bg_nabawi"), ("kaaba", "الكعبة", "bg_kaaba")]
FADES = [("f15", "خفيفة", 38), ("f25", "متوسطة", 64), ("f35", "أوضح", 90)]
SOFT, DIM, TRACK = "#FF9AA5A0", "#FF2A332E", "#FF222A26"

V = "[COMPLICATION.RANGED_VALUE_VALUE]"
NOW = "([HOUR_0_23] * 60 + [MINUTE])"
PASSED = f"((({NOW}) - floor({V} / 1440) + 1440) % 1440)"
LEFT = f"((({V} % 1440) - ({NOW}) + 1440) % 1440)"
FRAC = f"({PASSED} / max(1, {PASSED} + {LEFT}))"


def k(v):
    return v * K


def active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450">{HIDE}{inner}</Group>'


def draw(inner, w=450, h=450, x=0, y=0):
    return f'<PartDraw x="{x}" y="{y}" width="{w}" height="{h}">{inner}</PartDraw>'


def pol(r, deg, cx=C, cy=C):
    a = math.radians(deg - 90)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def arc(r, a0, a1, color, width, cap="ROUND", cx=C, cy=C):
    return (f'<Arc centerX="{cx:.1f}" centerY="{cy:.1f}" width="{2 * r:.1f}" height="{2 * r:.1f}" startAngle="{a0:.1f}" endAngle="{a1:.1f}">'
            f'<Stroke color="{color}" thickness="{width:.1f}" cap="{cap}"/></Arc>')


def hm(d):
    """A duration in minutes as h:mm."""
    return ("%s:%s%s", [f"floor({d} / 60)", f"floor(({d} % 60) / 10)", f"({d} % 10)"])


def clock_text(m):
    return ("%s%s:%s%s", [f"floor({m} / 600)", f"(floor({m} / 60) % 10)", f"floor(({m} % 60) / 10)", f"({m} % 10)"])


# ── the prayer squares ───────────────────────────────────────────────────────
def rounded_progress(name, x0, y0, w, h, r, frac, color, width):
    """A rounded square's frame drawn from the top middle, clockwise, as far as `frac`
    (an expression 0..1): four sides and four corners, each grown by a Transform."""
    segs = []   # (kind, data, length)
    L1, L2, Lc = w / 2 - r, h - 2 * r, math.pi * r / 2
    segs.append(("line", (x0 + w / 2, y0, x0 + w - r, y0), L1))
    segs.append(("arc", (x0 + w - r, y0 + r, 0), Lc))
    segs.append(("line", (x0 + w, y0 + r, x0 + w, y0 + h - r), L2))
    segs.append(("arc", (x0 + w - r, y0 + h - r, 90), Lc))
    segs.append(("line", (x0 + w - r, y0 + h, x0 + r, y0 + h), w - 2 * r))
    segs.append(("arc", (x0 + r, y0 + h - r, 180), Lc))
    segs.append(("line", (x0, y0 + h - r, x0, y0 + r), L2))
    segs.append(("arc", (x0 + r, y0 + r, 270), Lc))
    segs.append(("line", (x0 + r, y0, x0 + w / 2, y0), L1))
    total = sum(s[2] for s in segs)
    out, start = "", 0.0
    for i, (kind, d, length) in enumerate(segs):
        v = f"clamp({frac} * {total:.1f} - {start:.1f}, 0, {length:.1f})"
        show = f"(255 * clamp({frac} * {total:.1f} - {start:.1f}, 0, 1))"
        if kind == "line":
            xa, ya, xb, yb = d
            ex = f"({xa:.1f} + {(xb - xa) / length:.4f} * {v})"
            ey = f"({ya:.1f} + {(yb - ya) / length:.4f} * {v})"
            shape = (f'<Line startX="{xa:.1f}" startY="{ya:.1f}" endX="{xa:.1f}" endY="{ya:.1f}">'
                     f'<Transform target="endX" value="{ex}"/><Transform target="endY" value="{ey}"/>'
                     f'<Stroke color="{color}" thickness="{width:.1f}" cap="ROUND"/></Line>')
        else:
            cx, cy, a0 = d
            shape = (f'<Arc centerX="{cx:.1f}" centerY="{cy:.1f}" width="{2 * r:.1f}" height="{2 * r:.1f}" startAngle="{a0}" endAngle="{a0}">'
                     f'<Transform target="endAngle" value="({a0} + 90 * {v} / {length:.2f})"/>'
                     f'<Stroke color="{color}" thickness="{width:.1f}" cap="ROUND"/></Arc>')
        out += (f'<Group name="{name}{i}" x="0" y="0" width="450" height="450"><Transform target="alpha" value="{show}"/>'
                + draw(shape) + '</Group>')
        start += length
    return out


def prayer_squares(sid, centres, y_by, size, sc):
    """The prayer row as three squares: passed (frame filling with the time gone),
    the next prayer with its time (full white frame), left (frame emptying).
    centres: x of passed, next, left; y_by: their tops. One slot spans them."""
    w, h, r, sw = k(86 * sc * size[0]), k(76 * sc * size[1]), k(20 * sc), k(6 * sc)
    xs = [c - w / 2 for c in centres]
    ys = list(y_by)
    bx0, by0 = min(xs) - sw, min(ys) - sw
    bx1, by1 = max(xs) + w + sw, max(ys) + h + sw
    bw, bh = round(bx1 - bx0), round(by1 - by0)
    ox, oy = bx0, by0          # the slot's own corner: everything inside is drawn relative to it
    body = ""
    fracs = [FRAC, "1", f"(1 - {FRAC})"]
    for i in range(3):
        x, y = xs[i] - ox, ys[i] - oy
        body += draw(f'<RoundRectangle x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" cornerRadiusX="{r:.1f}" cornerRadiusY="{r:.1f}">'
                     f'<Fill color="#08FFFFFF"/><Stroke color="{DIM}" thickness="{sw:.1f}"/></RoundRectangle>', bw, bh)
        if i == 1:
            body += draw(f'<RoundRectangle x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" cornerRadiusX="{r:.1f}" cornerRadiusY="{r:.1f}">'
                         f'<Stroke color="#FFFFFFFF" thickness="{sw:.1f}"/></RoundRectangle>', bw, bh)
        else:
            body += rounded_progress(f"sq{i}_", x, y, w, h, r, fracs[i], A, sw).replace('width="450" height="450"', f'width="{bw}" height="{bh}"')
        lab_size, val_size = round(k(13 * sc)), round(k(21 * sc))
        tw = round(w)
        if i == 1:
            body += text(round(x), round(y + k(12 * sc)), tw, round(k(22 * sc)), ("%s", ["[COMPLICATION.TEXT]"]), lab_size + 1, A, SEMI)
            body += text(round(x), round(y + k(36 * sc)), tw, round(k(30 * sc)), clock_text(f"({V} % 1440)"), val_size, "#FFFFFFFF", NUM)
        else:
            body += text(round(x), round(y + k(12 * sc)), tw, round(k(22 * sc)), "مضى" if i == 0 else "متبقي", lab_size, SOFT, SEMI)
            body += text(round(x), round(y + k(36 * sc)), tw, round(k(30 * sc)), hm(PASSED if i == 0 else LEFT), val_size, "#FFFFFFFF", NUM)
    comp = f'<Complication type="RANGED_VALUE">{body}</Complication>'
    return slot(sid, round(ox), round(oy), bw, bh, "RANGED_VALUE", "PrayerWindowComplicationService", "RANGED_VALUE",
                f'<BoundingBox x="0" y="0" width="{bw}" height="{bh}"/>', comp, customizable="FALSE", ambient=False)


def prow(sid, y, sc):
    """The row as on the page: squares 100 apart, in a line."""
    g = k(100 * sc)
    return prayer_squares(sid, (C + g, C, C - g), (k(y),) * 3, (1, 1), sc)


# ── the other pieces ─────────────────────────────────────────────────────────
def date_slot(sid, y, size):
    """The date as day/month (5/11), or whatever short text the wearer picks."""
    s = round(k(size))
    w, h = round(k(110)), round(s * 1.35)
    top = round(k(y) - s * 1.0)
    body = f'<Complication type="SHORT_TEXT">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), s, "#FFFFFFFF", NUM)}</Complication>'
    return slot(sid, round(C - w / 2), top, w, h, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body, ambient=False)


def status_line(sid, y, size=13, x=None, sep=True):
    """Battery (a slot the wearer can change) and the temperature (the watch's own weather)."""
    s = round(k(size))
    h = round(s * 1.6)
    top = round(k(y) - s * 1.1)
    w = round(k(70))
    bx = round(C - w - k(6)) if x is None else round(x[0])
    tx = round(C + k(6)) if x is None else round(x[1])
    body = f'<Complication type="SHORT_TEXT">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), s, SOFT, NUM)}</Complication>'
    body += f'<Complication type="RANGED_VALUE">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), s, SOFT, NUM)}</Complication>'
    out = slot(sid, bx, top, w, h, "SHORT_TEXT RANGED_VALUE", None, "RANGED_VALUE", f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body,
               system="WATCH_BATTERY")
    temp = (text(tx, top, w, h, ("%s°", ["[WEATHER.TEMPERATURE]"]), s, SOFT, NUM, alpha="(255 * [WEATHER.IS_AVAILABLE])")
            + text(tx, top, w, h, "--°", s, SOFT, NUM, alpha="(255 * (1 - [WEATHER.IS_AVAILABLE]))"))
    dot = text(round(C - k(6)), top, round(k(12)), h, "·", s, SOFT, SEMI) if (sep and x is None) else ""
    return out + active("temp", temp + dot)


def reading(sid, y, w=192, h=36, size=15):
    """Where reading stopped («الكهف · 18»), on a pill: a tap opens the mushaf there."""
    W, Hh = round(k(w)), round(k(h))
    pill = (f'<PartDraw x="0" y="0" width="{W}" height="{Hh}" alpha="41"><RoundRectangle x="0" y="0" width="{W}" height="{Hh}" '
            f'cornerRadiusX="{k(13):.0f}" cornerRadiusY="{k(13):.0f}"><Fill color="{A}"/></RoundRectangle></PartDraw>')
    t = lambda p: text(0, 0, W, Hh, ("%s", [p]), round(k(size)), "#FFFFFFFF", SEMI)
    body = (f'<Complication type="LONG_TEXT">{pill}{t("[COMPLICATION.TITLE]")}</Complication>'
            f'<Complication type="SHORT_TEXT">{pill}{t("[COMPLICATION.TEXT]")}</Complication>')
    return slot(sid, round(C - W / 2), round(k(y)), W, Hh, "LONG_TEXT SHORT_TEXT", "ContinueReadingComplicationService", "LONG_TEXT",
                f'<BoundingBox x="0" y="0" width="{W}" height="{Hh}"/>', body)


def tap(sid, cx, cy, provider, label=True, size=13):
    """A quick tap: its name in the accent colour (or its icon on the hand faces)."""
    if label:
        w, h = round(k(72)), round(k(26))
        body = (f'<Complication type="SHORT_TEXT">{text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), round(k(size)), A, SEMI)}</Complication>'
                f'<Complication type="SMALL_IMAGE"><PartImage x="{(w - h) // 2}" y="0" width="{h}" height="{h}"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
        types, ptype = "SHORT_TEXT SMALL_IMAGE", "SHORT_TEXT"
    else:
        w = h = round(k(28))
        body = (f'<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="{w}" height="{h}"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>'
                f'<Complication type="SHORT_TEXT">{text(-8, 0, w + 16, h, ("%s", ["[COMPLICATION.TEXT]"]), 11, A, SEMI)}</Complication>')
        types, ptype = "SMALL_IMAGE SHORT_TEXT", "SMALL_IMAGE"
    return slot(sid, round(k(cx) - w / 2), round(k(cy) - h / 2), w, h, types, provider, ptype,
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body)


def taps(sid, y, label=True, dx=54):
    return (tap(sid, 219 + dx, y, "SurahIndexShortcutComplicationService", label)
            + tap(sid + 1, 219 - dx, y, "QuranShortcutComplicationService", label))


def digital_time(y, size, weight_font=NUM):
    s = round(k(size))
    h = round(s * 1.12)
    top = round(k(y) - s * 0.92)
    return (f'<DigitalClock x="0" y="{top}" width="450" height="{h}"><TimeText format="hh:mm" hourFormat="24" align="CENTER" '
            f'x="0" y="0" width="450" height="{h}"><Font family="{weight_font}" size="{s}" color="#FFFFFFFF"/></TimeText></DigitalClock>')


def hands():
    """Hour and minute hands in the chosen colour, the seconds in the accent, and on the
    rim a glowing mark at each: green for the hour and minute, red for the seconds."""
    def hand(name, length, width, color, angle, tail=0, hide=False):
        return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">{HIDE if hide else ""}'
                f'<Transform target="angle" value="{angle}"/>' + draw(
                    f'<Line startX="225" startY="{225 + tail:.1f}" endX="225" endY="{225 - length:.1f}"><Stroke color="{color}" thickness="{width}" cap="ROUND"/></Line>')
                + '</Group>')

    def laser(name, angle, width, color, glow):
        r1, r2 = k(203), k(217)
        line = lambda w, c: f'<Line startX="225" startY="{225 - r1:.1f}" endX="225" endY="{225 - r2:.1f}"><Stroke color="{c}" thickness="{w}" cap="ROUND"/></Line>'
        return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">{HIDE}'
                f'<Transform target="angle" value="{angle}"/>' + draw(line(width * 3, glow) + line(width * 1.8, glow) + line(width, color)) + '</Group>')
    hour = "([HOUR_0_23] % 12) * 30 + [MINUTE] * 0.5"
    minute = "[MINUTE] * 6 + [SECOND] * 0.1"
    sec = "[SECOND] * 6"
    return (laser("laserH", hour, 5, "#FF7DFFB0", "#337DFFB0") + laser("laserM", minute, 4, "#FF7DFFB0", "#337DFFB0")
            + laser("laserS", sec, 2.5, "#FFFF6A5A", "#33FF6A5A")
            + hand("hour", k(100), 11, H, hour) + hand("minute", k(160), 6, H, minute)
            + hand("second", k(175), 2.5, A, sec, tail=k(30), hide=True)
            + '<Group name="hub" x="0" y="0" width="450" height="450">'
            + draw(f'<Ellipse x="{225 - k(8):.1f}" y="{225 - k(8):.1f}" width="{k(16):.1f}" height="{k(16):.1f}"><Fill color="{A}"/></Ellipse>'
                   f'<Ellipse x="{225 - k(3):.1f}" y="{225 - k(3):.1f}" width="{k(6):.1f}" height="{k(6):.1f}"><Fill color="#FF000000"/></Ellipse>') + '</Group>')


def ticks(r=212, color="#FFCFD6D2", minor="#FF3C4440"):
    out = []
    for i in range(60):
        big = i % 5 == 0
        (x0, y0), (x1, y1) = pol(k(r), i * 6), pol(k(r - (16 if big else 7)), i * 6)
        out.append(f'<Line startX="{x0:.1f}" startY="{y0:.1f}" endX="{x1:.1f}" endY="{y1:.1f}"><Stroke color="{minor if not big else color}" thickness="{3 if big else 1.4}"/></Line>')
    return active("ticks", draw("".join(out)))


def numerals(r, size_of, color="#FFFFFFFF", hours=range(1, 13)):
    out = ""
    for h in hours:
        x, y = pol(k(r), h * 30)
        s = round(k(size_of(h)))
        w, hh = round(s * 1.8), round(s * 1.3)
        out += text(round(x - w / 2), round(y - hh / 2), w, hh, str(h), s, color, NUM)
    return active("numerals", out)


def background(color, picture):
    out = active("bg", draw(f'<Ellipse x="0" y="0" width="450" height="450"><Fill color="{color}"/></Ellipse>'))
    if not picture:
        return out
    # A picture under the face: which one, and how strongly it shows, are two settings.
    # The options hold pictures only, never a slot.
    fades = ""
    for fid, _, alpha in FADES:
        opts = ""
        for bid, _, res in BACKS:
            img = (f'<PartImage x="0" y="0" width="450" height="450" alpha="{alpha}"><Image resource="{res}"/></PartImage>' if res
                   else '<PartDraw x="0" y="0" width="1" height="1"><Line startX="0" startY="0" endX="0" endY="0"><Stroke color="#00000000" thickness="1"/></Line></PartDraw>')
            opts += f'<ListOption id="{bid}"><Group name="bg_{fid}_{bid}" x="0" y="0" width="450" height="450">{img}</Group></ListOption>'
        fades += f'<ListOption id="{fid}"><Group name="fade_{fid}" x="0" y="0" width="450" height="450"><ListConfiguration id="back">{opts}</ListConfiguration></Group></ListOption>'
    return out + f'<Group name="picture" x="0" y="0" width="450" height="450">{HIDE}<ListConfiguration id="fade">{fades}</ListConfiguration></Group>'


# ── rim rings with their own data ────────────────────────────────────────────
def prayer_rim(sid, r=206, width=10):
    """«المدار»: the rim fills from the last prayer toward the next, a white bead at the front."""
    rr = k(r)
    body = draw(arc(rr, 0, 359.9, TRACK, k(width)))
    body += draw(f'<Arc centerX="225" centerY="225" width="{2 * rr:.1f}" height="{2 * rr:.1f}" startAngle="0" endAngle="0">'
                 f'<Transform target="endAngle" value="(359.9 * {FRAC})"/><Stroke color="{A}" thickness="{k(width):.1f}" cap="ROUND"/></Arc>')
    body += (f'<Group name="bead" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5"><Transform target="angle" value="(360 * {FRAC})"/>'
             + draw(f'<Ellipse x="{225 - k(9):.1f}" y="{225 - rr - k(9):.1f}" width="{k(18):.1f}" height="{k(18):.1f}"><Fill color="#FFFFFFFF"/></Ellipse>') + '</Group>')
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="0" endAngle="359.9" thickness="{225 - rr + k(12):.0f}"/>'
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE", "PrayerWindowComplicationService", "RANGED_VALUE", bound,
                f'<Complication type="RANGED_VALUE">{body}</Complication>', customizable="FALSE")


def level_rim(sid, r, a0, a1, width, color, system, provider=None):
    """«الموجتان»: a ring at the top showing any level the wearer picks (battery, steps...)."""
    rr = k(r)
    f = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
         "max(1, [COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")
    body = draw(arc(rr, a0, a1, "#FF232B27", k(width)))
    body += draw(f'<Arc centerX="225" centerY="225" width="{2 * rr:.1f}" height="{2 * rr:.1f}" startAngle="{a0}" endAngle="{a0}">'
                 f'<Transform target="endAngle" value="({a0} + ({a1} - {a0}) * {f})"/><Stroke color="{color}" thickness="{k(width):.1f}" cap="ROUND"/></Arc>')
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE", provider, "RANGED_VALUE",
                f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{a0}" endAngle="{a1}" thickness="{225 - rr + k(width):.0f}"/>',
                f'<Complication type="RANGED_VALUE">{body}</Complication>', system=system)


SIX = ("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")


def six_rim(sid, r, width_on, width_off, labels=False, r_text=None, size=15):
    """Six sections, the dawn prayer first at the top; the running one in the accent and
    thicker. With labels, each name and start time along the ring, upright below too."""
    rr = k(r)
    index = f"floor({V} / 2073600)"
    body = ""
    for i in range(6):
        a0, a1 = i * 60 + 3, i * 60 + 57
        run = f"(1 - clamp(abs({index} - {i}), 0, 1))"
        body += draw(arc(rr, a0, a1, "#FF2B322E", k(width_off)))
        body += (f'<Group name="six{i}" x="0" y="0" width="450" height="450"><Transform target="alpha" value="(255 * {run})"/>'
                 + draw(arc(rr, a0, a1, A, k(width_on))) + '</Group>')
        if labels:
            mid = (a0 + a1) / 2
            bottom = 90 < mid < 270
            s0, s1, direction = (a1 - 3, a0 + 3, "COUNTER_CLOCKWISE") if bottom else (a0 + 3, a1 - 3, "CLOCKWISE")
            d = 2 * k(r_text if not bottom else r_text + 14)
            if bottom:
                d = 2 * k(r_text - 4)
            tpl = f'<Template>{SIX[i]} %s<Parameter expression="subText([COMPLICATION.TEXT], {i * 6}, {i * 6 + 5})"/></Template>'
            for color, alpha in ((A, run), ("#FFC2CBC6", f"(1 - {run})")):
                body += (f'<PartText x="0" y="0" width="450" height="450"><Transform target="alpha" value="(255 * {alpha})"/>'
                         f'<TextCircular centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" startAngle="{s0:.1f}" endAngle="{s1:.1f}" '
                         f'direction="{direction}" align="CENTER"><Font family="{SEMI}" size="{round(k(size))}" color="{color}">{tpl}</Font></TextCircular></PartText>')
    reach = 225 - (k(r_text) - k(size) if labels else rr - k(width_on))
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="0" endAngle="359.9" thickness="{reach:.0f}"/>'
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE", "PrayerRingComplicationService", "RANGED_VALUE", bound,
                f'<Complication type="RANGED_VALUE">{body}</Complication>', customizable="FALSE")


def sun_arc(sid, r=196, a0=-80, a1=80):
    """«الأفق»: the day from sunrise to sunset, the sun where the day is now."""
    rr = k(r)
    sr, ss = f"floor({V} / 1440)", f"({V} % 1440)"
    f = f"clamp((({NOW}) - {sr}) / max(1, {ss} - {sr}), 0, 1)"
    body = draw(arc(rr, a0, a1, "#FF26303A", k(4)))
    body += draw(f'<Arc centerX="225" centerY="225" width="{2 * rr:.1f}" height="{2 * rr:.1f}" startAngle="{a0}" endAngle="{a0}">'
                 f'<Transform target="endAngle" value="({a0} + ({a1} - {a0}) * {f})"/><Stroke color="{A}" thickness="{k(4):.1f}" cap="ROUND"/></Arc>')
    body += (f'<Group name="sun" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5"><Transform target="angle" value="({a0} + ({a1} - {a0}) * {f})"/>'
             + draw(f'<Ellipse x="{225 - k(10):.1f}" y="{225 - rr - k(10):.1f}" width="{k(20):.1f}" height="{k(20):.1f}"><Fill color="{A}"/></Ellipse>') + '</Group>')
    for ang, name in ((a0 - 6, "الشروق"), (a1 + 6, "المغرب")):
        x, y = pol(rr, ang)
        body += text(round(x - k(36)), round(y + k(8)), round(k(72)), round(k(20)), name, round(k(11)), SOFT, SEMI)
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{a0 - 10}" endAngle="{a1 + 10}" thickness="{225 - rr + k(24):.0f}"/>'
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE", "SunTimesComplicationService", "RANGED_VALUE", bound,
                f'<Complication type="RANGED_VALUE">{body}</Complication>', customizable="FALSE")


# ── the faces (positions from the proposal page, in its 438 px space) ────────
def f_orbit():
    return (background("#FF0A0D0C", True), prayer_rim(1), status_line(2, 70), date_slot(3, 116, 28),
            digital_time(212, 96), prow(4, 232, .95), reading(5, 322), taps(6, 382))


def f_crescent():
    return (background("#FF0C0E12", True), ticks(), numerals(178, lambda h: 30 if h % 3 else 42),
            date_slot(1, 146, 26), status_line(2, 226, 15, x=(k(312) - k(35), k(126) - k(35)), sep=False),
            prow(3, 244, .82), reading(4, 318), taps(5, 376, label=False, dx=48), hands())


def f_waves():
    return (background("#FF0D1110", False), level_rim(1, 205, -110, 110, 9, A, "WATCH_BATTERY"),
            level_rim(2, 188, -90, 90, 6, "#D9E0B252", "STEP_COUNT"),
            status_line(3, 70), date_slot(4, 116, 28), digital_time(212, 96), prow(5, 232, .95), reading(6, 322), taps(7, 382))


def f_dials():
    xs, ys = (306, 219, 132), (192, 272, 192)
    return (background("#FF0F1210", False), numerals(184, lambda h: 36),
            prayer_squares(1, tuple(C + k(x - 219) for x in xs), tuple(k(y) for y in ys), (88 / 86, 80 / 76), 1.0),
            date_slot(2, 112, 22), status_line(3, 138, 17), reading(4, 150, 118, 26, 12), taps(5, 375, label=False, dx=50), hands())


def f_horizon():
    return (background("#FF0B0F14", False), sun_arc(1), ticks(212, "#FF8FA0B0"),
            numerals(176, lambda h: 24, hours=(1, 2, 4, 5, 6, 7, 8, 10, 11, 12)),
            date_slot(2, 140, 26), status_line(3, 226, 16, x=(k(96) - k(35), k(342) - k(35)), sep=False),
            prow(4, 246, .82), reading(5, 318), taps(6, 382), hands())


def f_calm():
    return (background("#FF000000", True), six_rim(1, 210, 8, 4), status_line(2, 70), date_slot(3, 110, 28),
            digital_time(212, 112, "plex_semibold"), prow(4, 230, 1.0), reading(5, 322), taps(6, 382))


def f_sundial():
    ticks_ = []
    for i in range(60):
        big = i % 5 == 0
        (x0, y0), (x1, y1) = pol(k(216), i * 6), pol(k(216 - (10 if big else 5)), i * 6)
        ticks_.append(f'<Line startX="{x0:.1f}" startY="{y0:.1f}" endX="{x1:.1f}" endY="{y1:.1f}"><Stroke color="{"#FFCFD6D2" if big else "#FF4C5550"}" thickness="{2.5 if big else 1.2}"/></Line>')
    return (background("#FF0D100F", False), active("ticks", draw("".join(ticks_))),
            numerals(194, lambda h: 20, "#FFDDE3E0"), six_rim(1, 170, 12, 7, labels=True, r_text=150, size=14),
            date_slot(2, 132, 24), status_line(3, 158, 13), prow(4, 238, .74), reading(5, 306), taps(6, 352, label=False, dx=40), hands())


def f_squares():
    return (background("#FF0B0D0C", True), status_line(1, 66, 15), date_slot(2, 108, 30),
            digital_time(206, 104), prow(3, 226, 1.15), reading(4, 328, 210, 38, 16), taps(5, 386))


FACES = {
    "orbit": ("المدار", "DIGITAL", f_orbit, 0, False),
    "crescent": ("الهلال", "ANALOG", f_crescent, 1, True),
    "waves": ("الموجتان", "DIGITAL", f_waves, 2, False),
    "dials": ("الثلاثة عدادات", "ANALOG", f_dials, 3, True),
    "horizon": ("الأفق", "ANALOG", f_horizon, 4, True),
    "calm": ("البسيطة", "DIGITAL", f_calm, 5, False),
    "sundial": ("المزولة", "ANALOG", f_sundial, 0, True),
    "squares": ("المربعات الثلاثة", "DIGITAL", f_squares, 1, False),
}


def config(accent, analog, picture):
    c = '<ColorConfiguration id="accent" displayName="accent_label" defaultValue="' + ACCENTS[accent][0] + '">'
    c += "".join(f'<ColorOption id="{i}" displayName="accent_{i}" colors="{v}"/>' for i, _, v in ACCENTS) + '</ColorConfiguration>'
    if analog:
        c += '<ColorConfiguration id="hands" displayName="hands_label" defaultValue="white">'
        c += "".join(f'<ColorOption id="{i}" displayName="hands_{i}" colors="{v}"/>' for i, _, v in HANDS) + '</ColorConfiguration>'
    if picture:
        c += '<ListConfiguration id="back" displayName="back_label" defaultValue="none">'
        c += "".join(f'<ListOption id="{i}" displayName="back_{i}"/>' for i, _, _ in BACKS) + '</ListConfiguration>'
        c += '<ListConfiguration id="fade" displayName="fade_label" defaultValue="f25">'
        c += "".join(f'<ListOption id="{i}" displayName="fade_{i}"/>' for i, _, _ in FADES) + '</ListConfiguration>'
    return "<UserConfigurations>" + c + "</UserConfigurations>"


def write(fid):
    name, kind, build, accent, analog = FACES[fid]
    module = f"watchface-c-{fid}"
    existing = os.path.join(kit.ROOT, module, "src/main/res/raw/watchface.xml")
    assert not os.path.exists(existing) or "make_v8_faces.py" in open(existing, encoding="utf-8").read(), module
    mcf.ensure_module(module, name)
    root = os.path.join(kit.ROOT, module)
    res = os.path.join(root, "src/main/res")
    shutil.copy(os.path.join(mcf.FONT_DIR, NUM + ".ttf"), os.path.join(res, "font", NUM + ".ttf"))
    parts = build()
    picture = any('<ListConfiguration id="fade">' in p for p in parts)
    if picture:
        for b in ("haram", "nabawi", "kaaba"):
            shutil.copy(os.path.join(kit.ROOT, "design/assets", f"bg_{b}.png"), os.path.join(res, "drawable-nodpi", f"bg_{b}.png"))
    strings = [f'    <string name="face_name">القرآن الكريم · {name}</string>', '    <string name="accent_label">اللون</string>',
               '    <string name="hands_label">لون العقارب</string>', '    <string name="back_label">الخلفية</string>',
               '    <string name="fade_label">وضوح الخلفية</string>']
    strings += [f'    <string name="accent_{i}">{n}</string>' for i, n, _ in ACCENTS]
    strings += [f'    <string name="hands_{i}">{n}</string>' for i, n, _ in HANDS]
    strings += [f'    <string name="back_{i}">{n}</string>' for i, n, _ in BACKS]
    strings += [f'    <string name="fade_{i}">{n}</string>' for i, n, _ in FADES]
    open(os.path.join(res, "values/strings.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(strings) + "\n</resources>\n")
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f'<!-- «{name}». Generated by scripts/wff/make_v8_faces.py: edit the script, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           f'<Metadata key="CLOCK_TYPE" value="{kind}"/>\n<Metadata key="PREVIEW_TIME" value="10:08:36"/>\n'
           + config(accent, analog, picture) + '\n<Scene backgroundColor="#FF000000">\n' + "\n".join(parts) + "\n</Scene>\n</WatchFace>\n")
    open(existing, "w", encoding="utf-8").write(xml)
    n = xml.count("<ComplicationSlot ")
    ids = [s.split('"')[0] for s in xml.split('slotId="')[1:]]
    assert n <= 8 and len(ids) == len(set(ids)), (fid, n, ids)
    import xml.etree.ElementTree as ET
    tree = ET.fromstring(xml.split("\n", 2)[2])
    assert all(o.find(".//ComplicationSlot") is None for o in tree.iter("ListOption")), fid
    print(f"wrote {existing} ({n} slots, {len(xml) // 1024} KB)")


if __name__ == "__main__":
    for fid in FACES:
        write(fid)
