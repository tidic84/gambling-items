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
# Where the game draws its pieces on the felt, in model space (see the canvas mapping above).
WHEEL = (39.5, 18.25)        # the roulette wheel, canvas (-124, -18), radius 8
CUP = (41.5, 11.75)          # the bingo cup, canvas (-140, 34), radius 3.75
TOP = 13.28                  # the felt StationTables draws on, 0.83 of a block


# --- Textures ---------------------------------------------------------------------------------

def leather(seed):
    """Tufted oxblood leather: diamond creases between deep buttons, a soft sheen on each cushion."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = (x + .5) % 8 - 4, (y + .5) % 8 - 4
            cushion = 1 - (abs(dx) + abs(dy)) / 8
            c = mix((46, 12, 14), (118, 34, 38), cushion)
            if abs(abs(dx) + abs(dy) - 4) < .7:
                c = (30, 8, 10)
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-3, 3) for v in c)))
    for bx, by in ((0, 0), (8, 0), (0, 8), (8, 8), (4, 4), (12, 4), (4, 12), (12, 12)):
        if (bx + by) % 8 == 0:
            image.putpixel((bx, by), (196, 156, 80))
    return image


def lacquer():
    """Black lacquer with a faint brush grain, for the plinths and the trims."""
    rng = random.Random(7)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-3, 3) + math.sin(x * .8) * 2 for v in (22, 20, 24))))
    return image


def cloth(base, seed):
    """Casino baize: a fine twill, a little lighter along every other diagonal."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            twill = 5 if (x + y) % 4 < 2 else -3
            image.putpixel((x, y), clamp(tuple(v + twill + rng.uniform(-3, 3) for v in base)))
    return image


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

    def disc(self, centre, radius, y1, y2, material):
        """A sixteen-sided plate lying flat: eight bands through the centre, each as thick as one side."""
        half = radius * math.tan(math.pi / 16)
        for angle in (0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5):
            self.turned(centre, 2 * radius, 2 * half, y1, y2, angle, material)

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
    # The armrest along the players' edge: a rounded cushion of tufted leather, brass studs along its face.
    f.box((0, 13, -.6), (48, 13.7, 2.4), 'leather')
    f.box((.15, 13.7, -.45), (47.85, 14.4, 2.2), 'leather')
    f.box((.4, 14.4, -.1), (47.6, 14.75, 1.7), 'leather')
    for x in range(1, 48, 2):
        f.box((x, 13.25, -.75), (x + .4, 13.55, -.6), 'brass')
    # Narrow rails up the sides, clear of the controls printed on the felt, and the dealer's straight back edge.
    for x1, x2 in ((0, .55), (47.45, 48)):
        f.box((x1, 13, 2.4), (x2, 14, 32), 'leather')
    f.box((.55, 13, 31.45), (47.45, 13.9, 32), 'wood')
    f.box((.55, 13.9, 31.6), (47.45, 14.05, 31.9), 'brass')


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
    """Four turned legs under stretchers, and a wooden bowl round the wheel with a brass lip."""
    for x in (4, 44):
        for z in (4, 28):
            for w, y1, y2, material in ((3.4, 0, .8, 'dark'), (2.6, .8, 1.5, 'brass'), (1.6, 1.5, 4.6, 'wood'),
                                        (2.6, 4.6, 5.3, 'brass'), (2.3, 5.3, 7.2, 'wood'), (1.7, 7.2, 8.3, 'dark')):
                f.box((x - w / 2, y1, z - w / 2), (x + w / 2, y2, z + w / 2), material)
    for z in (4, 28):
        f.box((5, 3, z - .5), (43, 3.8, z + .5), 'wood')
    f.box((23.5, 3.1, 4.5), (24.5, 3.7, 27.5), 'wood')
    # The bowl: StationTables draws the wheel inside radius 8; the rim stands round it.
    f.disc(WHEEL, 8.4, 13, 14, 'wood')
    f.ring(WHEEL, 8.45, .3, 13.9, 14.15, 'brass')
    # A brass chip rake laid along the dealer's edge.
    f.box((14, 13.95, 29.4), (30, 14.2, 29.8), 'brass')
    f.box((12.8, 13.95, 28.8), (14, 14.3, 30.4), 'dark')


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


def element(a, b, material, rotation=None, offset=(0, 0, 0)):
    base, faces = material if isinstance(material, tuple) else (material, {})
    lo = [a[i] - offset[i] for i in range(3)]
    hi = [b[i] - offset[i] for i in range(3)]
    result = {'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi],
              'faces': {side: {'texture': '#' + faces.get(side, base), 'uv': [0, 0, 16, 16]} for side in SIDES}}
    if rotation:
        origin = [rotation['origin'][i] - offset[i] for i in range(3)]
        result['rotation'] = {'angle': rotation['angle'], 'axis': rotation['axis'], 'origin': [round(v, 4) for v in origin]}
    return result


def model(elements, game):
    textures = dict(TEXTURES, felt=FELT[game], particle='gamblingitems:block/table_wood')
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
            miniature.append(element(small(a), small(b), material, turned))
        save(f'models/block/{game}_table.json', model(miniature, game))
        count = sum(len(json.loads((ASSETS / f'models/block/{game}_table_part{p}.json').read_text())['elements']) for p in range(6))
        print(f'{game}: six joined parts, {count} elements')


if __name__ == '__main__':
    generate()
