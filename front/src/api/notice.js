import request from '../utils/request'

export function getCourseNotices(courseId) {
  return request.get(`/notice/course/${courseId}`)
}

export function createNotice(data) {
  return request.post('/notice/create', data)
}

export function deleteNotice(noticeId) {
  return request.delete(`/notice/${noticeId}`)
}

export function getMyNotices() {
  return request.get('/notice/my')
}