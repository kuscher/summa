#!/usr/bin/env python3
"""Prints the failure messages from the engine's last JUnit run."""
import glob, re, html
for f in glob.glob('engine/build/test-results/test/*.xml'):
    s = open(f).read()
    for m in re.finditer(r'<testcase name="(\w+)".*?<failure message="(.*?)"', s, re.S):
        print(m.group(1), html.unescape(m.group(2))[:4000])
