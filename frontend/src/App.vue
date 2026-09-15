<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, getTasks, runTask } from './api/task'
import { getRun } from './api/run'
import type { AgentTask, AgentTaskStatus } from './types/task'
import type { AgentRunStatus } from './types/run'

const tasks = ref<AgentTask[]>([])
const title = ref('')
const loading = ref(false)
const creating = ref(false)
const runningTaskId = ref<number | null>(null)
const pollTimers = new Map<number, number>()
const runStatusByTaskId = ref<Record<number, AgentRunStatus>>({})
const activeRunTaskIds = ref<number[]>([])

function isTerminalStatus(status: AgentRunStatus) {
  return status === 'COMPLETED' || status === 'FAILED'
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
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
  }
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
    const run = await getRun(runId)
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [taskId]: run.status }

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

async function handleRunTask(task: AgentTask) {
  runningTaskId.value = task.id

  try {
    const run = await runTask(task.id)
    runStatusByTaskId.value = { ...runStatusByTaskId.value, [task.id]: run.status }
    activeRunTaskIds.value = [...activeRunTaskIds.value, task.id]
    ElMessage.success('执行已入队，正在后台执行。')
    startRunPolling(task.id, run.runId)
  } catch {
    ElMessage.error('任务执行失败，请刷新任务列表后重试。')
  } finally {
    runningTaskId.value = null
  }
}

onMounted(loadTasks)
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
  </main>
</template>
