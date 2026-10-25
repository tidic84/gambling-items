"""Draw the casino cabinets: their textures, their three-block models and their inventory models.

    python tools/generate_cabinet_models.py

Each cabinet is described once, as boxes in a space one block wide, two and a half high and one
deep, seen from the front: u runs from the player's left (0) to right (16), y from the floor (0)
to the top (40), z from the front (0) to the back (16). The boxes are then cut along the blocks of
the cabinet (part 0 at the floor, 1 in the middle, 2 on top). A rotated box cannot be cut, so it
goes whole to the block that holds its centre.

What moves or lights up (wheel sectors, reels, coins, the claw, screens and lamps) is drawn by
SlotCabinet and MachineCabinet on the faces named "screen" here; their coordinates must agree.
Needs Pillow. Every pixel is computed, so running it twice writes the same files.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems'
TEXTURES = ASSETS / 'textures/block'
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
MACHINES = ('slot_machine', 'lucky_wheel', 'coin_pusher', 'prize_claw', 'pachinko', 'capsule_gacha', 'high_stakes',
            'plinko', 'big_six', 'video_poker', 'horse_race')

# ---------------------------------------------------------------------------------------------
# Textures

NIGHT = (18, 28, 48)
BRASS = (190, 150, 70)


def clamp(colour):
    return tuple(max(0, min(255, int(round(c)))) for c in colour)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def lacquer(base, seed, sheen=34, grain=3):
    """Glossy painted metal: lighter at the top, a soft highlight down the left, fine grain."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            light = 10 - y * 1.1
            light += sheen * math.exp(-((x - 4.5) ** 2) / 3.5) * (0.55 + 0.45 * (1 - y / 15))
            light += rng.uniform(-grain, grain)
            image.putpixel((x, y), clamp(tuple(c + light for c in base)))
    return image


def panel(base, line, seed):
    """A side panel: the lacquer, inset with a fine pinstripe a pixel from the edge."""
    image = lacquer(base, seed, sheen=18)
    for i in range(1, 15):
        for x, y in ((i, 1), (1, i), (i, 14), (14, i)):
            image.putpixel((x, y), clamp(line))
    for i in range(2, 14):
        image.putpixel((i, 2), clamp(mix(base, (0, 0, 0), .35)))
        image.putpixel((2, i), clamp(mix(base, (0, 0, 0), .35)))
    return image


def metal(dark, light, seed, bands=(3, 4, 9)):
    """Polished metal: horizontal brushing with bright bands where the light catches it."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        t = 0.35 + 0.45 * math.cos((y - 3.5) / 15 * math.pi) ** 2
        if y in bands:
            t = 0.95 if y == bands[0] else 0.8
        if y >= 14:
            t = 0.2
        row = rng.uniform(-4, 4)
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(c + row + rng.uniform(-2, 2) for c in mix(dark, light, t))))
    return image


def flat(base, seed, spread=4):
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(tuple(c + rng.uniform(-spread, spread) for c in base)))
    return image


def lamp(colour):
    """A lit lens: bright in the middle, a white glint top left, darker towards the rim."""
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            c = mix(mix(colour, (255, 255, 255), 0.35), mix(colour, (0, 0, 0), 0.35), d)
            if math.hypot(x - 5, y - 4.5) < 2.2:
                c = mix(c, (255, 255, 255), 0.6)
            image.putpixel((x, y), clamp(c))
    return image


def grille():
    image = flat((14, 16, 22), 7, 2)
    for y in range(1, 16, 3):
        for x in range(1 + (y // 3) % 2, 16, 3):
            image.putpixel((x, y), (2, 3, 6))
            if x + 1 < 16:
                image.putpixel((x + 1, y), (30, 34, 44))
    return image


def vents(base):
    image = lacquer(base, 8, sheen=10)
    for y in range(2, 15, 3):
        for x in range(2, 14):
            image.putpixel((x, y), clamp(mix(base, (0, 0, 0), .7)))
            image.putpixel((x, y + 1), clamp(mix(base, (255, 255, 255), .12)))
    return image


def screen():
    """The glass a display is drawn on: almost black, with faint scan lines."""
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            base = (8, 12, 20) if y % 2 else (11, 16, 26)
            if x + y == 4 or x + y == 5:
                base = (26, 34, 48)
            image.putpixel((x, y), base)
    return image


def tiers():
    return [(42, 49, 61), (74, 95, 120), (61, 125, 140), (23, 120, 74), (76, 195, 217), (217, 178, 90), (210, 67, 79)]


def wheel_face():
    """What the wheel shows in an inventory or before anyone played: twelve coloured sectors."""
    image = Image.new('RGB', (16, 16), (10, 14, 22))
    colours = tiers()
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - 8, y + .5 - 8
            d = math.hypot(dx, dy)
            if d <= 8:
                sector = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 12) % 12
                c = colours[(sector * 5) % len(colours)] if d > 1.6 else BRASS
                if d > 7.2:
                    c = BRASS
                image.putpixel((x, y), c)
    return image


def pachinko_board():
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), clamp(mix((18, 40, 92), (8, 16, 40), y / 15)))
    for row in range(5):
        for pin in range(row + 1):
            image.putpixel((8 - row + pin * 2 - (0 if row % 2 else 0), 2 + row * 2), (215, 220, 228))
    for pocket, c in enumerate(((210, 67, 79), (76, 195, 217), (23, 120, 74), (217, 178, 90), (23, 120, 74), (76, 195, 217), (210, 67, 79))):
        image.putpixel((1 + pocket * 2, 13), c)
        image.putpixel((2 + pocket * 2, 13), c)
    return image


def sign(colour, seed):
    """A lit sign before the cabinet writes on it: a warm glow in the middle of a dark band."""
    rng = random.Random(seed)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            glow = math.exp(-((y - 7.5) ** 2) / 18)
            c = mix(mix(colour, (0, 0, 0), .55), colour, glow)
            image.putpixel((x, y), clamp(tuple(v + rng.uniform(-3, 3) for v in c)))
    return image


def turf(seed, dark=False):
    """The grass of a race track: mown stripes across it, and blades of a lighter green."""
    rng = random.Random(seed)
    base = (40, 110, 52) if not dark else (30, 90, 42)
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            stripe = 8 if (x // 4) % 2 == 0 else -6
            image.putpixel((x, y), clamp(tuple(c + stripe + rng.uniform(-7, 7) for c in base)))
    return image


TEXTURE_SET = {
    'cab_crimson': lambda: lacquer((128, 18, 32), 11),
    'cab_crimson_side': lambda: panel((112, 16, 28), (205, 165, 80), 12),
    'cab_navy': lambda: lacquer((22, 36, 70), 13),
    'cab_navy_side': lambda: panel((20, 32, 62), (205, 165, 80), 14),
    'cab_green': lambda: lacquer((14, 92, 58), 15),
    'cab_green_side': lambda: panel((12, 80, 50), (205, 165, 80), 16),
    'cab_graphite': lambda: lacquer((40, 44, 52), 17, sheen=24),
    'cab_graphite_side': lambda: panel((36, 40, 48), (76, 195, 217), 18),
    'cab_pearl': lambda: lacquer((214, 208, 196), 19, sheen=22, grain=2),
    'cab_pearl_side': lambda: panel((204, 198, 186), (150, 158, 170), 20),
    'cab_candy': lambda: lacquer((196, 36, 44), 21, sheen=40),
    'cab_cream': lambda: lacquer((236, 222, 190), 22, sheen=16, grain=2),
    'cab_obsidian': lambda: lacquer((16, 16, 22), 23, sheen=26, grain=2),
    'cab_obsidian_side': lambda: panel((14, 14, 20), (210, 67, 79), 24),
    'cab_brass': lambda: metal((110, 82, 34), (240, 204, 120), 25),
    'cab_gold': lambda: metal((150, 108, 30), (255, 226, 130), 26, bands=(2, 3, 8)),
    'cab_chrome': lambda: metal((92, 100, 112), (232, 238, 244), 27),
    'cab_rubber': lambda: flat((20, 21, 24), 28, 3),
    'cab_interior': lambda: flat((12, 15, 22), 29, 2),
    'cab_felt': lambda: flat((16, 70, 46), 30, 4),
    'cab_screen': screen,
    'cab_grille': grille,
    'cab_vents': lambda: vents((36, 40, 48)),
    'cab_lamp_red': lambda: lamp((222, 40, 52)),
    'cab_lamp_green': lambda: lamp((40, 200, 110)),
    'cab_lamp_amber': lambda: lamp((246, 170, 40)),
    'cab_lamp_cyan': lambda: lamp((60, 210, 236)),
    'cab_lamp_white': lambda: lamp((240, 236, 220)),
    'cab_wheel_face': wheel_face,
    'cab_pachinko_board': pachinko_board,
    'cab_sign_warm': lambda: sign((236, 170, 70), 31),
    'cab_sign_cyan': lambda: sign((60, 190, 220), 32),
    'cab_sign_red': lambda: sign((220, 60, 70), 33),
    'cab_sign_violet': lambda: sign((170, 90, 230), 34),
    'cab_sign_green': lambda: sign((70, 210, 120), 35),
    'cab_violet': lambda: lacquer((70, 30, 120), 36),
    'cab_violet_side': lambda: panel((62, 26, 106), (217, 178, 90), 37),
    'cab_teal': lambda: lacquer((10, 96, 110), 38),
    'cab_teal_side': lambda: panel((8, 84, 96), (240, 236, 220), 39),
    'cab_orange': lambda: lacquer((214, 104, 24), 40, sheen=36),
    'cab_orange_side': lambda: panel((190, 90, 20), (250, 230, 190), 41),
    'cab_leather': lambda: flat((92, 18, 30), 42, 5),
    'cab_turf': lambda: turf(43),
    'cab_turf_dark': lambda: turf(44, dark=True),
    'cab_lamp_violet': lambda: lamp((180, 90, 240)),
    'cab_lamp_pink': lambda: lamp((240, 80, 170)),
    'cab_lamp_blue': lambda: lamp((60, 110, 240)),
}

# ---------------------------------------------------------------------------------------------
# Geometry


class Cabinet:
    def __init__(self, particle, width=1):
        self.boxes = []
        self.particle = particle
        # How many blocks wide: a giant machine's u runs to 32, its right column is u 16 to 32.
        self.width = width

    def box(self, a, b, material, rotation=None):
        """A box from a to b, in (u, y, z). material is a texture, or (texture, {side: texture})."""
        self.boxes.append((a, b, material, rotation))

    def frame(self, u1, y1, u2, y2, z1, z2, thick, material):
        """Four bars around a rectangle of the front, standing out from z1 to z2."""
        self.box((u1, y1, z1), (u2, y1 + thick, z2), material)
        self.box((u1, y2 - thick, z1), (u2, y2, z2), material)
        self.box((u1, y1 + thick, z1), (u1 + thick, y2 - thick, z2), material)
        self.box((u2 - thick, y1 + thick, z1), (u2, y2 - thick, z2), material)

    def disc(self, cu, cy, radius, z1, z2, material):
        """A sixteen-sided plate facing the player, its inner radius given: eight bands through the
        centre, each as long as the plate is wide and as thick as one side of it."""
        half = radius * math.tan(math.pi / 16)
        for angle in (0, 22.5, 45, -22.5):
            origin = {'angle': angle, 'axis': 'z', 'origin': (cu, cy, (z1 + z2) / 2)}
            self.box((cu - radius, cy - half, z1), (cu + radius, cy + half, z2), material, origin)
            self.box((cu - half, cy - radius, z1), (cu + half, cy + radius, z2), material, origin)


def side_faces(body, side):
    """A body box whose flanks show the side panel."""
    return body, {'east': side, 'west': side}


def plinth(c, inset=0.5):
    c.box((inset, 0, inset), (16 * c.width - inset, 1.5, 16 - inset), 'cab_rubber')


def tray(c, u1, u2, y, z_front, z_back, material='cab_chrome'):
    """A coin tray standing out of the front: a floor, a lip and two cheeks."""
    c.box((u1, y, z_front), (u2, y + .6, z_back), material)
    c.box((u1, y + .6, z_front), (u2, y + 1.6, z_front + .5), material)
    c.box((u1, y + .6, z_front + .5), (u1 + .5, y + 1.6, z_back), material)
    c.box((u2 - .5, y + .6, z_front + .5), (u2, y + 1.6, z_back), material)
    c.box((u1 + .5, y + .6, z_front + .5), (u2 - .5, y + .7, z_back), 'cab_interior')


def screen_panel(c, u1, y1, u2, y2, z, frame='cab_brass', thick=.5, depth=.5):
    """A display set in a frame: the glass at z, the frame standing depth in front of it."""
    c.box((u1 + thick, y1 + thick, z), (u2 - thick, y2 - thick, z + .1), 'cab_screen')
    c.frame(u1, y1, u2, y2, z - depth, z + .1, thick, frame)


def slot_machine():
    c = Cabinet('cab_crimson')
    body = side_faces('cab_crimson', 'cab_crimson_side')
    plinth(c)
    c.box((0, 1.5, 1.5), (16, 13, 16), body)
    c.box((0, 1.5, 1.2), (16, 2.2, 1.5), 'cab_brass')
    screen_panel(c, 2, 4, 14, 11, 1.4)                      # paytable
    tray(c, 3, 13, 2.2, -.6, 1.5)
    # The button deck, with its row of lit buttons and the big spin button.
    c.box((.5, 13, -1.2), (15.5, 14.2, 1.5), 'cab_crimson')
    c.box((.5, 12.6, -1.3), (15.5, 13, 1.5), 'cab_brass')
    for u, lens in ((2, 'cab_lamp_amber'), (4.6, 'cab_lamp_amber'), (7.2, 'cab_lamp_white')):
        c.box((u, 14.2, -.6), (u + 2, 14.6, .9), lens)
    c.box((10.5, 14.2, -.8), (14, 14.4, 1.1), 'cab_brass')
    c.box((11, 14.4, -.5), (13.5, 15.1, .8), 'cab_lamp_red')
    # The upper body, narrower, with brass pillars at its corners.
    c.box((.5, 14.2, 2), (15.5, 32, 16), side_faces('cab_crimson', 'cab_crimson_side'))
    c.box((0, 13, 1.4), (1, 32.6, 2.4), 'cab_brass')
    c.box((15, 13, 1.4), (16, 32.6, 2.4), 'cab_brass')
    screen_panel(c, 1.6, 19, 14.4, 29.6, 1.9, thick=.7, depth=.7)   # reels
    screen_panel(c, 3, 15.4, 13, 18.4, 1.9)                         # status
    c.box((1.2, 29.9, 1.6), (14.8, 30.5, 2), 'cab_brass')
    # The marquee, standing out over the reels, and an arched crown.
    c.box((0, 32, .5), (16, 37, 15), 'cab_crimson')
    c.box((.6, 32.6, .3), (15.4, 36.4, .5), 'cab_sign_warm')
    c.frame(0, 32, 16, 37, 0, .5, .6, 'cab_brass')
    c.box((1, 37, 1.5), (15, 38, 14), 'cab_brass')
    c.box((3, 38, 2.5), (13, 39, 13), 'cab_crimson')
    c.box((5.5, 39, 3.5), (10.5, 40, 12), 'cab_brass')
    # A grille at the back, where the cabinet breathes.
    c.box((3, 4, 16), (13, 12, 16.2), 'cab_grille')
    c.box((3, 18, 16), (13, 30, 16.2), 'cab_vents')
    return c


def lucky_wheel():
    c = Cabinet('cab_navy')
    plinth(c, 1)
    c.box((.5, 1.5, 1.5), (15.5, 14, 15.5), side_faces('cab_navy', 'cab_navy_side'))
    c.box((.5, 1.5, 1.2), (15.5, 2.2, 1.5), 'cab_brass')
    screen_panel(c, 2, 3.4, 14, 9.6, 1.4)                   # odds
    screen_panel(c, 3, 10.2, 13, 13.2, 1.4)                 # status
    # The deck and its single push button.
    c.box((0, 14, .5), (16, 15, 15.5), 'cab_brass')
    c.box((6, 15, 3), (10, 15.3, 7), 'cab_brass')
    c.box((6.5, 15.3, 3.5), (9.5, 16.1, 6.5), 'cab_lamp_red')
    # The column the wheel turns on.
    c.box((4, 15, 7), (12, 34.5, 15), side_faces('cab_navy', 'cab_navy_side'))
    c.box((4, 15, 6.6), (4.6, 34.5, 7), 'cab_brass')
    c.box((11.4, 15, 6.6), (12, 34.5, 7), 'cab_brass')
    # The wheel: a back plate, a brass rim and the face the sectors are drawn on.
    c.disc(8, 25.5, 9.6, 3, 4, 'cab_navy')
    c.disc(8, 25.5, 9.2, 1.6, 3, 'cab_brass')
    c.disc(8, 25.5, 8.4, 1.2, 1.6, 'cab_screen')
    # The sectors as the inventory shows them; in the world the cabinet draws the turning wheel over them.
    c.box((2.1, 19.6, 1.1), (13.9, 31.4, 1.2), 'cab_wheel_face')
    # The pointer over the rim, then the crown sign behind and above the wheel.
    c.box((7.2, 34.4, .6), (8.8, 36, 2.2), 'cab_brass', {'angle': 45, 'axis': 'z', 'origin': (8, 35.2, 1.4)})
    c.box((1, 35.5, 5), (15, 39.6, 12), 'cab_navy')
    c.box((1.3, 35.9, 4.8), (14.7, 39.2, 5), 'cab_sign_warm')
    c.frame(1, 35.5, 15, 39.6, 4.6, 5, .4, 'cab_brass')
    return c


def coin_pusher():
    c = Cabinet('cab_green')
    plinth(c)
    c.box((0, 1.5, 2), (16, 13, 16), side_faces('cab_green', 'cab_green_side'))
    c.box((0, 1.5, 1.7), (16, 2.2, 2), 'cab_brass')
    tray(c, 2, 14, 2.2, -1, 2)
    # The chute the coins fall out of, then the odds.
    c.box((5, 4.6, 1.8), (11, 6.6, 2), 'cab_interior')
    c.frame(4.6, 4.2, 11.4, 7, 1.5, 2, .4, 'cab_brass')
    screen_panel(c, 2, 7.6, 14, 12.6, 1.6)
    # The deck with the coin slot.
    c.box((0, 13, 0), (16, 14, 16), 'cab_brass')
    c.box((6, 14, .6), (10, 14.4, 3.2), 'cab_chrome')
    c.box((7, 14.4, 1.4), (9, 14.45, 2.4), 'cab_interior')
    # The glass case: chrome posts, rails, a dark back and two brass shelves.
    for u in (0, 15):
        for z in (0, 15):
            c.box((u, 14, z), (u + 1, 30, z + 1), 'cab_chrome')
    c.box((1, 29.4, 0), (15, 30, 1), 'cab_chrome')
    c.box((1, 29.4, 15), (15, 30, 16), 'cab_chrome')
    c.box((0, 29.4, 1), (1, 30, 15), 'cab_chrome')
    c.box((15, 29.4, 1), (16, 30, 15), 'cab_chrome')
    c.box((1, 14, 0), (15, 14.6, 1), 'cab_chrome')
    c.box((1, 14, 14.6), (15, 29.4, 15.4), 'cab_interior')
    c.box((1, 14, 1), (15, 15, 14.6), 'cab_interior')
    c.box((1, 17, 1), (15, 17.6, 14.6), 'cab_brass')
    c.box((1, 21, 5), (15, 21.6, 14.6), 'cab_brass')
    c.box((1, 21.6, 13), (15, 26, 14.6), 'cab_chrome')
    # The header and its beacon.
    c.box((0, 30, 0), (16, 35, 16), side_faces('cab_green', 'cab_green_side'))
    c.box((.6, 30.6, -.2), (15.4, 34.4, 0), 'cab_sign_warm')
    c.frame(0, 30, 16, 35, -.4, 0, .6, 'cab_brass')
    c.box((6, 35, 6), (10, 35.5, 10), 'cab_chrome')
    c.box((6.5, 35.5, 6.5), (9.5, 37.5, 9.5), 'cab_lamp_amber')
    return c


def prize_claw():
    c = Cabinet('cab_graphite')
    plinth(c)
    c.box((0, 1.5, 1), (16, 12, 16), side_faces('cab_graphite', 'cab_graphite_side'))
    # The prize door, on the left, and the screen on the right.
    c.box((2, 3, .8), (6.5, 8, 1), 'cab_interior')
    c.frame(1.5, 2.5, 7, 8.5, .5, 1, .5, 'cab_chrome')
    c.box((2.2, 7.2, .4), (6.3, 7.6, .8), 'cab_chrome')
    screen_panel(c, 8, 2.5, 14.5, 10.8, .9, frame='cab_chrome')
    # The control deck: a neon edge, a joystick and the drop button.
    c.box((0, 12, -1.6), (16, 13.4, 1.5), 'cab_graphite')
    c.box((0, 12, -1.8), (16, 12.5, -1.6), 'cab_lamp_cyan')
    c.box((2.8, 13.4, -1), (5.2, 13.7, 1.2), 'cab_chrome')
    c.box((3.7, 13.7, -.2), (4.3, 15.6, .4), 'cab_chrome')
    c.box((3.3, 15.6, -.6), (4.7, 17, .8), 'cab_lamp_red')
    c.box((10.5, 13.4, -1), (13.5, 13.6, 1.2), 'cab_chrome')
    c.box((11, 13.6, -.5), (13, 14.2, .7), 'cab_lamp_green')
    # The glass case: chrome posts with cyan neon, a floor, a chute and the gantry rails.
    for u in (0, 15):
        for z in (0, 15):
            c.box((u, 13.4, z), (u + 1, 33, z + 1), 'cab_chrome')
    c.box((.2, 13.4, -.15), (.8, 32.4, 0), 'cab_lamp_cyan')
    c.box((15.2, 13.4, -.15), (15.8, 32.4, 0), 'cab_lamp_cyan')
    c.box((1, 13.4, 0), (15, 14, 1), 'cab_chrome')
    c.box((1, 32.4, 0), (15, 33, 1), 'cab_chrome')
    c.box((1, 32.4, 15), (15, 33, 16), 'cab_chrome')
    c.box((0, 32.4, 1), (1, 33, 15), 'cab_chrome')
    c.box((15, 32.4, 1), (16, 33, 15), 'cab_chrome')
    c.box((1, 13.4, 15), (15, 32.4, 15.6), 'cab_interior')
    c.box((1, 13.4, 1), (15, 14.2, 15), 'cab_felt')
    c.box((1, 14.2, 4.2), (4.6, 17, 4.6), 'cab_chrome')
    c.box((4.2, 14.2, 1), (4.6, 17, 4.2), 'cab_chrome')
    c.box((1.4, 14.2, 1.4), (4.2, 14.25, 4.2), 'cab_interior')
    c.box((1, 31.4, 2), (15, 32, 2.6), 'cab_chrome')
    c.box((1, 31.4, 13.4), (15, 32, 14), 'cab_chrome')
    # The header, outlined in neon.
    c.box((0, 33, 0), (16, 38, 16), side_faces('cab_graphite', 'cab_graphite_side'))
    c.box((.8, 33.8, -.2), (15.2, 37.2, 0), 'cab_sign_cyan')
    c.frame(.4, 33.4, 15.6, 37.6, -.35, 0, .4, 'cab_lamp_cyan')
    return c


def pachinko():
    c = Cabinet('cab_pearl')
    c.box((2, 0, 4), (14, 1, 13), 'cab_rubber')
    c.box((2.5, 1, 4.5), (13.5, 8, 12.5), side_faces('cab_graphite', 'cab_graphite_side'))
    # The ball tray across the front, and the housing of the launch handle on the right.
    tray(c, 1.5, 14, 8, -1.6, 4)
    c.box((14, 8, -.4), (16, 11, 4), 'cab_chrome')
    # The board, standing slim and upright.
    c.box((0, 8, 4), (16, 37, 12), side_faces('cab_pearl', 'cab_pearl_side'))
    c.box((1.8, 12.8, 3.9), (14.2, 33.2, 4), 'cab_pachinko_board')
    c.frame(1, 12, 15, 34, 3.2, 4, .8, 'cab_chrome')
    for cu, cy in ((2.4, 13.4), (13.6, 13.4), (2.4, 32.6), (13.6, 32.6)):
        c.box((cu - .9, cy - .9, 3.4), (cu + .9, cy + .9, 3.95), 'cab_chrome', {'angle': 45, 'axis': 'z', 'origin': (cu, cy, 3.7)})
    c.box((0, 8, 3.6), (.6, 37, 4), 'cab_chrome')
    c.box((15.4, 8, 3.6), (16, 37, 4), 'cab_chrome')
    # A crown carrying the sign.
    c.box((1, 37, 5), (15, 40, 11), 'cab_pearl')
    c.box((1.4, 37.4, 4.8), (14.6, 39.6, 5), 'cab_sign_warm')
    c.frame(1, 37, 15, 40, 4.5, 5, .4, 'cab_chrome')
    return c


def capsule_gacha():
    c = Cabinet('cab_candy')
    c.box((2.5, 0, 2.5), (13.5, 1, 13.5), 'cab_rubber')
    c.box((3, 1, 3), (13, 13, 13), 'cab_candy')
    c.box((2.8, 9, 2.8), (13.2, 10, 13.2), 'cab_cream')
    c.box((2.8, 12.4, 2.8), (13.2, 13.4, 13.2), 'cab_brass')
    # The crank plate and the door the capsule comes out of.
    c.box((4.5, 6.8, 2.6), (11.5, 12, 3), 'cab_chrome')
    c.box((6, 2.5, 2.8), (10, 5.5, 3), 'cab_interior')
    c.frame(5.5, 2, 10.5, 6, 2.5, 3, .5, 'cab_chrome')
    # The collar the globe sits in, and its cap.
    for angle in (0, 45):
        c.box((4, 13.4, 4), (12, 14.4, 12), 'cab_chrome', {'angle': angle, 'axis': 'y', 'origin': (8, 13.9, 8)})
    for angle in (0, 45):
        c.box((5.4, 28, 5.4), (10.6, 29.4, 10.6), 'cab_candy', {'angle': angle, 'axis': 'y', 'origin': (8, 28.7, 8)})
    c.box((7, 29.4, 7), (9, 31, 9), 'cab_brass')
    # The topper sign.
    c.box((3.6, 31, 6), (12.4, 35.4, 10), 'cab_candy')
    c.box((4, 31.4, 5.8), (12, 35, 6), 'cab_sign_red')
    c.frame(3.6, 31, 12.4, 35.4, 5.6, 6, .4, 'cab_cream')
    return c


def high_stakes():
    """The VIP terminal: a fluted pedestal on a stepped gold base, a leather desk, and a tall screen
    leaning back under a hood, with neon wings on either side and a crown lamp on top."""
    c = Cabinet('cab_obsidian')
    # A stepped base: gold, then obsidian, then a thin gold band.
    c.box((1.5, 0, 1.5), (14.5, .8, 14.5), 'cab_gold')
    c.box((2.2, .8, 2.2), (13.8, 1.8, 13.8), 'cab_obsidian')
    c.box((2.6, 1.8, 2.6), (13.4, 2.2, 13.4), 'cab_gold')
    # The pedestal, fluted with gold and a red light running down its front.
    c.box((3.5, 2.2, 3.5), (12.5, 11.4, 12.5), side_faces('cab_obsidian', 'cab_obsidian_side'))
    for u in (3.5, 6.2, 9.2, 11.9):
        c.box((u, 2.2, 3.3), (u + .6, 11.4, 3.5), 'cab_gold')
    c.box((7.6, 3, 3.25), (8.4, 10.8, 3.5), 'cab_lamp_red')
    c.box((3, 11.4, 3), (13, 12, 13), 'cab_gold')
    # The desk: leather top, gold edge, a chip tray, a card slot and the two keys.
    c.box((0, 12, -.8), (16, 13.2, 12), 'cab_obsidian')
    c.box((0, 12.2, -1), (16, 13.2, -.8), 'cab_gold')
    c.box((.6, 13.2, -.4), (15.4, 13.4, 4.4), 'cab_leather')
    c.box((0, 13.2, -.8), (16, 13.6, -.4), 'cab_gold')
    c.box((2.2, 13.4, -.1), (5.3, 13.6, 2.5), 'cab_gold')
    c.box((2.5, 13.6, .2), (5, 14.2, 2.2), 'cab_lamp_green')
    c.box((10.7, 13.4, -.1), (13.8, 13.6, 2.5), 'cab_gold')
    c.box((11, 13.6, .2), (13.5, 14.2, 2.2), 'cab_lamp_red')
    c.box((6.2, 13.4, .6), (9.8, 13.6, 3.4), 'cab_chrome')
    c.box((6.8, 13.6, 1.6), (9.2, 13.65, 2.2), 'cab_lamp_amber')
    # The screen, leaning back, framed in gold under a hood.
    tilt = {'angle': 22.5, 'axis': 'x', 'origin': (8, 13.5, 6.5)}
    c.box((.5, 13.5, 5), (15.5, 33.5, 8.5), side_faces('cab_obsidian', 'cab_obsidian_side'), tilt)
    c.box((1.3, 14.3, 4.8), (14.7, 32.7, 5), 'cab_screen', tilt)
    for a, b in (((.5, 32.9, 4.3), (15.5, 33.5, 5)), ((.5, 13.5, 4.3), (15.5, 14.1, 5)),
                 ((.5, 14.1, 4.3), (1.1, 32.9, 5)), ((14.9, 14.1, 4.3), (15.5, 32.9, 5))):
        c.box(a, b, 'cab_gold', tilt)
    c.box((0, 33.5, 3), (16, 34.6, 8.5), 'cab_obsidian', tilt)
    c.box((0, 33.2, 2.7), (16, 33.5, 3.3), 'cab_lamp_red', tilt)
    # Neon wings on the flanks of the screen.
    for u in (-.4, 15.6):
        c.box((u, 14.5, 5.5), (u + .8, 32.5, 6.3), 'cab_lamp_red', tilt)
    # The crown: an obsidian block with a gold diamond that glows.
    c.box((4.5, 34.5, 9), (11.5, 36.5, 13), 'cab_obsidian')
    c.box((4.2, 34.2, 8.8), (11.8, 34.6, 13.2), 'cab_gold')
    c.box((6.8, 36.4, 10.2), (9.2, 38.8, 11.8), 'cab_lamp_red', {'angle': 45, 'axis': 'z', 'origin': (8, 37.6, 11)})
    return c


def plinko():
    """Giant plinko, two blocks wide: a tall pegged board behind glass, fed by a chute on top."""
    c = Cabinet('cab_teal', width=2)
    plinth(c)
    body = side_faces('cab_teal', 'cab_teal_side')
    c.box((0, 1.5, 2), (32, 12.5, 16), body)
    c.box((0, 1.5, 1.7), (32, 2.2, 2), 'cab_chrome')
    screen_panel(c, 8, 3, 24, 8.4, 1.9, frame='cab_chrome')            # odds
    screen_panel(c, 8, 8.6, 24, 11.8, 1.9, frame='cab_chrome')         # status
    tray(c, 1.5, 7, 2.2, -.8, 2)
    tray(c, 25, 30.5, 2.2, -.8, 2)
    # The deck and the big drop button in the middle.
    c.box((0, 12.5, -1.5), (32, 13.5, 4), 'cab_chrome')
    c.box((13.6, 13.5, -1), (18.4, 13.8, 1.8), 'cab_chrome')
    c.box((14.2, 13.8, -.5), (17.8, 14.8, 1.3), 'cab_lamp_red')
    # The board, its frame and its glass.
    c.box((0, 13.5, 4), (32, 35, 16), body)
    c.box((1.6, 14.2, 3.9), (30.4, 34.4, 4), 'cab_screen')
    c.frame(1, 13.5, 31, 35, 1, 4, .7, 'cab_chrome')
    for u in (.3, 31.1):
        c.box((u, 14, .9), (u + .6, 34.6, 1.5), 'cab_lamp_cyan')
    # The crown with the sign, and the chute the ball is fed into.
    c.box((1, 35, 5), (31, 39, 11), 'cab_teal')
    c.box((2, 35.5, 4.8), (30, 38.5, 5), 'cab_sign_cyan')
    c.frame(1, 35, 31, 39, 4.4, 5, .5, 'cab_chrome')
    c.box((14, 35, 1.5), (18, 36, 4.4), 'cab_chrome')
    c.box((13.6, 36, 1), (18.4, 39.6, 1.6), 'cab_chrome')
    c.box((13.6, 36, 1.6), (14.2, 39.6, 4.4), 'cab_chrome')
    c.box((17.8, 36, 1.6), (18.4, 39.6, 4.4), 'cab_chrome')
    c.box((14.2, 36, 1.6), (17.8, 36.4, 4.4), 'cab_interior')
    return c


def big_six():
    """The Big Six wheel, two blocks wide: a great upright wheel of fifty-four slots over a betting deck."""
    c = Cabinet('cab_violet', width=2)
    plinth(c)
    body = side_faces('cab_violet', 'cab_violet_side')
    c.box((0, 1.5, 2), (32, 9.5, 16), body)
    c.box((0, 1.5, 1.7), (32, 2.2, 2), 'cab_brass')
    screen_panel(c, 3, 3, 29, 8.8, 1.9)                    # the prize of each symbol, and the status
    # The deck with one betting button per symbol, in the colours of the prizes.
    c.box((0, 9.5, -2.4), (32, 10.2, 4), 'cab_brass')
    lenses = ('cab_lamp_white', 'cab_lamp_blue', 'cab_lamp_cyan', 'cab_lamp_green', 'cab_lamp_amber', 'cab_lamp_violet',
              'cab_lamp_red')
    for symbol, lens in enumerate(lenses):
        u = 3 + symbol * 3.8
        c.box((u - .2, 10.2, -1.8), (u + 3.2, 10.5, 1.2), 'cab_brass')
        c.box((u + .2, 10.5, -1.4), (u + 2.8, 11.3, .8), lens)
    # The tower behind the wheel, and the wheel: a back plate, a brass rim and the face it is drawn on.
    c.box((5, 10.2, 5), (27, 36, 16), body)
    c.box((2, 10.2, 4), (4, 38, 6), 'cab_brass')
    c.box((28, 10.2, 4), (30, 38, 6), 'cab_brass')
    c.disc(16, 25, 11.4, 3.4, 4.6, 'cab_violet')
    c.disc(16, 25, 11, 2.2, 3.4, 'cab_brass')
    c.disc(16, 25, 10.2, 2, 2.2, 'cab_screen')
    # The leather flapper over the top of the wheel, then the crown.
    c.box((15.4, 35.8, 1.4), (16.6, 37.4, 3.6), 'cab_brass')
    c.box((4, 37, 5), (28, 40, 10), 'cab_violet')
    c.box((4.6, 37.4, 4.8), (27.4, 39.6, 5), 'cab_sign_violet')
    c.frame(4, 37, 28, 40, 4.4, 5, .4, 'cab_brass')
    for u in (2.2, 28.2):
        c.box((u, 38, 4.2), (u + 1.6, 39.6, 5.8), 'cab_lamp_amber')
    return c


def video_poker():
    """A bar-top video poker: an upright screen with the five cards, the hold keys and the deal key under it."""
    c = Cabinet('cab_navy')
    plinth(c)
    body = side_faces('cab_navy', 'cab_navy_side')
    c.box((0, 1.5, 1.5), (16, 12.4, 16), body)
    c.box((0, 1.5, 1.2), (16, 2.2, 1.5), 'cab_chrome')
    screen_panel(c, 1.5, 2.8, 14.5, 11.8, 1.4, frame='cab_chrome')   # paytable
    # The deck: five amber hold keys and the red deal key.
    c.box((0, 12.4, -1.8), (16, 13, 3), 'cab_navy')
    c.box((0, 12.2, -2), (16, 12.6, -1.8), 'cab_chrome')
    for card in range(5):
        u = 1 + card * 2.1
        c.box((u, 13, -1.2), (u + 1.9, 13.3, 1.2), 'cab_chrome')
        c.box((u + .2, 13.3, -1), (u + 1.7, 13.8, 1), 'cab_lamp_amber')
    c.box((12, 13, -1.2), (15.2, 13.3, 1.2), 'cab_chrome')
    c.box((12.3, 13.3, -1), (14.9, 14.2, 1), 'cab_lamp_red')
    # The screen, upright, in a chrome bezel.
    c.box((.5, 13, 3), (15.5, 32, 16), body)
    screen_panel(c, .8, 15.6, 15.2, 31.4, 2, frame='cab_chrome', thick=.6, depth=.8)
    # The marquee.
    c.box((0, 32, 1), (16, 37, 15), 'cab_navy')
    c.box((.6, 32.6, .8), (15.4, 36.4, 1), 'cab_sign_warm')
    c.frame(0, 32, 16, 37, .5, 1, .5, 'cab_chrome')
    c.box((2, 37, 3), (14, 37.6, 12), 'cab_chrome')
    return c


def horse_race():
    """The derby, two blocks wide: six lanes of turf rising in terraces, the starting gates on the left,
    the finish on the right, and a tote board behind."""
    c = Cabinet('cab_orange', width=2)
    plinth(c)
    body = side_faces('cab_orange', 'cab_orange_side')
    c.box((0, 1.5, 2), (32, 11.4, 16), body)
    c.box((0, 1.5, 1.7), (32, 2.2, 2), 'cab_brass')
    screen_panel(c, 4, 3, 28, 10.6, 1.9)                    # each horse's odds and the bets
    # The deck: one betting button in front of each lane, in the colour of its horse.
    c.box((0, 11.4, -2.4), (32, 12, 3.5), 'cab_brass')
    silks = ('cab_lamp_red', 'cab_lamp_blue', 'cab_lamp_green', 'cab_lamp_amber', 'cab_lamp_violet', 'cab_lamp_white')
    for horse, lens in enumerate(silks):
        u = 3 + horse * 4.4
        c.box((u - .2, 12, -1.8), (u + 3.6, 12.3, 1.2), 'cab_brass')
        c.box((u + .2, 12.3, -1.4), (u + 3.2, 13.1, .8), lens)
    # The terraces: each lane higher and further back than the one before it.
    for lane in range(6):
        y, z = 13.6 + lane * 1.6, 3.5 + lane * 1.8
        turf = 'cab_turf' if lane % 2 == 0 else 'cab_turf_dark'
        c.box((1, 12, z), (31, y, z + 1.8), (turf, {'north': 'cab_orange', 'east': 'cab_orange', 'west': 'cab_orange'}))
        c.box((1, y, z), (31, y + .3, z + .2), 'cab_cream')
        # The starting gate and the finish post of the lane.
        c.box((3.6, y, z + .1), (3.9, y + 1.8, z + .3), 'cab_chrome')
        c.box((28.6, y, z + .1), (28.9, y + 2.2, z + .3), 'cab_lamp_white')
    # The back wall with the tote board, flanked by lit pillars, and the crown.
    c.box((0, 12, 14.4), (32, 36, 16), body)
    screen_panel(c, 4, 24.4, 28, 34.4, 14.3)
    for u in (0, 30.6):
        c.box((u, 12, 1), (u + 1.4, 36, 2.4), 'cab_brass')
        c.box((u + .3, 14, .8), (u + 1.1, 35, 1), 'cab_lamp_amber')
    c.box((2, 36, 8), (30, 40, 15), 'cab_orange')
    c.box((2.6, 36.4, 7.8), (29.4, 39.6, 8), 'cab_sign_warm')
    c.frame(2, 36, 30, 40, 7.4, 8, .4, 'cab_brass')
    return c


BUILDERS = {'slot_machine': slot_machine, 'lucky_wheel': lucky_wheel, 'coin_pusher': coin_pusher,
            'prize_claw': prize_claw, 'pachinko': pachinko, 'capsule_gacha': capsule_gacha,
            'high_stakes': high_stakes, 'plinko': plinko, 'big_six': big_six, 'video_poker': video_poker,
            'horse_race': horse_race}

# ---------------------------------------------------------------------------------------------
# Models


def auto_uv(side, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {'down': [x1, 16 - z2, x2, 16 - z1], 'up': [x1, z1, x2, z2],
          'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
          'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1]}[side]
    if all(0 <= v <= 16 for v in uv):
        return [round(v, 4) for v in uv]
    # Outside the block the automatic mapping would read a neighbouring texture of the atlas.
    width, height = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
    return [0, 0, round(min(16, width), 4), round(min(16, height), 4)]


def element(f, t, material, rotation):
    texture, overrides = (material, {}) if isinstance(material, str) else material
    # The front of the cabinet faces north; the player's left (u = 0) is the east side of the block.
    faces = {}
    for side in SIDES:
        name = overrides.get(side, texture)
        faces[side] = {'texture': '#' + name, 'uv': auto_uv(side, f, t)}
    value = {'from': [round(v, 4) for v in f], 'to': [round(v, 4) for v in t], 'faces': faces}
    if rotation:
        value['rotation'] = {'angle': rotation['angle'], 'axis': rotation['axis'],
                             'origin': [round(v, 4) for v in rotation['origin']]}
    return value


def to_model_space(a, b, y_shift=0, scale=1, offset=(0, 0, 0)):
    """(u, y, z) boxes to model coordinates: x = 16 - u, so the player's left is east."""
    (u1, y1, z1), (u2, y2, z2) = a, b
    f = (16 - u2, y1 - y_shift, z1)
    t = (16 - u1, y2 - y_shift, z2)
    return (tuple(v * scale + o for v, o in zip(f, offset)), tuple(v * scale + o for v, o in zip(t, offset)))


def rotation_in_model(rotation, y_shift=0, scale=1, offset=(0, 0, 0)):
    if not rotation:
        return None
    u, y, z = rotation['origin']
    origin = tuple(v * scale + o for v, o in zip((16 - u, y - y_shift, z), offset))
    # Mirroring u turns a rotation about z or y the other way.
    angle = -rotation['angle'] if rotation['axis'] in ('z', 'y') else rotation['angle']
    return {'angle': angle, 'axis': rotation['axis'], 'origin': origin}


def textures_of(cabinet):
    names = set()
    for _, _, material, _ in cabinet.boxes:
        if isinstance(material, str):
            names.add(material)
        else:
            names.add(material[0])
            names.update(material[1].values())
    textures = {name: f'gamblingitems:block/{name}' for name in sorted(names)}
    textures['particle'] = f'gamblingitems:block/{cabinet.particle}'
    return textures


def shift_u(a, b, rotation, column):
    """A box of the right column moved into the space of its own block."""
    if column == 0:
        return a, b, rotation
    du = 16 * column
    a, b = (a[0] - du, a[1], a[2]), (b[0] - du, b[1], b[2])
    if rotation:
        rotation = dict(rotation, origin=(rotation['origin'][0] - du,) + tuple(rotation['origin'][1:]))
    return a, b, rotation


def parts(cabinet, column=0):
    """The elements of each part of one column; a box across both columns is cut at u = 16."""
    result = [[], [], []]
    for a, b, material, rotation in cabinet.boxes:
        if rotation:
            centre_u, centre = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            if min(cabinet.width - 1, max(0, int(centre_u // 16))) != column:
                continue
            part = min(2, int(centre // 16))
            ca, cb, crot = shift_u(a, b, rotation, column)
            f, t = to_model_space(ca, cb, part * 16)
            result[part].append(element(f, t, material, rotation_in_model(crot, part * 16)))
            continue
        if cabinet.width == 1:
            left, right = a[0], b[0]
        else:
            # The outer edge keeps what stands past the block; the edge between the columns is cut.
            left = a[0] if column == 0 else max(a[0], 16)
            right = min(b[0], 16) if column == 0 else b[0]
            if left >= right:
                continue
        for part in range(3):
            low, high = max(a[1], part * 16), min(b[1], part * 16 + 16)
            if low >= high:
                continue
            ca, cb, _ = shift_u((left, low, a[2]), (right, high, b[2]), None, column)
            f, t = to_model_space(ca, cb, part * 16)
            result[part].append(element(f, t, material, None))
    return result


DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.95, 0.95, 0.95]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def inventory(cabinet):
    """The whole cabinet at two fifths of its size (a fifth for a giant), standing in the middle of an item slot."""
    scale = 0.4 / cabinet.width
    # x = 16 - u runs from 16 - 16 * width to 16, so its middle is at 16 - 8 * width.
    offset = (8 - (16 - 8 * cabinet.width) * scale, 0, 8 - 8 * scale)
    elements = []
    for a, b, material, rotation in cabinet.boxes:
        f, t = to_model_space(a, b, 0, scale, offset)
        elements.append(element(f, t, material, rotation_in_model(rotation, 0, scale, offset)))
    return elements


def save(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, draw in TEXTURE_SET.items():
        draw().save(TEXTURES / f'{name}.png', optimize=True)
    for machine in MACHINES:
        cabinet = BUILDERS[machine]()
        textures = textures_of(cabinet)
        for column in range(cabinet.width):
            suffix = '_right' if column else ''
            for index, elements in enumerate(parts(cabinet, column)):
                save(f'models/block/{machine}_part{index}{suffix}.json',
                     {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements})
        save(f'models/block/{machine}.json', {'parent': 'minecraft:block/block', 'textures': textures,
                                              'display': DISPLAY, 'elements': inventory(cabinet)})
        save(f'models/item/{machine}.json', {'parent': f'gamblingitems:block/{machine}'})
        save(f'blockstates/{machine}.json', {'variants': {
            f'facing={facing},part={part},right={str(right).lower()}': {
                'model': f'gamblingitems:block/{machine}_part{part}' + ('_right' if right and cabinet.width > 1 else ''),
                'y': rotation}
            for facing, rotation in (('north', 0), ('east', 90), ('south', 180), ('west', 270))
            for part in range(3) for right in (False, True)}})
        print(machine, [[len(p) for p in parts(cabinet, column)] for column in range(cabinet.width)], 'elements')


if __name__ == '__main__':
    main()
