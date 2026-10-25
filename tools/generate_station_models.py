"""Draw the game stations: a monitor three blocks wide and two high, in a graphite casing with a
brass lip, standing on two feet. The screen itself stays clear, because players click its buttons;
the frame stands round it, outside the six blocks.

    python tools/generate_station_models.py

Boxes are given as in generate_cabinet_models.py, across the whole monitor: u from the player's
left (0) to right (48), y from the floor (0) to the top (32), z from the screen (0) backwards.
They are cut along the six blocks; what sticks out of the outer edges stays with its edge block.
The inventory shows the monitor at a third of its size, with an icon of its game on the screen.
"""
import math
import random

from PIL import Image

from generate_cabinet_models import ASSETS, TEXTURES, TEXTURE_SET, auto_uv, save

STATIONS = ('upgrade', 'trade_up', 'case', 'crash', 'roulette', 'blackjack', 'battle', 'bingo')
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
INF = float('inf')


def monitor():
    boxes = []

    def box(a, b, material):
        boxes.append((a, b, material))

    # The casing behind the glass, and the glass the station draws on.
    box((0, 0, .5), (48, 32, 4), ('cab_graphite', {'south': 'cab_vents'}))
    box((0, 2, 0), (48, 32, .5), 'cab_screen')
    # The bezel: a deep bottom rail, slim sides and top, all outside the screen.
    box((-1.4, 0, -.6), (49.4, 2, 4.4), 'cab_graphite')
    box((-1.4, 1.6, -.75), (49.4, 2, -.6), 'cab_brass')
    box((-1.4, 2, -.6), (0, 32, 4.4), 'cab_graphite')
    box((48, 2, -.6), (49.4, 32, 4.4), 'cab_graphite')
    box((-1.4, 32, -.6), (49.4, 33.4, 4.4), 'cab_graphite')
    # The brass lip all round the front of the bezel.
    box((-1.7, 0, -.8), (-1.2, 33.7, 4.4), 'cab_brass')
    box((48.6 + .6, 0, -.8), (49.7, 33.7, 4.4), 'cab_brass')
    box((-1.2, 33.2, -.8), (49.2, 33.7, 4.4), 'cab_brass')
    # A strip of lamps along the top of the frame, and the speaker grilles at the foot.
    box((4, 32.5, -.7), (44, 33, -.6), 'cab_lamp_amber')
    box((2, .3, -.65), (10, 1.4, -.6), 'cab_grille')
    box((38, .3, -.65), (46, 1.4, -.6), 'cab_grille')
    # Two feet reaching back, so the monitor stands rather than hangs.
    for u in (5, 39):
        box((u, 0, 4), (u + 4, 1.2, 11), 'cab_rubber')
        box((u + 1, 1.2, 4), (u + 3, 6, 5.5), 'cab_graphite')
    return boxes


def element(f, t, material):
    texture, overrides = (material, {}) if isinstance(material, str) else material
    faces = {side: {'texture': '#' + overrides.get(side, texture), 'uv': auto_uv(side, f, t)} for side in SIDES}
    return {'from': [round(v, 4) for v in f], 'to': [round(v, 4) for v in t], 'faces': faces}


def textures_of(boxes, extra=()):
    names = set(extra)
    for _, _, material in boxes:
        if isinstance(material, str):
            names.add(material)
        else:
            names.add(material[0])
            names.update(material[1].values())
    textures = {name: f'gamblingitems:block/{name}' for name in sorted(names)}
    textures['particle'] = 'gamblingitems:block/cab_graphite'
    return textures


def part_models(boxes):
    """Part p: column p % 3 from the player's left, row p // 3 from the floor (GameStationBlock.partPos)."""
    result = []
    for part in range(6):
        column, row = part % 3, part // 3
        lo_u = column * 16 if column > 0 else -INF
        hi_u = column * 16 + 16 if column < 2 else INF
        lo_y = row * 16 if row > 0 else -INF
        hi_y = row * 16 + 16 if row < 1 else INF
        elements = []
        for a, b, material in boxes:
            u1, u2 = max(a[0], lo_u), min(b[0], hi_u)
            y1, y2 = max(a[1], lo_y), min(b[1], hi_y)
            if u1 >= u2 or y1 >= y2:
                continue
            # The player's left is the east of a north-facing block: x = 16 - u, within the block.
            f = (16 - (u2 - column * 16), y1 - row * 16, a[2])
            t = (16 - (u1 - column * 16), y2 - row * 16, b[2])
            elements.append(element(f, t, material))
        result.append(elements)
    return result


def icon(game):
    """The game a station plays, drawn on its screen in the inventory."""
    image = Image.new('RGB', (16, 16), (10, 15, 24))
    gold, green, red, cyan, cream = (217, 178, 90), (40, 170, 100), (210, 67, 79), (76, 195, 217), (243, 238, 226)

    def dot(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            image.putpixel((x, y), c)

    if game == 'upgrade':
        for i in range(5):
            for j in range(-i, i + 1):
                dot(8 + j, 3 + i, green)
        for y in range(8, 13):
            for x in range(6, 11):
                dot(x, y, green)
    elif game == 'trade_up':
        for x in range(3, 8):
            for y in range(9, 13):
                dot(x, y, cyan)
        for i in range(4):
            for j in range(-i, i + 1):
                dot(11 + j, 3 + i, gold)
        for y in range(7, 13):
            dot(11, y, gold)
    elif game == 'case':
        for x in range(3, 13):
            for y in range(6, 13):
                dot(x, y, (150, 100, 50) if y > 7 else gold)
        for x in range(7, 9):
            dot(x, 9, gold)
    elif game == 'crash':
        for x in range(2, 14):
            dot(x, 13 - int((x - 2) ** 1.5 / 4), green)
            dot(x, 14 - int((x - 2) ** 1.5 / 4), green)
        dot(13, 3, red)
        dot(12, 4, red)
    elif game == 'roulette':
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + .5 - 8, y + .5 - 8)
                if d <= 6:
                    sector = int((math.atan2(y - 7.5, x - 7.5) + math.pi) / (2 * math.pi) * 10) % 2
                    dot(x, y, red if sector else (30, 30, 36))
                if 6 < d <= 7:
                    dot(x, y, gold)
                if d <= 1.5:
                    dot(x, y, gold)
    elif game == 'blackjack':
        for ox, c in ((3, cream), (7, cream)):
            for x in range(ox, ox + 6):
                for y in range(3 + (ox - 3) // 2, 12 + (ox - 3) // 2):
                    dot(x, y, c)
        for x, y in ((9, 7), (8, 8), (9, 8), (10, 8), (9, 9)):
            dot(x, y, red)
    elif game == 'battle':
        for i in range(10):
            dot(3 + i, 3 + i, (200, 205, 215))
            dot(12 - i, 3 + i, (200, 205, 215))
        for x, y in ((4, 11), (11, 11), (3, 12), (12, 12)):
            dot(x, y, gold)
    else:
        rng = random.Random(4)
        for row in range(3):
            for column in range(3):
                c = rng.choice((red, cyan, gold, green))
                for x in range(3 + column * 4, 5 + column * 4):
                    for y in range(3 + row * 4, 5 + row * 4):
                        dot(x, y, c)
    return image


def main():
    for name in ('cab_graphite', 'cab_vents', 'cab_screen', 'cab_brass', 'cab_lamp_amber', 'cab_grille', 'cab_rubber'):
        TEXTURE_SET[name]().save(TEXTURES / f'{name}.png', optimize=True)
    boxes = monitor()
    textures = textures_of(boxes)
    for part, elements in enumerate(part_models(boxes)):
        save(f'models/block/station_part{part}.json',
             {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements})
    for game in STATIONS:
        icon(game).save(TEXTURES / f'station_icon_{game}.png', optimize=True)
        # The inventory: the whole monitor at a third of its size, its screen showing the game.
        scale, elements = 1 / 3, []
        for a, b, material in boxes:
            f = (16 - b[0] * scale, a[1] * scale + 3, a[2] * scale + 6)
            t = (16 - a[0] * scale, b[1] * scale + 3, b[2] * scale + 6)
            if material == 'cab_screen':
                material = ('cab_screen', {'north': 'icon'})
            elements.append(element(f, t, material))
        icon_textures = dict(textures_of(boxes))
        icon_textures['icon'] = f'gamblingitems:block/station_icon_{game}'
        for element_value in elements:
            if element_value['faces']['north']['texture'] == '#icon':
                element_value['faces']['north']['uv'] = [0, 0, 16, 16]
        save(f'models/block/{game}_station.json', {'parent': 'minecraft:block/block', 'textures': icon_textures,
                                                   'elements': elements})
        save(f'blockstates/{game}_station.json', {'variants': {
            f'facing={facing},part={part}': {'model': f'gamblingitems:block/station_part{part}', 'y': rotation}
            for facing, rotation in (('north', 0), ('east', 90), ('south', 180), ('west', 270))
            for part in range(6)}})
    print('stations:', [len(p) for p in part_models(boxes)], 'elements per part')


if __name__ == '__main__':
    main()
