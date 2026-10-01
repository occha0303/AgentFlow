<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadKnowledgeFile } from './api/knowledge'
import { createTask, getTasks, runTask } from './api/task'
import { getRun, getRunSteps, retryRun } from './api/run'
import { approveAction, getRunApprovals, rejectAction } from './api/approval'
import type { AgentApproval } from './types/approval'
import type { AgentTask, AgentTaskStatus } from './types/task'
import type { AgentRunStatus } from './types/run'
import type { AgentStep, AgentStepStatus } from './types/step'

const tasks = ref<AgentTask[]>([])
const title = ref('')
const loading = ref(false)
const creating = ref(false)
const runningTaskId = ref<number | null>(null)
const pollTimers = new Map<number, number>()
const runStatusByTaskId = ref<Record<number, AgentRunStatus>>({})
const activeRunTaskIds = ref<number[]>([])
const traceRunId = ref<number | null>(null)
const traceSteps = ref<AgentStep[]>([])
const traceRunStatus = ref<AgentRunStatus | null>(null)
const traceResultText = ref<string | null>(null)
const traceErrorMessage = ref<string | null>(null)
const traceRetryOfRunId = ref<number | null>(null)
const retryingRun = ref(false)
const traceApprovals = ref<AgentApproval[]>([])
const decisionReason = ref('')
const decidingApproval = ref(false)
const uploadingKnowledge = ref(false)
const knowledgeUploadMessage = ref('')
const activeRunStorageKey = 'agentflow.activeRun'

function isTerminalStatus(status: AgentRunStatus) {
  return status === 'COMPLETED' || status === 'FAILED'
}

function pendingApproval(): AgentApproval | undefined {
  return traceApprovals.value.find((approval) => approval.status === 'PENDING')
}

function approvalField(approval: AgentApproval, field: 'target' | 'content' | 'url' | 'value' | 'browserAction'): string {
  try {
    const payload = JSON.parse(approval.actionPayload) as Record<string, unknown>
    return typeof payload[field] === 'string' ? payload[field] : ''
  } catch {
    return ''
  }
}

function browserTarget(approval: AgentApproval): string {
  try {
    const payload = JSON.parse(approval.actionPayload) as { target?: { tag?: string; text?: string; ariaLabel?: string; ref?: string } }
    const target = payload.target
    return target ? [target.ref, target.tag, target.text || target.ariaLabel].filter(Boolean).join(' · ') : ''
  } catch {
    return ''
  }
}

function isTaskRunActive(taskId: number) {
  return activeRunTaskIds.value.includes(taskId)
}

function statusTagType(status: AgentTaskStatus): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'CREATED':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
  }
}

function runStatusTagType(status: AgentRunStatus): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'WAITING_APPROVAL':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
  }
}

function stepStatusTagType(status: AgentStepStatus): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'PENDING':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'WAITING_APPROVAL':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
  }
}

function stepMarker(status: AgentStepStatus) {
  if (status === 'COMPLETED') {
    return '✓'
  }

  if (status === 'FAILED') {
    return '✕'
  }

  return '●'
}

async function loadTasks() {
  loading.value = true

  try {
    tasks.value = await getTasks()
  } catch {
    ElMessage.error('无法加载任务列表，请确认后端正在运行。')
  } finally {
    loading.value = false
  }
}

function stopRunPolling(taskId: number, runId: number) {
  const timer = pollTimers.get(runId)

  if (timer !== undefined) {
    window.clearInterval(timer)
    pollTimers.delete(runId)
  }

  activeRunTaskIds.value = activeRunTaskIds.value.filter((activeTaskId) => activeTaskId !== taskId)
}

function stopAllTaskPolling() {
  pollTimers.forEach((timer) => window.clearInterval(timer))
  pollTimers.clear()
}

async function pollRunStatus(taskId: number, runId: number) {
  try {
    const [run, steps] = await Promise.all([getRun(runId), getRunSteps(runId)])
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [taskId]: run.status }

    if (traceRunId.value === runId) {
      traceSteps.value = steps
      traceRunStatus.value = run.status
      traceResultText.value = run.resultText
      traceErrorMessage.value = run.errorMessage
      traceRetryOfRunId.value = run.retryOfRunId
      if (run.status === 'WAITING_APPROVAL') {
        traceApprovals.value = await getRunApprovals(runId)
      }
    }

    if (!isTerminalStatus(run.status)) {
      return
    }

    stopRunPolling(taskId, runId)

    if (run.status === 'COMPLETED') {
      ElMessage.success('任务执行完成。')
    } else {
      ElMessage.error(run.errorMessage || '任务执行失败。')
    }
  } catch {
    stopRunPolling(taskId, runId)
    ElMessage.error('任务状态刷新失败，请刷新任务列表后重试。')
  }
}

async function handleApprove(approval: AgentApproval) {
  decidingApproval.value = true
  try {
    const decided = await approveAction(approval.id)
    traceApprovals.value = traceApprovals.value.map((item) => item.id === decided.id ? decided : item)
    ElMessage.success('已批准，后台将恢复执行。')
  } catch {
    ElMessage.error('批准失败；该审批可能已被处理。')
  } finally {
    decidingApproval.value = false
  }
}

async function handleReject(approval: AgentApproval) {
  decidingApproval.value = true
  try {
    const decided = await rejectAction(approval.id, decisionReason.value.trim())
    traceApprovals.value = traceApprovals.value.map((item) => item.id === decided.id ? decided : item)
    ElMessage.info('操作已拒绝。')
  } catch {
    ElMessage.error('拒绝失败；该审批可能已被处理。')
  } finally {
    decidingApproval.value = false
  }
}

async function restoreActiveRun() {
  const saved = window.localStorage.getItem(activeRunStorageKey)
  if (!saved) return

  try {
    const { taskId, runId } = JSON.parse(saved) as { taskId: number; runId: number }
    if (!Number.isSafeInteger(taskId) || !Number.isSafeInteger(runId)) return
    const run = await getRun(runId)
    traceRunId.value = runId
    traceRunStatus.value = run.status
    traceResultText.value = run.resultText
    traceErrorMessage.value = run.errorMessage
    traceRetryOfRunId.value = run.retryOfRunId
    traceSteps.value = await getRunSteps(runId)
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [taskId]: run.status }
    if (run.status === 'WAITING_APPROVAL') {
      traceApprovals.value = await getRunApprovals(runId)
    }
    if (!isTerminalStatus(run.status)) {
      activeRunTaskIds.value = [...activeRunTaskIds.value, taskId]
      startRunPolling(taskId, runId)
    }
  } catch {
    window.localStorage.removeItem(activeRunStorageKey)
  }
}

function startRunPolling(taskId: number, runId: number) {
  pollTimers.set(runId, window.setInterval(() => void pollRunStatus(taskId, runId), 1000))
}

async function handleCreateTask() {
  const taskTitle = title.value.trim()

  if (!taskTitle) {
    ElMessage.warning('请输入任务标题。')
    return
  }

  creating.value = true

  try {
    await createTask(taskTitle)
    title.value = ''
    ElMessage.success('任务已创建。')
    await loadTasks()
  } catch {
    ElMessage.error('创建任务失败，请确认后端正在运行。')
  } finally {
    creating.value = false
  }
}

async function handleKnowledgeFileChange(uploadFile: { raw?: File }) {
  if (!uploadFile.raw) {
    ElMessage.error('无法读取所选文件。')
    return
  }

  uploadingKnowledge.value = true
  knowledgeUploadMessage.value = ''

  try {
    const result = await uploadKnowledgeFile(uploadFile.raw)
    knowledgeUploadMessage.value = `Uploaded successfully · Indexed ${result.chunkCount} chunks`
    ElMessage.success(`${result.fileName} 已建立知识库索引。`)
  } catch (error: unknown) {
    knowledgeUploadMessage.value = ''
    ElMessage.error('文件上传或建立索引失败，请检查文件、PGvector 和 Embedding 配置。')
  } finally {
    uploadingKnowledge.value = false
  }
}

async function handleRunTask(task: AgentTask) {
  runningTaskId.value = task.id

  try {
    const run = await runTask(task.id)
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [task.id]: run.status }
    activeRunTaskIds.value = [...activeRunTaskIds.value, task.id]
    traceRunId.value = run.runId
    traceSteps.value = []
    traceRunStatus.value = run.status
    traceResultText.value = run.resultText
    traceErrorMessage.value = run.errorMessage
    traceRetryOfRunId.value = run.retryOfRunId
    traceApprovals.value = []
    decisionReason.value = ''
    window.localStorage.setItem(activeRunStorageKey, JSON.stringify({ taskId: task.id, runId: run.runId }))
    ElMessage.success('执行已入队，正在后台执行。')
    startRunPolling(task.id, run.runId)
  } catch {
    ElMessage.error('任务执行失败，请刷新任务列表后重试。')
  } finally {
    runningTaskId.value = null
  }
}

async function handleRetry() {
  if (traceRunId.value === null || traceRunStatus.value !== 'FAILED' || retryingRun.value) return
  retryingRun.value = true
  try {
    await ElMessageBox.confirm(
      '上一次浏览器或发布操作可能已经在外部成功。请先核对目标系统，再决定是否重新执行。新 Run 会重新请求审批。',
      '确认手动 Retry',
      { confirmButtonText: '继续 Retry', cancelButtonText: '取消', type: 'warning' },
    )
    const run = await retryRun(traceRunId.value)
    traceRunId.value = run.runId
    traceSteps.value = []
    traceRunStatus.value = run.status
    traceResultText.value = run.resultText
    traceErrorMessage.value = run.errorMessage
    traceRetryOfRunId.value = run.retryOfRunId
    traceApprovals.value = []
    decisionReason.value = ''
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [run.taskId]: run.status }
    activeRunTaskIds.value = [...new Set([...activeRunTaskIds.value, run.taskId])]
    window.localStorage.setItem(activeRunStorageKey, JSON.stringify({ taskId: run.taskId, runId: run.runId }))
    startRunPolling(run.taskId, run.runId)
    ElMessage.success(`已创建新的 Run #${run.runId}，正在后台执行。`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('Retry 失败；请确认旧 Run 仍为 FAILED，且尚未被重试。')
    }
  } finally {
    retryingRun.value = false
  }
}

onMounted(async () => {
  await loadTasks()
  await restoreActiveRun()
})
onUnmounted(stopAllTaskPolling)
</script>

<template>
  <main class="page">
    <header class="page-header">
      <h1>AgentFlow</h1>
      <p>AI Agent Workflow Platform</p>
    </header>

    <el-card class="task-section" shadow="never">
      <template #header>
        <h2>Create Task</h2>
      </template>

      <div class="create-task-form">
        <el-input
          v-model="title"
          aria-label="Task Title"
          placeholder="Task Title"
          @keyup.enter="handleCreateTask"
        />
        <el-button type="primary" :loading="creating" @click="handleCreateTask">
          Create Task
        </el-button>
      </div>
    </el-card>

    <el-card class="task-section" shadow="never">
      <template #header>
        <h2>Knowledge Base</h2>
      </template>

      <div class="knowledge-upload">
        <el-upload
          accept=".pdf,.docx,.txt,.md,.markdown"
          :auto-upload="false"
          :show-file-list="false"
          :disabled="uploadingKnowledge"
          :on-change="handleKnowledgeFileChange"
        >
          <el-button type="primary" :loading="uploadingKnowledge">Upload File</el-button>
        </el-upload>
        <span class="knowledge-hint">PDF, DOCX, TXT, Markdown · max 10 MB</span>
      </div>
      <p v-if="knowledgeUploadMessage" class="knowledge-result">{{ knowledgeUploadMessage }}</p>
    </el-card>

    <el-card class="task-section" shadow="never">
      <template #header>
        <h2>Tasks</h2>
      </template>

      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="title" label="Title" />
        <el-table-column label="Status" width="140">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Run Status" width="140">
          <template #default="scope">
            <el-tag
              v-if="runStatusByTaskId[scope.row.id]"
              :type="runStatusTagType(runStatusByTaskId[scope.row.id])"
            >
              {{ runStatusByTaskId[scope.row.id] }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="120">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              :disabled="runningTaskId !== null || isTaskRunActive(scope.row.id)"
              :loading="runningTaskId === scope.row.id"
              @click="handleRunTask(scope.row)"
            >
              Run
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="traceRunId !== null" class="task-section" shadow="never">
      <template #header>
        <h2>Execution Trace · Run #{{ traceRunId }}</h2>
      </template>

      <p v-if="traceRetryOfRunId !== null" class="trace-empty">Retry of Run #{{ traceRetryOfRunId }}</p>

      <p v-if="traceSteps.length === 0" class="trace-empty">等待 Consumer 创建执行步骤…</p>
      <ul v-else class="trace-list">
        <li v-for="step in traceSteps" :key="step.id" class="trace-item">
          <span class="trace-marker">{{ stepMarker(step.status) }}</span>
          <strong>{{ step.stepOrder }}. {{ step.stepType }}</strong>
          <el-tag :type="stepStatusTagType(step.status)" size="small">{{ step.status }}</el-tag>
          <div v-if="step.stepType === 'BROWSER'" class="browser-trace">
            <strong>BrowserTool</strong>
            <p>Visited URL: {{ step.inputSummary }}</p>
            <p v-if="step.outputSummary" class="trace-summary">{{ step.outputSummary }}</p>
          </div>
          <div v-else-if="step.stepType === 'BROWSER_ACTION'" class="browser-trace">
            <strong>Approved Browser Action</strong>
            <p>{{ step.inputSummary }}</p>
            <p v-if="step.outputSummary" class="trace-summary">{{ step.outputSummary }}</p>
          </div>
          <p v-else-if="step.outputSummary" class="trace-summary">{{ step.outputSummary }}</p>
          <p v-if="step.errorMessage" class="trace-error">Error: {{ step.errorMessage }}</p>
        </li>
      </ul>

      <section v-if="traceRunStatus === 'WAITING_APPROVAL' && pendingApproval()" class="approval-section">
        <h3>Pending Approval</h3>
        <p><strong>Action:</strong> {{ pendingApproval()!.actionSummary }}</p>
        <template v-if="pendingApproval()!.actionType.startsWith('BROWSER_')">
          <p><strong>Browser action:</strong> {{ approvalField(pendingApproval()!, 'browserAction') }}</p>
          <p><strong>Page URL:</strong> {{ approvalField(pendingApproval()!, 'url') }}</p>
          <p><strong>Target:</strong> {{ browserTarget(pendingApproval()!) }}</p>
          <p v-if="pendingApproval()!.actionType === 'BROWSER_TYPE'"><strong>Text:</strong> {{ approvalField(pendingApproval()!, 'value') }}</p>
        </template>
        <template v-else>
          <p><strong>Target:</strong> {{ approvalField(pendingApproval()!, 'target') }}</p>
          <p><strong>Content:</strong> {{ approvalField(pendingApproval()!, 'content') }}</p>
        </template>
        <el-input v-model="decisionReason" aria-label="Reject reason" placeholder="Reject reason (optional)" />
        <div class="approval-actions">
          <el-button type="primary" :loading="decidingApproval" @click="handleApprove(pendingApproval()!)">Approve</el-button>
          <el-button type="danger" :disabled="decidingApproval" @click="handleReject(pendingApproval()!)">Reject</el-button>
        </div>
      </section>
      <p v-else-if="traceRunStatus === 'WAITING_APPROVAL' && traceApprovals.some((approval) => approval.status === 'APPROVED')" class="trace-empty">
        Approved. Waiting for the Consumer to resume...
      </p>

      <section class="result-section">
        <h3>Result</h3>
        <p v-if="traceRunStatus === 'QUEUED' || traceRunStatus === 'RUNNING'" class="trace-empty">
          Agent is working...
        </p>
        <p v-else-if="traceRunStatus === 'WAITING_APPROVAL'" class="trace-empty">Waiting for user approval...</p>
        <p v-else-if="traceRunStatus === 'COMPLETED'" class="result-text">
          {{ traceResultText || 'LLM returned no content.' }}
        </p>
        <p v-else-if="traceRunStatus === 'FAILED'" class="trace-error">
          {{ traceErrorMessage?.startsWith('Execution interrupted or timed out')
            ? 'Execution interrupted or timed out.' : (traceErrorMessage || 'LLM request failed.') }}
        </p>
        <el-button v-if="traceRunStatus === 'FAILED'" type="primary" :loading="retryingRun" @click="handleRetry">
          Retry
        </el-button>
      </section>
    </el-card>
  </main>
</template>
