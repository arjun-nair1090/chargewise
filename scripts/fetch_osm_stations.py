#!/usr/bin/env python3
"""Fetch real EV charging stations in the Mumbai Metropolitan Region and Pune from OpenStreetMap.

Writes a snapshot to ``datasets/sources/osm_charging_stations.json`` that ``generate_dataset.py`` reads, so the
demo dataset can be regenerated offline and byte-for-byte from the committed snapshot. Run this only to refresh
the snapshot:

    python scripts/fetch_osm_stations.py

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0):
https://www.openstreetmap.org/copyright. Station locations and tags come from the Overpass API; street addresses
come from reverse geocoding with Nominatim, throttled to one request per second as its usage policy requires.
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

USER_AGENT = "ChargeWise-dataset-builder/1.0 (university algorithms project)"
OVERPASS_URLS = ["https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter"]
NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse"

# (south, west, north, east)
REGIONS = {
    "Mumbai Metropolitan Region": (18.85, 72.75, 19.50, 73.30),
    "Pune": (18.42, 73.68, 18.70, 74.02),
}


def http_get_json(url: str, data: bytes | None = None, attempts: int = 3) -> dict:
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            # Overpass answers 429/502/504 when busy; anything else is a real failure.
            if error.code not in (429, 502, 503, 504) or attempt == attempts:
                raise
            time.sleep(10 * attempt)
    raise AssertionError("unreachable")


def overpass_stations() -> tuple[list[dict], str]:
    boxes = "".join(f'nwr["amenity"="charging_station"]({s},{w},{n},{e});' for s, w, n, e in REGIONS.values())
    query = f"[out:json][timeout:120];({boxes});out center tags;"
    body = urllib.parse.urlencode({"data": query}).encode("utf-8")
    last_error: Exception | None = None
    for url in OVERPASS_URLS:
        try:
            payload = http_get_json(url, body)
            break
        except urllib.error.URLError as error:
            last_error = error
    else:
        raise RuntimeError(f"every Overpass endpoint failed: {last_error}")
    rows = []
    for element in payload["elements"]:
        centre = element if "lat" in element else element.get("center")
        if not centre:
            continue
        rows.append({
            "osmType": element["type"],
            "osmId": element["id"],
            "latitude": round(centre["lat"], 7),
            "longitude": round(centre["lon"], 7),
            "tags": element.get("tags", {}),
        })
    rows.sort(key=lambda row: (row["osmType"], row["osmId"]))
    return rows, payload.get("osm3s", {}).get("timestamp_osm_base", "")


def reverse_geocode(lat: float, lon: float) -> dict:
    query = urllib.parse.urlencode({"format": "jsonv2", "lat": lat, "lon": lon, "zoom": 18, "addressdetails": 1,
                                    "accept-language": "en"})
    result = http_get_json(f"{NOMINATIM_URL}?{query}")
    return result.get("address", {})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("datasets/sources/osm_charging_stations.json"))
    args = parser.parse_args()

    stations, osm_timestamp = overpass_stations()
    print(f"Overpass: {len(stations)} charging stations (OSM data as of {osm_timestamp})")
    for index, station in enumerate(stations, start=1):
        station["address"] = reverse_geocode(station["latitude"], station["longitude"])
        print(f"  geocoded {index}/{len(stations)} {station['osmType']}/{station['osmId']}")
        time.sleep(1.1)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {
        "source": "OpenStreetMap via Overpass API; addresses via Nominatim reverse geocoding",
        "license": "ODbL 1.0, (c) OpenStreetMap contributors, https://www.openstreetmap.org/copyright",
        "osmTimestamp": osm_timestamp,
        "fetchedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "regions": REGIONS,
        "stations": stations,
    }
    args.out.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
