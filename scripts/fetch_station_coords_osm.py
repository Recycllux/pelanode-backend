#!/usr/bin/env python3
"""Place Danube gauges on the river from OpenStreetMap Danube kilometre milestones.

The organiser data gives each Danube station its river km but no coordinates. OSM maps the Danube
kilometre posts as waterway=milestone nodes with a distance tag. For each Danube station this script
fetches the posts at floor(km) and ceil(km) near the station's approximate position, takes the post
closest to that position for each km (the gauge's bank, since the approximate point is the bank-side
town), and interpolates between them at the station's km.

Stations already sourced in station_coords.csv (e.g. PEGELONLINE) are kept. Results are appended as
"OSM Danube km milestones ..." rows; then rerun build_danube_pack.py to rebuild stations.csv.

Needs internet access to the Overpass API (run it on a workstation, not in a restricted sandbox).
Usage: python3 scripts/fetch_station_coords_osm.py [data/danube]
"""
import csv
import json
import math
import sys
import urllib.parse
import urllib.request
from pathlib import Path

DIR = Path(sys.argv[1] if len(sys.argv) > 1 else "data/danube")
OVERPASS = "https://overpass-api.de/api/interpreter"
BOX = 0.2  # degrees around the approximate point
MAX_SHIFT_KM = 15.0  # reject a result this far from the approximate point


def dist_km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 12742 * math.asin(math.sqrt(h))


def query(stations):
    parts = []
    for s in stations:
        ks = sorted({math.floor(s["km"]), math.ceil(s["km"])})
        rx = "|".join(str(k) for k in ks)
        bb = f'{s["lat"] - BOX},{s["lon"] - BOX},{s["lat"] + BOX},{s["lon"] + BOX}'
        parts.append(f'node["waterway"="milestone"]["distance"~"^({rx})([.,]0+)?$"]({bb});')
    q = "[out:json][timeout:180];(" + "".join(parts) + ");out;"
    data = urllib.parse.urlencode({"data": q}).encode()
    req = urllib.request.Request(OVERPASS, data=data, headers={"User-Agent": "pelanode-station-coords/1.0"})
    with urllib.request.urlopen(req, timeout=240) as r:
        return json.load(r)["elements"]


def main():
    coords_file = DIR / "station_coords.csv"
    sourced = {}
    if coords_file.exists():
        with open(coords_file, encoding="utf-8", newline="") as f:
            sourced = {r["station_id"]: r for r in csv.DictReader(f)}
    with open(DIR / "stations.csv", encoding="utf-8", newline="") as f:
        todo = [{"id": r["station_id"], "km": float(r["river_km"]), "lat": float(r["lat"]), "lon": float(r["lon"])}
                for r in csv.DictReader(f)
                if r["river"] == "Danube" and r["river_km"] and r["station_id"] not in sourced]
    if not todo:
        print("nothing to do")
        return
    posts = {}
    for e in query(todo):
        try:
            k = round(float(e["tags"]["distance"].replace(",", ".")))
        except (KeyError, ValueError):
            continue
        posts.setdefault(k, []).append((e["lat"], e["lon"]))

    found, missing = [], []
    for s in todo:
        here = (s["lat"], s["lon"])
        lo_k, hi_k = math.floor(s["km"]), math.ceil(s["km"])
        near = {}
        for k in {lo_k, hi_k}:
            cands = [p for p in posts.get(k, []) if dist_km(p, here) <= MAX_SHIFT_KM]
            if cands:
                near[k] = min(cands, key=lambda p: dist_km(p, here))
        if lo_k in near and hi_k in near:
            t = 0.0 if hi_k == lo_k else (hi_k - s["km"]) / (hi_k - lo_k)  # km decreases downstream
            a, b = near[hi_k], near[lo_k]
            lat, lon = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            note = f"OSM Danube km milestones {hi_k}/{lo_k} interpolated at km {s['km']:g} (ODbL)"
        elif near:
            k, (lat, lon) = next(iter(near.items()))
            note = f"OSM Danube km milestone {k} nearest to km {s['km']:g} (ODbL)"
        else:
            missing.append(s["id"])
            continue
        found.append({"station_id": s["id"], "lat": f"{lat:.6f}", "lon": f"{lon:.6f}", "coords_source": note})

    rows = list(sourced.values()) + found
    with open(coords_file, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["station_id", "lat", "lon", "coords_source"], lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print(f"placed={len(found)} missing={len(missing)} {' '.join(missing)}")
    print("now rerun: python3 scripts/build_danube_pack.py <Share/Danube dir>", DIR)


if __name__ == "__main__":
    main()
