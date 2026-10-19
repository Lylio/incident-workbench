import React from "react";
import { X, Upload, Sparkles, KeyRound, CheckCircle2 } from "lucide-react";
export default function Dialogs({
  dialog,
  setDialog,
  busy,
  error,
  config,
  context,
  question,
  setQuestion,
  onAnalyze,
  title,
  setTitle,
  files,
  setFiles,
  onImport,
  review,
  setReview,
  onReview,
}) {
  if (!dialog) return null;
  return (
    <div className="modal-backdrop">
      <section
        className={"modal " + (dialog === "context" ? "context-modal" : "")}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <button
          className="icon-button modal-close"
          disabled={busy}
          aria-label="Close dialog"
          onClick={() => setDialog(null)}
        >
          <X size={18} />
        </button>
        {dialog === "import" && (
          <>
            <span className="eyebrow">BRING THE EVIDENCE</span>
            <h2 id="modal-title">New investigation</h2>
            <p>
              Import up to six log files. Original source lines are preserved.
            </p>
            <form onSubmit={onImport}>
              <label>
                Investigation title
                <input
                  required
                  maxLength="120"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. Checkout failures after deployment"
                />
              </label>
              <label className="file-drop">
                <Upload size={26} />
                <strong>Select .log, .txt or .jsonl files</strong>
                <span>
                  UTF-8 · 1 MiB each · 3 MiB total · 4,000 nonblank lines
                </span>
                <input
                  aria-label="Log files"
                  type="file"
                  required
                  multiple
                  accept=".log,.txt,.jsonl"
                  onChange={(e) => setFiles([...e.target.files])}
                />
              </label>
              {files.map((f) => (
                <div className="file-item" key={f.name}>
                  {f.name}
                  <span>{(f.size / 1024).toFixed(1)} KiB</span>
                </div>
              ))}
              <p className="form-help">
                Supported text format:
                <br />
                <code>
                  2026-10-18T14:02:23Z ERROR [payment] [trace=req-1] Connection
                  timed out
                </code>
                <br />
                JSONL fields: timestamp, service, level, message, optional
                traceId.
              </p>
              {error && (
                <div className="form-error" role="alert">
                  {error}
                </div>
              )}
              <button
                className="button primary wide"
                disabled={busy}
                type="submit"
              >
                {busy ? "Importing…" : "Import evidence"}
              </button>
            </form>
          </>
        )}
        {dialog === "context" && (
          <>
            <span className="eyebrow">REVIEW WHAT WILL BE SENT</span>
            <h2 id="modal-title">AI evidence preview</h2>
            <p>
              This sends the investigation title, your question and the excerpts
              below to OpenAI. Raw source files and unselected records are not
              sent.
            </p>
            {context ? (
              <>
                <div className="context-meta">
                  <b>
                    {context.entries.length} / {context.totalEntries} records
                  </b>
                  <span>{context.selection}</span>
                </div>
                <div className="context-list">
                  {context.entries.map((e) => (
                    <article key={e.id}>
                      <b>
                        {e.id} · {e.service} · {e.level}
                      </b>
                      <small>
                        {e.timestamp} · {e.fileName}:{e.lineNumber}
                      </small>
                      <p>{e.message}</p>
                    </article>
                  ))}
                </div>
                <label>
                  Focus question (optional)
                  <textarea
                    maxLength="1000"
                    rows="3"
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    placeholder="What might explain the payment timeouts?"
                  />
                </label>
                <p className="form-help">
                  Messages are clipped to 300 characters plus a clipping marker.
                  At most 60 records are sent. AI output is a hypothesis, not a
                  verified cause.
                </p>
                {!config?.aiConfigured && (
                  <div className="form-error">
                    Add OPENAI_API_KEY locally to enable analysis. You can close
                    this dialog and continue exploring the evidence.
                  </div>
                )}
                {error && (
                  <div className="form-error" role="alert">
                    {error}
                  </div>
                )}
                <button
                  className="button primary wide"
                  disabled={busy || !config?.aiConfigured}
                  onClick={onAnalyze}
                >
                  {busy ? (
                    <>
                      <span className="spinner" />
                      Analyzing evidence…
                    </>
                  ) : (
                    <>
                      <Sparkles size={16} />
                      Send evidence to OpenAI
                    </>
                  )}
                </button>
              </>
            ) : (
              <p role={error ? "alert" : "status"}>
                {error || "Preparing evidence…"}
              </p>
            )}
          </>
        )}
        {dialog === "settings" && (
          <>
            <span className="eyebrow">LOCAL CONFIGURATION</span>
            <h2 id="modal-title">Enable AI analysis</h2>
            <p>
              The evidence workspace runs without a key. To enable real Spring
              AI/OpenAI analysis:
            </p>
            <ol>
              <li>
                Open the project's local <code>.env</code> file.
              </li>
              <li>
                Set <code>OPENAI_API_KEY=your-key</code>.
              </li>
              <li>
                Keep <code>OPENAI_MODEL=gpt-4.1-mini</code>, or select a
                compatible Chat Completions model available to your API account.
              </li>
              <li>
                Recreate the backend:
                <pre>docker compose up -d --no-deps backend</pre>
              </li>
              <li>Refresh this page.</li>
            </ol>
            <p className="form-help">
              Keep your key local; .env is Git-ignored. API billing is separate
              from ChatGPT. Only click Send evidence for logs you intend to
              share with OpenAI. Do not paste your key into this app or chat.
            </p>
          </>
        )}
        {dialog === "review" && (
          <>
            <span className="eyebrow">YOUR ENGINEERING JUDGEMENT</span>
            <h2 id="modal-title">
              {review.status === "ACCEPTED" ? "Accept" : "Reject"} hypothesis
            </h2>
            <p>
              This records your review decision. It does not prove the proposed
              cause.
            </p>
            <label>
              Review notes
              <textarea
                value={review.note}
                maxLength="2000"
                rows="5"
                onChange={(e) =>
                  setReview((v) => ({ ...v, note: e.target.value }))
                }
                placeholder="Why do you accept or reject this explanation?"
              />
            </label>
            {error && (
              <div className="form-error" role="alert">
                {error}
              </div>
            )}
            <button
              className="button primary wide"
              disabled={busy}
              onClick={onReview}
            >
              <CheckCircle2 size={16} />
              {busy ? "Saving…" : "Save review decision"}
            </button>
          </>
        )}
      </section>
    </div>
  );
}
