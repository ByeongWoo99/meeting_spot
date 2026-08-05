import { useState, useEffect, useRef } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Map from '../components/Map'
import CategoryFilter from '../components/CategoryFilter'
import PlaceCard from '../components/PlaceCard'
import DirectionInfo from '../components/DirectionInfo'
import ShareModal from '../components/ShareModal'
import { MARKER_COLORS } from '../utils/markerColors'
import { fetchPlaces } from '../api/placeApi'
import { fetchCarDirections } from '../api/directionApi'
import { describeCandidate, sendEvent } from '../api/midpointApi'

export default function Result() {
  const { state } = useLocation()
  const sessionKey = state?.sessionKey
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [shareModal, setShareModal] = useState(null)
  const [navPlace, setNavPlace] = useState(null)

  function openKakaoNav(origin, place) {
    const toName = encodeURIComponent(place.name)
    const toLat = place.lat
    const toLng = place.lng
    let url
    if (origin?.lat && origin?.lng) {
      const fromName = encodeURIComponent(origin.address || origin.name)
      url = `https://map.kakao.com/link/from/${fromName},${origin.lat},${origin.lng}/to/${toName},${toLat},${toLng}`
    } else {
      url = `https://map.kakao.com/link/to/${toName},${toLat},${toLng}`
    }
    window.open(url, '_blank')
  }

  function handleNavigate(place) {
    const validUsers = users.filter(u => u.lat && u.lng)
    if (validUsers.length <= 1) {
      openKakaoNav(validUsers[0] ?? null, place)
    } else {
      setNavPlace(place)
    }
    sendEvent(sessionKey, 'PLACE_NAVIGATED', { place: place.name, category: place.categoryCode, station: midpoint?.nearestStation })
  }

  const { users: stateUsers = [], candidates: stateCandidates = [], selectedIdx: initIdx = 0, initialCategory = 'ALL', searchNote = null, descriptions: stateDescriptions = {} } = state || {}

  const isSharedView = stateCandidates.length === 0 && !!searchParams.get('candidates')
  const urlActiveIdx = isSharedView ? parseInt(searchParams.get('activeIdx') || '0') : initIdx
  const [activeIdx, setActiveIdx] = useState(urlActiveIdx)

  // URL 파라미터를 마운트 시 1회만 파싱 — useMemo+JSON.parse는 매 렌더마다 새 참조를 만들어
  // midpoint → useEffect([midpoint]) 무한 루프를 유발하므로 useState lazy init으로 고정
  const [urlCandidates] = useState(() => {
    const param = searchParams.get('candidates')
    return param ? JSON.parse(param) : []
  })
  const [urlUsers] = useState(() => {
    const usersParam = searchParams.get('users')
    return usersParam ? JSON.parse(usersParam) : []
  })

  const candidates = stateCandidates.length > 0 ? stateCandidates : urlCandidates
  const midpoint = candidates[activeIdx] || null
  const users = isSharedView ? urlUsers : stateUsers

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
    sendEvent(sessionKey, 'RESULT_SHARED', { method: 'link' })
  }

  const [category, setCategory] = useState(initialCategory)
  const [places, setPlaces] = useState([])
  const [placesLoading, setPlacesLoading] = useState(false)
  const [selectedPlace, setSelectedPlace] = useState(null)

  const SHEET_HEIGHTS = { collapsed: 80, default: 360, expanded: 520 }
  const SHEET_CYCLE = { collapsed: 'default', default: 'expanded', expanded: 'collapsed' }
  const [sheetState, setSheetState] = useState('default')
  const [dragHeight, setDragHeight] = useState(null)
  const dragHeightRef = useRef(null)
  const handleRef = useRef(null)
  const onHandleDragStartRef = useRef(null)
  const [aiExpanded, setAiExpanded] = useState(false)
  const [bottomTab, setBottomTab] = useState('places')

  useEffect(() => { setAiExpanded(false) }, [activeIdx])
  const cycleSheet = () => setSheetState(s => SHEET_CYCLE[s])

  const onHandleDragStart = (e) => {
    e.preventDefault()
    const startY = e.touches ? e.touches[0].clientY : e.clientY
    const startHeight = SHEET_HEIGHTS[sheetState]
    dragHeightRef.current = null

    const onMove = (ev) => {
      const clientY = ev.touches ? ev.touches[0].clientY : ev.clientY
      const dy = startY - clientY
      const newH = Math.max(60, Math.min(560, startHeight + dy))
      dragHeightRef.current = newH
      setDragHeight(newH)
    }

    const onEnd = () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onEnd)
      window.removeEventListener('touchmove', onMove)
      window.removeEventListener('touchend', onEnd)

      const cur = dragHeightRef.current
      dragHeightRef.current = null
      setDragHeight(null)

      if (cur === null || Math.abs(cur - startHeight) < 40) {
        cycleSheet()
        return
      }

      const closest = Object.entries(SHEET_HEIGHTS).reduce((best, [st, h]) =>
        Math.abs(h - cur) < Math.abs(SHEET_HEIGHTS[best] - cur) ? st : best
      , 'default')
      setSheetState(closest)
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onEnd)
    window.addEventListener('touchmove', onMove, { passive: false })
    window.addEventListener('touchend', onEnd)
  }

  onHandleDragStartRef.current = onHandleDragStart

  useEffect(() => {
    const el = handleRef.current
    if (!el) return
    const fn = (e) => onHandleDragStartRef.current(e)
    el.addEventListener('touchstart', fn, { passive: false })
    return () => el.removeEventListener('touchstart', fn)
  }, [])

  const [descriptions, setDescriptions] = useState(stateDescriptions)
  const [carDirections, setCarDirections] = useState([])
  const [carLoading, setCarLoading] = useState(false)

  useEffect(() => {
    if (candidates.length === 0) return
    candidates.forEach(async (c) => {
      if (!c.nearestStation || !c.transitTimes?.length) return
      if (stateDescriptions[c.rank]) return
      const desc = await describeCandidate(c).catch(() => '')
      setDescriptions(prev => ({ ...prev, [c.rank]: desc }))
    })
  }, [candidates])

  useEffect(() => {
    if (!midpoint) return
    setPlacesLoading(true)
    setSelectedPlace(null)
    fetchPlaces(midpoint.lat, midpoint.lng, category, 1000)
      .then((data) => setPlaces(data.places || []))
      .catch(() => setPlaces([]))
      .finally(() => setPlacesLoading(false))
  }, [midpoint, category])

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

  const candidateTabs = candidates.length === 2 && (
    <div className="flex gap-2">
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
  )

  const tabBar = (extraClass = '') => !isNearbyMode && (
    <div className={`flex gap-2 flex-shrink-0 ${extraClass}`}>
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
  )

  return (
    <>
    <div className="h-screen flex flex-col md:flex-row bg-gray-50 overflow-hidden">

      {/* ── 모바일 전용 헤더 ── */}
      <div className="md:hidden flex-shrink-0 px-4 pt-4">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => navigate('/')}
              className="text-xs font-semibold bg-gray-100 hover:bg-gray-200 text-gray-600 px-3 py-1.5 rounded-lg transition-colors flex-shrink-0"
            >
              ← 다시 찾기
            </button>
            <div className="min-w-0">
              <h1 className="text-base font-bold text-gray-800 truncate">
                📍 {midpoint.nearestStation || midpoint.address || '중간지점'}
              </h1>
              <p className="text-xs text-gray-400 truncate">{midpoint.address}</p>
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

        {candidates.length === 2 && <div className="mb-3">{candidateTabs}</div>}
      </div>

      {/* ── 지도 영역 (바텀시트 포함) ── */}
      <div className="flex-1 min-h-0 relative">
        <Map locations={users} midpoint={midpoint} selectedPlace={selectedPlace} fillHeight />

        {/* ── 바텀시트: 모바일 전용 ── */}
        <div
          className="md:hidden absolute left-0 right-0 bottom-0 z-50 rounded-t-2xl flex flex-col overflow-hidden"
          style={{
            height: dragHeight ?? SHEET_HEIGHTS[sheetState],
            background: 'rgba(255,255,255,0.65)',
            boxShadow: '0 -2px 12px rgba(0,0,0,0.08)',
            transition: dragHeight !== null ? 'none' : 'height 0.3s cubic-bezier(0.4,0,0.2,1)',
          }}
        >
          {/* 핸들 */}
          <div
            ref={handleRef}
            onMouseDown={onHandleDragStart}
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

          {/* 미니 카드 (collapsed + 장소 선택 시) */}
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

          {/* 탭 바 */}
          {!isNearbyMode && sheetState !== 'collapsed' && tabBar('px-4 pt-1 pb-2')}

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

          {/* 장소 목록 탭 */}
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
                        onDetail={(place) => sendEvent(sessionKey, 'PLACE_DETAILED', { place: place.name, category: place.categoryCode, station: midpoint?.nearestStation })}
                        onNavigate={handleNavigate}
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* ── PC 전용 사이드바 ── */}
      <div className="hidden md:flex flex-col w-[768px] border-l border-gray-200 bg-white overflow-hidden flex-shrink-0">

        {/* 헤더 */}
        <div className="flex-shrink-0 px-5 py-4 border-b border-gray-100">
          <div className="flex items-center justify-between mb-3">
            <button
              onClick={() => navigate('/')}
              className="text-xs font-semibold bg-gray-100 hover:bg-gray-200 text-gray-600 px-3 py-1.5 rounded-lg transition-colors"
            >
              ← 다시 찾기
            </button>
            <button
              onClick={handleShareResult}
              className="text-xs bg-blue-50 hover:bg-blue-100 text-blue-600 font-semibold px-3 py-1.5 rounded-full transition-colors"
            >
              결과 공유
            </button>
          </div>

          <h1 className="text-xl font-bold text-gray-800 mb-0.5">
            📍 {midpoint.nearestStation || midpoint.address || '중간지점'}
          </h1>
          <p className="text-xs text-gray-400 mb-3">{midpoint.address}</p>

          {descriptions[midpoint.rank] ? (
            <div className="bg-blue-50 border border-blue-200 rounded-xl px-3 py-2 mb-3">
              <p className="text-xs text-blue-700">{descriptions[midpoint.rank]}</p>
            </div>
          ) : midpoint.nearestStation && (
            <p className="text-xs text-blue-400 mb-3">AI 설명 생성 중...</p>
          )}

          {searchNote && (
            <div className="bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 mb-3">
              <p className="text-xs text-amber-700">{searchNote}</p>
            </div>
          )}

          {candidates.length === 2 && candidateTabs}
        </div>

        {/* 탭 바 */}
        {!isNearbyMode && (
          <div className="flex-shrink-0 px-5 py-3 border-b border-gray-100">
            {tabBar()}
          </div>
        )}

        {/* 콘텐츠 */}
        <div className="flex-1 overflow-y-auto min-h-0">
          {!isNearbyMode && bottomTab === 'routes' ? (
            <div className="px-5 py-4">
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
          ) : (
            <div className="px-5 py-4">
              {isNearbyMode && (
                <div className="flex items-center justify-between mb-3">
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
              )}
              <p className="text-xs font-semibold text-gray-400 mb-2">주변 추천 장소</p>
              <CategoryFilter selected={category} onChange={setCategory} />
              <div className="mt-3">
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
                        onClick={(place) => setSelectedPlace(place)}
                        onDetail={(place) => sendEvent(sessionKey, 'PLACE_DETAILED', { place: place.name, category: place.categoryCode, station: midpoint?.nearestStation })}
                        onNavigate={handleNavigate}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

    </div>

    {navPlace && (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[100] px-4">
        <div className="bg-white rounded-2xl p-5 w-full max-w-sm shadow-xl">
          <h3 className="font-bold text-gray-800 mb-0.5">출발지 선택</h3>
          <p className="text-xs text-gray-400 mb-4 truncate">{navPlace.name}까지 길찾기</p>
          <div className="flex flex-col gap-2">
            {users.filter(u => u.lat && u.lng).map((user, i) => {
              const color = MARKER_COLORS[i % MARKER_COLORS.length]
              return (
                <button
                  key={i}
                  onClick={() => { openKakaoNav(user, navPlace); setNavPlace(null) }}
                  style={{ borderColor: color }}
                  className="text-left px-4 py-3 rounded-xl border-2 hover:opacity-80 transition-opacity flex items-center gap-3"
                >
                  <div className="w-3 h-3 rounded-full flex-shrink-0" style={{ backgroundColor: color }} />
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-gray-800">{user.name}</p>
                    <p className="text-xs text-gray-400 truncate">{user.address}</p>
                  </div>
                </button>
              )
            })}
          </div>
          <button
            onClick={() => setNavPlace(null)}
            className="w-full mt-3 text-sm text-gray-400 hover:text-gray-600 py-2 transition-colors"
          >
            취소
          </button>
        </div>
      </div>
    )}

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
