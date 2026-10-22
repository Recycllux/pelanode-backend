#!/usr/bin/env python3
"""Draw labelled placeholder frames for every media entry in the pack (step D-02 fallback).

Each frame says PLACEHOLDER and its media ID, so nobody mistakes it for real footage.
Usage: python3 scripts/make_placeholder_frames.py [pack_dir]   (needs Pillow)
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PACK = Path(sys.argv[1] if len(sys.argv) > 1 else "data/demo/hack-001")
COLOURS = {"surface": (120, 170, 200), "underwater": (20, 80, 90)}


def font(size):
    for name in ("DejaVuSans-Bold.ttf", "Arial Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    media = json.loads((PACK / "media.json").read_text(encoding="utf-8"))
    for m in media:
        folder = m["uri"].split("/")[2]
        img = Image.new("RGB", (m["width_px"], m["height_px"]), COLOURS[folder])
        d = ImageDraw.Draw(img)
        d.text((40, 40), "PLACEHOLDER", fill="white", font=font(64))
        d.text((40, 130), m["media_id"], fill="white", font=font(40))
        d.text((40, 190), f"{folder} camera, not real footage", fill="white", font=font(28))
        out = PACK / "media" / folder / f"{m['media_id']}.jpg"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out, "JPEG", quality=80)
    print(f"{len(media)} placeholder frames written under {PACK / 'media'}")


if __name__ == "__main__":
    main()
