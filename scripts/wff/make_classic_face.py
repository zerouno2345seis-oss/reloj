#!/usr/bin/env python3
"""Writes watchface/src/main/res/raw/watchface.xml: the classic face.

The row face's layout on plain black with a gold accent: the date and a gear
at the top, where reading stopped, a large time, the prayer row, and the
battery and temperature as arcs on the lower rim. Shared parts in kit.py.
"""
import kit

GOLD_RING, GOLD_HEAD = "#FF3A2F12", "#FFE8BC3E"


def main():
    p = [
        kit.seconds_ring(GOLD_RING, GOLD_HEAD),
        kit.clock(),                                  # the same place as on the always-on screen
        kit.temperature_arc(186, 222),
        kit.ambient_ring(),
        kit.edge_buttons(31, 32),
        kit.battery_arc(33, 138, 174),
        kit.date_slot(34),
        kit.gear_slot(37),
        kit.time_tap(38, 60, 146, 330, 106),
        kit.reading_slot(35, fill="#B81A1608", edge="#59E8BC3E"),
        kit.prayer_row(36),
    ]
    kit.write("watchface", "The classic face", "DIGITAL", p)


if __name__ == "__main__":
    main()
