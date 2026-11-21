# Podgląd panelu QS8 (PainelDeAjuste + LegendaTouch) w PIL, ta sama geometria co w Kotlinie.
# Nie jest zrzutem z Questa: fonty Roboto z Android Studio, przybliżone wypełnienia.
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/Applications/Android Studio.app/Contents/plugins/design-tools/resources/layoutlib/data/fonts/Roboto-Regular.ttf"
_cache = {}
def font(size, weight=400):
    k = (round(size, 1), weight)
    if k not in _cache:
        f = ImageFont.truetype(FONT, int(round(size)))
        f.set_variation_by_axes([weight, 100, 0])
        _cache[k] = f
    return _cache[k]

def rgb(r, g, b, a=255): return (r, g, b, a)
TEXTO = rgb(238, 241, 247); SUAVE = rgb(142, 152, 173); CABECALHO = rgb(125, 135, 156)
DESTAQUE = rgb(140, 188, 255); AVISO = rgb(217, 184, 112)
CRUZ = rgb(127, 180, 238); CIRC = rgb(255, 122, 122); QUAD = rgb(230, 144, 207); TRI = rgb(67, 214, 178)
BOM = rgb(95, 211, 141); ATENCAO = rgb(255, 199, 102); RUIM = rgb(255, 110, 110)

class C:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    def layer(self):
        return Image.new("RGBA", self.img.size, (0, 0, 0, 0))
    def comp(self, l): self.img = Image.alpha_composite(self.img, l)
    def rrect(self, box, r, fill=None, outline=None, width=1):
        l = self.layer(); d = ImageDraw.Draw(l)
        d.rounded_rectangle([round(v) for v in box], radius=r, fill=fill, outline=outline, width=width); self.comp(l)
    def rect(self, box, fill):
        l = self.layer(); ImageDraw.Draw(l).rectangle([round(v) for v in box], fill=fill); self.comp(l)
    def circle(self, cx, cy, r, fill=None, outline=None, width=1):
        l = self.layer(); ImageDraw.Draw(l).ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=round(width)); self.comp(l)
    def line(self, a, b, fill, width):
        l = self.layer(); ImageDraw.Draw(l).line([a, b], fill=fill, width=round(width)); self.comp(l)
    def poly(self, pts, outline, width):
        l = self.layer(); ImageDraw.Draw(l).line(pts + [pts[0]], fill=outline, width=round(width), joint="curve"); self.comp(l)
    def text(self, t, x, y, size, color, weight=400, anchor="ls"):
        l = self.layer(); ImageDraw.Draw(l).text((x, y), t, font=font(size, weight), fill=color, anchor=anchor); self.comp(l)

def measure(t, size, weight=400): return font(size, weight).getlength(t)

def fundo(c, box):
    x0, y0, x1, y1 = box
    l = c.layer(); d = ImageDraw.Draw(l)
    for y in range(int(y0), int(y1)):
        t = (y - y0) / (y1 - y0)
        col = tuple(int(a + (b - a) * t) for a, b in zip((14, 26, 50), (6, 9, 16))) + (240,)
        d.line([(x0, y), (x1, y)], fill=col)
    m = Image.new("L", c.img.size, 0); ImageDraw.Draw(m).rounded_rectangle(box, 36, fill=255)
    c.img.paste(Image.alpha_composite(c.img, l), (0, 0), m)
    c.rrect(box, 36, outline=(255, 255, 255, 60), width=2)

# ---------- LegendaTouch (kopia logiki z Kotlina) ----------
PRZES = 26; LEWA = -10; PRAWA = 104
def quest_dla(b):
    m = {"Cruz": ("L", "A"), "Circulo": ("L", "B"), "Quadrado": ("L", "X"), "Triangulo": ("L", "Y"),
         "DirV": ("G",), "DirH": ("G",)}
    if b in m: return m[b]
    if b.startswith("N:"):
        t = b[2:].replace(" ", "")
        return {"L1R1": ("Q", "Grip"), "L2": ("Q", "LT"), "R2": ("Q", "RT"), "Share": ("K", "L"), "Options": ("K", "R")}.get(t)
    return None
def polowa(f): return max(16, f * 0.85)
def szer_pig(t, f, mn): return max(mn, measure(t, f, 700) + f * 0.9)
def szer(e, f):
    k = e[0]
    if k in ("L", "G"): return 32
    if k == "K": return 44
    if k == "M": return 40
    if k == "Q": return szer_pig(e[1], f, 32)
    if k == "/": return 20
    b = e[1]
    if b.startswith("N:"): return szer_pig(b[2:], f, 36)
    return 38
QT = (255, 255, 255, 64)
def rysuj(c, e, cx, cy, f):
    k = e[0]
    if k == "L":
        c.circle(cx, cy, 16, fill=QT); c.text(e[1], cx, cy + 7, 19, TEXTO, 700, "ms")
    elif k == "G":
        c.circle(cx, cy, 16, fill=QT); c.circle(cx, cy, 10, outline=TEXTO, width=2.5); c.circle(cx, cy, 4, fill=TEXTO)
    elif k == "K":
        g = cx - 6
        c.circle(g, cy, 16, fill=QT); c.circle(g, cy, 10, outline=TEXTO, width=2.5); c.circle(g, cy, 6, fill=TEXTO)
        c.text(e[1], cx + 16, cy + 6, 16, TEXTO, 700, "ms")
    elif k == "M":
        c.rrect([cx - 20, cy - 16, cx + 20, cy + 16], 16, fill=QT)
        for dy in (-7, 0, 7): c.line((cx - 9, cy + dy), (cx + 9, cy + dy), TEXTO, 3)
    elif k == "Q":
        s = szer_pig(e[1], f, 32); h = polowa(f)
        c.rrect([cx - s / 2, cy - h, cx + s / 2, cy + h], h, fill=QT); c.text(e[1], cx, cy + f * 0.36, f, TEXTO, 700, "ms")
    elif k == "/":
        c.text("/", cx, cy + 7, 22, SUAVE, 400, "ms")
    else:
        ps(c, e[1], cx, cy, f)
def ps(c, b, cx, cy, f):
    if b.startswith("N:"):
        t = b[2:]; s = szer_pig(t, f, 36); h = polowa(f)
        c.rrect([cx - s / 2, cy - h, cx + s / 2, cy + h], 9, fill=(255, 255, 255, 26)); c.text(t, cx, cy + f * 0.36, f, TEXTO, 700, "ms")
    elif b in ("DirV", "DirH"):
        a, d = 6.5, 18
        c.rect([cx - a, cy - d, cx + a, cy + d], (255, 255, 255, 56)); c.rect([cx - d, cy - a, cx + d, cy + a], (255, 255, 255, 56))
        if b == "DirV": c.rect([cx - a, cy - d, cx + a, cy - a], TEXTO); c.rect([cx - a, cy + a, cx + a, cy + d], TEXTO)
        else: c.rect([cx - d, cy - a, cx - a, cy + a], TEXTO); c.rect([cx + a, cy - a, cx + d, cy + a], TEXTO)
    else:
        c.circle(cx, cy, 19, fill=(255, 255, 255, 18)); r = 14
        if b == "Circulo": c.circle(cx, cy, r * 0.8, outline=CIRC, width=3.2)
        elif b == "Cruz":
            d = r * 0.7; c.line((cx - d, cy - d), (cx + d, cy + d), CRUZ, 3.2); c.line((cx + d, cy - d), (cx - d, cy + d), CRUZ, 3.2)
        elif b == "Quadrado":
            d = r * 0.65; c.rrect([cx - d, cy - d, cx + d, cy + d], 1, outline=QUAD, width=3)
        elif b == "Triangulo":
            c.poly([(cx, cy - r * 0.8), (cx + r * 0.8, cy + r * 0.55), (cx - r * 0.8, cy + r * 0.55)], TRI, 3.2)
def rzad(c, els, prawa, cy, maks, f):
    while f > 12 and sum(szer(e, f) for e in els) > maks: f -= 1
    tot = sum(szer(e, f) for e in els); x = prawa - tot
    for e in els:
        s = szer(e, f); rysuj(c, e, x + s / 2, cy, f); x += s
    return tot
def komorka(c, x0, cy, b):
    q = quest_dla(b)
    els = [q, ("/",), ("P", b)] if q else [("P", b)]
    rzad(c, els, x0 + PRAWA, cy, PRAWA - LEWA, 17)
    return PRZES
def naglowek(c, w, atalho):
    x = w - 52; cy = 66
    def tekst(t):
        nonlocal x
        c.text(t, x, 74, 22, SUAVE, 400, "rs"); x -= measure(t, 22) + 14
    def para(q, p):
        nonlocal x
        x -= rzad(c, [q, ("/",), ("P", p)], x, cy, 1000, 19) + 14
    tekst("hold to end the session"); para(("L", "B"), "Circulo"); x -= 12
    tekst("close  ·"); para(("L", "B"), "Circulo"); x -= 2
    tekst("or"); para(("M",), "N:" + atalho)
    c.rect([52, 112, w - 52, 113], (255, 255, 255, 18))

# ---------- PainelDeAjuste ----------
def linha(c, x0, x1, y, l):
    b, rot, val, extra = l
    q = komorka(c, x0, y + 38, b)
    t0 = x0 + 86 + q
    c.text(rot, t0, y + 32, 21, SUAVE)
    lim = x1 - larg_extra(extra) - 12; t = val
    while len(t) > 3 and t0 + measure(t, 29, 700) > lim: t = t[:-2] + "…"
    c.text(t, t0, y + 66, 29, TEXTO, 700)
    if extra and extra[0] == "deg": degraus(c, x1, y, extra[1], extra[2])
    if extra and extra[0] == "tag": etiqueta(c, x1, y, extra[1], extra[2])
    if extra and extra[0] == "chave": chave(c, x1, y, extra[1])
    return t != val
def larg_extra(e):
    if not e: return 0
    if e[0] == "deg": return e[1] * 11 + (e[1] - 1) * 7
    if e[0] == "tag": return measure(e[1], 18) + 20
    return 56
def degraus(c, xr, y, tot, at):
    x = xr - (tot * 11 + (tot - 1) * 7)
    for k in range(tot):
        c.rrect([x, y + 50, x + 11, y + 61], 3, fill=DESTAQUE if k <= at else (255, 255, 255, 36)); x += 18
def etiqueta(c, xr, y, t, cor):
    s = measure(t, 18, 700) + 20
    c.rrect([xr - s, y + 36, xr, y + 66], 15, fill=cor[:3] + (51,)); c.text(t, xr - s / 2, y + 57, 18, cor, 700, "ms")
def chave(c, xr, y, on):
    c.rrect([xr - 56, y + 37, xr, y + 67], 15, fill=(60, 127, 224, 255) if on else (255, 255, 255, 41))
    c.circle(xr - 15 if on else xr - 41, y + 52, 11, fill=(255, 255, 255, 255))

def panel(nazwa, sciezka):
    W, H = 2048, 464
    c = C(W, H); fundo(c, (8, 8, W - 8, H - 8))
    c.text(nazwa, 52, 78, 36, TEXTO, 700)
    x = 52 + measure(nazwa, 36, 700) + 18; c.rect([x, 50, x + 2, 82], (255, 255, 255, 70)); x += 20
    c.text("Screen settings", x, 78, 34, SUAVE, 300); x += measure("Screen settings", 34, 300) + 32
    c.circle(x + 7, 66, 7, fill=ATENCAO); c.text("The game gets no input while this is open", x + 24, 74, 22, AVISO)
    lewy_koniec = x + 24 + measure("The game gets no input while this is open", 22)
    naglowek(c, W, "L3 + R3 + R1")
    larg = (W - 2 * 52 - 4 * 44) / 5
    col = lambda i: (52 + i * (larg + 44), 52 + i * (larg + 44) + larg)
    def secao(x0, x1, t):
        c.text(t, x0, 146, 17, CABECALHO, 700)
    secao(col(0)[0], col(1)[1], "SCREEN"); secao(col(2)[0], col(3)[1], "PICTURE & CONTROLLER"); secao(col(4)[0], col(4)[1], "LIVE")
    tela = [("DirV", "Size", "70°", ("tag", "just right", BOM)), ("DirH", "Distance", "4.0 m", None),
            ("N:L1  R1", "Height", "+0.00 m", None), ("N:L2", "Shape", "Curved", None),
            ("N:R2", "Curvature", "Medium", ("deg", 4, 2)), ("Cruz", "Recenter", "In front of you", None)]
    imagem = [("Quadrado", "Sharpness", "Medium", ("deg", 5, 2)), ("N:Share", "Brightness", "Normal", ("deg", 4, 2)),
              ("Triangulo", "Room", "Hidden", ("deg", 5, 0))]
    outros = [("N:Options", "3D strength", "3D off in the launcher", None), ("N:R3", "Adaptive triggers", "Off", ("chave", False)),
              ("N:PS", "Touchpad click", "Map · closes this", None)]
    cols = [tela[:3], tela[3:], imagem, outros]
    obciete = []
    for i, ls in enumerate(cols):
        x0, x1 = col(i)
        for j, l in enumerate(ls):
            if linha(c, x0, x1, [170, 250, 330][j], l): obciete.append(l[1])
    xd = col(4)[0] - 22; c.rect([xd, 160, xd + 1, H - 30], (255, 255, 255, 18))
    meds = [("Network", "0.00% loss", "1450 packets/s", BOM), ("Latency", "31 ms", "head to photon", BOM),
            ("Frames", "0 dropped", "last second", BOM), ("Thermal", "62%", "headroom · steady", BOM)]
    y = 154; x0, x1 = col(4)
    for n, v, d, e in meds:
        c.circle(x0 + 8, y + 42, 7, fill=e); c.text(n, x0 + 26, y + 49, 21, SUAVE); c.text(v, x1, y + 49, 25, TEXTO, 700, "rs")
        c.text(d, x0 + 26, y + 70, 17, CABECALHO); y += 60
    # Dolna linia TouchMenu.dorysujLegende
    leg = ("Quest controller / PS5 controller   ·   LT RT  Quest triggers   ·   Grip  grip buttons   ·   "
           "stick: either one, L / R: press the left / right stick   ·   adaptive triggers, touchpad click: PS5 controller only")
    prawa = 52 + 4 * larg + 3 * 44; f = 20
    while f > 13 and measure(leg, f) > prawa - 52: f -= 1
    c.text(leg, 52, H - 22, f, SUAVE)
    tlo = Image.new("RGBA", (W, H), (40, 44, 52, 255)); out = Image.alpha_composite(tlo, c.img)
    out.convert("RGB").save(sciezka)
    print("panel:", sciezka, "| ucięte wartości:", obciete or "brak", "| font legendy:", f,
          "| lewy nagłówek kończy się na", round(lewy_koniec))

def przypomnienie(pad, sciezka, akord="L3 + R3 + R1"):
    W, H = 2048, 464
    c = C(W, H); r = (W / 2 - 720, 8, W / 2 + 720, 200); fundo(c, r)
    x = r[0] + 48; cy = r[1] + 56
    if pad:
        xt = x + rzad(c, [("P", "N:" + akord)], x + szer(("P", "N:" + akord), 24), cy, 1000, 24) + 16
        c.text("or", xt, r[1] + 66, 26, SUAVE); xt += measure("or", 26) + 16
        xt += rzad(c, [("M",)], xt + 40, cy, 1000, 24) + 20
        c.text("opens the settings panel", xt, r[1] + 68, 34, TEXTO, 700)
        linie = ["The menu button on the left Quest controller works too. Same button closes the panel.",
                 "Move, resize and sharpen the screen, and click the touchpad."]
    else:
        xt = x + rzad(c, [("M",)], x + 40, cy, 1000, 24) + 20
        c.text("on the left Quest controller opens the settings panel", xt, r[1] + 68, 34, TEXTO, 700)
        linie = ["Home mode: your PS5 controller is connected to the console, as it should be.",
                 f"With a controller paired to the Quest instead, {akord} opens the panel too."]
    y = r[1] + 120
    for l in linie:
        f = 27
        while f > 18 and measure(l, f) > (r[2] - r[0]) - 96: f -= 1
        c.text(l, x, y, f, SUAVE); y += 40
    out = Image.alpha_composite(Image.new("RGBA", (W, H), (40, 44, 52, 255)), c.img).crop((int(r[0]) - 20, 0, int(r[2]) + 20, 220))
    out.convert("RGB").save(sciezka); print("przypomnienie:", sciezka)

d = sys.argv[1]
panel("QuestStation8", d + "/panel-qs8.png")
przypomnienie(False, d + "/przypomnienie-tryb-domowy.png")
przypomnienie(True, d + "/przypomnienie-z-padem.png")
