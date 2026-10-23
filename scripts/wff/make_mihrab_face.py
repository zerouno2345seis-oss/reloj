#!/usr/bin/env python3
"""Writes watchface-mihrab/src/main/res/raw/watchface.xml: the mihrab face.

A full ring of six coloured segments round the rim, each one a slot with its
value written along the arc (upright on the lower half); the seconds run as a
thin line just inside it. In the middle, the mihrab arch over zaher's photo
of the Buenos Aires mosque, the 24 h time and the prayer row, scaled to fit.
The always-on screen is the row face's (time, date, prayer row) with the ring
as dim outlines. Approved spec, from 12 o'clock clockwise:

    0  date           fixed            60  battery meter  fixed
  120  steps          customizable    180  heart rate     customizable
  240  surah · verse  fixed           300  next prayer    customizable

Eight slots: the six segments and the prayer row twice (scaled in the middle,
and at the row face's place for the always-on screen). The surah segment is
the way into the mushaf here, in place of a tap on the time.
"""
import kit
from make_row_face import font, slot

T = 49                       # ring thickness: 48 px on the 438 px design
MID = 225 - T / 2            # radius of the ring's middle line
GAP = 3
TEXT_D = 2 * MID - 6         # the text's circle, a hair inside the middle line
DARK = "#FF1A1408"
WHITE = "#FFFFFFFF"
AOD_GREY = "#FFB8BEC4"


def _layer(name, inner, when="active"):
    """A full-face group shown only on the active face, or only on the always-on screen."""
    if when == "active":
        return f'<Group name="{name}" x="0" y="0" width="450" height="450">{kit.HIDE_IN_AMBIENT}{inner}</Group>'
    return (f'<Group name="{name}" x="0" y="0" width="450" height="450" alpha="0">'
            f'<Variant mode="AMBIENT" target="alpha" value="255"/>{inner}</Group>')


def _arc(start, end, color, d=2 * MID, width=T):
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
            f'startAngle="{start}" endAngle="{end}"><Stroke color="{color}" thickness="{width}" cap="BUTT"/></Arc></PartDraw>')


def _level(start, end, color):
    v = ("([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
         "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN])")
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{2 * MID:.0f}" height="{2 * MID:.0f}" '
            f'startAngle="{start}" endAngle="{start}"><Transform target="endAngle" value="({start} + ({end} - {start}) * clamp({v}, 0, 1))"/>'
            f'<Stroke color="{color}" thickness="{T}" cap="BUTT"/></Arc></PartDraw>')


def _text(start, end, body, color, bottom):
    tpl, params = body
    assert tpl.count("%s") == len(params), tpl
    inner = "<Template>" + tpl + "".join(f'<Parameter expression="{p}"/>' for p in params) + "</Template>"
    a, b, direction = (end, start, "COUNTER_CLOCKWISE") if bottom else (start, end, "CLOCKWISE")
    return (f'<PartText x="0" y="0" width="450" height="450">'
            f'<TextCircular centerX="225" centerY="225" width="{TEXT_D:.0f}" height="{TEXT_D:.0f}" startAngle="{a}" endAngle="{b}" '
            f'direction="{direction}" align="CENTER">' + font(22, color) + inner + '</Font></TextCircular></PartText>')


def segment(sid, centre, color, types, default, provider=None, system="EMPTY", customizable="FALSE",
            body=("%s", ["[COMPLICATION.TEXT]"]), meter=False, text_in_ambient=False):
    """One segment of the ring: its centre ±30°, less the gap. A meter shows a dim track
    with its level in full colour and white text; a text segment is solid with dark text."""
    start, end = centre - 30 + GAP / 2, centre + 30 - GAP / 2
    if start < 0:  # the top segment: 331.5 to 388.5, not a negative angle
        start, end = start + 360, end + 360
    bottom = 90 < centre % 360 < 270
    c = color.lstrip("#")
    out = []
    for t in types.split():
        if meter and t == "RANGED_VALUE":
            draw, ink = _arc(start, end, f"#55{c}") + _level(start, end, f"#FF{c}"), WHITE
        else:
            draw, ink = _arc(start, end, f"#FF{c}"), DARK
        layers = _layer(f"seg{sid}", draw + _text(start, end, body, ink, bottom))
        if text_in_ambient:
            layers += _layer(f"segAod{sid}", _text(start, end, body, AOD_GREY, bottom), when="ambient")
        out.append(f'<Complication type="{t}">{layers}</Complication>')
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{start}" endAngle="{end}" thickness="{T}"/>'
    return slot(sid, 0, 0, 450, 450, types, provider, default, bound, "".join(out),
                customizable=customizable, system=system, ambient=False)


def background():
    return ('<Group name="background" x="0" y="0" width="450" height="450" alpha="150">' + kit.HIDE_IN_AMBIENT
            + '<PartImage x="0" y="0" width="450" height="450"><Image resource="bg_photo"/></PartImage></Group>')


def arch():
    gold = "#FFE8BC3E"
    return _layer("arch", '<PartDraw x="0" y="0" width="450" height="450">'
                  f'<Arc centerX="225" centerY="190" width="210" height="160" startAngle="270" endAngle="450"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Arc>'
                  f'<Line startX="120" startY="190" endX="120" endY="252"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Line>'
                  f'<Line startX="330" startY="190" endX="330" endY="252"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Line>'
                  '</PartDraw>')


def inner_seconds():
    d = 2 * (225 - T - 5)
    return _layer("seconds",
                  f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="360"><Stroke color="#FF2A2416" thickness="3"/></Arc></PartDraw>'
                  f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="0">'
                  '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FFE8BC3E" thickness="3" cap="ROUND"/></Arc></PartDraw>')


def ambient_outline():
    """On the always-on screen the ring is only dim outlines."""
    arcs = "".join(f'<Arc centerX="225" centerY="225" width="{2 * MID:.0f}" height="{2 * MID:.0f}" '
                   f'startAngle="{c + 330 + GAP / 2}" endAngle="{c + 390 - GAP / 2}"><Stroke color="#FF2C3A35" thickness="1.5"/></Arc>'
                   for c in range(0, 360, 60))
    return _layer("ambientRing", f'<PartDraw x="0" y="0" width="450" height="450">{arcs}</PartDraw>', when="ambient")


def main():
    p = [
        background(),
        arch(),
        inner_seconds(),
        kit.clock(y=150, size=72, when="active", height=80),
        kit.ambient_clock(),
        ambient_outline(),
        segment(61, 0, "#d9b36a", "SHORT_TEXT", "SHORT_TEXT", "GregorianDateComplicationService", text_in_ambient=True),
        segment(62, 60, "#3fb58a", "RANGED_VALUE SHORT_TEXT", "RANGED_VALUE", system="WATCH_BATTERY", meter=True),
        segment(63, 120, "#9b8cf0", "RANGED_VALUE SHORT_TEXT", "RANGED_VALUE", system="STEP_COUNT", customizable="TRUE", meter=True),
        segment(64, 180, "#e46a6a", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", system="HEART_RATE", customizable="TRUE"),
        segment(65, 240, "#d9b36a", "SHORT_TEXT", "SHORT_TEXT", "ContinueReadingComplicationService"),
        segment(66, 300, "#5aa9e6", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", "NextPrayerComplicationService",
                customizable="TRUE", body=("%s %s", ["[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"])),
        kit.prayer_row(67, box=(100, 258, 250, 76), when="active"),
        kit.prayer_row(68, when="ambient"),
    ]
    kit.write("watchface-mihrab", "The mihrab face", "DIGITAL", p)


if __name__ == "__main__":
    main()
