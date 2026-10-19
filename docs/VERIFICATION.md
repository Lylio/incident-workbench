# Executed verification — V1

## Automated checks

- **20 Java tests passed**, zero failures/errors/skips, followed by Maven executable JAR packaging:
  - 11 importer/context/export tests: mixed formats, UTC normalization, exact source locations, stable equal-time order, partial coverage, UTF-8/BOM/CRLF, filename/size/line bounds, missing offsets, excerpt limits, invalid citations and CSV escaping.
  - 7 Spring Boot API tests: sample parsing, multipart/partial import, persistence, JSON/CSV/Markdown exports, status changes, analysis context, append-only reviews, cross-incident isolation, missing-key behavior, provider failures and invalid response rejection.
  - 1 admission test: concurrent analysis receives 429; the permit is released after completion.
  - 1 real Spring AI adapter test: an actual ChatClient sends a Chat Completions request to a local loopback HTTP server and converts a structured fixture response to typed Java records. It checks the model, bounded output setting and presence of evidence/system instructions.
- **4 frontend tests passed** for combined filters, observed-error grouping, review scoping and stable service ordering.
- **Vite production build passed** with the committed lockfile.

## Browser workflows

Chromium exercised the real running Spring Boot API with file-backed H2 in PostgreSQL compatibility mode.

Without an API key:

- Parsed and saved the sample: 34 records, 3 source files/services, 6 ERROR records and zero rejected lines.
- Searched `SocketTimeoutException` (3 matches), filtered ERROR records (6), and opened original source context.
- Selected a single evidence ID, previewed exactly that excerpt and confirmed the AI send action was disabled without configuration.
- Opened the source library and downloaded a Markdown evidence report.
- Uploaded a partial log, confirmed rejection diagnostics, changed status to RESOLVED and retained that state after refresh.

With a **clearly labelled local test analyst**, not a live model:

- Saved a typed analysis through the real service, validation and persistence path.
- Followed its evidence citation to original source.
- Accepted then rejected a hypothesis with notes; both reviews remained in the audit history and the latest decision survived refresh.
- Downloaded a report containing the saved analysis and latest review.

Both paths had zero browser runtime errors and no horizontal document overflow at 390px mobile width. Desktop and mobile screenshots were inspected during implementation. The final evidence panel uses its own vertical scroll area to keep the timeline and assistant in view.

The `BrowserFixture` class is in test sources only. It labels every fixture output as a test and is excluded from the production JAR/Docker runtime. Production contains no fake-AI mode or canned analysis response.

## Persistence and startup checks

- Stopping and starting the Java process preserved source evidence, a saved analysis and two review decisions in file-backed H2.
- First-launch port selection chose a fallback when the default UI port was occupied.
- Existing `.env` content was preserved unchanged.
- `bash -n start.sh` passed.
- Backend resource samples match the separately supplied `samples/` files.

## Not executed here

Docker Engine was unavailable, so the actual Docker image builds, Nginx container and PostgreSQL Compose launch still need a target-machine smoke test. H2 tests do not establish PostgreSQL runtime behavior.

No real OpenAI API key was supplied and no paid model call was made. The Spring AI transport/conversion path was tested against a local HTTP fixture; actual account authorization, model access, billing, live model output quality and provider latency remain to be checked with the user's account. Invalid live output is rejected rather than displayed as a successful saved result.

Suggested smoke check: run `bash start.sh`, import the sample, inspect sources, export a report, then run `docker compose restart backend` and confirm the incident persists. Configure the local API key, preview a small evidence selection, generate an analysis, inspect every citation and save a review decision.
