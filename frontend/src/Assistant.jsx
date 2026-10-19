import React from "react";
import {
  Sparkles,
  ExternalLink,
  CheckCircle2,
  XCircle,
  FileSearch,
  ArrowUpRight,
  KeyRound,
} from "lucide-react";
import { latestReview, observedPatterns } from "./evidence";
export default function Assistant({
  view,
  config,
  analysis,
  setAnalysis,
  onAnalyze,
  onReview,
  onEvidence,
  busy,
  onSettings,
}) {
  const patterns = observedPatterns(view.incident.entries);
  return (
    <aside className="assistant card">
      <div className="section-head">
        <h2>
          <Sparkles size={18} />
          Investigation assistant
        </h2>
        <span
          className={
            "ai-indicator " + (config?.aiConfigured ? "configured" : "")
          }
        >
          {config?.aiConfigured ? "CONFIGURED" : "OPTIONAL"}
        </span>
      </div>
      <div className="assistant-body">
        {view.analyses.length > 0 && (
          <label className="analysis-select">
            Saved analysis
            <select
              aria-label="Saved analysis"
              value={analysis?.id || ""}
              onChange={(e) =>
                setAnalysis(view.analyses.find((a) => a.id === e.target.value))
              }
            >
              {view.analyses.map((a) => (
                <option key={a.id} value={a.id}>
                  {new Date(a.createdAt).toLocaleString()}
                </option>
              ))}
            </select>
          </label>
        )}
        {analysis ? (
          <>
            <div className="analysis-summary">
              <span className="eyebrow">
                AI INVESTIGATION · {analysis.model}
              </span>
              <p>{analysis.draft.summary}</p>
              <small>
                {analysis.context.entries.length} of{" "}
                {analysis.context.totalEntries} evidence records used
              </small>
            </div>
            {analysis.draft.hypotheses.map((h, i) => {
              const review = latestReview(view.reviews, analysis.id, i);
              return (
                <article className="hypothesis" key={i}>
                  <div className="hypothesis-heading">
                    <span>Hypothesis {i + 1}</span>
                    <span className="unconfirmed">
                      AI suggestion · Unconfirmed
                    </span>
                  </div>
                  <h3>{h.title}</h3>
                  <p>{h.reasoning}</p>
                  <div className="supporting">
                    Supporting evidence
                    {h.evidenceIds.map((id) => (
                      <button key={id} onClick={() => onEvidence(id)}>
                        <FileSearch size={14} />
                        {id}
                        <ArrowUpRight size={12} />
                      </button>
                    ))}
                  </div>
                  {review && (
                    <div
                      className={"review-status " + review.status.toLowerCase()}
                    >
                      <strong>
                        Human review: {review.status.toLowerCase()}
                      </strong>
                      {review.note && <p>{review.note}</p>}
                    </div>
                  )}
                  <div className="review-actions">
                    <button
                      disabled={busy}
                      onClick={() => onReview(i, "ACCEPTED")}
                    >
                      <CheckCircle2 size={14} />
                      Accept
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => onReview(i, "REJECTED")}
                    >
                      <XCircle size={14} />
                      Reject
                    </button>
                  </div>
                </article>
              );
            })}
            <section className="next-checks">
              <h3>Suggested next checks</h3>
              {analysis.draft.nextChecks.map((s, i) => (
                <p key={i}>
                  <span>{i + 1}</span>
                  {s}
                </p>
              ))}
            </section>
            <details className="limitations">
              <summary>Limitations & evidence coverage</summary>
              {analysis.draft.limitations.map((l, i) => (
                <p key={i}>{l}</p>
              ))}
              <p>
                Citation IDs are validated; this does not establish that a
                hypothesis is correct. Human acceptance records a review
                decision, not a proven root cause.
              </p>
            </details>
          </>
        ) : (
          <>
            <div className="assistant-empty">
              <div className="sparkle-disc">
                <Sparkles size={28} />
              </div>
              <h3>Turn evidence into questions.</h3>
              <p>
                Build a timeline, inspect the logs, then ask AI for hypotheses
                linked to the evidence you choose.
              </p>
              {!config?.aiConfigured && (
                <div className="key-note">
                  <KeyRound size={16} />
                  <span>
                    No API key configured. Import, timeline, filtering and
                    exports work now.
                  </span>
                </div>
              )}
              <button
                className="button primary wide"
                disabled={busy}
                onClick={onAnalyze}
              >
                <Sparkles size={15} />
                Preview AI evidence
              </button>
              {!config?.aiConfigured && (
                <button className="text-button" onClick={onSettings}>
                  How to enable OpenAI
                </button>
              )}
            </div>
            <section className="patterns">
              <span className="eyebrow">OBSERVED PATTERNS · NO AI USED</span>
              <h3>Errors in the imported logs</h3>
              {patterns.length ? (
                patterns.map((g, i) => (
                  <article key={i}>
                    <span className="count">{g.ids.length}×</span>
                    <div>
                      <strong>{g.service}</strong>
                      <p>{g.message}</p>
                      <button
                        className="text-button"
                        onClick={() => onEvidence(g.ids[0])}
                      >
                        Inspect {g.ids[0]} <ArrowUpRight size={12} />
                      </button>
                    </div>
                  </article>
                ))
              ) : (
                <p>
                  No ERROR or FATAL records were imported. That does not prove
                  the system was healthy.
                </p>
              )}
            </section>
          </>
        )}
        {analysis && (
          <button
            className="button primary wide"
            disabled={busy}
            onClick={onAnalyze}
          >
            <Sparkles size={15} />
            Analyze another evidence selection
          </button>
        )}
      </div>
    </aside>
  );
}
