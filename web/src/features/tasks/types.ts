/**
 * Learning tasks — lightweight to-dos surfaced on the workspace ("today")
 * and the study calendar. Mirrors the backend `learning_tasks` table.
 * Deliberately not a project-management system.
 */

export type TaskStatus = 'todo' | 'inProgress' | 'done'
export type TaskPriority = 'low' | 'medium' | 'high'

export interface LearningTask {
  id: string
  /** Syllabus anchor (any depth). */
  nodeCode?: string
  title: string
  status: TaskStatus
  priority: TaskPriority
  /** Epoch ms; undefined = unscheduled backlog. */
  dueAt?: number
}
