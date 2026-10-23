"""Parts shared by the faces, so they read alike: the same prayer row, the same
always-on screen, the same edge buttons and arcs as the row face (design 1).

Every position is in the 450 px Watch Face Format square. Each face script
picks the parts it wants and adds its own look around them.
"""
import os

from make_row_face import APP, BOLD, GOLD, SEMI, SOFT, WHITE, duration, font, slot, text

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HIDE_IN_AMBIENT = '<Variant mode="AMBIENT" target="alpha" value="0"/>'


def seconds_ring(track="#FF123A30", head="#FF43D49A"):
    """A thin ring round the edge that fills once a minute."""
    return ('<PartDraw x="0" y="0" width="450" height="450">' + HIDE_IN_AMBIENT +
            f'<Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="360"><Stroke color="{track}" thickness="3"/></Arc></PartDraw>'
            '<PartDraw x="0" y="0" width="450" height="450">' + HIDE_IN_AMBIENT +
            '<Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="0">'
            f'<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="{head}" thickness="3" cap="ROUND"/></Arc></PartDraw>')


def clock(y=142, size=110, when="always", color=WHITE, height=114):
    """The digital time, 24 h. `when`: "always", "active" (hidden in always-on) or "ambient" (only there)."""
    body = (f'<DigitalClock x="0" y="{y}" width="450" height="{height}"><TimeText format="hh:mm" hourFormat="24" align="CENTER" '
            f'x="0" y="0" width="450" height="{height}">' + font(size, color) + '</Font></TimeText></DigitalClock>')
    if when == "always":
        return body
    alpha, amb = ("255", "0") if when == "active" else ("0", "255")
    return (f'<Group name="clock_{when}" x="0" y="0" width="450" height="450" alpha="{alpha}">'
            f'<Variant mode="AMBIENT" target="alpha" value="{amb}"/>{body}</Group>')


def ambient_clock():
    """The always-on screen's time: where and how the row face draws it."""
    return clock(when="ambient")


def ambient_ring():
    return ('<Group name="ambientRing" x="0" y="0" width="450" height="450" alpha="0"><Variant mode="AMBIENT" target="alpha" value="255"/>'
            '<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="360">'
            '<Stroke color="#FF2C3A35" thickness="1.5"/></Arc></PartDraw></Group>')


def _icon(x, y, s):
    return (f'<Complication type="SMALL_IMAGE"><PartImage x="{x}" y="{y}" width="{s}" height="{s}">'
            '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')


def edge_buttons(quran_id, bookmarks_id):
    """The mushaf on the right edge and the bookmarks on the left: see-through bands, the icon alone."""
    arc_r = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="45" endAngle="135" thickness="44"/>'
    arc_l = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="225" endAngle="315" thickness="44"/>'
    # A BoundingArc is read from the slot's own corner: the slots span the face.
    return (slot(quran_id, 0, 0, 450, 450, "SMALL_IMAGE", "QuranShortcutComplicationService", "SMALL_IMAGE", arc_r, _icon(412, 208, 34), customizable="FALSE")
            + slot(bookmarks_id, 0, 0, 450, 450, "SMALL_IMAGE", "BookmarksShortcutComplicationService", "SMALL_IMAGE", arc_l, _icon(4, 208, 34), customizable="FALSE"))


def date_slot(sid, y=29, size=30):
    """The date at the top; it stays on the always-on screen."""
    return slot(sid, 150, y, 150, 36, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                '<BoundingBox x="0" y="0" width="150" height="36"/>',
                '<Complication type="SHORT_TEXT">' + text(0, 0, 150, 36, ("%s", ["[COMPLICATION.TEXT]"]), size) + '</Complication>',
                ambient=False)


def time_tap(sid, x, y, w, h):
    """Over the time: a tap opens the mushaf where reading stopped. Draws nothing."""
    return slot(sid, x, y, w, h, "SMALL_IMAGE", "QuranShortcutComplicationService", "SMALL_IMAGE",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>',
                '<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="1" height="1" alpha="0">'
                '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>',
                customizable="FALSE")


def reading_slot(sid, x=70, y=68, w=310, h=70, fill="#B80A201C", edge="#59AADCC8"):
    """Where reading stopped, and how the verse begins."""
    body = ('<Complication type="LONG_TEXT">'
            f'<PartDraw x="0" y="0" width="{w}" height="{h}"><RoundRectangle x="0" y="0" width="{w}" height="{h}" cornerRadiusX="22" cornerRadiusY="22">'
            f'<Fill color="{fill}"/><Stroke color="{edge}" thickness="1"/></RoundRectangle></PartDraw>'
            + text(12, 4, w - 24, 24, ("%s", ["[COMPLICATION.TITLE]"]), 16, SOFT, SEMI)
            + text(12, 27, w - 24, h - 32, ("%s", ["[COMPLICATION.TEXT]"]), 23) + '</Complication>')
    return slot(sid, x, y, w, h, "LONG_TEXT", "ContinueReadingComplicationService", "LONG_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body)


def reading_pill(sid, x, y, w, h, fill="#E60F5A45"):
    """A short form for tight faces: just the place."""
    body = ('<Complication type="SHORT_TEXT">'
            f'<PartDraw x="0" y="0" width="{w}" height="{h}"><RoundRectangle x="0" y="0" width="{w}" height="{h}" cornerRadiusX="{h // 2}" cornerRadiusY="{h // 2}">'
            f'<Fill color="{fill}"/></RoundRectangle></PartDraw>'
            + text(10, 0, w - 20, h, ("%s", ["[COMPLICATION.TEXT]"]), 20) + '</Complication>')
    return slot(sid, x, y, w, h, "SHORT_TEXT", "ContinueReadingComplicationService", "SHORT_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body)


PRAYER_BOX = (64, 269, 323, 101)


def prayer_row(sid, fill="#D104100D", edge="#47AADCC8", box=PRAYER_BOX, when="always"):
    """Passed (right) | the next prayer and its time (middle) | left (left), as on the row
    face and at the same place, so the always-on screen is the same on every face.

    `box` scales the row for a tighter face; `when` is "always", "active" (hidden on
    the always-on screen) or "ambient" (shown only there, at the row face's place)."""
    x, y, w, h = box
    k = h / PRAYER_BOX[3]
    sz = lambda v: round(v * k)
    V = "[COMPLICATION.RANGED_VALUE_VALUE]"
    now = "([HOUR_0_23] * 60 + [MINUTE])"
    nxt = f"({V} % 1440)"
    prev = f"floor({V} / 1440)"
    passed = f"(({now} - {prev} + 1440) % 1440)"
    left = f"(({nxt} - {now} + 1440) % 1440)"
    col = w / 3
    parts = (f'<PartDraw x="0" y="0" width="{w}" height="{h}">'
             f'<RoundRectangle x="0" y="0" width="{w}" height="{h}" cornerRadiusX="{sz(16)}" cornerRadiusY="{sz(16)}"><Fill color="{fill}"/><Stroke color="{edge}" thickness="1"/></RoundRectangle>'
             f'<Line startX="{col:.0f}" startY="{sz(14)}" endX="{col:.0f}" endY="{h - sz(14)}"><Stroke color="#38AADCC8" thickness="1"/></Line>'
             f'<Line startX="{2*col:.0f}" startY="{sz(14)}" endX="{2*col:.0f}" endY="{h - sz(14)}"><Stroke color="#38AADCC8" thickness="1"/></Line></PartDraw>'
             + text(int(2 * col), sz(14), int(col), sz(24), "مضى", sz(17), SOFT, SEMI)
             + duration(passed, int(2 * col), sz(42), int(col), sz(42), sz(26))
             + text(int(col), sz(10), int(col), sz(38), ("%s", ["[COMPLICATION.TEXT]"]), sz(26))
             + text(int(col), sz(52), int(col), sz(32), ("%s%s:%s%s", [f"floor({nxt} / 600)", f"(floor({nxt} / 60) % 10)", f"floor(({nxt} % 60) / 10)", f"({nxt} % 10)"]), sz(29))
             + text(0, sz(14), int(col), sz(24), "متبقي", sz(17), SOFT, SEMI)
             + duration(left, 0, sz(42), int(col), sz(42), sz(26)))
    if when == "ambient":
        parts = (f'<Group name="ambientPrayer" x="0" y="0" width="{w}" height="{h}" alpha="0">'
                 f'<Variant mode="AMBIENT" target="alpha" value="255"/>{parts}</Group>')
    body = f'<Complication type="RANGED_VALUE">{parts}</Complication>'
    return slot(sid, x, y, w, h, "RANGED_VALUE", "PrayerWindowComplicationService", "RANGED_VALUE",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body, customizable="FALSE", ambient=(when == "active"))


# ── the rim arcs: battery and temperature written along the circle ──────────
# Inside the seconds ring (diameter 434), so nothing is drawn over it.
RIM = 376           # diameter of the arc's middle line
RIM_THICK = 28


def _rim_text(start, end, body, size, color=WHITE, alpha=None, bottom=True):
    """Text along the rim between two angles (0 at 12 o'clock, clockwise).
    Below the middle it runs counter-clockwise so it reads upright."""
    tr = f'<Transform target="alpha" value="{alpha}"/>' if alpha else ""
    if isinstance(body, tuple):
        tpl, params = body
        assert tpl.count("%s") == len(params), tpl
        inner = "<Template>" + tpl + "".join(f'<Parameter expression="{p}"/>' for p in params) + "</Template>"
    else:
        inner = body
    a, b, direction = (end, start, "COUNTER_CLOCKWISE") if bottom else (start, end, "CLOCKWISE")
    return (f'<PartText x="0" y="0" width="450" height="450">{tr}'
            f'<TextCircular centerX="225" centerY="225" width="{RIM}" height="{RIM}" startAngle="{a}" endAngle="{b}" direction="{direction}" align="CENTER">'
            + font(size, color) + inner + '</Font></TextCircular></PartText>')


def _rim_track(start, end, color):
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{RIM}" height="{RIM}" '
            f'startAngle="{start}" endAngle="{end}"><Stroke color="{color}" thickness="{RIM_THICK}" cap="ROUND"/></Arc></PartDraw>')


def battery_arc(sid, start, end, bottom=True, track="#E6102420", fill="#FF2F8F67"):
    """The battery (or whatever is chosen there) as an arc on the rim, its level filling it."""
    bound = (f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{start}" endAngle="{end}" '
             f'thickness="{(450 - RIM) // 2 + RIM_THICK // 2}"/>')
    level = ('<PartDraw x="0" y="0" width="450" height="450">'
             f'<Arc centerX="225" centerY="225" width="{RIM}" height="{RIM}" startAngle="{start}" endAngle="{start}">'
             f'<Transform target="endAngle" value="({start} + ({end} - {start}) * clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1))"/>'
             f'<Stroke color="{fill}" thickness="{RIM_THICK}" cap="ROUND"/></Arc></PartDraw>')
    label = _rim_text(start, end, ("%s", ["[COMPLICATION.TEXT]"]), 20, bottom=bottom)
    body = (f'<Complication type="RANGED_VALUE">{_rim_track(start, end, track)}{level}{label}</Complication>'
            f'<Complication type="SHORT_TEXT">{_rim_track(start, end, track)}{label}</Complication>')
    return slot(sid, 0, 0, 450, 450, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE", bound, body, system="WATCH_BATTERY", customizable="FALSE")


def temperature_arc(start, end, bottom=True, track="#E6102420"):
    """The watch's own weather on the rim (not a slot: no weather source can fill one)."""
    return ('<Group name="temperatureArc" x="0" y="0" width="450" height="450">' + HIDE_IN_AMBIENT
            + _rim_track(start, end, track)
            + _rim_text(start, end, ("%s°", ["[WEATHER.TEMPERATURE]"]), 20, alpha="(255 * [WEATHER.IS_AVAILABLE])", bottom=bottom)
            + _rim_text(start, end, "--°", 20, SOFT, alpha="(255 * (1 - [WEATHER.IS_AVAILABLE]))", bottom=bottom)
            + '</Group>')


# The watch keeps what was once chosen for a face's slots, even across new
# slot ids (a prayer name came back on the battery arc), so the edge buttons
# and the battery arc are locked to what they show. Slot ids: classic 31-38,
# hands 41-47, mihrab 51-57; the row face 1-8.


def write(module, title, clock_type, parts, preview="16:40:24"):
    out = os.path.join(ROOT, module, "src/main/res/raw/watchface.xml")
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f'<!-- {title}. Generated by scripts/wff/: edit the script, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           f'<Metadata key="CLOCK_TYPE" value="{clock_type}"/>\n<Metadata key="PREVIEW_TIME" value="{preview}"/>\n'
           '<Scene backgroundColor="#FF000000">\n' + "\n".join(parts) + '\n</Scene>\n</WatchFace>\n')
    open(out, "w").write(xml)
    slots = xml.count("<ComplicationSlot ")
    assert slots <= 8, slots
    ids = [int(s.split('"')[0]) for s in xml.split('slotId="')[1:]]
    assert len(ids) == len(set(ids)), ids
    print(f"wrote {out} ({slots} slots)")
