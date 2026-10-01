export type AgentApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AgentApproval {
  id: number
  runId: number
  stepId: number
  actionType: string
  actionSummary: string
  actionPayload: string
  status: AgentApprovalStatus
  createdAt: string
  decidedAt: string | null
  decisionReason: string | null
}
