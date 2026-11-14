"""Turns some platform-style tickets into PDFs with a QR code, for the end-to-end test.

python make_pdfs.py <tickets.json> <out dir> <id> [<id> ...]
"""
import json
import pathlib
import sys

import qrcode
from fpdf import FPDF

tickets = {t["id"]: t for t in json.load(open(sys.argv[1], encoding="utf-8"))}
out = pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
for i, ticket_id in enumerate(sys.argv[3:]):
    pdf = FPDF()
    pdf.add_page()
    pdf.add_font("dejavu", fname="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    pdf.set_font("dejavu", size=10)
    pdf.multi_cell(120, 5, tickets[ticket_id]["text"].replace("[REMOVIDO]", "Ana Silva"))
    qr = out / f"qr-{i}.png"
    qrcode.make(f"ENTRY-TESTE-{i:04d}").save(qr)
    pdf.image(str(qr), x=145, y=15, w=50)
    qr.unlink()
    pdf.output(str(out / f"{ticket_id}.pdf"))
print("pdfs:", len(sys.argv) - 3)
