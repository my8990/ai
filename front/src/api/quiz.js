import request from '../utils/request'

export function createQuiz(data) {
  return request.post('/quiz/create', data)
}

export function updateQuiz(data) {
  return request.put('/quiz/update', data)
}

export function publishQuiz(id) {
  return request.post(`/quiz/publish/${id}`)
}

export function deleteQuiz(id) {
  return request.delete(`/quiz/delete/${id}`)
}

export function getCourseQuizzes(courseId) {
  return request.get(`/quiz/course/${courseId}`)
}

export function getQuizDetail(id) {
  return request.get(`/quiz/detail/${id}`)
}

export function getMyAllQuizzes() {
  return request.get('/quiz/my-all')
}

export function getMyQuizzes() {
  return request.get('/quiz-bank/my')
}

export function shareQuiz(id) {
  return request.post(`/quiz-bank/share/${id}`)
}

export function unshareQuiz(id) {
  return request.post(`/quiz-bank/unshare/${id}`)
}

export function getMarketQuizzes(params = {}) {
  return request.get('/quiz-bank/market', { params })
}

export function favoriteQuiz(id) {
  return request.post(`/quiz-bank/favorite/${id}`)
}

export function unfavoriteQuiz(id) {
  return request.delete(`/quiz-bank/favorite/${id}`)
}

export function getMyFavorites() {
  return request.get('/quiz-bank/favorites')
}

export function forkQuiz(id) {
  return request.post(`/quiz-bank/fork/${id}`)
}

export function assignQuiz(data) {
  return request.post('/quiz-assign/create', data)
}

export function updateAssignment(data) {
  return request.put('/quiz-assign/update', data)
}

export function publishAssignment(id) {
  return request.post(`/quiz-assign/publish/${id}`)
}

export function endAssignment(id) {
  return request.post(`/quiz-assign/end/${id}`)
}

export function deleteAssignment(id) {
  return request.delete(`/quiz-assign/delete/${id}`)
}