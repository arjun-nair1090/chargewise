#!/usr/bin/env python3
"""Fetch every EV charging station in India mapped on OpenStreetMap.

Writes a snapshot to ``datasets/sources/osm_charging_stations.json`` that ``generate_dataset.py`` reads, so the
demo dataset can be regenerated offline and byte-for-byte from the committed snapshot. Run this only to refresh
the snapshot:

    python scripts/fetch_osm_stations.py

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0):
https://www.openstreetmap.org/copyright. Station locations and tags come from the Overpass API. Street addresses
come from the station's own addr:* tags; stations already in the previous snapshot keep the address Nominatim
reverse geocoding gave them. City and state are derived offline from GeoNames (see ``fetch_geonames_places.py``),
so a refresh makes no bulk geocoding requests.
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
AREA = 'area["ISO3166-1"="IN"][admin_level=2]'


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
    query = f'[out:json][timeout:300];{AREA}->.india;nwr["amenity"="charging_station"](area.india);out center tags;'
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


def tag_address(tags: dict) -> dict:
    """The station's own addr:* tags, in the Nominatim field names generate_dataset.py reads."""
    fields = {"addr:street": "road", "addr:suburb": "suburb", "addr:city": "city", "addr:state": "state",
              "addr:postcode": "postcode"}
    address = {name: tags[key].strip() for key, name in fields.items() if tags.get(key, "").strip()}
    if tags.get("addr:housenumber") and "road" in address:
        address["road"] = f"{tags['addr:housenumber'].strip()}, {address['road']}"
    return address


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("datasets/sources/osm_charging_stations.json"))
    args = parser.parse_args()

    previous: dict[tuple[str, int], dict] = {}
    if args.out.exists():
        for row in json.loads(args.out.read_text(encoding="utf-8"))["stations"]:
            if row.get("addressSource") != "tags":
                previous[(row["osmType"], row["osmId"])] = row["address"]
    stations, osm_timestamp = overpass_stations()
    print(f"Overpass: {len(stations)} charging stations (OSM data as of {osm_timestamp})")
    for station in stations:
        known = previous.get((station["osmType"], station["osmId"]))
        station["address"] = known if known is not None else tag_address(station["tags"])
        station["addressSource"] = "nominatim" if known is not None else "tags"

    args.out.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {
        "source": "OpenStreetMap via Overpass API; addresses from addr:* tags or Nominatim reverse geocoding",
        "license": "ODbL 1.0, (c) OpenStreetMap contributors, https://www.openstreetmap.org/copyright",
        "osmTimestamp": osm_timestamp,
        "fetchedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "area": AREA,
        "stations": stations,
    }
    args.out.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
