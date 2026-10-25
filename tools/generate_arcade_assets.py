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
    """The glass of a lit tube: white hot in the middle, its colour towards the edges."""
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            d = abs(x + .5 - 8) / 8
            image.putpixel((x, y), clamp(mix(mix(colour, (255, 255, 255), 0.75), colour, min(1, d * 1.6))))
    return image


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


def felt(seed, base=(16, 70, 46)):
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(c + rng.uniform(-4, 4) for c in base)))
    return image


def counter_front(seed):
    """The cashier's front: polished wood with a brass grille window and a lit sign band."""
    image = wood(seed)
    for x in range(16):
        image.putpixel((x, 1), (230, 190, 90))
        image.putpixel((x, 2), (250, 220, 120))
    for y in range(4, 12):
        for x in range(3, 13):
            image.putpixel((x, y), (20, 24, 32) if (x + y) % 3 else (190, 150, 70))
    for x in range(16):
        image.putpixel((x, 14), (190, 150, 70))
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
    save_png('block/neon_cap.png', Image.new('RGB', (16, 16), (60, 64, 72)))
    for name, colour in DYES.items():
        save_png(f'block/neon_{name}.png', neon(colour))
        save(f'models/block/neon_tube_{name}.json', {
            'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'tube': f'gamblingitems:block/neon_{name}', 'cap': 'gamblingitems:block/neon_cap',
                         'particle': f'gamblingitems:block/neon_{name}'},
            'elements': [box((7, 0, 7), (9, 1.5, 9), 'cap'), box((7.25, 1.5, 7.25), (8.75, 14.5, 8.75), 'tube'),
                         box((7, 14.5, 7), (9, 16, 9), 'cap')]})
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

    # The cashier's counter: a wooden counter with a felt top, a brass grille window and a chip rack.
    save_png('block/cashier_wood.png', wood(61))
    save_png('block/cashier_front.png', counter_front(62))
    save_png('block/cashier_felt.png', felt(63))
    save_png('block/cashier_brass.png', Image.new('RGB', (16, 16), (200, 160, 80)))
    chip_tex = {f'chip_{face}': f'gamblingitems:item/chip_{face}' for face in CHIPS}
    rack = []
    for index, face in enumerate(CHIPS):
        u = 3 + index * 2.6
        rack.append(box((u, 15, 9), (u + 2.2, 15.6 + index * 0.4, 13), f'chip_{face}'))
    save('models/block/cashier.json', {
        'parent': 'minecraft:block/block',
        'textures': dict({'wood': 'gamblingitems:block/cashier_wood', 'front': 'gamblingitems:block/cashier_front',
                          'felt': 'gamblingitems:block/cashier_felt', 'brass': 'gamblingitems:block/cashier_brass',
                          'particle': 'gamblingitems:block/cashier_wood'}, **chip_tex),
        'elements': [box((0, 0, 0), (16, 13, 16), 'wood', {'north': 'front'}),
                     box((-0.5, 13, -0.5), (16.5, 14, 16.5), 'brass'),
                     box((0, 14, 0), (16, 15, 16), 'felt'),
                     *rack]})
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
