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
```
