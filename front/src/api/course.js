import request from '../utils/request'

export function createCourse(data) {
  return request.post('/course/create', data)
}

export function updateCourse(data) {
  return request.put('/course/update', data)
}

export function getCourseDetail(courseId) {
  return request.get(`/course/detail/${courseId}`)
}

export function getMyCourses() {
  return request.get('/course/my')
}

export function toggleCourseStatus(courseId) {
  return request.put(`/course/toggle/${courseId}`)
}

export function dissolveCourse(courseId) {
  return request.delete(`/course/dissolve/${courseId}`)
}

export function getStudentList(courseId) {
  return request.get(`/course/students/${courseId}`)
}

export function getStudentCount(courseId) {
  return request.get(`/course/count/${courseId}`)
}

export function joinCourse(code) {
  return request.post('/course/join', { code })
}

export function quitCourse(courseId) {
  return request.put(`/course/quit/${courseId}`)
}

export function removeStudent(courseId, studentId) {
  return request.put(`/course/remove-student/${courseId}/${studentId}`)
}