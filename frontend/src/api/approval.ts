import { apiClient } from './task'
import type { AgentApproval } from '../types/approval'

export async function getRunApprovals(runId: number): Promise<AgentApproval[]> {
  const response = await apiClient.get<AgentApproval[]>(`/api/runs/${runId}/approvals`)
  return response.data
}

export async function approveAction(approvalId: number): Promise<AgentApproval> {
  const response = await apiClient.post<AgentApproval>(`/api/approvals/${approvalId}/approve`)
  return response.data
}

export async function rejectAction(approvalId: number, reason: string): Promise<AgentApproval> {
  const response = await apiClient.post<AgentApproval>(`/api/approvals/${approvalId}/reject`, { reason })
  return response.data
}
