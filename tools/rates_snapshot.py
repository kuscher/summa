#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Refreshes the exchange rates bundled with Summa (engine/src/main/resources/summa/rates.json).

Source: the European Central Bank's daily reference rates (free reuse with the source cited).
Summa ships these so money works offline; the app refreshes from Frankfurter/ECB at runtime.
    python3 tools/rates_snapshot.py
"""
import json
import pathlib
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"


def main():
    xml = urllib.request.urlopen(URL, timeout=30).read()
    root = ET.fromstring(xml)
    rates, date = {}, None
    for cube in root.iter():
        if cube.tag.endswith("Cube") and "time" in cube.attrib:
            date = cube.attrib["time"]
        if cube.tag.endswith("Cube") and "currency" in cube.attrib:
            rates[cube.attrib["currency"]] = cube.attrib["rate"]
    out = {"base": "EUR", "asOf": date, "source": "European Central Bank", "rates": dict(sorted(rates.items()))}
    target = ROOT / "engine/src/main/resources/summa/rates.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(out, indent=1) + "\n")
    print(target.relative_to(ROOT), date, len(rates), "currencies")


if __name__ == "__main__":
    main()
