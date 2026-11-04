#!/usr/bin/env python3
"""Generate the ChargeWise demo dataset from real stations and real EV models.

Where each part comes from (every record carries a ``source`` field):

* Stations: every public car charging station in India listed by Open Charge Map or mapped on OpenStreetMap,
  read from the committed snapshots in ``datasets/sources`` (``fetch_ocm_stations.py``,
  ``fetch_osm_stations.py``). Open Charge Map data (c) Open Charge Map contributors, CC BY 4.0; OpenStreetMap
  data (c) OpenStreetMap contributors, ODbL 1.0. Where both list a site, the Open Charge Map record is kept,
  because it carries connector detail and live status.
* City and state: the station's nearest town and the largest city whose reach covers it, from GeoNames
  (``fetch_geonames_places.py``, CC BY 4.0); the state a record states itself wins where it can be read.
* Connectors: the listed connector types, power ratings and statuses where the source has them; otherwise
  estimated from the hardware the operator typically installs (``source`` says which).
* Vehicles: real EV models sold in India with their published battery and DC charging specs. Owners, number
  plates and state of charge are generated.
* Grid capacity and charging requests: generated from the seed. No public source publishes a station's
  sanctioned load or live bookings.

The output is deterministic for a given seed and snapshots, so the committed JSON can be regenerated
byte-for-byte:

    python scripts/generate_dataset.py --seed 20261102 --out datasets

Charging-request windows are stored as offsets (multiples of 15 minutes) from the next slot boundary
at load time, so the demo data never goes stale (architecture D2).
"""

from __future__ import annotations

import argparse
import difflib
import json
import math
import random
import re
from pathlib import Path

SLOT_MINUTES = 15
MAX_WINDOW_SLOTS = 48
HORIZON_SLOTS = 96  # requests start within the next 24 hours

SOURCES = Path(__file__).resolve().parent.parent / "datasets" / "sources"
OSM_SNAPSHOT = SOURCES / "osm_charging_stations.json"
OCM_SNAPSHOT = SOURCES / "ocm_charging_stations.json"
PLACES_SNAPSHOT = SOURCES / "geonames_in_places.json"

# Two- and three-wheeler networks and battery-swap points are not car charging stations.
TWO_WHEELER = re.compile(
    r"\b(ather|ola|honda|e:swap|yuma|vida|bolt|bounce|yulu|sun mobility|battery smart|hero electric|okinawa|"
    r"revolt|tvs)\b|battery[ -]?swap", re.IGNORECASE)
OSM_MERGE_M = 40  # OSM sometimes maps each charger of one site as its own node
SAME_SITE_M = 75  # an OSM site this close to an Open Charge Map site is the same site
OCM_DUPLICATE_M = 20
MAX_PER_CONNECTION = 12  # a listed quantity above this is a site-wide total typed into one row

# Display names for the brand/operator spellings found in the snapshots.
OPERATOR_NAMES = {
    "tata": "Tata Power",
    "tata power": "Tata Power",
    "tata power ez charge": "Tata Power",
    "go ego": "GoEgo",
    "goego": "GoEgo",
    "goego charging station": "GoEgo",
    "go ec": "GO EC",
    "jio-bp": "Jio-bp pulse",
    "jio pulse": "Jio-bp pulse",
    "jio bp pulse": "Jio-bp pulse",
    "shell": "Shell Recharge",
    "shell recharge": "Shell Recharge",
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
    "chargemod": "ChargeMOD",
    "chargezone": "ChargeZone",
    "zeon": "Zeon Charging",
    "zeon network": "Zeon Charging",
    "zeon charging": "Zeon Charging",
    "kseb": "KSEB",
    "kerala state electricity board": "KSEB",
    "kseb charging station": "KSEB",
    "eesl": "EESL",
    "energy efficiency services limited (eesl)": "EESL",
    "relux electric": "Relux Electric",
    "edrive bpcl": "BPCL eDrive",
    "charge_in by mahindra": "Charge_iN",
    "adani gas-ev": "Adani TotalEnergies",
}
NO_OPERATOR = {"(unknown operator)", "(business owner at location)", "unknown", ""}

# Typical hardware per operator, used only when the source has no connector detail: (connector type, kW)
# cycled to the station's mapped capacity. Kazam's 3.3 kW AC units follow the Bharat AC-001 standard; the
# PowerGrid and Mahavitaran public sites use multi-standard DC units.
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
    "KSEB": [("CCS2", 60.0), ("CHADEMO", 50.0), ("TYPE2_AC", 22.0)],
    "EESL": [("CCS2", 50.0), ("CHADEMO", 50.0), ("TYPE2_AC", 22.0)],
}
DEFAULT_PROFILE = [("CCS2", 30.0), ("TYPE2_AC", 7.4), ("BHARAT_DC001", 15.0)]
DEFAULT_KW = {"CCS2": 30.0, "TYPE2_AC": 7.4, "CHADEMO": 50.0, "BHARAT_DC001": 15.0, "BHARAT_AC001": 3.3}
MAX_AC_KW = {"TYPE2_AC": 22.0, "BHARAT_AC001": 7.4}
MIN_CAR_KW = 3.0

OSM_SOCKETS = {
    "type2_combo": "CCS2",
    "type2": "TYPE2_AC",
    "type2_cable": "TYPE2_AC",
    "chademo": "CHADEMO",
    "gb_dc": "BHARAT_DC001",
}
# Bharat DC-001 uses the GB/T connector; Bharat AC-001 uses the IEC 60309 industrial socket.
OCM_CONNECTORS = {
    "CCS (Type 2)": "CCS2",
    "Type 2 (Tethered Connector)": "TYPE2_AC",
    "Type 2 (Socket Only)": "TYPE2_AC",
    "CHAdeMO": "CHADEMO",
    "GB-T DC - GB/T 20234.3": "BHARAT_DC001",
    "CEE 3 Pin": "BHARAT_AC001",
    "IEC 60309 3-pin": "BHARAT_AC001",
    "IEC 60309 5-pin": "BHARAT_AC001",
    "Blue Commando (2P+E)": "BHARAT_AC001",
}
DC_TYPES = {"CCS2", "CHADEMO", "BHARAT_DC001"}

OCM_STATION_STATUS = {"Not Operational": "OFFLINE", "Temporarily Unavailable": "MAINTENANCE"}
OCM_SKIP_STATUS = {"Planned For Future Date", "Removed (Decommissioned)", "Removed (Duplicate Listing)"}
OCM_SKIP_USAGE = {"Private - Restricted Access", "Privately Owned - Notice Required"}
GENERIC_TITLES = re.compile(r"^(ev|electric vehicle|e-?vehicle)?\s*(charging|charger|charge)\s*(station|point|hub)?s?$",
                            re.IGNORECASE)

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

# Made-up owners: given names from across India, family names by state (a pan-Indian pool elsewhere).
GIVEN_NAMES = [
    "Aarav", "Priya", "Rohan", "Sneha", "Vikram", "Ananya", "Kabir", "Meera", "Arjun", "Ishita", "Siddharth",
    "Neha", "Omkar", "Tanvi", "Farhan", "Kavya", "Yash", "Riya", "Aditya", "Zoya", "Rahul", "Divya", "Karthik",
    "Lakshmi", "Nikhil", "Pooja", "Suresh", "Anjali", "Imran", "Fatima", "Harpreet", "Gurleen", "Manoj",
    "Shreya", "Varun", "Aisha", "Deepak", "Nandini", "Abhishek", "Sana", "Ravi", "Swati", "Tushar", "Bhavna",
    "Joseph", "Mariam", "Anirudh", "Keerthi", "Sourav", "Ritika",
]
FAMILY_NAMES = {
    "Maharashtra": ["Kulkarni", "Deshpande", "Patil", "Joshi", "Gokhale", "Jadhav", "Bhosale", "Chavan", "Sawant"],
    "Karnataka": ["Rao", "Gowda", "Hegde", "Shetty", "Kamath", "Bhat"],
    "Kerala": ["Nair", "Menon", "Pillai", "Kurian", "Thomas", "Varghese"],
    "Tamil Nadu": ["Iyer", "Subramanian", "Krishnan", "Raman", "Murugan", "Natarajan"],
    "Telangana": ["Reddy", "Rao", "Goud", "Naidu"],
    "Andhra Pradesh": ["Reddy", "Naidu", "Chowdary", "Varma"],
    "Delhi": ["Kapoor", "Malhotra", "Khanna", "Sharma", "Gupta", "Arora"],
    "Haryana": ["Yadav", "Malik", "Sangwan", "Chaudhary"],
    "Uttar Pradesh": ["Srivastava", "Mishra", "Tripathi", "Verma", "Khan"],
    "Gujarat": ["Shah", "Patel", "Mehta", "Desai", "Parikh"],
    "Rajasthan": ["Rathore", "Agarwal", "Shekhawat", "Meena"],
    "Odisha": ["Mohanty", "Das", "Patnaik", "Mishra", "Sahoo"],
    "West Bengal": ["Banerjee", "Chatterjee", "Mukherjee", "Bose", "Sen"],
    "Punjab": ["Singh", "Gill", "Sandhu", "Dhillon"],
}
PAN_INDIAN_FAMILY_NAMES = ["Sharma", "Singh", "Kumar", "Gupta", "Iyer", "Qureshi", "Sheikh", "Fernandes", "Das"]
DRIVER_COUNT = 100
REQUEST_COUNT = 600
MIN_CITY_STATIONS = 8  # drivers live in cities with at least this many stations

# GeoNames gives a few taluks and city sections a larger population than the city they belong to.
CITY_NAMES = {"Kanayannur": "Kochi", "Rasapudipalem": "Visakhapatnam", "Edattala": "Aluva"}
PLUS_CODE = re.compile(r"\b[23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,3}\b,?\s*", re.IGNORECASE)

# Vehicle registration prefixes by state and the RTO district codes in use there (approximate ranges).
STATE_PLATES = {
    "Andhra Pradesh": ("AP", 40), "Assam": ("AS", 34), "Bihar": ("BR", 50), "Chandigarh": ("CH", 4),
    "Chhattisgarh": ("CG", 30), "Delhi": ("DL", 13), "Goa": ("GA", 12), "Gujarat": ("GJ", 39),
    "Haryana": ("HR", 99), "Himachal Pradesh": ("HP", 90), "Jammu and Kashmir": ("JK", 22), "Jharkhand": ("JH", 24),
    "Karnataka": ("KA", 70), "Kerala": ("KL", 86), "Madhya Pradesh": ("MP", 70), "Maharashtra": ("MH", 50),
    "Odisha": ("OD", 35), "Puducherry": ("PY", 5), "Punjab": ("PB", 91), "Rajasthan": ("RJ", 58),
    "Tamil Nadu": ("TN", 99), "Telangana": ("TS", 38), "Uttar Pradesh": ("UP", 96), "Uttarakhand": ("UK", 20),
    "West Bengal": ("WB", 98),
}


# ---- geometry and places ----

def metres_between(a: dict, b: dict) -> float:
    lat = math.radians((a["latitude"] + b["latitude"]) / 2)
    dy = (a["latitude"] - b["latitude"]) * 111_320
    dx = (a["longitude"] - b["longitude"]) * 111_320 * math.cos(lat)
    return math.hypot(dx, dy)


class Grid:
    """Points bucketed in 0.25-degree cells, for nearest-neighbour lookups within a few tens of kilometres."""

    CELL = 0.25

    def __init__(self, rows: list[dict] = ()):
        self.cells: dict[tuple[int, int], list[dict]] = {}
        for row in rows:
            self.add(row)

    def key(self, row: dict) -> tuple[int, int]:
        return int(math.floor(row["latitude"] / self.CELL)), int(math.floor(row["longitude"] / self.CELL))

    def add(self, row: dict) -> None:
        self.cells.setdefault(self.key(row), []).append(row)

    def within(self, point: dict, metres: float) -> list[tuple[float, dict]]:
        cos_lat = max(0.3, math.cos(math.radians(point["latitude"])))
        reach = int(math.ceil(metres / 111_320 / self.CELL / cos_lat))
        row, col = self.key(point)
        found = []
        for r in range(row - reach, row + reach + 1):
            for c in range(col - reach, col + reach + 1):
                for candidate in self.cells.get((r, c), []):
                    distance = metres_between(point, candidate)
                    if distance <= metres:
                        found.append((distance, candidate))
        found.sort(key=lambda pair: (pair[0], pair[1].get("id", 0)))
        return found


class Places:
    """City and state for a coordinate, from GeoNames towns and cities."""

    def __init__(self):
        rows = json.loads(PLACES_SNAPSHOT.read_text(encoding="utf-8"))["places"]
        self.grid = Grid(rows)
        self.states = sorted({row["state"] for row in rows})
        self.aliases = {self.squash(state): state for state in self.states}
        self.aliases.update({self.squash(code): state for state, (code, _) in STATE_PLATES.items()})
        self.aliases.update({"tg": "Telangana", "or": "Odisha", "orissa": "Odisha", "ncr": "Delhi",
                             "newdelhi": "Delhi", "bangaloreurban": "Karnataka", "bengaluruurban": "Karnataka",
                             "chennai": "Tamil Nadu", "mumbai": "Maharashtra", "pune": "Maharashtra",
                             "villupuram": "Tamil Nadu", "pondicherry": "Puducherry"})

    @staticmethod
    def squash(text: str) -> str:
        return re.sub(r"[^a-z]", "", text.lower())

    def state_named(self, text: str) -> str | None:
        """A state from free text such as "KARNATAKA", "MH" or "Tamil Nasdu"; None when it cannot be read."""
        key = self.squash(text)
        if not key or key == "india":
            return None
        if key in self.aliases:
            return self.aliases[key]
        close = difflib.get_close_matches(key, [k for k in self.aliases if len(k) > 3], n=1, cutoff=0.8)
        return self.aliases[close[0]] if close else None

    @staticmethod
    def reach_m(place: dict) -> float:
        """How far a place's name carries: a town names its own streets, a metropolis its suburbs too."""
        km = 6 + 5 * math.log10(max(place["population"], 1) / 50_000)
        return min(20.0, max(5.0, km)) * 1000

    def locate(self, point: dict, stated_state: str | None) -> tuple[str | None, str, str] | None:
        """(locality, city, state) for a point; None for a point more than 120 km from any Indian town, which
        is a mistyped coordinate."""
        near = self.grid.within(point, 25_000) or self.grid.within(point, 120_000)
        if not near:
            return None
        # A stated state is a hint typed by a contributor, so it stands only if a nearby town is in it.
        nearby_states = {p["state"] for _, p in near[:8]}
        state = stated_state if stated_state in nearby_states else near[0][1]["state"]
        in_state = [(d, p) for d, p in near if p["state"] == state] or near
        covering = [p for d, p in in_state if d <= self.reach_m(p)]
        city = max(covering, key=lambda p: (p["population"], -p["id"]))["name"] if covering else in_state[0][1]["name"]
        city = CITY_NAMES.get(city, city)
        nearest_distance, nearest = in_state[0]
        locality = CITY_NAMES.get(nearest["name"], nearest["name"])
        locality = locality if nearest_distance <= 6_000 and locality != city else None
        return locality, city, state


# ---- names and addresses ----

def clean(text: str | None) -> str:
    text = PLUS_CODE.sub("", (text or "").replace("\n", " "))
    text = re.sub(r"\s+", " ", text).strip(" ,.-")
    if len(text) > 4 and text.isupper():
        text = text.title()
    return text


def operator_named(value: str | None) -> str | None:
    value = re.sub(r"\s*\((IN|India|TR)\)\s*$", "", (value or "").strip(), flags=re.IGNORECASE)
    lookup = value.lower()
    if lookup in NO_OPERATOR:
        return None
    return OPERATOR_NAMES.get(lookup, value)


def join_address(parts: list[str | None], city: str, state: str, postcode: str | None = None) -> str:
    """Address parts in order, dropping any part already said, then the city, state and PIN if missing."""
    text = ""
    pieces = [piece for part in parts for piece in (part or "").split(",")] + [city, state]
    for piece in pieces:
        piece = clean(piece)
        if piece and piece.lower() not in text.lower() and piece.lower() not in ("india", "u", "up border"):
            text = f"{text}, {piece}" if text else piece
    if postcode and re.fullmatch(r"\d{6}", postcode) and postcode not in text:
        text = f"{text} {postcode}"
    return text


def site_name(title: str | None, operator: str | None, place_name: str) -> str:
    title = clean(title)
    if not title or GENERIC_TITLES.match(title) or title.lower() in OPERATOR_NAMES:
        return f"{operator or 'EV charging point'}, {place_name}"
    return title[:150]


# ---- Open Charge Map sites ----

def ocm_connectors(location: dict, operator: str | None) -> list[tuple[str, float, str, str]]:
    """(connector type, kW, status, source) for one Open Charge Map location."""
    profile = dict((t, p) for t, p in reversed(OPERATOR_PROFILES.get(operator or "", DEFAULT_PROFILE)))
    result = []
    for connection in location["connections"]:
        kw = connection["powerKw"] or None
        connector_type = OCM_CONNECTORS.get(connection["type"])
        if connector_type is None and connection["type"] in ("Unknown", ""):
            # An unlabelled connection: a DC rating is a CCS2 gun, a mid-size AC rating a Type 2 point, and a
            # rating under 3 kW a two-wheeler socket.
            if kw is None:
                connector_type = "CCS2" if connection["current"] == "DC" else None
            elif kw >= 15:
                connector_type = "CCS2"
            elif kw >= 3:
                connector_type = "TYPE2_AC"
        if connector_type is None:
            continue
        source = "openchargemap"
        if kw is None:
            kw, source = profile.get(connector_type, DEFAULT_KW[connector_type]), "openchargemap (power estimated)"
        kw = round(min(float(kw), MAX_AC_KW.get(connector_type, 400.0)), 1)
        if kw < MIN_CAR_KW:
            continue  # a socket this small charges two-wheelers, not cars
        status = "OUT_OF_SERVICE" if connection["status"] in OCM_STATION_STATUS else "OPERATIONAL"
        quantity = min(MAX_PER_CONNECTION, max(1, connection["quantity"] or 1))
        result.extend([(connector_type, kw, status, source)] * quantity)
    return result


def ocm_sites(places: Places) -> list[dict]:
    snapshot = json.loads(OCM_SNAPSHOT.read_text(encoding="utf-8"))
    kept: list[dict] = []
    index = Grid()
    for location in snapshot["locations"]:
        operator = operator_named(location["operator"])
        if location["status"] in OCM_SKIP_STATUS or location["usage"] in OCM_SKIP_USAGE:
            continue
        if TWO_WHEELER.search(" ".join([location["title"], location["operator"]])):
            continue
        plugs = ocm_connectors(location, operator)
        if not plugs:
            continue
        point = {"latitude": location["latitude"], "longitude": location["longitude"]}
        title = clean(location["title"]).lower()
        if any(clean(other["title"]).lower() == title or other["operatorName"] == operator
               for _, other in index.within(point, OCM_DUPLICATE_M)):
            continue  # the same site listed twice
        located = places.locate(point, places.state_named(location["stateOrProvince"]))
        if located is None:
            continue
        locality, city, state = located
        town = clean(location["town"])
        place_name = town if town and town.lower() not in title else (locality or city)
        site = {
            **point,
            "title": location["title"],
            "operatorName": operator,
            "name": site_name(location["title"], operator, place_name),
            "address": join_address([location["addressLine1"], location["addressLine2"], town], city, state,
                                    location["postcode"]),
            "city": city,
            "state": state,
            "status": OCM_STATION_STATUS.get(location["status"], "OPERATIONAL"),
            "plugs": plugs,
            "locality": locality or town or city,
            "source": f"openchargemap:{location['ocmId']}",
        }
        kept.append(site)
        index.add(site)
    return kept


# ---- OpenStreetMap sites ----

def osm_operator(tags: dict) -> str | None:
    for key in ("brand", "operator"):
        value = tags.get(key)
        if value:
            # Mahavitaran's DC units are tagged with the charger maker (Delta) as brand.
            if value.strip().lower() == "delta" and tags.get("operator"):
                continue
            return operator_named(value)
    return None


def is_car_station(tags: dict) -> bool:
    if tags.get("access") in ("private", "no") or tags.get("scooter") == "yes":
        return False
    if tags.get("charging_station:battery_swapping") == "yes" or tags.get("disused") == "yes":
        return False
    return not TWO_WHEELER.search(" ".join(tags.get(k, "") for k in ("brand", "operator", "name", "network")))


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
                result.extend([(connector_type, parse_kw(tags.get(f"socket:{socket}:output")))]
                              * min(MAX_PER_CONNECTION, int(count)))
    return result


def osm_connectors(site: dict, operator: str | None, rng: random.Random) -> list[tuple[str, float, str, str]]:
    """(connector type, kW, status, source) for one OSM site. OSM has no live status, so a few connectors are
    marked out of service at random to exercise the outage paths."""
    tags = site["tags"]
    profile = OPERATOR_PROFILES.get(operator or "", DEFAULT_PROFILE)
    profile_kw = dict((t, kw) for t, kw in reversed(profile))

    mapped = socket_connectors(site)
    if mapped:
        plugs = [(t, kw if kw else profile_kw.get(t, DEFAULT_KW[t]),
                  "openstreetmap" if kw else "openstreetmap (power estimated)") for t, kw in mapped]
    else:
        capacity = tags.get("capacity", "").strip().lower()
        power = parse_kw(tags.get("note")) or parse_kw(tags.get("description")) or parse_kw(capacity)
        count = None
        if capacity.isdigit():
            # A bare number above ten is a power rating mistyped as capacity (e.g. Statiq "60").
            count, power = (int(capacity), power) if int(capacity) <= 10 else (None, power or float(capacity))
        count_match = re.search(r"space for (\d+)", tags.get("description", "").lower())
        if count_match:
            count = int(count_match.group(1))
        count = min(MAX_PER_CONNECTION, count or len(profile))
        if power is not None:
            # A mapped DC rating: every bay is a CCS2 gun sharing that rating, as at most single-charger sites.
            plugs = [("CCS2", power, "openstreetmap")] * count
        else:
            plugs = [(profile[i % len(profile)][0], profile[i % len(profile)][1], "estimated") for i in range(count)]
    return [(t, round(min(float(kw), MAX_AC_KW.get(t, 400.0)), 1),
             "OUT_OF_SERVICE" if rng.random() < 0.05 else "OPERATIONAL", source) for t, kw, source in plugs]


def osm_sites(places: Places, taken: list[dict], rng: random.Random) -> list[dict]:
    snapshot = json.loads(OSM_SNAPSHOT.read_text(encoding="utf-8"))
    rows = [row for row in snapshot["stations"] if is_car_station(row["tags"])]
    merged: list[dict] = []
    for row in rows:
        twin = next((m for m in merged if metres_between(m, row) <= OSM_MERGE_M
                     and osm_operator(m["tags"]) == osm_operator(row["tags"])), None)
        if twin is None:
            merged.append({**row, "members": [row]})
        else:
            twin["members"].append(row)

    known = Grid(taken)
    result = []
    for site in merged:
        if known.within(site, SAME_SITE_M):
            continue  # Open Charge Map lists this site, with connector detail
        tags, address = site["tags"], site["address"]
        operator = osm_operator(tags)
        stated = places.state_named(address.get("state", "")) if address.get("state") else None
        located = places.locate(site, stated)
        if located is None:
            continue
        locality, city, state = located
        suburb = address.get("suburb", "")
        if re.search(r"\bWard$", suburb):
            suburb = ""
        local = suburb or address.get("neighbourhood") or address.get("village") or address.get("town")
        place_name = local or locality or city
        name = tags.get("name:en") or tags.get("name")
        if tags.get("branch"):
            name = f"{operator or name}, {tags['branch']}"
        elif name and operator and clean(name).lower() not in OPERATOR_NAMES and clean(name) != operator:
            name = f"{clean(name)}, {place_name}"
        result.append({
            "latitude": site["latitude"],
            "longitude": site["longitude"],
            "name": site_name(name, operator, place_name),
            "address": join_address([address.get("road"), local, address.get("city")], city, state,
                                    address.get("postcode") if site.get("addressSource") == "tags" else None),
            "city": city,
            "state": state,
            "status": "OPERATIONAL",
            "plugs": osm_connectors(site, operator, rng),
            "locality": place_name,
            "source": f"openstreetmap:{site['osmType']}/{site['osmId']}",
        })
    return result


def stations_and_connectors(rng: random.Random) -> tuple[list[dict], list[dict]]:
    places = Places()
    sites = ocm_sites(places)
    sites += osm_sites(places, sites, rng)
    # Ordered by state, city, then north to south, so the ids read naturally in lists and on the map.
    sites.sort(key=lambda s: (s["state"], s["city"], -s["latitude"], s["longitude"]))

    # Sites of one name get their locality, then their address, so every station name is unique.
    for qualifier in ("locality", "address"):
        counts: dict[str, int] = {}
        for site in sites:
            counts[site["name"]] = counts.get(site["name"], 0) + 1
        for site in sites:
            extra = clean(str(site.get(qualifier) or "").split(",")[0])
            if counts[site["name"]] > 1 and extra and extra.lower() not in site["name"].lower():
                site["name"] = f"{site['name']}, {extra}"[:160]
    seen: dict[str, int] = {}
    for site in sites:
        seen[site["name"]] = seen.get(site["name"], 0) + 1
        if seen[site["name"]] > 1:
            site["name"] = f"{site['name']} ({seen[site['name']]})"

    station_rows: list[dict] = []
    connector_rows: list[dict] = []
    for index, site in enumerate(sites, start=1):
        station_id = f"S-{index:04d}"
        for connector_type, kw, status, source in site["plugs"]:
            connector_rows.append({
                "id": f"C-{len(connector_rows) + 1:05d}",
                "stationId": station_id,
                "connectorType": connector_type,
                "maxPowerKw": kw,
                "status": status,
                "source": source,
            })
        # Sanctioned grid load is not public. Sites share a supply smaller than every charger at full power
        # (60-100 % of the nameplate total), which is what makes power allocation matter.
        nameplate = sum(kw for _, kw, _, _ in site["plugs"])
        grid = max(5.0, round(nameplate * rng.uniform(0.6, 1.0) / 5) * 5)
        station_rows.append({
            "id": station_id,
            "name": site["name"],
            "address": site["address"],
            "city": site["city"],
            "state": site["state"],
            "latitude": site["latitude"],
            "longitude": site["longitude"],
            "gridCapacityKw": float(grid),
            "status": site["status"],
            "source": site["source"],
        })
    return station_rows, connector_rows


# ---- drivers, vehicles and requests ----

def home_cities(station_rows: list[dict]) -> list[tuple[str, str]]:
    """(city, state) for each driver: cities with enough stations, in proportion to their station count."""
    counts: dict[tuple[str, str], int] = {}
    for station in station_rows:
        if station["status"] == "OPERATIONAL":
            key = (station["city"], station["state"])
            counts[key] = counts.get(key, 0) + 1
    cities = sorted((key for key, n in counts.items() if n >= MIN_CITY_STATIONS), key=lambda k: (-counts[k], k))
    total = sum(counts[key] for key in cities)
    shares = {key: max(1, DRIVER_COUNT * counts[key] // total) for key in cities}
    # Largest remainder, so the shares add up to exactly DRIVER_COUNT.
    order = sorted(cities, key=lambda k: (-(DRIVER_COUNT * counts[k] % total), k))
    index = 0
    while sum(shares.values()) < DRIVER_COUNT:
        shares[order[index % len(order)]] += 1
        index += 1
    while sum(shares.values()) > DRIVER_COUNT:
        shares[max(cities, key=lambda k: shares[k])] -= 1
    return [key for key in cities for _ in range(shares[key])]


def registration(rng: random.Random, state: str, used: set[str]) -> str:
    code, districts = STATE_PLATES.get(state, ("KA", 70))
    while True:
        letters = "".join(rng.choice("ABCDEFGHJKLMNPRSTUVWXYZ") for _ in range(2))
        plate = f"{code}{rng.randint(1, districts):02d}{letters}{rng.randint(1000, 9999)}"
        if plate not in used:
            used.add(plate)
            return plate


def vehicles(rng: random.Random, station_rows: list[dict]) -> list[dict]:
    used_plates: set[str] = set()
    used_names: set[str] = set()
    weights = [model[4] for model in VEHICLE_MODELS]
    result = []
    for number, (city, state) in enumerate(home_cities(station_rows), start=1):
        while True:
            owner = f"{rng.choice(GIVEN_NAMES)} {rng.choice(FAMILY_NAMES.get(state, PAN_INDIAN_FAMILY_NAMES))}"
            if owner not in used_names:
                used_names.add(owner)
                break
        # Most households run one EV; every third driver has a second.
        for _ in range(2 if number % 3 == 1 else 1):
            model, battery, max_kw, plug_types, _ = rng.choices(VEHICLE_MODELS, weights=weights)[0]
            result.append({
                "registrationNumber": registration(rng, state, used_plates),
                "ownerEmail": f"driver{number:02d}@demo.chargewise.test",
                "ownerName": owner,
                "homeCity": city,
                "homeState": state,
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
    by_city: dict[tuple[str, str], list[dict]] = {}
    for station in station_rows:
        by_city.setdefault((station["city"], station["state"]), []).append(station)
    # Demand is uneven: within a city a few sites (a mall, a highway plaza) draw most drivers, so popularity
    # falls off with a random rank, as in a Zipf distribution.
    popularity: dict[str, float] = {}
    for stations in by_city.values():
        ranked = stations[:]
        rng.shuffle(ranked)
        for rank, station in enumerate(ranked, start=1):
            popularity[station["id"]] = 1 / rank
    windows: dict[str, list[tuple[int, int]]] = {v["registrationNumber"]: [] for v in vehicle_rows}
    result = []
    while len(result) < REQUEST_COUNT:
        vehicle = rng.choice(vehicle_rows)
        local = by_city[(vehicle["homeCity"], vehicle["homeState"])]
        compatible = [s for s in local if any(p["connectorType"] in vehicle["connectorTypes"]
                                               for p in plugs[s["id"]])]
        # Most drivers pick a station they can plug into; a few do not check, which the matcher must reject.
        pool = compatible if compatible and rng.random() < 0.92 else local
        station = rng.choices(pool, weights=[popularity[s["id"]] for s in pool])[0]
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
    vehicle_rows = vehicles(rng, station_rows)
    request_rows = charging_requests(rng, vehicle_rows, station_rows, connector_rows)

    args.out.mkdir(parents=True, exist_ok=True)
    write_json(args.out / "stations.json", station_rows)
    write_json(args.out / "connectors.json", connector_rows)
    write_json(args.out / "vehicles.json", vehicle_rows)
    write_json(args.out / "charging_requests.json", request_rows)
    owners = len({v["ownerEmail"] for v in vehicle_rows})
    print(f"seed {args.seed}: {len(station_rows)} stations, {len(connector_rows)} connectors, "
          f"{len(vehicle_rows)} vehicles ({owners} drivers), {len(request_rows)} charging requests -> {args.out}")


if __name__ == "__main__":
    main()
