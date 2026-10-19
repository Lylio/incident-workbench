export const COLORS = [
  "#50a5ff",
  "#4bd5b0",
  "#fb7b8b",
  "#b397ff",
  "#efc46c",
  "#69d4e8",
  "#d9a6e2",
  "#c5d376",
  "#8bd1a4",
  "#a9b8f2",
  "#eeae91",
  "#95bdc6",
];
export function services(entries) {
  return [...new Set(entries.map((e) => e.service))].sort((a, b) => {
    const order = ["order", "inventory", "payment"];
    const ai = order.indexOf(a),
      bi = order.indexOf(b);
    return (ai < 0 ? 100 : ai) - (bi < 0 ? 100 : bi) || a.localeCompare(b);
  });
}
export function filtered(
  entries,
  { query = "", service = "", level = "", trace = "" },
) {
  const q = query.toLowerCase();
  return entries.filter(
    (e) =>
      (!service || e.service === service) &&
      (!level || e.level === level) &&
      (!trace || e.traceId === trace) &&
      (!q ||
        [e.id, e.message, e.service, e.traceId, e.fileName]
          .join(" ")
          .toLowerCase()
          .includes(q)),
  );
}
export function observedPatterns(entries) {
  const groups = new Map();
  for (const e of entries.filter(
    (e) => e.level === "ERROR" || e.level === "FATAL",
  )) {
    const key =
      e.service +
      "|" +
      e.message.replace(/ORD-\d+/g, "ORD-#").replace(/order=[^ ]+/g, "order=#");
    const g = groups.get(key) || {
      message: e.message,
      service: e.service,
      ids: [],
    };
    g.ids.push(e.id);
    groups.set(key, g);
  }
  return [...groups.values()]
    .sort((a, b) => b.ids.length - a.ids.length)
    .slice(0, 3);
}
export function utc(time) {
  return new Date(time).toISOString().slice(11, 19);
}
export function latestReview(reviews, id, index) {
  return reviews.find(
    (r) => r.analysisId === id && r.hypothesisIndex === index,
  );
}
