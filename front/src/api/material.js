import request from '../utils/request'

export function createMaterial(data) {
  return request.post('/material/create', data)
}

export function getCourseMaterials(courseId) {
  return request.get(`/material/course/${courseId}`)
}

export function deleteMaterial(materialId) {
  return request.delete(`/material/delete/${materialId}`)
}

export function toggleMaterial(materialId) {
  return request.put(`/material/toggle/${materialId}`)
}