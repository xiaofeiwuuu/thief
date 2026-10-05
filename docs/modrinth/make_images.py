"""
Makes the pictures for the Modrinth page, from the mod's own textures (and a few of the game's item textures, read from a copy of the game's jar
unpacked to MCJAR, which are not kept in this repository). Run from the repository root: python3 docs/modrinth/make_images.py
These are artwork, not screenshots of the game.
"""
import os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(ROOT, "..", "..", "src/main/resources/assets/thief/textures")
MCJAR = "/tmp/mcjar/assets/minecraft/textures/item"
OUT = os.path.join(ROOT, "images")
os.makedirs(OUT, exist_ok=True)
random.seed(11)

F_TITLE = "/System/Library/Fonts/Supplemental/Arial Black.ttf"
F_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
F_CJK = "/System/Library/Fonts/STHeiti Medium.ttc"
GOLD = (255, 214, 90, 255)
SOFT = (200, 220, 255, 255)


def font(path, size):
    return ImageFont.truetype(path, size)


def load(p):
    return Image.open(p).convert("RGBA")


def big(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def gradient(w, h, top, bottom):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return im


def night(w=1280, h=720, moon=True):
    im = gradient(w, h, (8, 12, 34), (34, 58, 96))
    d = ImageDraw.Draw(im)
    for _ in range(80):
        x, y = random.randrange(w), random.randrange(h // 2 + 120)
        s = random.choice((2, 2, 3, 4))
        d.rectangle([x, y, x + s, y + s], fill=random.choice(((255, 245, 210, 210), (190, 210, 255, 170), (255, 255, 255, 140))))
    if moon:
        m = Image.new("L", (w, h), 0)                                      # a crescent: a disc less a disc beside it
        md = ImageDraw.Draw(m)
        md.ellipse([w - 190, 50, w - 90, 150], fill=255)
        md.ellipse([w - 160, 40, w - 70, 130], fill=0)
        moon_im = Image.new("RGBA", (w, h), (250, 244, 214, 255))
        moon_im.putalpha(m)
        im.alpha_composite(moon_im)
    d.rectangle([0, h - 70, w, h], fill=(18, 20, 30, 255))
    for x in range(0, w, 32):
        d.rectangle([x, h - 70, x + 16, h - 62], fill=(40, 84, 52, 255))
    return im


def text(im, xy, s, f, fill=(255, 255, 255, 255), shadow=True, anchor="la"):
    d = ImageDraw.Draw(im)
    if shadow:
        d.text((xy[0] + 3, xy[1] + 3), s, font=f, fill=(0, 0, 0, 170), anchor=anchor)
    d.text(xy, s, font=f, fill=fill, anchor=anchor)


def panel(im, box, alpha=110):
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rounded_rectangle(box, radius=22, fill=(4, 8, 24, alpha))
    im.alpha_composite(ov)


def glow(img, color=(255, 214, 90), radius=10, strength=1.0):
    """The picture with a soft glow of this colour round it (a thief that has stolen shines)."""
    pad = radius * 3
    base = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    base.alpha_composite(img, (pad, pad))
    a = base.split()[3].point(lambda v: 255 if v > 10 else 0).filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(radius))
    a = a.point(lambda v: min(255, int(v * 1.6 * strength)))
    halo = Image.new("RGBA", base.size, color + (255,))
    halo.putalpha(a)
    halo.alpha_composite(base)
    return halo


def figure(skin, hat=None):
    """A front view of a skin (the standard 64 by 64 layout), 16 wide and 40 high; a top hat over the head if asked."""
    s = load(os.path.join(TEX, "entity", skin))
    out = Image.new("RGBA", (16, 40), (0, 0, 0, 0))

    def part(box, at, over):
        out.alpha_composite(s.crop(box), at)
        out.alpha_composite(s.crop(over), at)

    oy = 8
    part((4, 20, 8, 32), (4, oy + 20), (4, 36, 8, 48))
    part((20, 52, 24, 64), (8, oy + 20), (4, 52, 8, 64))
    part((20, 20, 28, 32), (4, oy + 8), (20, 36, 28, 48))
    part((44, 20, 48, 32), (0, oy + 8), (44, 36, 48, 48))
    part((36, 52, 40, 64), (12, oy + 8), (52, 52, 56, 64))
    part((8, 8, 16, 16), (4, oy), (40, 8, 48, 16))
    if hat == "top":
        d = ImageDraw.Draw(out)
        d.rectangle([2, oy - 1, 13, oy], fill=(16, 16, 22, 255))        # the brim
        d.rectangle([4, oy - 7, 11, oy - 2], fill=(22, 22, 30, 255))    # the crown
        d.rectangle([4, oy - 3, 11, oy - 2], fill=(150, 24, 40, 255))   # the band
    return out


def chest(size=1):
    """A small pixel chest, 16 by 14."""
    im = Image.new("RGBA", (16, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    wood, dark, band, lock = (150, 100, 52, 255), (96, 62, 30, 255), (66, 40, 20, 255), (230, 200, 90, 255)
    d.rectangle([1, 2, 14, 12], fill=wood)
    d.rectangle([1, 2, 14, 5], fill=(170, 118, 62, 255))
    d.rectangle([1, 6, 14, 6], fill=band)
    d.rectangle([1, 12, 14, 12], fill=dark)
    d.rectangle([0, 2, 0, 12], fill=dark)
    d.rectangle([15, 2, 15, 12], fill=dark)
    d.rectangle([7, 5, 8, 8], fill=lock)
    return im


def vanilla(name):
    return load(os.path.join(MCJAR, name))


def item(name):
    return load(os.path.join(TEX, "item", name))


# ---------------------------------------------------------------------------------------------- the icon
def make_icon():
    n = 64
    im = gradient(n, n, (10, 16, 44), (44, 74, 120)).convert("RGBA")
    d = ImageDraw.Draw(im)
    for _ in range(12):
        d.point((random.randrange(n), random.randrange(n // 2)), fill=(255, 244, 200, 255))
    d.ellipse([44, 5, 58, 19], fill=(250, 244, 214, 255))
    d.ellipse([49, 3, 62, 16], fill=(20, 30, 62, 255))
    s = load(os.path.join(TEX, "entity", "thief.png"))
    head = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    head.alpha_composite(s.crop((8, 8, 16, 16)))
    head.alpha_composite(s.crop((40, 8, 48, 16)))
    h = glow(big(head, 3), radius=3)
    im.alpha_composite(h, (32 - h.width // 2, 40 - h.height // 2))
    d.rectangle([0, 56, 63, 63], fill=(18, 20, 30, 255))
    icon = big(im, 8)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 511, 511], radius=96, fill=255)
    out = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    out.paste(icon, (0, 0), mask)
    out.save(os.path.join(OUT, "icon.png"))


# ---------------------------------------------------------------------------------------------- the pictures
def title_card():
    im = night()
    text(im, (70, 120), "Thief", font(F_TITLE, 138))
    text(im, (74, 276), "小偷", font(F_CJK, 72), fill=SOFT)
    text(im, (74, 386), "Thieves visit at night. Catch them.", font(F_BOLD, 40), fill=GOLD)
    text(im, (74, 438), "Tie them up. Collect the bounty.", font(F_BOLD, 40), fill=GOLD)
    text(im, (74, 516), "夜里有小偷来偷东西：抓住它，绑起来，领赏金。", font(F_CJK, 30), fill=SOFT)
    text(im, (74, 562), "Forge 1.20.1  ·  Early release 0.1.0", font(F_BOLD, 26), fill=(160, 180, 220, 255))
    t = glow(big(figure("thief.png"), 10), radius=8)
    im.alpha_composite(t, (1010, 720 - 70 - t.height + 40))
    c = big(chest(), 9)
    im.alpha_composite(c, (770, 720 - 70 - c.height + 2))
    im.convert("RGB").save(os.path.join(OUT, "01_title.png"))


def cast_card():
    im = night()
    text(im, (640, 48), "Who comes by night", font(F_TITLE, 58), anchor="ma")
    text(im, (640, 126), "夜里来的都是谁", font(F_CJK, 36), fill=SOFT, anchor="ma")
    cards = [("Thief", "小偷", ["thief.png"], None, "Robs chests and ripe crops, then runs", "偷箱子和成熟的庄稼，然后跑"),
             ("Crew", "团伙", ["thief.png", "thief.png", "thief.png"], None, "Robbers, a lookout, a sign-bearer", "盗贼、望风的、举牌谈判的"),
             ("Magician", "魔术师", ["magician.png"], "top", "Copies, swaps, cards, smoke, disguise", "分身、掉包、扑克牌、烟雾、伪装")]
    for i, (en, zh, skins, hat, ne, nz) in enumerate(cards):
        cx = 220 + i * 420
        panel(im, (cx - 190, 190, cx + 190, 650), 95)
        k = 7 if len(skins) == 1 else 5
        total = sum(big(figure(s, hat), k).width + 8 for s in skins) - 8
        x = cx - total // 2
        for s in skins:
            f = big(figure(s, hat), k)
            im.alpha_composite(f, (x, 500 - f.height))
            x += f.width + 8
        text(im, (cx, 512), en, font(F_BOLD, 30), anchor="ma")
        text(im, (cx, 552), zh, font(F_CJK, 24), fill=SOFT, anchor="ma")
        text(im, (cx, 592), ne, font(F_BOLD, 17), fill=GOLD, anchor="ma")
        text(im, (cx, 618), nz, font(F_CJK, 16), fill=(220, 230, 250, 255), anchor="ma")
    im.convert("RGB").save(os.path.join(OUT, "02_cast.png"))


def tools_card():
    im = night()
    text(im, (640, 40), "Your tools", font(F_TITLE, 58), anchor="ma")
    text(im, (640, 118), "你的工具", font(F_CJK, 34), fill=SOFT, anchor="ma")
    tools = [("rope.png", "Rope", "绳子", "Tie a thief, lead it"),
             ("whip.png", "Whip", "鞭子", "Make it talk"),
             ("rack.png", "Rack", "刑架", "Spread it out"),
             ("bounty_board.png", "Bounty Board", "悬赏榜", "Turn it in for emeralds"),
             ("thief_tracker.png", "Thief Tracker", "追踪器", "Where is the loot?"),
             ("thief_guide.png", "Thief Guide", "小偷指南", "An in-game book"),
             ("magician_token.png", "Magician's Badge", "魔术师徽章", "Worth 8 emeralds"),
             ("magic_prop.png", "Magician's Prop", "魔术道具", "A fake, left in a chest")]
    panel(im, (50, 170, 1230, 650), 95)
    for i, (f, en, zh, note) in enumerate(tools):
        col, row = i % 4, i // 4
        cx = 195 + col * 300
        cy = 270 + row * 230
        ic = big(item(f), 6)
        im.alpha_composite(ic, (cx - ic.width // 2, cy - 55))
        text(im, (cx, cy + 56), en, font(F_BOLD, 22), anchor="ma")
        text(im, (cx, cy + 88), zh, font(F_CJK, 18), fill=SOFT, anchor="ma")
        text(im, (cx, cy + 112), note, font(F_BOLD, 15), fill=GOLD, anchor="ma")
    im.convert("RGB").save(os.path.join(OUT, "03_tools.png"))


def defence_card():
    im = night()
    text(im, (640, 40), "Keep your things safe", font(F_TITLE, 54), anchor="ma")
    text(im, (640, 116), "守住你的东西", font(F_CJK, 34), fill=SOFT, anchor="ma")
    rows = [(vanilla("bell.png"), "A bell by the chest", "箱子旁挂一口钟", "It rings when robbed; the thief takes only 1 stack"),
            (vanilla("iron_door.png"), "An iron door", "铁门", "Stops a thief; a wooden one does not"),
            (vanilla("bone.png"), "Dogs and wolves", "狗和狼", "Thieves run from them, tamed or wild"),
            (item("thief_tracker.png"), "Tracker", "追踪器", "Points to the one that carries the loot"),
            (item("rope.png"), "Rope", "绳子", "Tie it. No need to fight")]
    panel(im, (60, 170, 1220, 650), 95)
    for i, (icon, en, zh, note) in enumerate(rows):
        y = 190 + i * 92
        ic = big(icon, 5)
        im.alpha_composite(ic, (100, y + 2))
        text(im, (210, y + 2), en, font(F_BOLD, 28))
        text(im, (210 + ImageDraw.Draw(im).textlength(en, font=font(F_BOLD, 28)) + 20, y + 6), zh, font(F_CJK, 22), fill=SOFT)
        text(im, (210, y + 44), note, font(F_BOLD, 19), fill=GOLD, shadow=False)
    im.convert("RGB").save(os.path.join(OUT, "04_defence.png"))


def loop_card():
    im = night()
    text(im, (640, 40), "From robbery to bounty", font(F_TITLE, 54), anchor="ma")
    text(im, (640, 116), "从被偷到领赏金", font(F_CJK, 34), fill=SOFT, anchor="ma")
    steps = [(chest(), "Dusk", "黄昏", "Goes for chests and crops"),
             (vanilla("wheat.png"), "It runs", "它逃跑", "…shining for 20 seconds"),
             (item("rope.png"), "Rope it", "绑住它", "It stops and follows you"),
             (item("bounty_board.png"), "The board", "悬赏榜", "3 emeralds, more for crews"),
             (vanilla("emerald.png"), "Reward", "奖励", "And any loot comes back")]
    panel(im, (40, 190, 1240, 620), 95)
    for i, (icon, en, zh, note) in enumerate(steps):
        cx = 140 + i * 250
        ic = big(icon, 6)
        im.alpha_composite(ic, (cx - ic.width // 2, 270 - ic.height // 2))
        text(im, (cx, 360), en, font(F_BOLD, 26), anchor="ma")
        text(im, (cx, 398), zh, font(F_CJK, 22), fill=SOFT, anchor="ma")
        text(im, (cx, 442), note, font(F_BOLD, 15), fill=GOLD, anchor="ma")
        if i < len(steps) - 1:
            d = ImageDraw.Draw(im)
            x = cx + 108
            d.polygon([(x, 262), (x + 26, 262), (x + 26, 248), (x + 52, 270), (x + 26, 292), (x + 26, 278), (x, 278)], fill=(255, 255, 255, 220))
    text(im, (640, 520), "Or hang it up, tie it to a tree, spread it on a rack — it resents it.", font(F_BOLD, 22), anchor="ma")
    text(im, (640, 558), "也可以吊起来、绑在树上、绑在刑架上——它会记仇。", font(F_CJK, 20), fill=SOFT, anchor="ma")
    im.convert("RGB").save(os.path.join(OUT, "05_loop.png"))


def magician_card():
    im = night()
    text(im, (60, 40), "The Magician", font(F_TITLE, 64))
    text(im, (60, 122), "魔术师", font(F_CJK, 36), fill=SOFT)
    f = glow(big(figure("magician.png", "top"), 12), color=(190, 120, 255), radius=10)
    im.alpha_composite(f, (80, 720 - 70 - f.height + 36))
    lines = [("Copies of itself, every 20 s — the real one has the boss bar", "每 20 秒做分身，真身带血条"),
             ("Reaches into your chests, and leaves a prop in their place", "隔空取箱子里的东西，换成道具"),
             ("Throws cards, drops smoke, swaps places", "扔扑克牌、放烟雾、互换位置"),
             ("Drinks a potion and looks like an animal or a villager", "喝药水，伪装成动物或村民"),
             ("The rope holds it only when it is worn down", "要打到残血，绳子才绑得住")]
    panel(im, (420, 170, 1240, 650), 100)
    for i, (en, zh) in enumerate(lines):
        y = 200 + i * 90
        text(im, (450, y), en, font(F_BOLD, 22))
        text(im, (450, y + 34), zh, font(F_CJK, 19), fill=SOFT)
    im.convert("RGB").save(os.path.join(OUT, "06_magician.png"))


if __name__ == "__main__":
    make_icon()
    title_card()
    cast_card()
    tools_card()
    defence_card()
    loop_card()
    magician_card()
    print("done")
