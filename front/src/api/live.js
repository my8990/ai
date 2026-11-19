import request from '../utils/request'

export function startLive(data) {
  return request.post('/live/start', data)
}

export function endLive(sessionId, publishCourseware = false) {
  return request.put(`/live/end/${sessionId}`, { publishCourseware })
}

export function getLiveEndPreview(sessionId) {
  return request.get(`/live/end-preview/${sessionId}`, { silent: true })
}

export function publishLiveCourseware(sessionId) {
  return request.post(`/live/courseware/publish/${sessionId}`)
}

export function joinLive(data) {
  return request.post('/live/join', data)
}

export function getLiveState(sessionId) {
  return request.get(`/live/state/${sessionId}`)
}

export function turnLivePage(data) {
  return request.put('/live/page', data)
}

export function getLiveSlides(sessionId) {
  return request.get(`/live/slides/${sessionId}`)
}

export function addLiveSlide(data) {
  return request.post('/live/slide/add', data)
}

export function addLiveSlides(data) {
  return request.post('/live/slide/batch', data)
}

export function uploadCourseware(file, onUploadProgress) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/file/courseware/upload', form, {
    timeout: 180000,
    onUploadProgress
  })
}

export function deleteLiveSlide(slideId) {
  return request.delete(`/live/slide/${slideId}`)
}

export function getCurrentLive(courseId) {
  return request.get(`/live/current/${courseId}`)
}

export function getLiveWxacode(sessionId) {
  return request.get(`/live/qrcode/${sessionId}`, { silent: true })
}

export function heartbeatLive(data) {
  return request.post('/live/heartbeat', data, { silent: true })
}

export function getLiveOnline(sessionId) {
  return request.get(`/live/online/${sessionId}`, { silent: true })
}

export function getLiveHistory(courseId) {
  return request.get(`/live/history/${courseId}`)
}

export function getLiveReport(sessionId) {
  return request.get(`/live/report/${sessionId}`)
}

export function exportLiveReport(sessionId) {
  return request.get(`/live/report/${sessionId}/export`, { responseType: 'blob' })
}

export function createLiveWsTicket(sessionId) {
  return request.post(`/live/ws-ticket/${sessionId}`, null, { silent: true })
}

export function toggleLiveHand(sessionId) {
  return request.post(`/live/hand/${sessionId}`, null, { silent: true })
}

export function askLiveQuestion(data) {
  return request.post('/live/question', data)
}

export function getLiveInteractions(sessionId) {
  return request.get(`/live/interactions/${sessionId}`, { silent: true })
}

export function resolveLiveInteraction(id) {
  return request.put(`/live/interaction/${id}/resolve`)
}

export function publishLiveQuiz(sessionId, data) {
  return request.post(`/live/quiz/publish/${sessionId}`, data)
}

export function endLiveQuiz(sessionId) {
  return request.post(`/live/quiz/end/${sessionId}`)
}