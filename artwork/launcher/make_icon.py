#!/usr/bin/env python3
"""Generates the Updraft launcher icon (3D open book lifted by rising air) from one shape list.

One drawing feeds two outputs so the preview and the shipped icon cannot drift apart:
  * SVG previews (``--preview DIR``) with adaptive-icon masks, for choosing a variant;
  * Android vector layers (``--android RES_DIR --variant NAME``): background, foreground, monochrome.

The artwork is original; no third-party icon is used. Everything stays inside the 66 dp-diameter
adaptive-icon safe circle (centre 54,54; radius 33) so launchers can mask it freely.
"""
import argparse
import os
import re

W = 108.0

# ---------------------------------------------------------------- shape model
# A paint is "#RRGGBB", "#AARRGGBB" or ("linear", x1, y1, x2, y2, [(offset, colour), ...])
# or ("radial", cx, cy, r, [(offset, colour), ...]).


def lin(x1, y1, x2, y2, *stops):
    return ("linear", x1, y1, x2, y2, list(stops))


def rad(cx, cy, r, *stops):
    return ("radial", cx, cy, r, list(stops))


def parse(colour):
    c = colour.lstrip("#")
    if len(c) == 8:
        return c[2:], int(c[:2], 16) / 255.0
    return c, 1.0


class Shape:
    def __init__(self, d, fill=None, stroke=None, width=0.0, cap="round", even_odd=False):
        self.d, self.fill, self.stroke, self.width, self.cap, self.even_odd = d, fill, stroke, width, cap, even_odd


class Group:
    def __init__(self, shapes, rotate=0.0, pivot=(54.0, 54.0), dx=0.0, dy=0.0):
        self.shapes, self.rotate, self.pivot, self.dx, self.dy = shapes, rotate, pivot, dx, dy


# ------------------------------------------------------------------- artwork
def book_shapes(p):
    """Open book seen from the front: cover rim gives the thickness, pages curve down into the spine."""
    cover = ("M24.5,45 C36,42 48,43.5 54,48.5 C60,43.5 72,42 83.5,45 L83.5,70.5 "
             "C71,68.5 60,70 54,77 C48,70 37,68.5 24.5,70.5 Z")
    left = "M54,72 L54,50 C48,45.5 38,44 27,46 L27,67 C38,65 48,66.5 54,72 Z"
    right = "M54,72 L54,50 C60,45.5 70,44 81,46 L81,67 C70,65 60,66.5 54,72 Z"
    return [
        Shape(cover, lin(0, 44, 0, 78, (0, p["cover_top"]), (1, p["cover_bottom"]))),
        Shape(left, lin(27, 0, 54, 0, (0, p["page_light"]), (0.72, p["page_mid"]), (1, p["page_dark"]))),
        Shape(right, lin(81, 0, 54, 0, (0, p["page_light"]), (0.72, p["page_mid"]), (1, p["page_dark"]))),
        # stacked page edges under each page: this is what makes the cover read as a thick block
        Shape("M27,68.4 C38,66.4 48,68 54,73.6 M81,68.4 C70,66.4 60,68 54,73.6", None, p["stack"], 0.6),
        Shape("M27,69.8 C38,67.8 48,69.4 54,75 M81,69.8 C70,67.8 60,69.4 54,75", None, p["stack"], 0.6),
        # soft light along the top edge of both pages sells the paper thickness
        Shape("M27,46.2 C38,44.2 48,45.7 54,50.2 C60,45.7 70,44.2 81,46.2", None, p["edge"], 0.9),
        # text lines
        Shape("M32,52 C38,51 44,52 49,55 M32,57 C38,56 44,57 49,60 M32,62 C38,61 43,62 47,64",
              None, p["ink"], 0.9),
        Shape("M76,52 C70,51 64,52 59,55 M76,57 C70,56 64,57 59,60 M76,62 C70,61 65,62 61,64",
              None, p["ink"], 0.9),
    ]


def shadow_shapes(p, cy=82.0):
    out = []
    for rx, a in ((30, 0.05), (25, 0.06), (20, 0.08), (15, 0.10)):
        out.append(Shape(f"M{54 - rx},{cy} a{rx},{rx * 0.22:.2f} 0 1,0 {2 * rx},0 a{rx},{rx * 0.22:.2f} 0 1,0 {-2 * rx},0 Z",
                         "#%02X%s" % (int(a * 255), p["shadow"].lstrip("#"))))
    return out


LEAF = "M54,50 C60,43 71,40.5 81,43.5 C78.5,47.5 70,50 54,50 Z"


def wind_up(p, gust=True):
    """Wind riffles the pages: three sheets fan up from the spine; two gusts with curled tails blow in from the left."""
    items = []
    for angle, a_top, a_end in ((-58, "#B3FFFFFF", "#80EFE2C2"), (-34, "#E6FFFFFF", "#B3EFE2C2"), (-11, "#FFFFFFFF", "#F2EBDDB8")):
        sheet = Shape(LEAF, lin(54, 50, 81, 44, (0, p["flying_b"]), (0.55, a_top), (1, a_end)))
        items.append(Group([sheet, Shape("M54,50 C60,43 71,40.5 81,43.5", None, p["edge"], 0.7)], rotate=angle, pivot=(54, 50)))
    if gust:
        g = lin(24, 0, 42, 0, (0, p["wind_far"]), (1, p["wind_near"]))
        items += [Shape("M28,36 H40 C43.5,36 43.5,31 40,31", None, g, 1.7),
                  Shape("M31,42 H45 C48.5,42 48.5,37.5 45.2,37.5", None, g, 1.7)]
    return items


LIGHT = dict(cover_top="#B98B3C", cover_bottom="#6B4A1C", page_light="#FFFDF7", page_mid="#F1E7D0",
             page_dark="#D9C8A2", edge="#FFFFFF", ink="#B89E6E", shadow="#5A3E14",
             wind_near="#B8893A", wind_far="#22B8893A", stack="#C7B07F", flying_a="#FFFFFF", flying_b="#EBDDBB")
DARK = dict(cover_top="#F2D48A", cover_bottom="#B88A3A", page_light="#FFFDF7", page_mid="#F3E8CF",
            page_dark="#D6C39A", edge="#FFFFFF", ink="#B9A275", shadow="#000000",
            wind_near="#F7E3AE", wind_far="#22F7E3AE", stack="#C9AE78", flying_a="#FFFFFF", flying_b="#F0E2BF")


def variants():
    bg_light = rad(54, 40, 80, (0, "#FFFCF4"), (0.6, "#F8F4EC"), (1, "#E9DDC2"))
    bg_dark = rad(54, 34, 84, (0, "#A57B38"), (0.55, "#6E4D1D"), (1, "#3B2609"))

    def art(pal, gust):
        return [Group(shadow_shapes(pal) + book_shapes(pal) + wind_up(pal, gust), dy=3)]

    return {
        "A": dict(bg=bg_light, fg=art(LIGHT, True)),
        "B": dict(bg=bg_dark, fg=art(DARK, True)),
        "C": dict(bg=bg_dark, fg=art(DARK, False)),
    }


def mono_layer(variant):
    """Single-colour silhouette for themed icons: cover rim with the pages cut out, plus the wind."""
    p = LIGHT
    cover = ("M24.5,45 C36,42 48,43.5 54,48.5 C60,43.5 72,42 83.5,45 L83.5,70.5 "
             "C71,68.5 60,70 54,77 C48,70 37,68.5 24.5,70.5 Z")
    pages = ("M54,72 L54,50 C48,45.5 38,44 27,46 L27,67 C38,65 48,66.5 54,72 Z "
             "M54,72 L54,50 C60,45.5 70,44 81,46 L81,67 C70,65 60,66.5 54,72 Z")
    book = Shape(cover + " " + pages, "#000000", even_odd=True)
    solid = "#000000"
    fan = [Group([Shape(LEAF, None, solid, 1.4)], rotate=a, pivot=(54, 50)) for a in (-58, -34, -11)]
    gust = [Shape("M28,36 H40 C43.5,36 43.5,31 40,31", None, solid, 1.9),
            Shape("M31,42 H45 C48.5,42 48.5,37.5 45.2,37.5", None, solid, 1.9)]
    return [Group([book] + fan + (gust if variant != "C" else []), dy=3)]


# -------------------------------------------------------------------- output
class Ids:
    def __init__(self):
        self.n = 0

    def next(self):
        self.n += 1
        return f"g{self.n}"


def svg_paint(paint, ids, defs):
    if paint is None:
        return "none", ""
    if isinstance(paint, str):
        hexa, a = parse(paint)
        return "#" + hexa, f' opacity="{a:.3f}"' if a < 1 else ""
    gid = ids.next()
    if paint[0] == "linear":
        _, x1, y1, x2, y2, stops = paint
        head = f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
    else:
        _, cx, cy, r, stops = paint
        head = f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="{r}">'
    body = "".join('<stop offset="%s" stop-color="#%s" stop-opacity="%.3f"/>' % ((o,) + parse(c)) for o, c in stops)
    defs.append(head + body + ("</linearGradient>" if paint[0] == "linear" else "</radialGradient>"))
    return f"url(#{gid})", ""


def svg_shapes(items, ids, defs):
    out = []
    for it in items:
        if isinstance(it, Group):
            inner = svg_shapes(it.shapes, ids, defs)
            out.append(f'<g transform="translate({it.dx} {it.dy}) rotate({it.rotate} {it.pivot[0]} {it.pivot[1]})">{inner}</g>')
            continue
        fill, fo = svg_paint(it.fill, ids, defs)
        stroke, so = svg_paint(it.stroke, ids, defs)
        attrs = f'd="{it.d}" fill="{fill}"'
        if it.even_odd:
            attrs += ' fill-rule="evenodd"'
        if it.stroke is not None:
            attrs += f' stroke="{stroke}" stroke-width="{it.width}" stroke-linecap="{it.cap}" stroke-linejoin="round"'
        out.append(f"<path {attrs}{fo or so}/>")
    return "".join(out)


def svg_icon(v, mask):
    ids, defs = Ids(), []
    bg = svg_paint(v["bg"], ids, defs)[0]
    fg = svg_shapes(v["fg"], ids, defs)
    clip = {"circle": '<circle cx="54" cy="54" r="54"/>',
            "squircle": '<rect x="0" y="0" width="108" height="108" rx="30"/>',
            "none": '<rect x="0" y="0" width="108" height="108"/>'}[mask]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">'
            f'<defs>{"".join(defs)}<clipPath id="m">{clip}</clipPath></defs>'
            f'<g clip-path="url(#m)"><rect width="108" height="108" fill="{bg}"/>{fg}</g></svg>')


def xml_paint(paint, attr, indent):
    """Returns the path attribute (solid) or a nested <aapt:attr> gradient."""
    pad = " " * indent
    if isinstance(paint, str):
        hexa, a = parse(paint)
        argb = ("#%02X%s" % (round(a * 255), hexa)) if a < 1 else "#" + hexa
        return f'android:{attr}="{argb}"', ""
    if paint[0] == "linear":
        _, x1, y1, x2, y2, stops = paint
        g = (f'{pad}<aapt:attr name="android:{attr}">\n{pad}    <gradient android:type="linear" android:startX="{x1}" '
             f'android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">\n')
    else:
        _, cx, cy, r, stops = paint
        g = (f'{pad}<aapt:attr name="android:{attr}">\n{pad}    <gradient android:type="radial" android:centerX="{cx}" '
             f'android:centerY="{cy}" android:gradientRadius="{r}">\n')
    for o, c in stops:
        hexa, a = parse(c)
        g += f'{pad}        <item android:offset="{o}" android:color="#{round(a * 255):02X}{hexa}" />\n'
    g += f"{pad}    </gradient>\n{pad}</aapt:attr>\n"
    return None, g


def xml_shapes(items, indent=4):
    pad = " " * indent
    out = ""
    for it in items:
        if isinstance(it, Group):
            out += (f'{pad}<group android:rotation="{it.rotate}" android:pivotX="{it.pivot[0]}" '
                    f'android:pivotY="{it.pivot[1]}" android:translateX="{it.dx}" android:translateY="{it.dy}">\n')
            out += xml_shapes(it.shapes, indent + 4) + f"{pad}</group>\n"
            continue
        attrs, nested = [f'android:pathData="{it.d}"'], ""
        if it.fill is not None:
            a, n = xml_paint(it.fill, "fillColor", indent + 4)
            attrs.append(a) if a else None
            nested += n
        if it.stroke is not None:
            a, n = xml_paint(it.stroke, "strokeColor", indent + 4)
            attrs.append(a) if a else None
            nested += n
            attrs += [f'android:strokeWidth="{it.width}"', f'android:strokeLineCap="{it.cap}"',
                      'android:strokeLineJoin="round"']
        if it.even_odd:
            attrs.append('android:fillType="evenOdd"')
        if nested:
            out += f"{pad}<path " + " ".join(attrs) + f">\n{nested}{pad}</path>\n"
        else:
            out += f"{pad}<path " + " ".join(attrs) + " />\n"
    return out


HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by artwork/launcher/make_icon.py; edit that script, not this file. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    xmlns:aapt="http://schemas.android.com/aapt"\n'
          '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def write_android(res, name):
    v = variants()[name]
    drawable = os.path.join(res, "drawable")
    os.makedirs(drawable, exist_ok=True)
    bg = xml_shapes([Shape("M0,0 H108 V108 H0 Z", v["bg"])])
    open(os.path.join(drawable, "ic_launcher_background.xml"), "w").write(HEADER + bg + "</vector>\n")
    open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w").write(HEADER + xml_shapes(v["fg"]) + "</vector>\n")
    open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w").write(HEADER + xml_shapes(mono_layer(name)) + "</vector>\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--android")
    ap.add_argument("--variant", default="A")
    a = ap.parse_args()
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        for name, v in variants().items():
            for mask in ("squircle", "circle", "none"):
                open(os.path.join(a.preview, f"icon-{name}-{mask}.svg"), "w").write(svg_icon(v, mask))
    if a.android:
        write_android(a.android, a.variant)


if __name__ == "__main__":
    main()
