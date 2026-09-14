<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, getTasks } from './api/task'
import type { AgentTask } from './types/task'

const tasks = ref<AgentTask[]>([])
const title = ref('')
const loading = ref(false)
const creating = ref(false)

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
            <el-tag type="success">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </main>
</template>
