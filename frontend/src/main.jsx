import React, { useState, useEffect, useMemo } from "react";
import { createRoot } from "react-dom/client";
import {
  TriangleAlert,
  Search,
  Files,
  FileText,
  Plus,
  Download,
  Sparkles,
  ChevronRight,
  Activity,
  Clock,
  Upload,
  Play,
  X,
  CheckCircle2,
  KeyRound,
  AlertCircle,
  Filter,
} from "lucide-react";
import { api } from "./api";
import { COLORS, services, filtered } from "./evidence";
import Timeline from "./Timeline";
import Evidence, { SourceInspector } from "./Evidence";
import Assistant from "./Assistant";
import Dialogs from "./Dialogs";
import "./style.css";
const emptyFilters = { query: "", service: "", level: "", trace: "" };
function App() {
  const [config, setConfig] = useState(null),
    [recent, setRecent] = useState([]),
    [view, setView] = useState(null),
    [page, setPage] = useState("Investigations"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [dialog, setDialog] = useState(null),
    [filters, setFilters] = useState(emptyFilters),
    [selected, setSelected] = useState(""),
    [checked, setChecked] = useState([]),
    [analysis, setAnalysis] = useState(null),
    [context, setContext] = useState(null),
    [question, setQuestion] = useState(""),
    [title, setTitle] = useState(""),
    [files, setFiles] = useState([]),
    [review, setReview] = useState({
      status: "ACCEPTED",
      hypothesisIndex: 0,
      note: "",
    }),
    [source, setSource] = useState("");
  async function refresh() {
    const [c, r] = await Promise.all([api("/config"), api("/incidents")]);
    setConfig(c);
    setRecent(r);
    return r;
  }
  function display(v, reset = true) {
    setView(v);
    setAnalysis(v.analyses[0] || null);
    if (reset) {
      setSelected("");
      setChecked([]);
      setFilters(emptyFilters);
      setPage("Investigations");
      setSource(v.incident.sources[0]?.name || "");
    }
  }
  useEffect(() => {
    refresh()
      .then(async (r) => {
        if (r.length) display(await api("/incidents/" + r[0].id));
      })
      .catch(() =>
        setError(
          "The API is not ready. Wait for the backend, then retry connection.",
        ),
      );
  }, []);
  async function load(id) {
    setBusy(true);
    setError("");
    try {
      display(await api("/incidents/" + id));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function sample() {
    setBusy(true);
    setError("");
    try {
      display(await api("/incidents/sample", { method: "POST" }));
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function importFiles(e) {
    e.preventDefault();
    if (
      files.length > 6 ||
      files.reduce((sum, f) => sum + f.size, 0) > 3 * 1024 * 1024 ||
      files.some((f) => f.size > 1024 * 1024)
    ) {
      setError("Use at most 6 files, 1 MiB each and 3 MiB total.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const body = new FormData();
      body.append("title", title);
      files.forEach((f) => body.append("files", f));
      display(await api("/incidents", { method: "POST", body }));
      setDialog(null);
      setFiles([]);
      setTitle("");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function preview() {
    setError("");
    setBusy(true);
    setContext(null);
    setDialog("context");
    try {
      setContext(
        await api("/incidents/" + view.incident.id + "/context", {
          method: "POST",
          body: JSON.stringify({ evidenceIds: checked }),
        }),
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function analyze() {
    setBusy(true);
    setError("");
    try {
      await api("/incidents/" + view.incident.id + "/analyses", {
        method: "POST",
        body: JSON.stringify({ evidenceIds: checked, question }),
      });
      display(await api("/incidents/" + view.incident.id), false);
      setDialog(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function inspect(id) {
    setFilters(emptyFilters);
    setSelected(id);
    setPage("Investigations");
    setTimeout(
      () =>
        document
          .getElementById("evidence-section")
          ?.scrollIntoView({ behavior: "smooth", block: "start" }),
      30,
    );
  }
  async function saveReview() {
    setBusy(true);
    setError("");
    try {
      await api("/incidents/" + view.incident.id + "/reviews", {
        method: "POST",
        body: JSON.stringify({ ...review, analysisId: analysis.id }),
      });
      const v = await api("/incidents/" + view.incident.id);
      setView(v);
      setDialog(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function changeStatus(status) {
    setBusy(true);
    setError("");
    try {
      setView(
        await api("/incidents/" + view.incident.id + "/status", {
          method: "PUT",
          body: JSON.stringify({ status }),
        }),
      );
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const entries = useMemo(
    () => (view ? filtered(view.incident.entries, filters) : []),
    [view, filters],
  );
  const laneNames = view ? services(view.incident.entries) : [];
  const focused = view?.incident.entries.find((e) => e.id === selected);
  const errorCount =
    view?.incident.entries.filter((e) => ["ERROR", "FATAL"].includes(e.level))
      .length || 0;
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <TriangleAlert size={28} />
          <span>
            Incident<span>Workbench</span>
          </span>
        </div>
        <div className="nav-caption">INVESTIGATION WORKSPACE</div>
        <nav>
          {[
            ["Investigations", Search],
            ["Evidence library", Files],
            ["Saved reports", FileText],
          ].map(([name, Icon]) => (
            <button
              key={name}
              className={page === name ? "active" : ""}
              onClick={() => setPage(name)}
            >
              <Icon size={18} />
              {name}
            </button>
          ))}
        </nav>
        <div className="archive-heading">
          RECENT INVESTIGATIONS <span>{recent.length}</span>
        </div>
        <div className="incident-nav">
          {recent.map((i) => (
            <button
              key={i.id}
              disabled={busy}
              className={view?.incident.id === i.id ? "selected" : ""}
              onClick={() => load(i.id)}
            >
              <i className={i.status === "RESOLVED" ? "resolved" : ""} />
              <span>
                {i.title}
                <small>
                  {i.entries} records · {i.services} services
                </small>
              </span>
            </button>
          ))}
          {!recent.length && <p>Your imported incidents will appear here.</p>}
        </div>
        <div className="sidebar-bottom">
          <span>
            <i />
            LOCAL WORKSPACE
          </span>
          <small>
            Evidence first.
            <br />
            Hypotheses second.
          </small>
          <button onClick={() => setDialog("settings")}>
            <KeyRound size={13} />
            {config?.aiConfigured ? "OpenAI configured" : "Configure AI"}
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            WORKSPACE <ChevronRight size={12} />
            {page}
          </span>
          <div>
            <span className="local-badge">LOCAL DEVELOPMENT</span>
            <button
              className="button subtle"
              disabled={!view}
              onClick={() => {
                setPage("Saved reports");
              }}
            >
              <Download size={15} />
              Export report
            </button>
            <button
              className="button primary"
              disabled={busy}
              onClick={() => {
                setError("");
                setDialog("import");
              }}
            >
              <Plus size={17} />
              New investigation
            </button>
          </div>
        </header>
        <main>
          {error && !dialog && (
            <div role="alert" className="error-banner">
              <AlertCircle size={16} />
              {error}
              <button
                onClick={() =>
                  refresh()
                    .then(() => setError(""))
                    .catch((e) => setError(e.message))
                }
              >
                Retry connection
              </button>
              <button aria-label="Dismiss error" onClick={() => setError("")}>
                <X size={16} />
              </button>
            </div>
          )}
          {!view ? (
            <section className="welcome card">
              <div className="welcome-mark">
                <Activity size={45} />
              </div>
              <span className="eyebrow">MAKE SENSE OF THE FAILURE</span>
              <h1>Every incident leaves a trail.</h1>
              <p>
                Bring your logs together. Reconstruct the timeline.
                <br />
                Investigate with evidence-linked AI hypotheses.
              </p>
              <div>
                <button
                  className="button primary"
                  disabled={busy}
                  onClick={sample}
                >
                  <Play size={16} />
                  {busy ? "Importing…" : "Explore sample incident"}
                </button>
                <button
                  className="button subtle"
                  disabled={busy}
                  onClick={() => setDialog("import")}
                >
                  <Upload size={16} />
                  Import your logs
                </button>
              </div>
              <small>
                The sample uses real log parsing and persistence. No API key
                needed to explore.
              </small>
            </section>
          ) : (
            <>
              <div className="incident-heading">
                <div>
                  <div className="eyebrow">
                    {page === "Investigations"
                      ? "FOLLOW THE EVIDENCE"
                      : page.toUpperCase()}
                  </div>
                  <h1>{view.incident.title}</h1>
                  <div className="incident-meta">
                    <select
                      aria-label="Investigation status"
                      disabled={busy}
                      value={view.status}
                      onChange={(e) => changeStatus(e.target.value)}
                    >
                      <option value="OPEN">Investigating</option>
                      <option value="RESOLVED">Resolved</option>
                    </select>
                    <span>
                      INC-{view.incident.id.slice(0, 6).toUpperCase()}
                    </span>
                    <span>
                      Imported{" "}
                      {new Date(view.incident.createdAt).toLocaleString()}
                    </span>
                  </div>
                </div>
                <button
                  className="button subtle"
                  disabled={busy}
                  onClick={sample}
                >
                  <Play size={14} />
                  Load sample
                </button>
              </div>
              <div className="evidence-summary">
                <div className="service-chips">
                  <span>Observed services:</span>
                  {laneNames.map((s, i) => (
                    <button
                      key={s}
                      className={filters.service === s ? "chosen" : ""}
                      onClick={() =>
                        setFilters((f) => ({
                          ...f,
                          service: f.service === s ? "" : s,
                        }))
                      }
                    >
                      <i style={{ background: COLORS[i] }} />
                      {s}
                    </button>
                  ))}
                </div>
                <span>
                  <Files size={14} />
                  {view.incident.sources.length} log files
                </span>
                <span>
                  <AlertCircle size={14} />
                  {errorCount} errors
                </span>
                <span>
                  <Clock size={14} />
                  {view.incident.entries.length} records
                </span>
              </div>
              {view.incident.rejectedLines > 0 && (
                <div className="partial-banner">
                  Partial import: {view.incident.rejectedLines} nonblank lines
                  were not parsed. Inspect diagnostics in Evidence library.
                  These lines are retained in source files but excluded from
                  timeline and AI evidence.
                </div>
              )}
              {page === "Investigations" && (
                <div className="investigation-grid">
                  <div className="evidence-workspace">
                    <Timeline
                      entries={entries}
                      allEntries={view.incident.entries}
                      selected={selected}
                      onSelect={inspect}
                    />
                    <div id="evidence-section">
                      <Evidence
                        incident={view.incident}
                        entries={entries}
                        filters={filters}
                        setFilters={setFilters}
                        selected={selected}
                        onSelect={setSelected}
                        checked={checked}
                        setChecked={setChecked}
                      />
                    </div>
                    <SourceInspector
                      incident={view.incident}
                      entry={focused}
                      onClose={() => setSelected("")}
                    />
                    <div className="workspace-footnote">
                      Timestamps normalized to UTC · Clock skew is not corrected
                      · Evidence is immutable after import
                    </div>
                  </div>
                  <Assistant
                    view={view}
                    config={config}
                    analysis={analysis}
                    setAnalysis={setAnalysis}
                    onAnalyze={preview}
                    onReview={(index, status) => {
                      setReview({ hypothesisIndex: index, status, note: "" });
                      setError("");
                      setDialog("review");
                    }}
                    onEvidence={inspect}
                    busy={busy}
                    onSettings={() => setDialog("settings")}
                  />
                </div>
              )}
              {page === "Evidence library" && (
                <div className="library-grid">
                  <section className="card">
                    <div className="section-head">
                      <h2>Imported source files</h2>
                      <a
                        href={
                          "/api/incidents/" + view.incident.id + "/evidence.csv"
                        }
                      >
                        <Download size={14} />
                        CSV
                      </a>
                    </div>
                    {view.incident.sources.map((s) => (
                      <button
                        className={
                          "source-file " + (source === s.name ? "active" : "")
                        }
                        key={s.name}
                        onClick={() => setSource(s.name)}
                      >
                        <FileText size={20} />
                        <span>
                          <b>{s.name}</b>
                          <small>
                            {s.parsedLines} parsed · {s.rejectedLines} rejected
                            · {s.totalLines} nonblank lines
                          </small>
                        </span>
                      </button>
                    ))}
                    <div className="diagnostics">
                      <h3>Import diagnostics</h3>
                      {view.incident.diagnostics.length ? (
                        view.incident.diagnostics.map((d, i) => (
                          <p key={i}>
                            <b>
                              {d.fileName}:{d.lineNumber}
                            </b>
                            {d.reason}
                          </p>
                        ))
                      ) : (
                        <p>Every nonblank line was parsed.</p>
                      )}
                      {view.incident.rejectedLines > 100 && (
                        <p>Only the first 100 diagnostics are displayed.</p>
                      )}
                    </div>
                  </section>
                  <section className="card source-library">
                    <div className="section-head">
                      <h2>{source}</h2>
                      <span>Original UTF-8 source</span>
                    </div>
                    <pre>
                      {
                        view.incident.sources.find((s) => s.name === source)
                          ?.content
                      }
                    </pre>
                  </section>
                </div>
              )}
              {page === "Saved reports" && (
                <section className="card reports">
                  <div className="section-head">
                    <h2>Investigation exports</h2>
                    <span>Latest 10 AI analyses included</span>
                  </div>
                  <div className="export-grid">
                    {[
                      [
                        "report.md",
                        "Markdown report",
                        "Evidence register, AI hypotheses and latest human review decisions",
                      ],
                      [
                        "export.json",
                        "JSON snapshot",
                        "Original source files, parsed records, saved analyses and review history",
                      ],
                      [
                        "evidence.csv",
                        "Evidence CSV",
                        "Parsed records with timestamps, trace IDs and source locations",
                      ],
                    ].map(([path, name, desc]) => (
                      <a
                        key={path}
                        href={"/api/incidents/" + view.incident.id + "/" + path}
                      >
                        <FileText size={27} />
                        <h3>{name}</h3>
                        <p>{desc}</p>
                        <span>
                          Download <Download size={14} />
                        </span>
                      </a>
                    ))}
                  </div>
                  <div className="report-history">
                    <h3>
                      Saved AI analyses <span>{view.analyses.length}</span>
                    </h3>
                    {view.analyses.length ? (
                      view.analyses.map((a) => (
                        <button
                          key={a.id}
                          onClick={() => {
                            setAnalysis(a);
                            setPage("Investigations");
                          }}
                        >
                          <Sparkles size={18} />
                          <span>
                            {new Date(a.createdAt).toLocaleString()}
                            <small>
                              {a.model} · {a.context.entries.length} evidence
                              excerpts
                            </small>
                          </span>
                          <ChevronRight size={16} />
                        </button>
                      ))
                    ) : (
                      <p>
                        No AI analysis has been saved. Evidence-only exports are
                        available now.
                      </p>
                    )}
                    <details>
                      <summary>
                        Review audit history ({view.reviews.length})
                      </summary>
                      {view.reviews.map((r) => (
                        <article key={r.id}>
                          <b>
                            {r.status} · hypothesis {r.hypothesisIndex + 1}
                          </b>
                          <small>
                            {new Date(r.createdAt).toLocaleString()} · analysis{" "}
                            {r.analysisId.slice(0, 8)}
                          </small>
                          <p>{r.note || "No review note."}</p>
                        </article>
                      ))}
                    </details>
                  </div>
                </section>
              )}
            </>
          )}
        </main>
      </div>
      <Dialogs
        dialog={dialog}
        setDialog={setDialog}
        busy={busy}
        error={error}
        config={config}
        context={context}
        question={question}
        setQuestion={setQuestion}
        onAnalyze={analyze}
        title={title}
        setTitle={setTitle}
        files={files}
        setFiles={setFiles}
        onImport={importFiles}
        review={review}
        setReview={setReview}
        onReview={saveReview}
      />
    </div>
  );
}
createRoot(document.getElementById("root")).render(<App />);
