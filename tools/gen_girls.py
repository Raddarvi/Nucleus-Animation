"""Generates original chibi anime-girl pixel-art portraits (48x64) for the mod."""
import sys, os
from PIL import Image

W, H = 48, 64
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v*f))) for v in c[:3]) + (c[3],)

SKIN = hexc('ffe3d1'); SKIN_SH = hexc('f5c4ae'); BLUSH = hexc('ff9fae'); OUTLINE = hexc('2a1630')
WHITE = hexc('ffffff'); MOUTH = hexc('c8505e')

def ellipse_pts(cx, cy, rx, ry):
    for y in range(int(cy-ry)-1, int(cy+ry)+2):
        for x in range(int(cx-rx)-1, int(cx+rx)+2):
            if ((x+0.5-cx)/rx)**2 + ((y+0.5-cy)/ry)**2 <= 1.0:
                yield x, y

class Canvas:
    def __init__(s):
        s.img = Image.new('RGBA', (W, H), (0,0,0,0)); s.px = s.img.load()
    def put(s, x, y, c):
        if 0 <= x < W and 0 <= y < H: s.px[x, y] = c
    def ell(s, cx, cy, rx, ry, c):
        for x, y in ellipse_pts(cx, cy, rx, ry): s.put(x, y, c)
    def rect(s, x0, y0, x1, y1, c):
        for y in range(y0, y1+1):
            for x in range(x0, x1+1): s.put(x, y, c)
    def outline(s):
        a = s.img.copy().load(); out = []
        for y in range(H):
            for x in range(W):
                if a[x, y][3] == 0:
                    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                        nx, ny = x+dx, y+dy
                        if 0 <= nx < W and 0 <= ny < H and a[nx, ny][3] > 0 and a[nx, ny] != OUTLINE:
                            out.append((x, y)); break
        for x, y in out: s.put(x, y, OUTLINE)

def diamond(c, cx, cy, r, col, light):
    for dy in range(-r, r+1):
        w = r - abs(dy)
        for dx in range(-w, w+1): c.put(cx+dx, cy+dy, col)
    c.put(cx, cy-r+1, light); c.put(cx-1, cy, light) if r > 1 else None

def girl(name, hair, eye, outfit, style, acc, gem):
    c = Canvas()
    hair = hexc(hair); hs = shade(hair, 0.75); hl = shade(hair, 1.25)
    eye = hexc(eye); outfit = hexc(outfit); os_ = shade(outfit, 0.75); gem = hexc(gem)
    cx, cy = 24, 27

    # --- hair behind ---
    if style == 'long':
        c.ell(cx, cy-1, 15, 15, hair)
        c.rect(cx-15, cy, cx-9, 58, hair); c.rect(cx+9, cy, cx+15, 58, hair)
        for y in range(52, 59):  # wavy tips
            c.put(cx-15 + (y % 2), y, hs); c.put(cx+15 - (y % 2), y, hs)
    elif style == 'bob':
        c.ell(cx, cy, 15, 14, hair); c.rect(cx-15, cy, cx+15, cy+10, hair)
    elif style == 'ponytail':
        c.ell(cx, cy-1, 14, 14, hair)
        c.ell(cx+14, cy-6, 5, 7, hair); c.ell(cx+16, cy+4, 4, 9, hair); c.ell(cx+17, cy+12, 3, 6, hs)
    elif style == 'buns':
        c.ell(cx, cy-1, 14, 14, hair)
        c.ell(cx-12, cy-13, 5, 5, hair); c.ell(cx+12, cy-13, 5, 5, hair)
        c.rect(cx-14, cy, cx-10, cy+16, hair); c.rect(cx+10, cy, cx+14, cy+16, hair)
    elif style == 'wavy':
        c.ell(cx, cy-1, 16, 15, hair)
        for i, yy in enumerate(range(cy, 60, 3)):
            off = 1 if i % 2 else 0
            c.ell(cx-13-off, yy, 4, 3, hair if i % 3 else hs); c.ell(cx+13+off, yy, 4, 3, hair if i % 3 else hs)

    # --- body / outfit ---
    c.rect(cx-3, cy+10, cx+3, cy+14, SKIN_SH)             # neck
    for y in range(cy+14, H):
        w = min(16, 7 + (y - (cy+14)))
        for x in range(cx-w, cx+w+1): c.put(x, y, outfit)
    for y in range(cy+14, H):                               # shading on sides
        w = min(16, 7 + (y - (cy+14)))
        c.put(cx-w, y, os_); c.put(cx+w, y, os_)
    # collar
    for i in range(5):
        c.put(cx-4+i, cy+14+i, WHITE); c.put(cx+4-i, cy+14+i, WHITE)
        c.put(cx-5+i, cy+14+i, WHITE); c.put(cx+5-i, cy+14+i, WHITE)
    diamond(c, cx, cy+20, 2, gem, WHITE)                   # crystal brooch

    # --- face ---
    c.ell(cx, cy+1, 11, 11, SKIN)
    for x, y in ellipse_pts(cx, cy+1, 11, 11):
        if y >= cy+9: c.put(x, y, SKIN_SH)
    c.ell(cx, cy+1, 10, 10, SKIN)

    # --- bangs ---
    for x in range(cx-12, cx+13):
        top = cy - 12
        jag = [3, 5, 4, 6, 3, 5, 7, 4, 5, 3, 6, 4][(x - cx + 12) % 12]
        for y in range(top, cy - 6 + jag):
            if ((x+0.5-cx)/13)**2 + ((y+0.5-(cy-1))/14)**2 <= 1.0: c.put(x, y, hair)
    for x in range(cx-9, cx+10, 4): c.put(x, cy-9, hl); c.put(x+1, cy-10, hl)   # shine
    c.rect(cx-12, cy-4, cx-10, cy+6, hair); c.rect(cx+10, cy-4, cx+12, cy+6, hair)  # side locks

    # --- eyes ---
    for ex in (cx-6, cx+3):
        c.rect(ex, cy+1, ex+3, cy+5, eye)
        c.rect(ex, cy+4, ex+3, cy+5, shade(eye, 0.65))
        c.rect(ex-1, cy, ex+4, cy, OUTLINE)                # lash line
        c.put(ex+4 if ex > cx else ex-1, cy-1, OUTLINE)    # lash flick
        c.put(ex+1, cy+2, WHITE); c.put(ex+2, cy+4, hexc('ffffff', 180))
    # blush, mouth
    for bx in (cx-8, cx+6):
        c.put(bx, cy+7, BLUSH); c.put(bx+1, cy+7, BLUSH); c.put(bx+2, cy+7, BLUSH)
    c.put(cx-1, cy+8, MOUTH); c.put(cx, cy+8, MOUTH); c.put(cx, cy+9, shade(MOUTH, 1.2))

    # --- accessories ---
    if acc == 'pin':
        diamond(c, cx-9, cy-8, 2, gem, WHITE)
    elif acc == 'flower':
        for dx, dy in ((0,-2),(2,0),(0,2),(-2,0)): c.ell(cx+9+dx, cy-8+dy, 1.5, 1.5, gem)
        c.put(cx+9, cy-8, hexc('fff36b'))
    elif acc == 'ribbon':
        c.rect(cx+6, cy-13, cx+8, cy-11, gem); c.ell(cx+4, cy-12, 2.5, 2, gem); c.ell(cx+10, cy-12, 2.5, 2, gem)
    elif acc == 'crown':
        gold = hexc('ffcc33')
        c.rect(cx-6, cy-14, cx+6, cy-13, gold)
        for x in (cx-6, cx-3, cx, cx+3, cx+6): c.rect(x, cy-17, x, cy-15, gold)
        diamond(c, cx, cy-15, 1, gem, WHITE)
    elif acc == 'halo':
        for x, y in ellipse_pts(cx, cy-17, 9, 2.5):
            c.put(x, y, hexc('fff2a8'))
        for x, y in ellipse_pts(cx, cy-17, 6, 1.2):
            c.put(x, y, (0, 0, 0, 0))
        diamond(c, cx-9, cy-7, 2, gem, WHITE); diamond(c, cx+9, cy-7, 2, hexc('7cf7ff'), WHITE)
    elif acc == 'ears':
        for side in (-1, 1):
            bx = cx + side*9
            for i in range(5):
                for j in range(-2+i//2, 3-i//2): c.put(bx + j, cy-13-i, hair)
            c.put(bx, cy-14, hexc('ffb3c1'))

    c.outline()
    c.img.save(os.path.join(OUT, f'{name}.png'))
    return c.img

GIRLS = [
    # name,     hair,     eyes,     outfit,   style,      accessory, gem
    ('hana',    '8a5a3c', '5fae5a', '6fbf73', 'bob',      'flower',  'ff8fb1'),   # common
    ('nori',    '2f2d3a', '7d6bd1', '8c8c9c', 'long',     'pin',     'c8c8d8'),   # common
    ('sora',    'a9dcff', '3f8de0', 'f4f4ff', 'bob',      'ribbon',  '6fc0ff'),   # uncommon
    ('kiko',    'ff9a3c', 'b35a1f', '3fbf6f', 'ponytail', 'ears',    'ffd36b'),   # uncommon
    ('ruri',    '3a4fd8', '8fd0ff', '24306e', 'long',     'pin',     '5577ff'),   # rare
    ('shion',   'a45be0', 'e06bd1', '4a2a6e', 'wavy',     'ribbon',  'b24dff'),   # epic
    ('kohaku',  'd9821a', 'ff7a1f', '7a3f12', 'ponytail', 'ribbon',  'ffaa00'),   # epic
    ('midori',  '1fbf6a', '9dffb8', '0e5a35', 'wavy',     'crown',   '55ff55'),   # legendary
    ('divana',  'fff4d6', 'ff6bf0', 'f2f0ff', 'long',     'halo',    'ff55ff'),   # mythic
]

imgs = [girl(*g) for g in GIRLS]
if len(sys.argv) > 2:
    sheet = Image.new('RGBA', (W*len(imgs), H), (40, 30, 60, 255))
    for i, im in enumerate(imgs): sheet.alpha_composite(im, (i*W, 0))
    sheet.resize((sheet.width*5, sheet.height*5), Image.NEAREST).save(sys.argv[2])
