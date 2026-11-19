import request from '../utils/request'

export function createHomework(data) {
  return request.post('/homework/create', data)
}

export function updateHomework(data) {
  return request.put('/homework/update', data)
}

export function deleteHomework(id) {
  return request.delete(`/homework/delete/${id}`)
}

export function getCourseHomework(courseId) {
  return request.get(`/homework/course/${courseId}`)
}

export function getHomeworkDetail(id) {
  return request.get(`/homework/detail/${id}`)
}

export function submitHomework(data) {
  return request.post('/homework/submit', data)
}

export function gradeHomework(data) {
  return request.put('/homework/grade', data)
}

export function getHomeworkSubmissions(homeworkId) {
  return request.get(`/homework/submissions/${homeworkId}`)
}

export function getMySubmit(homeworkId) {
  return request.get(`/homework/my-submit/${homeworkId}`)
}

export function getMyHomework(courseId) {
  return request.get(`/homework/my/${courseId}`)
}

export function uploadFile(formData) {
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function setScoreRule(data) {
  return request.post('/score-rule/set', data)
}

export function getScoreRule(courseId) {
  return request.get(`/score-rule/get/${courseId}`)
}

export function getCourseScores(courseId) {
  return request.get(`/score/course/${courseId}`)
}

export function getMyScore(courseId) {
  return request.get(`/score/my/${courseId}`)
}

export function getScoreDetail(courseId, studentId) {
  return request.get(`/score/detail/${courseId}/${studentId}`)
}

export function exportScores(courseId, sort = 'studentNo') {
  return request.get(`/score/export/${courseId}`, { responseType: 'blob', params: { sort } })
}