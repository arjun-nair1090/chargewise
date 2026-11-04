#!/usr/bin/env python3
"""Generate the ChargeWise demo dataset from real stations and real EV models.

Where each part comes from (every record carries a ``source`` field):

* Stations: real public charging stations from OpenStreetMap (names, operators, coordinates, street
  addresses), read from the committed snapshot ``datasets/sources/osm_charging_stations.json`` that
  ``fetch_osm_stations.py`` writes. Data (c) OpenStreetMap contributors, ODbL 1.0.
* Connectors: taken from the station's OSM socket and power tags where mapped; otherwise estimated from the
  hardware that operator typically installs (``source: "estimated"``).
* Vehicles: real EV models sold in India with their published battery and DC charging specs. Owners, number
  plates and state of charge are generated.
* Grid capacity, station/connector status and charging requests: generated from the seed. No public source
  publishes a station's sanctioned load or live bookings.

The output is deterministic for a given seed and snapshot, so the committed JSON can be regenerated
byte-for-byte:

    python scripts/generate_dataset.py --seed 20261102 --out datasets

Charging-request windows are stored as offsets (multiples of 15 minutes) from the next slot boundary
at load time, so the demo data never goes stale (architecture D2).
"""

from __future__ import annotations

import argparse
import json
import math
import random
import re
from pathlib import Path

SLOT_MINUTES = 15
MAX_WINDOW_SLOTS = 48
HORIZON_SLOTS = 96  # requests start within the next 24 hours

SNAPSHOT = Path(__file__).resolve().parent.parent / "datasets" / "sources" / "osm_charging_stations.json"

# Networks that serve only two-wheelers, and battery-swap points, are not car charging stations.
TWO_WHEELER_NETWORKS = {"ather energy", "bolt", "honda"}
MERGE_DISTANCE_M = 40  # OSM sometimes maps each charger of one site as its own node

# Display names for the brand/operator spellings found in the snapshot.
OPERATOR_NAMES = {
    "tata": "Tata Power",
    "tata power": "Tata Power",
    "go ego": "GoEgo",
    "goego": "GoEgo",
    "goego charging station": "GoEgo",
    "jio-bp": "Jio-bp pulse",
    "jio pulse": "Jio-bp pulse",
    "shell": "Shell Recharge",
    "statiq": "Statiq",
    "kazam": "Kazam",
    "bijlify": "Bijlify",
    "ev dock": "EV Dock",
    "ev dock charging station": "EV Dock",
    "powerup": "PowerUP",
    "fortum": "Fortum",
    "powergrid": "POWERGRID",
    "mahavitaran": "Mahavitaran (MSEDCL)",
    "zeal energy services": "Zeal Energy",
    "o s overseas": "O S Overseas",
}

# Typical hardware per operator, used only when OSM has no socket tags: (connector type, kW) cycled to the
# station's mapped capacity. Kazam's 3.3 kW AC units follow the Bharat AC-001 standard; the PowerGrid and
# Mahavitaran public sites use multi-standard DC units.
OPERATOR_PROFILES = {
    "Tata Power": [("CCS2", 30.0), ("TYPE2_AC", 7.4)],
    "GoEgo": [("CCS2", 30.0), ("TYPE2_AC", 7.4)],
    "Jio-bp pulse": [("CCS2", 60.0), ("TYPE2_AC", 22.0)],
    "Shell Recharge": [("CCS2", 60.0), ("TYPE2_AC", 22.0)],
    "Statiq": [("CCS2", 60.0), ("TYPE2_AC", 7.4)],
    "Kazam": [("BHARAT_AC001", 3.3), ("TYPE2_AC", 7.4)],
    "Bijlify": [("CCS2", 30.0), ("TYPE2_AC", 7.4)],
    "EV Dock": [("TYPE2_AC", 7.4)],
    "Fortum": [("CCS2", 50.0), ("TYPE2_AC", 22.0)],
    "POWERGRID": [("CCS2", 50.0), ("BHARAT_DC001", 15.0), ("BHARAT_AC001", 3.3)],
    "Mahavitaran (MSEDCL)": [("CCS2", 50.0), ("CHADEMO", 50.0), ("TYPE2_AC", 22.0)],
}
DEFAULT_PROFILE = [("CCS2", 30.0), ("TYPE2_AC", 7.4), ("BHARAT_DC001", 15.0)]

OSM_SOCKETS = {
    "type2_combo": "CCS2",
    "type2": "TYPE2_AC",
    "type2_cable": "TYPE2_AC",
    "chademo": "CHADEMO",
    "gb_dc": "BHARAT_DC001",
}
DC_TYPES = {"CCS2", "CHADEMO", "BHARAT_DC001"}

# Real EV models sold in India, with published battery capacity and peak DC charging power.
# (model, battery kWh, max charging kW, connector types, relative share in the demo fleet)
VEHICLE_MODELS = [
    ("Tata Nexon.ev 45", 46.08, 60.0, ["CCS2", "TYPE2_AC"], 14),
    ("Tata Punch.ev Long Range", 35.0, 50.0, ["CCS2", "TYPE2_AC"], 12),
    ("Tata Tiago.ev Long Range", 24.0, 25.0, ["CCS2", "TYPE2_AC"], 10),
    ("MG Windsor EV", 38.0, 45.0, ["CCS2", "TYPE2_AC"], 10),
    ("Tata Tigor EV", 26.0, 25.0, ["CCS2", "TYPE2_AC"], 5),
    ("MG ZS EV", 50.3, 76.0, ["CCS2", "TYPE2_AC"], 6),
    ("Mahindra XUV400 EL Pro", 39.4, 50.0, ["CCS2", "TYPE2_AC"], 6),
    ("Mahindra BE 6 (59 kWh)", 59.0, 140.0, ["CCS2", "TYPE2_AC"], 4),
    ("BYD Atto 3", 60.48, 80.0, ["CCS2", "TYPE2_AC"], 4),
    ("Hyundai Kona Electric", 39.2, 50.0, ["CCS2", "TYPE2_AC"], 3),
    ("Mahindra e-Verito (fleet)", 21.2, 15.0, ["BHARAT_DC001", "BHARAT_AC001"], 3),
    ("Nissan Leaf 40 kWh (import)", 40.0, 50.0, ["CHADEMO", "TYPE2_AC"], 1),
]

# Made-up owners. (name, home region)
DRIVERS = [
    ("Aarav Kulkarni", "Pune"), ("Priya Deshpande", "Pune"), ("Rohan Mehta", "Mumbai"),
    ("Sneha Patil", "Pune"), ("Vikram Iyer", "Mumbai"), ("Ananya Joshi", "Pune"),
    ("Kabir Shah", "Mumbai"), ("Meera Gokhale", "Pune"), ("Arjun Nair", "Pune"),
    ("Ishita Banerjee", "Mumbai"), ("Siddharth Rao", "Pune"), ("Neha Kapoor", "Mumbai"),
    ("Omkar Jadhav", "Pune"), ("Tanvi Bhosale", "Pune"), ("Farhan Qureshi", "Mumbai"),
    ("Kavya Menon", "Pune"), ("Yash Thakur", "Mumbai"), ("Riya Chavan", "Pune"),
    ("Aditya Sawant", "Pune"), ("Zoya Sheikh", "Mumbai"),
]
VEHICLES_PER_DRIVER = 2
REQUEST_COUNT = 120

# Maharashtra RTO codes by home region (Mumbai and suburbs/Thane/Navi Mumbai; Pune/Pimpri-Chinchwad).
RTO_CODES = {"Mumbai": ["01", "02", "03", "04", "05", "43", "46", "47"], "Pune": ["12", "14"]}

MUMBAI_REGION_MIN_LAT = 18.8  # every snapshot site north of this is in the Mumbai Metropolitan Region


# ---- stations ----

def metres_between(a: dict, b: dict) -> float:
    lat = math.radians((a["latitude"] + b["latitude"]) / 2)
    dy = (a["latitude"] - b["latitude"]) * 111_320
    dx = (a["longitude"] - b["longitude"]) * 111_320 * math.cos(lat)
    return math.hypot(dx, dy)


def operator_of(tags: dict) -> str | None:
    for key in ("brand", "operator"):
        value = tags.get(key)
        if value:
            lookup = value.strip().lower()
            # Mahavitaran's DC units are tagged with the charger maker (Delta) as brand.
            if lookup == "delta" and tags.get("operator"):
                continue
            return OPERATOR_NAMES.get(lookup, value.strip())
    return None


def is_car_station(tags: dict) -> bool:
    if tags.get("access") == "private" or tags.get("scooter") == "yes":
        return False
    if tags.get("charging_station:battery_swapping") == "yes":
        return False
    names = {tags.get(k, "").strip().lower() for k in ("brand", "operator", "name")}
    return not names & TWO_WHEELER_NETWORKS


def region_of(row: dict) -> str:
    return "Mumbai" if row["latitude"] >= MUMBAI_REGION_MIN_LAT else "Pune"


METRO_CITIES = {"Mumbai", "Thane", "Navi Mumbai", "Panvel", "Pune", "Pimpri-Chinchwad"}
COUNTY_CITIES = {"Pune City Subdistrict": "Pune", "Thane Subdistrict": "Thane", "Panvel Subdistrict": "Panvel"}


def locality_of(address: dict) -> str:
    suburb = address.get("suburb", "")
    if suburb and not re.search(r"\bWard$", suburb):
        return suburb
    for key in ("neighbourhood", "village", "town", "road"):
        if address.get(key):
            return address[key]
    return address.get("county", "Maharashtra").removesuffix(" Subdistrict")


def city_of(address: dict, region: str) -> str:
    """The metro city a site belongs to (Nominatim reports some Pune suburbs as separate towns)."""
    if address.get("city") in METRO_CITIES:
        return address["city"]
    if address.get("city_district", "").startswith("Mumbai"):
        return "Mumbai"
    return COUNTY_CITIES.get(address.get("county", ""), region)


def address_of(address: dict, region: str) -> str:
    # Nominatim's interpolated postcodes are often wrong for these points, so they are left out. Villages
    # and towns on the city fringe (Mhalunge, Tathawade) stay in, before the metro city.
    fringe = address.get("village") or address.get("town")
    if address.get("county") == "Pune City Subdistrict":
        fringe = None  # Nominatim's "town" inside Pune city limits is a stale boundary, not a place name
    parts = [address.get("road"), locality_of(address), fringe, city_of(address, region), "Maharashtra"]
    seen: list[str] = []
    for part in parts:
        if part and part not in seen:
            seen.append(part)
    return ", ".join(seen)


def name_of(tags: dict, operator: str | None, address: dict) -> str:
    locality = locality_of(address)
    name = tags.get("name:en") or tags.get("name")
    if tags.get("branch"):
        return f"{operator or name}, {tags['branch']}"
    if name and operator and name.strip().lower() not in OPERATOR_NAMES and name.strip() != operator:
        return f"{name.strip()}, {locality}"
    if operator:
        return f"{operator}, {locality}"
    return f"EV charging point, {locality}"


def load_snapshot() -> list[dict]:
    snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    rows = [row for row in snapshot["stations"] if is_car_station(row["tags"])]
    merged: list[dict] = []
    for row in rows:
        twin = next((m for m in merged if metres_between(m, row) <= MERGE_DISTANCE_M
                     and operator_of(m["tags"]) == operator_of(row["tags"])), None)
        if twin is None:
            merged.append({**row, "members": [row]})
        else:
            twin["members"].append(row)
    # Order by region, then north to south, so the ids read naturally on the map.
    merged.sort(key=lambda r: (region_of(r) != "Mumbai", -r["latitude"], r["longitude"]))
    return merged


def parse_kw(text: str | None) -> float | None:
    if not text:
        return None
    match = re.search(r"(\d+(?:\.\d+)?)\s*kw", text.lower())
    return float(match.group(1)) if match else None


def socket_connectors(site: dict) -> list[tuple[str, float | None]]:
    """Connectors spelled out by socket:* tags across every node merged into this site."""
    result: list[tuple[str, float | None]] = []
    for member in site["members"]:
        tags = member["tags"]
        for socket, connector_type in OSM_SOCKETS.items():
            count = tags.get(f"socket:{socket}")
            if count and count.isdigit():
                result.extend([(connector_type, parse_kw(tags.get(f"socket:{socket}:output")))] * int(count))
    return result


def site_connectors(site: dict, operator: str | None) -> list[tuple[str, float, str]]:
    """(connector type, kW, source) for one site."""
    tags = site["tags"]
    profile = OPERATOR_PROFILES.get(operator or "", DEFAULT_PROFILE)
    profile_kw = dict((t, kw) for t, kw in reversed(profile))

    mapped = socket_connectors(site)
    if mapped:
        return [(t, kw if kw else profile_kw.get(t, 7.4 if t == "TYPE2_AC" else 30.0),
                 "openstreetmap" if kw else "openstreetmap (power estimated)") for t, kw in mapped]

    capacity = tags.get("capacity", "").strip().lower()
    power = parse_kw(tags.get("note")) or parse_kw(tags.get("description")) or parse_kw(capacity)
    count = None
    if capacity.isdigit():
        # A bare number above ten is a power rating mistyped as capacity (e.g. Statiq "60").
        count, power = (int(capacity), power) if int(capacity) <= 10 else (None, power or float(capacity))
    count_match = re.search(r"space for (\d+)", tags.get("description", "").lower())
    if count_match:
        count = int(count_match.group(1))
    count = count or len(profile)

    if power is not None:
        # A mapped DC rating: every bay is a CCS2 gun sharing that rating, as at most single-charger sites.
        return [("CCS2", power, "openstreetmap")] * count
    return [(profile[i % len(profile)][0], profile[i % len(profile)][1], "estimated") for i in range(count)]


def stations_and_connectors(rng: random.Random) -> tuple[list[dict], list[dict]]:
    sites = load_snapshot()
    station_rows: list[dict] = []
    connector_rows: list[dict] = []
    site_roads: dict[str, str | None] = {}
    maintenance_index = rng.randrange(len(sites))
    for index, site in enumerate(sites, start=1):
        operator = operator_of(site["tags"])
        region = region_of(site)
        station_id = f"S-{index:02d}"
        plugs = site_connectors(site, operator)
        for connector_type, kw, source in plugs:
            connector_rows.append({
                "id": f"C-{len(connector_rows) + 1:03d}",
                "stationId": station_id,
                "connectorType": connector_type,
                "maxPowerKw": kw,
                "status": "OUT_OF_SERVICE" if rng.random() < 0.06 else "OPERATIONAL",
                "source": source,
            })
        # Sanctioned grid load is not public. Sites share a supply smaller than every charger at full power
        # (60-100 % of the nameplate total), which is what makes power allocation matter.
        nameplate = sum(kw for _, kw, _ in plugs)
        grid = max(10.0, round(nameplate * rng.uniform(0.6, 1.0) / 5) * 5)
        station_rows.append({
            "id": station_id,
            "name": name_of(site["tags"], operator, site["address"]),
            "address": address_of(site["address"], region),
            "latitude": site["latitude"],
            "longitude": site["longitude"],
            "gridCapacityKw": float(grid),
            # One site under maintenance shows the STATION_NOT_OPERATIONAL path in the demo.
            "status": "MAINTENANCE" if index - 1 == maintenance_index else "OPERATIONAL",
            "region": region,
            "source": f"openstreetmap:{site['osmType']}/{site['osmId']}",
        })
        site_roads[station_id] = site["address"].get("road")
    # Two sites of one operator in one locality get their road added, so every station name is unique.
    names = [s["name"] for s in station_rows]
    for station in station_rows:
        if names.count(station["name"]) > 1 and site_roads[station["id"]]:
            station["name"] = f"{station['name']} ({site_roads[station['id']]})"
    return station_rows, connector_rows


# ---- vehicles and requests ----

def registration(rng: random.Random, region: str, used: set[str]) -> str:
    while True:
        letters = "".join(rng.choice("ABCDEFGHJKLMNPRSTUVWXYZ") for _ in range(2))
        plate = f"MH{rng.choice(RTO_CODES[region])}{letters}{rng.randint(1000, 9999)}"
        if plate not in used:
            used.add(plate)
            return plate


def vehicles(rng: random.Random) -> list[dict]:
    used: set[str] = set()
    weights = [model[4] for model in VEHICLE_MODELS]
    result = []
    for number, (owner, region) in enumerate(DRIVERS, start=1):
        for _ in range(VEHICLES_PER_DRIVER):
            model, battery, max_kw, plug_types, _ = rng.choices(VEHICLE_MODELS, weights=weights)[0]
            result.append({
                "registrationNumber": registration(rng, region, used),
                "ownerEmail": f"driver{number:02d}@demo.chargewise.test",
                "ownerName": owner,
                "homeRegion": region,
                "model": model,
                "batteryCapacityKwh": battery,
                "currentSoc": round(rng.uniform(8.0, 70.0), 1),
                "maxChargingKw": max_kw,
                "connectorTypes": plug_types,
                "source": "manufacturer specs",
            })
    return result


def overlaps(windows: list[tuple[int, int]], start: int, end: int) -> bool:
    return any(start < other_end and other_start < end for other_start, other_end in windows)


def charging_requests(rng: random.Random, vehicle_rows: list[dict], station_rows: list[dict],
                      connector_rows: list[dict]) -> list[dict]:
    plugs: dict[str, list[dict]] = {}
    for connector in connector_rows:
        plugs.setdefault(connector["stationId"], []).append(connector)
    windows: dict[str, list[tuple[int, int]]] = {v["registrationNumber"]: [] for v in vehicle_rows}
    result = []
    while len(result) < REQUEST_COUNT:
        vehicle = rng.choice(vehicle_rows)
        local = [s for s in station_rows if s["region"] == vehicle["homeRegion"]]
        compatible = [s for s in local if any(p["connectorType"] in vehicle["connectorTypes"]
                                               for p in plugs[s["id"]])]
        # Most drivers pick a station they can plug into; a few do not check, which the matcher must reject.
        station = rng.choice(compatible if compatible and rng.random() < 0.92 else local)
        usable = [p for p in plugs[station["id"]] if p["connectorType"] in vehicle["connectorTypes"]]
        fast = any(p["connectorType"] in DC_TYPES for p in usable)
        best_kw = max((p["maxPowerKw"] for p in usable), default=vehicle["maxChargingKw"])
        # Public DC top-ups last 30-90 minutes; AC sessions run 1.5-4 hours while the car is parked.
        slots = rng.randint(2, 6) if fast else rng.randint(6, 16)
        start_slot = rng.randrange(1, HORIZON_SLOTS)
        if slots > MAX_WINDOW_SLOTS or overlaps(windows[vehicle["registrationNumber"]], start_slot, start_slot + slots):
            continue
        hours = slots * SLOT_MINUTES / 60
        headroom = vehicle["batteryCapacityKwh"] * (100 - vehicle["currentSoc"]) / 100
        # Charging tapers, so a session averages well under the peak rate.
        deliverable = min(vehicle["maxChargingKw"], best_kw) * hours * 0.85
        ceiling = min(headroom, deliverable)
        if ceiling < 4.0:
            continue
        # Half-kWh steps rounded down keep every request within headroom and deliverable energy.
        energy = int(rng.uniform(4.0, ceiling) * 2) / 2
        windows[vehicle["registrationNumber"]].append((start_slot, start_slot + slots))
        result.append({
            "id": f"R-{len(result) + 1:04d}",
            "registrationNumber": vehicle["registrationNumber"],
            "stationId": station["id"],
            "requestedEnergyKwh": energy,
            "startOffsetMinutes": start_slot * SLOT_MINUTES,
            "durationMinutes": slots * SLOT_MINUTES,
            "source": "generated",
        })
    return result


def write_json(path: Path, rows: list[dict]) -> None:
    path.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--seed", type=int, default=20261102)
    parser.add_argument("--out", type=Path, default=Path("datasets"))
    args = parser.parse_args()

    rng = random.Random(args.seed)
    station_rows, connector_rows = stations_and_connectors(rng)
    vehicle_rows = vehicles(rng)
    request_rows = charging_requests(rng, vehicle_rows, station_rows, connector_rows)

    args.out.mkdir(parents=True, exist_ok=True)
    write_json(args.out / "stations.json", station_rows)
    write_json(args.out / "connectors.json", connector_rows)
    write_json(args.out / "vehicles.json", vehicle_rows)
    write_json(args.out / "charging_requests.json", request_rows)
    print(f"seed {args.seed}: {len(station_rows)} stations, {len(connector_rows)} connectors, "
          f"{len(vehicle_rows)} vehicles, {len(request_rows)} charging requests -> {args.out}")


if __name__ == "__main__":
    main()
