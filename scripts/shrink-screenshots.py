#!/usr/bin/env python3
"""Downscale captured screenshots so they can be reviewed in the repository."""
import sys
from pathlib import Path

from PIL import Image

width = 540
for path in sorted(Path(sys.argv[1]).glob("*.png")):
    img = Image.open(path)
    img.resize((width, round(img.height * width / img.width))).save(path, optimize=True)
    print(path, path.stat().st_size)
