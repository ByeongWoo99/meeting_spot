import { useState, useEffect, useMemo } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Map from '../components/Map'
import CategoryFilter from '../components/CategoryFilter'
import PlaceCard from '../components/PlaceCard'
import DirectionInfo from '../components/DirectionInfo'
import ShareModal from '../components/ShareModal'
import { fetchPlaces } from '../api/placeApi'
import { fetchCarDirections } from '../api/directionApi'

export default function Result() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [shareModal, setShareModal] = useState(null)

  const { users: stateUsers = [], candidates: stateCandidates = [], selectedIdx: initIdx = 0, initialCategory = 'ALL', searchNote = null } = state || {}

  const isSharedView = stateCandidates.length === 0 && !!searchParams.get('candidates')

  const urlActiveIdx = isSharedView ? parseInt(searchParams.get('activeIdx') || '0') : initIdx
  const [activeIdx, setActiveIdx] = useState(urlActiveIdx)

  // URL 파라미터에서 candidates 복원 (useMemo로 참조 안정화 — 불안정하면 useEffect 무한 재실행)
  const candidates = useMemo(() => {
    if (stateCandidates.length > 0) return stateCandidates
    const param = searchParams.get('candidates')
    return param ? JSON.parse(param) : []
  }, [searchParams, stateCandidates])

  const midpoint = candidates[activeIdx] || null

  // 공유 URL 접속 시 users 복원
  const users = useMemo(() => {
    if (!isSharedView) return stateUsers
    const usersParam = searchParams.get('users')
    return usersParam ? JSON.parse(usersParam) : []
  }, [isSharedView, searchParams, stateUsers])

  const isNearbyMode = users.length === 1

  function handleShareResult() {
    const url = new URL(window.location.origin + '/result')
    url.searchParams.set('candidates', JSON.stringify(candidates))
    url.searchParams.set('activeIdx', activeIdx)
    const validUsers = users.filter(u => u.lat && u.lng)
    if (validUsers.length > 0) {
      url.searchParams.set('users', JSON.stringify(
        validUsers.map(u => ({ name: u.name, address: u.address, lat: u.lat, lng: u.lng }))
      ))
    }
    setShareModal({
      url: url.toString(),
      title: `📍 ${midpoint?.nearestStation || midpoint?.address}`,
      description: '중간지점 만남 장소 추천 결과를 확인해보세요!',
    })
  }

  const [category, setCategory] = useState(initialCategory)
  const [places, setPlaces] = useState([])
  const [placesLoading, setPlacesLoading] = useState(false)
  const [selectedPlace, setSelectedPlace] = useState(null)

  const [carDirections, setCarDirections] = useState([])
  const [carLoading, setCarLoading] = useState(false)

  // 장소 검색 — midpoint(후보 탭 전환 포함) 또는 category 변경 시 재실행
  useEffect(() => {
    if (!midpoint) return
    setPlacesLoading(true)
    setSelectedPlace(null)
    fetchPlaces(midpoint.lat, midpoint.lng, category, 1000)
      .then((data) => setPlaces(data.places || []))
      .catch(() => setPlaces([]))
      .finally(() => setPlacesLoading(false))
  }, [midpoint, category])

  // 자가용 경로 조회 — midpoint(후보 탭 전환 포함) 변경 시 재실행, 주변 장소 모드 제외
  useEffect(() => {
    if (!midpoint || isNearbyMode) return
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
    <>
    <div className="h-screen flex flex-col bg-gray-50 overflow-hidden">
      <div className="max-w-2xl w-full mx-auto flex flex-col h-full">

        {/* 상단 고정 영역 */}
        <div className="flex-shrink-0 px-4 pt-4">
          {/* 헤더 */}
          <div className="flex items-center justify-between gap-3 mb-3">
            <div className="flex items-center gap-3">
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
            <button
              onClick={handleShareResult}
              className="text-xs bg-blue-50 hover:bg-blue-100 text-blue-600 font-semibold px-3 py-1.5 rounded-full transition-colors flex-shrink-0"
            >
              결과 공유
            </button>
          </div>

          {/* 탐색 안내 메시지 */}
          {searchNote && (
            <div className="bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 mb-3">
              <p className="text-xs text-amber-700">{searchNote}</p>
            </div>
          )}

          {/* 후보 탭 */}
          {candidates.length === 2 && (
            <div className="flex gap-2 mb-3">
              {candidates.map((c, i) => (
                <button
                  key={i}
                  onClick={() => setActiveIdx(i)}
                  className={`flex-1 py-2 rounded-xl text-xs font-semibold transition-colors
                    ${activeIdx === i
                      ? 'bg-amber-500 text-white'
                      : 'bg-white text-amber-600 border border-amber-300 hover:bg-amber-50'}`}
                >
                  {i + 1}위 {c.nearestStation || '중간지점'}
                </button>
              ))}
            </div>
          )}

          {/* 지도 */}
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-2 mb-3">
            <Map locations={users} midpoint={midpoint} selectedPlace={selectedPlace} />
          </div>

          {/* 교통편 정보 / 장소 정보 */}
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 px-4 py-3 mb-3">
            {isNearbyMode ? (
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-gray-800">{midpoint.address}</p>
                <a
                  href={`https://map.kakao.com/link/map/${encodeURIComponent(midpoint.address)},${midpoint.lat},${midpoint.lng}`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-sm text-blue-500 font-semibold hover:underline whitespace-nowrap ml-4"
                >
                  지도보기
                </a>
              </div>
            ) : (
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
            )}
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

    {shareModal && (
      <ShareModal
        url={shareModal.url}
        title={shareModal.title}
        description={shareModal.description}
        onClose={() => setShareModal(null)}
      />
    )}
    </>
  )
}
