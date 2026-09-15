export type AgentTaskStatus = 'CREATED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AgentTask {
  id: number
  title: string
  status: AgentTaskStatus
}
