#!/usr/bin/env python3
"""Build the Iskar discharge pack from the two organiser CSVs (EIT Water Hackathon, Share/).

Inputs (UTF-8, columns date, discharge(m3/s)):
  - Искър_Нови_Искър_2025.csv   Iskar at Novi Iskar (upstream, near Sofia)
  - Искър_Ореховица_2025.csv    Iskar at Orehovitsa (lower Iskar, nearer the Danube)

Values are kept as published (rounded to 3 decimals). Missing days stay missing; nothing is filled.
Station coordinates are not in the source files: the table below holds approximate positions,
flagged coords_approx=true.

Usage: python3 scripts/build_iskar_pack.py <Share dir> [output_dir]   (default data/iskar)
"""
import csv
import sys
from pathlib import Path

SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("Share")
OUT = Path(sys.argv[2] if len(sys.argv) > 2 else "data/iskar")

# file -> (station_id, English name, Bulgarian name, lat, lon)
STATIONS = {
    "Искър_Нови_Искър_2025.csv": ("NOVI_ISKAR", "Novi Iskar", "Нови Искър", 42.82, 23.35),
    "Искър_Ореховица_2025.csv": ("OREHOVITSA", "Orehovitsa", "Ореховица", 43.60, 24.37),
}

OUT.mkdir(parents=True, exist_ok=True)
rows = []
for name, (sid, *_rest) in STATIONS.items():
    with open(SRC / name, encoding="utf-8") as f:
        seen = set()
        for r in csv.DictReader(f):
            day = r["date"].strip()[:10]
            value = r["discharge(m3/s)"].strip()
            if not day or not value or day in seen:
                continue
            seen.add(day)
            rows.append((day, sid, round(float(value), 3)))
rows.sort()
with open(OUT / "discharge.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["date", "station_id", "discharge_m3s"])
    w.writerows(rows)
with open(OUT / "stations.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["station_id", "name", "name_bg", "river", "lat", "lon", "coords_approx"])
    for sid, en, bg, lat, lon in STATIONS.values():
        w.writerow([sid, en, bg, "Iskar", lat, lon, "true"])
print(f"{len(rows)} rows -> {OUT}")
