# 중간지점 만남 장소 추천 서비스 - 개발 기획서

## 1. 프로젝트 개요

### 1.1 서비스 소개
여러 명의 사용자가 각자의 출발지를 입력하면, 대중교통 소요시간 기반 복합 점수 알고리즘으로 **모든 사용자에게 공평한 중간지점**을 계산하여 만남 장소를 추천해주는 서비스입니다. 단순히 중간지점만 알려주는 것이 아니라, 해당 지점 주변의 **맛집, 카페, 명소** 등을 함께 추천하고, 각 사용자의 출발지에서 추천 장소까지의 **교통편(자가용/대중교통)**도 안내합니다. 결과는 카카오톡 또는 URL로 공유할 수 있습니다.

### 1.2 핵심 사용자 시나리오
1. 사용자 A(강남), B(홍대), C(잠실)가 만남을 계획함
2. 각자 출발지를 입력하면 대중교통 소요시간 기반으로 가장 공평한 중간지점 후보 2개(예: 1위 을지로, 2위 종로)가 계산됨
3. 두 후보를 탭으로 비교하고 원하는 곳을 선택
4. 선택한 역 주변 맛집, 카페, 핫플레이스 목록이 추천됨
5. 각 사용자별로 출발지 → 중간지점까지의 자가용/대중교통 경로와 소요시간이 안내됨
6. 결과를 카카오톡으로 공유하거나 URL로 공유

### 1.3 기대 효과
- 친구/동료 간 만남 장소 선정 시 발생하는 **"어디서 만날까?" 고민 해소**
- 대중교통 기반 복합 점수로 **실질적으로 공평한 장소 선정**
- 장소 추천 + 교통편 + 공유까지 **원스톱 서비스** 제공

---

## 2. 기술 스택

### 2.1 프론트엔드
| 기술 | 버전 | 용도 |
|------|------|------|
| **React** | 19 | UI 라이브러리 |
| **Vite** | - | 빌드 도구 |
| **Tailwind CSS** | - | 스타일링 |
| **Axios** | - | HTTP 클라이언트 |
| **React Router** | v6 | 라우팅 |

### 2.2 백엔드
| 기술 | 버전 | 용도 |
|------|------|------|
| **Spring Boot** | 3.5.0 | 백엔드 프레임워크 |
| **Java** | 17 | 프로그래밍 언어 |
| **Gradle** | - | 빌드 도구 |
| **Spring WebFlux (WebClient)** | - | 외부 API 비동기 호출 |

### 2.3 외부 API
| API | 용도 |
|-----|------|
| **카카오맵 JavaScript API** | 프론트엔드 지도 표시, 장소 검색 자동완성 |
| **카카오 로컬 REST API** | 지하철역 후보 탐색, 좌표→주소 변환, 주변 장소 검색 |
| **카카오 모빌리티 API** | 자가용 경로 탐색 (거리, 소요시간, 톨비) |
| **카카오 공유 SDK** | 카카오톡으로 결과 공유 |
| **ODsay 대중교통 API** | 출발지→역 대중교통 소요시간 조회 (중간지점 알고리즘 핵심) |

---

## 3. 구현된 기능

### Phase 1: 핵심 기능 ✅ 완료

#### 출발지 입력
- 2~6명 인원 수 선택
- 카카오 장소 검색 자동완성 (300ms 디바운스)
- 입력 완료 시 ✓ 체크 표시
- 인원 수 변경 시 기존 입력 데이터 유지
- 미입력 출발지 특정해서 오류 메시지 표시 (예: "출발지1, 출발지3을 입력해주세요")
- 동일 출발지 입력 시 오류 메시지

#### 중간지점 계산 알고리즘
1. 모든 출발지 위도·경도 평균 계산
2. 평균 좌표 반경 10km 내 지하철역 최대 6개 검색 (카카오 로컬 API)
3. 호선·출구가 다른 동일 역 중복 제거 ("수원역 1호선" → "수원역" 기준)
4. 유니크 역 최대 3개로 제한
5. 각 후보 역에 대해 모든 출발지의 대중교통 소요시간 조회 (ODsay API, 역당 300ms 대기)
6. 복합 점수 계산: `score = 0.4 × 최대소요시간 + 0.6 × 표준편차` (균등성 중심)
7. 점수 오름차순 정렬 → **상위 2개 후보 반환**

```
복합 점수 설명:
- 최대소요시간 (40%): 아무도 너무 오래 걸리지 않는 곳
- 표준편차 (60%): 소요시간이 고르게 분배된 곳 (공정성 우선)
```

#### 결과 화면
- **홈 화면**: 1위/2위 후보 탭 표시 → 각 탭에서 해당 역의 대중교통 소요시간 확인 → 원하는 후보 선택 후 결과 페이지 이동
- **결과 페이지**: 후보 탭 유지, 탭 전환 시 지도·장소·교통편 모두 자동 갱신
- 카카오맵에 출발지 마커 + 중간지점 마커 표시
- 자가용/대중교통 탭 전환, 각 출발지별 소요시간·거리·톨비 표시
- 경로별 "지도보기" (카카오맵 길찾기 연결), "공유하기" 버튼

#### 주변 장소 추천
- 카테고리 필터: 전체 / 음식점 / 카페 / 관광명소 / 문화시설 / 숙박
- 중간지점 반경 1000m 내 장소 카드 목록
- 장소 클릭 시 지도에 해당 위치 마커 표시

#### 주변 명소 찾기 모드
- 홈 화면 탭에서 "주변 명소 찾기" 선택
- 단일 위치 입력 → 해당 위치 주변 장소 바로 검색
- 교통편 정보 없이 장소 목록만 표시

#### 결과 공유
- **결과 공유 버튼**: 전체 결과 페이지를 URL 또는 카카오톡으로 공유
  - URL에 `candidates`, `activeIdx`, `users` 파라미터 인코딩
  - 공유 URL 접속 시 동일 후보 탭 복원, 자가용 경로 재조회
- **경로 공유하기 버튼**: 각 출발지별 카카오맵 경로 URL 공유
- 공유 방식 선택 모달: 카카오톡 / URL 복사

---

### Phase 2: 추가 예정 기능

| 기능 | 설명 | 우선순위 |
|------|------|---------|
| 역 없을 때 에러 처리 | 10km 반경 내 역이 없으면 평균 좌표 대신 에러 메시지 반환 | 높음 |
| 버스터미널 fallback | 지하철역 없는 지역에서 버스터미널을 대안으로 검색 | 중간 |
| 최근 검색 기록 | localStorage에 이전 출발지 저장 | 중간 |
| 검색 반경 조절 | 사용자가 500m~2km 범위 직접 설정 | 낮음 |
| 장소 상세 정보 | 영업시간, 전화번호 PlaceCard에 표시 | 낮음 |
| 가중치 적용 | 특정 사용자 배려 (슬라이더로 조절) | 낮음 |
| PWA | 모바일 홈 화면 설치 가능 (AWS 배포 후) | 낮음 |

---

## 4. 프로젝트 구조

```
meeting-spot/
│
├── frontend/
│   ├── index.html                      # 카카오맵 SDK, 카카오 공유 SDK 로드
│   ├── src/
│   │   ├── components/
│   │   │   ├── Map.jsx                 # 카카오맵, 출발지·중간지점·장소 마커
│   │   │   ├── LocationInput.jsx       # 출발지 입력 + 카카오 장소 자동완성
│   │   │   ├── UserCountSelector.jsx   # 인원 수 선택 (2~6명)
│   │   │   ├── PlaceCard.jsx           # 개별 장소 카드
│   │   │   ├── CategoryFilter.jsx      # 카테고리 필터 탭
│   │   │   ├── DirectionInfo.jsx       # 자가용/대중교통 탭 + 경로별 공유하기
│   │   │   └── ShareModal.jsx          # 공유 방식 선택 모달 (카카오톡/URL복사)
│   │   ├── pages/
│   │   │   ├── Home.jsx                # 출발지 입력, 후보 탭, 중간지점 결과
│   │   │   └── Result.jsx              # 후보 탭, 지도, 교통편, 장소 목록, 공유
│   │   ├── api/
│   │   │   ├── midpointApi.js          # POST /api/midpoint → candidates 배열
│   │   │   ├── placeApi.js             # GET /api/places
│   │   │   └── directionApi.js         # GET /api/directions (자가용)
│   │   ├── utils/
│   │   │   ├── format.js               # formatSeconds, formatDistance
│   │   │   └── kakaoShare.js           # Kakao.Share.sendDefault() 래퍼
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── vite.config.js                  # __KAKAO_JS_KEY__ 치환 플러그인 포함
│   └── .env                            # VITE_KAKAO_JS_KEY
│
├── backend/
│   └── src/main/java/com/meetingspot/
│       ├── controller/
│       │   ├── MidpointController.java
│       │   ├── PlaceController.java
│       │   └── DirectionController.java
│       ├── service/
│       │   ├── MidpointService.java    # 중간지점 알고리즘 핵심
│       │   ├── TransitService.java     # ODsay API 호출
│       │   ├── PlaceService.java
│       │   └── DirectionService.java
│       ├── dto/
│       │   ├── request/MidpointRequest.java
│       │   └── response/
│       │       ├── MidpointResponse.java   # candidates: List<Candidate>
│       │       ├── PlaceResponse.java
│       │       └── DirectionResponse.java
│       ├── config/
│       │   ├── WebConfig.java          # CORS
│       │   └── KakaoApiConfig.java     # WebClient Bean 설정
│       └── resources/application.yml
│
└── meeting-spot-plan.md
```

---

## 5. API 설계

### POST /api/midpoint
```json
// Request
{
  "locations": [
    {"name": "사용자 1", "lat": 37.4979, "lng": 127.0276},
    {"name": "사용자 2", "lat": 37.5563, "lng": 126.9236}
  ]
}

// Response
{
  "candidates": [
    {
      "rank": 1,
      "nearestStation": "을지로입구역",
      "lat": 37.5664, "lng": 126.9822,
      "address": "서울특별시 중구 을지로 지하 170",
      "stationLat": 37.5664, "stationLng": 126.9822,
      "transitTimes": [
        {"userName": "사용자 1", "durationSeconds": 1800},
        {"userName": "사용자 2", "durationSeconds": 1980}
      ],
      "transitFallback": false,
      "compositeScore": 1134.5
    },
    {
      "rank": 2,
      "nearestStation": "종각역",
      ...
    }
  ]
}
```

### GET /api/places
```
GET /api/places?lat=37.5664&lng=126.9822&category=FD6&radius=1000
```

### GET /api/directions (자가용, 다중 출발지)
```
POST /api/directions
Request: { origins: [{name, lat, lng},...], destination: {lat, lng} }
Response: [{userName, duration, distance, tollFee}, ...]
```

---

## 6. 동작 플로우

```
[홈 화면]
  출발지 입력 (카카오 장소 검색)
    │
    ▼
  "중간지점 찾기" 클릭 → 로딩 팝업
    │
    ▼ POST /api/midpoint
  [백엔드]
    평균 좌표 계산
    → 카카오 로컬 API: 10km 반경 지하철역 6개 조회
    → 동일 역 중복 제거 → 유니크 3개로 제한
    → ODsay API: 각 역의 모든 사용자 소요시간 조회 (3역 × N명)
    → 복합 점수 계산 → 상위 2개 반환
    │
    ▼
  후보 탭 [1위 OO역] [2위 OO역] 표시
  선택된 후보의 대중교통 소요시간 표시
    │
    ▼ "주변 맛집 · 카페 추천 보기 →" 클릭
    │
[결과 페이지]
  후보 탭 유지 (탭 전환 시 모든 데이터 갱신)
  지도 + 교통편 (자가용/대중교통) + 장소 목록
    │
    ├─ 탭 전환 → GET /api/places (새 후보 기준)
    │            POST /api/directions (새 목적지 기준)
    │
    ├─ "결과 공유" → ShareModal → 카카오톡 or URL 복사
    │
    └─ 경로 "공유하기" → ShareModal → 카카오맵 경로 URL 공유
```

---

## 7. 배포 계획

- **인프라**: AWS (EC2 + S3 + CloudFront 또는 Elastic Beanstalk)
- **배포 후 카카오 설정**: 실제 도메인을 카카오 개발자 콘솔 플랫폼 도메인에 등록
- **카카오톡 공유**: 공개 도메인 등록 후 정상 동작 확인 필요 (localhost에서는 링크 클릭 불가)
