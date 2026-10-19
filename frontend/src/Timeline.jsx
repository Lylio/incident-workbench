import React from "react";
import { COLORS, services, utc } from "./evidence";
export default function Timeline({ entries, allEntries, selected, onSelect }) {
  const lanes = services(allEntries),
    first = new Date(allEntries[0].timestamp).getTime(),
    last = new Date(allEntries.at(-1).timestamp).getTime(),
    span = Math.max(last - first, 1000),
    W = 920,
    L = 112,
    R = 22,
    H = 60 + lanes.length * 59;
  const shown = entries.slice(0, 600);
  return (
    <section className="card timeline-card">
      <div className="section-head">
        <h2>Incident timeline</h2>
        <span>
          {utc(allEntries[0].timestamp)} – {utc(allEntries.at(-1).timestamp)}{" "}
          UTC
        </span>
      </div>
      <div className="timeline-scroll">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          role="img"
          aria-label="Imported evidence timeline by service"
        >
          <title>
            Evidence timestamps by service; markers open source records
          </title>
          {Array.from({ length: 6 }, (_, i) => {
            const x = L + ((W - L - R) * i) / 5;
            return (
              <g key={i}>
                <line
                  x1={x}
                  x2={x}
                  y1="32"
                  y2={H - 12}
                  stroke="#294055"
                  strokeDasharray="3 5"
                />
                <text
                  x={x}
                  y="18"
                  textAnchor="middle"
                  fill="#718ca5"
                  fontSize="10"
                >
                  {utc(first + (span * i) / 5)}
                </text>
              </g>
            );
          })}
          {lanes.map((service, i) => (
            <g key={service}>
              <circle cx="12" cy={57 + i * 59} r="4" fill={COLORS[i]} />
              <text x="24" y={61 + i * 59} fill="#c8d8e9" fontSize="12">
                {service}
              </text>
              <line
                x1={L}
                x2={W - R}
                y1={57 + i * 59}
                y2={57 + i * 59}
                stroke={COLORS[i]}
                opacity=".4"
              />
            </g>
          ))}
          {shown.map((e) => {
            const x =
                L +
                ((new Date(e.timestamp).getTime() - first) / span) *
                  (W - L - R),
              y = 57 + lanes.indexOf(e.service) * 59,
              color =
                e.level === "ERROR" || e.level === "FATAL"
                  ? "#fb7b8b"
                  : e.level === "WARN"
                    ? "#efc46c"
                    : COLORS[lanes.indexOf(e.service)];
            return (
              <g key={e.id}>
                {e.id === selected && (
                  <circle
                    cx={x}
                    cy={y}
                    r="10"
                    stroke={color}
                    fill="none"
                    strokeWidth="2"
                  />
                )}
                <circle
                  role="button"
                  aria-label={`Open ${e.id}`}
                  tabIndex="0"
                  cx={x}
                  cy={y}
                  r={e.level === "ERROR" ? 5 : 3.5}
                  fill={color}
                  stroke="#102030"
                  strokeWidth="1"
                  onClick={() => onSelect(e.id)}
                  onKeyDown={(k) => {
                    if (k.key === "Enter" || k.key === " ") {
                      k.preventDefault();
                      onSelect(e.id);
                    }
                  }}
                >
                  <title>
                    {e.id} · {e.service} · {e.message}
                  </title>
                </circle>
              </g>
            );
          })}
        </svg>
      </div>
      <div className="timeline-legend">
        <span>
          <i style={{ background: "#50a5ff" }} />
          Service event
        </span>
        <span>
          <i style={{ background: "#efc46c" }} />
          Warning
        </span>
        <span>
          <i style={{ background: "#fb7b8b" }} />
          Error
        </span>
        <span className="muted">
          {entries.length > 600
            ? `First 600 of ${entries.length} filtered records`
            : `${entries.length} records · click a marker to inspect`}
        </span>
      </div>
    </section>
  );
}
