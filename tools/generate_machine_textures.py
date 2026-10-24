"""Draw the block textures of the casino cabinets: brushed steel, brass, the dark plinth, the roof,
and one front per machine. Every texture is computed, so the same script always draws the same
pixels; run it again after changing a colour here.

    python tools/generate_machine_textures.py

Writes 16x16 PNGs under the canonical resources. Needs Pillow.
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems/textures/block'

NIGHT = (14, 22, 34)
STEEL = (27, 36, 48)
STEEL_LIGHT = (43, 58, 76)
BRASS = (176, 141, 67)
BRASS_LIGHT = (217, 178, 90)
BRASS_DARK = (107, 86, 49)
DARK = (10, 16, 25)
RED = (150, 36, 48)
GREEN = (23, 120, 74)
CYAN = (76, 195, 217)
CREAM = (243, 238, 226)
TIERS = [(42, 49, 61), (74, 95, 120), (61, 125, 140), (23, 120, 74), (76, 195, 217), (217, 178, 90), (210, 67, 79)]


def shade(colour, amount):
    return tuple(max(0, min(255, int(channel + amount))) for channel in colour)


def brushed(base, seed, spread=6):
    """A metal plate brushed along its width: every row a little lighter or darker."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16), base)
    for y in range(16):
        row = rng.randint(-spread, spread)
        for x in range(16):
            image.putpixel((x, y), shade(base, row + rng.randint(-2, 2)))
    return image


def frame(image, colour, light):
    for i in range(16):
        image.putpixel((i, 0), light)
        image.putpixel((0, i), light)
        image.putpixel((i, 15), colour)
        image.putpixel((15, i), colour)


def rivets(image):
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        image.putpixel((x, y), BRASS_LIGHT)


def disc(image, cx, cy, radius, colour):
    for y in range(16):
        for x in range(16):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                image.putpixel((x, y), colour)


def body():
    image = brushed(STEEL, 1)
    frame(image, DARK, STEEL_LIGHT)
    rivets(image)
    return image


def brass():
    image = Image.new('RGB', (16, 16))
    rng = random.Random(2)
    for y in range(16):
        base = BRASS_LIGHT if y in (2, 3) else BRASS_DARK if y >= 13 else BRASS
        for x in range(16):
            image.putpixel((x, y), shade(base, rng.randint(-5, 5)))
    return image


def dark():
    image = brushed(DARK, 3, 2)
    for i in range(0, 16, 4):
        for j in range(16):
            image.putpixel((i, j), shade(DARK, 6))
    return image


def roof():
    image = brushed(NIGHT, 4, 3)
    frame(image, BRASS_DARK, BRASS)
    return image


def front_base():
    """A night-blue screen behind brass, on the steel of the cabinet."""
    image = body()
    for y in range(2, 14):
        for x in range(2, 14):
            image.putpixel((x, y), NIGHT)
    for i in range(2, 14):
        image.putpixel((i, 2), BRASS)
        image.putpixel((2, i), BRASS)
        image.putpixel((i, 13), BRASS_DARK)
        image.putpixel((13, i), BRASS_DARK)
    return image


def slot_front():
    image = front_base()
    for reel in range(3):
        x0 = 3 + reel * 4
        for y in range(5, 11):
            for x in range(x0, x0 + 3):
                image.putpixel((x, y), CREAM)
        for x, y in ((x0, 7), (x0 + 1, 7), (x0 + 2, 7), (x0 + 2, 8), (x0 + 1, 9)):
            image.putpixel((x, y), RED)
    return image


def wheel_front():
    image = front_base()
    cx = cy = 8
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            distance = math.hypot(dx, dy)
            if distance <= 5:
                sector = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8) % 8
                image.putpixel((x, y), TIERS[(sector * 3) % len(TIERS)] if distance > 1.2 else BRASS_LIGHT)
            elif distance <= 5.6:
                image.putpixel((x, y), BRASS)
    image.putpixel((7, 2), CREAM)
    image.putpixel((8, 2), CREAM)
    return image


def pusher_front():
    image = front_base()
    for x in range(3, 13):
        image.putpixel((x, 5), (135, 149, 161))
        image.putpixel((x, 9), BRASS_DARK)
    for x0, y0 in ((3, 7), (6, 7), (9, 7), (5, 11), (8, 11)):
        for x in range(x0, x0 + 3):
            image.putpixel((x, y0), BRASS_LIGHT)
            image.putpixel((x, y0 + 1), BRASS)
    return image


def claw_front():
    image = front_base()
    for x in range(3, 13):
        image.putpixel((x, 3), (135, 149, 161))
    for y in range(4, 8):
        image.putpixel((8, y), (135, 149, 161))
    for x, y in ((6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (6, 9), (10, 9), (7, 10), (9, 10)):
        image.putpixel((x, y), CREAM)
    disc(image, 5, 11.5, 1.5, TIERS[5])
    disc(image, 11, 11.5, 1.5, TIERS[3])
    return image


def pachinko_front():
    image = front_base()
    for row in range(4):
        for pin in range(row + 1):
            x = 8 - row + pin * 2
            image.putpixel((x, 4 + row * 2), (135, 149, 161))
    for pocket, colour in enumerate((TIERS[6], TIERS[4], TIERS[2], TIERS[4], TIERS[6])):
        image.putpixel((4 + pocket * 2, 12), colour)
    image.putpixel((9, 7), CREAM)
    return image


def gacha_front():
    image = front_base()
    disc(image, 8, 6.5, 4.2, (20, 30, 44))
    for x, y, tier in ((6, 5, 5), (9, 5, 3), (7, 7, 4), (10, 7, 6), (6, 8, 2), (8, 9, 5)):
        image.putpixel((x, y), TIERS[tier])
    for y in range(10, 13):
        for x in range(4, 12):
            image.putpixel((x, y), RED)
    image.putpixel((7, 11), DARK)
    image.putpixel((8, 11), DARK)
    image.putpixel((11, 10), BRASS_LIGHT)
    return image


def high_stakes_front():
    image = front_base()
    cx, cy = 7.5, 8
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            distance = math.hypot(dx, dy)
            if 3.2 <= distance <= 4.8:
                angle = (math.atan2(dx, -dy) + 2 * math.pi) % (2 * math.pi)
                image.putpixel((x, y), GREEN if angle < math.pi / 2 else (58, 24, 32))
    for x, y in ((7, 8), (8, 7), (9, 6)):
        image.putpixel((x, y), CREAM)
    image.putpixel((12, 4), CYAN)
    image.putpixel((12, 6), BRASS_LIGHT)
    return image


TEXTURES = {
    'cabinet_body': body,
    'cabinet_brass': brass,
    'cabinet_dark': dark,
    'cabinet_roof': roof,
    'slot_machine_front': slot_front,
    'lucky_wheel_front': wheel_front,
    'coin_pusher_front': pusher_front,
    'prize_claw_front': claw_front,
    'pachinko_front': pachinko_front,
    'capsule_gacha_front': gacha_front,
    'high_stakes_front': high_stakes_front,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, draw in TEXTURES.items():
        draw().save(OUT / f'{name}.png', optimize=True)
        print('wrote', name)


if __name__ == '__main__':
    main()
