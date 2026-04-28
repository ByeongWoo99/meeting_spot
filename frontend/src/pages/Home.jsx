import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import UserCountSelector from '../components/UserCountSelector'
import LocationInput from '../components/LocationInput'
import Map from '../components/Map'
import axios from 'axios'
import { calcMidpoint } from '../api/midpointApi'
import { formatSeconds } from '../utils/format'

function makeUsers(count) {
  return Array.from({ length: count }, (_, i) => ({
    name: `사용자 ${i + 1}`,
    address: '',
    lat: null,
    lng: null,
  }))
}

export default function Home() {
  const navigate = useNavigate()
  const [mode, setMode] = useState('midpoint') // 'midpoint' | 'nearby'

  // 중간지점 모드 상태
  const [userCount, setUserCount] = useState(2)
  const [users, setUsers] = useState(makeUsers(2))
  const [candidates, setCandidates] = useState([])   // 후보 역 배열 (최대 2개)
  const [selectedIdx, setSelectedIdx] = useState(0)  // 선택된 후보 인덱스
  const midpoint = candidates[selectedIdx] || null
  const [loading, setLoading] = useState(false)
  const [loadingStep, setLoadingStep] = useState(0)
  const abortRef = useRef(null)
  const [error, setError] = useState(null)

  const LOADING_STEPS = [
    { label: '후보 역 탐색 중...', sub: '주변 지하철역을 검색하고 있어요' },
    { label: '대중교통 소요시간 조회 중...', sub: '각 출발지에서 후보 역까지 시간을 계산하고 있어요' },
    { label: '최적 중간지점 계산 중...', sub: '가장 공평한 장소를 찾고 있어요' },
  ]

  useEffect(() => {
    if (!loading) { setLoadingStep(0); return }
    const t1 = setTimeout(() => setLoadingStep(1), 2000)
    const t2 = setTimeout(() => setLoadingStep(2), 5000)
    return () => { clearTimeout(t1); clearTimeout(t2) }
  }, [loading])

  // 주변 장소 모드 상태
  const [nearbyUser, setNearbyUser] = useState({ address: '', lat: null, lng: null })
  const [nearbyError, setNearbyError] = useState(null)

  function handleCountChange(count) {
    setUserCount(count)
    setUsers(prev => {
      if (count >= prev.length) {
        const extras = Array.from({ length: count - prev.length }, (_, i) => ({
          name: `사용자 ${prev.length + i + 1}`,
          address: '',
          lat: null,
          lng: null,
        }))
        return [...prev, ...extras]
      }
      return prev.slice(0, count)
    })
    setCandidates([])
    setSelectedIdx(0)
  }

  function handleLocationChange(index, loc) {
    setUsers((prev) => prev.map((u, i) => (i === index ? { ...u, ...loc } : u)))
    setCandidates([])
    setSelectedIdx(0)
  }

  async function handleSubmit() {
    const valid = users.filter((u) => u.lat && u.lng)
    if (valid.length < users.length) {
      const missing = users
        .map((u, i) => (!u.lat || !u.lng) ? `출발지${i + 1}` : null)
        .filter(Boolean)
      const joined = missing.length === 1
        ? missing[0]
        : missing.slice(0, -1).join(', ') + '와 ' + missing[missing.length - 1]
      setError(`${joined}을 입력해주세요.`)
      return
    }
    const allSame = valid.every(
      (u) => u.lat === valid[0].lat && u.lng === valid[0].lng
    )
    if (allSame) {
      setError('서로 다른 출발지를 입력해주세요.')
      return
    }
    setError(null)
    setLoading(true)
    abortRef.current = new AbortController()
    try {
      const locations = users
        .filter((u) => u.lat && u.lng)
        .map((u) => ({ name: u.name, lat: u.lat, lng: u.lng }))
      const result = await calcMidpoint(locations, abortRef.current.signal)
      setCandidates(result)
      setSelectedIdx(0)
    } catch (e) {
      if (!axios.isCancel(e) && e.name !== 'CanceledError') {
        setError('중간지점 계산에 실패했습니다. 백엔드 서버를 확인해 주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  function handleNext() {
    navigate('/result', { state: { users, candidates, selectedIdx } })
  }

  function handleNearbySearch() {
    if (!nearbyUser.lat || !nearbyUser.lng) {
      setNearbyError('위치를 입력해주세요.')
      return
    }
    navigate('/result', {
      state: {
        users: [{ name: '나', ...nearbyUser }],
        candidates: [{
          rank: 1,
          lat: nearbyUser.lat,
          lng: nearbyUser.lng,
          address: nearbyUser.address,
          nearestStation: null,
          stationLat: nearbyUser.lat,
          stationLng: nearbyUser.lng,
          transitTimes: null,
          transitFallback: true,
        }],
        selectedIdx: 0,
        initialCategory: 'ALL',
      }
    })
  }

  function switchMode(next) {
    setMode(next)
    setError(null)
    setNearbyError(null)
    setNearbyUser({ address: '', lat: null, lng: null })
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto px-4 py-8">

        {/* 헤더 */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-gray-800 mb-2">중간지점 만남 장소 추천</h1>
          <p className="text-gray-500 text-sm">출발지를 입력하면 모두에게 공평한 만남 장소를 찾아드려요</p>
        </div>

        {/* 모드 탭 */}
        <div className="flex gap-2 mb-3">
          <button
            onClick={() => switchMode('midpoint')}
            className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-colors
              ${mode === 'midpoint' ? 'bg-blue-500 text-white' : 'bg-white text-gray-500 border border-gray-200 hover:bg-gray-50'}`}
          >
            중간지점 찾기
          </button>
          <button
            onClick={() => switchMode('nearby')}
            className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-colors
              ${mode === 'nearby' ? 'bg-green-500 text-white' : 'bg-white text-gray-500 border border-gray-200 hover:bg-gray-50'}`}
          >
            주변 명소 찾기
          </button>
        </div>

        {/* 입력 카드 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 mb-4">
          {mode === 'midpoint' ? (
            <>
              <UserCountSelector count={userCount} onChange={handleCountChange} />
              <div className="mb-4">
                {users.map((user, i) => (
                  <LocationInput
                    key={i}
                    index={i}
                    value={user}
                    onChange={(loc) => handleLocationChange(i, loc)}
                  />
                ))}
              </div>
              {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
              <button
                onClick={handleSubmit}
                disabled={loading}
                className="w-full bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                {loading ? '계산 중...' : '중간지점 찾기'}
              </button>
            </>
          ) : (
            <>
              <p className="text-sm text-gray-500 mb-4">위치를 입력하면 주변 맛집·카페·명소를 찾아드려요</p>
              <LocationInput
                index={0}
                value={nearbyUser}
                onChange={(loc) => setNearbyUser(prev => ({ ...prev, ...loc }))}
              />
              {nearbyError && <p className="text-red-500 text-sm mb-3">{nearbyError}</p>}
              <button
                onClick={handleNearbySearch}
                className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                주변 장소 찾기
              </button>
            </>
          )}
        </div>

        {/* 지도 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-4">
          <Map locations={mode === 'midpoint' ? users : [nearbyUser]} midpoint={midpoint} />
        </div>

        {/* 중간지점 결과 */}
        {mode === 'midpoint' && candidates.length > 0 && (
          <div className="bg-amber-50 border border-amber-200 rounded-2xl p-5">

            {/* 후보 탭 */}
            {candidates.length === 2 && (
              <div className="flex gap-2 mb-4">
                {candidates.map((c, i) => (
                  <button
                    key={i}
                    onClick={() => setSelectedIdx(i)}
                    className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-colors
                      ${selectedIdx === i
                        ? 'bg-amber-500 text-white'
                        : 'bg-white text-amber-600 border border-amber-300 hover:bg-amber-50'}`}
                  >
                    {i + 1}위 {c.nearestStation || '중간지점'}
                  </button>
                ))}
              </div>
            )}

            <h2 className="text-lg font-bold text-amber-800 mb-1">
              📍 {midpoint.nearestStation || '중간지점'}
            </h2>
            <p className="text-sm text-amber-700 mb-3">{midpoint.address}</p>

            {midpoint.transitTimes && midpoint.transitTimes.length > 0 && (
              <div className="mb-3">
                <p className="text-xs font-semibold text-amber-600 mb-1">🚇 대중교통 소요시간</p>
                <div className="flex flex-col gap-1">
                  {midpoint.transitTimes.map((t, i) => (
                    <div key={i} className="flex justify-between text-xs text-amber-800">
                      <span>{t.userName}</span>
                      <span className="font-semibold">
                        {t.durationSeconds > 0 ? formatSeconds(t.durationSeconds) : '-'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {midpoint.transitFallback && (
              <p className="text-xs text-amber-500 mb-3">* 직선거리 기준으로 선택됨</p>
            )}

            <button
              onClick={handleNext}
              className="w-full bg-amber-500 hover:bg-amber-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              주변 맛집 · 카페 추천 보기 →
            </button>
          </div>
        )}
      </div>

      {/* 계산 중 로딩 팝업 */}
      {loading && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl px-8 py-6 flex flex-col items-center gap-4 shadow-xl w-72">
            <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
            <div className="text-center">
              <p className="text-gray-800 font-semibold text-sm">{LOADING_STEPS[loadingStep].label}</p>
              <p className="text-gray-400 text-xs mt-1">{LOADING_STEPS[loadingStep].sub}</p>
            </div>
            <div className="flex gap-1.5">
              {LOADING_STEPS.map((_, i) => (
                <div
                  key={i}
                  className={`h-1.5 rounded-full transition-all duration-500
                    ${i <= loadingStep ? 'bg-blue-500 w-6' : 'bg-gray-200 w-3'}`}
                />
              ))}
            </div>
            <button
              onClick={() => abortRef.current?.abort()}
              className="text-sm font-semibold text-red-500 border border-red-400 hover:bg-red-50 px-5 py-1.5 rounded-lg transition-colors"
            >
              취소
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
