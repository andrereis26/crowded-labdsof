"""Runs one extraction method over a ticket dataset and records what it found (ADR 0020).

python run.py <rules|nuextract> <tickets.json> <out.json>

`rules` is the baseline without AI. `nuextract` calls a llama.cpp server running NuExtract at
LLM_URL (default http://llm:8080), with the prompt Core's NuExtractTicketExtractor sends.
"""
import json
import os
import re
import resource
import sys
import time
import urllib.request

FIELDS = ["eventCode", "gate", "sector", "seat"]
LLM_URL = os.environ.get("LLM_URL", "http://llm:8080")


# ---------- Rules, without AI: a label followed by a value ----------
LABELS = {
    "eventCode": r"c[oó]d(?:igo|\.)?\s*(?:do|del)?\s*(?:evento|espet[aá]culo)|event\s*code|id\s+do\s+evento",
    "gate": r"porta|entrada|gate|puerta|acesso",
    "sector": r"setor|sector|zona|bancada|section",
    "seat": r"lugar|seat|asiento|cadeira",
}


def rules(text):
    flat = re.sub(r"\s*\n\s*", " \n ", text)
    out = {}
    for field, label in LABELS.items():
        if field == "eventCode":
            m = re.search(rf"(?i)(?:{label})\s*:?\s*\n?\s*([A-Za-z0-9][A-Za-z0-9-]{{2,31}})", flat)
        else:
            m = re.search(rf"(?i)\b(?:{label})\b\s*:?\s*([^\n·:]+?)\s*(?=\n|·|$)", text)
        out[field] = m.group(1).strip() if m else None
    return out


# ---------- NuExtract 2.0 behind llama.cpp's server ----------
TEMPLATE = json.dumps({"event_code": "verbatim-string", "gate": "verbatim-string",
                       "sector": "verbatim-string", "seat": "verbatim-string"}, indent=4)
KEYS = {"event_code": "eventCode", "gate": "gate", "sector": "sector", "seat": "seat"}


def nuextract(text):
    """NuExtract 2.0's template in its chat format (Qwen2)."""
    prompt = ("<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n"
              f"<|im_start|>user\n# Template:\n{TEMPLATE}\n{text}<|im_end|>\n<|im_start|>assistant\n")
    return complete(prompt, ["<|im_end|>"])


def complete(prompt, stop):
    body = json.dumps({"prompt": prompt, "temperature": 0, "n_predict": 160,
                       "stop": stop}).encode()
    req = urllib.request.Request(f"{LLM_URL}/completion", body,
                                 {"Content-Type": "application/json"})
    raw = json.loads(urllib.request.urlopen(req, timeout=60).read())["content"]
    cleaned = re.sub(r"^\s*```(?:json)?|```\s*$", "", raw.strip())
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        data = None
    if not isinstance(data, dict):
        return {f: None for f in FIELDS} | {"_invalid": raw[:200]}
    return {KEYS[k]: (v if isinstance(v, str) and v.strip() else None)
            for k, v in data.items() if k in KEYS}


def main():
    method, src, dst = sys.argv[1:4]
    run = {"rules": rules, "nuextract": nuextract}[method]
    tickets = json.load(open(src, encoding="utf-8"))
    t0 = time.perf_counter()
    run(tickets[0]["text"])  # Warm-up: the server loads the model on the first request.
    load = time.perf_counter() - t0
    rows = []
    for t in tickets:
        start = time.perf_counter()
        got = run(t["text"])
        rows.append({"id": t["id"], "kind": t["kind"], "expected": t["expected"],
                     "got": {f: got.get(f) for f in FIELDS}, "invalid": got.get("_invalid"),
                     "seconds": round(time.perf_counter() - start, 3)})
    # The client's own memory; the model's is measured on its container (README).
    peak_mb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
    with open(dst, "w", encoding="utf-8") as out:
        json.dump({"method": method, "warmup_seconds": round(load, 2), "peak_rss_mb": round(peak_mb),
                   "rows": rows}, out, ensure_ascii=False, indent=1)
    print(method, "done", round(load, 1), "s warm-up")


if __name__ == "__main__":
    main()
