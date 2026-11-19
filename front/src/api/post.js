import request from '../utils/request'

export function createPost(data) {
  return request.post('/post/create', data)
}

export function getCoursePosts(courseId) {
  return request.get(`/post/course/${courseId}`)
}

export function getPostDetail(postId) {
  return request.get(`/post/detail/${postId}`)
}

export function deletePost(postId) {
  return request.delete(`/post/delete/${postId}`)
}

export function addComment(data) {
  return request.post('/post/comment', data)
}

export function deleteComment(commentId) {
  return request.delete(`/post/comment/${commentId}`)
}

export function toggleLike(postId) {
  return request.post(`/post/like/${postId}`)
}