import { useState, useEffect, useMemo } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Map from '../components/Map'
import CategoryFilter from '../components/CategoryFilter'
import PlaceCard from '../components/PlaceCard'
import DirectionInfo from '../components/DirectionInfo'
import ShareModal from '../components/ShareModal'
import { fetchPlaces } from '../api/placeApi'
import { fetchCarDirections } from '../api/directionApi'
import { describeCandidate } from '../api/midpointApi'

export default function Result() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [shareModal, setShareModal] = useState(null)

  const { users: stateUsers = [], candidates: stateCandidates = [], selectedIdx: initIdx = 0, initialCategory = 'ALL', searchNote = null, descriptions: stateDescriptions = {} } = state || {}

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

  const SHEET_HEIGHTS = { collapsed: 80, default: 360, expanded: 520 }
  const SHEET_CYCLE = { collapsed: 'default', default: 'expanded', expanded: 'collapsed' }
  const [sheetState, setSheetState] = useState('default')
  const [dragHeight, setDragHeight] = useState(null)
  const [aiExpanded, setAiExpanded] = useState(false)
  const [bottomTab, setBottomTab] = useState('places')

  useEffect(() => { setAiExpanded(false) }, [activeIdx])
  const cycleSheet = () => setSheetState(s => SHEET_CYCLE[s])

  const onHandleDragStart = (e) => {
    e.preventDefault()
    const startY = e.touches ? e.touches[0].clientY : e.clientY
    const startHeight = SHEET_HEIGHTS[sheetState]
    let moved = false

    const onMove = (ev) => {
      const clientY = ev.touches ? ev.touches[0].clientY : ev.clientY
      const dy = startY - clientY
      if (Math.abs(dy) > 4) moved = true
      setDragHeight(Math.max(60, Math.min(560, startHeight + dy)))
    }

    const onEnd = () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onEnd)
      window.removeEventListener('touchmove', onMove)
      window.removeEventListener('touchend', onEnd)
      if (!moved) {
        setDragHeight(null)
        cycleSheet()
        return
      }
      setDragHeight(prev => {
        const cur = prev ?? startHeight
        const closest = Object.entries(SHEET_HEIGHTS).reduce((best, [st, h]) =>
          Math.abs(h - cur) < Math.abs(SHEET_HEIGHTS[best] - cur) ? st : best
        , 'default')
        setSheetState(closest)
        return null
      })
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onEnd)
    window.addEventListener('touchmove', onMove, { passive: false })
    window.addEventListener('touchend', onEnd)
  }

  const [descriptions, setDescriptions] = useState(stateDescriptions)

  const [carDirections, setCarDirections] = useState([])
  const [carLoading, setCarLoading] = useState(false)

  // AI 설명 비동기 로딩 — 설명이 없는 후보만 fetch
  useEffect(() => {
    if (candidates.length === 0) return
    candidates.forEach(async (c) => {
      if (!c.nearestStation || !c.transitTimes?.length) return
      if (stateDescriptions[c.rank]) return
      const desc = await describeCandidate(c).catch(() => '')
      setDescriptions(prev => ({ ...prev, [c.rank]: desc }))
    })
  }, [candidates])

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
      <div className="max-w-2xl w-full mx-auto flex flex-col h-full relative">

        {/* 상단 고정 영역: 헤더 + AI 설명 + 안내 + 후보 탭 */}
        <div className="flex-shrink-0 px-4 pt-4">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div className="flex items-center gap-3">
              <button onClick={() => navigate('/')} className="text-xs font-semibold bg-gray-100 hover:bg-gray-200 text-gray-600 px-3 py-1.5 rounded-lg transition-colors flex-shrink-0">
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

          {descriptions[midpoint.rank] ? (
            <div className="mb-3">
              <button
                onClick={() => setAiExpanded(v => !v)}
                className="text-xs text-blue-500 hover:text-blue-700 font-medium flex items-center gap-1 px-1 mb-1"
              >
                {aiExpanded ? '▲ AI 설명 접기' : '▼ AI 설명 보기'}
              </button>
              {aiExpanded && (
                <div className="bg-blue-50 border border-blue-200 rounded-xl px-3 py-2">
                  <p className="text-xs text-blue-700">{descriptions[midpoint.rank]}</p>
                </div>
              )}
            </div>
          ) : midpoint.nearestStation && (
            <p className="text-xs text-blue-400 px-1 mb-3">AI 설명 생성 중...</p>
          )}

          {searchNote && (
            <div className="bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 mb-3">
              <p className="text-xs text-amber-700">{searchNote}</p>
            </div>
          )}

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
        </div>

        {/* 지도 영역: 고정 크기 */}
        <div className="flex-1 min-h-0">
          <Map locations={users} midpoint={midpoint} selectedPlace={selectedPlace} fillHeight />
        </div>

        {/* 바텀 시트: 지도 위 absolute 오버레이 */}
        <div
          className="absolute left-0 right-0 bottom-0 z-50 rounded-t-2xl flex flex-col overflow-hidden"
          style={{
            height: dragHeight ?? SHEET_HEIGHTS[sheetState],
            background: 'rgba(255,255,255,0.65)',
            boxShadow: '0 -2px 12px rgba(0,0,0,0.08)',
            transition: dragHeight !== null ? 'none' : 'height 0.3s cubic-bezier(0.4,0,0.2,1)',
          }}
        >
          {/* 핸들: 드래그 + 탭(4px 이하 이동 시 cycleSheet) */}
          <div
            onMouseDown={onHandleDragStart}
            onTouchStart={onHandleDragStart}
            className="flex flex-col items-center justify-center min-h-[50px] cursor-grab active:cursor-grabbing flex-shrink-0 select-none gap-1"
          >
            <div className="w-10 h-1 bg-gray-200 rounded-full" />
            {sheetState === 'collapsed' && !selectedPlace && (
              <p className="text-xs font-medium text-gray-500">↑ 장소 목록 보기</p>
            )}
            {sheetState === 'expanded' && (
              <p className="text-xs font-medium text-gray-500">↓ 접기</p>
            )}
          </div>

          {/* 미니 카드: collapsed 상태이고 장소가 선택된 경우에만 표시 */}
          {sheetState === 'collapsed' && selectedPlace && (
            <div className="flex items-center gap-2 px-4 pb-3 flex-shrink-0">
              <span className={`text-xs font-bold px-2 py-0.5 rounded-full whitespace-nowrap ${
                { FD6: 'bg-orange-100 text-orange-700', CE7: 'bg-yellow-100 text-yellow-700', AT4: 'bg-green-100 text-green-700', CT1: 'bg-purple-100 text-purple-700' }[selectedPlace.categoryCode] || 'bg-gray-100 text-gray-600'
              }`}>
                {{ FD6: '맛집', CE7: '카페', AT4: '명소', CT1: '문화시설' }[selectedPlace.categoryCode] || selectedPlace.categoryCode}
              </span>
              <span className="text-sm font-bold text-gray-800 flex-1 min-w-0 truncate">{selectedPlace.name}</span>
              {selectedPlace.distance > 0 && (
                <span className="text-xs text-gray-400 whitespace-nowrap">{selectedPlace.distance}m</span>
              )}
              <button
                onClick={(e) => { e.stopPropagation(); setSheetState('expanded'); setBottomTab('places') }}
                className="text-xs font-bold bg-blue-500 text-white px-3 py-1 rounded-full whitespace-nowrap"
              >
                목록으로 ↑
              </button>
            </div>
          )}

          {/* 바텀 탭: 경로 정보 | 장소 목록 (주변 장소 모드에서는 탭 없이 장소만) */}
          {!isNearbyMode && sheetState !== 'collapsed' && (
            <div className="flex gap-2 px-4 pt-1 pb-2 flex-shrink-0">
              <button
                onClick={() => setBottomTab('routes')}
                className={`flex-1 py-1.5 rounded-xl text-xs font-semibold transition-colors
                  ${bottomTab === 'routes' ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
              >
                경로 정보
              </button>
              <button
                onClick={() => setBottomTab('places')}
                className={`flex-1 py-1.5 rounded-xl text-xs font-semibold transition-colors
                  ${bottomTab === 'places' ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
              >
                장소 목록
              </button>
            </div>
          )}

          {/* 경로 정보 탭 */}
          {!isNearbyMode && bottomTab === 'routes' && sheetState !== 'collapsed' && (
            <div className="flex-1 overflow-y-auto px-4 pb-4 min-h-0">
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
          )}

          {/* 장소 목록 탭 (주변 장소 모드 포함) */}
          {(isNearbyMode || bottomTab === 'places') && sheetState !== 'collapsed' && (
            <>
              {isNearbyMode && (
                <div className="flex-shrink-0 px-4 pb-2">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-semibold text-gray-800 truncate">{midpoint.address}</p>
                    <a
                      href={`https://map.kakao.com/link/map/${encodeURIComponent(midpoint.address)},${midpoint.lat},${midpoint.lng}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-xs text-blue-500 font-semibold hover:underline whitespace-nowrap ml-3"
                    >
                      지도보기
                    </a>
                  </div>
                </div>
              )}
              <div className="flex-shrink-0 px-4 pb-2">
                <p className="text-xs font-semibold text-gray-400 mb-2">주변 추천 장소</p>
                <CategoryFilter selected={category} onChange={setCategory} />
              </div>
              <div className="flex-1 overflow-y-auto px-4 pb-4 min-h-0">
                {placesLoading ? (
                  <div className="text-center py-8 text-gray-400 text-sm">장소를 검색 중...</div>
                ) : places.length === 0 ? (
                  <div className="text-center py-8 text-gray-400 text-sm">주변 장소를 찾지 못했습니다.</div>
                ) : (
                  <div className="flex flex-col gap-3">
                    {places.map((place) => (
                      <PlaceCard
                        key={place.id}
                        place={place}
                        selected={selectedPlace?.id === place.id}
                        onClick={(place) => { setSelectedPlace(place); setSheetState('collapsed') }}
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
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
