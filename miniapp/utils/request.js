const BASE_URL = 'http://localhost:8080/api'
const AMAP_BASE = 'https://restapi.amap.com'

let forbiddenHandling = false

function loadAmapKey() {
  try {
    const local = require('./amap.local')
    return (local && local.key) || ''
  } catch (e) {
    return ''
  }
}

const AMAP_KEY = loadAmapKey()

function request(options) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success(res) {
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          wx.reLaunch({ url: '/pages/login/login' })
          reject(new Error('登录已过期'))
          return
        }
        const data = res.data || {}
        if (res.statusCode === 403) {
          if (!options.silent) {
            wx.showToast({ title: data.message || '无权限访问', icon: 'none' })
          }
          if (!options.skipForbiddenRedirect && !forbiddenHandling) {
            forbiddenHandling = true
            setTimeout(() => {
              forbiddenHandling = false
              wx.switchTab({ url: '/pages/index/index' })
            }, 800)
          }
          const err = new Error(data.message || '无权限访问')
          err.code = data.code
          err.statusCode = 403
          reject(err)
          return
        }
        if (data.code !== 200) {
          if (!options.silent) {
            wx.showToast({ title: data.message || '请求失败', icon: 'none' })
          }
          const err = new Error(data.message || '请求失败')
          err.code = data.code
          err.statusCode = res.statusCode
          reject(err)
          return
        }
        resolve(data)
      },
      fail(err) {
        if (!options.silent) {
          wx.showToast({ title: '网络错误', icon: 'none' })
        }
        reject(err)
      }
    })
  })
}

function login(data) {
  return request({ url: '/user/login', method: 'POST', data })
}

function register(data) {
  return request({ url: '/user/register', method: 'POST', data })
}

function getUserInfo() {
  return request({ url: '/user/info' })
}

function wxLogin(code) {
  return request({ url: '/user/wx-login', method: 'POST', data: { code } })
}

function submitAppeal(reason) {
  return request({ url: '/appeal/submit', method: 'POST', data: { reason } })
}

function getMyAppeals() {
  return request({ url: '/appeal/my' })
}

function getAllUsers() {
  return request({ url: '/user/all' })
}

function banUser(userId, reason) {
  return request({ url: '/user/ban', method: 'PUT', data: { userId, reason } })
}

function unbanUser(userId) {
  return request({ url: `/user/unban/${userId}`, method: 'PUT' })
}

function getAllAppeals() {
  return request({ url: '/appeal/all' })
}

function handleAppeal(appealId, status, reply) {
  return request({ url: '/appeal/handle', method: 'PUT', data: { appealId, status, reply } })
}

function removeStudent(courseId, studentId) {
  return request({ url: `/course/remove-student/${courseId}/${studentId}`, method: 'PUT' })
}

function createCheckin(data) {
  return request({ url: '/checkin/create', method: 'POST', data })
}

function endCheckin(checkinId) {
  return request({ url: `/checkin/end/${checkinId}`, method: 'PUT' })
}

function getCourseCheckins(courseId) {
  return request({ url: `/checkin/course/${courseId}`, method: 'GET' })
}

function getCheckinDetail(checkinId) {
  return request({ url: `/checkin/detail/${checkinId}`, method: 'GET' })
}

function submitCheckin(data) {
  return request({ url: '/checkin/submit', method: 'POST', data, skipForbiddenRedirect: true, silent: true })
}

function submitCheckinByCode(data) {
  return request({ url: '/checkin/submit-by-code', method: 'POST', data, skipForbiddenRedirect: true, silent: true })
}

function getMyCheckins() {
  return request({ url: '/checkin/my', method: 'GET' })
}

function joinLive(data) {
  return request({ url: '/live/join', method: 'POST', data })
}

function getLiveState(sessionId) {
  return request({ url: `/live/state/${sessionId}`, method: 'GET' })
}

function getLiveSlides(sessionId) {
  return request({ url: `/live/slides/${sessionId}`, method: 'GET' })
}

function getCurrentLive(courseId) {
  return request({ url: `/live/current/${courseId}`, method: 'GET' })
}

function getActiveLives() {
  return request({ url: '/live/active', method: 'GET' })
}

function heartbeatLive(data) {
  return request({ url: '/live/heartbeat', method: 'POST', data, silent: true })
}

function createLiveWsTicket(sessionId) {
  return request({ url: `/live/ws-ticket/${sessionId}`, method: 'POST', silent: true })
}

function toggleLiveHand(sessionId) {
  return request({ url: `/live/hand/${sessionId}`, method: 'POST', silent: true })
}

function askLiveQuestion(data) {
  return request({ url: '/live/question', method: 'POST', data })
}

function getMyCourses() {
  return request({ url: '/course/my', method: 'GET' })
}

function joinCourse(code) {
  return request({ url: '/course/join', method: 'POST', data: { code } })
}

function createHomework(data) {
  return request({ url: '/homework/create', method: 'POST', data })
}

function getCourseHomework(courseId) {
  return request({ url: `/homework/course/${courseId}`, method: 'GET' })
}

function getHomeworkDetail(id) {
  return request({ url: `/homework/detail/${id}`, method: 'GET' })
}

function submitHomework(data) {
  return request({ url: '/homework/submit', method: 'POST', data })
}

function gradeHomework(data) {
  return request({ url: '/homework/grade', method: 'PUT', data })
}

function getHomeworkSubmissions(homeworkId) {
  return request({ url: `/homework/submissions/${homeworkId}`, method: 'GET' })
}

function getMySubmit(homeworkId) {
  return request({ url: `/homework/my-submit/${homeworkId}`, method: 'GET' })
}

function getMyHomework(courseId) {
  return request({ url: `/homework/my/${courseId}`, method: 'GET' })
}

function getMyAllHomework() {
  return request({ url: '/homework/my-all', method: 'GET' })
}

function createQuiz(data) {
  return request({ url: '/quiz/create', method: 'POST', data })
}

function updateQuiz(data) {
  return request({ url: '/quiz/update', method: 'PUT', data })
}

function publishQuiz(id) {
  return request({ url: `/quiz/publish/${id}`, method: 'POST' })
}

function deleteQuiz(id) {
  return request({ url: `/quiz/delete/${id}`, method: 'DELETE' })
}

function getCourseQuizzes(courseId) {
  return request({ url: `/quiz/course/${courseId}`, method: 'GET' })
}

function getQuizDetail(id) {
  return request({ url: `/quiz/detail/${id}`, method: 'GET' })
}

function takeQuiz(id, assignmentId) {
  const query = assignmentId ? `?assignmentId=${assignmentId}` : ''
  return request({ url: `/quiz/take/${id}${query}`, method: 'GET' })
}

function submitQuiz(data) {
  return request({ url: '/quiz/submit', method: 'POST', data })
}

function getMyQuizRecord(quizId, assignmentId) {
  const query = assignmentId ? `?assignmentId=${assignmentId}` : ''
  return request({ url: `/quiz/my-record/${quizId}${query}`, method: 'GET' })
}

function publishAssignment(id) {
  return request({ url: `/quiz-assign/publish/${id}`, method: 'POST' })
}

function endAssignment(id) {
  return request({ url: `/quiz-assign/end/${id}`, method: 'POST' })
}

function deleteAssignment(id) {
  return request({ url: `/quiz-assign/delete/${id}`, method: 'DELETE' })
}

function getMyAllQuizzes() {
  return request({ url: '/quiz/my-all', method: 'GET' })
}

function setScoreRule(data) {
  return request({ url: '/score-rule/set', method: 'POST', data })
}

function getScoreRule(courseId) {
  return request({ url: `/score-rule/get/${courseId}`, method: 'GET' })
}

function getCourseScores(courseId) {
  return request({ url: `/score/course/${courseId}`, method: 'GET' })
}

function getMyScore(courseId) {
  return request({ url: `/score/my/${courseId}`, method: 'GET' })
}

function getScoreDetail(courseId, studentId) {
  return request({ url: `/score/detail/${courseId}/${studentId}`, method: 'GET' })
}

function getCourseMaterials(courseId) {
  return request({ url: `/material/course/${courseId}`, method: 'GET' })
}

function createMaterial(data) {
  return request({ url: '/material/create', method: 'POST', data })
}

function deleteMaterial(materialId) {
  return request({ url: `/material/delete/${materialId}`, method: 'DELETE' })
}

function toggleMaterial(materialId) {
  return request({ url: `/material/toggle/${materialId}`, method: 'PUT' })
}

function getCoursePosts(courseId, params) {
  return request({ url: `/post/course/${courseId}`, method: 'GET', data: params })
}

function getMyAllPosts() {
  return request({ url: '/post/my', method: 'GET' })
}

function getCourseNotices(courseId) {
  return request({ url: `/notice/course/${courseId}`, method: 'GET' })
}

function createNotice(data) {
  return request({ url: '/notice/create', method: 'POST', data })
}

function deleteNotice(noticeId) {
  return request({ url: `/notice/${noticeId}`, method: 'DELETE' })
}

function getMyNotices() {
  return request({ url: '/notice/my', method: 'GET' })
}

function getPostDetail(postId) {
  return request({ url: `/post/detail/${postId}`, method: 'GET' })
}

function createPost(data) {
  return request({ url: '/post/create', method: 'POST', data })
}

function deletePost(postId) {
  return request({ url: `/post/delete/${postId}`, method: 'DELETE' })
}

function commentPost(data) {
  return request({ url: '/post/comment', method: 'POST', data })
}

function deletePostComment(commentId) {
  return request({ url: `/post/comment/${commentId}`, method: 'DELETE' })
}

function likePost(postId) {
  return request({ url: `/post/like/${postId}`, method: 'POST' })
}

function uploadFile(filePath) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')
    wx.uploadFile({
      url: BASE_URL + '/file/upload',
      filePath: filePath,
      name: 'file',
      header: { 'Authorization': token ? 'Bearer ' + token : '' },
      success(res) {
        const data = JSON.parse(res.data)
        if (data.code === 200) {
          resolve(data)
        } else {
          wx.showToast({ title: data.message || '上传失败', icon: 'none' })
          reject(new Error(data.message))
        }
      },
      fail(err) {
        wx.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

function aiChat(message, history) {
  return request({ url: '/ai/chat', method: 'POST', data: { message, history } })
}

function supportsChunkedRequest() {
  try {
    const parts = (wx.getSystemInfoSync().SDKVersion || '').split('.').map(Number)
    const target = [2, 20, 1]
    for (let i = 0; i < target.length; i += 1) {
      const value = parts[i] || 0
      if (value > target[i]) return true
      if (value < target[i]) return false
    }
    return true
  } catch (e) {
    return false
  }
}

function decodeUtf8(bytes) {
  let text = ''
  let i = 0
  while (i < bytes.length) {
    const b = bytes[i]
    let need = 1
    let cp = b
    if (b < 0x80) {
      need = 1
      cp = b
    } else if (b >= 0xc0 && b < 0xe0) {
      need = 2
      cp = b & 0x1f
    } else if (b >= 0xe0 && b < 0xf0) {
      need = 3
      cp = b & 0x0f
    } else if (b >= 0xf0) {
      need = 4
      cp = b & 0x07
    } else {
      i += 1
      continue
    }
    if (i + need > bytes.length) break
    for (let k = 1; k < need; k += 1) {
      cp = (cp << 6) | (bytes[i + k] & 0x3f)
    }
    text += String.fromCodePoint(cp)
    i += need
  }
  return { text, rest: bytes.slice(i) }
}

function aiChatStream(options) {
  if (!supportsChunkedRequest()) return null
  const { message, history, onContent, onError, onDone } = options || {}
  const token = wx.getStorageSync('token')
  let pending = new Uint8Array(0)
  let buffer = ''
  let finished = false
  let cancelled = false

  const emit = line => {
    if (finished || cancelled) return
    const trimmed = line.trim()
    if (!trimmed || !trimmed.startsWith('data:')) return
    const payload = trimmed.slice(5).trim()
    if (!payload || payload === '[DONE]') return
    let data
    try {
      data = JSON.parse(payload)
    } catch (e) {
      return
    }
    if (data.type === 'content' && data.content) {
      onContent && onContent(data.content)
    } else if (data.type === 'error') {
      finished = true
      onError && onError(data.content || 'AI 服务异常')
    } else if (data.type === 'done') {
      finished = true
      onDone && onDone()
    }
  }

  const consume = final => {
    const lines = buffer.split('\n')
    if (final) {
      buffer = ''
    } else {
      buffer = lines.pop()
    }
    lines.forEach(emit)
  }

  const appendText = text => {
    buffer += text
    consume(false)
  }

  const task = wx.request({
    url: BASE_URL + '/ai/chat/stream',
    method: 'POST',
    data: { message, history },
    header: {
      'Content-Type': 'application/json',
      'Authorization': token ? 'Bearer ' + token : ''
    },
    enableChunked: true,
    responseType: 'text',
    success(res) {
      if (res.statusCode === 401) {
        wx.removeStorageSync('token')
        wx.removeStorageSync('userInfo')
        wx.reLaunch({ url: '/pages/login/login' })
        return
      }
      if (finished || cancelled) return
      if (typeof res.data === 'string' && res.data) {
        appendText(res.data)
      }
      if (finished || cancelled) return
      consume(true)
      if (finished || cancelled) return
      finished = true
      if (res.statusCode && res.statusCode !== 200) {
        onError && onError('AI 服务异常')
      } else {
        onDone && onDone()
      }
    },
    fail(err) {
      if (finished || cancelled) return
      finished = true
      onError && onError('网络错误')
    }
  })

  if (typeof task.onChunkReceived !== 'function') {
    cancelled = true
    task.abort()
    return null
  }

  task.onChunkReceived(res => {
    if (finished || cancelled) return
    if (typeof res.data === 'string') {
      appendText(res.data)
      return
    }
    const incoming = new Uint8Array(res.data)
    const merged = new Uint8Array(pending.length + incoming.length)
    merged.set(pending, 0)
    merged.set(incoming, pending.length)
    const decoded = decodeUtf8(merged)
    pending = decoded.rest
    appendText(decoded.text)
  })

  return task
}

function aiStatus() {
  return request({ url: '/ai/status', method: 'GET' })
}

function aiPing() {
  return request({ url: '/ai/ping', method: 'GET' })
}

const geocodeCache = {}

const PLACE_KEYWORDS = ['教学楼', '实验楼', '实训楼', '综合楼', '办公楼', '图书馆', '体育馆', '宿舍', '食堂', '学院', '大学', '学校', '大厦', '广场', '中心', '医院', '楼']

function pickBestPoi(pois) {
  if (!pois || !pois.length) return null
  let best = null
  let bestScore = -1
  for (let i = 0; i < pois.length; i += 1) {
    const poi = pois[i]
    if (!poi || !poi.name) continue
    const distance = poi.distance === '' || poi.distance == null ? 99999 : Number(poi.distance)
    let score = 0
    if (PLACE_KEYWORDS.some(k => poi.name.indexOf(k) !== -1)) score += 100
    score -= Math.min(distance / 50, 40)
    score += (pois.length - i) / pois.length
    if (score > bestScore) {
      bestScore = score
      best = poi
    }
  }
  return best
}

function reverseGeocode(latitude, longitude) {
  return new Promise((resolve) => {
    if (!AMAP_KEY || latitude == null || longitude == null) {
      resolve(null)
      return
    }
    const cacheKey = Number(latitude).toFixed(5) + ',' + Number(longitude).toFixed(5)
    if (geocodeCache[cacheKey]) {
      resolve(geocodeCache[cacheKey])
      return
    }
    wx.request({
      url: AMAP_BASE + '/v3/geocode/regeo',
      method: 'GET',
      data: {
        key: AMAP_KEY,
        location: Number(longitude).toFixed(6) + ',' + Number(latitude).toFixed(6),
        extensions: 'all',
        radius: 500,
        batch: false
      },
      success(res) {
        const data = res.data || {}
        if (data.status === '1' && data.regeocode) {
          const geo = data.regeocode
          const component = geo.addressComponent || {}
          const poi = pickBestPoi(geo.pois)
          const poiName = poi ? poi.name : ''
          const formatted = geo.formatted_address || ''
          const address = poiName || formatted
          const result = {
            address,
            poiName,
            formattedAddress: formatted,
            poiDistance: poi ? Number(poi.distance) || 0 : 0,
            province: component.province || '',
            city: component.city || '',
            district: component.district || '',
            township: component.township || '',
            street: (component.streetNumber && component.streetNumber.street) || ''
          }
          geocodeCache[cacheKey] = result
          resolve(result)
          return
        }
        resolve(null)
      },
      fail() { resolve(null) }
    })
  })
}

module.exports = { BASE_URL, request, reverseGeocode, login, register, getUserInfo, wxLogin, submitAppeal, getMyAppeals, banUser, unbanUser, getAllAppeals, handleAppeal, removeStudent, createCheckin, endCheckin, getCourseCheckins, getCheckinDetail, submitCheckin, submitCheckinByCode, getMyCheckins, joinLive, getLiveState, getLiveSlides, getCurrentLive, getActiveLives, getMyCourses, joinCourse, heartbeatLive, createLiveWsTicket, toggleLiveHand, askLiveQuestion, createHomework, getCourseHomework, getHomeworkDetail, submitHomework, gradeHomework, getHomeworkSubmissions, getMySubmit, getMyHomework, getMyAllHomework, createQuiz, updateQuiz, publishQuiz, deleteQuiz, getCourseQuizzes, getQuizDetail, takeQuiz, submitQuiz, getMyQuizRecord, publishAssignment, endAssignment, deleteAssignment, getMyAllQuizzes, setScoreRule, getScoreRule, getCourseScores, getMyScore, getScoreDetail, getCourseMaterials, createMaterial, deleteMaterial, toggleMaterial, getCourseNotices, createNotice, deleteNotice, getMyNotices, getCoursePosts, getMyAllPosts, getPostDetail, createPost, deletePost, commentPost, deletePostComment, likePost, uploadFile, aiChat, aiChatStream, aiStatus, aiPing }