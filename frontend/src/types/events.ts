import type { AgentRunStatus } from './run'
import type { AgentStep } from './step'

export interface RunSnapshot {
  runId: number
  runStatus: AgentRunStatus
  resultText: string | null
  errorMessage: string | null
  retryOfRunId: number | null
  steps: AgentStep[]
  approvals: Array<{ approvalId: number; runId: number; status: string }>
}

export interface RunStatusEvent {
  runId: number
  status: AgentRunStatus
}

export interface ResultEvent extends RunStatusEvent {
  resultText: string | null
  errorMessage: string | null
}
