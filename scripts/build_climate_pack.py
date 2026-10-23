#!/usr/bin/env python3
"""Build the monthly climate grids from the organiser's Google Earth Engine exports (Share/gee_output).

Each GeoTIFF is one month of 2024 over Bulgaria. The TIFFs carry no georeference tags, so the grid
coordinates come from the CSV export of the same grid (lon, lat, value; row-major from the north-west
corner, checked against the TIFF values). Output: one CSV per variable with a lon/lat column pair and
one column per month (2024-01 ... 2024-12).

Needs: tifffile, numpy (pip install tifffile numpy).
Usage: python3 scripts/build_climate_pack.py <Share/gee_output dir> [output_dir]   (default data/climate)
"""
import csv
import re
import sys
from pathlib import Path

import numpy as np
import tifffile

SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("Share/gee_output")
OUT = Path(sys.argv[2] if len(sys.argv) > 2 else "data/climate")
MONTH = re.compile(r"_(2024-\d\d)-01_\d{4}-\d\d-01\.tif$")


def grid(variable):
    sample = sorted((SRC / variable).glob("*.csv"))[0]
    with open(sample, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return [(float(r["lon"]), float(r["lat"])) for r in rows]


def build(variable):
    cells = grid(variable)
    months = {}
    for tif in sorted((SRC / variable).glob("*.tif")):
        m = MONTH.search(tif.name)
        if not m:
            continue
        values = tifffile.imread(tif).astype(float).ravel()
        if len(values) != len(cells):
            raise SystemExit(f"{tif.name}: {len(values)} cells, grid has {len(cells)}")
        months[m.group(1)] = values
    keys = sorted(months)
    OUT.mkdir(parents=True, exist_ok=True)
    with open(OUT / f"{variable}.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["lon", "lat", *keys])
        for i, (lon, lat) in enumerate(cells):
            row = [f"{lon:.6f}", f"{lat:.6f}"]
            for k in keys:
                v = months[k][i]
                row.append("" if np.isnan(v) else f"{v:.4g}")
            w.writerow(row)
    print(f"{variable}: {len(cells)} cells x {len(keys)} months -> {OUT / (variable + '.csv')}")


if __name__ == "__main__":
    build("precipitation")
    build("soil_moisture")
