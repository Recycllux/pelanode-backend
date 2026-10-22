#!/usr/bin/env python3
"""Rebuild the minimum HACK-001 demo pack from the P0-08 storyboard values.

Fallback for step D-01 while the original pack files are missing. Every record is
labelled simulated/replayed and every media file is a placeholder. Re-running the
script gives byte-identical files (no randomness, fixed timestamps).

Usage: python3 scripts/build_demo_pack.py [output_dir]   (default data/demo/hack-001)
"""
import json
import math
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "data/demo/hack-001")
START = datetime(2026, 10, 24, 14, 22, tzinfo=timezone.utc)
MINUTES = 42                       # 14:22 -> 15:04, 43 points (P0-08)
LAT0, LON0 = 43.8990, 25.9200      # Lower Danube demonstration zone (synthetic)
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LON = M_PER_DEG_LAT * math.cos(math.radians(LAT0))
DRIFT_M = 1700.0                   # about 1.7 km over the mission (P0-08 scene 3)


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%S.000Z")


def dump(name, data):
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def track():
    points = []
    step = DRIFT_M / MINUTES
    for i in range(MINUTES + 1):
        east = step * i
        north = 60.0 * math.sin(i / MINUTES * math.pi)       # gentle meander
        lat = round(LAT0 + north / M_PER_DEG_LAT, 6)
        lon = round(LON0 + east / M_PER_DEG_LON, 6)
        heading = round((90.0 - math.degrees(math.atan2(
            60.0 * math.pi / MINUTES * math.cos(i / MINUTES * math.pi), step))) % 360, 1)
        points.append({
            "track_point_id": f"TP-HACK-001-{i + 1:04d}",
            "mission_id": "HACK-001", "node_id": "PN-MVP-001",
            "captured_at": iso(START + timedelta(minutes=i)),
            "lat": lat, "lon": lon, "h_acc_m": 3.0,
            "speed_mps": round(step / 60.0, 2), "heading_deg": heading,
            "source": "replayed", "quality_flag": "demo_only", "data_origin": "replayed",
        })
    return points


# The three storyboard events (P0-08 scenes 4-6). EV-0144 keeps the P0-08 values;
# its perspective/class pair is still open (P0-11 C-12), and its frame ID is not in P0-08,
# so it carries a TBD media ID until the footage arrives.
EVENTS = [
    {"n": "0142", "minute": 10, "primary": "S", "media": "SURF-003210", "pair_media": "UW-003210",
     "class": "anthropogenic_debris", "confidence": 0.93, "quality": 0.84, "flags": [],
     "bbox": [412, 288, 96, 64]},
    {"n": "0143", "minute": 15, "primary": "U", "media": "UW-003892", "pair_media": "SURF-003892",
     "class": "submerged_debris", "confidence": 0.82, "quality": 0.71, "flags": [],
     "bbox": [520, 340, 120, 88]},
    {"n": "0144", "minute": 19, "primary": "U", "media": "UW-TBD-0144", "pair_media": "SURF-TBD-0144",
     "class": "surface_anomaly", "confidence": 0.76, "quality": 0.38, "flags": ["low_visibility"],
     "bbox": [300, 260, 180, 120]},
]

SENSOR = {"S": ("camera_surface", "above", "CAM-SURF"), "U": ("camera_underwater", "below", "CAM-UW")}


def observations_media_outputs(points):
    observations, media, outputs = [], [], []
    for e in EVENTS:
        p = points[e["minute"]]
        t = START + timedelta(minutes=e["minute"])
        other = "U" if e["primary"] == "S" else "S"
        ids = {k: f"OBS-HACK-001-{k}-{e['n']}" for k in ("S", "U")}
        for kind, media_id, gap in ((e["primary"], e["media"], 0), (other, e["pair_media"], 400)):
            sensor, perspective, camera = SENSOR[kind]
            primary = kind == e["primary"]
            observations.append({
                "observation_id": ids[kind], "mission_id": "HACK-001", "node_id": "PN-MVP-001",
                "captured_at": iso(t + timedelta(milliseconds=gap)),
                "lat": p["lat"], "lon": p["lon"], "h_acc_m": 3.0, "position_source": "replayed",
                "speed_mps": p["speed_mps"], "heading_deg": p["heading_deg"],
                "roll_deg": None, "pitch_deg": None,
                "source_sensor": sensor, "perspective": perspective, "camera_id": camera,
                "sequence": int(media_id.split("-")[1]) if media_id.split("-")[1].isdigit() else None,
                "paired_observation_id": ids[other], "pair_gap_ms": 400,
                "media_id": media_id, "media_status": "ok",
                "quality_score": e["quality"] if primary else 0.8,
                "quality_flags": e["flags"] if primary else [],
                "sensors": {"water_temp_c": None, "turbidity_ntu": None},
                "data_origin": "replayed", "schema_version": "1.0",
            })
            folder = "surface" if kind == "S" else "underwater"
            media.append({
                "media_id": media_id, "observation_id": ids[kind], "kind": "frame",
                "uri": f"/media/{folder}/{media_id}.jpg", "width_px": 1280, "height_px": 720,
                "mime_type": "image/jpeg", "provenance": "placeholder",
                "licence_note": "Placeholder until the team footage is delivered", "sha256": None,
            })
        outputs.append({
            "model_output_id": f"MO-HACK-001-{e['n']}", "observation_id": ids[e["primary"]],
            "task": "anomaly" if e["class"] == "surface_anomaly" else "detection",
            "taxonomy": "pn-classes-0.1", "class": e["class"], "confidence": e["confidence"],
            "bbox": e["bbox"], "mask_uri": None, "model_id": "pn-det", "model_version": "0.1",
            # Hand-authored pack scores, not model output: SIMULATED INFERENCE (P0-15, P15-02)
            "inference_origin": "simulated",
            "processed_at": "2026-10-22T16:00:00.000Z", "processing_ms": None,
            "review_required": e["class"] == "surface_anomaly", "schema_version": "1.0",
        })
    return observations, media, outputs


def device_status():
    rows = []
    for i in range(0, MINUTES + 1, 2):
        rows.append({
            "status_id": f"ST-HACK-001-{i:04d}", "node_id": "PN-MVP-001", "mission_id": "HACK-001",
            "observed_at": iso(START + timedelta(minutes=i)),
            "battery_pct": round(82 - i * 0.1, 1), "connectivity": "online", "gnss_status": "fix",
            "surface_camera": "ok", "underwater_camera": "ok", "storage_pct": round(41 + i * 0.05, 1),
            "data_origin": "simulated",
        })
    return rows


def context():
    """Hourly NIMH/EAMEDR-style series, 08:22Z -> 15:22Z (P0-08 scene 2)."""
    datasets = [
        {"dataset_id": "sim_hydro_discharge", "provider": "Simulated (NIMH-style)",
         "name": "Simulated river discharge", "variables": ["discharge"], "format": "json",
         "licence": "Synthetic, HACK-001 pack", "provenance": "simulated", "status": "loaded"},
        {"dataset_id": "sim_hydro_level", "provider": "Simulated (EAMEDR-style)",
         "name": "Simulated water level", "variables": ["water_level"], "format": "json",
         "licence": "Synthetic, HACK-001 pack", "provenance": "simulated", "status": "loaded"},
    ]
    stations = {
        "sim_hydro_discharge": ("STN-SIM-01", "Simulated station 1", 43.8500, 25.8200, "discharge", "m3/s",
                                820.0, 1256.0, 0),
        "sim_hydro_level": ("STN-SIM-02", "Simulated station 2", 43.9300, 25.9900, "water_level", "m",
                            4.10, 5.36, 2),
    }
    records = []
    t0 = datetime(2026, 10, 24, 8, 22, tzinfo=timezone.utc)
    for ds, (sid, sname, lat, lon, var, unit, v0, v1, dec) in stations.items():
        for h in range(8):
            # Slow start, steeper rise; the exponent puts discharge at ~1,173 m3/s at 14:22Z,
            # the value P0-08 scene 5 quotes for EV-0143.
            frac = (h / 7) ** 1.367
            value = round(v0 + (v1 - v0) * frac, dec)
            t = t0 + timedelta(hours=h)
            stamp = t.strftime("%Y-%m-%dT%H:%M:%SZ")
            records.append({
                "context_record_id": f"{ds}:{sid}:{stamp}", "dataset_id": ds,
                "provider": next(d["provider"] for d in datasets if d["dataset_id"] == ds),
                "variable": var, "value": value, "unit": unit,
                "source_value": str(value), "source_unit": unit,
                "observed_at": iso(t), "source_timestamp": stamp,
                "station_id": sid, "station_name": sname, "lat": lat, "lon": lon,
                "geometry_type": "point", "provenance": "simulated",
                "licence_note": "Synthetic series from the HACK-001 pack",
            })
    return datasets, records


def main():
    points = track()
    observations, media, outputs = observations_media_outputs(points)
    datasets, records = context()
    dump("mission.json", {
        "mission_id": "HACK-001", "name": "Danube high-flow reconnaissance",
        "scenario": "Post-high-flow reconnaissance, drift replay",
        "region": "Lower Danube demonstration zone (synthetic)", "node_id": "PN-MVP-001",
        "mode": "drift", "planned_start": iso(START), "planned_end": iso(START + timedelta(minutes=MINUTES)),
        "area_bbox": [25.90, 43.88, 25.98, 43.92], "event_id_start": 142,
        "data_origin": "simulated", "schema_version": "1.0",
    })
    dump("node.json", {"node_id": "PN-MVP-001", "node_type": "simulated",
                       "camera_ids": ["CAM-SURF", "CAM-UW"], "firmware_version": None,
                       "schema_version": "1.0"})
    dump("track.json", points)
    dump("observations.json", observations)
    dump("media.json", media)
    dump("inference/model_outputs.json", outputs)
    dump("device_status.json", device_status())
    dump("context/datasets.json", datasets)
    dump("context/records.json", records)
    (OUT / "PROVENANCE.md").write_text(
        "# HACK-001 pack provenance\n\n"
        "Generated by `scripts/build_demo_pack.py` from the P0-08 storyboard values.\n\n"
        "- Mission, node, track, device status: **simulated / replayed** (synthetic drift, 43 points).\n"
        "- Observations: **replayed**, media are **placeholders** until team footage is delivered.\n"
        "- Model outputs: **simulated inference** (hand-authored P0-08 scores, `inference_origin: simulated`).\n"
        "- Context: **simulated** NIMH/EAMEDR-style series, replaced by organiser data at G1.\n",
        encoding="utf-8")
    print(f"pack written to {OUT}: {len(points)} track points, {len(observations)} observations, "
          f"{len(outputs)} model outputs, {len(records)} context records")


if __name__ == "__main__":
    main()
