#!/usr/bin/env python3
"""Writes watchface-hands/src/main/res/raw/watchface.xml: the face with hands.

zaher's design from the face editor (2026-10-23), scaled from 438 to 450 px:
the old mosques behind; a thin decorative ring of fourteen teal dashes at the
rim with the seconds running just inside it; minute ticks and all twelve hour
numerals (3 and 9 set inward, clear of the mushaf and bookmark icons at mid
height); a small reading pill above the centre, the date below it (a tap opens
the settings) and the prayer row lower down. No battery or temperature here.
The hands pass under the panels, so they never hide the text. The always-on
screen is the shared one: digital time, date and prayer row.
"""
import kit


def ticks():
    """Sixty marks; the hours longer, the quarters gold. Drawn as rotated groups."""
    out = []
    for i in range(60):
        if i in (0, 15, 30, 45):
            length, w, color = 18, 4, "#FFE8BC3E"
        elif i % 5 == 0:
            length, w, color = 14, 3, "#FFB8C4BE"
        else:
            length, w, color = 6, 1.5, "#FF5B6B64"
        out.append(f'<Group name="tick{i}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5" angle="{i * 6}">'
                   f'<PartDraw x="0" y="0" width="450" height="450"><Line startX="225" startY="18" endX="225" endY="{18 + length}">'
                   f'<Stroke color="{color}" thickness="{w}" cap="ROUND"/></Line></PartDraw></Group>')
    return ('<Group name="dial" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT + "".join(out) + '</Group>')


def numerals():
    """All twelve hours inside the ticks; 3 and 9 set inward, clear of the icons there."""
    import math
    out = []
    for h in range(1, 13):
        r = 150 if h in (3, 9) else 176
        a = math.radians(h * 30)
        cx, cy = 225 + r * math.sin(a), 225 - r * math.cos(a)
        color = "#FFE8BC3E" if h == 12 else "#FFD3DAD6"
        out.append(kit.text(round(cx - 16), round(cy - 12), 32, 24, str(h), 16, color))
    return '<Group name="numerals" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT + "".join(out) + '</Group>'


def dashed_ring():
    """Fourteen teal dashes round the rim, 3° apart; decoration only."""
    step = 360 / 14
    arcs = "".join(f'<Arc centerX="225" centerY="225" width="440" height="440" startAngle="{i * step - step / 2 + 1.5:.2f}" '
                   f'endAngle="{i * step + step / 2 - 1.5:.2f}"><Stroke color="#D97fc3b0" thickness="10" cap="BUTT"/></Arc>'
                   for i in range(14))
    return ('<Group name="ring" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT
            + f'<PartDraw x="0" y="0" width="450" height="450">{arcs}</PartDraw></Group>')


def seconds():
    """The seconds as a thin line just inside the ring."""
    d = 422
    return ('<Group name="seconds" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT
            + f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="0">'
            + '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FFE8BC3E" thickness="2" cap="ROUND"/></Arc></PartDraw></Group>')


def icon_button(sid, x, y, provider, size=36):
    body = (f'<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="{size}" height="{size}">'
            '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
    return kit.slot(sid, x, y, size, size, "SMALL_IMAGE", provider, "SMALL_IMAGE",
                    f'<BoundingBox x="0" y="0" width="{size}" height="{size}"/>', body, customizable="FALSE")


def background():
    return ('<Group name="background" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT
            + '<PartImage x="0" y="0" width="450" height="450"><Image resource="bg_mosques"/></PartImage></Group>')


def hands():
    """Hour and minute hands, short enough to stay in the clear middle."""
    def hand(name, length, width, color, angle):
        return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">'
                f'<Transform target="angle" value="{angle}"/>'
                f'<PartDraw x="0" y="0" width="450" height="450"><Line startX="225" startY="225" endX="225" endY="{225 - length}">'
                f'<Stroke color="{color}" thickness="{width}" cap="ROUND"/></Line></PartDraw></Group>')
    return ('<Group name="hands" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT
            + hand("hour", 70, 9, "#FFFFFFFF", "([HOUR_0_23] % 12) * 30 + [MINUTE] * 0.5")
            + hand("minute", 112, 6, "#FFE8EEEA", "[MINUTE] * 6 + [SECOND] * 0.1")
            + '<PartDraw x="0" y="0" width="450" height="450"><Ellipse x="216" y="216" width="18" height="18"><Fill color="#FFE8BC3E"/></Ellipse>'
            + '<Ellipse x="221" y="221" width="8" height="8"><Fill color="#FF000000"/></Ellipse></PartDraw></Group>')


def main():
    p = [
        background(),
        dashed_ring(),
        seconds(),
        ticks(),
        numerals(),
        hands(),                       # before the panels, which are drawn over them
        kit.ambient_clock(),
        kit.ambient_ring(),
        icon_button(41, 397, 210, "QuranShortcutComplicationService"),
        icon_button(42, 17, 210, "BookmarksShortcutComplicationService"),
        kit.reading_slot(46, 147, 74, 156, 35),
        kit.time_tap(45, 175, 145, 100, 80),           # the centre, apart from the date below it
        kit.date_slot(44, y=237, when="active"),
        kit.date_slot(43, when="ambient"),
        kit.prayer_row(47, box=(104, 282, 242, 76), when="active"),
        kit.prayer_row(48, when="ambient"),
    ]
    kit.write("watchface-hands", "The face with hands", "ANALOG", p, preview="10:08:32")


if __name__ == "__main__":
    main()
