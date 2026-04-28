import { http } from './http'

export interface InteractionState {
  imageId: string
  favoriteCount: number
  likeCount: number
  commentCount: number
  favoritedByMe: boolean
  likedByMe: boolean
}

export interface ImageComment {
  id: string
  imageId: string
  userId: string
  authorName: string
  authorAvatarUrl: string | null
  content: string | null
  status: string
  createdAt: string
  updatedAt: string
  mine: boolean
  parentCommentId: string | null
  rootCommentId: string | null
  depth: number
  deleted: boolean
  hasReplies: boolean
  replies: ImageComment[]
}

export interface CommentPage {
  items: ImageComment[]
  page: number
  size: number
  total: number
  commentTotal: number
}

export type FeedbackStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface FeedbackRecord {
  id: string
  userId: string
  username: string
  displayName: string
  imageId: string | null
  imageTitle: string | null
  type: string
  title: string
  content: string
  status: FeedbackStatus
  response: string | null
  handledBy: string | null
  handledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface FeedbackPage {
  items: FeedbackRecord[]
  page: number
  size: number
  total: number
}

export async function getImageComments(imageId: string, params: { page?: number; size?: number } = {}) {
  const response = await http.get<CommentPage>(`/images/${imageId}/comments`, { params })
  return response.data
}

export async function createImageComment(
  imageId: string,
  content: string,
  parentCommentId?: string | null,
  parentUpdatedAt?: string | null,
) {
  const response = await http.post<ImageComment>(`/images/${imageId}/comments`, { content, parentCommentId, parentUpdatedAt })
  return response.data
}

export async function updateImageComment(imageId: string, commentId: string, content: string) {
  const response = await http.patch<ImageComment>(`/images/${imageId}/comments/${commentId}`, { content })
  return response.data
}

export async function deleteImageComment(imageId: string, commentId: string) {
  await http.delete(`/images/${imageId}/comments/${commentId}`)
}

export async function favoriteImage(imageId: string) {
  const response = await http.post<InteractionState>(`/images/${imageId}/favorite`)
  return response.data
}

export async function unfavoriteImage(imageId: string) {
  const response = await http.delete<InteractionState>(`/images/${imageId}/favorite`)
  return response.data
}

export async function likeImage(imageId: string) {
  const response = await http.post<InteractionState>(`/images/${imageId}/like`)
  return response.data
}

export async function unlikeImage(imageId: string) {
  const response = await http.delete<InteractionState>(`/images/${imageId}/like`)
  return response.data
}

export async function getMyFeedback(params: { status?: FeedbackStatus | ''; page?: number; size?: number } = {}) {
  const response = await http.get<FeedbackPage>('/feedback', { params })
  return response.data
}

export async function createFeedback(payload: { type: string; title: string; content: string; imageId?: string | null }) {
  const response = await http.post<FeedbackRecord>('/feedback', payload)
  return response.data
}

export async function closeFeedback(id: string) {
  await http.post(`/feedback/${id}/close`)
}

export async function getAdminFeedback(params: { keyword?: string; status?: FeedbackStatus | ''; page?: number; size?: number } = {}) {
  const response = await http.get<FeedbackPage>('/feedback/admin', { params })
  return response.data
}

export async function handleFeedback(id: string, payload: { status: FeedbackStatus; response?: string | null }) {
  const response = await http.patch<FeedbackRecord>(`/feedback/admin/${id}`, payload)
  return response.data
}
