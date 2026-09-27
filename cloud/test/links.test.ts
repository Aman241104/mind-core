import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { normalizeUrl, triage } from "../src/links.ts";

const v = JSON.parse(readFileSync(fileURLToPath(new URL("../../shared/vectors.json", import.meta.url).href), "utf8"));

test("normalize matches shared vectors", () => {
  for (const [raw, want] of v.normalize) assert.equal(normalizeUrl(raw), want, raw);
});

test("shelf matches shared vectors", () => {
  for (const [url, note, title, want] of v.shelf) assert.equal(triage(url, note, title).shelf, want, url);
});
