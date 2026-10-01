/**
 * Luhn Gate — TypeScript core.
 * Compile: tsc luhn.ts --target es2020 --module commonjs
 * Run:     node luhn.js
 */
import express, { Request, Response, NextFunction } from "express";
import { z } from "zod";

export type Brand =
  | "visa" | "mastercard" | "amex" | "discover"
  | "jcb"  | "diners"     | "unknown";

export interface Verdict {
  readonly valid: boolean;
  readonly brand: Brand;
  readonly s1: number;
  readonly s2: number;
  readonly total: number;
  readonly digits: number;
  readonly elapsedUs: number;
}

const DIGITS = /^\d+$/;

const IIN: ReadonlyArray<readonly [Brand, readonly (readonly [string, string])[]]> = [
  ["visa",       [["4", "4"]]],
  ["mastercard", [["51", "55"], ["2221", "2720"]]],
  ["amex",       [["34", "34"], ["37", "37"]]],
  ["discover",   [["6011", "6011"], ["644", "649"], ["65", "65"]]],
  ["jcb",        [["3528", "3589"]]],
  ["diners",     [["300", "305"], ["36", "36"], ["38", "39"]]],
];

const clean = (raw: string): string => raw.replace(/\s+/g, "");

export function luhnTest(raw: string): boolean {
  const s = clean(raw);
  if (s.length < 2 || !DIGITS.test(s)) return false;
  let sum = 0;
  let alt = false;
  for (let i = s.length - 1; i >= 0; i--) {
    let d = s.charCodeAt(i) - 48;
    if (alt) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
    alt = !alt;
  }
  return sum % 10 === 0;
}

export function detectBrand(raw: string): Brand {
  const s = clean(raw);
  for (const [brand, ranges] of IIN) {
    for (const [lo, hi] of ranges) {
      const w = lo.length;
      const prefix = s.slice(0, w);
      if (DIGITS.test(prefix) && prefix >= lo && prefix <= hi) return brand;
    }
  }
  return "unknown";
}

export function audit(raw: string): Verdict {
  const s = clean(raw);
  const t0 = performance.now();
  if (s.length < 2 || !DIGITS.test(s)) {
    return { valid: false, brand: "unknown", s1: 0, s2: 0, total: 0,
             digits: s.length, elapsedUs: (performance.now() - t0) * 1000 };
  }
  let s1 = 0, s2 = 0, alt = false;
  for (let i = s.length - 1; i >= 0; i--) {
    let d = s.charCodeAt(i) - 48;
    if (alt) {
      const dd = d * 2;
      s2 += dd > 9 ? dd - 9 : dd;
    } else {
      s1 += d;
    }
    alt = !alt;
  }
  const total = s1 + s2;
  return { valid: total % 10 === 0, brand: detectBrand(s),
           s1, s2, total, digits: s.length,
           elapsedUs: (performance.now() - t0) * 1000 };
}

// ---------- HTTP surface ----------
const PanSchema = z.string().min(1).max(32).transform(clean)
  .refine(v => DIGITS.test(v), { message: "PAN must be numeric" });

const BatchSchema = z.array(PanSchema).max(10_000);

export const app = express();
app.use(express.json({ limit: "2mb" }));

app.get("/health", (_req, res) => res.json({ status: "ok" }));

app.post("/v1/validate", (req: Request, res: Response) => {
  const parsed = PanSchema.safeParse(req.body?.pan);
  if (!parsed.success) return res.status(422).json({ error: parsed.error.flatten() });
  res.json(audit(parsed.data));
});

app.post("/v1/validate/batch", (req: Request, res: Response) => {
  const parsed = BatchSchema.safeParse(req.body);
  if (!parsed.success) return res.status(422).json({ error: parsed.error.flatten() });
  res.json(parsed.data.map(audit));
});

app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  res.status(500).json({ error: err.message });
});

// ---------- self-test ----------
const VECTORS: ReadonlyArray<readonly [string, boolean]> = [
  ["4111111111111111", true],
  ["4111111111111112", false],
  ["49927398716",      true],
  ["49927398717",      false],
  ["1234567812345678", false],
  ["1234567812345670", true],
];

if (require.main === module) {
  let failed = 0;
  for (const [pan, expected] of VECTORS) {
    const got = luhnTest(pan);
    const ok = got === expected;
    if (!ok) failed++;
    console.log(`[${ok ? "ok" : "XX"}] ${pan} → ${got} (brand=${detectBrand(pan)})`);
  }
  process.exit(failed ? 1 : 0);
}
