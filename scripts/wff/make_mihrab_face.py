#!/usr/bin/env python3
"""Writes watchface-mihrab/src/main/res/raw/watchface.xml: the mihrab face.

The time stands inside a mihrab arch; the battery and temperature are arcs on
the upper rim, either side of the date; the prayer row sits under the arch and
where reading stopped is a pill at the bottom. Shared parts in kit.py.
"""
import kit

ARCH = "#FFE8BC3E"


def arch():
    """A pointed-top niche: two uprights and a half ellipse over them."""
    return ('<PartDraw x="0" y="0" width="450" height="450">' + kit.HIDE_IN_AMBIENT +
            f'<Arc centerX="225" centerY="168" width="250" height="196" startAngle="270" endAngle="450"><Stroke color="{ARCH}" thickness="2.5" cap="ROUND"/></Arc>'
            f'<Line startX="100" startY="168" endX="100" endY="262"><Stroke color="{ARCH}" thickness="2.5" cap="ROUND"/></Line>'
            f'<Line startX="350" startY="168" endX="350" endY="262"><Stroke color="{ARCH}" thickness="2.5" cap="ROUND"/></Line>'
            '</PartDraw>')


def main():
    p = [
        arch(),
        kit.clock(y=150, size=100, when="active", height=104),
        kit.ambient_clock(),
        kit.temperature_arc(298, 334, bottom=False),
        kit.ambient_ring(),
        kit.edge_buttons(21, 22),
        kit.battery_arc(23, 26, 62, bottom=False),
        kit.date_slot(24),
        kit.time_tap(25, 104, 150, 242, 104),
        kit.reading_pill(26, 135, 376, 180, 40),
        kit.prayer_row(27),
    ]
    kit.write("watchface-mihrab", "The mihrab face", "DIGITAL", p)


if __name__ == "__main__":
    main()
