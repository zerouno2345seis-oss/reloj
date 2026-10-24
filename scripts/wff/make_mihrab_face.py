#!/usr/bin/env python3
"""Writes watchface-mihrab/src/main/res/raw/watchface.xml: the mihrab face.

zaher's design from the face editor (2026-10-23), scaled from 438 to 450 px:
a full ring of six coloured segments round the rim, every one customizable,
values written along the arc (upright on the lower half), the seconds as a
thin line just inside it. Within: the date, the 24 h time under a mihrab arch
over the Buenos Aires mosque, the prayer row and where reading stopped, all
at 0.85 of the row face's size, with no panels behind them. At first the
segments show, from 12 o'clock: date (a tap opens the settings), battery,
steps, heart rate, surah and verse (a tap opens the mushaf), next prayer.

Eight slots: the six segments, the prayer row and the reading place. So the
always-on screen keeps this face's own time and prayer-row placement (with
the date, as everywhere), and there is no separate tap target on the time.
"""
import kit
from make_row_face import font, slot

T = 41                       # ring thickness: 40 px on the 438 px design
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
            f'direction="{direction}" align="CENTER">' + font(20, color) + inner + '</Font></TextCircular></PartText>')


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
                  f'<Arc centerX="225" centerY="165" width="230" height="150" startAngle="270" endAngle="450"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Arc>'
                  f'<Line startX="110" startY="165" endX="110" endY="205"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Line>'
                  f'<Line startX="340" startY="165" endX="340" endY="205"><Stroke color="{gold}" thickness="2.5" cap="ROUND"/></Line>'
                  '</PartDraw>')


def date_line():
    """The date under the top segment, drawn by the face (no slot is left for it); it
    stays on the always-on screen."""
    return kit.text(165, 64, 120, 30, ("%s/%s", ["[DAY]", "[MONTH]"]), 26, shadow=True)


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
    # Every segment can be changed by the wearer; these are what they show at first.
    p = [
        background(),
        arch(),
        inner_seconds(),
        kit.clock(y=91, size=94, height=98),            # the same on the always-on screen
        date_line(),
        ambient_outline(),
        segment(61, 0, "#d9b36a", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", "GregorianDateComplicationService", customizable="TRUE"),
        segment(62, 60, "#3fb58a", "RANGED_VALUE SHORT_TEXT", "RANGED_VALUE", system="WATCH_BATTERY", customizable="TRUE", meter=True),
        segment(63, 120, "#9b8cf0", "RANGED_VALUE SHORT_TEXT", "RANGED_VALUE", system="STEP_COUNT", customizable="TRUE", meter=True),
        segment(64, 180, "#e46a6a", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", system="HEART_RATE", customizable="TRUE"),
        segment(65, 240, "#d9b36a", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", "ContinueReadingComplicationService", customizable="TRUE"),
        segment(66, 300, "#5aa9e6", "SHORT_TEXT RANGED_VALUE", "SHORT_TEXT", "NextPrayerComplicationService",
                customizable="TRUE", body=("%s %s", ["[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"])),
        # One prayer row for both screens: eight slots leave no room for a second one at the
        # shared always-on place, so this face's always-on prayer row sits where it is here.
        kit.prayer_row(67, box=(88, 216, 275, 86)),
        kit.reading_slot(68, 110, 304, 230, 54),
    ]
    kit.write("watchface-mihrab", "The mihrab face", "DIGITAL", p)


if __name__ == "__main__":
    main()
