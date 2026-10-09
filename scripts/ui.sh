#!/usr/bin/env bash
# Helpers for the emulator scripts: tap an element by its visible text or content description instead of by coordinates,
# so the scripts survive layout changes. Source it: `. scripts/ui.sh`.

ui_dump() {
  adb shell uiautomator dump /data/local/tmp/ui.xml > /dev/null 2>&1
  adb exec-out cat /data/local/tmp/ui.xml
}

# tap_text <regex> [seconds to wait afterwards]: taps the centre of the first element whose first text line (or content
# description) matches the regex, preferring clickable ones. Returns 1 when nothing matches.
tap_text() {
  local pat="$1" wait="${2:-2}" xy
  xy=$(ui_dump | python3 -c '
import re, sys
import xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1])
try:
    root = ET.fromstring(sys.stdin.read())
except Exception:
    sys.exit(1)
best = None
for n in root.iter("node"):
    for field in ("text", "content-desc"):
        v = (n.get(field) or "").strip().split("\n")[0]
        if not v or not pat.search(v):
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        cand = ((x1 + x2) // 2, (y1 + y2) // 2, n.get("clickable") == "true")
        if best is None or (cand[2] and not best[2]):
            best = cand
if best is None:
    sys.exit(1)
print(best[0], best[1])' "$pat") || { echo "not found: $pat"; return 1; }
  # shellcheck disable=SC2086
  adb shell input tap $xy
  sleep "$wait"
}
