<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, getTasks, runTask } from './api/task'
import type { AgentTask, AgentTaskStatus } from './types/task'

const tasks = ref<AgentTask[]>([])
const title = ref('')
const loading = ref(false)
const creating = ref(false)
const runningTaskId = ref<number | null>(null)

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
  task.status = 'RUNNING'

  try {
    const updatedTask = await runTask(task.id)
    await loadTasks()

    if (updatedTask.status === 'FAILED') {
      ElMessage.error('任务执行失败。')
    } else {
      ElMessage.success('任务执行完成。')
    }
  } catch {
    await loadTasks()
    ElMessage.error('任务执行失败，请刷新任务列表后重试。')
  } finally {
    runningTaskId.value = null
  }
}

onMounted(loadTasks)
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
        <el-table-column label="Actions" width="120">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'CREATED'"
              type="primary"
              size="small"
              :disabled="runningTaskId !== null"
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
