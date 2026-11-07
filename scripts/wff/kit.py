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


def edge_buttons(quran_id, bookmarks_id, icon_y=208):
    """The mushaf on the right edge and the bookmarks on the left: see-through bands, the icon alone."""
    arc_r = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="45" endAngle="135" thickness="44"/>'
    arc_l = '<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="225" endAngle="315" thickness="44"/>'
    # A BoundingArc is read from the slot's own corner: the slots span the face.
    return (slot(quran_id, 0, 0, 450, 450, "SMALL_IMAGE", "QuranShortcutComplicationService", "SMALL_IMAGE", arc_r, _icon(412, icon_y, 34), customizable="FALSE")
            + slot(bookmarks_id, 0, 0, 450, 450, "SMALL_IMAGE", "BookmarksShortcutComplicationService", "SMALL_IMAGE", arc_l, _icon(4, icon_y, 34), customizable="FALSE"))


def date_slot(sid, y=29, size=30, when="always", x=150, w=150, h=36, ink=WHITE):
    """The date; a tap on it opens the settings. "always" keeps it on the always-on screen;
    "active" hides it there; "ambient" shows it only there, at the shared top place."""
    inner = text(0, 0, w, h, ("%s", ["[COMPLICATION.TEXT]"]), size, ink)
    if when == "ambient":
        inner = (f'<Group name="ambientDate" x="0" y="0" width="{w}" height="{h}" alpha="0">'
                 f'<Variant mode="AMBIENT" target="alpha" value="255"/>{inner}</Group>')
    return slot(sid, x, y, w, h, "SHORT_TEXT", "GregorianDateComplicationService", "SHORT_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>',
                '<Complication type="SHORT_TEXT">' + inner + '</Complication>',
                ambient=(when == "active"))


def time_tap(sid, x, y, w, h):
    """Over the time: a tap opens the mushaf where reading stopped. Draws nothing."""
    return slot(sid, x, y, w, h, "SMALL_IMAGE", "QuranShortcutComplicationService", "SMALL_IMAGE",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>',
                '<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="1" height="1" alpha="0">'
                '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>',
                customizable="FALSE")


def reading_slot(sid, x=70, y=68, w=310, h=70, ink=WHITE, soft=SOFT):
    """Where reading stopped, and how the verse begins, straight on the face (no panel,
    a shadow under the text). Text scales with the height, but never below 12 px."""
    k = h / 70
    title, verse = max(12, round(16 * k)), max(12, round(23 * k))
    th = round(h * 24 / 70)
    body = ('<Complication type="LONG_TEXT">'
            + text(round(12 * k), round(4 * k), w - round(24 * k), th, ("%s", ["[COMPLICATION.TITLE]"]), title, soft, SEMI, shadow=True)
            + text(round(12 * k), round(27 * k), w - round(24 * k), h - round(32 * k), ("%s", ["[COMPLICATION.TEXT]"]), verse, ink, shadow=True) + '</Complication>')
    return slot(sid, x, y, w, h, "LONG_TEXT", "ContinueReadingComplicationService", "LONG_TEXT",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body)


PRAYER_BOX = (64, 269, 323, 101)


def prayer_row(sid, box=PRAYER_BOX, when="always", ink=WHITE, soft=SOFT):
    """Passed (right) | the next prayer and its time (middle) | left (left), as on the row
    face and at the same place, so the always-on screen is the same on every face.
    No panel behind it: the text sits on the face, lifted by a shadow.

    `box` scales the row for a tighter face; `when` is "always", "active" (hidden on
    the always-on screen) or "ambient" (shown only there, at the row face's place)."""
    x, y, w, h = box
    parts = prayer_parts(w, h, ink, soft)
    if when == "ambient":
        parts = (f'<Group name="ambientPrayer" x="0" y="0" width="{w}" height="{h}" alpha="0">'
                 f'<Variant mode="AMBIENT" target="alpha" value="255"/>{parts}</Group>')
    body = f'<Complication type="RANGED_VALUE">{parts}</Complication>'
    return slot(sid, x, y, w, h, "RANGED_VALUE", "PrayerWindowComplicationService", "RANGED_VALUE",
                f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>', body, customizable="FALSE", ambient=(when == "active"))


def prayer_parts(w, h, ink=WHITE, soft=SOFT):
    """The prayer row's drawing in a w x h box, for a PrayerWindow complication."""
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
             f'<Line startX="{col:.0f}" startY="{sz(14)}" endX="{col:.0f}" endY="{h - sz(14)}"><Stroke color="#38AADCC8" thickness="1"/></Line>'
             f'<Line startX="{2*col:.0f}" startY="{sz(14)}" endX="{2*col:.0f}" endY="{h - sz(14)}"><Stroke color="#38AADCC8" thickness="1"/></Line></PartDraw>'
             + text(int(2 * col), sz(14), int(col), sz(24), "مضى", sz(17), soft, SEMI, shadow=True)
             + duration(passed, int(2 * col), sz(42), int(col), sz(42), sz(26), ink, shadow=True)
             + text(int(col), sz(10), int(col), sz(38), ("%s", ["[COMPLICATION.TEXT]"]), sz(26), ink, shadow=True)
             + text(int(col), sz(52), int(col), sz(32), ("%s%s:%s%s", [f"floor({nxt} / 600)", f"(floor({nxt} / 60) % 10)", f"floor(({nxt} % 60) / 10)", f"({nxt} % 10)"]), sz(29), ink, shadow=True)
             + text(0, sz(14), int(col), sz(24), "متبقي", sz(17), soft, SEMI, shadow=True)
             + duration(left, 0, sz(42), int(col), sz(42), sz(26), ink, shadow=True))
    return parts


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


def write(module, title, clock_type, parts, preview="16:40:24", config=""):
    out = os.path.join(ROOT, module, "src/main/res/raw/watchface.xml")
    scene = unify_aod("\n".join(parts), analog=(clock_type == "ANALOG"))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f'<!-- {title}. Generated by scripts/wff/: edit the script, not this. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           f'<Metadata key="CLOCK_TYPE" value="{clock_type}"/>\n<Metadata key="PREVIEW_TIME" value="{preview}"/>\n'
           + config + '<Scene backgroundColor="#FF000000">\n' + scene + '\n</Scene>\n</WatchFace>\n')
    open(out, "w").write(xml)
    slots = xml.count("<ComplicationSlot ")
    assert slots <= 8, slots
    ids = [int(s.split('"')[0]) for s in xml.split('slotId="')[1:]]
    assert len(ids) == len(set(ids)), ids
    print(f"wrote {out} ({slots} slots)")


def tap_zone(sid, x, y, w, h, provider):
    """An area that opens part of the app when touched. Draws nothing.
    (The face format has no double tap: a slot answers a single touch.)"""
    return slot(sid, round(x), round(y), round(w), round(h), "SMALL_IMAGE", provider, "SMALL_IMAGE",
                f'<BoundingBox x="0" y="0" width="{round(w)}" height="{round(h)}"/>',
                '<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="1" height="1" alpha="0">'
                '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>',
                customizable="FALSE")


# zaher, 2026-11-07: on every face with hands, the figures open parts of the app.
# zaher, 2026-11-07 (second round): two taps, not one, so a touch that only wakes the
# screen opens nothing. The face format has no double tap, so these sources open a
# see-through page in the app that waits for the second tap (TapGateActivity).
NUMBER_TAPS = ((0, "GateSurahStartComplicationService"),      # 12: the surah list from al-Fatiha
               (90, "GateBookmarksComplicationService"),      # 3: the bookmarks
               (180, "GateBurujComplicationService"),         # 6: the surah list at al-Buruj
               (270, "GateSettingsComplicationService"))      # 9: the settings


def number_taps(first_sid, r=180, w=70, h=60):
    """Four touch areas centred on 12, 3, 6 and 9 at radius r: four slots."""
    import math
    out = ""
    for i, (deg, provider) in enumerate(NUMBER_TAPS):
        a = math.radians(deg - 90)
        cx, cy = 225 + r * math.cos(a), 225 + r * math.sin(a)
        out += tap_zone(first_sid + i, cx - w / 2, cy - h / 2, w, h, provider)
    return out


def digit_taps(first_sid, x, y, w, h):
    """On a digital face: the hours open the surah list, the minutes open it at al-Buruj.
    The time reads left to right, so the hours are the left half."""
    return (tap_zone(first_sid, x, y, w / 2, h, "GateSurahStartComplicationService")
            + tap_zone(first_sid + 1, x + w / 2, y, w / 2, h, "GateBurujComplicationService"))


HOUR_ANGLE = "([HOUR_0_23] % 12) * 30 + [MINUTE] * 0.5"
MINUTE_ANGLE = "[MINUTE] * 6 + [SECOND] * 0.1"
SECOND_ANGLE = "[SECOND] * 6"


def lasers(r_in=208, r_out=222, hour="#FF7DFFB0", minute="#FF7DFFB0", second="#FFFF6A5A", seconds=True):
    """A glowing mark on the rim facing each hand, in that hand's own colour (zaher,
    2026-11-07). Each mark is three strokes, the wider two faint, for the glow; the
    colour may be a configuration value, so the faintness is the PartDraw's alpha.
    Hidden on always-on."""
    def mark(name, angle, width, color):
        line = lambda w, a: (f'<PartDraw x="0" y="0" width="450" height="450" alpha="{a}"><Line startX="225" startY="{225 - r_in:.1f}" endX="225" endY="{225 - r_out:.1f}">'
                             f'<Stroke color="{color}" thickness="{w:.1f}" cap="ROUND"/></Line></PartDraw>')
        return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">{HIDE_IN_AMBIENT}'
                f'<Transform target="angle" value="{angle}"/>'
                + line(width * 3, 51) + line(width * 1.8, 90) + line(width, 255) + '</Group>')
    return (mark("laserH", HOUR_ANGLE, 5, hour) + mark("laserM", MINUTE_ANGLE, 4, minute)
            + (mark("laserS", SECOND_ANGLE, 2.5, second) if seconds else ""))


# ── the always-on screen, one for every face (zaher, 2026-11-07) ──────────────
# From the top: the date and the temperature, the time in large figures (on a face
# with hands: its own hour and minute hands), then the prayer row (passed | next
# prayer and its time | left), all large. The always-on screen updates once a
# minute, so it has no seconds.
AOD_TOP = (40, 50, 370, 52)            # date and temperature
AOD_TIME_Y, AOD_TIME_SIZE = 104, 128
AOD_ROW = (38, 258, 374, 124)           # the prayer row
AOD_ROW_ANALOG = (38, 270, 374, 120)
AOD_INK, AOD_SOFT = "#FFFFFFFF", "#FFC4CEC9"


def _aod_group(inner):
    return (f'<Group name="aod" x="0" y="0" width="450" height="450" alpha="0">'
            f'<Variant mode="AMBIENT" target="alpha" value="255"/>{inner}</Group>')


def aod_static(analog, hands_xml=""):
    x, y, w, h = AOD_TOP
    top = (text(x + w // 2, y, w // 2, h, ("%s/%s", ["[DAY]", "[MONTH]"]), 40, AOD_INK)
           + text(x, y, w // 2, h, ("%s°", ["[WEATHER.TEMPERATURE]"]), 40, AOD_INK, alpha="(255 * [WEATHER.IS_AVAILABLE])"))
    if analog:
        middle = hands_xml + ('<PartDraw x="0" y="0" width="450" height="450"><Ellipse x="216" y="216" width="18" height="18">'
                              '<Fill color="#FFFFFFFF"/></Ellipse></PartDraw>')
    else:
        middle = clock(y=AOD_TIME_Y, size=AOD_TIME_SIZE, height=round(AOD_TIME_SIZE * 1.04), color=AOD_INK)
    return _aod_group(top + middle)


def unify_aod(scene, analog):
    """Gives a face the shared always-on screen. Everything the face draws is hidden
    there; every slot is hidden there except the prayer row's, which carries the
    always-on row as well (no slot is spent on it). Always-on copies the face made
    for itself (a second date or prayer slot) are dropped, freeing their slots."""
    import copy
    import xml.etree.ElementTree as ET
    root = ET.fromstring(f"<Scene>{scene}</Scene>")
    drawn, slots, hands = [], [], []
    for el in list(root):
        if el.tag != "ComplicationSlot":
            drawn.append(el)
            continue
        raw = ET.tostring(el, encoding="unicode")
        if 'name="ambientPrayer"' in raw or 'name="ambientDate"' in raw:
            continue                     # an always-on copy: the shared screen replaces it
        slots.append(el)
    if analog:
        for el in drawn:
            for g in el.iter("Group"):
                if g.get("name") in ("hour", "minute"):
                    h = copy.deepcopy(g)
                    for v in [c for c in h if c.tag == "Variant"]:
                        h.remove(v)
                    hands.append(ET.tostring(h, encoding="unicode"))
    prayer = next((sl for sl in slots if "PrayerWindowComplicationService" in (sl.find("DefaultProviderPolicy").get("primaryProvider") or "")), None)
    for sl in slots:
        variants = [c for c in sl if c.tag == "Variant"]
        if sl is prayer:
            for v in variants:
                sl.remove(v)
            others = [tuple(float(o.get(k)) for k in ("x", "y", "width", "height")) for o in slots
                      if o is not sl and o.find("BoundingArc") is None]   # an arc slot keeps to the rim
            _carry_aod_row(sl, _aod_box(sl, AOD_ROW_ANALOG if analog else AOD_ROW, others))
        elif not variants:
            sl.insert(0, ET.fromstring(HIDE_IN_AMBIENT))
    if prayer is not None:
        # Last: where its widened box still meets another slot (the corners between
        # its own place and the row), a touch goes to the slot listed first, the other.
        slots.remove(prayer)
        slots.append(prayer)
    active = (f'<Group name="face" x="0" y="0" width="450" height="450">{HIDE_IN_AMBIENT}'
              + "".join(ET.tostring(e, encoding="unicode") for e in drawn) + "</Group>")
    return "\n".join([active] + [ET.tostring(sl, encoding="unicode") for sl in slots] + [aod_static(analog, "".join(hands))])


def _aod_box(sl, box, others):
    """The always-on row's box, kept clear of every other slot: the prayer slot grows
    to hold it, and where slots overlap, a touch could reach the wrong one."""
    x, y, w, h = (float(sl.get(k)) for k in ("x", "y", "width", "height"))
    ax, ay, aw, ah = box
    top, bottom = ay, ay + ah
    hit = lambda a, b: a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]
    for o in others:
        if hit((ax, top, aw, bottom - top), o):
            if o[1] + o[3] / 2 > (top + bottom) / 2:
                bottom = min(bottom, o[1] - 2)
            else:
                top = max(top, o[1] + o[3] + 2)
    if bottom - top >= 64:
        return (ax, round(top), aw, round(bottom - top))
    return (x, y, w, h)                   # no room: the row stays in the slot's own place


def _carry_aod_row(sl, box):
    """Widens the prayer slot to hold the always-on row too: the face's own drawing
    is hidden there, the large row shown only there."""
    import xml.etree.ElementTree as ET
    x, y, w, h = (float(sl.get(k)) for k in ("x", "y", "width", "height"))
    ax, ay, aw, ah = box
    nx, ny = min(x, ax), min(y, ay)
    nw, nh = max(x + w, ax + aw) - nx, max(y + h, ay + ah) - ny
    sl.set("x", f"{nx:.0f}"); sl.set("y", f"{ny:.0f}"); sl.set("width", f"{nw:.0f}"); sl.set("height", f"{nh:.0f}")
    for b in list(sl):
        if b.tag.startswith("Bounding"):
            sl.remove(b)
    idx = [i for i, c in enumerate(sl) if c.tag == "DefaultProviderPolicy"][0]
    sl.insert(idx + 1, ET.fromstring(f'<BoundingBox x="0" y="0" width="{nw:.0f}" height="{nh:.0f}"/>'))
    for comp in sl.findall("Complication"):
        inner = list(comp)
        for c in inner:
            comp.remove(c)
        own = ET.fromstring(f'<Group name="prayerOnFace" x="{x - nx:.0f}" y="{y - ny:.0f}" width="{w:.0f}" height="{h:.0f}">{HIDE_IN_AMBIENT}</Group>')
        for c in inner:
            own.append(c)
        comp.append(own)
        if comp.get("type") == "RANGED_VALUE":
            aw_, ah_ = round(aw), round(ah)
            aod = ET.fromstring(f'<Group name="aodPrayer" x="{ax - nx:.0f}" y="{ay - ny:.0f}" width="{aw_}" height="{ah_}" alpha="0">'
                                f'<Variant mode="AMBIENT" target="alpha" value="255"/>{prayer_parts(aw_, ah_, AOD_INK, AOD_SOFT)}</Group>')
            comp.append(aod)
