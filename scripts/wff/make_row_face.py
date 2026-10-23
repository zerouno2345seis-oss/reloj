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


# ── the rim ring (zaher's design, 2026-10-23): four quarters, 87° each ────────
RING_T = 32                       # 31 px on the 438 px design
RING_MID = 225 - RING_T / 2
RING_TEXT_D = 2 * RING_MID - 4
INNER = 186                       # everything else stays inside this radius


def _ring_arc(start, end, color):
    d = 2 * RING_MID
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
            f'startAngle="{start}" endAngle="{end}"><Stroke color="{color}" thickness="{RING_T}" cap="BUTT"/></Arc></PartDraw>')


def _ring_level(start, end, color, fraction):
    d = 2 * RING_MID
    return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
            f'startAngle="{start}" endAngle="{start}"><Transform target="endAngle" value="({start} + ({end} - {start}) * clamp({fraction}, 0, 1))"/>'
            f'<Stroke color="{color}" thickness="{RING_T}" cap="BUTT"/></Arc></PartDraw>')


def _ring_text(start, end, body, color, alpha=None):
    tpl, params = body if isinstance(body, tuple) else (body, [])
    assert tpl.count("%s") == len(params), tpl
    inner = ("<Template>" + tpl + "".join(f'<Parameter expression="{x}"/>' for x in params) + "</Template>") if params else tpl
    centre = (start + end) / 2 % 360
    bottom = 90 < centre < 270
    a, b, direction = (end, start, "COUNTER_CLOCKWISE") if bottom else (start, end, "CLOCKWISE")
    tr = f'<Transform target="alpha" value="{alpha}"/>' if alpha else ""
    return (f'<PartText x="0" y="0" width="450" height="450">{tr}'
            f'<TextCircular centerX="225" centerY="225" width="{RING_TEXT_D:.0f}" height="{RING_TEXT_D:.0f}" startAngle="{a}" endAngle="{b}" '
            f'direction="{direction}" align="CENTER">' + font(19, color) + inner + '</Font></TextCircular></PartText>')


def _quarter(centre):
    return centre - 43.5 + (360 if centre - 43.5 < 0 else 0), centre + 43.5 + (360 if centre - 43.5 < 0 else 0)


def _active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/>{inner}</Group>'


def icon_quarter(sid, centre, color, provider, icon_x, icon_y):
    """A quarter of the ring that is a button: solid colour, the app's icon in it."""
    start, end = _quarter(centre)
    body = (f'<Complication type="SMALL_IMAGE">' + _active(f"q{sid}", _ring_arc(start, end, f"#FF{color}")
            + f'<PartImage x="{icon_x}" y="{icon_y}" width="26" height="26"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage>')
            + '</Complication>')
    bound = f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{start}" endAngle="{end}" thickness="{RING_T}"/>'
    return slot(sid, 0, 0, 450, 450, "SMALL_IMAGE", provider, "SMALL_IMAGE", bound, body, customizable="FALSE", ambient=False)


def main():
    p = []
    # ── background, chosen in the face's settings ──
    p.append('<ListConfiguration id="bg">'
             '<ListOption id="photo"><PartImage x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/><Image resource="bg_photo"/></PartImage></ListOption>'
             '<ListOption id="mosques"><PartImage x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/><Image resource="bg_mosques"/></PartImage></ListOption>'
             '<ListOption id="pattern"><PartImage x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/><Image resource="bg_pattern"/></PartImage></ListOption>'
             '<ListOption id="plain"><PartDraw x="0" y="0" width="450" height="450"><Rectangle x="0" y="0" width="450" height="450"><Fill color="#FF000000"/></Rectangle></PartDraw></ListOption>'
             '</ListConfiguration>')

    # ── seconds: a thin ring just inside the rim ring ──
    d = 2 * (INNER + 4)
    p.append(_active("seconds",
             f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="360"><Stroke color="#FF123A30" thickness="3"/></Arc></PartDraw>'
             f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d}" height="{d}" startAngle="0" endAngle="0">'
             '<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="#FF43D49A" thickness="3" cap="ROUND"/></Arc></PartDraw>'))

    # ── temperature, the right quarter: a meter from -5° to 40°, the value along the arc (the watch's own weather, not a slot) ──
    t0, t1 = _quarter(90)
    level = "(([WEATHER.TEMPERATURE] + 5) / 45)"
    p.append(_active("temperature", _ring_arc(t0, t1, "#55d9b36a")
             + _ring_level(t0, t1, "#FFd9b36a", f"({level} * [WEATHER.IS_AVAILABLE])")
             + _ring_text(t0, t1, ("%s°", ["[WEATHER.TEMPERATURE]"]), "#FF1A1408", alpha="(255 * [WEATHER.IS_AVAILABLE])")
             + _ring_text(t0, t1, "--°", "#FF1A1408", alpha="(255 * (1 - [WEATHER.IS_AVAILABLE]))")))

    # ── the time, larger; on the always-on screen it moves to the row face's usual place ──
    import kit
    p.append(kit.clock(y=140, size=132, when="active", height=136))
    p.append(kit.ambient_clock())

    # ── slots ──
    # 1. bookmarks, the top quarter; 4. the mushaf, the left quarter
    p.append(icon_quarter(1, 0, "d9b36a", "BookmarksShortcutComplicationService", 212, 3))
    p.append(icon_quarter(2, 270, "3fb58a", "QuranShortcutComplicationService", 3, 212))
    # 3. the battery, the bottom quarter: a meter, the level along the arc
    b0, b1 = _quarter(180)
    frac = "(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]))"
    battery = (f'<Complication type="RANGED_VALUE">' + _active("battery", _ring_arc(b0, b1, "#553fb58a") + _ring_level(b0, b1, "#FF3fb58a", frac)
               + _ring_text(b0, b1, ("%s", ["[COMPLICATION.TEXT]"]), "#FFFFFFFF")) + '</Complication>'
               f'<Complication type="SHORT_TEXT">' + _active("batteryText", _ring_arc(b0, b1, "#FF3fb58a")
               + _ring_text(b0, b1, ("%s", ["[COMPLICATION.TEXT]"]), "#FF1A1408")) + '</Complication>')
    p.append(slot(3, 0, 0, 450, 450, "RANGED_VALUE SHORT_TEXT", None, "RANGED_VALUE",
                  f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{b0}" endAngle="{b1}" thickness="{RING_T}"/>',
                  battery, system="WATCH_BATTERY", customizable="FALSE", ambient=False))
    # 4. the date, top; a tap on it opens the settings. It stays on the always-on screen.
    p.append(slot(4, 165, 48, 120, 38, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                  '<BoundingBox x="0" y="0" width="120" height="38"/>',
                  '<Complication type="SHORT_TEXT">' + text(0, 0, 120, 38, ("%s", ["[COMPLICATION.TEXT]"]), 31) + '</Complication>',
                  ambient=False))
    # 5. last reading position
    p.append(kit.reading_slot(5, 95, 92, 260, 63))
    # 6. the time itself: a tap opens the mushaf where reading stopped
    p.append(kit.time_tap(6, 60, 158, 330, 98))
    # 7. the prayer row, a little smaller, and 8. the same row at the usual place for the always-on screen
    p.append(kit.prayer_row(7, box=(90, 263, 270, 91), when="active"))
    p.append(kit.prayer_row(8, when="ambient"))

    # ── always-on: the time, the date and the prayer row, as on every face; the ring as a dim line ──
    p.append('<Group name="ambientRing" x="0" y="0" width="450" height="450" alpha="0"><Variant mode="AMBIENT" target="alpha" value="255"/>'
             '<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="434" height="434" startAngle="0" endAngle="360"><Stroke color="#FF2C3A35" thickness="1.5"/></Arc></PartDraw></Group>')

    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Design 1, "one row". Generated by scripts/wff/make_row_face.py: edit that, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           '<Metadata key="CLOCK_TYPE" value="DIGITAL"/>\n<Metadata key="PREVIEW_TIME" value="16:40:24"/>\n'
           '<UserConfigurations><ListConfiguration id="bg" displayName="bg_label" defaultValue="mosques">'
           '<ListOption id="photo" displayName="bg_photo_label"/><ListOption id="mosques" displayName="bg_mosques_label"/><ListOption id="pattern" displayName="bg_pattern_label"/>'
           '<ListOption id="plain" displayName="bg_plain_label"/></ListConfiguration></UserConfigurations>\n'
           '<Scene backgroundColor="#FF000000">\n' + "\n".join(p) + '\n</Scene>\n</WatchFace>\n')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w").write(xml)
    slots = xml.count("<ComplicationSlot ")
    assert slots <= 8, slots
    print(f"wrote {OUT} ({slots} slots)")


if __name__ == "__main__":
    main()
