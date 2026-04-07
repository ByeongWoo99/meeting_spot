# 중간지점 만남 장소 추천 서비스 - 개발 기획서

## 1. 프로젝트 개요

### 1.1 서비스 소개
여러 명의 사용자가 각자의 출발지를 입력하면, 모든 사용자에게 **공평한 거리에 위치한 중간지점**을 자동으로 계산하여 만남 장소를 추천해주는 서비스입니다. 단순히 중간지점만 알려주는 것이 아니라, 해당 지점 주변의 **맛집, 카페, 핫플레이스** 등을 함께 추천하고, 각 사용자의 출발지에서 추천 장소까지의 **교통편(자가용/대중교통)**도 안내합니다.

### 1.2 핵심 사용자 시나리오
1. 사용자 A(강남), B(홍대), C(잠실)가 만남을 계획함
2. 각자 출발지를 입력하면 세 명 모두에게 비슷한 거리인 중간지점(예: 을지로)이 계산됨
3. 을지로 주변 맛집, 카페, 핫플레이스 목록이 추천됨
4. 각 사용자별로 출발지 → 을지로까지의 자가용/대중교통 경로와 소요시간이 안내됨
5. 마음에 안 들면 다른 장소를 재추천 받을 수 있음

### 1.3 기대 효과
- 친구/동료 간 만남 장소 선정 시 발생하는 **"어디서 만날까?" 고민 해소**
- 특정 사람에게 불리하지 않은 **공평한 장소 선정**
- 장소 추천 + 교통편까지 **원스톱 서비스** 제공

---

## 2. 기술 스택

### 2.1 프론트엔드
| 기술 | 용도 | 선택 이유 |
|------|------|-----------|
| **React 18** | UI 라이브러리 | 채용 시장 수요 1위, 방대한 생태계, 컴포넌트 기반 개발 |
| **Vite** | 빌드 도구 | CRA 대비 빌드 속도 10배 이상 빠름, HMR(Hot Module Replacement) 지원 |
| **Tailwind CSS** | 스타일링 | 유틸리티 클래스 기반으로 빠른 UI 개발 가능, 별도 CSS 파일 관리 불필요 |
| **Axios** | HTTP 클라이언트 | fetch보다 편리한 API 호출, 인터셉터/에러 핸들링 지원 |
| **React Router** | 라우팅 | SPA 내 페이지 전환 처리 |

### 2.2 백엔드
| 기술 | 용도 | 선택 이유 |
|------|------|-----------|
| **Spring Boot 3.x** | 백엔드 프레임워크 | Java 생태계 표준, 채용 시장 수요 높음, 풍부한 레퍼런스 |
| **Java 17+** | 프로그래밍 언어 | LTS 버전, Record/Sealed Class 등 최신 문법 활용 |
| **Gradle** | 빌드 도구 | Maven 대비 빌드 속도 빠름, Groovy/Kotlin DSL로 유연한 설정 |
| **H2 Database** | 개발용 DB | 내장형 DB로 별도 설치 없이 즉시 사용 가능 |
| **Spring WebClient** | 외부 API 호출 | 카카오맵 API 등 외부 서비스 호출에 사용, 비동기 지원 |

### 2.3 외부 API
| API | 용도 | 비용 |
|-----|------|------|
| **카카오맵 JavaScript API** | 프론트엔드 지도 표시, 마커, 오버레이 | 무료 (일 300,000회) |
| **카카오 로컬 API** | 주소 검색, 키워드로 장소 검색, 카테고리 검색 | 무료 (일 100,000회) |
| **카카오 모빌리티 API** | 자가용 경로 탐색 (거리, 소요시간, 톨비) | 무료 (일 10,000회) |
| **대중교통 API (ODsay)** | 대중교통 경로 탐색 (버스, 지하철, 환승 정보) | 무료 플랜 제공 |

> **카카오맵 API를 선택한 이유**: 국내 장소 데이터 품질이 우수하고, 무료 할당량이 넉넉하며, JavaScript SDK와 REST API를 모두 제공하여 프론트/백엔드 양쪽에서 활용 가능합니다.

---

## 3. 개발 단계 (MVP 우선 접근)

MVP(Minimum Viable Product) 방식으로 **핵심 기능부터 먼저 완성**하고, 이후 확장 기능을 점진적으로 추가합니다.

### Phase 1: MVP - 핵심 기능

---

#### 1단계: 프로젝트 셋업 및 환경 구성

**목표**: 프론트엔드/백엔드 프로젝트를 생성하고, 개발 환경을 구성합니다.

**프론트엔드 (React + Vite)**
- `npm create vite@latest frontend -- --template react` 명령으로 프로젝트 생성
- Tailwind CSS 설치 및 설정
- Axios, React Router 설치
- 카카오맵 JavaScript SDK를 `index.html`에 스크립트 태그로 추가
- ESLint, Prettier 설정으로 코드 스타일 통일

**백엔드 (Spring Boot)**
- Spring Initializr(https://start.spring.io)에서 프로젝트 생성
  - Dependencies: Spring Web, Spring Boot DevTools, Lombok, H2 Database
- `application.yml`에 카카오 API 키 설정 (환경변수로 관리)
- CORS 설정 (프론트엔드 localhost:5173 허용)

**카카오 API 키 발급**
- Kakao Developers(https://developers.kakao.com) 가입
- 애플리케이션 등록
- JavaScript 키 (프론트엔드용), REST API 키 (백엔드용) 발급
- 플랫폼에 localhost 도메인 등록

---

#### 2단계: 출발지 입력 및 중간지점 계산

**목표**: 사용자들이 출발지를 입력하면, 모두에게 공평한 중간지점을 계산합니다.

**프론트엔드 상세:**

*사용자 수 입력*
- 2~10명 범위에서 사용자 수를 선택할 수 있는 UI
- 사용자 수에 따라 출발지 입력 필드가 동적으로 생성됨

*출발지 입력 (카카오 주소 검색 연동)*
- 각 사용자별 출발지 입력 필드 제공
- 입력 시 카카오 주소 검색 API를 호출하여 자동완성 드롭다운 표시
- 주소 선택 시 해당 위치의 위도(latitude), 경도(longitude) 좌표를 저장
- 예: "강남역" 입력 → 자동완성에서 선택 → {lat: 37.4979, lng: 127.0276} 저장

*지도 표시*
- 카카오맵 위에 각 사용자의 출발지를 마커로 표시
- 사용자별로 다른 색상의 마커 사용 (구분 용이)
- 모든 출발지가 화면에 보이도록 지도 범위(bounds) 자동 조정

**백엔드 상세:**

*중간지점 계산 API*
```
POST /api/midpoint
Request Body:
{
  "locations": [
    {"name": "사용자1", "lat": 37.4979, "lng": 127.0276},
    {"name": "사용자2", "lat": 37.5563, "lng": 126.9236},
    {"name": "사용자3", "lat": 37.5133, "lng": 127.1001}
  ]
}

Response:
{
  "midpoint": {"lat": 37.5225, "lng": 127.0171},
  "nearestStation": "을지로입구역",
  "address": "서울특별시 중구 을지로 지하 170"
}
```

*중간지점 계산 알고리즘*
1. **기본 계산**: 모든 출발지 좌표의 위도 평균, 경도 평균을 구함
   - 중간 위도 = (lat1 + lat2 + lat3) / 3
   - 중간 경도 = (lng1 + lng2 + lng3) / 3
2. **실제 장소로 스냅핑**: 계산된 좌표는 도로 위가 아닐 수 있으므로, 카카오 로컬 API로 해당 좌표 근처의 실제 랜드마크(지하철역, 주요 상권)를 검색하여 가장 가까운 실제 장소로 보정
3. **공평성 검증**: 각 출발지에서 중간지점까지의 직선 거리를 계산하여 편차가 크지 않은지 확인

---

#### 3단계: 주변 명소(맛집/핫플레이스) 추천

**목표**: 계산된 중간지점 주변의 맛집, 카페, 관광명소 등을 검색하여 추천합니다.

**백엔드 상세:**

*장소 검색 API*
```
GET /api/places?lat=37.5225&lng=127.0171&category=FD6&radius=1000

Response:
{
  "places": [
    {
      "id": "12345678",
      "name": "을지로 골목식당",
      "category": "음식점 > 한식",
      "address": "서울 중구 을지로3가 123",
      "distance": 150,
      "phone": "02-1234-5678",
      "rating": 4.5,
      "placeUrl": "https://place.map.kakao.com/12345678",
      "lat": 37.5230,
      "lng": 127.0180
    },
    ...
  ],
  "totalCount": 15
}
```

*카카오 로컬 API 카테고리 코드*
| 코드 | 카테고리 | 설명 |
|------|----------|------|
| FD6 | 음식점 | 한식, 중식, 일식, 양식 등 모든 음식점 |
| CE7 | 카페 | 커피숍, 디저트 카페 등 |
| AT4 | 관광명소 | 공원, 전시관, 랜드마크 등 |
| CT1 | 문화시설 | 영화관, 공연장, 박물관 등 |
| AD5 | 숙박 | 호텔, 모텔, 게스트하우스 등 |

*검색 로직*
- 중간지점 좌표 기준 반경 500m~2km 내 장소 검색
- 카테고리별로 각각 검색 후 결과 병합
- 거리순 + 평점순으로 정렬하여 상위 결과 반환

**프론트엔드 상세:**
- 추천 장소를 카드 형태로 목록 표시 (장소명, 카테고리, 거리, 평점)
- 카테고리 탭으로 필터링 (전체 / 맛집 / 카페 / 명소 / 문화시설)
- 카드 클릭 시 지도 위에 해당 장소 위치로 이동 + 정보 오버레이 표시
- 지도에 추천 장소들을 마커로 표시 (카테고리별 다른 아이콘)

---

#### 4단계: 교통편 안내

**목표**: 각 사용자의 출발지에서 추천 장소까지의 이동 경로와 소요시간을 안내합니다.

**백엔드 상세:**

*경로 탐색 API*
```
GET /api/directions?originLat=37.4979&originLng=127.0276&destLat=37.5225&destLng=127.0171&mode=car

Response (자가용):
{
  "mode": "car",
  "totalDistance": 5200,
  "totalDuration": 18,
  "tollFee": 0,
  "fuelCost": 650,
  "routes": [
    {"description": "강남대로 → 테헤란로", "distance": 2100, "duration": 7},
    {"description": "테헤란로 → 을지로", "distance": 3100, "duration": 11}
  ]
}

Response (대중교통):
{
  "mode": "transit",
  "totalDuration": 25,
  "totalWalkDistance": 450,
  "fare": 1350,
  "steps": [
    {"type": "walk", "description": "강남역 2호선 승차", "duration": 3},
    {"type": "subway", "line": "2호선", "from": "강남", "to": "을지로입구", "duration": 18, "stationCount": 9},
    {"type": "walk", "description": "을지로입구역 하차 후 도보", "duration": 4}
  ]
}
```

*외부 API 연동*
- **자가용**: 카카오 모빌리티 길찾기 API 사용 (소요시간, 거리, 톨비, 유류비 정보 제공)
- **대중교통**: ODsay 대중교통 API 사용 (버스/지하철 노선, 환승 정보, 요금 제공)
  - 카카오맵 자체에는 대중교통 경로 탐색 API가 없으므로 ODsay를 별도 연동

**프론트엔드 상세:**
- 추천 장소를 선택하면 각 사용자별 교통편 정보 표시
- 자가용 / 대중교통 탭으로 전환 가능
- 소요시간, 거리, 요금 등을 사용자별로 비교하기 쉽게 표시
- 사용자별 소요시간 막대 그래프로 공평성 시각화

---

### Phase 2: 확장 기능

---

#### 5단계: 재추천 기능

**목표**: 추천된 장소가 마음에 들지 않을 때, 차선의 장소를 다시 추천합니다.

**동작 방식:**
1. 사용자가 "다른 장소 추천" 버튼 클릭
2. 현재 추천된 장소를 제외 목록에 추가
3. 중간지점 주변에서 **명소 밀도(Point of Interest Density)**가 높은 순으로 다음 후보 지역 탐색
4. 새로운 지역의 주변 명소와 교통편을 다시 조회하여 제공

*재추천 API*
```
POST /api/midpoint/retry
Request Body:
{
  "locations": [...],
  "excludedAreas": [
    {"lat": 37.5225, "lng": 127.0171, "radius": 500}
  ]
}
```

**재추천 알고리즘:**
- 중간지점을 중심으로 반경을 넓혀가며 후보 지역 탐색
- 각 후보 지역의 주변 명소 수를 카운트
- 명소 밀도가 가장 높은 지역을 우선 추천
- 이미 추천된 지역은 제외

---

#### 6단계: 가중치 적용 (특정 사용자 배려)

**목표**: 특정 사용자가 더 많이/적게 이동하도록 가중치를 조절하여 중간지점을 보정합니다.

**사용 시나리오:**
- "A는 차가 있으니 좀 더 멀리 와도 돼" → A에게 높은 가중치 부여
- "B는 몸이 안 좋으니 가까운 곳으로" → B에게 낮은 가중치 부여
- "C는 아이를 데려오니까 이동거리를 줄여줘" → C에게 낮은 가중치 부여

**구현 방식:**
- 각 사용자에게 1~5 범위의 가중치(weight) 부여 (기본값: 3)
- 가중치가 낮을수록 중간지점이 해당 사용자에게 가까워짐
- 가중 중심점 계산 공식:
  ```
  가중 위도 = Σ(lat_i × weight_i) / Σ(weight_i)
  가중 경도 = Σ(lng_i × weight_i) / Σ(weight_i)
  ```
  - weight가 높은 사용자 → 중간지점에서 더 먼 거리 허용
  - weight가 낮은 사용자 → 중간지점이 해당 사용자 쪽으로 이동

**프론트엔드:**
- 각 사용자 옆에 슬라이더 UI로 가중치 조절
- 가중치 변경 시 실시간으로 중간지점이 지도 위에서 이동하는 애니메이션

---

#### 7단계: 리뷰 기반 장소 추천

**목표**: 주변 명소의 리뷰를 분석하여 긍정적인 평가가 많은 곳 위주로 추천하거나, 사용자가 원하는 분위기의 장소를 추천합니다.

**7-1. 긍정 리뷰 기반 정렬**
- 카카오 장소 상세 정보에서 평점/리뷰 수 데이터 수집
- 평점이 높고 리뷰 수가 일정 이상인 장소를 우선 추천
- 정렬 점수 = (평점 × 0.7) + (리뷰 수 정규화 × 0.3)

**7-2. 분위기/키워드 기반 추천**

*사용자가 선택할 수 있는 분위기 태그:*
| 태그 | 설명 | 검색 키워드 예시 |
|------|------|-----------------|
| 조용한 | 대화하기 좋은 조용한 곳 | "조용한 카페", "프라이빗" |
| 활기찬 | 분위기 있는 활발한 곳 | "분위기 좋은", "핫플" |
| 데이트 | 연인과 방문하기 좋은 곳 | "데이트 맛집", "로맨틱" |
| 단체모임 | 여러 명이 모이기 좋은 곳 | "단체석", "대형", "파티룸" |
| 가성비 | 합리적인 가격의 장소 | "가성비", "학생 맛집" |
| 특별한날 | 기념일, 축하 모임 | "파인다이닝", "코스요리", "와인" |

*구현 방식:*
- 사용자가 분위기 태그를 선택하면 해당 키워드를 포함하여 장소 검색
- 리뷰 텍스트에서 키워드 매칭을 통해 분위기와 일치하는 장소 우선 정렬
- (고급) 리뷰 텍스트 감성 분석을 통한 자동 분류 (향후 ML 모델 적용 가능)

---

## 4. 프로젝트 구조

```
meeting-spot/
│
├── frontend/                           # React 프론트엔드
│   ├── public/
│   │   └── index.html                  # 카카오맵 SDK 스크립트 포함
│   ├── src/
│   │   ├── components/                 # 재사용 가능한 UI 컴포넌트
│   │   │   ├── Map.jsx                 # 카카오맵 렌더링, 마커, 오버레이
│   │   │   ├── LocationInput.jsx       # 출발지 입력 + 주소 자동완성
│   │   │   ├── UserCountSelector.jsx   # 사용자 수 선택
│   │   │   ├── PlaceList.jsx           # 추천 장소 카드 목록
│   │   │   ├── PlaceCard.jsx           # 개별 장소 카드
│   │   │   ├── CategoryFilter.jsx      # 카테고리 필터 탭
│   │   │   ├── DirectionInfo.jsx       # 교통편 정보 표시
│   │   │   └── WeightSlider.jsx        # 가중치 조절 슬라이더 (Phase 2)
│   │   ├── pages/
│   │   │   ├── Home.jsx                # 메인 페이지 (출발지 입력)
│   │   │   └── Result.jsx              # 결과 페이지 (추천 장소 + 교통편)
│   │   ├── api/
│   │   │   ├── midpointApi.js          # 중간지점 API 호출
│   │   │   ├── placeApi.js             # 장소 검색 API 호출
│   │   │   └── directionApi.js         # 경로 탐색 API 호출
│   │   ├── hooks/
│   │   │   ├── useKakaoMap.js          # 카카오맵 커스텀 훅
│   │   │   └── useGeolocation.js       # 현재 위치 가져오기 훅
│   │   ├── App.jsx                     # 라우팅 설정
│   │   ├── main.jsx                    # 엔트리 포인트
│   │   └── index.css                   # Tailwind 설정
│   ├── package.json
│   ├── vite.config.js
│   └── tailwind.config.js
│
├── backend/                            # Spring Boot 백엔드
│   ├── build.gradle                    # Gradle 빌드 설정
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/meetingspot/
│   │   │   │   ├── MeetingSpotApplication.java    # 메인 클래스
│   │   │   │   ├── controller/
│   │   │   │   │   ├── MidpointController.java    # 중간지점 API 엔드포인트
│   │   │   │   │   ├── PlaceController.java       # 장소 검색 API 엔드포인트
│   │   │   │   │   └── DirectionController.java   # 경로 탐색 API 엔드포인트
│   │   │   │   ├── service/
│   │   │   │   │   ├── MidpointService.java       # 중간지점 계산 비즈니스 로직
│   │   │   │   │   ├── PlaceService.java          # 장소 추천 비즈니스 로직
│   │   │   │   │   ├── DirectionService.java      # 경로 탐색 비즈니스 로직
│   │   │   │   │   └── KakaoApiService.java       # 카카오 API 호출 공통 서비스
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   ├── MidpointRequest.java   # 중간지점 요청 DTO
│   │   │   │   │   │   └── RetryRequest.java      # 재추천 요청 DTO
│   │   │   │   │   └── response/
│   │   │   │   │   │   ├── MidpointResponse.java  # 중간지점 응답 DTO
│   │   │   │   │   │   ├── PlaceResponse.java     # 장소 검색 응답 DTO
│   │   │   │   │   │   └── DirectionResponse.java # 경로 탐색 응답 DTO
│   │   │   │   └── config/
│   │   │   │       ├── WebConfig.java             # CORS 설정
│   │   │   │       └── KakaoApiConfig.java        # 카카오 API 설정
│   │   │   └── resources/
│   │   │       └── application.yml                # 설정 파일
│   │   └── test/                                  # 테스트 코드
│   │       └── java/com/meetingspot/
│   │           └── service/
│   │               └── MidpointServiceTest.java   # 중간지점 계산 단위 테스트
│   └── settings.gradle
│
└── README.md                           # 프로젝트 설명 및 실행 방법
```

---

## 5. API 설계

### 5.1 API 목록

| Method | Endpoint | 설명 | Phase |
|--------|----------|------|-------|
| POST | `/api/midpoint` | 출발지 목록을 받아 중간지점 계산 | MVP |
| GET | `/api/places` | 중간지점 주변 장소 검색 | MVP |
| GET | `/api/directions` | 출발지→목적지 경로 탐색 | MVP |
| POST | `/api/midpoint/retry` | 제외 장소를 고려한 재추천 | Phase 2 |

### 5.2 상세 API 명세

#### POST /api/midpoint - 중간지점 계산
```json
// Request
{
  "locations": [
    {"name": "사용자1", "lat": 37.4979, "lng": 127.0276, "weight": 3},
    {"name": "사용자2", "lat": 37.5563, "lng": 126.9236, "weight": 3},
    {"name": "사용자3", "lat": 37.5133, "lng": 127.1001, "weight": 3}
  ]
}

// Response (200 OK)
{
  "success": true,
  "data": {
    "midpoint": {
      "lat": 37.5225,
      "lng": 127.0171,
      "address": "서울특별시 중구 을지로 지하 170",
      "placeName": "을지로입구역"
    },
    "distances": [
      {"name": "사용자1", "distanceKm": 3.2, "estimatedMinutes": 15},
      {"name": "사용자2", "distanceKm": 3.5, "estimatedMinutes": 18},
      {"name": "사용자3", "distanceKm": 3.8, "estimatedMinutes": 20}
    ]
  }
}
```

#### GET /api/places - 주변 장소 검색
```json
// Request: GET /api/places?lat=37.5225&lng=127.0171&category=FD6&radius=1000&page=1

// Response (200 OK)
{
  "success": true,
  "data": {
    "places": [
      {
        "id": "12345678",
        "name": "을지로 골목식당",
        "category": "음식점 > 한식",
        "address": "서울 중구 을지로3가 123",
        "roadAddress": "서울 중구 을지로 100",
        "distance": 150,
        "phone": "02-1234-5678",
        "placeUrl": "https://place.map.kakao.com/12345678",
        "lat": 37.5230,
        "lng": 127.0180
      }
    ],
    "totalCount": 15,
    "currentPage": 1,
    "totalPages": 2
  }
}
```

#### GET /api/directions - 경로 탐색
```json
// Request: GET /api/directions?originLat=37.4979&originLng=127.0276&destLat=37.5225&destLng=127.0171&mode=transit

// Response (200 OK)
{
  "success": true,
  "data": {
    "mode": "transit",
    "totalDuration": 25,
    "totalDistance": 5200,
    "fare": 1350,
    "steps": [
      {
        "type": "walk",
        "description": "강남역 2호선까지 도보",
        "duration": 3,
        "distance": 200
      },
      {
        "type": "subway",
        "line": "2호선",
        "from": "강남",
        "to": "을지로입구",
        "duration": 18,
        "stationCount": 9
      },
      {
        "type": "walk",
        "description": "목적지까지 도보",
        "duration": 4,
        "distance": 250
      }
    ]
  }
}
```

---

## 6. 핵심 알고리즘: 중간지점 계산

### 6.1 기본 알고리즘 (가중 중심점)
```
입력: N개의 출발지 좌표 (lat_i, lng_i)와 가중치 weight_i

1. 가중 중심점 계산:
   center_lat = Σ(lat_i × weight_i) / Σ(weight_i)
   center_lng = Σ(lng_i × weight_i) / Σ(weight_i)

2. 실제 장소로 스냅핑:
   - 계산된 좌표 주변 500m 내 주요 랜드마크(지하철역, 상권) 검색
   - 가장 가까운 실제 장소의 좌표로 보정

3. 공평성 검증:
   - 각 출발지에서 중간지점까지의 거리 계산
   - 최대 거리와 최소 거리의 차이가 전체 평균의 50% 이하인지 확인
   - 초과 시 경고 메시지 표시
```

### 6.2 고려사항
- 지구는 구형이므로 단순 평균이 아닌 **Haversine 공식**으로 거리 계산
- 직선 거리가 아닌 **실제 도로/교통 기반 거리**로 보정하면 정확도 향상 (Phase 2)
- 바다, 산 등 이동 불가 지역에 중간지점이 찍히지 않도록 검증 필요

---

## 7. 사용자 흐름 (User Flow)

```
[시작]
  │
  ▼
사용자 수 입력 (2~10명)
  │
  ▼
각 사용자 출발지 입력 (주소 검색)
  │
  ├── (선택) 가중치 조절 ── Phase 2
  │
  ▼
"중간지점 찾기" 버튼 클릭
  │
  ▼
중간지점 계산 + 지도에 표시
  │
  ▼
주변 맛집/카페/명소 목록 표시
  │
  ├── 카테고리 필터링
  ├── (선택) 분위기 태그 선택 ── Phase 2
  │
  ▼
장소 선택
  │
  ├── 마음에 안 들면 → "다른 장소" 클릭 → 재추천 ── Phase 2
  │
  ▼
각 사용자별 교통편 안내 (자가용/대중교통)
  │
  ▼
[완료] 장소 확정 및 공유
```

---

## 8. 구현 일정 (예상)

| 단계 | 내용 | 주요 산출물 |
|------|------|------------|
| 1단계 | 프로젝트 셋업 | React/Spring Boot 프로젝트 생성, API 키 발급 |
| 2단계 | 출발지 입력 + 중간지점 | 주소 검색 UI, 중간지점 계산 API, 지도 표시 |
| 3단계 | 장소 추천 | 카테고리별 장소 검색, 카드 목록 UI |
| 4단계 | 교통편 | 자가용/대중교통 경로 탐색, 소요시간 표시 |
| 5단계 | 재추천 | 제외 장소 기반 차선 추천 로직 |
| 6단계 | 가중치 | 슬라이더 UI, 가중 중심점 알고리즘 |
| 7단계 | 리뷰 기반 | 평점 정렬, 분위기 태그 필터링 |

---

## 9. 검증 방법

### MVP 테스트 시나리오
1. **중간지점 계산 정확성**: 서울 내 3곳(강남, 홍대, 잠실) 입력 → 중간지점이 합리적인 위치(을지로, 종로 등)에 나오는지 확인
2. **장소 검색**: 중간지점 주변 맛집/카페가 정상적으로 검색되는지 확인
3. **교통편**: 각 출발지에서 중간지점까지 자가용/대중교통 경로가 정상 조회되는지 확인
4. **API 통신**: 프론트엔드 ↔ 백엔드 간 데이터 흐름이 정상인지 확인
5. **엣지 케이스**: 2명만 입력, 같은 위치 입력, 매우 먼 거리(서울-부산) 입력 시 동작 확인
