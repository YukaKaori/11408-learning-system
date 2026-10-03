import { api } from '@/api/http'

export type TaskStatus = 'todo' | 'inProgress' | 'done'

export type TaskPriority = 'low' | 'medium' | 'high'

/**
 * Mirror of TaskResponse.java. `dueAt` is null for unscheduled backlog tasks;
 * `completedAt` is stamped when status moves to `done` and cleared when it
 * leaves it (both server-side, never sent by the client). `nodeCode` is the
 * syllabus node the task advances.
 */
export interface TaskDto {
  id: string
  nodeCode: string | null
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  dueAt: number | null
  completedAt: number | null
  createdAt: number
  updatedAt: number
}

export interface TaskListFilters {
  status?: TaskStatus
  /** Only tasks due strictly before this instant (epoch ms). */
  dueBefore?: number
  /** A syllabus scope — the node and everything beneath it. */
  nodeCode?: string
}

/** New tasks always start as `todo`; `priority` defaults to `medium`. */
export interface CreateTaskPayload {
  title: string
  description?: string
  priority?: TaskPriority
  dueAt?: number
  nodeCode?: string
}

/**
 * Partial update — omitted fields keep their value. Clear sentinels:
 * `nodeCode: ''` un-anchors the task, `dueAt: 0` unschedules it.
 */
export interface UpdateTaskPayload {
  title?: string
  description?: string
  status?: TaskStatus
  priority?: TaskPriority
  dueAt?: number
  nodeCode?: string
}

export function listTasks(filters: TaskListFilters = {}) {
  return api.get<TaskDto[]>('/v1/tasks', { params: filters })
}

export function getTask(id: string) {
  return api.get<TaskDto>(`/v1/tasks/${id}`)
}

export function createTask(payload: CreateTaskPayload) {
  return api.post<TaskDto>('/v1/tasks', payload)
}

export function updateTask(id: string, payload: UpdateTaskPayload) {
  return api.put<TaskDto>(`/v1/tasks/${id}`, payload)
}

export function deleteTask(id: string) {
  return api.delete<void>(`/v1/tasks/${id}`)
}
