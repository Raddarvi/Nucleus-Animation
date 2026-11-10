"""Generates 16x16 pixel-art item icons used by the mod.
Usage: python3 tools/gen_icons.py src/main/resources/assets/nucleusanimation/textures/gui/icons
"""
import sys, os
from PIL import Image

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
OL = (30, 20, 40, 255)

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def new():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); return im, im.load()

def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16: px[x, y] = c

def outline(im):
    px = im.load(); a = im.copy().load(); pts = []
    for y in range(16):
        for x in range(16):
            if a[x, y][3] == 0 and any(0 <= x+dx < 16 and 0 <= y+dy < 16 and a[x+dx, y+dy][3] > 0
                                       for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))):
                pts.append((x, y))
    for x, y in pts: px[x, y] = OL

def gem(name, col, flawless=True):
    im, px = new(); c = hexc(col); d = shade(c, 0.6); l = shade(c, 1.4); w = (255, 255, 255, 255)
    rows = [(5, 10), (4, 11), (3, 12), (2, 13), (2, 13), (3, 12), (4, 11), (5, 10), (6, 9), (7, 8)]
    for i, (a, b) in enumerate(rows):
        for x in range(a, b + 1):
            col_ = l if i < 2 else (c if x < 8 else d)
            put(px, x, 3 + i, col_)
    put(px, 6, 4, w); put(px, 5, 5, w); put(px, 7, 4, w)
    if flawless:
        put(px, 12, 2, w); put(px, 13, 1, hexc('ffffaa')); put(px, 2, 11, w)
    outline(im); im.save(os.path.join(OUT, f'{name}.png'))

def alloy():
    im, px = new()
    rainbow = ['ff5555', 'ffaa00', 'ffff55', '55ff55', '55ffff', '5555ff', 'ff55ff']
    for y in range(5, 12):
        for x in range(2 + (11 - y) // 2, 14 - (11 - y) // 2):
            px[x, y] = hexc(rainbow[(x + y) % len(rainbow)])
    for x in range(5, 11): px[x, 5] = (255, 255, 255, 255)
    put(px, 13, 2, (255, 255, 255, 255)); put(px, 2, 3, hexc('ffff99')); put(px, 14, 13, hexc('ff99ff'))
    outline(im); im.save(os.path.join(OUT, 'divans_alloy.png'))

def claw():
    im, px = new(); b = hexc('e8e0d0'); s = hexc('a89c88')
    for k, ox in enumerate((3, 7, 11)):
        for i in range(8):
            x = ox + i // 3; y = 3 + i
            put(px, x, y, b); put(px, x + 1, y, s)
    for x in range(3, 14):
        for y in range(11, 14): put(px, x, y, hexc('7a4a2a') if y > 11 else hexc('9a6a3a'))
    outline(im); im.save(os.path.join(OUT, 'quick_claw.png'))

def dye():
    im, px = new(); g = hexc('3fd06a'); d = shade(g, 0.6); l = shade(g, 1.4)
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 9) ** 2 <= 22: px[x, y] = d if x > 9 or y > 11 else g
    put(px, 6, 6, l); put(px, 5, 7, l); put(px, 6, 7, (255, 255, 255, 255))
    for y in range(2, 5): put(px, 8, y, g)  # drip
    put(px, 8, 1, l)
    outline(im); im.save(os.path.join(OUT, 'jade_dye.png'))

def jaderald():
    im, px = new(); c = hexc('2fd47c'); d = shade(c, 0.55); l = shade(c, 1.35)
    for y in range(2, 14):
        w = 5 - abs(y - 7) // 2 if y < 12 else 2
        for x in range(8 - w, 8 + w):
            put(px, x, y, l if x < 7 and y < 7 else (c if x < 9 else d))
    put(px, 6, 4, (255, 255, 255, 255)); put(px, 13, 3, hexc('ffff88')); put(px, 2, 12, (255, 255, 255, 255))
    outline(im); im.save(os.path.join(OUT, 'jaderald.png'))

def fragment():
    im, px = new(); c = hexc('ffcc33'); d = shade(c, 0.6); l = shade(c, 1.3)
    shape = [(6, 9), (5, 10), (5, 11), (4, 11), (4, 12), (5, 12), (6, 11), (7, 10), (8, 9)]
    for i, (a, b) in enumerate(shape):
        for x in range(a, b + 1): put(px, x, 4 + i, l if x < a + 2 else (c if x < b else d))
    put(px, 6, 5, (255, 255, 255, 255)); put(px, 12, 3, hexc('fff4b0'))
    outline(im); im.save(os.path.join(OUT, 'divan_fragment.png'))

def powder():
    im, px = new(); c = hexc('ff77ff')
    for x, y in ((4, 10), (7, 12), (10, 9), (12, 12), (6, 7), (9, 5), (3, 13), (11, 6), (8, 9)):
        put(px, x, y, c); put(px, x + 1, y, shade(c, 0.7))
    for x, y in ((8, 2), (8, 3), (7, 3), (9, 3), (8, 4)): put(px, x, y, (255, 255, 255, 255))
    outline(im); im.save(os.path.join(OUT, 'powder.png'))

def generic():
    im, px = new(); c = hexc('c9a46a'); d = shade(c, 0.65)
    for y in range(6, 14):
        for x in range(3, 13): px[x, y] = c if x < 10 else d
    for x in range(5, 11): put(px, x, 5, d)
    for x in range(6, 10): put(px, x, 4, c)
    put(px, 7, 8, hexc('ffdd55')); put(px, 8, 8, hexc('ffdd55')); put(px, 4, 7, shade(c, 1.3))
    outline(im); im.save(os.path.join(OUT, 'generic.png'))

GEMS = {
    'ruby': 'ff5555', 'amber': 'ffaa00', 'amethyst': 'b24dff', 'jade': '55ff55',
    'sapphire': '5577ff', 'topaz': 'ffff55', 'jasper': 'ff55ff', 'opal': 'f0f0ff',
    'onyx': '505060', 'aquamarine': '55ffff', 'citrine': 'e0a020', 'peridot': '3f9f2f',
}
for n, col in GEMS.items():
    gem(f'flawless_{n}', col, True)
    gem(f'gem_{n}', col, False)
alloy(); claw(); dye(); jaderald(); fragment(); powder(); generic()

if len(sys.argv) > 2:
    files = sorted(f for f in os.listdir(OUT) if f.endswith('.png'))
    sheet = Image.new('RGBA', (16 * len(files), 16), (40, 30, 60, 255))
    for i, f in enumerate(files): sheet.alpha_composite(Image.open(os.path.join(OUT, f)), (16 * i, 0))
    sheet.resize((sheet.width * 4, 64), Image.NEAREST).save(sys.argv[2])
