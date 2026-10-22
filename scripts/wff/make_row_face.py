#!/usr/bin/env python3
"""Writes watchface-row/src/main/res/raw/watchface.xml: design 1, "one row".

The face was drawn at 438 px on the design canvas; Watch Face Format works in a
450 px square, so every position here is the design's times 450/438. Kept as a
script because the passed/left lines need one text per case of the two-hour
rule, which is tedious and error-prone by hand.

Samsung's runtime rules this follows (each broke a face before):
  - a Template holds %s for every Parameter; static text is plain Font text
  - a slot's fallback type is one of its supportedTypes
  - a complication draws only inside its slot's bounds; edge slots use BoundingArc
  - at most eight slots
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "watchface-row/src/main/res/raw/watchface.xml")
APP = "com.quran.watch8/com.quran.watch8.complication."

WHITE, SOFT, GOLD, GREEN, YELLOW = "#FFFFFFFF", "#FFD3DAD6", "#FFE8BC3E", "#FF3CC489", "#FFE8BC3E"
BOLD, SEMI = "plex_bold", "plex_semibold"


def font(size, color=WHITE, family=BOLD):
    return f'<Font family="{family}" size="{size}" color="{color}">'


def text(x, y, w, h, body, size, color=WHITE, family=BOLD, alpha=None, ambient=None, align="CENTER"):
    """A PartText. `body` is either plain text or (template, [expressions])."""
    tr = f'<Transform target="alpha" value="{alpha}"/>' if alpha else ""
    var = f'<Variant mode="AMBIENT" target="alpha" value="{ambient}"/>' if ambient is not None else ""
    if isinstance(body, tuple):
        tpl, params = body
        assert tpl.count("%s") == len(params), tpl
        inner = "<Template>" + tpl + "".join(f'<Parameter expression="{p}"/>' for p in params) + "</Template>"
    else:
        inner = body
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}">{tr}{var}'
            f'<Text align="{align}" ellipsis="TRUE">{font(size, color, family)}{inner}</Font></Text></PartText>')


# ── the two-hour rule, as texts switched on by alpha ────────────────────────
def lt(d, a):   # 1 when d < a (d whole minutes)
    return f"clamp({a} - {d}, 0, 1)"


def ge(d, a):   # 1 when d >= a
    return f"clamp({d} - {a} + 1, 0, 1)"


def duration(d, x, y, w, h, size, color=WHITE):
    """Under two hours: hours and minutes; from two hours: whole hours only.
    Written short (س for hours, د for minutes) so the numbers can be large."""
    m = f"({d} % 60)"
    hrs = f"floor({d} / 60)"
    no_min = f"(1 - clamp({m}, 0, 1))"
    cases = [
        (f"{lt(d, 60)}", ("%sد", [d])),
        (f"{ge(d, 60)} * {lt(d, 120)} * {no_min}", "1س"),
        (f"{ge(d, 60)} * {lt(d, 120)} * clamp({m}, 0, 1)", ("1س %sد", [m])),
        (f"{ge(d, 120)}", ("%sس", [hrs])),
    ]
    return "".join(text(x, y, w, h, body, size, color, alpha=f"(255 * {cond})") for cond, body in cases)


def slot(sid, x, y, w, h, types, provider, ptype, bound, body, customizable="TRUE", system="EMPTY", ambient=True):
    assert ptype in types.split(), (sid, ptype, types)
    pol = (f'<DefaultProviderPolicy defaultSystemProvider="{system}" defaultSystemProviderType="{ptype}" '
           + (f'primaryProvider="{APP}{provider}" primaryProviderType="{ptype}"' if provider else "") + '/>')
    amb = '<Variant mode="AMBIENT" target="alpha" value="0"/>' if ambient else ""
    return (f'<ComplicationSlot slotId="{sid}" x="{x}" y="{y}" width="{w}" height="{h}" supportedTypes="{types}" '
            f'isCustomizable="{customizable}">{amb}{pol}{bound}{body}</ComplicationSlot>')


def main():
    p = []
    # ── background, chosen in the face's settings ──
    p.append('<ListConfiguration id="bg">'
             '<ListOption id="mosques"><PartImage x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/><Image resource="bg_mosques"/></PartImage></ListOption>'
             '<ListOption id="pattern"><PartImage x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/><Image resource="bg_pattern"/></PartImage></ListOption>'
             '<ListOption id="plain"><PartDraw x="0" y="0" width="450" height="450"><Rectangle x="0" y="0" width="450" height="450"><Fill color="#FF000000"/></Rectangle></PartDraw></ListOption>'
             '</ListConfiguration>')

    # ── seconds ring: from 12 o'clock, clockwise, once a minute ──
    p.append('<PartDraw x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/>'
             '<Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="360"><Stroke color="#FF123A30" thickness="3"/></Arc></PartDraw>')
    p.append('<PartDraw x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/>'
             '<Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="0">'
             '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FF43D49A" thickness="3" cap="ROUND"/></Arc></PartDraw>')
    p.append('<Group name="secondsHead" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">'
             '<Transform target="angle" value="([SECOND] * 6)"/><Variant mode="AMBIENT" target="alpha" value="0"/>'
             '<PartDraw x="0" y="0" width="450" height="450"><Ellipse x="217" y="0" width="16" height="16"><Fill color="#667DF0C0"/></Ellipse>'
             '<Ellipse x="221" y="4" width="8" height="8"><Fill color="#FFF2FFF9"/></Ellipse></PartDraw></Group>')
    # ── the bottom chord; the round screen trims the rectangle ──
    p.append('<PartDraw x="0" y="380" width="450" height="70"><Variant mode="AMBIENT" target="alpha" value="0"/>'
             '<Rectangle x="0" y="0" width="450" height="70"><Fill color="#E0041010"/></Rectangle>'
             '<Line startX="225" startY="6" endX="225" endY="52"><Stroke color="#38AADCC8" thickness="1.5"/></Line></PartDraw>')

    # ── the time ──
    p.append('<DigitalClock x="0" y="142" width="450" height="114"><TimeText format="hh:mm" hourFormat="24" align="CENTER" x="0" y="0" width="450" height="114">'
             + font(110) + '</Font></TimeText></DigitalClock>')

    # ── temperature (bottom left): the watch's own weather, not a slot ──
    p.append('<Group name="temperature" x="123" y="384" width="100" height="47"><Variant mode="AMBIENT" target="alpha" value="0"/>'
             '<PartDraw x="6" y="10" width="22" height="28"><RoundRectangle x="7" y="0" width="8" height="18" cornerRadiusX="4" cornerRadiusY="4"><Stroke color="#FFE8BC3E" thickness="2"/></RoundRectangle>'
             '<Ellipse x="3" y="15" width="16" height="12"><Stroke color="#FFE8BC3E" thickness="2"/></Ellipse></PartDraw>'
             + text(30, 0, 70, 47, ("%s°", ["[WEATHER.TEMPERATURE]"]), 24, alpha="(255 * [WEATHER.IS_AVAILABLE])")
             + text(30, 0, 70, 47, "--°", 24, SOFT, alpha="(255 * (1 - [WEATHER.IS_AVAILABLE]))")
             + '</Group>')

    # ── slots ──
    # 1. Quran, right edge: a narrow see-through band, the icon alone
    arc_r = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="45" endAngle="135" thickness="44"/>'
    arc_l = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="225" endAngle="315" thickness="44"/>'
    icon = lambda x, y, s: (f'<Complication type="SMALL_IMAGE"><PartImage x="{x}" y="{y}" width="{s}" height="{s}"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
    p.append(slot(1, 406, 93, 44, 264, "SMALL_IMAGE", "QuranShortcutComplicationService", "SMALL_IMAGE", arc_r, icon(6, 115, 34)))
    # 2. bookmarks, left edge
    p.append(slot(2, 0, 93, 44, 264, "SMALL_IMAGE", "BookmarksShortcutComplicationService", "SMALL_IMAGE", arc_l, icon(4, 115, 34)))
    # 3. bottom right: battery by default
    small = ('<Complication type="SHORT_TEXT">'
             '<PartImage x="70" y="12" width="22" height="22"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage>'
             + text(0, 0, 70, 47, ("%s", ["[COMPLICATION.TEXT]"]), 24) + '</Complication>'
             '<Complication type="RANGED_VALUE">'
             '<PartImage x="70" y="12" width="22" height="22"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage>'
             + text(0, 0, 70, 47, ("%s", ["[COMPLICATION.TEXT]"]), 24) + '</Complication>')
    p.append(slot(3, 227, 384, 100, 47, "SHORT_TEXT RANGED_VALUE", None, "RANGED_VALUE",
                  '<BoundingBox x="0" y="0" width="100" height="47"/>', small, system="WATCH_BATTERY"))
    # 4. the date, top
    p.append(slot(4, 125, 29, 200, 36, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                  '<BoundingBox x="0" y="0" width="200" height="36"/>',
                  '<Complication type="SHORT_TEXT">' + text(0, 0, 200, 36, ("%s", ["[COMPLICATION.TEXT]"]), 30) + '</Complication>',
                  ambient=False))
    # 5. last reading position
    reading = ('<Complication type="LONG_TEXT"><PartDraw x="0" y="0" width="310" height="70">'
               '<RoundRectangle x="0" y="0" width="310" height="70" cornerRadiusX="22" cornerRadiusY="22"><Fill color="#B80A201C"/><Stroke color="#59AADCC8" thickness="1"/></RoundRectangle></PartDraw>'
               + text(12, 4, 286, 24, ("%s", ["[COMPLICATION.TITLE]"]), 16, SOFT, SEMI)
               + text(12, 27, 286, 38, ("%s", ["[COMPLICATION.TEXT]"]), 23) + '</Complication>')
    p.append(slot(5, 70, 68, 310, 70, "LONG_TEXT", "ContinueReadingComplicationService", "LONG_TEXT",
                  '<BoundingBox x="0" y="0" width="310" height="70"/>', reading))
    # 6. the prayer row: passed (right) | next and its time (middle) | left (left)
    V = "[COMPLICATION.RANGED_VALUE_VALUE]"
    now = "([HOUR_0_23] * 60 + [MINUTE])"
    nxt = f"({V} % 1440)"
    prev = f"floor({V} / 1440)"
    passed = f"(({now} - {prev} + 1440) % 1440)"
    left = f"(({nxt} - {now} + 1440) % 1440)"
    col = 323 / 3
    row = ('<Complication type="RANGED_VALUE"><PartDraw x="0" y="0" width="323" height="101">'
           '<RoundRectangle x="0" y="0" width="323" height="101" cornerRadiusX="16" cornerRadiusY="16"><Fill color="#D104100D"/><Stroke color="#47AADCC8" thickness="1"/></RoundRectangle>'
           f'<Line startX="{col:.0f}" startY="14" endX="{col:.0f}" endY="87"><Stroke color="#38AADCC8" thickness="1"/></Line>'
           f'<Line startX="{2*col:.0f}" startY="14" endX="{2*col:.0f}" endY="87"><Stroke color="#38AADCC8" thickness="1"/></Line></PartDraw>'
           # right column (RTL first): passed
           + text(int(2 * col), 14, int(col), 24, "مضى", 17, SOFT, SEMI)
           + duration(passed, int(2 * col), 42, int(col), 42, 26)
           # middle: the next prayer and its time
           + text(int(col), 10, int(col), 38, ("%s", ["[COMPLICATION.TEXT]"]), 26)
           + text(int(col), 52, int(col), 32, ("%s%s:%s%s", [f"floor({nxt} / 600)", f"(floor({nxt} / 60) % 10)", f"floor(({nxt} % 60) / 10)", f"({nxt} % 10)"]), 29)
           # left: the time to it
           + text(0, 14, int(col), 24, "متبقي", 17, SOFT, SEMI)
           + duration(left, 0, 42, int(col), 42, 26)
           + '</Complication>')
    p.append(slot(6, 64, 269, 323, 101, "RANGED_VALUE", "PrayerWindowComplicationService", "RANGED_VALUE",
                  '<BoundingBox x="0" y="0" width="323" height="101"/>', row, customizable="FALSE", ambient=False))

    # ── always-on: the time, the date and the next prayer, dim ──
    p.append('<Group name="ambientPrayer" x="0" y="0" width="450" height="450" alpha="0"><Variant mode="AMBIENT" target="alpha" value="255"/>'
             '<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="360"><Stroke color="#FF2C3A35" thickness="1.5"/></Arc></PartDraw></Group>')

    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Design 1, "one row". Generated by scripts/wff/make_row_face.py: edit that, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           '<Metadata key="CLOCK_TYPE" value="DIGITAL"/>\n<Metadata key="PREVIEW_TIME" value="16:40:24"/>\n'
           '<UserConfigurations><ListConfiguration id="bg" displayName="bg_label" defaultValue="mosques">'
           '<ListOption id="mosques" displayName="bg_mosques_label"/><ListOption id="pattern" displayName="bg_pattern_label"/>'
           '<ListOption id="plain" displayName="bg_plain_label"/></ListConfiguration></UserConfigurations>\n'
           '<Scene backgroundColor="#FF000000">\n' + "\n".join(p) + '\n</Scene>\n</WatchFace>\n')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w").write(xml)
    slots = xml.count("<ComplicationSlot ")
    assert slots <= 8, slots
    print(f"wrote {OUT} ({slots} slots)")


if __name__ == "__main__":
    main()
