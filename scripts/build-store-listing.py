"""Build store graphics, normalize captures without stretching, validate and zip.

Requires Pillow. Run after StoreListingCaptureTest from the repository root.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'store-listing'

def font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/' + ('segoeuib.ttf' if bold else 'segoeui.ttf'), size)

def graphics():
    icon = Image.open(ROOT / 'app/src/main/res/drawable-nodpi/sinkpoint_icon_art.png')
    icon.convert('RGBA').resize((512, 512), Image.Resampling.LANCZOS).save(OUT / 'app-icon-512.png')
    # Original code-drawn artwork using the board shapes and the app's palette.
    scale = 3
    art = Image.new('RGB', (1024 * scale, 500 * scale), '#35228e')
    d = ImageDraw.Draw(art)
    def rect(box, fill, radius=0, outline=None, width=1):
        d.rounded_rectangle(tuple(int(x * scale) for x in box), radius * scale, fill, outline, width * scale)
    for y in range(500):
        t = y / 499
        color = tuple(round(a * (1-t) + b*t) for a, b in zip((101, 74, 227), (49, 31, 133)))
        d.line((0, y*scale, 3072, y*scale), fill=color, width=scale)
    # Subtle board pattern extends to the edges; title stays within crop-safe space.
    for x in range(0, 1024, 62):
        d.line((x*scale, 0, x*scale, 1500), fill='#654cce', width=2*scale)
    for y in range(0, 500, 62):
        d.line((0, y*scale, 3072, y*scale), fill='#654cce', width=2*scale)
    rect((52, 110, 555, 384), '#423098', 28)
    d.text((80*scale, 142*scale), 'Sinkpoint', font=font(70*scale, True), fill='#ffffff')
    d.text((84*scale, 245*scale), 'Plan your next shot.', font=font(24*scale), fill='#dbd4ff')
    d.text((84*scale, 302*scale), 'Solo or nearby multiplayer', font=font(19*scale), fill='#b8e9d7')
    # A stylized game board, not a fictional screenshot.
    for row in range(5):
        for col in range(5):
            x, y = 612 + col*61, 99 + row*61
            rect((x, y, x+53, y+53), '#7961df', 10)
    rect((612, 160, 787, 213), '#b8e9d7', 14)
    rect((795, 282, 909, 335), '#b8e9d7', 14)
    for x, y in [(638, 186), (699, 186), (821, 308)]:
        d.ellipse(((x-11)*scale, (y-11)*scale, (x+11)*scale, (y+11)*scale), fill='#ff7969')
    for x, y in [(882,125), (699,308), (760,369)]:
        d.ellipse(((x-5)*scale, (y-5)*scale, (x+5)*scale, (y+5)*scale), fill='#eae6ff')
    d.ellipse((808*scale, 173*scale, 834*scale, 199*scale), outline='#ffffff', width=2*scale)
    d.line((821*scale, 165*scale, 821*scale, 207*scale), fill='white', width=2*scale)
    d.line((800*scale, 186*scale, 842*scale, 186*scale), fill='white', width=2*scale)
    art.resize((1024, 500), Image.Resampling.LANCZOS).save(OUT / 'featured-graphic-1024x500.png')

def package():
    groups = {'phone-screenshots': ((1080, 1920), 8), 'phone-landscape-screenshots': ((1920, 1080), 4),
              'tablet-screenshots/portrait': ((1440, 2560), 4), 'tablet-screenshots/landscape': ((2560, 1440), 4)}
    assets = []
    for group, (size, count) in groups.items():
        files = sorted((OUT / group).glob('*.png'))
        assert len(files) == count, (group, len(files), count)
        for file in files:
            with Image.open(file) as source:
                assert source.size == size, (file, source.size, size)
                # Flatten platform output to a 24-bit PNG; preserve native pixels.
                rgb = Image.new('RGB', source.size, '#f7f8fc')
                if source.mode == 'RGBA':
                    rgb.paste(source, mask=source.getchannel('A'))
                else:
                    rgb.paste(source.convert('RGB'))
            rgb.save(file, optimize=True)
            assets.append({'file': file.relative_to(OUT).as_posix(), 'width': size[0], 'height': size[1], 'bytes': file.stat().st_size})
    for name, size in [('app-icon-512.png', (512, 512)), ('featured-graphic-1024x500.png', (1024, 500))]:
        with Image.open(OUT / name) as image:
            assert image.size == size
            assert image.mode == ('RGBA' if name.startswith('app-icon') else 'RGB')
        assert (OUT / name).stat().st_size < 1024*1024
    counts = {name: len((OUT / name).read_text(encoding='utf-8').strip()) for name in ['title.txt', 'short-description.txt', 'full-description.txt']}
    for name, limit in [('title.txt', 30), ('short-description.txt', 80), ('full-description.txt', 4000)]:
        assert counts[name] <= limit, (name, counts[name])
    report = {'character_counts': counts, 'screenshots': assets, 'capture': 'Native Compose / Robolectric SDK 30, deterministic demo match; not physical-device captures'}
    (OUT / 'validation.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    archive = ROOT / 'store-listing-package.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in sorted(OUT.rglob('*')):
            if file.is_file():
                z.write(file, file.relative_to(ROOT))
    print(json.dumps({'screenshots': len(assets), 'character_counts': counts, 'zip_bytes': archive.stat().st_size}, indent=2))

if __name__ == '__main__':
    graphics()
    package()
