"""
Luhn Gate — Python verification service.
Run:  uvicorn luhn:app --reload
CLI:  python luhn.py 49927398716
"""
from __future__ import annotations
import re
import time
from dataclasses import dataclass, field
from typing import Iterable

try:
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, Field, field_validator
    _HAS_FASTAPI = True
except ImportError:
    _HAS_FASTAPI = False


_DIGITS = re.compile(r"\d+")
_IIN_MAP = {
    "visa":       (("4",),),
    "mastercard": (("51", "55"), ("2221", "2720")),
    "amex":       (("34", "37"),),
    "discover":   (("6011", "6011"), ("644", "649"), ("65", "65")),
    "jcb":        (("3528", "3589"),),
    "diners":     (("300", "305"), ("36", "36"), ("38", "39")),
}


def _clean(raw: str) -> str:
    return re.sub(r"\s+", "", raw or "")


def luhn_sum(raw: str) -> int:
    """Return the raw mod-10 checksum sum (not yet reduced)."""
    clean = _clean(raw)
    if not clean or not _DIGITS.fullmatch(clean):
        raise ValueError("input must be numeric after whitespace stripping")
    total, alternate = 0, False
    for ch in reversed(clean):
        d = ord(ch) - 48
        if alternate:
            d *= 2
            if d > 9:
                d -= 9
        total += d
        alternate = not alternate
    return total


def luhn_test(raw: str) -> bool:
    clean = _clean(raw)
    if len(clean) < 2 or not _DIGITS.fullmatch(clean):
        return False
    return luhn_sum(clean) % 10 == 0


def detect_brand(raw: str) -> str:
    clean = _clean(raw)
    for brand, ranges in _IIN_MAP.items():
        for lo, hi in ranges:
            width = len(lo)
            prefix = clean[:width]
            if prefix.isdigit() and lo <= prefix <= hi:
                return brand
    return "unknown"


@dataclass(frozen=True, slots=True)
class Verdict:
    valid: bool
    brand: str
    s1: int
    s2: int
    total: int
    digits: int
    elapsed_us: float = field(default=0.0)


def audit(raw: str) -> Verdict:
    clean = _clean(raw)
    t0 = time.perf_counter_ns()
    if len(clean) < 2 or not _DIGITS.fullmatch(clean):
        return Verdict(False, "unknown", 0, 0, 0, len(clean),
                       (time.perf_counter_ns() - t0) / 1000.0)

    s1 = s2 = 0
    alt = False
    for ch in reversed(clean):
        d = ord(ch) - 48
        if alt:
            dd = d * 2
            s2 += dd - 9 if dd > 9 else dd
        else:
            s1 += d
        alt = not alt

    total = s1 + s2
    return Verdict(total % 10 == 0, detect_brand(clean), s1, s2, total,
                   len(clean), (time.perf_counter_ns() - t0) / 1000.0)


# ---------- FastAPI surface ----------
if _HAS_FASTAPI:
    app = FastAPI(title="Luhn Gate", version="1.0.0")

    class ValidateRequest(BaseModel):
        pan: str = Field(..., min_length=1, max_length=32)

        @field_validator("pan")
        @classmethod
        def _no_symbols(cls, v: str) -> str:
            if not _DIGITS.fullmatch(_clean(v)):
                raise ValueError("PAN must contain digits only")
            return v

    class ValidateResponse(BaseModel):
        valid: bool
        brand: str
        s1: int
        s2: int
        total: int
        digits: int
        elapsed_us: float

    @app.post("/v1/validate", response_model=ValidateResponse)
    def validate(req: ValidateRequest) -> ValidateResponse:
        v = audit(req.pan)
        if v.digits < 2:
            raise HTTPException(status_code=422, detail="PAN too short")
        return ValidateResponse(**v.__dict__)

    @app.post("/v1/validate/batch", response_model=list[ValidateResponse])
    def validate_batch(payload: list[ValidateRequest]) -> list[ValidateResponse]:
        if len(payload) > 10_000:
            raise HTTPException(status_code=413, detail="batch too large")
        return [ValidateResponse(**audit(p.pan).__dict__) for p in payload]

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}


# ---------- CLI ----------
if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        for arg in sys.argv[1:]:
            v = audit(arg)
            mark = "PASS" if v.valid else "FAIL"
            print(f"{arg:<24} {mark}  brand={v.brand:<10} "
                  f"s1={v.s1:<4} s2={v.s2:<4} total={v.total:<4} "
                  f"{v.elapsed_us:.1f}µs")
    else:
        # self-test against canonical vectors
        vectors: Iterable[tuple[str, bool]] = [
            ("4111111111111111", True),
            ("4111111111111112", False),
            ("49927398716",      True),
            ("49927398717",      False),
            ("1234567812345678", False),
            ("1234567812345670", True),
        ]
        failures = 0
        for pan, expected in vectors:
            got = luhn_test(pan)
            ok = got == expected
            failures += not ok
            print(f"[{'ok' if ok else 'XX'}] {pan} → {got}")
        sys.exit(1 if failures else 0)
