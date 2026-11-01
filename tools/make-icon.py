"""Draws the mod icon: a trade row (iron ingot -> emerald), the Trade All button and a stock bar, as 32x32 pixel art
scaled to 128x128.

Run from the repo root:  python -I tools/make-icon.py
Writes src/main/resources/assets/villager-trade-helper/icon.png. No dependencies beyond the standard library.
"""
import struct
import sys
import zlib
from pathlib import Path

SIZE = 32
SCALE = 4
OUT = Path("src/main/resources/assets/villager-trade-helper/icon.png")

# Vanilla GUI colours, and the button colours from TradeButton.java.
OUTLINE = (0x10, 0x10, 0x10)
HIGHLIGHT = (0xFF, 0xFF, 0xFF)
PANEL = (0xC6, 0xC6, 0xC6)
SHADOW = (0x55, 0x55, 0x55)
SLOT_DARK = (0x37, 0x37, 0x37)
SLOT_LIGHT = (0xFF, 0xFF, 0xFF)
SLOT_FILL = (0x8B, 0x8B, 0x8B)
ARROW = (0x40, 0x40, 0x40)
BUTTON_HOVER = {"body": (0x6A, 0x78, 0xC8), "light": (0xA3, 0xAE, 0xF0), "dark": (0x3B, 0x43, 0x85), "icon": (0xFF, 0xFF, 0xFF)}
BAR_TRACK = (0x2B, 0x2B, 0x2B)
BAR = (0x55, 0xFF, 0x55)

IRON = {"h": (0xFF, 0xFF, 0xFF), "L": (0xD8, 0xD8, 0xD8), "d": (0x7E, 0x7E, 0x7E)}
INGOT = [
    ".......",
    "..hhhh.",
    ".hLLLLd",
    "hLLLLdd",
    "dddddd.",
    ".......",
]
EMERALD = {"h": (0xB6, 0xFF, 0xC8), "L": (0x17, 0xDD, 0x62), "d": (0x0B, 0x8A, 0x3C)}
GEM = [
    "...h...",
    "..hLL..",
    ".hLLLd.",
    "hLLLLdd",
    ".dLLdd.",
    "..ddd..",
    "...d...",
]
RIGHT_ARROW = [
    "...#..",
    "...##.",
    "######",
    "...##.",
    "...#..",
]
# Double chevron: "trade it all".
FAST_FORWARD = [
    "#...#..",
    ".#...#.",
    "..#...#",
    ".#...#.",
    "#...#..",
]

img = [[None] * SIZE for _ in range(SIZE)]


def px(x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(x, y, c)


def sprite(x, y, rows, colours):
    for sy, row in enumerate(rows):
        for sx, ch in enumerate(row):
            colour = colours.get(ch)
            if colour:
                px(x + sx, y + sy, colour)


def panel(x0, y0, x1, y1):
    """Vanilla-style GUI panel with rounded corners."""
    rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL)
    rect(x0 + 2, y0, x1 - 2, y0, OUTLINE)
    rect(x0 + 2, y1, x1 - 2, y1, OUTLINE)
    rect(x0, y0 + 2, x0, y1 - 2, OUTLINE)
    rect(x1, y0 + 2, x1, y1 - 2, OUTLINE)
    for cx, cy in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        px(cx, cy, OUTLINE)
    rect(x0 + 2, y0 + 1, x1 - 2, y0 + 1, HIGHLIGHT)
    rect(x0 + 1, y0 + 2, x0 + 1, y1 - 2, HIGHLIGHT)
    rect(x0 + 2, y1 - 1, x1 - 2, y1 - 1, SHADOW)
    rect(x1 - 1, y0 + 2, x1 - 1, y1 - 2, SHADOW)


def slot(x, y):
    """9x9 slot: dark top/left edge, light bottom/right edge."""
    rect(x, y, x + 8, y + 8, SLOT_FILL)
    rect(x, y, x + 7, y, SLOT_DARK)
    rect(x, y + 1, x, y + 7, SLOT_DARK)
    rect(x + 1, y + 8, x + 8, y + 8, SLOT_LIGHT)
    rect(x + 8, y + 1, x + 8, y + 7, SLOT_LIGHT)


def button(x0, y0, x1, y1, glyph, colours):
    rect(x0, y0, x1, y1, colours["body"])
    rect(x0, y0, x1 - 1, y0, colours["light"])
    rect(x0, y0 + 1, x0, y1 - 1, colours["light"])
    rect(x0 + 1, y1, x1, y1, colours["dark"])
    rect(x1, y0 + 1, x1, y1 - 1, colours["dark"])
    width = len(glyph[0])
    height = len(glyph)
    sprite((x0 + x1 + 1 - width) // 2, (y0 + y1 + 1 - height) // 2, glyph, {"#": colours["icon"]})


panel(0, 0, 31, 31)
slot(3, 4)
sprite(4, 5, INGOT, IRON)
sprite(13, 6, RIGHT_ARROW, {"#": ARROW})
slot(20, 4)
sprite(21, 5, GEM, EMERALD)
button(3, 16, 28, 23, FAST_FORWARD, BUTTON_HOVER)
rect(3, 26, 28, 27, BAR_TRACK)
rect(3, 26, 20, 27, BAR)


def write_png(path, pixels, scale):
    rows = []
    for row in pixels:
        line = bytearray()
        for c in row:
            rgba = (0, 0, 0, 0) if c is None else (*c, 255)
            line += bytes(rgba) * scale
        rows += [b"\x00" + bytes(line)] * scale
    width = len(pixels[0]) * scale
    height = len(pixels) * scale

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.parent.mkdir(parents=True, exist_ok=True)
    write_png(out, img, SCALE)
    print(f"wrote {out} ({SIZE * SCALE}x{SIZE * SCALE})")
