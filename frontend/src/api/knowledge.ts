import { apiClient } from './task'
import type { KnowledgeUploadResult } from '../types/knowledge'

export async function uploadKnowledgeFile(file: File): Promise<KnowledgeUploadResult> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await apiClient.post<KnowledgeUploadResult>('/api/knowledge/files', formData)
  return response.data
}
