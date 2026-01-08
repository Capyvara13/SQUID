"""Convert dashboard-qt/icon.jpg to dashboard-qt/icon.ico using Pillow.

Usage:
    python convert_icon.py

This script will read `icon.jpg` (or `icon.png`) next to this script and
produce `icon.ico` with common sizes (256x256, 48x48, 32x32, 16x16) suitable
for PyInstaller `--icon` option on Windows.
"""
from PIL import Image
from pathlib import Path
import sys

BASE = Path(__file__).parent
jpg = BASE / 'icon.jpg'
png = BASE / 'icon.png'
ico = BASE / 'icon.ico'

src = None
if jpg.exists():
    src = jpg
elif png.exists():
    src = png
else:
    print('No icon.jpg or icon.png found in dashboard-qt; skipping icon conversion')
    sys.exit(0)

try:
    im = Image.open(src)
    # Ensure RGBA for correct conversion
    if im.mode != 'RGBA':
        im = im.convert('RGBA')
    sizes = [(256,256),(48,48),(32,32),(16,16)]
    # Pillow can save multiple sizes in one ICO file by resizing into a list
    icons = [im.resize(s, Image.LANCZOS) for s in sizes]
    icons[0].save(ico, format='ICO', sizes=sizes)
    print(f'Created icon: {ico}')
except Exception as e:
    print('Icon conversion failed:', e)
    sys.exit(1)
