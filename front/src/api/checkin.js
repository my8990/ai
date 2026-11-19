import request from '../utils/request'

export function createCheckin(data) {
  return request.post('/checkin/create', data)
}

export function endCheckin(checkinId) {
  return request.put(`/checkin/end/${checkinId}`)
}

export function deleteCheckin(checkinId) {
  return request.delete(`/checkin/delete/${checkinId}`)
}

export function getCourseCheckins(courseId) {
  return request.get(`/checkin/course/${courseId}`)
}

export function getCheckinDetail(checkinId) {
  return request.get(`/checkin/detail/${checkinId}`)
}

export function submitCheckin(data) {
  return request.post('/checkin/submit', data)
}

export function submitCheckinByCode(data) {
  return request.post('/checkin/submit-by-code', data)
}

export function getMyCheckins() {
  return request.get('/checkin/my')
}

export function updateRecordStatus(data) {
  return request.put('/checkin/record/status', data)
}