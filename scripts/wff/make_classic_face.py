#!/usr/bin/env python3
"""Writes watchface/src/main/res/raw/watchface.xml: the classic face.

zaher's second editor design (2026-10-23), scaled from 438 to 450 px: the
Buenos Aires mosque behind, a thin ring of seven teal dashes at the rim with
the seconds just inside it, and from the top the date (a tap opens the
settings), where reading stopped, a large time (a tap opens the mushaf) and
the prayer row. No battery, temperature or edge icons on this face. The
always-on screen is the shared one: time, date, prayer row.
"""
import kit

T = 23                                  # 22 px on the 438 px design
MID = 225 - T / 2
SECONDS_D = 2 * (225 - T - 4)


def _active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450">{kit.HIDE_IN_AMBIENT}{inner}</Group>'


def seconds():
    d = SECONDS_D
    return _active("seconds",
                   f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="360"><Stroke color="#FF3A2F12" thickness="3"/></Arc></PartDraw>'
                   f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="0">'
                   '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FFE8BC3E" thickness="3" cap="ROUND"/></Arc></PartDraw>')


def background():
    return _active("background", '<PartImage x="0" y="0" width="450" height="450"><Image resource="bg_photo"/></PartImage>')


# zaher, 2026-11-07 (second round): the ring's colours change in the watch's face
# editor by turning the bezel, as with any colour setting; two shades per option,
# the first three dashes the darker one.
RINGS = [("teal", "فيروزي", "#D96ca595", "#D97fc3b0"), ("blue", "أزرق", "#D9406f9e", "#D95aa9e6"),
         ("green", "أخضر", "#D9338a5e", "#D94fc488"), ("violet", "بنفسجي", "#D97a62b8", "#D9a991f0"),
         ("coral", "مرجاني", "#D9b25a4f", "#D9ef7a6b"), ("silver", "فضي", "#D9707a76", "#D9b8c2bd")]
RING_DARK, RING_LIGHT = "[CONFIGURATION.ring.0]", "[CONFIGURATION.ring.1]"
# The hands are not drawn: a glowing mark on the ring stands for each, the hour
# white, the minute gold, the seconds red.
HOUR, MINUTE, SECOND = "#FFFFFFFF", "#FFE8BC3E", "#FFFF5A4E"


def config():
    opts = "".join(f'<ColorOption id="{i}" displayName="ring_{i}" colors="{a} {b}"/>' for i, _, a, b in RINGS)
    return (f'<UserConfigurations><ColorConfiguration id="ring" displayName="ring_label" defaultValue="{RINGS[0][0]}">'
            f'{opts}</ColorConfiguration></UserConfigurations>\n')


def write_strings():
    lines = ['    <string name="face_name">القرآن الكريم · الحلقة</string>', '    <string name="ring_label">لون الحلقة</string>']
    lines += [f'    <string name="ring_{i}">{n}</string>' for i, n, _, _ in RINGS]
    open(kit.os.path.join(kit.ROOT, "watchface/src/main/res/values/strings.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(lines) + "\n</resources>\n")


def decorative_ring():
    """Seven dashes round the rim, 3° apart (the first three a shade darker); decoration only."""
    step = 360 / 7
    arcs = "".join(f'<Arc centerX="225" centerY="225" width="{2 * MID:.0f}" height="{2 * MID:.0f}" '
                   f'startAngle="{i * step - step / 2 + 1.5:.2f}" endAngle="{i * step + step / 2 - 1.5:.2f}">'
                   f'<Stroke color="{RING_DARK if i < 3 else RING_LIGHT}" thickness="{T}" cap="BUTT"/></Arc>'
                   for i in range(7))
    return _active("ring", f'<PartDraw x="0" y="0" width="450" height="450">{arcs}</PartDraw>')


def main():
    p = [
        background(),
        decorative_ring(),
        seconds(),
        kit.lasers(r_in=176, r_out=222, hour=HOUR, minute=MINUTE, second=SECOND),
        kit.clock(y=152, size=126, when="active", height=130),   # centre y 211 on the design, ×1.15
        kit.date_slot(34, y=49),
        kit.reading_slot(35, 80, 95, 290, 70),
        # zaher, 2026-11-07: the hours open the surah list, the minutes open it at al-Buruj.
        kit.digit_taps(40, 60, 160, 330, 112),
        # The design's row (centre y 334, ×0.85) narrowed and raised a little to stay inside the seconds ring.
        kit.prayer_row(37, box=(100, 292, 250, 82), when="active"),
    ]
    write_strings()
    kit.write("watchface", "The classic face", "DIGITAL", p, config=config())


if __name__ == "__main__":
    main()
