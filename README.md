# 중간지점 만남 장소 추천 서비스

여러 명의 출발지를 입력하면 모두에게 공평한 중간지점을 계산하고, 주변 맛집·카페·핫플레이스를 추천해주는 서비스입니다.

## 주요 기능

**중간지점 찾기 모드**
- 2~4명의 출발지 입력 (카카오맵 주소 자동완성)
- OdSay 대중교통 API 기반 소요시간으로 공평한 중간 지하철역 계산
- 탐색 방식 선택: 대중교통 소요시간만 / 장소 밀도 반영
- 만남 목적별 카테고리 선택 (전체 · 음식점 · 카페 · 문화·명소)
- Gemini AI가 생성하는 중간지점 설명
- 중간지점 주변 추천 장소 목록 및 카카오맵 길찾기 연동

**주변 명소 찾기 모드**
- 단일 위치 입력으로 주변 맛집·카페·명소 탐색

**공통**
- 출발지별 자가용 소요시간·거리·톨비 안내
- API 응답 DB 캐싱으로 재요청 시 응답시간 91% 단축

## 기술 스택

| 구분 | 기술 |
|---|---|
| 프론트엔드 | React 18, Vite, Tailwind CSS |
| 백엔드 | Spring Boot 3.5, Java 17, Spring Data JPA |
| DB | PostgreSQL |
| 외부 API | 카카오맵 JS SDK, 카카오 로컬 API, 카카오 모빌리티 API, OdSay 대중교통 API, Gemini API |
| 인프라 | AWS EC2, GitHub Actions CI/CD |

## 실행 방법

### 사전 준비

아래 서비스에서 API 키를 발급받아야 합니다.

| 키 이름 | 발급처 |
|---|---|
| `VITE_KAKAO_JS_KEY` | [카카오 개발자 센터](https://developers.kakao.com) → JavaScript 키 |
| `KAKAO_REST_API_KEY` | [카카오 개발자 센터](https://developers.kakao.com) → REST API 키 |
| `ODSAY_API_KEY` | [OdSay 개발자 센터](https://lab.odsay.com) |
| `GEMINI_API_KEY` | [Google AI Studio](https://aistudio.google.com) |

### PostgreSQL 설정

```sql
CREATE DATABASE meetingspot;
CREATE USER meetingspot_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE meetingspot TO meetingspot_user;
GRANT CREATE ON SCHEMA public TO meetingspot_user;
```

### 프론트엔드

```bash
cd frontend
cp .env.example .env
# .env 파일에 VITE_KAKAO_JS_KEY 입력
npm install
npm run dev
```

### 백엔드

`backend/.env` 파일을 생성하고 환경변수를 입력합니다.

```
KAKAO_REST_API_KEY=your_kakao_rest_api_key
ODSAY_API_KEY=your_odsay_api_key
GEMINI_API_KEY=your_gemini_api_key
DB_PASSWORD=your_db_password
```

```bash
cd backend
./gradlew bootRun
```

서버 실행 시 JPA가 `transit_cache`, `place_cache` 테이블을 자동 생성합니다.

## 캐싱 구조

반복 API 호출을 줄이기 위해 PostgreSQL에 결과를 캐싱합니다.

| 테이블 | 캐싱 대상 | TTL |
|---|---|---|
| `transit_cache` | OdSay 출발지→역 대중교통 소요시간 | 30일 |
| `place_cache` | 카카오 역별 카테고리 장소 수 | 7일 |

캐싱 적용 후 평균 응답시간: **2,889ms → 258ms (91% 단축)**

## CI/CD

`main` 브랜치에 push 시 GitHub Actions가 자동으로 빌드 및 EC2 배포를 수행합니다.

```
build-backend → deploy JAR to EC2 → restart service
      ↓ (완료 후)
build-frontend → deploy dist/ to EC2
```

GitHub Secrets에 아래 값을 등록해야 합니다.

| Secret | 설명 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_USERNAME` | SSH 접속 유저명 |
| `EC2_KEY` | EC2 PEM 키 내용 |
| `VITE_KAKAO_JS_KEY` | 카카오 JavaScript 키 |
