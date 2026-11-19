import request from '../utils/request'

export function getPendingTeachers() {
  return request.get('/user/pending-teachers')
}

export function approveTeacher(userId) {
  return request.put(`/user/approve/${userId}`)
}

export function rejectTeacher(userId) {
  return request.put(`/user/reject/${userId}`)
}

export function getAllTeachers() {
  return request.get('/user/teachers')
}

export function getAllUsers() {
  return request.get('/user/all')
}

export function banUser(userId, reason) {
  return request.put('/user/ban', { userId, reason })
}

export function unbanUser(userId) {
  return request.put(`/user/unban/${userId}`)
}

export function getAllAppeals() {
  return request.get('/appeal/all')
}

export function handleAppeal(appealId, status, reply) {
  return request.put('/appeal/handle', { appealId, status, reply })
}

export function submitAppeal(reason) {
  return request.post('/appeal/submit', { reason })
}

export function getMyAppeals() {
  return request.get('/appeal/my')
}