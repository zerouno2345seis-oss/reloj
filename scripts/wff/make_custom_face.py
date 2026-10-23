#!/usr/bin/env python3
"""Writes watchface-custom/src/main/res/raw/watchface.xml from a face-editor
export: zaher's own face («واجهتي»), designed from scratch in the editor.

    python3 make_custom_face.py [design.json]     (default: every design/faces/*.json)

A design's "id" picks its face: "custom" is «واجهتي»; any other id gets a face
of its own (a watchface-c-<id> module, made on first use), so a copy saved
from the editor under a new id is added beside the original. "faceName" is
the name the watch shows.

The JSON is the editor's export, in its 438 px space; everything is scaled to
the 450 px Watch Face Format square. Angles are degrees clockwise from 12
o'clock and name a segment's centre. Every key is optional:

  background          "photo" | "mosques" | "pattern" | "none"
  backgroundGradient  {"type": "none|linear|radial", "angle": deg, "overImage": bool,
                       "stops": [{"color": "#RRGGBB", "alpha": 0-1, "pos": 0-1}]}
  clock               "digital" | "analog"; hourNumerals "none|4|12", hourNumeralSize, minuteTicks
  colors              text, soft, accent, seconds, secondsTrack, hourHand, minuteHand,
                      secondHand, numerals, ticks
  ring                {"thickness", "fontSize", "secondsInside", "gradient": {"type": "none|sweep", "stops"}}
  segments            [{"angle", "spanDeg", "content", "color", "colorEnd", "textColor", "customizable"}]
                      content: none, date, battery, steps, heart, surah, next, temp, khatma,
                      bookmarks, mushaf
  elements            date, pill, time, prayer, edges: {"show", "y" (centre), "scale"}
  backgroundImage     a picture from the computer: the data URL itself, {"dataUrl": ...} or
                      {"file": "images/x.jpg"} (next to the design), with "dim": 0-1 to darken it;
                      it becomes the background; backgroundImageOpacity 0-1 sets how strongly it shows
  font / fonts        one Google family for everything, or {"time": ..., "text": ...}
  colors.auto         true: text, accents, seconds, hands and any segment coloured "auto"
                      take their colours from the background picture

Samsung allows eight slots. The date, reading place, prayer row and data
segments come first; when the design needs more, the extras go in this order,
and the build says so: the second (always-on) prayer row, the tap on the
time, the edge icons.
"""
import json
import math
import os
import sys

import kit
from make_row_face import SEMI, font, slot, text

K = 450 / 438
DEFAULT_JSON = os.path.join(kit.ROOT, "design/faces/custom.json")

PROVIDERS = {
    "date": ("SHORT_TEXT", "GregorianDateComplicationService", None),
    "battery": ("RANGED_VALUE", None, "WATCH_BATTERY"),
    "steps": ("RANGED_VALUE", None, "STEP_COUNT"),
    "heart": ("SHORT_TEXT", None, "HEART_RATE"),
    "surah": ("SHORT_TEXT", "ContinueReadingComplicationService", None),
    "next": ("SHORT_TEXT", "NextPrayerComplicationService", None),
    "khatma": ("RANGED_VALUE", "KhatmaProgressComplicationService", None),
    "bookmarks": ("SMALL_IMAGE", "BookmarksShortcutComplicationService", None),
    "mushaf": ("SMALL_IMAGE", "QuranShortcutComplicationService", None),
}
METERS = {"battery", "steps", "khatma"}
ICONS = {"bookmarks": "icon_bookmark", "mushaf": "icon_quran"}
FONT_DIR = os.path.join(kit.ROOT, "design/fonts")
# The editor names Google families; each is bundled (all SIL OFL, licences in design/fonts).
# None is the face's own IBM Plex Sans Arabic. Variable fonts draw at their regular weight.
FONTS = {
    "ibm plex sans arabic": None, "plex": None,
    "tajawal": "tajawal_bold", "cairo": "cairo", "reem kufi": "reem_kufi", "almarai": "almarai_bold",
    "el messiri": "el_messiri", "aref ruqaa": "aref_ruqaa_bold", "ruqaa": "aref_ruqaa_bold",
    "lateef": "lateef_bold", "scheherazade new": "scheherazade_bold", "changa": "changa",
    "amiri": "amiri_bold", "noto kufi arabic": "noto_kufi",
    "lalezar": "lalezar", "rakkas": "rakkas", "harmattan": "harmattan_bold",
}


def font_file(name):
    if not name:
        return None
    key = str(name).split(",")[0].strip().strip("'\"").lower()      # "Noto Kufi Arabic, sans-serif"
    if key not in FONTS:
        print(f"note: font {name!r} is not bundled; IBM Plex Sans Arabic is used")
        return None
    return FONTS[key]


def user_background(spec, folder, module):
    """The picture from the computer, cropped to the round face and darkened; returns its resource name."""
    import base64
    import io
    from PIL import Image
    img = spec.get("backgroundImage")
    if not img:
        return None
    if isinstance(img, str):                       # the editor sends the data URL itself
        img = {"dataUrl": img, "dim": 0}
    if img.get("dataUrl"):
        raw = base64.b64decode(img["dataUrl"].split(",", 1)[1])
        im = Image.open(io.BytesIO(raw))
    else:
        im = Image.open(os.path.join(folder, img["file"]))
    im = im.convert("RGB")
    side = min(im.size)
    left, top = (im.width - side) // 2, (im.height - side) // 2
    im = im.crop((left, top, left + side, top + side)).resize((450, 450), Image.LANCZOS)
    dim = float(img.get("dim", 0))
    if dim > 0:
        im = Image.blend(im, Image.new("RGB", im.size, (0, 0, 0)), min(dim, 0.95))
    out = os.path.join(kit.ROOT, module, "src/main/res/drawable-nodpi/bg_user.webp")
    im.save(out, "WEBP", quality=80, method=6)
    return "bg_user", im


def palette(im, n=6):
    """Colours for a face from its picture: the dominant ones, the liveliest first."""
    import colorsys
    small = im.resize((80, 80)).quantize(colors=n).convert("RGB")
    counts = sorted(small.getcolors(80 * 80), reverse=True)
    out = []
    for _, (r_, g, b) in counts:
        h, l, sat = colorsys.rgb_to_hls(r_ / 255, g / 255, b / 255)
        out.append((sat * min(l, 1 - l) * 4, h, l, sat))
    out.sort(reverse=True)

    def hexc(h, l, sat):
        rr, gg, bb = colorsys.hls_to_rgb(h, l, sat)
        return f"#{round(rr * 255):02x}{round(gg * 255):02x}{round(bb * 255):02x}"
    lively = [hexc(h, max(0.55, min(l, 0.7)), max(sat, 0.45)) for _, h, l, sat in out]
    return lively or ["#e8bc3e"]


def apply_auto_colours(spec, im):
    cols = palette(im)
    c = spec.setdefault("colors", {})
    accent = cols[0]
    c.update({"accent": accent, "seconds": accent, "secondsTrack": "#2a3a34",
              "hourHand": "#ffffff", "minuteHand": "#f1f1f1", "secondHand": accent, "numerals": "#e6e6e6",
              "text": "#ffffff", "soft": "#dadada"})
    i = 0
    for seg in spec.get("segments", []):
        if seg.get("color") == "auto":
            seg["color"] = cols[i % len(cols)]
            i += 1


def use_fonts(spec, module, parts):
    """Copies the chosen fonts into the face and points the time and the text at them."""
    import shutil
    f = spec.get("fonts", {})
    one = spec.get("font")
    time_font, text_font = font_file(f.get("time", one)), font_file(f.get("text", one))
    font_dir = os.path.join(kit.ROOT, module, "src/main/res/font")
    for name in os.listdir(font_dir):          # only what this face uses, beside the defaults
        if not name.startswith("plex_"):
            os.remove(os.path.join(font_dir, name))
    for chosen in {time_font, text_font} - {None}:
        shutil.copy(os.path.join(FONT_DIR, chosen + ".ttf"), os.path.join(font_dir, chosen + ".ttf"))
    out = []
    for p in parts:
        is_clock = "<DigitalClock" in p
        target = time_font if is_clock else text_font
        if target:
            p = p.replace('family="plex_bold"', f'family="{target}"').replace('family="plex_semibold"', f'family="{target}"')
        out.append(p)
    return out


def argb(hex_color, alpha=1.0):
    h = (hex_color or "#FFFFFF").lstrip("#")
    if len(h) == 8:
        return "#" + h.upper()
    return f"#{round(max(0, min(1, alpha)) * 255):02X}{h.upper()}"


def pol(r, deg):
    a = math.radians(deg)
    return 225 + r * math.sin(a), 225 - r * math.cos(a)


def active(name, inner):
    return f'<Group name="{name}" x="0" y="0" width="450" height="450">{kit.HIDE_IN_AMBIENT}{inner}</Group>'


def ambient_only(name, inner):
    return (f'<Group name="{name}" x="0" y="0" width="450" height="450" alpha="0">'
            f'<Variant mode="AMBIENT" target="alpha" value="255"/>{inner}</Group>')


def gradient_xml(g, box=(0, 0, 450, 450)):
    """A WFF gradient from the editor's {type, angle, stops}; None when there is none."""
    if not g or g.get("type", "none") == "none" or not g.get("stops"):
        return None
    stops = sorted(g["stops"], key=lambda s: s.get("pos", 0))
    colors = " ".join(argb(s["color"], s.get("alpha", 1)) for s in stops)
    positions = " ".join(f'{s.get("pos", 0):.3f}' for s in stops)
    x, y, w, h = box
    cx, cy = x + w / 2, y + h / 2
    if g["type"] == "radial":
        return f'<RadialGradient centerX="{cx:.0f}" centerY="{cy:.0f}" radius="{max(w, h) / 2:.0f}" colors="{colors}" positions="{positions}"/>'
    if g["type"] == "sweep":
        return f'<SweepGradient centerX="{cx:.0f}" centerY="{cy:.0f}" colors="{colors}" positions="{positions}"/>'
    a = math.radians(g.get("angle", 180))
    dx, dy = math.sin(a) * w / 2, -math.cos(a) * h / 2
    return (f'<LinearGradient startX="{cx - dx:.0f}" startY="{cy - dy:.0f}" endX="{cx + dx:.0f}" endY="{cy + dy:.0f}" '
            f'colors="{colors}" positions="{positions}"/>')


class Face:
    def __init__(self, spec):
        self.s = spec
        c = spec.get("colors", {})
        self.ink = argb(c.get("text", "#FFFFFF"))
        self.soft = argb(c.get("soft", "#D3DAD6"))
        self.accent = argb(c.get("accent", "#E8BC3E"))
        self.sec = argb(c.get("seconds", c.get("accent", "#43D49A")))
        self.sec_track = argb(c.get("secondsTrack", "#2A3A34"))
        ring = spec.get("ring", {})
        self.T = ring.get("thickness", 0) * K
        self.font_px = round(ring.get("fontSize", 18) * K)
        self.mid = 225 - self.T / 2
        self.ring_gradient = gradient_xml(ring.get("gradient"))
        self.seconds_inside = ring.get("secondsInside", True)
        self.parts, self.slots, self.notes = [], [], []

    # ── background ──────────────────────────────────────────────────────────
    def background(self):
        bg = self.s.get("background", "none")
        image = {"photo": "bg_photo", "mosques": "bg_mosques", "pattern": "bg_pattern", "user": "bg_user"}.get(bg)
        g = self.s.get("backgroundGradient", {})
        grad = gradient_xml(g)
        layers = ""
        if image and not (grad and not g.get("overImage", True)):
            alpha = round(255 * float(self.s.get("backgroundImageOpacity", 1))) if bg == "user" else 255
            layers += f'<PartImage x="0" y="0" width="450" height="450" alpha="{alpha}"><Image resource="{image}"/></PartImage>'
        if grad:
            layers += f'<PartDraw x="0" y="0" width="450" height="450"><Rectangle x="0" y="0" width="450" height="450"><Fill color="#FFFFFFFF">{grad}</Fill></Rectangle></PartDraw>'
        if layers:
            self.parts.append(active("background", layers))

    # ── the ring ────────────────────────────────────────────────────────────
    def _stroke(self, color, color_end, start, end, width=None):
        width = width if width is not None else self.T
        inner = ""
        if self.ring_gradient and not color_end:
            inner = self.ring_gradient
        elif color_end:
            (x0, y0), (x1, y1) = pol(self.mid, start), pol(self.mid, end)
            inner = (f'<LinearGradient startX="{x0:.0f}" startY="{y0:.0f}" endX="{x1:.0f}" endY="{y1:.0f}" '
                     f'colors="{argb(color)} {argb(color_end)}" positions="0 1"/>')
        return f'<Stroke color="{argb(color)}" thickness="{width:.1f}" cap="BUTT">{inner}</Stroke>'

    def _arc(self, start, end, color, color_end=None, alpha=1.0):
        d = 2 * self.mid
        col = argb(color, alpha) if not color.startswith("#") or len(color.lstrip("#")) != 8 else color
        return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
                f'startAngle="{start:.2f}" endAngle="{end:.2f}">{self._stroke(col, color_end, start, end)}</Arc></PartDraw>')

    def _level(self, start, end, color):
        d = 2 * self.mid
        v = ("([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
             "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN])")
        return (f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
                f'startAngle="{start:.2f}" endAngle="{start:.2f}"><Transform target="endAngle" value="({start:.2f} + ({end - start:.2f}) * clamp({v}, 0, 1))"/>'
                f'<Stroke color="{argb(color)}" thickness="{self.T:.1f}" cap="BUTT"/></Arc></PartDraw>')

    def _text_on_arc(self, start, end, body, color, alpha=None):
        tpl, params = body if isinstance(body, tuple) else (body, [])
        inner = ("<Template>" + tpl + "".join(f'<Parameter expression="{p}"/>' for p in params) + "</Template>") if params else tpl
        bottom = 90 < ((start + end) / 2) % 360 < 270
        a, b, direction = (end, start, "COUNTER_CLOCKWISE") if bottom else (start, end, "CLOCKWISE")
        d = 2 * self.mid - 4
        tr = f'<Transform target="alpha" value="{alpha}"/>' if alpha else ""
        return (f'<PartText x="0" y="0" width="450" height="450">{tr}<TextCircular centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" '
                f'startAngle="{a:.2f}" endAngle="{b:.2f}" direction="{direction}" align="CENTER">'
                + font(self.font_px, argb(color)) + inner + '</Font></TextCircular></PartText>')

    def ring(self, budget):
        segs = self.s.get("segments", [])
        if not segs or self.T <= 0:
            return
        outline = ""
        for i, seg in enumerate(segs):
            half = seg.get("spanDeg", 360 / len(segs) - 3) / 2
            start, end = seg.get("angle", 0) - half, seg.get("angle", 0) + half
            if start < 0:
                start, end = start + 360, end + 360
            content = seg.get("content", "none")
            color, color_end = seg.get("color", "#7fc3b0"), seg.get("colorEnd")
            ink = seg.get("textColor", "#FFFFFF" if content in METERS else "#1A1408")
            d = 2 * self.mid
            outline += (f'<Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" startAngle="{start:.2f}" endAngle="{end:.2f}">'
                        f'<Stroke color="#FF2C3A35" thickness="1.5"/></Arc>')
            if content == "none":
                self.parts.append(active(f"seg{i}", self._arc(start, end, color, color_end, alpha=0.85)))
                continue
            if content == "temp":
                self.parts.append(active(f"seg{i}", self._arc(start, end, color, color_end)
                                         + self._text_on_arc(start, end, ("%s°", ["[WEATHER.TEMPERATURE]"]), ink, "(255 * [WEATHER.IS_AVAILABLE])")
                                         + self._text_on_arc(start, end, "--°", ink, "(255 * (1 - [WEATHER.IS_AVAILABLE]))")))
                continue
            if budget.left() < 1:
                self.notes.append(f"segment {i + 1} ({content}) dropped: no slot left")
                self.parts.append(active(f"seg{i}", self._arc(start, end, color, color_end, alpha=0.85)))
                continue
            default, provider, system = PROVIDERS[content]
            sid = budget.take()
            bound = (f'<BoundingArc centerX="225" centerY="225" width="450" height="450" startAngle="{start:.2f}" '
                     f'endAngle="{end:.2f}" thickness="{self.T:.1f}"/>')
            if content in ICONS:
                # The icon is drawn by the face: drawn by the slot, the watch clipped it.
                body = f'<Complication type="SMALL_IMAGE">{active(f"seg{i}", self._arc(start, end, color, color_end))}</Complication>'
                types = "SMALL_IMAGE"
                cx, cy = pol(self.mid, seg.get("angle", 0))
                size = max(24, min(36, self.T * 1.1))
                self.parts.append(active(f"segIcon{i}", f'<PartImage x="{cx - size / 2:.0f}" y="{cy - size / 2:.0f}" width="{size:.0f}" height="{size:.0f}">'
                                                         f'<Image resource="{ICONS[content]}"/></PartImage>'))
            else:
                label = ("%s %s", ["[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"]) if content == "next" else ("%s", ["[COMPLICATION.TEXT]"])
                other = "SHORT_TEXT" if default == "RANGED_VALUE" else "RANGED_VALUE"
                types = f"{default} {other}"
                bodies = []
                for t in (default, other):
                    if content in METERS and t == "RANGED_VALUE":
                        draw = self._arc(start, end, color, None, alpha=0.33) + self._level(start, end, color)
                    else:
                        draw = self._arc(start, end, color, color_end)
                    bodies.append(f'<Complication type="{t}">{active(f"seg{i}{t}", draw + self._text_on_arc(start, end, label, ink))}</Complication>')
                body = "".join(bodies)
            customizable = "TRUE" if seg.get("customizable") else "FALSE"
            self.parts.append(slot(sid, 0, 0, 450, 450, types, provider, default, bound, body,
                                   customizable=customizable, system=system or "EMPTY", ambient=False))
        self.parts.append(ambient_only("ringOutline", f'<PartDraw x="0" y="0" width="450" height="450">{outline}</PartDraw>'))

    # ── seconds, dial, hands ──────────────────────────────────────────────────
    def seconds(self):
        r = (225 - self.T - 5) if self.seconds_inside else 217
        d = 2 * r
        self.parts.append(active("seconds",
                                 f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" startAngle="0" endAngle="360"><Stroke color="{self.sec_track}" thickness="3"/></Arc></PartDraw>'
                                 f'<PartDraw x="0" y="0" width="450" height="450"><Arc centerX="225" centerY="225" width="{d:.0f}" height="{d:.0f}" startAngle="0" endAngle="0">'
                                 f'<Transform target="endAngle" value="([SECOND] * 6)"/><Stroke color="{self.sec}" thickness="3" cap="ROUND"/></Arc></PartDraw>'))
        return r

    def dial(self, inner_r):
        c = self.s.get("colors", {})
        out = []
        if self.s.get("minuteTicks"):
            tick = argb(c.get("ticks", "#B8C4BE"))
            for i in range(60):
                length, w = (16, 3) if i % 5 == 0 else (6, 1.5)
                (x0, y0), (x1, y1) = pol(inner_r - 3, i * 6), pol(inner_r - 3 - length, i * 6)
                out.append(f'<Line startX="{x0:.1f}" startY="{y0:.1f}" endX="{x1:.1f}" endY="{y1:.1f}"><Stroke color="{tick}" thickness="{w}" cap="ROUND"/></Line>')
        if out:
            self.parts.append(active("ticks", f'<PartDraw x="0" y="0" width="450" height="450">{"".join(out)}</PartDraw>'))
        which = self.s.get("hourNumerals", "none")
        if which != "none":
            hours = (12, 3, 6, 9) if which == "4" else range(1, 13)
            size = round(self.s.get("hourNumeralSize", 18) * K)
            r = inner_r - 3 - (24 if self.s.get("minuteTicks") else 6) - size / 2
            nums = argb(c.get("numerals", "#D3DAD6"))
            texts = []
            for h in hours:
                x, y = pol(r, h * 30)
                texts.append(text(round(x - size), round(y - size * 0.75), size * 2, round(size * 1.5), str(h), size,
                                  self.accent if h == 12 else nums))
            self.parts.append(active("numerals", "".join(texts)))

    def hands(self):
        c = self.s.get("colors", {})

        def hand(name, length, width, color, angle):
            return (f'<Group name="{name}" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5"><Transform target="angle" value="{angle}"/>'
                    f'<PartDraw x="0" y="0" width="450" height="450"><Line startX="225" startY="225" endX="225" endY="{225 - length}">'
                    f'<Stroke color="{color}" thickness="{width}" cap="ROUND"/></Line></PartDraw></Group>')
        self.parts.append(active("hands",
                                 hand("hour", 70, 9, argb(c.get("hourHand", "#FFFFFF")), "([HOUR_0_23] % 12) * 30 + [MINUTE] * 0.5")
                                 + hand("minute", 112, 6, argb(c.get("minuteHand", "#E8EEEA")), "[MINUTE] * 6 + [SECOND] * 0.1")
                                 + (hand("second", 118, 2, argb(c.get("secondHand", "#E8BC3E")), "[SECOND] * 6") if not self.s.get("ring", {}).get("secondsInside", True) else "")
                                 + f'<PartDraw x="0" y="0" width="450" height="450"><Ellipse x="216" y="216" width="18" height="18"><Fill color="{self.accent}"/></Ellipse></PartDraw>'))


class Budget:
    """Hands out slot ids; Samsung allows eight slots on a face."""
    def __init__(self, first_id=71, limit=8):
        self.next, self.used, self.limit = first_id, 0, limit

    def left(self):
        return self.limit - self.used

    def take(self):
        assert self.left() > 0
        self.used += 1
        self.next += 1
        return self.next - 1


def element(spec, name, default_y, default_scale=1.0):
    e = spec.get("elements", {}).get(name, {})
    return e.get("show", True), e.get("y", default_y) * K, e.get("scale", default_scale)


def build(spec, module=None, folder="."):
    if spec.get("backgroundImage") and module:
        name, im = user_background(spec, folder, module)
        spec["background"] = "user"
        if spec.get("colors", {}).get("auto"):
            apply_auto_colours(spec, im)
    f = Face(spec)
    analog = spec.get("clock", "digital") == "analog"
    show_prayer, py, ps = element(spec, "prayer", 300)
    show_pill, ly, ls = element(spec, "pill", 120)
    show_date, dy, ds = element(spec, "date", 60)
    show_time, ty, tsc = element(spec, "time", 200)
    show_edges, ey, es = element(spec, "edges", 222)
    pw, ph = round(323 * ps), round(101 * ps)
    prayer_box = (round(225 - pw / 2), round(py - ph / 2), pw, ph)

    # Plan the eight slots: the date, reading place, prayer row and data segments come first;
    # then the edge icons, the tap on the time and the always-on copy of the prayer row.
    ring_slots = sum(1 for g in spec.get("segments", []) if g.get("content", "none") not in ("none", "temp")) if f.T > 0 else 0
    ring_slots = min(ring_slots, 8 - int(show_date) - int(show_pill) - int(show_prayer))
    base = int(show_date) + int(show_pill) + int(show_prayer) + ring_slots
    free = 8 - base
    want_edges = show_edges and free >= 2
    free -= 2 if want_edges else 0
    want_tap = (show_time or analog) and free >= 1
    free -= 1 if want_tap else 0
    want_aod_prayer = show_prayer and prayer_box != kit.PRAYER_BOX and free >= 1
    if show_edges and not want_edges:
        f.notes.append("no slots left for the edge icons")
    if (show_time or analog) and not want_tap:
        f.notes.append("no slot left for the tap on the time")
    if show_prayer and prayer_box != kit.PRAYER_BOX and not want_aod_prayer:
        f.notes.append("the always-on prayer row keeps this face's own place (no slot left for a second one)")

    budget = Budget()
    f.background()
    budget.limit = 8 - (int(show_date) + int(show_pill) + int(show_prayer))   # those come first
    f.ring(budget)
    budget.limit = 8
    inner_r = f.seconds()
    if analog:
        f.dial(inner_r)
        f.hands()
    elif show_time:
        size = round(110 * tsc)
        f.parts.append(kit.clock(y=round(ty - size * 0.52), size=size, when="active", height=round(size * 1.04), color=f.ink))
    f.parts.append(kit.ambient_clock())

    if show_date:
        w, h = round(150 * ds), round(36 * ds)
        f.parts.append(kit.date_slot(budget.take(), y=round(dy - h / 2), size=round(30 * ds), x=round(225 - w / 2), w=w, h=h, ink=f.ink))
    if show_pill:
        w, h = round(310 * ls), round(70 * ls)
        f.parts.append(kit.reading_slot(budget.take(), round(225 - w / 2), round(ly - h / 2), w, h, ink=f.ink, soft=f.soft))
    if show_prayer:
        f.parts.append(kit.prayer_row(budget.take(), box=prayer_box, when="active" if want_aod_prayer else "always", ink=f.ink, soft=f.soft))
    if want_tap:
        if analog:
            f.parts.append(kit.time_tap(budget.take(), 175, 175, 100, 100))
        else:
            size = round(110 * tsc)
            f.parts.append(kit.time_tap(budget.take(), 60, round(ty - size * 0.45), 330, round(size * 0.9)))
    if want_edges:
        size = round(34 * es)
        for provider, x in (("QuranShortcutComplicationService", 225 + inner_r - size - 8),
                            ("BookmarksShortcutComplicationService", 225 - inner_r + 8)):
            body = (f'<Complication type="SMALL_IMAGE"><PartImage x="0" y="0" width="{size}" height="{size}">'
                    '<Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>')
            f.parts.append(slot(budget.take(), round(x), round(ey - size / 2), size, size, "SMALL_IMAGE", provider, "SMALL_IMAGE",
                                f'<BoundingBox x="0" y="0" width="{size}" height="{size}"/>', body, customizable="FALSE"))
    if want_aod_prayer:
        f.parts.append(kit.prayer_row(budget.take(), when="ambient", ink=f.ink, soft=f.soft))
    return f


def module_for(spec):
    """The face's own package: "custom" is «واجهتي»; any other id is a separate face, so a
    copy saved from the editor is added beside the original instead of replacing it."""
    fid = str(spec.get("id", "custom")).lower()
    assert fid.isascii() and fid.replace("_", "").isalnum() and fid[0].isalpha(), f"id must be letters and digits: {fid}"
    return "watchface-custom" if fid == "custom" else f"watchface-c-{fid}"


def ensure_module(module, face_name):
    """A new face module, copied from watchface-custom with its own package and name."""
    import shutil
    root = kit.ROOT
    dst = os.path.join(root, module)
    if module != "watchface-custom" and not os.path.exists(dst):
        src = os.path.join(root, "watchface-custom")
        shutil.copytree(src, dst, ignore=shutil.ignore_patterns("build"))
        pkg = "com.quran.watch8." + module.replace("-", ".")
        for rel in ("build.gradle.kts", "src/main/AndroidManifest.xml"):
            path = os.path.join(dst, rel)
            text_ = open(path, encoding="utf-8").read().replace("com.quran.watch8.watchface.custom", pkg)
            open(path, "w", encoding="utf-8").write(text_)
    strings = os.path.join(dst, "src/main/res/values/strings.xml")
    open(strings, "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <string name="face_name">القرآن الكريم · {face_name}</string>\n</resources>\n')


def build_file(path):
    spec = json.load(open(path, encoding="utf-8"))
    module = module_for(spec)
    name = spec.get("faceName", "واجهتي")
    ensure_module(module, name)
    user_bg = os.path.join(kit.ROOT, module, "src/main/res/drawable-nodpi/bg_user.webp")
    if not spec.get("backgroundImage") and os.path.exists(user_bg):
        os.remove(user_bg)
    f = build(spec, module, os.path.dirname(os.path.abspath(path)))
    kit.write(module, f"«{name}», from the face editor", "ANALOG" if spec.get("clock") == "analog" else "DIGITAL",
              use_fonts(spec, module, f.parts))
    for n in f.notes:
        print(f"note ({module}):", n)


def main():
    """One file, or with no argument every design in design/faces/."""
    if len(sys.argv) > 1:
        build_file(sys.argv[1])
        return
    folder = os.path.dirname(DEFAULT_JSON)
    for name in sorted(os.listdir(folder)):
        if name.endswith(".json"):
            build_file(os.path.join(folder, name))


if __name__ == "__main__":
    main()
