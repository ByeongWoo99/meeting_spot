import { useEffect, useRef } from 'react'

const MARKER_COLORS = ['#3B82F6', '#EF4444', '#22C55E', '#A855F7', '#F97316', '#EC4899']

const PLACE_CATEGORY_COLORS = {
  FD6: '#F97316',
  CE7: '#EAB308',
  AT4: '#22C55E',
  CT1: '#A855F7',
}

export default function Map({ locations, midpoint, selectedPlace }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markersRef = useRef([])
  const infowindowsRef = useRef([])
  const placeMarkersRef = useRef([])
  const placeInfowindowsRef = useRef([])

  // 지도 초기화 (최초 1회)
  useEffect(() => {
    if (!window.kakao?.maps || !containerRef.current) return
    const { kakao } = window
    mapRef.current = new kakao.maps.Map(containerRef.current, {
      center: new kakao.maps.LatLng(37.5665, 126.9780),
      level: 8,
    })
  }, [])

  // 출발지 + 중간지점 마커 업데이트
  useEffect(() => {
    if (!mapRef.current || !window.kakao?.maps) return
    const { kakao } = window

    markersRef.current.forEach((m) => m.setMap(null))
    infowindowsRef.current.forEach((iw) => iw.close())
    markersRef.current = []
    infowindowsRef.current = []

    const bounds = new kakao.maps.LatLngBounds()
    let hasPoint = false

    locations.forEach((loc, i) => {
      if (!loc.lat || !loc.lng) return
      const position = new kakao.maps.LatLng(loc.lat, loc.lng)
      const color = MARKER_COLORS[i % MARKER_COLORS.length]
      const marker = new kakao.maps.Marker({
        position, map: mapRef.current, image: createMarkerImage(kakao, color),
      })
      const infowindow = new kakao.maps.InfoWindow({
        content: `<div style="padding:4px 8px;font-size:12px;white-space:nowrap">${loc.address || `출발지 ${i + 1}`}</div>`,
      })
      infowindow.open(mapRef.current, marker)
      markersRef.current.push(marker)
      infowindowsRef.current.push(infowindow)
      bounds.extend(position)
      hasPoint = true
    })

    if (midpoint?.lat && midpoint?.lng) {
      const position = new kakao.maps.LatLng(midpoint.lat, midpoint.lng)
      const marker = new kakao.maps.Marker({
        position, map: mapRef.current,
        image: createMarkerImage(kakao, '#F59E0B', true), zIndex: 10,
      })
      const infowindow = new kakao.maps.InfoWindow({
        content: `<div style="padding:4px 8px;font-size:12px;font-weight:bold;color:#D97706;white-space:nowrap">📍 ${midpoint.nearestStation || '중간지점'}</div>`,
      })
      infowindow.open(mapRef.current, marker)
      markersRef.current.push(marker)
      infowindowsRef.current.push(infowindow)
      bounds.extend(position)
      hasPoint = true
    }

    if (hasPoint) mapRef.current.setBounds(bounds, 80)
  }, [locations, midpoint])

  // 선택된 장소로 지도 이동
  useEffect(() => {
    if (!mapRef.current || !window.kakao?.maps || !selectedPlace) return
    const { kakao } = window

    placeMarkersRef.current.forEach((m) => m.setMap(null))
    placeInfowindowsRef.current.forEach((iw) => iw.close())
    placeMarkersRef.current = []
    placeInfowindowsRef.current = []

    const position = new kakao.maps.LatLng(selectedPlace.lat, selectedPlace.lng)
    const color = PLACE_CATEGORY_COLORS[selectedPlace.categoryCode] || '#6B7280'
    const marker = new kakao.maps.Marker({
      position, map: mapRef.current,
      image: createMarkerImage(kakao, color, true), zIndex: 20,
    })
    const infowindow = new kakao.maps.InfoWindow({
      content: `<div style="padding:6px 10px;font-size:13px;font-weight:bold;white-space:nowrap">${selectedPlace.name}</div>`,
    })
    infowindow.open(mapRef.current, marker)
    placeMarkersRef.current.push(marker)
    placeInfowindowsRef.current.push(infowindow)
    mapRef.current.panTo(position)
  }, [selectedPlace])

  return (
    <div ref={containerRef} className="w-full rounded-xl overflow-hidden" style={{ height: '400px' }} />
  )
}

function createMarkerImage(kakao, color, large = false) {
  const size = large ? 36 : 28
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24">
    <circle cx="12" cy="12" r="10" fill="${color}" stroke="white" stroke-width="2"/>
  </svg>`
  const src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
  return new kakao.maps.MarkerImage(src, new kakao.maps.Size(size, size), {
    offset: new kakao.maps.Point(size / 2, size / 2),
  })
}
