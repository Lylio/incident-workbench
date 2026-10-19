import test from "node:test";
import assert from "node:assert/strict";
import {
  filtered,
  observedPatterns,
  latestReview,
  services,
} from "./evidence.js";
const e = [
  {
    id: "E0001",
    timestamp: "2026-10-18T14:00:00Z",
    service: "order",
    level: "ERROR",
    traceId: "a",
    fileName: "a.log",
    message: "Timeout order=ORD-1",
  },
  {
    id: "E0002",
    timestamp: "2026-10-18T14:00:01Z",
    service: "order",
    level: "ERROR",
    traceId: "b",
    fileName: "a.log",
    message: "Timeout order=ORD-2",
  },
  {
    id: "E0003",
    service: "payment",
    level: "INFO",
    traceId: "a",
    fileName: "b.log",
    message: "Started",
  },
];
test("filters combine rather than losing trace/service restrictions", () => {
  assert.equal(
    filtered(e, { service: "order", trace: "a", query: "timeout" }).length,
    1,
  );
  assert.equal(filtered(e, { level: "INFO" }).length, 1);
  assert.equal(filtered(e, { query: "E0002" })[0].id, "E0002");
});
test("patterns count observed errors without inventing root causes", () => {
  const g = observedPatterns(e);
  assert.equal(g.length, 1);
  assert.deepEqual(g[0].ids, ["E0001", "E0002"]);
});
test("review selection stays scoped to the analysis and hypothesis", () => {
  assert.equal(
    latestReview(
      [
        { analysisId: "a", hypothesisIndex: 0, status: "REJECTED" },
        { analysisId: "b", hypothesisIndex: 0, status: "ACCEPTED" },
      ],
      "b",
      0,
    ).status,
    "ACCEPTED",
  );
  assert.equal(latestReview([], "b", 0), undefined);
});
test("service lanes have stable ordering", () =>
  assert.deepEqual(services(e), ["order", "payment"]));
