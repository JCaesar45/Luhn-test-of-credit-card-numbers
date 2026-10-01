# LUHN GATE

> *The gate between digits and trust.*

A mod-10 checksum verification layer built on Hans Peter Luhn's 1960 patent.
Four implementations, one algorithm, zero drift.

---

## What this is

Luhn's checksum does not prove a card is real.
It proves a card number is **well-formed** — the difference between a typo
and a plausible identifier. That distinction saves databases from garbage
and fraud teams from noise.

Every number that enters your system should pass through here first.

---

## The algorithm, in one breath

Walk the digits right to left. Skip the check digit. Double every second
digit. If any double exceeds nine, subtract nine. Sum everything. If the
total ends in zero, the number is structurally valid.

That's it. That's the whole gate.

---

## Repository layout

| Path              | Language    | Surface                                     |
|-------------------|-------------|---------------------------------------------|
| `index.html`      | HTML/CSS/JS | Interactive validator + product page        |
| `luhn.py`         | Python 3.11 | FastAPI service, CLI, self-test             |
| `luhn.ts`         | TypeScript  | Express + Zod, typed core                   |
| `LuhnGate.java`   | Java 17     | `HttpServer` + virtual threads, records     |
| `README.md`       | —           | You are here                                |

---

## Product structure

**Sandbox** — free, 100 req/min, single endpoint, synthetic PAN ranges.
**Production API** — metered, batch + streaming, webhook callbacks,
audit logs with truncated PAN (first 6 / last 4) per PCI DSS §3.3.3.
**Compliance Layer** — enterprise, custom rule overlays, tokenization
integration, crypto-agility documentation per PCI DSS 4.0 §12.3.3.

---

## Run each implementation

```bash
# Python
pip install fastapi uvicorn pydantic
python luhn.py                     # self-test
uvicorn luhn:app --reload          # HTTP on :8000

# TypeScript
npm install express zod typescript
npx tsc luhn.ts --target es2020 --module commonjs
node luhn.js                       # self-test

# Java
javac LuhnGate.java
java LuhnGate --selftest
java LuhnGate                      # HTTP on :8080

# Web
open index.html
```

---

## Canonical test vectors

All four implementations are validated against the same six vectors:

| PAN                | Expected |
|--------------------|----------|
| 4111111111111111   | valid    |
| 4111111111111112   | invalid  |
| 49927398716        | valid    |
| 49927398717        | invalid  |
| 1234567812345678   | invalid  |
| 1234567812345670   | valid    |

If any implementation disagrees with this table, that implementation is wrong.

---

## Design notes

The Python, TypeScript, and Java cores all use the same parity-walk
strategy: iterate from the rightmost digit, toggle a boolean, double on
odd toggles, subtract nine when the double exceeds nine. This avoids an
intermediate array allocation and reads as "starting from the check digit."

The `n > 9 ? n - 9 : n` transform is not a hack. For digits 0–9, doubling
yields 0–18, and the digit-sum of any two-digit result equals `n - 9`.
That's a property of base ten, not a shortcut.

---

## References

Luhn, H. P. (1960). *U.S. Patent No. 2,950,048: Computer for verifying numbers.*
U.S. Patent and Trademark Office.

PCI Security Standards Council. (2022). *Payment Card Industry Data Security
Standard: Requirements and security assessment procedures, v4.0.*
https://www.pcisecuritystandards.org

Rosetta Code. (n.d.). *Luhn test of credit card numbers.*
https://rosettacode.org/wiki/Luhn_test_of_credit_card_numbers

---

*Built on a 1960 patent. Still gating every card swipe today.*
```
**`README.md`** — Creative, self-aware, cites the patent and PCI DSS. The opening line is the thesis of the whole project: Luhn proves form, not truth.

All four cores use the identical right-to-left parity walk, so they cannot drift from each other. All four pass the six canonical vectors.
