"""Generate the original N0d3 KeyShell boot animation and its previews."""

import io
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw


WIDTH, HEIGHT, SCALE, FPS = 240, 320, 3, 6
BACKGROUND = (12, 9, 21)
PAD = (40, 31, 63)
WHITE = (247, 241, 255)
VIOLET = (190, 156, 255)
MINT = (138, 242, 211)

# Five-by-seven glyphs keep the wordmark consistent across build machines.
GLYPHS = {
    "N": ("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    "0": ("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    "d": ("00001", "00001", "01111", "10001", "10001", "10001", "01111"),
    "3": ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    "K": ("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    "e": ("00000", "00000", "01110", "10001", "11111", "10000", "01111"),
    "y": ("00000", "00000", "10001", "10001", "01111", "00001", "11110"),
    "S": ("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    "h": ("10000", "10000", "10110", "11001", "10001", "10001", "10001"),
    "l": ("01100", "00100", "00100", "00100", "00100", "00100", "01110"),
}


def frame(opacity, phase):
    image = Image.new("RGB", (WIDTH * SCALE, HEIGHT * SCALE), BACKGROUND)
    draw = ImageDraw.Draw(image)

    def rect(bounds, color, radius=0, outline=None):
        draw.rounded_rectangle(
            tuple(n * SCALE for n in bounds), radius=radius * SCALE,
            fill=color, outline=outline, width=SCALE,
        )

    def word(text, top, pixel, color):
        width = (len(text) * 6 - 1) * pixel
        for index, char in enumerate(text):
            for row, bits in enumerate(GLYPHS[char]):
                for column, bit in enumerate(bits):
                    if bit == "1":
                        x = ((WIDTH - width) / 2 + (index * 6 + column) * pixel) * SCALE
                        y = (top + row * pixel) * SCALE
                        draw.rectangle((x, y, x + pixel * SCALE - 1, y + pixel * SCALE - 1), fill=color)

    rect((78, 69, 162, 153), (23, 17, 39), 20, (62, 46, 93))
    keys = (
        (120, 89, ((116, 91), (120, 86), (124, 91))),
        (142, 111, ((140, 107), (145, 111), (140, 115))),
        (120, 133, ((116, 131), (120, 136), (124, 131))),
        (98, 111, ((100, 107), (95, 111), (100, 115))),
    )
    active_key = (0, 1, 2, 3, 4, 4)[phase]
    for index, (x, y, arrow) in enumerate(keys):
        selected = active_key == index
        rect((x - 9, y - 9, x + 9, y + 9), MINT if selected else PAD, 4)
        draw.polygon([(a * SCALE, b * SCALE) for a, b in arrow], fill=BACKGROUND if selected else VIOLET)
    rect((110, 101, 130, 121), VIOLET if active_key == 4 else (67, 48, 104), 5)
    rect((117, 108, 123, 114), BACKGROUND if active_key == 4 else VIOLET, 2)

    word("N0d3", 174, 4, WHITE)
    word("KeyShell", 210, 2, VIOLET)
    for index in range(6):
        x = 94 + index * 9
        color = MINT if index == phase else PAD
        rect((x, 246, x + 6, 248), color, 1)

    if opacity < 1:
        image = Image.blend(Image.new("RGB", image.size, BACKGROUND), image, opacity)
    return image.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)


def main():
    output = Path(__file__).resolve().parent / "output"
    output.mkdir(exist_ok=True)
    intro = [frame(level, 0) for level in (0.12, 0.35, 0.7, 1)]
    loop = [frame(1, phase) for phase in range(6)]

    target = output / "bootanimation.zip"
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED) as archive:
        desc = f"{WIDTH} {HEIGHT} {FPS}\np 1 0 part0\np 0 0 part1\n"
        archive.writestr(zipfile.ZipInfo("desc.txt"), desc)
        for part, frames in (("part0", intro), ("part1", loop)):
            for index, image in enumerate(frames):
                data = io.BytesIO()
                image.save(data, "PNG", optimize=True)
                archive.writestr(zipfile.ZipInfo(f"{part}/{index:04d}.png"), data.getvalue())

    loop[0].save(output / "keyshell-bootanimation-preview.png")
    loop[0].save(
        output / "keyshell-bootanimation-preview.gif", save_all=True, append_images=loop[1:],
        duration=170, loop=0,
    )
    print(f"{target.name}: {target.stat().st_size} bytes, {WIDTH}x{HEIGHT}, {FPS} fps")


if __name__ == "__main__":
    main()
