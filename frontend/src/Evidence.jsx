import React, { useState, useEffect } from "react";
import {
  Search,
  Filter,
  FileText,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
} from "lucide-react";
import { services, utc } from "./evidence";
export default function Evidence({
  incident,
  entries,
  filters,
  setFilters,
  selected,
  onSelect,
  checked,
  setChecked,
}) {
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [filters]);
  useEffect(() => {
    const i = entries.findIndex((e) => e.id === selected);
    if (i >= 0) setPage(Math.floor(i / 25));
  }, [selected, entries]);
  const pages = Math.max(1, Math.ceil(entries.length / 25));
  const update = (key, value) => setFilters((f) => ({ ...f, [key]: value }));
  return (
    <section className="card evidence-card">
      <div className="section-head">
        <h2>
          Evidence <span>({entries.length})</span>
        </h2>
        <div className="search">
          <Search size={15} />
          <input
            aria-label="Search evidence"
            placeholder="Search logs, errors, trace IDs…"
            value={filters.query}
            onChange={(e) => update("query", e.target.value)}
          />
        </div>
      </div>
      <div className="filter-row">
        <Filter size={14} />
        <select
          aria-label="Service filter"
          value={filters.service}
          onChange={(e) => update("service", e.target.value)}
        >
          <option value="">All services</option>
          {services(incident.entries).map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
        <select
          aria-label="Level filter"
          value={filters.level}
          onChange={(e) => update("level", e.target.value)}
        >
          <option value="">All levels</option>
          {["ERROR", "FATAL", "WARN", "INFO", "DEBUG", "TRACE"].map((l) => (
            <option key={l}>{l}</option>
          ))}
        </select>
        {filters.trace && (
          <button className="trace-filter" onClick={() => update("trace", "")}>
            Trace {filters.trace}
            <X size={12} />
          </button>
        )}
        <span className="selection-count">
          {checked.length
            ? `${checked.length}/60 selected for AI`
            : "Select records for a focused AI analysis"}
        </span>
        {checked.length > 0 && (
          <button className="text-button" onClick={() => setChecked([])}>
            Clear
          </button>
        )}
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th />
              <th>ID / Time UTC</th>
              <th>Source</th>
              <th>Log message</th>
            </tr>
          </thead>
          <tbody>
            {entries.slice(page * 25, page * 25 + 25).map((e) => (
              <tr
                key={e.id}
                className={selected === e.id ? "selected-row" : ""}
              >
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Include ${e.id}`}
                    checked={checked.includes(e.id)}
                    disabled={!checked.includes(e.id) && checked.length >= 60}
                    onChange={(ev) =>
                      setChecked((ids) =>
                        ev.target.checked
                          ? [...ids, e.id]
                          : ids.filter((id) => id !== e.id),
                      )
                    }
                  />
                </td>
                <td>
                  <button
                    className="evidence-time"
                    onClick={() => onSelect(e.id)}
                  >
                    <b>{e.id}</b>
                    <span>{utc(e.timestamp)}</span>
                  </button>
                </td>
                <td>
                  <span className={"level level-" + e.level}>{e.level}</span>
                  <small>{e.service}</small>
                </td>
                <td>
                  <button
                    className="log-message"
                    onClick={() => onSelect(e.id)}
                  >
                    {e.message}
                  </button>
                  {e.traceId && (
                    <button
                      className="trace-link"
                      onClick={() => update("trace", e.traceId)}
                    >
                      trace:{e.traceId}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!entries.length && (
          <div className="empty-small">No records match these filters.</div>
        )}
      </div>
      <footer className="table-footer">
        <span>
          {entries.length
            ? `${page * 25 + 1}–${Math.min((page + 1) * 25, entries.length)} of ${entries.length}`
            : "0 records"}{" "}
          · {incident.sources.length} imported files
        </span>
        <div>
          <button
            aria-label="Previous evidence page"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            <ChevronLeft size={15} />
          </button>
          <span>
            {page + 1}/{pages}
          </span>
          <button
            aria-label="Next evidence page"
            disabled={page >= pages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            <ChevronRight size={15} />
          </button>
        </div>
      </footer>
    </section>
  );
}
export function SourceInspector({ incident, entry, onClose }) {
  if (!entry) return null;
  const source = incident.sources.find((s) => s.name === entry.fileName),
    lines = source.content.split(/\r\n|[\n\r\v\f\u0085\u2028\u2029]/),
    from = Math.max(0, entry.lineNumber - 3),
    to = Math.min(lines.length, entry.lineNumber + 2);
  return (
    <section className="card source-inspector">
      <div className="section-head">
        <h2>
          <FileText size={16} />
          {entry.id} · {entry.fileName}:{entry.lineNumber}
        </h2>
        <button
          className="icon-button"
          aria-label="Close source inspector"
          onClick={onClose}
        >
          <X size={16} />
        </button>
      </div>
      <div className="source-meta">
        <span>
          {entry.timestamp} · {entry.service} · {entry.level}
        </span>
        <span>Original source context</span>
      </div>
      <div className="source-lines">
        {lines.slice(from, to).map((line, i) => (
          <div
            key={i}
            className={from + i + 1 === entry.lineNumber ? "highlight" : ""}
          >
            <span>{from + i + 1}</span>
            <code>{line || " "}</code>
          </div>
        ))}
      </div>
    </section>
  );
}
