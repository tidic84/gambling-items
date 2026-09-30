"""Generate continuous six-block casino furniture using vanilla model geometry.

Run from any directory: python tools/generate_table_models.py
Each part is clipped from one complete piece of furniture. North-facing part 0
is on the east side, matching GameTableBlock.partPos, not the model's left side.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems'
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
TEXTURES = dict(wood='dark_oak_planks', panel='stripped_dark_oak_log',
                dark='black_terracotta', leather='black_concrete',
                brass='gold_block', felt='green_wool', red='red_concrete',
                ivory='white_concrete')


def furniture(game):
    boxes = []
    def box(a, b, material):
        boxes.append((a, b, material))
    # One slab, with an inset shadow line and a substantial wooden apron.
    box((0, 11.6, 0), (48, 13, 32), 'wood')
    box((.3, 11.2, .3), (47.7, 11.6, 31.7), 'brass')
    box((.7, 9.1, .7), (47.3, 11.2, 31.3), 'panel')
    box((1, 8.8, 1), (47, 9.1, 31), 'dark')
    box((.25, 13, .25), (47.75, 13.2, 31.75), 'felt')
    # Raised perimeter only; no rails across the seams between blocks.
    for a, b in [((0, 13, 0), (48, 13.65, .24)),
                 ((0, 13, 31.76), (48, 13.65, 32)),
                 ((0, 13, .24), (.24, 13.65, 31.76)),
                 ((47.76, 13, .24), (48, 13.65, 31.76))]:
        box(a, b, 'leather')
    # Front armrest occupies only the unused edge beyond the control row.
    box((.3, 13.2, .3), (47.7, 13.7, 1.4), 'leather')
    box((.5, 13.7, .45), (47.5, 13.82, 1.15), 'dark')
    # Four shaped legs, brass collars and broad feet.
    for x in (3, 45):
        for z in (3, 29):
            box((x-1.7, 0, z-1.7), (x+1.7, .65, z+1.7), 'dark')
            box((x-1.45, .65, z-1.45), (x+1.45, 1.2, z+1.45), 'brass')
            box((x-1.05, 1.2, z-1.05), (x+1.05, 6.8, z+1.05), 'wood')
            box((x-1.3, 6.8, z-1.3), (x+1.3, 7.2, z+1.3), 'brass')
            box((x-1.55, 7.2, z-1.55), (x+1.55, 9.1, z+1.55), 'panel')
    # Side stretchers and a rear foot rail keep the player side open.
    for x in (3, 45):
        box((x-.6, 2.2, 3), (x+.6, 3.2, 29), 'wood')
    box((3, 2.3, 28.4), (45, 3.1, 29.6), 'brass')
    # Inlaid front panels, with a different motif for each game.
    for x in (8, 18, 30, 40):
        box((x-2.5, 9.55, .62), (x+2.5, 10.75, .7), 'dark')
        box((x-2.1, 10.05, .58), (x+2.1, 10.25, .62), 'brass')
    box((22, 9.3, .5), (26, 11, .7), 'brass')
    box((22.25, 9.5, .42), (25.75, 10.8, .5), 'dark')
    for x, material in ((23, 'ivory'), (24.2, 'red')):
        box((x, 9.7, .36), (x+.8, 10.6, .42), material)
    if game == 'roulette':
        # A second lower stretcher makes the roulette's heavier cabinet distinct.
        box((3, 4, 15.3), (45, 5, 16.7), 'panel')
    return boxes


def element(a, b, material):
    return {'from': [round(v, 4) for v in a], 'to': [round(v, 4) for v in b],
            'faces': {side: {'texture': '#' + material, 'uv': [0, 0, 16, 16]} for side in SIDES}}


def model(elements):
    return {'parent': 'minecraft:block/block', 'textures': {
        **{key: 'minecraft:block/' + value for key, value in TEXTURES.items()},
        'particle': 'minecraft:block/dark_oak_planks'}, 'elements': elements}


def save(relative, value):
    (ASSETS / relative).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def generate():
    for game in ('roulette', 'blackjack'):
        boxes = furniture(game)
        for part in range(6):
            ox, oz = (2 - part % 3) * 16, (part // 3) * 16
            elements = []
            for a, b, material in boxes:
                lo = [max(a[0], ox), a[1], max(a[2], oz)]
                hi = [min(b[0], ox+16), b[1], min(b[2], oz+16)]
                if any(lo[i] >= hi[i] for i in range(3)):
                    continue
                elements.append(element([lo[0]-ox, lo[1], lo[2]-oz],
                                        [hi[0]-ox, hi[1], hi[2]-oz], material))
            save(f'models/block/{game}_table_part{part}.json', model(elements))
        variants = {f'facing={direction},part={part}': {
            'model': f'gamblingitems:block/{game}_table_part{part}', 'y': rotation}
            for direction, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
            for part in range(6)}
        save(f'blockstates/{game}_table.json', {'variants': variants})
        # Inventory shows the complete furniture, not a single sixth of the table.
        miniature = [element([a[0]/3, a[1]/3+4, a[2]/3+8/3],
                             [b[0]/3, b[1]/3+4, b[2]/3+8/3], m) for a, b, m in boxes]
        if game == 'roulette':
            for r, y, mat in [(2.1, 8.7, 'wood'), (1.8, 8.9, 'brass'), (1.6, 9.05, 'red'), (1, 9.2, 'dark'), (.35, 9.4, 'brass')]:
                for row in range(12):
                    z1, z2 = -r + row*r/6, -r + (row+1)*r/6
                    half = math.sqrt(r*r - ((z1+z2)/2)**2)
                    miniature.append(element([12-half, y, 8+z1], [12+half, y+.15, 8+z2], mat))
            for x in range(3, 9):
                for z in range(7, 10):
                    miniature.append(element([x, 8.5, z], [x+.8, 8.54, z+.8], 'red' if (x+z)%2 else 'leather'))
        else:
            for x in (4, 7, 10):
                miniature.append(element([x, 8.5, 7], [x+1.4, 8.6, 9], 'ivory'))
        save(f'models/block/{game}_table.json', model(miniature))
        print(f'{game}: six joined parts, {sum(len(json.loads((ASSETS / f"models/block/{game}_table_part{p}.json").read_text())["elements"]) for p in range(6))} elements')


if __name__ == '__main__':
    generate()
