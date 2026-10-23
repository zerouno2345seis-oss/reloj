#!/usr/bin/env python3
"""Writes watchface-hands/src/main/res/raw/watchface.xml: the face with hands.

Short hands in a clear middle; the panels above and below are drawn over them,
so the hands pass under the text and never hide it. Ticks round the rim, the
battery and temperature as arcs low on the rim. The always-on screen is the
row face's: digital time, date and the prayer row. Shared parts in kit.py.
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
                   f'<PartDraw x="0" y="0" width="450" height="450"><Line startX="225" startY="6" endX="225" endY="{6 + length}">'
                   f'<Stroke color="{color}" thickness="{w}" cap="ROUND"/></Line></PartDraw></Group>')
    return ('<Group name="dial" x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT + "".join(out) + '</Group>')


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
            + hand("second", 118, 2, "#FFE8BC3E", "[SECOND] * 6")
            + '<PartDraw x="0" y="0" width="450" height="450"><Ellipse x="216" y="216" width="18" height="18"><Fill color="#FFE8BC3E"/></Ellipse>'
            + '<Ellipse x="221" y="221" width="8" height="8"><Fill color="#FF000000"/></Ellipse></PartDraw></Group>')


def main():
    p = [
        ticks(),
        hands(),                       # first: the panels below are drawn over them
        kit.ambient_clock(),
        kit.temperature_arc(182, 210),
        kit.ambient_ring(),
        kit.edge_buttons(41, 42),
        kit.battery_arc(43, 150, 178),
        kit.date_slot(44),
        kit.time_tap(45, 150, 150, 150, 110),
        kit.reading_slot(46),
        kit.prayer_row(47),
    ]
    kit.write("watchface-hands", "The face with hands", "ANALOG", p, preview="10:08:32")


if __name__ == "__main__":
    main()
