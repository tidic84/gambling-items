"""Draw the arcade's decor and chips: textures, block and item models, blockstates.

    python tools/generate_arcade_assets.py

Covers the casino chips, the cashier's counter, the leaderboard's wall plate, the croupier (drawn by
its renderer in the world; the model here is the one shown in an inventory), the neon tubes in the
sixteen dye colours, and the two casino carpets. Needs Pillow; every pixel is computed, so running it
twice writes the same files.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems'
CHIPS = {1: (232, 228, 220), 5: (200, 48, 60), 25: (31, 138, 76), 100: (28, 28, 34)}
DYES = {'white': (249, 255, 254), 'orange': (249, 128, 29), 'magenta': (199, 78, 189), 'light_blue': (58, 179, 218),
        'yellow': (254, 216, 61), 'lime': (128, 199, 31), 'pink': (243, 139, 170), 'gray': (71, 79, 82),
        'light_gray': (157, 157, 151), 'cyan': (22, 156, 156), 'purple': (137, 50, 184), 'blue': (60, 68, 170),
        'brown': (131, 84, 50), 'green': (94, 124, 22), 'red': (176, 46, 38), 'black': (29, 29, 33)}


def clamp(colour):
    return tuple(max(0, min(255, int(round(c)))) for c in colour)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def save_png(relative, image):
    path = ASSETS / 'textures' / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def save(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def chip(face, colour):
    """A chip seen from above: its colour, six white edge spots, a white ring and the face value inside."""
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    edge = (245, 242, 232) if colour[0] < 200 else (40, 90, 200)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - 8, y + .5 - 8)
            if d > 7.6:
                continue
            angle = (math.atan2(y + .5 - 8, x + .5 - 8) + math.pi) / (2 * math.pi) * 6
            c = colour
            if d > 6 and int(angle * 2) % 2 == 0:
                c = edge
            elif 4.6 < d <= 5.3:
                c = edge
            shade = 1.08 - 0.14 * (y / 15)
            image.putpixel((x, y), clamp(tuple(v * shade for v in c)) + (255,))
    digits = {1: ['.#.', '##.', '.#.', '.#.', '###'], 5: ['###', '#..', '##.', '..#', '##.'],
              2: ['##.', '..#', '.#.', '#..', '###'], 0: ['###', '#.#', '#.#', '#.#', '###']}
    text = [int(c) for c in str(face)]
    width = len(text) * 4 - 1
    left = 8 - width // 2
    ink = edge if face != 1 else (40, 40, 48)
    for index, digit in enumerate(text):
        for row, line in enumerate(digits[digit]):
            for col, mark in enumerate(line):
                if mark == '#':
                    image.putpixel((left + index * 4 + col, 6 + row), clamp(ink) + (255,))
    return image


def neon(colour):
    """The glass of a lit tube: a hot core a little whiter than its colour, the colour itself saturated and
    brightened towards the edges, so the tube reads as light and not as painted glass."""
    peak = max(colour)
    vivid = tuple(c * 255 / peak if peak else 255 for c in colour)
    vivid = mix(vivid, (255, 255, 255), 0.12)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            d = abs(x + .5 - 8) / 8
            image.putpixel((x, y), clamp(mix(mix(vivid, (255, 255, 255), 0.55), vivid, min(1, d * 2.2))))
    return image


def glowing(element):
    """A lit element: full bright whatever the light around it (1.21.4+ reads light_emission), and unshaded."""
    element['light_emission'] = 15
    element['shade'] = False
    return element


def carpet(base, accent, seed):
    """A casino carpet: a dark ground with a repeating pattern of diamonds and stars in two colours."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            c = base
            dx, dy = abs((x % 8) - 3.5), abs((y % 8) - 3.5)
            if dx + dy < 2:
                c = accent
            elif abs(dx + dy - 3) < 0.6:
                c = mix(accent, base, 0.55)
            if (x + y) % 8 == 0 and (x - y) % 8 == 0:
                c = (240, 200, 90)
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-6, 6) for v in c)))
    return image


def wood(seed, base=(92, 46, 24)):
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        stripe = math.sin(y * 1.3) * 6
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(c + stripe + rng.uniform(-5, 5) for c in base)))
    return image


def marble(seed):
    """A white marble counter top with soft grey veins running across it."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    phase = [rng.uniform(0, 6.3) for _ in range(2)]
    for y in range(16):
        for x in range(16):
            vein = abs(math.sin(x * 0.45 + y * 0.9 + math.sin(y * 0.6 + phase[0]) * 2.2 + phase[1]))
            c = mix((236, 232, 226), (150, 146, 150), max(0, 0.18 - vein) / 0.18 * 0.8)
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-4, 4) for v in c)))
    return image


def raised_panel(seed):
    """A moulded wooden panel: a dark frame, a bevel lit from the top left, a raised field of grain."""
    image = wood(seed, (84, 40, 22))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            c = image.getpixel((x, y))
            if edge == 0:
                c = mix(c, (30, 14, 8), 0.7)
            elif edge in (1, 2):
                c = mix(c, (255, 220, 170), 0.18) if x + y < 15 else mix(c, (20, 10, 6), 0.35)
            elif edge == 3:
                c = mix(c, (210, 170, 80), 0.55)
            image.putpixel((x, y), clamp(c))
    return image


def polished_brass():
    """Brass with a soft highlight across it, so bars and rims read as metal."""
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            t = 0.5 + 0.5 * math.cos((x + y * 0.3) / 16 * math.pi * 2)
            image.putpixel((x, y), clamp(mix((150, 108, 44), (250, 214, 120), t)))
    return image


def register():
    """The cash register's face: a green read-out on top, rows of cream keys, a brass-edged drawer."""
    image = Image.new('RGB', (16, 16), (40, 42, 48))
    for y in range(16):
        for x in range(16):
            if y < 4:
                image.putpixel((x, y), (40, 180, 110) if 2 <= x <= 13 and 1 <= y <= 2 else (24, 26, 30))
            elif y < 12 and x % 3 != 0 and y % 2 == 1:
                image.putpixel((x, y), (226, 218, 196) if (x + y) % 5 else (210, 70, 60))
            elif y >= 13:
                image.putpixel((x, y), (180, 140, 60) if y == 13 else (52, 54, 60))
    return image


def lamp_glass():
    """The green glass shade of a banker's lamp, lit from inside towards its rim."""
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(mix((20, 96, 52), (90, 220, 130), y / 15)))
    return image


def box(f, t, texture, faces=None, rotation=None):
    faces = faces or {}
    element = {'from': list(f), 'to': list(t), 'faces': {}}
    for side in ('down', 'up', 'north', 'south', 'west', 'east'):
        element['faces'][side] = {'texture': '#' + faces.get(side, texture)}
    if rotation:
        element['rotation'] = rotation
    return element


def horizontal_variants(model):
    return {'variants': {f'facing={facing}': {'model': model, 'y': rotation}
                         for facing, rotation in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}}


def main():
    for face, colour in CHIPS.items():
        save_png(f'item/chip_{face}.png', chip(face, colour))
        save(f'models/item/chip_{face}.json', {'parent': 'minecraft:item/generated',
                                               'textures': {'layer0': f'gamblingitems:item/chip_{face}'}})

    # The neon tubes: one model per colour, standing up; the blockstate lays it along its facing.
    save_png('block/neon_cap.png', Image.new('RGB', (16, 16), (92, 98, 108)))
    for name, colour in DYES.items():
        save_png(f'block/neon_{name}.png', neon(colour))
        save(f'models/block/neon_tube_{name}.json', {
            'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'tube': f'gamblingitems:block/neon_{name}', 'cap': 'gamblingitems:block/neon_cap',
                         'particle': f'gamblingitems:block/neon_{name}'},
            'elements': [box((6.5, 0, 6.5), (9.5, 1.5, 9.5), 'cap'), glowing(box((6.75, 1.5, 6.75), (9.25, 14.5, 9.25), 'tube')),
                         box((6.5, 14.5, 6.5), (9.5, 16, 9.5), 'cap')]})
    rotations = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
                 'east': {'x': 90, 'y': 90}, 'west': {'x': 90, 'y': 270}}
    save('blockstates/neon_tube.json', {'variants': {
        f'color={name},facing={facing}': dict({'model': f'gamblingitems:block/neon_tube_{name}'}, **rotation)
        for name in DYES for facing, rotation in rotations.items()}})
    save('models/item/neon_tube.json', {'parent': 'gamblingitems:block/neon_tube_pink'})

    # The carpets.
    for name, base, accent, seed in (('casino_carpet', (70, 14, 24), (190, 40, 60), 51),
                                     ('casino_carpet_blue', (14, 24, 66), (40, 90, 200), 52)):
        save_png(f'block/{name}.png', carpet(base, accent, seed))
        save(f'models/block/{name}.json', {'parent': 'minecraft:block/carpet', 'textures': {'wool': f'gamblingitems:block/{name}'}})
        save(f'blockstates/{name}.json', {'variants': {'': {'model': f'gamblingitems:block/{name}'}}})
        save(f'models/item/{name}.json', {'parent': f'gamblingitems:block/{name}'})

    # The cashier's wicket: a moulded wooden counter under a marble top, a brass grille with its pass-through,
    # and behind it the clerk's desk with a banker's lamp, the cash register and the racks of chips.
    save_png('block/cashier_wood.png', wood(61, (74, 34, 18)))
    save_png('block/cashier_panel.png', raised_panel(62))
    save_png('block/cashier_marble.png', marble(63))
    save_png('block/cashier_brass.png', polished_brass())
    save_png('block/cashier_register.png', register())
    save_png('block/cashier_lamp.png', lamp_glass())
    for stale in ('cashier_front', 'cashier_felt'):
        (ASSETS / 'textures/block' / f'{stale}.png').unlink(missing_ok=True)
    chip_tex = {f'chip_{face}': f'gamblingitems:item/chip_{face}' for face in CHIPS}
    faces = list(CHIPS)
    parts = [
        # The base: a dark plinth, the carcass, three raised panels and a brass rail along the top.
        box((.4, 0, 2.4), (15.6, 1, 15.6), 'dark'),
        box((0, 1, 2), (16, 10, 16), 'wood'),
        *[box((u, 2, 1.7), (u + 4.4, 9, 2), 'panel') for u in (.8, 5.8, 10.8)],
        box((0, 9.4, 1.6), (16, 9.8, 2), 'brass'),
        # The marble counter, overhanging the customer's side, with its brass nosing.
        box((-.3, 10, .4), (16.3, 11, 16.3), 'marble'),
        box((-.3, 9.8, .2), (16.3, 10.2, .6), 'brass'),
        # The grille across the counter: posts, a top rail, bars, and the pass-through under its arch.
        box((0, 11, 7), (.8, 16, 7.8), 'brass'), box((15.2, 11, 7), (16, 16, 7.8), 'brass'),
        box((0, 15.2, 7), (16, 16, 7.8), 'brass'),
        box((5.2, 13.6, 7.1), (10.8, 14.1, 7.7), 'brass'),
        *[box((u, 11, 7.2), (u + .35, 15.2, 7.6), 'brass') for u in (1.6, 2.8, 4, 11.65, 12.85, 14.05)],
        *[box((u, 14.1, 7.2), (u + .35, 15.2, 7.6), 'brass') for u in (5.6, 6.9, 8.75, 10.05)],
        box((5.2, 11, 6.6), (10.8, 11.2, 8.2), 'brass'),
        # The customer's side: a chip tray with a stack of each colour, and a brass bell.
        box((1, 11, 1.2), (8, 11.5, 5.4), 'wood'),
        *[box((1.5 + i * 1.6, 11.5, 1.8), (2.7 + i * 1.6, 11.9 + i * .5, 4.8), f'chip_{face}') for i, face in enumerate(faces)],
        box((12, 11, 2.2), (14, 11.4, 4.2), 'dark'), box((12.4, 11.4, 2.6), (13.6, 12.4, 3.8), 'brass'),
        box((12.85, 12.4, 3.05), (13.15, 12.9, 3.35), 'brass'),
        # The clerk's side: the register, the banker's lamp, a rack of chips, and the back panel.
        box((9.6, 11, 9.4), (14.6, 13.2, 13.6), 'dark', {'north': 'register'}),
        box((9.8, 13.2, 11.4), (14.4, 14.4, 13.6), 'dark'),
        box((2, 11, 10.8), (4.6, 11.4, 13.4), 'brass'), box((3.1, 11.4, 11.9), (3.5, 14.2, 12.3), 'brass'),
        box((1.4, 14.2, 11.2), (5.2, 15.2, 13), 'lamp'),
        *[box((5.6 + i * .9, 11, 13.8), (6.4 + i * .9, 12.4 + (i % 2) * .6, 15), f'chip_{face}') for i, face in enumerate(faces)],
        box((0, 11, 15.4), (16, 16, 16), 'panel'),
    ]
    save('models/block/cashier.json', {
        'parent': 'minecraft:block/block',
        'textures': dict({'wood': 'gamblingitems:block/cashier_wood', 'panel': 'gamblingitems:block/cashier_panel',
                          'marble': 'gamblingitems:block/cashier_marble', 'brass': 'gamblingitems:block/cashier_brass',
                          'register': 'gamblingitems:block/cashier_register', 'lamp': 'gamblingitems:block/cashier_lamp',
                          'dark': 'gamblingitems:block/croupier_dark', 'particle': 'gamblingitems:block/cashier_wood'},
                         **chip_tex),
        'elements': parts})
    save('blockstates/cashier.json', horizontal_variants('gamblingitems:block/cashier'))
    save('models/item/cashier.json', {'parent': 'gamblingitems:block/cashier'})

    # The leaderboard's wall plate; the board itself is drawn by its renderer, two blocks wide in front of it.
    save('models/block/leaderboard.json', {
        'parent': 'minecraft:block/block',
        'textures': {'plate': 'gamblingitems:block/cab_obsidian', 'trim': 'gamblingitems:block/cab_gold',
                     'particle': 'gamblingitems:block/cab_obsidian'},
        'elements': [box((1, 1, 14), (15, 15, 16), 'plate'), box((0, 0, 13.6), (16, 1, 16), 'trim'),
                     box((0, 15, 13.6), (16, 16, 16), 'trim')]})
    save('blockstates/leaderboard.json', horizontal_variants('gamblingitems:block/leaderboard'))
    save('models/item/leaderboard.json', {
        'parent': 'minecraft:block/block',
        'textures': {'screen': 'gamblingitems:block/cab_screen', 'trim': 'gamblingitems:block/cab_gold',
                     'particle': 'gamblingitems:block/cab_obsidian'},
        'elements': [box((0, 2, 7), (16, 14, 9), 'trim', {'north': 'screen', 'south': 'screen'})],
        'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.625, 0.625, 0.625]}}})

    # The croupier: invisible as a block (its renderer draws it); a little figure in an inventory.
    save('models/block/croupier.json', {'textures': {'particle': 'gamblingitems:block/cab_crimson'}})
    save('blockstates/croupier.json', horizontal_variants('gamblingitems:block/croupier'))
    save_png('block/croupier_skin.png', Image.new('RGB', (16, 16), (217, 162, 122)))
    save_png('block/croupier_shirt.png', Image.new('RGB', (16, 16), (243, 238, 226)))
    save_png('block/croupier_dark.png', Image.new('RGB', (16, 16), (28, 28, 34)))
    save('models/item/croupier.json', {
        'parent': 'minecraft:block/block',
        'textures': {'skin': 'gamblingitems:block/croupier_skin', 'shirt': 'gamblingitems:block/croupier_shirt',
                     'vest': 'gamblingitems:block/cab_crimson', 'dark': 'gamblingitems:block/croupier_dark',
                     'particle': 'gamblingitems:block/cab_crimson'},
        'elements': [box((5.6, 0, 7), (7.8, 6, 9), 'dark'), box((8.2, 0, 7), (10.4, 6, 9), 'dark'),
                     box((4.6, 6, 6.6), (11.4, 11, 9.4), 'vest'), box((3.4, 6.4, 7), (4.6, 11, 9), 'shirt'),
                     box((11.4, 6.4, 7), (12.6, 11, 9), 'shirt'), box((5.5, 11, 5.5), (10.5, 15, 10.5), 'skin'),
                     box((5.3, 14, 5.3), (10.7, 15.4, 10.7), 'dark')],
        'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.9, 0.9, 0.9]}}})
    print('arcade assets written')


if __name__ == '__main__':
    main()
