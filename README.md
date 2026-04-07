# 중간지점 만남 장소 추천 서비스

여러 명의 출발지를 입력하면 모두에게 공평한 중간지점을 계산하고, 주변 맛집·카페·핫플레이스를 추천해주는 서비스입니다.

## 주요 기능

- 최대 6명의 출발지 입력 (카카오맵 주소 자동완성)
- minimax 알고리즘으로 공평한 중간 지하철역 계산
- 중간지점 주변 장소 추천 (음식점, 카페, 관광지, 문화시설)
- 출발지별 자가용 소요시간·거리·톨비 안내
- 카카오맵 연동 길찾기 (자가용 / 대중교통)

## 기술 스택

| 구분 | 기술 |
|---|---|
| 프론트엔드 | React 18, Vite, Tailwind CSS, Axios |
| 백엔드 | Spring Boot 3, Java 17, Gradle |
| 외부 API | 카카오맵 JS SDK, 카카오 로컬 API, 카카오 모빌리티 API |

## 실행 방법

### 사전 준비
[카카오 개발자 센터](https://developers.kakao.com)에서 앱을 생성하고 아래 두 가지 키를 발급받아야 합니다.
- JavaScript 키
- REST API 키

### 프론트엔드

```bash
cd frontend
cp .env.example .env
# .env 파일에 VITE_KAKAO_JS_KEY 입력
npm install
npm run dev
```

### 백엔드

```bash
cd backend
# 환경변수 KAKAO_REST_API_KEY 설정 후 실행
export KAKAO_REST_API_KEY=your_rest_api_key
./gradlew bootRun
```

접속: http://localhost:5173
