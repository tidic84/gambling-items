"""Build native Minecraft handheld models. Each glyph is geometry, not a font or bitmap.

Run: python tools/generate_handheld_models.py
Optional contact sheet: python tools/generate_handheld_models.py --preview (requires Pillow).
"""
from pathlib import Path
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems/models/item'
PALETTE = {
    'W': ('white_concrete', '#f3efe4'), 'D': ('gray_concrete', '#465161'),
    'C': ('cyan_concrete', '#36cbd5'), 'B': ('blue_concrete', '#567beb'),
    'Y': ('yellow_concrete', '#edc34c'), 'O': ('orange_concrete', '#d98735'),
    'R': ('red_concrete', '#dc4856'), 'G': ('lime_concrete', '#8ed253'),
    'P': ('purple_concrete', '#b774db'), 'K': ('black_concrete', '#111924'),
}
# Thirteen by thirteen glyphs, with strong silhouettes even at inventory scale.
GLYPHS = {
    'upgrader': ('UPGRADER', 'C', [
        '......C......', '.....CCC.....', '....CCWCC....', '...CCWWWCC...',
        '..CCCCWCCCC..', '.....CWC.....', '.....CWC.....', '.....CCC.....',
        '.............', '..WWW...CCC..', '.WWWWW.CCCCC.', '..WWW...CCC..', '.............']),
    'trade_up': ('TRADE UP', 'B', [
        '.............', '........B....', '..BBBBBBBB...', '..BWWWWWWBB..',
        '..B.....BBB..', '........B....', '.............', '....C........',
        '..CCC.....C..', '..CCWWWWWWC..', '...CCCCCCCC..', '....C........', '.............']),
    'case_opening': ('CASES', 'Y', [
        '.............', '...YYYYYYY...', '..YWWWWWWWY..', '.YYYYYYYYYYY.',
        '.YOOOOOOOOOY.', '.YYYYYYYYYYY.', '.YOOOOWOOOOY.', '.YOOOOWOOOOY.',
        '.YOOOOOOOOOY.', '.YOOOOOOOOOY.', '.YYYYYYYYYYY.', '..DDDDDDDDD..', '.............']),
    'crash': ('CRASH', 'R', [
        '..........R..', '.......R.RRR.', '.........RRR.', '.W......R.R..',
        '.W.....RR....', '.W....RR.....', '.W...RR......', '.W..RR.......',
        '.W.RR........', '.WRR.........', '.WWWWWWWWWWW.', '.............', '.............']),
    'roulette': ('ROULETTE', 'R', [
        '.....GGG.....', '...WWGGGRR...', '..WWWWGRRRR..', '.RRWWWGRRWWW.',
        '.RRRWWGWWWWW.', 'RRRRRWWWRRRRR', 'RRRRWWYWWRRRR',
        'WWWWWWWWRRRRR', '.WWWWWGWWRRR.', '.WWWRRGWWWRR.',
        '..RRRRGWWWW..', '...RRRGWWR...', '.....GGG.....']),
    'blackjack': ('BLACKJACK', 'W', [
        'WWWWWWW......', 'WKKKKKW......', 'WKRRRKW......', 'WKRKRKW......',
        'WKRRRKWWWWWWW', 'WKRKRKWWKKKKW', 'WKKKKKWWKKKKW', 'WWWWWWWWKRKRW',
        '......WWRRRRW', '......WWKRRKW', '......WWKKKKW', '......WWKKKKW', '......WWWWWWW']),
    'case_battle': ('CASE BATTLE', 'P', [
        'WW.........WW', '.WW.......WW.', '..WW.....WW..', '...WW...WW...',
        '....WW.WW....', '.....WWW.....', '.....WWW.....', '....PWWWP....',
        '...PP.W.PP...', '..PP.....PP..', '.P..PPPPP..P.', '....PWYWP....', '....PPPPP....']),
    'bingo': ('BINGO', 'G', [
        '.GGGGGGGGGGG.', '.GWWWWWWWWWG.', '.GGGGGGGGGGG.', '.GWGGWGGWGGG.',
        '.GWGGWGGWGGG.', '.GWWWWWWWWWG.', '.GWGGWRRWGGG.', '.GWGGWRRWGGG.',
        '.GWWWWWWWWWG.', '.GWGGWGGWGGG.', '.GWGGWGGWGGG.', '.GGGGGGGGGGG.', '.............']),
    'slot_machine': ('SLOTS', 'R', [
        '..RRRRRRRRR..', '.RRRRRRRRRRR.', 'DDDDDDDDDDDDD', 'DWWWWWWWWWWWD',
        'DRRRWRRRWRRRD', 'DWWRWWWRWWWRD', 'DWRWWWRWWWRWD', 'DWRWWWRWWWRWD',
        'DWWWWWWWWWWWD', 'DDDDDDDDDDDDD', '..RRRRRRRRR..', '...RWWWWWR...', '..RRRRRRRRR..']),
}


def cube(start, end, texture):
    return {'from': start, 'to': end, 'faces': {
        side: {'texture': '#' + texture, 'uv': [2, 2, 14, 14]}
        for side in ('north', 'south', 'east', 'west', 'up', 'down')}}


def disk(radius, near, far, texture):
    """A round pixel silhouette, made from merged half-pixel horizontal strips."""
    bands = []
    for row in range(32):
        bottom, top = row / 2, (row + 1) / 2
        distance = max(abs(bottom - 8), abs(top - 8))
        if distance >= radius:
            continue
        half = math.floor(math.sqrt(radius * radius - distance * distance) * 2) / 2
        if half <= 0:
            continue
        if bands and bands[-1][2] == half and bands[-1][1] == bottom:
            bands[-1][1] = top
        else:
            bands.append([bottom, top, half])
    return [cube([8 - half, bottom, near], [8 + half, top, far], texture)
            for bottom, top, half in bands]


def build():
    # Vanilla item/generated rotations and offsets, with a smaller hand scale.
    # Both faces carry a readable glyph, including when viewed from the offhand.
    display = {
        'gui': {'rotation': [0, 0, 0], 'scale': [1, 1, 1]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [.45, .45, .45]},
        'firstperson_lefthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [.45, .45, .45]},
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [.4, .4, .4]},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [.4, .4, .4]},
        'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0]},
    }
    textures = {key: 'minecraft:block/' + block for key, (block, _) in PALETTE.items()}
    textures['particle'] = 'minecraft:block/black_concrete'
    parent = {'parent': 'minecraft:block/block', 'gui_light': 'front', 'textures': textures, 'display': display}
    (MODELS / 'handheld.json').write_text(json.dumps(parent, indent=2) + '\n', encoding='utf-8', newline='\n')
    for mode, (_, accent, rows) in GLYPHS.items():
        assert len(rows) == 13 and all(len(row) == 13 for row in rows), mode
        assert set(''.join(rows)) <= set(PALETTE) | {'.'}, mode
        elements = (disk(7.5, 7.4, 8.6, 'D') + disk(7.1, 7.32, 8.68, accent)
                    + disk(6.6, 7.28, 8.72, 'K'))
        # Merge horizontal runs to keep the model small and avoid coplanar faces.
        for y, row in enumerate(rows):
            x = 0
            while x < len(row):
                ink = row[x]
                end = x + 1
                while end < len(row) and row[end] == ink:
                    end += 1
                if ink != '.':
                    unit = 9.25 / 13
                    left, right = round(3.375 + x * unit, 5), round(3.375 + end * unit, 5)
                    bottom, top = round(12.625 - (y + 1) * unit, 5), round(12.625 - y * unit, 5)
                    elements.append(cube([left, bottom, 8.72], [right, top, 8.82], ink))
                    elements.append(cube([16 - right, bottom, 7.18], [16 - left, top, 7.28], ink))
                x = end
        # Keep resource diffs readable: one cuboid per line instead of hundreds of face lines.
        text = '{\n  "parent": "gamblingitems:item/handheld",\n  "elements": [\n'
        text += ',\n'.join('    ' + json.dumps(element, separators=(',', ':')) for element in elements)
        text += '\n  ]\n}\n'
        (MODELS / (mode + '_item.json')).write_text(text, encoding='utf-8', newline='\n')


def preview():
    from PIL import Image, ImageDraw, ImageFont
    sheet = Image.new('RGB', (960, 990), '#0d131c')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('C:/Windows/Fonts/consolab.ttf', 20)
    for i, (mode, (label, _, _)) in enumerate(GLYPHS.items()):
        x, y = (i % 3) * 320 + 32, (i // 3) * 330 + 20
        model = json.loads((MODELS / (mode + '_item.json')).read_text())
        for element in sorted(model['elements'], key=lambda element: element['to'][2]):
            a, b = element['from'], element['to']
            ink = element['faces']['north']['texture'][1:]
            draw.rectangle((round(x + a[0] * 16), round(y + (16 - b[1]) * 16),
                            round(x + b[0] * 16) - 1, round(y + (16 - a[1]) * 16) - 1),
                           fill=PALETTE[ink][1])
        draw.text((x + 128, y + 289), label, fill='#e8eff6', font=font, anchor='mm')
    path = ROOT / 'build/handheld-icons-preview.png'
    path.parent.mkdir(exist_ok=True)
    sheet.save(path)
    print(path)


if __name__ == '__main__':
    build()
    if '--preview' in sys.argv:
        preview()
