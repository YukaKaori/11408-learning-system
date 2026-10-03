import { api } from '@/api/http'

export type MaterialType = 'pdf' | 'markdown' | 'video' | 'article' | 'link' | 'document'

export const MATERIAL_TYPES: MaterialType[] = ['link', 'article', 'video', 'pdf', 'markdown', 'document']

/**
 * Mirror of MaterialResponse.java — an item on the candidate's reference shelf
 * (教材, 视频课, 真题 PDFs, links), optionally anchored to a syllabus node.
 */
export interface MaterialDto {
  id: string
  nodeCode: string | null
  title: string
  type: MaterialType
  description: string | null
  sourceUrl: string | null
  sizeBytes: number | null
  createdAt: number
}

export interface CreateMaterialPayload {
  title: string
  type: MaterialType
  description?: string
  sourceUrl?: string
  nodeCode?: string
}

/** Partial update; `nodeCode: ''` un-anchors the material. */
export interface UpdateMaterialPayload {
  title?: string
  type?: MaterialType
  description?: string
  sourceUrl?: string
  nodeCode?: string
}

/**
 * The candidate's materials, newest first. With `nodeCode`: the node's subtree
 * plus its ancestors — a textbook filed under all of 408 is still reference
 * for one of its 考点.
 */
export function listMaterials(nodeCode?: string) {
  return api.get<MaterialDto[]>('/v1/materials', { params: { nodeCode } })
}

export function createMaterial(payload: CreateMaterialPayload) {
  return api.post<MaterialDto>('/v1/materials', payload)
}

export function updateMaterial(id: string, payload: UpdateMaterialPayload) {
  return api.put<MaterialDto>(`/v1/materials/${id}`, payload)
}

export function deleteMaterial(id: string) {
  return api.delete<void>(`/v1/materials/${id}`)
}
