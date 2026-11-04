#!/usr/bin/env python3
"""Fetch the Indian towns and cities GeoNames lists, for placing each charging station in a city and state.

Writes ``datasets/sources/geonames_in_places.json``, which ``generate_dataset.py`` reads to give every station a
city and state offline (one nearest-place lookup per station, no geocoding service). Run this only to refresh it:

    python scripts/fetch_geonames_places.py

Data from GeoNames (https://www.geonames.org), licensed under Creative Commons Attribution 4.0. ``cities5000`` lists
every populated place with at least 5,000 people; ``admin1CodesASCII`` names the states and union territories.
"""

from __future__ import annotations

import argparse
import io
import json
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

USER_AGENT = "ChargeWise-dataset-builder/1.0 (university algorithms project)"
CITIES_URL = "https://download.geonames.org/export/dump/cities5000.zip"
ADMIN1_URL = "https://download.geonames.org/export/dump/admin1CodesASCII.txt"

# GeoNames still names a few states by older or shortened forms.
STATE_NAMES = {
    "Andaman and Nicobar": "Andaman and Nicobar Islands",
    "Delhi": "Delhi",
    "National Capital Territory of Delhi": "Delhi",
    "Odisha": "Odisha",
    "Orissa": "Odisha",
    "Pondicherry": "Puducherry",
    "Puducherry": "Puducherry",
    "Union Territory of Puducherry": "Puducherry",
    "Uttaranchal": "Uttarakhand",
    "Jammu and Kashmir": "Jammu and Kashmir",
    "Dadra and Nagar Haveli and Daman and Diu": "Dadra and Nagar Haveli and Daman and Diu",
}


def download(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("datasets/sources/geonames_in_places.json"))
    args = parser.parse_args()

    states: dict[str, str] = {}
    for line in download(ADMIN1_URL).decode("utf-8").splitlines():
        code, name, *_ = line.split("\t")
        if code.startswith("IN."):
            states[code.removeprefix("IN.")] = STATE_NAMES.get(name, name)

    with zipfile.ZipFile(io.BytesIO(download(CITIES_URL))) as archive:
        text = archive.read("cities5000.txt").decode("utf-8")
    places = []
    for line in text.splitlines():
        field = line.split("\t")
        # geonameid, name, asciiname, alternatenames, lat, lon, class, code, country, cc2, admin1, ..., population
        # Sections and localities of a city (PPLX, PPLL, PPLQ, PPLR, PPLF) are not towns in their own right.
        if field[8] != "IN" or field[10] not in states or field[7] in ("PPLX", "PPLL", "PPLQ", "PPLR", "PPLF"):
            continue
        places.append({
            "id": int(field[0]),
            "name": field[2] or field[1],
            "latitude": round(float(field[4]), 5),
            "longitude": round(float(field[5]), 5),
            "population": int(field[14] or 0),
            "state": states[field[10]],
        })
    places.sort(key=lambda place: place["id"])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {
        "source": "GeoNames cities5000 and admin1CodesASCII, https://download.geonames.org/export/dump/",
        "license": "CC BY 4.0, GeoNames, https://www.geonames.org",
        "fetchedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "places": places,
    }
    args.out.write_text(json.dumps(snapshot, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {len(places)} places in {len(set(states.values()))} states to {args.out}")


if __name__ == "__main__":
    main()
