#!/usr/bin/env python3
"""«القرآن الكريم»: one watch face holding several studio designs (design/faces/<id>.json),
picked from the face's own settings («التصميم»), so the watch installs one package once.

Each design is built by make_custom_face.build() exactly as its own face, then:
- everything it draws goes into <ListOption id="<design>"> of one <ListConfiguration id="design">;
- its complication slots are pooled: the format allows at most 8 slots, only at the top of
  the Scene, and never inside a ListOption (a face built that way sent the watch into a reboot
  loop). So there is ONE slot per kind of data (battery, steps, ...), covering the whole face,
  and inside it each design draws that data in its own place, chosen by the same
  ListConfiguration. A design that shows a value twice draws it twice from the one slot.
- the slots cannot follow each design's layout for taps, so the data slots take no taps; the
  slot over the middle of the dial opens the mushaf in every design.

  python3 scripts/wff/make_unified_face.py            # the default design list below
"""
import copy
import json
import os
import shutil
import xml.etree.ElementTree as ET

import kit
import make_custom_face as mcf

MODULE = "watchface-unified"
PACKAGE = "com.quran.watch8.watchface.unified"
DESIGNS = ["chrono", "heritage", "halfs", "lcd", "dash", "petals", "crystal", "asala", "sport"]
FACES = os.path.join(kit.ROOT, "design/faces")

# One slot per source; the order is the slot order. key -> (slot id, supported types)
SLOTS = [
    ("WATCH_BATTERY", "battery"),
    ("STEP_COUNT", "steps"),
    ("HEART_RATE", "heart"),
    ("NextPrayer", "next"),
    ("HijriDate", "hijri"),
    ("GregorianDate", "date"),
    ("KhatmaProgress", "khatma"),
    ("QuranShortcut", "mushaf"),
]
# Sources a design may name that have no slot here, and what is drawn instead.
REPLACE = {"prev": "temp"}       # «مضى على الصلاة» -> the temperature (needs no slot)
LABELS = {"temp": "الحرارة"}
MUSHAF_TAP = (135, 135, 180, 180)   # the middle of the dial, in every design


def source_of(slot):
    pp = slot.find("DefaultProviderPolicy")
    prov = pp.get("primaryProvider") or ""
    if prov:
        return prov.rsplit(".", 1)[-1].replace("ComplicationService", "")
    return pp.get("defaultSystemProvider")


def spec_for(fid):
    spec = json.load(open(os.path.join(FACES, f"{fid}.json"), encoding="utf-8"))
    for item in (spec.get("pods") or {}).get("items", []):
        if item.get("content") in REPLACE:
            item["content"] = REPLACE[item["content"]]
            item["label"] = LABELS.get(item["content"], "")
    for seg in spec.get("segments", []):
        seg["content"] = REPLACE.get(seg.get("content"), seg.get("content"))
    return spec


def ensure_module():
    dst = os.path.join(kit.ROOT, MODULE)
    if not os.path.exists(dst):
        shutil.copytree(os.path.join(kit.ROOT, "watchface-custom"), dst, ignore=shutil.ignore_patterns("build"))
        for rel in ("build.gradle.kts", "src/main/AndroidManifest.xml"):
            path = os.path.join(dst, rel)
            t = open(path, encoding="utf-8").read().replace("com.quran.watch8.watchface.custom", PACKAGE)
            open(path, "w", encoding="utf-8").write(t)
    return dst


def build_all(designs=DESIGNS):
    dst = ensure_module()
    font_dir = os.path.join(dst, "src/main/res/font")
    for name in os.listdir(font_dir):
        if name not in ("plex_bold.ttf", "plex_semibold.ttf"):
            os.remove(os.path.join(font_dir, name))
    drawn = {}                       # design -> [part xml]
    pooled = {k: {} for k, _ in SLOTS}   # source -> design -> [slot element]
    names = {}
    for fid in designs:
        spec = spec_for(fid)
        names[fid] = spec.get("faceName", fid)
        f = mcf.build(spec, MODULE, FACES)
        if spec.get("backgroundImage"):
            raise ValueError(f"{fid}: a photo background is not supported in the unified face")
        parts = mcf.use_fonts(spec, MODULE, f.parts, keep=True)
        mcf.copy_icons(f.icons, MODULE)
        drawn[fid] = []
        for p in parts:
            if p.lstrip().startswith("<ComplicationSlot"):
                el = ET.fromstring(p)
                src = source_of(el)
                if src not in pooled:
                    raise ValueError(f"{fid}: no unified slot for {src}")
                pooled[src].setdefault(fid, []).append(el)
            else:
                drawn[fid].append(p)
    return drawn, pooled, names


def instance(el, ctype, n):
    """One design's drawing of a slot's data: its Complication of this type, moved to where
    the design put the slot, hidden on the always-on screen if the slot was."""
    comp = next((c for c in el.findall("Complication") if c.get("type") == ctype), None)
    if comp is None:
        return ""
    x, y, w, h = (el.get(k) for k in ("x", "y", "width", "height"))
    hide = any(v.get("mode") == "AMBIENT" and v.get("target") == "alpha" and v.get("value") == "0"
               for v in el.findall("Variant"))
    inner = "".join(ET.tostring(c, encoding="unicode") for c in comp)
    amb = '<Variant mode="AMBIENT" target="alpha" value="0"/>' if hide else ""
    return f'<Group name="i{n}" x="{x}" y="{y}" width="{w}" height="{h}">{amb}{inner}</Group>'


def unified_slot(sid, src, per_design, designs):
    if not per_design:
        return None
    types = []
    for els in per_design.values():
        for el in els:
            for t in el.get("supportedTypes").split():
                if t not in types:
                    types.append(t)
    first = next(iter(per_design.values()))[0]
    policy = ET.tostring(first.find("DefaultProviderPolicy"), encoding="unicode")
    ptype = first.find("DefaultProviderPolicy").get("defaultSystemProviderType")
    if ptype not in types:
        types.insert(0, ptype)
    if src == "QuranShortcut":
        x, y, w, h = MUSHAF_TAP
        bound = f'<BoundingBox x="{x}" y="{y}" width="{w}" height="{h}"/>'
    else:
        # A dot in the square's corner, off the round screen: these slots draw, they take no taps.
        bound = '<BoundingBox x="0" y="0" width="2" height="2"/>'
    comps = []
    for t in types:
        opts = []
        for fid in designs:
            body = "".join(instance(el, t, n) for n, el in enumerate(per_design.get(fid, [])))
            opts.append(f'<ListOption id="{fid}"><Group name="{src}_{fid}" x="0" y="0" width="450" height="450">{body}</Group></ListOption>')
        comps.append(f'<Complication type="{t}"><Group name="{src}_{t}" x="0" y="0" width="450" height="450">'
                     f'<ListConfiguration id="design">{"".join(opts)}</ListConfiguration></Group></Complication>')
    return (f'<ComplicationSlot slotId="{sid}" x="0" y="0" width="450" height="450" supportedTypes="{" ".join(types)}" '
            f'isCustomizable="FALSE">{policy.strip()}{bound}{"".join(comps)}</ComplicationSlot>')


def previews(dst, designs):
    """The design list's pictures: each design's own picker preview, made smaller."""
    from PIL import Image
    out = os.path.join(dst, "src/main/res/drawable-nodpi")
    for fid in designs:
        src = os.path.join(kit.ROOT, f"watchface-c-{fid}/src/main/res/drawable-nodpi/preview.png")
        Image.open(src).resize((180, 180), Image.LANCZOS).save(os.path.join(out, f"design_{fid}.png"))


def main(designs=DESIGNS):
    drawn, pooled, names = build_all(designs)
    dst = os.path.join(kit.ROOT, MODULE)
    previews(dst, designs)
    default = designs[0]
    strings = ['    <string name="face_name">القرآن الكريم</string>', '    <string name="design_label">التصميم</string>']
    strings += [f'    <string name="design_{fid}">{names[fid]}</string>' for fid in designs]
    open(os.path.join(dst, "src/main/res/values/strings.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(strings) + "\n</resources>\n")
    config = ('<UserConfigurations><ListConfiguration id="design" displayName="design_label" '
              f'defaultValue="{default}">'
              + "".join(f'<ListOption id="{fid}" displayName="design_{fid}" icon="design_{fid}"/>' for fid in designs)
              + '</ListConfiguration></UserConfigurations>')
    scene = ['<ListConfiguration id="design">'
             + "".join(f'<ListOption id="{fid}"><Group name="design_{fid}" x="0" y="0" width="450" height="450">'
                       + "".join(drawn[fid]) + '</Group></ListOption>' for fid in designs)
             + '</ListConfiguration>']
    sid = 101
    for src, _ in SLOTS:
        s = unified_slot(sid, src, pooled[src], designs)
        if s:
            scene.append(s)
            sid += 1
    out = os.path.join(dst, "src/main/res/raw/watchface.xml")
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- «القرآن الكريم»: the studio designs in one face. Generated by scripts/wff/make_unified_face.py. -->\n'
           '<WatchFace width="450" height="450" clipShape="CIRCLE">\n'
           '<Metadata key="CLOCK_TYPE" value="ANALOG"/>\n<Metadata key="PREVIEW_TIME" value="10:08:36"/>\n'
           + config + '\n<Scene backgroundColor="#FF000000">\n' + "\n".join(scene) + '\n</Scene>\n</WatchFace>\n')
    open(out, "w", encoding="utf-8").write(xml)
    n = xml.count("<ComplicationSlot ")
    assert n <= 8, n
    print(f"wrote {out} ({n} slots, {len(xml) // 1024} KB, designs: {', '.join(designs)})")


if __name__ == "__main__":
    main()
