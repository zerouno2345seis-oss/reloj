#!/usr/bin/env python3
"""Writes watchface/src/main/res/raw/watchface.xml: the classic face.

zaher's design from the face editor (2026-10-23), scaled from 438 to 450 px:
the Buenos Aires mosque behind, a thin battery arc low on the right and a
temperature arc low on the left (dark fills, white values along the arc),
the seconds as a ring inside them, the mushaf and bookmark icons inside the
ring at mid height, and, from the top, the date (a tap opens the settings),
where reading stopped, a large time (a tap opens the mushaf) and the prayer
row. The always-on screen is the shared one: time, date, prayer row.
"""
import kit
from make_row_face import font, slot

T = 23                                  # 22 px on the 438 px design
MID = 225 - T / 2
TEXT_D = 2 * MID - 4
SECONDS_D = 2 * (225 - T - 4)


def _span(centre, span):
    return centre - span / 2, centre + span / 2


def _arc(start, end, color):
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{2 * MID:.0f}" height="{2 * MID:.0f}" '
            f'startAngle="{start}" endAngle="{end}"><Stroke color="{color}" thickness="{T}" cap="BUTT"/></Arc></PartDraw>')


def _text(start, end, body, alpha=None):
    tpl, params = body if isinstance(body, tuple) else (body, [])
    assert tpl.count("%s") == len(params), tpl
    inner = ("<Template>" + tpl + "".join(f'<Parameter expression="{x}"/>' for x in params) + "</Template>") if params else tpl
    bottom = 90 < (start + end) / 2 % 360 < 270
    a, b, direction = (end, start, "COUNTER_CLOCKWISE") if bottom else (start, end, "CLOCKWISE")
    tr = f'<Transform target="alpha" value="{alpha}"/>' if alpha else ""
    return (f'<PartText x="0" y="0" width="450" height="450">{tr}'
            f'<TextCircular centerX="225" centerY="225" width="{TEXT_D:.0f}" height="{TEXT_D:.0f}" startAngle="{a}" endAngle="{b}" '
            f'direction="{direction}" align="CENTER">' + font(15, "#FFFFFFFF") + inner + '</Font></TextCircular></PartText>')


def _active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450">{kit.HIDE_IN_AMBIENT}{inner}</Group>'


def seconds():
    d = SECONDS_D
    return _active("seconds",
                   f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="360"><Stroke color="#FF3A2F12" thickness="3"/></Arc></PartDraw>'
                   f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="0">'
                   '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FFE8BC3E" thickness="3" cap="ROUND"/></Arc></PartDraw>')


def temperature(centre=243, span=41, color="#FF331d05"):
    """The watch's own weather (no slot can carry it), low on the left."""
    a, b = _span(centre, span)
    return _active("temperature", _arc(a, b, color)
                   + _text(a, b, ("%s°", ["[WEATHER.TEMPERATURE]"]), alpha="(255 * [WEATHER.IS_AVAILABLE])")
                   + _text(a, b, "--°", alpha="(255 * (1 - [WEATHER.IS_AVAILABLE]))"))


def battery(sid, centre=113, span=40, color="#FF003320"):
    a, b = _span(centre, span)
    body = "".join(f'<Complication type="{t}">' + _active(f"battery{t}", _arc(a, b, color) + _text(a, b, ("%s", ["[COMPLICATION.TEXT]"])))
                   + '</Complication>' for t in ("RANGED_VALUE", "SHORT_TEXT"))
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{a}" endAngle="{b}" thickness="{T}"/>'
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE", bound, body,
                system="WATCH_BATTERY", customizable="FALSE", ambient=False)


def icon_button(sid, x, y, provider):
    """An icon inside the ring, its own small tap target."""
    body = ('<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="34" height="34">'
            '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
    return slot(sid, x, y, 34, 34, "SMALL_IMAGE", provider, "SMALL_IMAGE",
                '<BoundingBox x="0" y="0" width="34" height="34"/>', body, customizable="FALSE")


def background():
    return _active("background", '<PartImage x="0" y="0" width="450" height="450"><Image resource="bg_photo"/></PartImage>')


def main():
    p = [
        background(),
        seconds(),
        temperature(),
        kit.clock(y=152, size=126, when="active", height=130),   # centre y 211 on the design, ×1.15
        kit.ambient_clock(),
        kit.ambient_ring(),
        battery(33),
        # the mushaf on the right, the bookmarks on the left, at mid height inside the ring
        icon_button(31, 384, 215, "QuranShortcutComplicationService"),
        icon_button(32, 32, 215, "BookmarksShortcutComplicationService"),
        kit.date_slot(34, y=34),
        kit.reading_slot(35, 80, 95, 290, 70, fill="#B81A1608", edge="#59E8BC3E"),
        kit.time_tap(36, 70, 160, 310, 112),
        # The design's row (centre y 334, ×0.85) narrowed and raised a little to stay inside the seconds ring.
        kit.prayer_row(37, box=(95, 284, 260, 86), when="active"),
        kit.prayer_row(38, when="ambient"),
    ]
    kit.write("watchface", "The classic face", "DIGITAL", p)


if __name__ == "__main__":
    main()
