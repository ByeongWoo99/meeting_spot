import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter } from 'k6/metrics'

const rateLimitHits = new Counter('rate_limit_429')

// 테스트 설정
export let options = {
  iterations: 20
}

// 테스트 케이스 (서울·수도권 내 다양한 출발지 조합)
const testCases = [
  {
    label: '강남-홍대',
    locations: [
      { name: '사용자1', lat: 37.4979, lng: 127.0276 },
      { name: '사용자2', lat: 37.5572, lng: 126.9239 },
    ],
  },
  {
    label: '수원-강남',
    locations: [
      { name: '사용자1', lat: 37.2636, lng: 127.0286 },
      { name: '사용자2', lat: 37.4979, lng: 127.0276 },
    ],
  },
  {
    label: '인천-잠실',
    locations: [
      { name: '사용자1', lat: 37.4563, lng: 126.7052 },
      { name: '사용자2', lat: 37.5133, lng: 127.1000 },
    ],
  },
  {
    label: '3명-강남-홍대-잠실',
    locations: [
      { name: '사용자1', lat: 37.4979, lng: 127.0276 },
      { name: '사용자2', lat: 37.5572, lng: 126.9239 },
      { name: '사용자3', lat: 37.5133, lng: 127.1000 },
    ],
  },
  {
    label: '4명-수원-잠실-인천-홍대',
    locations: [
      { name: '사용자1', lat: 37.2636, lng: 127.0286 },
      { name: '사용자2', lat: 37.5133, lng: 127.1000 },
      { name: '사용자3', lat: 37.4563, lng: 126.7052 },
      { name: '사용자4', lat: 37.5572, lng: 126.9239 },
    ],
  },
]

let idx = 0

export default function () {
  const tc = testCases[idx % testCases.length]
  idx++

  const payload = JSON.stringify({
    locations: tc.locations,
    category: 'ALL',
  })

  const res = http.post(
    'http://localhost:8080/api/midpoint',
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  )

  check(res, {
    '응답 성공 (200)': (r) => r.status === 200,
    '후보 존재': (r) => {
      try {
        return JSON.parse(r.body).candidates?.length > 0
      } catch {
        return false
      }
    },
  })

  if (res.status === 429) rateLimitHits.add(1)

  console.log(`[${tc.label}] ${res.timings.duration.toFixed(0)}ms`)

  sleep(1)  // 요청 간 1초 간격 (API 부담 최소화)
}
