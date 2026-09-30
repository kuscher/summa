#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Builds engine/src/main/resources/summa/places.tsv.gz from GeoNames (CC BY 4.0, geonames.org):
cities of 100k+ people, every capital, and every country (mapped to its capital's time zone).
Rows: name, country code, IANA zone, population, alternate spellings (|-separated). Largest first,
so when two places share a name the bigger one wins.
    python3 tools/places.py
"""
import csv
import gzip
import io
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".cache/summa"
BASE = "https://download.geonames.org/export/dump/"


def fetch(name):
    p = CACHE / name
    if not p.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(BASE + name, p)
    return p


def main():
    rows = {}
    capitals = {}
    with zipfile.ZipFile(fetch("cities15000.zip")) as z:
        data = z.read("cities15000.txt").decode("utf-8")
    for line in data.splitlines():
        f = line.split("\t")
        name, ascii_, fcode, cc, zone, pop = f[1], f[2], f[7], f[8], f[17], int(f[14] or 0)
        if not zone:
            continue
        if fcode == "PPLC":
            capitals[cc] = (zone, name)
        if pop >= 100_000 or fcode == "PPLC":
            key = name.lower()
            if key in rows and rows[key][3] >= pop:
                continue
            alts = [ascii_] if ascii_ and ascii_ != name else []
            rows[key] = (name, cc, zone, pop, alts)
    # Countries by their capital's zone ("time in Japan", "3 pm Germany in Brazil").
    info = fetch("countryInfo.txt").read_text(encoding="utf-8")
    for line in info.splitlines():
        if line.startswith("#"):
            continue
        f = line.split("\t")
        cc, country = f[0], f[4]
        if cc in capitals:
            zone, _ = capitals[cc]
            rows.setdefault(country.lower(), (country, cc, zone, 10**10, []))
    out = sorted(rows.values(), key=lambda r: -r[3])
    buf = io.StringIO()
    for name, cc, zone, pop, alts in out:
        buf.write("\t".join([name, cc, zone, str(pop), "|".join(alts)]) + "\n")
    target = ROOT / "engine/src/main/resources/summa/places.tsv.gz"
    with gzip.open(target, "wt", encoding="utf-8", compresslevel=9) as g:
        g.write(buf.getvalue())
    print(target.relative_to(ROOT), len(out), "places", target.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
