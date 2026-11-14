"""Scores result files against a dataset: event codes right, wrong or invented, details, latency.

python score.py <tickets.json> <result.json> [<result.json> ...]
"""
import json
import re
import statistics
import sys

FIELDS = ["eventCode", "gate", "sector", "seat"]


def norm(v):
    return re.sub(r"[^a-z0-9]", "", v.lower()) if v else None


LABEL_WORDS = re.compile(r"(?i)\b(fila|lugar|cadeira|seat|row|asiento|porta|gate|puerta|entrada|"
                         r"setor|sector|section|zona|bancada)\b")


def detail_ok(exp, got):
    """Equal after ignoring case, punctuation and label words ("Lugar 14" equals "14")."""
    e = norm(LABEL_WORDS.sub("", exp)) if exp else None
    g = norm(LABEL_WORDS.sub("", got)) if got else None
    return (e or None) == (g or None)


tickets = {t["id"]: t["text"] for t in json.load(open(sys.argv[1], encoding="utf-8"))}
for path in sys.argv[2:]:
    r = json.load(open(path, encoding="utf-8"))
    rows = r["rows"]
    code_ok = wrong = invented = 0
    details = {f: 0 for f in FIELDS[1:]}
    misses = []
    for row in rows:
        exp, got = row["expected"]["eventCode"], row["got"]["eventCode"]
        if norm(exp) == norm(got):
            code_ok += 1
        else:
            misses.append(f'{row["id"]}: expected {exp!r} got {got!r}')
            if got:
                wrong += 1
                if got.lower() not in tickets[row["id"]].lower():
                    invented += 1
        for f in FIELDS[1:]:
            details[f] += detail_ok(row["expected"][f], row["got"][f])
    secs = [row["seconds"] for row in rows]
    n = len(rows)
    # A wrong code would connect the ticket to another event (risk R2); a missing one only asks the
    # participant to type it. So a wrong code costs twice what a right one earns.
    weighted = (code_ok - 2 * wrong) / n
    print(f'== {r["method"]}  (warm-up {r["warmup_seconds"]} s, client peak RSS {r["peak_rss_mb"]} MB)')
    print(f'  event code correct: {code_ok}/{n}   wrong code returned: {wrong}   not in text: {invented}'
          f'   weighted score (right - 2 x wrong) / n: {weighted:.2f}')
    print('  details correct: ' + ', '.join(f'{f} {v}/{n}' for f, v in details.items()))
    print(f'  latency per ticket: median {statistics.median(secs):.2f} s, max {max(secs):.2f} s')
    invalid = [row["id"] for row in rows if row.get("invalid")]
    if invalid:
        print(f'  invalid JSON: {invalid}')
    for m in misses:
        print('   - ' + m)
