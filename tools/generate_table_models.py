"""Generate continuous six-block casino furniture using vanilla model geometry.

Run from any directory: python tools/generate_table_models.py
Each part is clipped from one complete piece of furniture. North-facing part 0
is on the east side, matching GameTableBlock.partPos, not the model's left side.

The felt itself, the cards, the wheel and the board are drawn by StationTables; the
furniture here is what holds them: the padded armrest the players lean on, the
apron, and a base of its own for each game. Model space runs x 0..48 across and
z 0..32 from the players' edge; a point of the felt canvas (cx, cy) lies at
x = 24 - cx / 8, z = 16 - cy / 8. Needs Pillow for the textures.
"""
import json
import math
import os
import random
from pathlib import Path

from PIL import Image

from generate_arcade_assets import clamp, mix, polished_brass, raised_panel, wood

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems'
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
TEXTURES = dict(wood='gamblingitems:block/table_wood', panel='gamblingitems:block/table_panel',
                dark='gamblingitems:block/table_dark', leather='gamblingitems:block/table_leather',
                brass='gamblingitems:block/table_brass', red='minecraft:block/red_concrete',
                ivory='minecraft:block/white_concrete')
# The bingo table takes a blue cloth, so it reads apart from the green card tables.
FELT = {'roulette': 'gamblingitems:block/table_felt_green', 'blackjack': 'gamblingitems:block/table_felt_green',
        'bingo': 'gamblingitems:block/table_felt_blue'}
# The roulette comes in four tables while the choice between them is open (see TableLayout.DIRECTION):
# 0 Vegas, 1 Monte-Carlo, 2 electronic, 3 saloon. Its furniture and its textures follow the one asked for.
DIRECTION = int(os.environ.get('ROULETTE_DIRECTION', '0'))
ROULETTE_TEXTURES = dict(wood='gamblingitems:block/roulette_wood', panel='gamblingitems:block/roulette_panel',
                         dark='gamblingitems:block/roulette_dark', leather='gamblingitems:block/roulette_leather',
                         brass='gamblingitems:block/roulette_metal', glow='gamblingitems:block/roulette_glow',
                         felt='gamblingitems:block/roulette_felt')
# Where the game draws its pieces on the felt, in model space (see the canvas mapping above).
CUP = (41.5, 11.75)          # the bingo cup, canvas (-140, 34), radius 3.75
TOP = 13.28                  # the felt StationTables draws on, 0.83 of a block


# --- Textures ---------------------------------------------------------------------------------

def leather(seed, base=(104, 28, 32), stitch=(150, 96, 70)):
    """Padded leather, oxblood unless told otherwise: a fine pebbled grain, a soft sheen drifting across
    the hide, and a saddle stitch running along it. Every term repeats over the tile, so a long rail
    shows no seam."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            sheen = math.sin(math.tau * (x + 2 * y) / 16) * 5 + math.sin(math.tau * (3 * x - y) / 16) * 3
            pebble = rng.uniform(-6, 6) if (x * 7 + y * 3) % 5 else -9
            c = tuple(v + sheen + pebble for v in base)
            if y == 13 and x % 3 != 2:
                c = stitch
            elif y in (12, 14):
                c = tuple(v - 10 for v in c)
            image.putpixel((x, y), clamp(c))
    return image


def lacquer():
    """Black lacquer with a faint brush grain, for the plinths and the trims."""
    rng = random.Random(7)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-3, 3) + math.sin(x * .8) * 2 for v in (22, 20, 24))))
    return image


def cloth(base, seed, size=64):
    """Casino baize, four times finer than a block texture: a tight twill, loose fibres catching the
    light, and a soft mottling where the nap lies one way or the other. Every term repeats over the
    tile, so the cloth runs on from one block of the table to the next without a seam."""
    rng = random.Random(seed)
    waves = [(rng.randint(1, 3), rng.randint(-3, 3), rng.uniform(0, math.tau), rng.uniform(1.5, 3)) for _ in range(5)]
    image = Image.new('RGB', (size, size))
    for y in range(size):
        for x in range(size):
            # The weave: a diagonal rib every other thread, and a fainter cross thread under it.
            twill = 3.5 if (x + y) % 4 < 2 else -2.5
            cross = 1 if (x - y) % 8 == 0 else 0
            # The nap: broad soft patches, lighter and darker.
            nap = sum(amp * math.sin(math.tau * (kx * x + ky * y) / size + phase) for kx, ky, phase, amp in waves)
            # A few stray fibres, lighter than the cloth.
            fibre = rng.uniform(5, 9) if rng.random() < .025 else 0
            grain = rng.uniform(-2.5, 2.5)
            image.putpixel((x, y), clamp(tuple(v + twill + cross + nap + fibre + grain for v in base)))
    return image


def metal(base, seed):
    """Brushed metal: fine scratches along it and a soft band of light across."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        scratch = rng.uniform(-7, 7)
        for x in range(16):
            band = 22 * math.cos((x + y * 0.3) / 16 * math.tau)
            image.putpixel((x, y), clamp(tuple(v + scratch + band + rng.uniform(-2, 2) for v in base)))
    return image


def framed(image, line):
    """A panel cut from a plain texture: a line round it, the field inside left as it is."""
    image = image.copy()
    for i in range(16):
        for at in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= at[0] <= 14 and 1 <= at[1] <= 14:
                image.putpixel(at, line)
    return image


def glass(seed, size=64):
    """The black glass of an electronic table: a faint grid of light under it and a sheen across it."""
    rng = random.Random(seed)
    image = Image.new('RGB', (size, size))
    for y in range(size):
        for x in range(size):
            c = (7, 9, 14)
            if x % 8 == 0 or y % 8 == 0:
                c = (13, 22, 32)
            if x % 16 == 0 and y % 16 == 0:
                c = (30, 70, 90)
            sheen = 5 * math.sin(math.tau * (x + y) / size)
            image.putpixel((x, y), clamp(tuple(v + sheen + rng.uniform(-1, 1) for v in c)))
    return image


def planks(seed, size=64):
    """A table top of oak boards: each a tone of its own, grain along it, a dark joint, a nail at the ends."""
    rng = random.Random(seed)
    image = Image.new('RGB', (size, size))
    for board in range(size // 16):
        tone = rng.uniform(-12, 12)
        shift = rng.randint(0, size)
        for y in range(16):
            streak = math.sin(y * 1.9 + board) * 5
            for x in range(size):
                c = tuple(v + tone + streak + rng.uniform(-5, 5) for v in (134, 96, 54))
                if y == 15:
                    c = (46, 30, 16)
                elif (x + shift) % size == 0:
                    c = (58, 38, 20)
                elif y in (3, 11) and (x + shift) % size in (2, size - 3):
                    c = (40, 40, 44)
                image.putpixel((x, board * 16 + y), clamp(c))
    return image


def roulette_textures():
    """The seven textures of the roulette, for the table it is asked to be."""
    if DIRECTION == 1:
        # Monte-Carlo: blond walnut, cream leather, a midnight cloth.
        blond = wood(81, (128, 88, 44))
        return dict(wood=blond, panel=framed(blond, (84, 54, 24)), dark=wood(82, (60, 36, 18)),
                    leather=leather(83, (214, 196, 160), (150, 120, 80)), metal=polished_brass(),
                    glow=polished_brass(), felt=cloth((16, 28, 70), 84))
    if DIRECTION == 2:
        # Electronic: black lacquer, steel, glass, and a line of light.
        black = lacquer()
        light = Image.new('RGB', (16, 16), (53, 224, 255))
        return dict(wood=black, panel=framed(black, (53, 224, 255)), dark=black,
                    leather=leather(93, (26, 26, 32), (53, 224, 255)), metal=metal((150, 160, 175), 94),
                    glow=light, felt=glass(95))
    if DIRECTION == 3:
        # Saloon: oak, a brown hide, iron, and boards for a top.
        oak = wood(101, (112, 78, 42))
        return dict(wood=oak, panel=framed(oak, (58, 38, 20)), dark=wood(102, (52, 34, 18)),
                    leather=leather(103, (112, 72, 40), (200, 170, 110)), metal=metal((84, 84, 90), 104),
                    glow=metal((84, 84, 90), 104), felt=planks(105))
    return dict(wood=wood(71, (70, 30, 16)), panel=raised_panel(72), dark=lacquer(), leather=leather(73),
                metal=polished_brass(), glow=polished_brass(), felt=cloth((18, 84, 50), 74))


def write_textures():
    def save_png(name, image):
        image.save(ASSETS / 'textures/block' / f'{name}.png', optimize=True)
    save_png('table_wood', wood(71, (70, 30, 16)))
    save_png('table_panel', raised_panel(72))
    save_png('table_leather', leather(73))
    save_png('table_brass', polished_brass())
    save_png('table_dark', lacquer())
    save_png('table_felt_green', cloth((18, 84, 50), 74))
    save_png('table_felt_blue', cloth((22, 52, 112), 75))
    for name, image in roulette_textures().items():
        save_png(f'roulette_{name}', image)


# --- Furniture --------------------------------------------------------------------------------

class Furniture:
    """Boxes of the whole table. A box is clipped across the six parts; a turned one (rotation set) stays
    whole and goes to the part holding its centre, as a block model allows an element to reach past it."""

    def __init__(self):
        self.boxes = []

    def box(self, a, b, material, rotation=None):
        self.boxes.append((a, b, material, rotation))

    def turned(self, centre, length, width, y1, y2, angle, material):
        """A bar lying flat, its centre given, turned by a multiple of 22.5 degrees from the x axis."""
        angle = (angle + 90) % 180 - 90
        swap = abs(angle) > 45
        if swap:
            angle = angle - 90 if angle > 0 else angle + 90
        along, across = (width, length) if swap else (length, width)
        cx, cz = centre
        self.box((cx - along / 2, y1, cz - across / 2), (cx + along / 2, y2, cz + across / 2), material,
                 {'angle': angle, 'axis': 'y', 'origin': (cx, (y1 + y2) / 2, cz)} if angle else None)

    def ring(self, centre, radius, width, y1, y2, material, sides=16):
        """A flat ring of straight segments round a centre, as a bowl's rim or a cage's hoop."""
        side = 2 * (radius + width / 2) * math.tan(math.pi / sides) + .05
        for index in range(sides):
            a = 2 * math.pi * index / sides
            at = (centre[0] + math.cos(a) * radius, centre[1] + math.sin(a) * radius)
            # A bar along the x axis lies tangent at a = 90 degrees; MC turns +angle from x towards -z.
            self.turned(at, side, width, y1, y2, -(math.degrees(a) + 90), material)


def common(f):
    """What every table has: the top, the padded armrest, the apron and its trims."""
    # The top: a thick wooden slab, the baize on it, a brass line and a lacquered shadow under it.
    f.box((0, 11.6, 0), (48, 13, 32), 'wood')
    f.box((.25, 13, .25), (47.75, 13.2, 31.75), 'felt')
    f.box((.3, 11.2, .3), (47.7, 11.6, 31.7), 'brass')
    # The apron: moulded panels all round, between lacquered rails.
    f.box((.7, 8.6, .7), (47.3, 11.2, 31.3), ('wood', {'north': 'panel', 'south': 'panel', 'west': 'panel', 'east': 'panel'}))
    f.box((.6, 10.9, .6), (47.4, 11.2, 31.4), 'dark')
    f.box((1, 8.3, 1), (47, 8.6, 31), 'dark')
    # The armrest along the players' edge: a rounded cushion of padded leather, brass studs along its face.
    f.box((0, 13, -.6), (48, 13.7, 2.4), 'leather')
    f.box((.15, 13.7, -.45), (47.85, 14.4, 2.2), 'leather')
    f.box((.4, 14.4, -.1), (47.6, 14.75, 1.7), 'leather')
    for x in range(1, 48, 2):
        f.box((x, 13.25, -.75), (x + .4, 13.55, -.6), 'brass')
    # Narrow rails up the sides, clear of the controls printed on the felt, and the dealer's straight back edge.
    for x1, x2 in ((0, .55), (47.45, 48)):
        f.box((x1, 13, 2.4), (x2, 14, 32), 'leather')
        f.box((x1 - .1, 14, 2.4), (x2 + .1, 14.2, 32), 'brass')
    f.box((.55, 13, 31.45), (47.45, 13.9, 32), 'wood')
    f.box((.55, 13.9, 31.6), (47.45, 14.05, 31.9), 'brass')
    # A stepped gold moulding round the slab, under the felt, and a gold kick at the foot of the apron.
    f.box((-.3, 12.6, -.3), (48.3, 13, 32.3), 'brass')
    f.box((-.15, 11.6, -.15), (48.15, 11.9, 32.15), 'brass')
    f.box((.5, 8, .5), (47.5, 8.3, 31.5), 'brass')
    # Studs along the apron's sides and back, matching the armrest's.
    for x in range(2, 47, 3):
        f.box((x, 9.7, 31.3), (x + .5, 10.2, 31.45), 'brass')
    for z in range(3, 31, 3):
        for x1 in (.55, 47.3):
            f.box((x1, 9.7, z), (x1 + .15, 10.2, z + .5), 'brass')


def blackjack(f):
    """Two heavy pedestals and a brass foot rail for the players, as at a casino's card tables."""
    for x in (10, 38):
        f.box((x - 5, 0, 11), (x + 5, .8, 21), 'dark')
        f.box((x - 4.2, .8, 11.8), (x + 4.2, 1.5, 20.2), 'brass')
        f.box((x - 3, 1.5, 13), (x + 3, 7.6, 19), ('wood', {s: 'panel' for s in ('north', 'south', 'west', 'east')}))
        f.box((x - 3.8, 7.6, 12.2), (x + 3.8, 8.3, 19.8), 'brass')
        # A strut under the foot rail, from the pedestal to the players' side.
        f.box((x - .3, 2, -1.6), (x + .3, 2.6, 13), 'brass')
    f.box((3, 2.2, -1.9), (45, 2.9, -1.2), 'brass')
    for x in (3, 44.3):
        f.box((x, 0, -1.9), (x + .7, 2.2, -1.2), 'brass')
    # The front panel's inlay: a card suit in ivory and red, under a brass plate.
    f.box((21, 9.2, .45), (27, 10.9, .7), 'brass')
    f.box((21.4, 9.5, .35), (26.6, 10.6, .45), 'dark')
    for x, material in ((22.2, 'ivory'), (23.7, 'red'), (25, 'ivory')):
        f.box((x, 9.75, .28), (x + .8, 10.35, .35), material)


def roulette(f):
    """What the roulette stands on: four turned legs at Vegas, one great pedestal at Monte-Carlo, a
    plinth with a line of light at its foot for the electronic table, two barrels at the saloon.
    The bowl, the wheel and all that lies on the felt are StationTables' voxels."""
    if DIRECTION == 1:
        f.box((9, 0, 6), (39, .9, 26), 'dark')
        f.box((10.5, .9, 7.5), (37.5, 1.6, 24.5), 'brass')
        f.box((15, 1.6, 10), (33, 7.4, 22), ('wood', {s: 'panel' for s in ('north', 'south', 'west', 'east')}))
        f.box((13, 7.4, 8), (35, 8.3, 24), 'brass')
        # A scrolled foot at each end of the plinth.
        for x in (4, 40):
            f.box((x, 0, 12), (x + 4, 1.4, 20), 'wood')
            f.box((x + .5, 1.4, 13), (x + 3.5, 2.2, 19), 'brass')
        return
    if DIRECTION == 2:
        f.box((3, 0, 3), (45, 8.3, 29), 'dark')
        f.box((2.8, .5, 2.8), (45.2, .9, 29.2), 'glow')
        f.box((2.8, 7.5, 2.8), (45.2, 7.9, 29.2), 'glow')
        return
    if DIRECTION == 3:
        for x in (11, 37):
            for w, y1, y2 in ((9, 0, 2.5), (10.6, 2.5, 5.8), (9, 5.8, 8.3)):
                f.box((x - w / 2, y1, 16 - w / 2), (x + w / 2, y2, 16 + w / 2), 'wood')
            for y in (2.3, 5.6):
                f.box((x - 5.5, y, 10.5), (x + 5.5, y + .4, 21.5), 'brass')
        f.box((11, 3.6, 15.5), (37, 4.4, 16.5), 'dark')
        return
    for x in (4, 44):
        for z in (4, 28):
            for w, y1, y2, material in ((3.4, 0, .8, 'dark'), (2.6, .8, 1.5, 'brass'), (1.6, 1.5, 4.6, 'wood'),
                                        (2.6, 4.6, 5.3, 'brass'), (2.3, 5.3, 7.2, 'wood'), (1.7, 7.2, 8.3, 'dark')):
                f.box((x - w / 2, y1, z - w / 2), (x + w / 2, y2, z + w / 2), material)
    for z in (4, 28):
        f.box((5, 3, z - .5), (43, 3.8, z + .5), 'wood')
    f.box((23.5, 3.1, 4.5), (24.5, 3.7, 27.5), 'wood')


def bingo(f):
    """Tapered legs with an H stretcher, and the caller's ball cage standing over the cup."""
    for x in (4, 44):
        for z in (4, 28):
            for w, y1, y2, material in ((2.8, 0, .8, 'brass'), (2, .8, 4.5, 'wood'), (2.6, 4.5, 8.3, 'panel')):
                f.box((x - w / 2, y1, z - w / 2), (x + w / 2, y2, z + w / 2), material)
    for x in (4, 44):
        f.box((x - .5, 2.4, 5), (x + .5, 3.2, 27), 'wood')
    f.box((5, 2.5, 15.5), (43, 3.1, 16.5), 'wood')
    # The cage: a brass foot ring, upright wires, two hoops, and a crank on its side.
    cx, cz = CUP
    f.ring(CUP, 3.9, .5, 13.2, 13.85, 'brass')
    for index in range(8):
        a = 2 * math.pi * (index + .5) / 8
        x, z = cx + math.cos(a) * 3.9, cz + math.sin(a) * 3.9
        f.box((x - .17, 13.7, z - .17), (x + .17, 19.4, z + .17), 'brass')
    f.ring(CUP, 3.9, .3, 16.4, 16.65, 'brass', sides=8)
    f.ring(CUP, 3.9, .35, 19.4, 19.75, 'brass', sides=8)
    f.box((cx - .2, 19.75, cz - 4), (cx + .2, 20.1, cz + 4), 'brass')
    f.box((cx - 4, 19.75, cz - .2), (cx + 4, 20.1, cz + .2), 'brass')
    f.box((cx - .9, 20.1, cz - .9), (cx + .9, 20.6, cz + .9), 'dark')
    f.box((cx - 5.2, 16.2, cz - .3), (cx - 4.1, 16.8, cz + .3), 'brass')
    f.box((cx - 5.6, 15.4, cz - .25), (cx - 5.1, 16.8, cz + .25), 'brass')
    f.box((cx - 5.9, 15.2, cz - .35), (cx - 5.3, 15.6, cz + .35), 'ivory')
    # A few balls waiting in the cage, round its rim so the number in the cup stays in sight.
    for index, material in enumerate(('ivory', 'red', 'ivory', 'brass', 'ivory')):
        a = 2 * math.pi * index / 5 + .4
        x, z = cx + math.cos(a) * 3, cz + math.sin(a) * 3
        f.box((x - .45, 13.7, z - .45), (x + .45, 14.6, z + .45), material)


GAMES = {'blackjack': blackjack, 'roulette': roulette, 'bingo': bingo}


# Faces that carry a whole picture (a moulded panel) keep it entire; every other material is cut to
# the size of the face, so a thin rail shows a strip of leather rather than the whole hide squeezed flat.
PICTURES = {'panel'}


def window(lo, hi, scale):
    """The stretch of a 16 pixel texture a face from lo to hi shows, at the texture's own scale."""
    size = min((hi - lo) * scale, 16)
    start = (lo * scale) % 16
    if start + size > 16:
        start = 16 - size
    return round(start, 4), round(start + size, 4)


def face_uv(side, lo, hi, texture, scale):
    if texture in PICTURES:
        return [0, 0, 16, 16]
    # Each face reads the two axes lying in its plane; the vertical runs down the texture.
    axes = {'down': (0, 2), 'up': (0, 2), 'north': (0, 1), 'south': (0, 1), 'west': (2, 1), 'east': (2, 1)}[side]
    u1, u2 = window(lo[axes[0]], hi[axes[0]], scale)
    if axes[1] == 1:
        v1, v2 = window(16 - hi[1], 16 - lo[1], scale)
    else:
        v1, v2 = window(lo[2], hi[2], scale)
    return [u1, v1, u2, v2]


def element(a, b, material, rotation=None, offset=(0, 0, 0), scale=1):
    base, faces = material if isinstance(material, tuple) else (material, {})
    lo = [a[i] - offset[i] for i in range(3)]
    hi = [b[i] - offset[i] for i in range(3)]
    result = {'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi],
              'faces': {side: {'texture': '#' + faces.get(side, base),
                               'uv': face_uv(side, lo, hi, faces.get(side, base), scale)} for side in SIDES}}
    if rotation:
        origin = [rotation['origin'][i] - offset[i] for i in range(3)]
        result['rotation'] = {'angle': rotation['angle'], 'axis': rotation['axis'], 'origin': [round(v, 4) for v in origin]}
    return result


def model(elements, game):
    textures = dict(TEXTURES, felt=FELT[game], particle='gamblingitems:block/table_wood')
    if game == 'roulette':
        textures.update(ROULETTE_TEXTURES)
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}


def save(relative, value):
    (ASSETS / relative).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def generate():
    write_textures()
    for game, build in GAMES.items():
        f = Furniture()
        common(f)
        build(f)
        for part in range(6):
            ox, oz = (2 - part % 3) * 16, (part // 3) * 16
            elements = []
            for a, b, material, rotation in f.boxes:
                if rotation:
                    centre = rotation['origin']
                    if ox <= centre[0] < ox + 16 and oz <= centre[2] < oz + 16:
                        elements.append(element(a, b, material, rotation, (ox, 0, oz)))
                    continue
                lo = [max(a[0], ox), a[1], max(a[2], oz)]
                hi = [min(b[0], ox + 16), b[1], min(b[2], oz + 16)]
                # The armrest and the foot rail stand a little proud of the front part's edge.
                if oz == 0:
                    lo[2] = a[2] if a[2] < 0 else lo[2]
                if any(lo[i] >= hi[i] for i in range(3)):
                    continue
                elements.append(element(lo, hi, material, offset=(ox, 0, oz)))
            for e in elements:
                for corner in (e['from'], e['to']):
                    assert all(-16 <= v <= 32 for v in corner), (game, part, e)
            save(f'models/block/{game}_table_part{part}.json', model(elements, game))
        variants = {f'facing={direction},part={part}': {
            'model': f'gamblingitems:block/{game}_table_part{part}', 'y': rotation}
            for direction, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
            for part in range(6)}
        save(f'blockstates/{game}_table.json', {'variants': variants})
        # Inventory shows the complete furniture at a third of its size, not a single sixth of the table.
        def small(point):
            return [point[0] / 3, point[1] / 3 + 4, point[2] / 3 + 8 / 3]
        miniature = []
        for a, b, material, rotation in f.boxes:
            turned = None
            if rotation:
                turned = dict(rotation, origin=small(rotation['origin']))
            miniature.append(element(small(a), small(b), material, turned, scale=3))
        save(f'models/block/{game}_table.json', model(miniature, game))
        count = sum(len(json.loads((ASSETS / f'models/block/{game}_table_part{p}.json').read_text())['elements']) for p in range(6))
        print(f'{game}: six joined parts, {count} elements')


if __name__ == '__main__':
    generate()
