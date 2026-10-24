#!/usr/bin/env python3
"""Genera i VectorDrawable delle scene "Velluto" a partire dai sorgenti SVG.

Sorgenti: tools/scenes/src/scene_*.svg (SVG dei mockup, con segnaposto {{g1}}..{{g5}},
{{accent}} e, per la clessidra, {{glassFill}}/{{glassStroke}} che cambiano col tema).
Uscita:   core/ui/src/main/res/drawable/scene_*.xml (tema chiaro, default) e, solo per
          le scene con colori legati al tema, drawable-night/scene_*.xml (tema scuro).

Uso:  python3 tools/scenes/build_scenes.py --accent FFC83D

Solo libreria standard di Python 3. Gli XML generati non vanno modificati a mano.
"""
import argparse
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(HERE, "src")
DEFAULT_RES = os.path.normpath(os.path.join(HERE, "..", "..", "core", "ui", "src", "main", "res"))
SVG_NS = "{http://www.w3.org/2000/svg}"

# Colori che dipendono dal tema (da theme(dark, acc) di People-Promemoria).
THEME_VARS = {
    "light": {"glassFill": "rgba(255,255,255,0.45)", "glassStroke": "rgba(120,86,30,0.45)"},
    "dark": {"glassFill": "rgba(255,255,255,0.08)", "glassStroke": "rgba(255,255,255,0.55)"},
}


# ---------------------------------------------------------------- colori

def hex_rgb(h):
    h = h.strip().lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    if len(h) != 6 or not re.fullmatch(r"[0-9a-fA-F]{6}", h):
        raise ValueError("colore non valido: %r" % h)
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def js_round(v):
    # Math.round di JS: arrotonda .5 verso +inf.
    return int(math.floor(v + 0.5))


def mix(a, b, t):
    x, y = hex_rgb(a), hex_rgb(b)
    return "#" + "".join("%02X" % js_round(v + (y[i] - v) * t) for i, v in enumerate(x))


def palette(acc):
    acc = "#" + acc.strip().lstrip("#").upper()
    hex_rgb(acc)
    return {
        "accent": acc,
        "g1": mix(acc, "#FFFFFF", 0.7),
        "g2": mix(acc, "#FFFFFF", 0.22),
        "g3": acc,
        "g4": mix(acc, "#000000", 0.38),
        "g5": mix(acc, "#000000", 0.66),
    }


def parse_color(s):
    """-> (r, g, b, a) con a in 0..1, oppure None per 'none'."""
    s = s.strip()
    if s in ("none", "transparent"):
        return None if s == "none" else (0, 0, 0, 0.0)
    if s.startswith("#"):
        r, g, b = hex_rgb(s)
        return (r, g, b, 1.0)
    m = re.fullmatch(r"rgba?\(([^)]*)\)", s)
    if m:
        p = [float(v) for v in m.group(1).split(",")]
        return (int(p[0]), int(p[1]), int(p[2]), p[3] if len(p) > 3 else 1.0)
    if s == "white":
        return (255, 255, 255, 1.0)
    if s == "black":
        return (0, 0, 0, 1.0)
    raise ValueError("colore non supportato: %r" % s)


def argb(c, extra_alpha=1.0):
    r, g, b, a = c
    a = max(0.0, min(1.0, a * extra_alpha))
    ai = js_round(a * 255)
    if ai == 255:
        return "#%02X%02X%02X" % (r, g, b)
    return "#%02X%02X%02X%02X" % (ai, r, g, b)


# ---------------------------------------------------------------- numeri e affini

def fmt(v):
    if abs(v) < 5e-5:
        v = 0.0
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


IDENT = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)  # a b c d e f  (x' = a x + c y + e, y' = b x + d y + f)


def mat_mul(m, n):
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = n
    return (a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2,
            a * e2 + c * f2 + e, b * e2 + d * f2 + f)


def mat_apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


def mat_inv(m):
    a, b, c, d, e, f = m
    det = a * d - b * c
    ia, ib, ic, id_ = d / det, -b / det, -c / det, a / det
    return (ia, ib, ic, id_, -(ia * e + ic * f), -(ib * e + id_ * f))


def parse_transform(s):
    m = IDENT
    if not s:
        return m
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", s):
        v = [float(x) for x in re.split(r"[\s,]+", args.strip()) if x]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "skewX":
            t = (1, 0, math.tan(math.radians(v[0])), 1, 0, 0)
        elif name == "skewY":
            t = (1, math.tan(math.radians(v[0])), 0, 1, 0, 0)
        elif name == "matrix":
            t = tuple(v)
        else:
            raise ValueError("transform non supportata: " + name)
        m = mat_mul(m, t)
    return m


# ---------------------------------------------------------------- path

CMD_RE = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]")
NUM_RE = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def tokenize(d):
    toks, i, n = [], 0, len(d)
    while i < n:
        ch = d[i]
        if ch in " ,\t\r\n":
            i += 1
        elif CMD_RE.match(ch):
            toks.append(ch)
            i += 1
        else:
            m = NUM_RE.match(d, i)
            if not m:
                raise ValueError("path non valido vicino a %r" % d[i:i + 20])
            toks.append(m.group(0))
            i = m.end()
    return toks


def parse_path(d):
    """SVG path -> lista di segmenti assoluti: ('M',x,y) ('L',x,y) ('C',..6) ('Q',..4)
    ('A',rx,ry,rot,large,sweep,x,y) ('Z',)."""
    toks = tokenize(d)
    out, i = [], 0
    cx = cy = sx = sy = 0.0
    prev_c = prev_q = None
    cmd = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    def flag():
        # nei comandi A i flag possono essere attaccati: "a30 30 0 011 1"
        nonlocal i
        t = toks[i]
        if t[0] in "01" and len(t) > 1:
            toks[i] = t[1:]
            return int(t[0])
        i += 1
        return int(float(t))

    while i < len(toks):
        if CMD_RE.match(toks[i]):
            cmd = toks[i]
            i += 1
        elif cmd is None:
            raise ValueError("path senza comando iniziale")
        rel = cmd.islower()
        C = cmd.upper()
        ox, oy = (cx, cy) if rel else (0.0, 0.0)
        if C == "Z":
            out.append(("Z",))
            cx, cy = sx, sy
            prev_c = prev_q = None
            cmd = None
            continue
        if C == "M":
            x, y = num() + ox, num() + oy
            out.append(("M", x, y))
            cx, cy, sx, sy = x, y, x, y
            cmd = "l" if rel else "L"
            prev_c = prev_q = None
        elif C == "L":
            x, y = num() + ox, num() + oy
            out.append(("L", x, y))
            cx, cy = x, y
            prev_c = prev_q = None
        elif C == "H":
            x = num() + ox
            out.append(("L", x, cy))
            cx = x
            prev_c = prev_q = None
        elif C == "V":
            y = num() + oy
            out.append(("L", cx, y))
            cy = y
            prev_c = prev_q = None
        elif C in "CS":
            if C == "C":
                x1, y1 = num() + ox, num() + oy
            else:
                x1, y1 = (2 * cx - prev_c[0], 2 * cy - prev_c[1]) if prev_c else (cx, cy)
            x2, y2 = num() + ox, num() + oy
            x, y = num() + ox, num() + oy
            out.append(("C", x1, y1, x2, y2, x, y))
            prev_c, prev_q = (x2, y2), None
            cx, cy = x, y
        elif C in "QT":
            if C == "Q":
                x1, y1 = num() + ox, num() + oy
            else:
                x1, y1 = (2 * cx - prev_q[0], 2 * cy - prev_q[1]) if prev_q else (cx, cy)
            x, y = num() + ox, num() + oy
            out.append(("Q", x1, y1, x, y))
            prev_q, prev_c = (x1, y1), None
            cx, cy = x, y
        elif C == "A":
            rx, ry, rot = num(), num(), num()
            large, sweep = flag(), flag()
            x, y = num() + ox, num() + oy
            out.append(("A", rx, ry, rot, large, sweep, x, y))
            cx, cy = x, y
            prev_c = prev_q = None
    return out


def transform_segs(segs, m):
    if m == IDENT:
        return segs
    res = []
    for s in segs:
        k = s[0]
        if k == "A":
            raise ValueError("transform su archi non supportata")
        if k == "Z":
            res.append(s)
            continue
        pts = s[1:]
        np_ = []
        for j in range(0, len(pts), 2):
            np_.extend(mat_apply(m, pts[j], pts[j + 1]))
        res.append((k,) + tuple(np_))
    return res


def segs_to_d(segs):
    parts = []
    for s in segs:
        k = s[0]
        if k == "Z":
            parts.append("Z")
        elif k == "A":
            rx, ry, rot, la, sw, x, y = s[1:]
            parts.append("A%s,%s %s %d,%d %s,%s" % (fmt(rx), fmt(ry), fmt(rot), la, sw, fmt(x), fmt(y)))
        else:
            v = s[1:]
            parts.append(k + " ".join("%s,%s" % (fmt(v[j]), fmt(v[j + 1])) for j in range(0, len(v), 2)))
    return " ".join(parts)


def arc_points(x0, y0, rx, ry, phi, fa, fs, x, y, n=48):
    """Campiona un arco SVG (implementazione delle note F.6.5 della specifica)."""
    if rx == 0 or ry == 0 or (x0 == x and y0 == y):
        return [(x, y)]
    rx, ry = abs(rx), abs(ry)
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x0 - x) / 2, (y0 - y) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num_ = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = math.sqrt(max(0.0, num_ / den))
    if fa == fs:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    ccx = cp * cxp - sp * cyp + (x0 + x) / 2
    ccy = sp * cxp + cp * cyp + (y0 + y) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    pts = []
    for j in range(1, n + 1):
        t = t1 + dt * j / n
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((cp * ex - sp * ey + ccx, sp * ex + cp * ey + ccy))
    return pts


def flatten(segs):
    pts, cx, cy, sx, sy = [], 0.0, 0.0, 0.0, 0.0
    for s in segs:
        k = s[0]
        if k == "M":
            cx, cy = s[1], s[2]
            sx, sy = cx, cy
            pts.append((cx, cy))
        elif k == "L":
            cx, cy = s[1], s[2]
            pts.append((cx, cy))
        elif k == "C":
            x1, y1, x2, y2, x, y = s[1:]
            for j in range(1, 41):
                t = j / 40
                u = 1 - t
                pts.append((u ** 3 * cx + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x,
                            u ** 3 * cy + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y))
            cx, cy = x, y
        elif k == "Q":
            x1, y1, x, y = s[1:]
            for j in range(1, 41):
                t = j / 40
                u = 1 - t
                pts.append((u * u * cx + 2 * u * t * x1 + t * t * x, u * u * cy + 2 * u * t * y1 + t * t * y))
            cx, cy = x, y
        elif k == "A":
            pts.extend(arc_points(cx, cy, *s[1:]))
            cx, cy = s[6], s[7]
        elif k == "Z":
            cx, cy = sx, sy
    return pts


def bbox(segs):
    pts = flatten(segs)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)


def only_lines(segs):
    """True se il path e fatto solo di segmenti dritti a due punti (area di riempimento nulla)."""
    count = 0
    for s in segs:
        if s[0] == "M":
            count = 1
        elif s[0] == "L":
            count += 1
            if count > 2:
                return False
        else:
            return False
    return True


# ---------------------------------------------------------------- forme -> path

def f(el, name, default=0.0):
    v = el.get(name)
    return float(v) if v not in (None, "") else default


def rect_segs(x, y, w, h, rx=0.0, ry=None):
    ry = rx if ry is None else ry
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if rx <= 0 or ry <= 0:
        return [("M", x, y), ("L", x + w, y), ("L", x + w, y + h), ("L", x, y + h), ("Z",)]
    return [("M", x + rx, y), ("L", x + w - rx, y), ("A", rx, ry, 0, 0, 1, x + w, y + ry),
            ("L", x + w, y + h - ry), ("A", rx, ry, 0, 0, 1, x + w - rx, y + h),
            ("L", x + rx, y + h), ("A", rx, ry, 0, 0, 1, x, y + h - ry),
            ("L", x, y + ry), ("A", rx, ry, 0, 0, 1, x + rx, y), ("Z",)]


def ellipse_segs(cx, cy, rx, ry):
    return [("M", cx - rx, cy), ("A", rx, ry, 0, 1, 0, cx + rx, cy),
            ("A", rx, ry, 0, 1, 0, cx - rx, cy), ("Z",)]


def shape_segs(el):
    tag = el.tag.replace(SVG_NS, "")
    if tag == "path":
        return parse_path(el.get("d"))
    if tag == "rect":
        rx = el.get("rx")
        ry = el.get("ry")
        rxv = float(rx) if rx else (float(ry) if ry else 0.0)
        ryv = float(ry) if ry else rxv
        return rect_segs(f(el, "x"), f(el, "y"), f(el, "width"), f(el, "height"), rxv, ryv)
    if tag == "circle":
        r = f(el, "r")
        return ellipse_segs(f(el, "cx"), f(el, "cy"), r, r)
    if tag == "ellipse":
        return ellipse_segs(f(el, "cx"), f(el, "cy"), f(el, "rx"), f(el, "ry"))
    if tag == "line":
        return [("M", f(el, "x1"), f(el, "y1")), ("L", f(el, "x2"), f(el, "y2"))]
    if tag in ("polygon", "polyline"):
        v = [float(n) for n in NUM_RE.findall(el.get("points"))]
        segs = [("M", v[0], v[1])] + [("L", v[j], v[j + 1]) for j in range(2, len(v), 2)]
        if tag == "polygon":
            segs.append(("Z",))
        return segs
    raise ValueError("forma non supportata: " + tag)


# ---------------------------------------------------------------- gradienti

def num_or_pct(v, default):
    if v is None or v == "":
        return default
    v = v.strip()
    return float(v[:-1]) / 100 if v.endswith("%") else float(v)


def fix_transparent_stops(stops):
    """CSS (e i browser per SVG) interpolano in alfa premoltiplicato, Android no: uno stop
    trasparente prende l'RGB del vicino cosi la sfumatura non vira al nero."""
    out = []
    for j, (off, c) in enumerate(stops):
        if c[3] != 0:
            out.append((off, c))
            continue
        prev = stops[j - 1][1] if j > 0 else None
        nxt = stops[j + 1][1] if j + 1 < len(stops) else None
        prev = prev if prev is not None and prev[3] != 0 else None
        nxt = nxt if nxt is not None and nxt[3] != 0 else None
        if prev and nxt and prev[:3] != nxt[:3]:
            out.append((off, prev[:3] + (0.0,)))
            out.append((off, nxt[:3] + (0.0,)))
        elif prev or nxt:
            out.append((off, (prev or nxt)[:3] + (0.0,)))
        else:
            out.append((off, c))
    return out


def svg_stops(grad):
    stops, last = [], 0.0
    for st in grad:
        if st.tag.replace(SVG_NS, "") != "stop":
            continue
        off = max(last, min(1.0, num_or_pct(st.get("offset"), 0.0)))
        last = off
        c = parse_color(st.get("stop-color", "#000000"))
        op = float(st.get("stop-opacity", "1"))
        stops.append((off, c[:3] + (c[3] * op,)))
    return fix_transparent_stops(stops)


class Gradient:
    def __init__(self, kind, stops, **geo):
        self.kind, self.stops, self.geo = kind, stops, geo

    def xml(self, attr, indent, alpha=1.0):
        g = self.geo
        if self.kind == "linear":
            head = ('<gradient android:type="linear" android:startX="%s" android:startY="%s" '
                    'android:endX="%s" android:endY="%s" android:tileMode="clamp">'
                    % (fmt(g["x1"]), fmt(g["y1"]), fmt(g["x2"]), fmt(g["y2"])))
        else:
            head = ('<gradient android:type="radial" android:centerX="%s" android:centerY="%s" '
                    'android:gradientRadius="%s" android:tileMode="clamp">'
                    % (fmt(g["cx"]), fmt(g["cy"]), fmt(g["r"])))
        lines = [indent + '<aapt:attr name="android:%s">' % attr, indent + "    " + head]
        for off, c in self.stops:
            lines.append(indent + '        <item android:offset="%s" android:color="%s"/>'
                         % (fmt(off), argb(c, alpha)))
        lines += [indent + "    </gradient>", indent + "</aapt:attr>"]
        return "\n".join(lines)


def resolve_gradient(gel, box, m):
    """Gradiente SVG -> coordinate assolute. box = bbox della forma nel suo spazio locale,
    m = trasformazione locale -> viewport."""
    tag = gel.tag.replace(SVG_NS, "")
    obb = gel.get("gradientUnits", "objectBoundingBox") == "objectBoundingBox"
    if gel.get("gradientTransform"):
        raise ValueError("gradientTransform non supportata")
    bx, by, bw, bh = box
    nmat = (bw, 0, 0, bh, bx, by) if obb else IDENT
    A = mat_mul(m, nmat)
    stops = svg_stops(gel)
    if tag == "linearGradient":
        x1 = num_or_pct(gel.get("x1"), 0.0)
        y1 = num_or_pct(gel.get("y1"), 0.0)
        x2 = num_or_pct(gel.get("x2"), 1.0)
        y2 = num_or_pct(gel.get("y2"), 0.0)
        dx, dy = x2 - x1, y2 - y1
        l2 = dx * dx + dy * dy
        # t(q) = (A^-1 q - p1) . d / |d|^2  e affine in q: gradiente esatto anche con
        # bbox non quadrato o con skew (le isolinee restano quelle del browser).
        ia, ib, ic, id_, _, _ = mat_inv(A)
        gx = (ia * dx + ib * dy) / l2
        gy = (ic * dx + id_ * dy) / l2
        sx, sy = mat_apply(A, x1, y1)
        g2 = gx * gx + gy * gy
        return Gradient("linear", stops, x1=sx, y1=sy, x2=sx + gx / g2, y2=sy + gy / g2)
    if tag == "radialGradient":
        a, b, c, d, _, _ = A
        if abs(b) > 1e-9 or abs(c) > 1e-9 or abs(a - d) > 1e-6:
            raise ValueError("radialGradient su bbox non quadrato: servirebbe un'ellisse")
        if gel.get("fx") or gel.get("fy"):
            raise ValueError("fuoco del radialGradient non supportato")
        cx = num_or_pct(gel.get("cx"), 0.5)
        cy = num_or_pct(gel.get("cy"), 0.5)
        r = num_or_pct(gel.get("r"), 0.5)
        px, py = mat_apply(A, cx, cy)
        return Gradient("radial", stops, cx=px, cy=py, r=r * a)
    raise ValueError("gradiente non supportato: " + tag)


# ---------------------------------------------------------------- sfondi CSS

def split_top(s):
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur.strip())
    return parts


def css_color_and_positions(stop):
    m = re.match(r"(rgba?\([^)]*\)|#[0-9a-fA-F]{3,8}|transparent)\s*(.*)$", stop.strip())
    if not m:
        raise ValueError("stop CSS non valido: %r" % stop)
    return parse_color(m.group(1)), m.group(2).split()


def css_layers(bg, w, h):
    """Sfondo CSS a strati -> lista di Gradient, dal piu in basso al piu in alto."""
    layers = []
    for layer in split_top(bg):
        m = re.fullmatch(r"(linear|radial)-gradient\((.*)\)", layer, re.S)
        if not m:
            raise ValueError("strato CSS non supportato: %r" % layer)
        kind, args = m.group(1), split_top(m.group(2))
        if kind == "linear":
            ang = 180.0
            if args[0].endswith("deg"):
                ang = float(args.pop(0)[:-3])
            t = math.radians(ang)
            dx, dy = math.sin(t), -math.cos(t)
            length = abs(w * dx) + abs(h * dy)
            cx, cy = w / 2, h / 2
            stops = []
            for a in args:
                c, pos = css_color_and_positions(a)
                for p in pos:
                    stops.append((float(p.rstrip("%")) / 100, c))
            layers.append(Gradient("linear", fix_transparent_stops(stops),
                                   x1=cx - dx * length / 2, y1=cy - dy * length / 2,
                                   x2=cx + dx * length / 2, y2=cy + dy * length / 2))
        else:
            head = args.pop(0)
            hm = re.fullmatch(r"circle at ([\d.]+)% ([\d.]+)%", head.strip())
            if not hm:
                raise ValueError("radial-gradient CSS non supportato: %r" % head)
            cx, cy = float(hm.group(1)) / 100 * w, float(hm.group(2)) / 100 * h
            raw = []
            for a in args:
                c, pos = css_color_and_positions(a)
                for p in pos:
                    if not p.endswith("px") and p != "0":
                        raise ValueError("stop radiale atteso in px: %r" % p)
                    raw.append((float(p.rstrip("px")), c))
            r = raw[-1][0]
            stops = [(d / r, c) for d, c in raw]
            layers.append(Gradient("radial", fix_transparent_stops(stops), cx=cx, cy=cy, r=r))
    return list(reversed(layers))  # in CSS il primo strato sta in cima


# ---------------------------------------------------------------- conversione

STYLE_INHERIT = ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
                 "fill-opacity", "stroke-opacity")
SHAPES = ("path", "rect", "circle", "ellipse", "line", "polygon", "polyline")


class Converter:
    def __init__(self, root):
        self.root = root
        self.defs = {el.get("id"): el for el in root.iter() if el.get("id")}
        self.out = []

    def paint(self, value, segs, m, attr, indent, alpha):
        """-> (attributi inline, blocco aapt o None)"""
        if value is None or value == "none":
            return None, None
        um = re.fullmatch(r"url\(#([^)]+)\)", value.strip())
        if um:
            gel = self.defs[um.group(1)]
            box = bbox(segs)
            grad = resolve_gradient(gel, box, m)
            return None, grad.xml(attr, indent + "    ", alpha)
        return argb(parse_color(value), alpha), None

    def emit_shape(self, el, style, m, indent):
        tag = el.tag.replace(SVG_NS, "")
        local = shape_segs(el)
        em = mat_mul(m, parse_transform(el.get("transform")))
        segs = transform_segs(local, em)
        op = float(el.get("opacity", "1"))
        fill = style.get("fill")
        if fill is None:
            # default SVG: nero; sui path fatti solo di linee non disegna nulla
            fill = "none" if (tag in ("line", "polyline") or only_lines(local)) else "#000000"
        stroke = style.get("stroke")
        name = el.get("data-name")
        pivot = el.get("data-pivot")
        ind = indent
        if name:
            gattrs = ' android:name="%s_group"' % name
            if pivot:
                px, py = [float(v) for v in pivot.split()]
                gattrs += ' android:pivotX="%s" android:pivotY="%s"' % (fmt(px), fmt(py))
            self.out.append(indent + "<group%s>" % gattrs)
            ind = indent + "    "
        fop = float(style.get("fill-opacity", "1")) * op
        sop = float(style.get("stroke-opacity", "1")) * op
        attrs = []
        if name:
            attrs.append('android:name="%s"' % name)
        attrs.append('android:pathData="%s"' % segs_to_d(segs))
        blocks = []
        # le trasformazioni sono gia applicate alle coordinate: i gradienti in
        # objectBoundingBox usano il bbox locale e la stessa matrice.
        fc, fb = self.paint(fill, local, em, "fillColor", ind, fop)
        if fc:
            attrs.append('android:fillColor="%s"' % fc)
        if fb:
            blocks.append(fb)
        if stroke and stroke != "none":
            sc, sb = self.paint(stroke, local, em, "strokeColor", ind, sop)
            if sc:
                attrs.append('android:strokeColor="%s"' % sc)
            if sb:
                blocks.append(sb)
            sw = float(style.get("stroke-width", "1"))
            sx = math.hypot(em[0], em[1])
            attrs.append('android:strokeWidth="%s"' % fmt(sw * sx))
            cap = style.get("stroke-linecap")
            if cap and cap != "butt":
                attrs.append('android:strokeLineCap="%s"' % cap)
            join = style.get("stroke-linejoin")
            if join and join != "miter":
                attrs.append('android:strokeLineJoin="%s"' % join)
        sep = "\n" + ind + "    "
        if blocks:
            self.out.append(ind + "<path" + sep + sep.join(attrs) + ">")
            self.out.extend(blocks)
            self.out.append(ind + "</path>")
        else:
            self.out.append(ind + "<path" + sep + sep.join(attrs) + " />")
        if name:
            self.out.append(indent + "</group>")

    def walk(self, el, style, m, indent):
        for child in el:
            tag = child.tag.replace(SVG_NS, "")
            if tag in ("defs", "title", "desc"):
                continue
            st = dict(style)
            for k in STYLE_INHERIT:
                if child.get(k) is not None:
                    st[k] = child.get(k)
            if tag == "g":
                gm = mat_mul(m, parse_transform(child.get("transform")))
                if child.get("opacity"):
                    raise ValueError("opacity sui gruppi non supportata")
                clip = child.get("clip-path")
                if clip:
                    cid = re.fullmatch(r"url\(#([^)]+)\)", clip).group(1)
                    csegs = []
                    for ce in self.defs[cid]:
                        csegs += transform_segs(shape_segs(ce), mat_mul(m, parse_transform(ce.get("transform"))))
                    self.out.append(indent + "<group>")
                    self.out.append(indent + '    <clip-path android:pathData="%s" />' % segs_to_d(csegs))
                    self.walk(child, st, gm, indent + "    ")
                    self.out.append(indent + "</group>")
                else:
                    self.walk(child, st, gm, indent)
            elif tag in SHAPES:
                self.emit_shape(child, st, m, indent)
            else:
                raise ValueError("elemento non supportato: " + tag)


def render(template, values, accent):
    def sub(mt):
        k = mt.group(1)
        if k not in values:
            raise KeyError("segnaposto sconosciuto: " + k)
        return values[k]

    text = re.sub(r"\{\{(\w+)\}\}", sub, template)
    root = ET.fromstring(text)
    vb = [float(v) for v in root.get("viewBox").split()]
    w, h = vb[2], vb[3]
    conv = Converter(root)
    ind = "    "
    bg = root.get("data-bg")
    if bg:
        full = segs_to_d(rect_segs(0, 0, w, h))
        for j, g in enumerate(css_layers(bg, w, h)):
            conv.out.append(ind + "<path")
            conv.out.append(ind + '    android:name="bg_%d"' % j)
            conv.out.append(ind + '    android:pathData="%s">' % full)
            conv.out.append(g.xml("fillColor", ind + "    "))
            conv.out.append(ind + "</path>")
    conv.walk(root, {}, IDENT, ind)
    head = ['<?xml version="1.0" encoding="utf-8"?>',
            # niente doppio trattino dentro un commento XML: aapt2 lo rifiuta
            "<!-- Generato da tools/scenes/build_scenes.py (accento %s). Non modificare a mano:"
            " cambia il sorgente in tools/scenes/src e rilancia lo script. -->" % accent.lstrip("#"),
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            '    xmlns:aapt="http://schemas.android.com/aapt"',
            '    android:width="%sdp"' % fmt(w),
            '    android:height="%sdp"' % fmt(h),
            '    android:viewportWidth="%s"' % fmt(w),
            '    android:viewportHeight="%s">' % fmt(h)]
    return "\n".join(head + conv.out + ["</vector>", ""])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--accent", default="FFC83D", help="colore d'accento, es. FFC83D")
    ap.add_argument("--res", default=DEFAULT_RES, help="cartella res di destinazione")
    ap.add_argument("--only", nargs="*", help="genera solo queste scene (nomi senza estensione)")
    args = ap.parse_args()
    pal = palette(args.accent)
    names = sorted(n[:-4] for n in os.listdir(SRC_DIR) if n.startswith("scene_") and n.endswith(".svg"))
    if args.only:
        names = [n for n in names if n in args.only]
    written = []
    for name in names:
        tpl = open(os.path.join(SRC_DIR, name + ".svg"), encoding="utf-8").read()
        themed = any(k in tpl for k in ("{{%s}}" % v for v in THEME_VARS["light"]))
        variants = [("drawable", "light")] + ([("drawable-night", "dark")] if themed else [])
        for folder, theme in variants:
            vals = dict(pal)
            vals.update(THEME_VARS[theme])
            xml = render(tpl, vals, pal["accent"])
            d = os.path.join(args.res, folder)
            os.makedirs(d, exist_ok=True)
            p = os.path.join(d, name + ".xml")
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(xml)
            written.append(p)
    print("accento %s  g1 %s  g2 %s  g4 %s  g5 %s" % (pal["accent"], pal["g1"], pal["g2"], pal["g4"], pal["g5"]))
    for p in written:
        print("scritto", os.path.relpath(p))


if __name__ == "__main__":
    sys.exit(main())
