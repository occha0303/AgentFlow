# AgentFlow

Full-stack AI task-execution platform built with Spring Boot, Vue 3 and distributed task execution.

## Project structure

- `frontend/` — Vue 3 + TypeScript application
- `backend/` — Spring Boot application
- `docs/` — project documentation

## Current status

AgentTask, asynchronous AgentRun, RocketMQ, Execution Trace and Spring AI LLM execution are implemented incrementally.

## Local AI configuration

Copy `.env.example` as a local reference only; do not commit `.env`. Before starting the backend in PowerShell, set an OpenAI-compatible endpoint, key and model:

```powershell
$env:AI_BASE_URL = "https://api.openai.com"
$env:AI_API_KEY = "your-api-key"
$env:AI_MODEL = "your-model"
$env:SEARCH_API_KEY = "your-tavily-api-key"
# Optional. Defaults to https://api.tavily.com
$env:SEARCH_API_BASE_URL = "https://api.tavily.com"
$env:EMBEDDING_BASE_URL = "https://api.openai.com"
$env:EMBEDDING_API_KEY = "your-embedding-api-key"
$env:EMBEDDING_MODEL = "text-embedding-3-small"
```

The WebSearchTool uses Tavily's public Search REST API. UrlReaderTool reads only public `http` and `https`
pages; it blocks local and private-network addresses, validates every redirect target, and limits returned text.

## Local RAG configuration

MySQL remains the business database. PGvector stores only document chunks, metadata, and embeddings for the
knowledge base. Start the local databases with `docker compose up -d`; the default PGvector connection targets
`jdbc:postgresql://localhost:5432/agentflow_rag`. Configure an embedding-capable OpenAI-compatible provider with
the `EMBEDDING_*` variables above. A chat endpoint that does not implement embeddings cannot index documents.

## Local run reliability

`docker compose up -d redis` starts the local Redis execution-lock service on `127.0.0.1:6379`.
It has no persistence volume: MySQL remains the source of truth for runs, steps and approvals.
The backend defaults to an unauthenticated local Redis; `REDIS_HOST`, `REDIS_PORT`, and
`REDIS_PASSWORD` can point it at a protected instance. `AGENT_RUN_LOCK_TTL_SECONDS` defaults to
900 and `AGENT_RUN_STALE_SECONDS` defaults to 600. A scheduler checks stale `RUNNING` runs
every 60 seconds, but never times out `WAITING_APPROVAL`.

`POST /api/runs/{runId}/retry` accepts only a `FAILED` run, creates a new `QUEUED` run with
`retryOfRunId`, and returns HTTP 202. The old run and its approvals remain unchanged; any
new side-effect proposal requires a new approval. A timeout is not automatically retried
because an external browser action may have succeeded just before the worker stopped.

## Run live updates (SSE v1)

`GET /api/runs/{runId}/events` streams a database-backed `snapshot` followed by
`run-status`, `step-update`, `approval-update`, `result-ready`, and `error` events.
The Vue page uses `EventSource`; after repeated connection failures, it falls back
to GET requests every five seconds until the SSE connection recovers. Existing GET
endpoints remain available. Browser action payloads are not sent over SSE; the UI
fetches approval details only when an approval event or snapshot requires them.

Manual QA when Java 21, MySQL, RocketMQ, Redis, PGvector and AI credentials are available:

1. Run a normal LLM task: observe QUEUED, RUNNING, Step updates and COMPLETED without one-second polling.
2. Prepare a browser write: observe WAITING_APPROVAL and the approval card; approve and observe BROWSER_ACTION and COMPLETED.
3. Reject an approval: observe FAILED and the final error immediately.
4. Disconnect/reconnect the browser network: observe Reconnecting, low-frequency fallback, then a snapshot restoring the stored state.
5. Retry a FAILED run: confirm the old stream closes, a new runId is shown, and its Trace contains only new steps.

SSE keeps only connections in this Spring Boot process; MySQL remains the source of
truth. Multiple backend instances would need a cross-instance event bus and shared
authentication/authorization before this design could provide reliable user isolation.
