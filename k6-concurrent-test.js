import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const rateLimitHits = new Counter('rate_limit_429')

export let options = {
  scenarios: {
    concurrent_users: {
      executor: 'per-vu-iterations',
      vus: 20,
      iterations: 1,
      maxDuration: '60s',
    }
  }
}

const testCases = [
  // 2명 케이스 (10개)
  { label: '강남-홍대',     locations: [{ name: '사용자1', lat: 37.4979, lng: 127.0276 }, { name: '사용자2', lat: 37.5572, lng: 126.9239 }] },
  { label: '수원-강남',     locations: [{ name: '사용자1', lat: 37.2636, lng: 127.0286 }, { name: '사용자2', lat: 37.4979, lng: 127.0276 }] },
  { label: '인천-잠실',     locations: [{ name: '사용자1', lat: 37.4563, lng: 126.7052 }, { name: '사용자2', lat: 37.5133, lng: 127.1000 }] },
  { label: '건대-사당',     locations: [{ name: '사용자1', lat: 37.5404, lng: 127.0694 }, { name: '사용자2', lat: 37.4769, lng: 126.9816 }] },
  { label: '노원-강남',     locations: [{ name: '사용자1', lat: 37.6542, lng: 127.0568 }, { name: '사용자2', lat: 37.4979, lng: 127.0276 }] },
  { label: '일산-잠실',     locations: [{ name: '사용자1', lat: 37.6584, lng: 126.7722 }, { name: '사용자2', lat: 37.5133, lng: 127.1000 }] },
  { label: '판교-홍대',     locations: [{ name: '사용자1', lat: 37.3946, lng: 127.1112 }, { name: '사용자2', lat: 37.5572, lng: 126.9239 }] },
  { label: '부천-서울역',   locations: [{ name: '사용자1', lat: 37.5034, lng: 126.7660 }, { name: '사용자2', lat: 37.5547, lng: 126.9707 }] },
  { label: '분당-이태원',   locations: [{ name: '사용자1', lat: 37.3595, lng: 127.1085 }, { name: '사용자2', lat: 37.5344, lng: 126.9939 }] },
  { label: '구로-왕십리',   locations: [{ name: '사용자1', lat: 37.4952, lng: 126.8877 }, { name: '사용자2', lat: 37.5614, lng: 127.0369 }] },
  // 3명 케이스 (5개)
  { label: '3명-강남-홍대-잠실',     locations: [{ name: '사용자1', lat: 37.4979, lng: 127.0276 }, { name: '사용자2', lat: 37.5572, lng: 126.9239 }, { name: '사용자3', lat: 37.5133, lng: 127.1000 }] },
  { label: '3명-수원-건대-노원',     locations: [{ name: '사용자1', lat: 37.2636, lng: 127.0286 }, { name: '사용자2', lat: 37.5404, lng: 127.0694 }, { name: '사용자3', lat: 37.6542, lng: 127.0568 }] },
  { label: '3명-일산-여의도-판교',   locations: [{ name: '사용자1', lat: 37.6584, lng: 126.7722 }, { name: '사용자2', lat: 37.5215, lng: 126.9241 }, { name: '사용자3', lat: 37.3946, lng: 127.1112 }] },
  { label: '3명-인천-사당-분당',     locations: [{ name: '사용자1', lat: 37.4563, lng: 126.7052 }, { name: '사용자2', lat: 37.4769, lng: 126.9816 }, { name: '사용자3', lat: 37.3595, lng: 127.1085 }] },
  { label: '3명-노원-마포-건대',     locations: [{ name: '사용자1', lat: 37.6542, lng: 127.0568 }, { name: '사용자2', lat: 37.5547, lng: 126.9094 }, { name: '사용자3', lat: 37.5404, lng: 127.0694 }] },
  // 4명 케이스 (5개)
  { label: '4명-수원-잠실-인천-홍대',     locations: [{ name: '사용자1', lat: 37.2636, lng: 127.0286 }, { name: '사용자2', lat: 37.5133, lng: 127.1000 }, { name: '사용자3', lat: 37.4563, lng: 126.7052 }, { name: '사용자4', lat: 37.5572, lng: 126.9239 }] },
  { label: '4명-노원-강남-부천-분당',     locations: [{ name: '사용자1', lat: 37.6542, lng: 127.0568 }, { name: '사용자2', lat: 37.4979, lng: 127.0276 }, { name: '사용자3', lat: 37.5034, lng: 126.7660 }, { name: '사용자4', lat: 37.3595, lng: 127.1085 }] },
  { label: '4명-일산-판교-강남-잠실',     locations: [{ name: '사용자1', lat: 37.6584, lng: 126.7722 }, { name: '사용자2', lat: 37.3946, lng: 127.1112 }, { name: '사용자3', lat: 37.4979, lng: 127.0276 }, { name: '사용자4', lat: 37.5133, lng: 127.1000 }] },
  { label: '4명-인천-여의도-사당-건대',   locations: [{ name: '사용자1', lat: 37.4563, lng: 126.7052 }, { name: '사용자2', lat: 37.5215, lng: 126.9241 }, { name: '사용자3', lat: 37.4769, lng: 126.9816 }, { name: '사용자4', lat: 37.5404, lng: 127.0694 }] },
  { label: '4명-수원-노원-부천-의정부',   locations: [{ name: '사용자1', lat: 37.2636, lng: 127.0286 }, { name: '사용자2', lat: 37.6542, lng: 127.0568 }, { name: '사용자3', lat: 37.5034, lng: 126.7660 }, { name: '사용자4', lat: 37.7381, lng: 127.0337 }] },
]

export default function () {
  const tc = testCases[__VU % testCases.length]

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

  console.log(`[VU${__VU}][${tc.label}] ${res.timings.duration.toFixed(0)}ms`)
}
