"""Makes Minecraft's grey inventory/chest screens light blue. Run by the build, not shipped."""
import os, sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
TINT = (0.78, 0.92, 1.0)  # keeps black/white, turns grey into light blue

for root, _, files in os.walk(src):
    for f in files:
        if not f.endswith('.png'):
            continue
        im = Image.open(os.path.join(root, f)).convert('RGBA')
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if a and max(r, g, b) - min(r, g, b) < 12:  # only grey pixels
                    px[x, y] = (int(r * TINT[0]), int(g * TINT[1]), min(255, int(b * TINT[2]) + 12), a)
        out = os.path.join(dst, os.path.relpath(root, src))
        os.makedirs(out, exist_ok=True)
        im.save(os.path.join(out, f))
