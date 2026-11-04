#!/usr/bin/env python3
"""Snapshot every Indian charging location in Open Charge Map.

Open Charge Map publishes its live registry as one JSON file per location in the public ``ocm-export``
repository, so no API key is needed. This script sparse-clones only the India folder and the reference data,
resolves the lookup ids (operator, connector type, status, usage) to their titles, and writes the fields the
dataset uses to ``datasets/sources/ocm_charging_stations.json``. Run it only to refresh the snapshot:

    python scripts/fetch_ocm_stations.py                         # clones into a temporary directory
    python scripts/fetch_ocm_stations.py --export-dir ocm-export # or reads an existing clone

Data from Open Charge Map contributors (https://openchargemap.org), licensed under CC BY 4.0. Every Indian
location in the export has Data Provider 1 (Open Charge Map contributors); the script fails if that changes, so a
differently licensed record never enters the dataset unnoticed.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

REPOSITORY = "https://github.com/openchargemap/ocm-export.git"
OCM_CONTRIBUTORS = 1


def clone(target: Path) -> Path:
    subprocess.run(["git", "clone", "--quiet", "--depth", "1", "--filter=blob:none", "--sparse", REPOSITORY,
                    str(target)], check=True)
    subprocess.run(["git", "-C", str(target), "sparse-checkout", "set", "--no-cone", "/data/IN/",
                    "/data/referencedata.json"], check=True)
    return target


def titles(reference: dict, key: str) -> dict[int, str]:
    return {row["ID"]: (row.get("Title") or "").strip() for row in reference[key]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--export-dir", type=Path, help="an existing clone of openchargemap/ocm-export")
    parser.add_argument("--out", type=Path, default=Path("datasets/sources/ocm_charging_stations.json"))
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as scratch:
        root = args.export_dir or clone(Path(scratch) / "ocm-export")
        commit = subprocess.run(["git", "-C", str(root), "log", "-1", "--format=%H %cI"], check=True,
                                capture_output=True, text=True).stdout.split()
        reference = json.loads((root / "data" / "referencedata.json").read_text(encoding="utf-8"))
        operators = titles(reference, "Operators")
        connection_types = titles(reference, "ConnectionTypes")
        current_types = titles(reference, "CurrentTypes")
        status_types = titles(reference, "StatusTypes")
        usage_types = titles(reference, "UsageTypes")

        locations = []
        for path in sorted((root / "data" / "IN").glob("*.json")):
            poi = json.loads(path.read_text(encoding="utf-8"))
            if poi.get("DataProviderID") != OCM_CONTRIBUTORS:
                raise SystemExit(f"{path.name}: data provider {poi.get('DataProviderID')} has its own licence")
            address = poi.get("AddressInfo") or {}
            locations.append({
                "ocmId": poi["ID"],
                "title": (address.get("Title") or "").strip(),
                "addressLine1": (address.get("AddressLine1") or "").strip(),
                "addressLine2": (address.get("AddressLine2") or "").strip(),
                "town": (address.get("Town") or "").strip(),
                "stateOrProvince": (address.get("StateOrProvince") or "").strip(),
                "postcode": (address.get("Postcode") or "").strip(),
                "latitude": round(address["Latitude"], 7),
                "longitude": round(address["Longitude"], 7),
                "operator": operators.get(poi.get("OperatorID"), ""),
                "usage": usage_types.get(poi.get("UsageTypeID"), ""),
                "status": status_types.get(poi.get("StatusTypeID"), ""),
                "numberOfPoints": poi.get("NumberOfPoints"),
                "dateLastVerified": poi.get("DateLastVerified"),
                "connections": [{
                    "type": connection_types.get(connection.get("ConnectionTypeID"), ""),
                    "current": current_types.get(connection.get("CurrentTypeID"), ""),
                    "powerKw": connection.get("PowerKW"),
                    "quantity": connection.get("Quantity"),
                    "status": status_types.get(connection.get("StatusTypeID"), ""),
                } for connection in poi.get("Connections") or []],
            })

    locations.sort(key=lambda row: row["ocmId"])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {
        "source": "Open Charge Map export, https://github.com/openchargemap/ocm-export (data/IN)",
        "license": "CC BY 4.0, Open Charge Map contributors, https://openchargemap.org",
        "exportCommit": commit[0],
        "exportDate": commit[1],
        "fetchedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "locations": locations,
    }
    args.out.write_text(json.dumps(snapshot, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {len(locations)} Open Charge Map locations to {args.out}")


if __name__ == "__main__":
    main()
