import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import UserCountSelector from '../components/UserCountSelector'
import LocationInput from '../components/LocationInput'
import Map from '../components/Map'
import { calcMidpoint } from '../api/midpointApi'

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
  const [userCount, setUserCount] = useState(2)
  const [users, setUsers] = useState(makeUsers(2))
  const [midpoint, setMidpoint] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  function handleCountChange(count) {
    setUserCount(count)
    setUsers(makeUsers(count))
    setMidpoint(null)
  }

  function handleLocationChange(index, loc) {
    setUsers((prev) => prev.map((u, i) => (i === index ? { ...u, ...loc } : u)))
    setMidpoint(null)
  }

  async function handleSubmit() {
    const valid = users.filter((u) => u.lat && u.lng)
    if (valid.length < 2) {
      setError('출발지를 2곳 이상 선택해 주세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const locations = users
        .filter((u) => u.lat && u.lng)
        .map((u) => ({ name: u.name, lat: u.lat, lng: u.lng }))
      const result = await calcMidpoint(locations)
      setMidpoint(result)
    } catch (e) {
      setError('중간지점 계산에 실패했습니다. 백엔드 서버를 확인해 주세요.')
    } finally {
      setLoading(false)
    }
  }

  function handleNext() {
    navigate('/result', { state: { users, midpoint } })
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto px-4 py-8">
        {/* 헤더 */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-gray-800 mb-2">중간지점 만남 장소 추천</h1>
          <p className="text-gray-500 text-sm">출발지를 입력하면 모두에게 공평한 만남 장소를 찾아드려요</p>
        </div>

        {/* 입력 카드 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 mb-4">
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
        </div>

        {/* 지도 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-4">
          <Map locations={users} midpoint={midpoint} />
        </div>

        {/* 중간지점 결과 */}
        {midpoint && (
          <div className="bg-amber-50 border border-amber-200 rounded-2xl p-5">
            <h2 className="text-lg font-bold text-amber-800 mb-1">
              📍 {midpoint.nearestStation || '중간지점'}
            </h2>
            <p className="text-sm text-amber-700 mb-4">{midpoint.address}</p>
            <button
              onClick={handleNext}
              className="w-full bg-amber-500 hover:bg-amber-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              주변 맛집 · 카페 추천 보기 →
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
