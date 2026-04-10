import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Map from '../components/Map'
import CategoryFilter from '../components/CategoryFilter'
import PlaceCard from '../components/PlaceCard'
import DirectionInfo from '../components/DirectionInfo'
import { fetchPlaces } from '../api/placeApi'
import { fetchCarDirections } from '../api/directionApi'

export default function Result() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const { users = [], midpoint = null } = state || {}

  const [category, setCategory] = useState('ALL')
  const [places, setPlaces] = useState([])
  const [placesLoading, setPlacesLoading] = useState(false)
  const [selectedPlace, setSelectedPlace] = useState(null)

  const [carDirections, setCarDirections] = useState([])
  const [carLoading, setCarLoading] = useState(false)

  // 장소 검색
  useEffect(() => {
    if (!midpoint) return
    setPlacesLoading(true)
    setSelectedPlace(null)
    fetchPlaces(midpoint.lat, midpoint.lng, category, 1000)
      .then((data) => setPlaces(data.places || []))
      .catch(() => setPlaces([]))
      .finally(() => setPlacesLoading(false))
  }, [midpoint, category])

  // 자가용 경로 조회 (최초 1회)
  useEffect(() => {
    if (!midpoint) return
    const validUsers = users.filter(u => u.lat && u.lng)
    if (validUsers.length === 0) return

    setCarLoading(true)
    const destLat = midpoint.stationLat || midpoint.lat
    const destLng = midpoint.stationLng || midpoint.lng
    fetchCarDirections(
      validUsers.map(u => ({ name: u.address, lat: u.lat, lng: u.lng })),
      { lat: destLat, lng: destLng }
    )
      .then(setCarDirections)
      .catch(() => setCarDirections([]))
      .finally(() => setCarLoading(false))
  }, [midpoint])

  if (!midpoint) {
    return (
      <div className="h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-gray-500 mb-4">먼저 출발지를 입력해주세요.</p>
          <button onClick={() => navigate('/')} className="bg-blue-500 text-white px-6 py-2 rounded-xl">
            돌아가기
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50 overflow-hidden">
      <div className="max-w-2xl w-full mx-auto flex flex-col h-full">

        {/* 상단 고정 영역 */}
        <div className="flex-shrink-0 px-4 pt-4">
          {/* 헤더 */}
          <div className="flex items-center gap-3 mb-3">
            <button onClick={() => navigate('/')} className="text-gray-400 hover:text-gray-600 text-sm">
              ← 다시 찾기
            </button>
            <div>
              <h1 className="text-lg font-bold text-gray-800">
                📍 {midpoint.nearestStation || midpoint.address || '중간지점'}
              </h1>
              <p className="text-xs text-gray-400">{midpoint.address}</p>
            </div>
          </div>

          {/* 지도 */}
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-2 mb-3">
            <Map locations={users} midpoint={midpoint} selectedPlace={selectedPlace} />
          </div>

          {/* 교통편 정보 */}
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 px-4 py-3 mb-3">
            <DirectionInfo
              carDirections={carDirections}
              carLoading={carLoading}
              transitDirections={midpoint?.transitTimes?.map(t => ({
                userName: t.userName,
                duration: t.durationSeconds,
                distance: -1,
                tollFee: 0,
              }))}
              users={users}
              midpoint={midpoint}
            />
          </div>

          {/* 카테고리 탭 */}
          <div className="bg-white rounded-t-2xl border border-b-0 border-gray-100 shadow-sm px-4 pt-4 pb-2">
            <p className="text-xs font-semibold text-gray-400 mb-2">주변 추천 장소</p>
            <CategoryFilter selected={category} onChange={setCategory} />
          </div>
        </div>

        {/* 스크롤 되는 장소 목록 */}
        <div className="flex-1 overflow-y-auto px-4">
          <div className="bg-white rounded-b-2xl border border-t-0 border-gray-100 shadow-sm px-4 pb-4">
            {placesLoading ? (
              <div className="text-center py-10 text-gray-400 text-sm">장소를 검색 중...</div>
            ) : places.length === 0 ? (
              <div className="text-center py-10 text-gray-400 text-sm">주변 장소를 찾지 못했습니다.</div>
            ) : (
              <div className="flex flex-col gap-3">
                {places.map((place) => (
                  <PlaceCard
                    key={place.id}
                    place={place}
                    selected={selectedPlace?.id === place.id}
                    onClick={setSelectedPlace}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

      </div>
    </div>
  )
}
