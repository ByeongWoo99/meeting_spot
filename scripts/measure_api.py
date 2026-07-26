import requests
import csv
import time
import random
import re
import os
from datetime import datetime

BASE_URL = "http://localhost:8080"
OUTPUT_FILE = "scripts/results_after_cache.csv"
LOG_FILE = "backend/logs/app.log"
DELAY_SECONDS = 5

# 권역별 좌표 목록
LOCATIONS = {
    "서울": [
        {"name": "강남역", "lat": 37.4979, "lng": 127.0276},
        {"name": "홍대입구역", "lat": 37.5572, "lng": 126.9241},
        {"name": "신촌역", "lat": 37.5553, "lng": 126.9368},
        {"name": "건대입구역", "lat": 37.5403, "lng": 127.0698},
        {"name": "강동구청역", "lat": 37.5303, "lng": 127.1238},
        {"name": "서울역", "lat": 37.5547, "lng": 126.9707},
    ],
    "경기남부": [
        {"name": "수원역", "lat": 37.2664, "lng": 127.0000},
        {"name": "분당서현역", "lat": 37.3838, "lng": 127.1215},
        {"name": "안양역", "lat": 37.3942, "lng": 126.9568},
        {"name": "평택역", "lat": 36.9921, "lng": 127.0891},
    ],
    "경기북부": [
        {"name": "의정부역", "lat": 37.7381, "lng": 127.0447},
        {"name": "일산역", "lat": 37.6758, "lng": 126.7700},
        {"name": "파주금촌역", "lat": 37.7514, "lng": 126.7800},
    ],
    "인천": [
        {"name": "인천역", "lat": 37.4735, "lng": 126.6164},
        {"name": "부평역", "lat": 37.4890, "lng": 126.7228},
        {"name": "송도역", "lat": 37.3855, "lng": 126.6564},
    ],
    "충청": [
        {"name": "대전역", "lat": 36.3323, "lng": 127.4344},
        {"name": "천안역", "lat": 36.8090, "lng": 127.1478},
    ],
    "경상": [
        {"name": "부산역", "lat": 35.1150, "lng": 129.0422},
        {"name": "대구역", "lat": 35.8793, "lng": 128.6266},
    ],
}

GYEONGGI = LOCATIONS["경기남부"] + LOCATIONS["경기북부"] + LOCATIONS["인천"]
JIBANG = LOCATIONS["충청"] + LOCATIONS["경상"]

CASES = [
    ("케이스1_경기+경기",           [GYEONGGI, GYEONGGI]),
    ("케이스2_서울+경기",           [LOCATIONS["서울"], GYEONGGI]),
    ("케이스3_서울+지방",           [LOCATIONS["서울"], JIBANG]),
    ("케이스4_서울+경기+지방",      [LOCATIONS["서울"], GYEONGGI, JIBANG]),
    ("케이스5_서울+서울+경기+지방", [LOCATIONS["서울"], LOCATIONS["서울"], GYEONGGI, JIBANG]),
]

REPEAT = 5


def select_locations(region_pools):
    pool_map = {}
    for idx, pool in enumerate(region_pools):
        pid = id(pool)
        if pid not in pool_map:
            pool_map[pid] = {"pool": pool, "indices": []}
        pool_map[pid]["indices"].append(idx)

    selected = [None] * len(region_pools)
    for entry in pool_map.values():
        sampled = random.sample(entry["pool"], len(entry["indices"]))
        for i, idx in enumerate(entry["indices"]):
            selected[idx] = sampled[i]
    return selected


def get_log_size():
    if not os.path.exists(LOG_FILE):
        return 0
    return os.path.getsize(LOG_FILE)


def parse_log_since(offset):
    """offset 이후에 추가된 로그에서 API 시간을 추출"""
    if not os.path.exists(LOG_FILE):
        return [], [], []

    for enc in ("utf-8", "cp949", "utf-8-sig"):
        try:
            with open(LOG_FILE, "r", encoding=enc, errors="ignore") as f:
                f.seek(offset)
                new_logs = f.read()
            break
        except Exception:
            continue
    else:
        return [], [], []

    # 클래스명 기반 매칭 (한글 불필요)
    # TransitService : OdSay API ???: 305ms
    odsay_times = [int(m) for m in re.findall(r"TransitService\s+: OdSay API [^:]+: (\d+)ms", new_logs)]

    # MidpointService : Kakao ??????: 267ms  (장소수 병렬 조회)
    kakao_count_times = [int(m) for m in re.findall(r"MidpointService\s+: Kakao [^:]+: (\d+)ms", new_logs)]

    # PlaceService : Kakao ??????: 320ms  (장소 목록 검색)
    kakao_place_times = [int(m) for m in re.findall(r"PlaceService\s+: Kakao [^:]+: (\d+)ms", new_logs)]

    return odsay_times, kakao_count_times, kakao_place_times


def call_midpoint(locations):
    payload = {
        "locations": [{"name": loc["name"], "lat": loc["lat"], "lng": loc["lng"]} for loc in locations],
        "category": "ALL"
    }
    start = time.time()
    try:
        response = requests.post(f"{BASE_URL}/api/midpoint", json=payload, timeout=120)
        elapsed_ms = int((time.time() - start) * 1000)
        if response.status_code == 200:
            candidates = response.json().get("candidates", [])
            return elapsed_ms, candidates, "성공", ""
        else:
            return elapsed_ms, [], "실패", f"HTTP {response.status_code}"
    except Exception as e:
        return int((time.time() - start) * 1000), [], "실패", str(e)


def main():
    if not os.path.exists(LOG_FILE):
        print(f"[경고] 로그 파일을 찾을 수 없습니다: {LOG_FILE}")
        print("Spring Boot가 실행 중인지 확인하세요.\n")

    random.seed(42)
    print(f"API 응답시간 측정 시작 → {OUTPUT_FILE}\n")

    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow([
            "요청시각", "케이스", "인원수", "출발지목록",
            "1순위역", "2순위역",
            "전체응답시간(ms)",
            "OdSay호출횟수", "OdSay총합(ms)", "OdSay평균(ms)",
            "Kakao장소수조회(ms)",
            "Kakao장소목록횟수", "Kakao장소목록총합(ms)",
            "성공여부", "에러"
        ])

        total = len(CASES) * REPEAT
        count = 0

        for case_name, region_pools in CASES:
            for i in range(REPEAT):
                count += 1
                selected = select_locations(region_pools)
                origins = ", ".join(loc["name"] for loc in selected)

                print(f"[{count}/{total}] {case_name} ({i+1}회) — {origins}")

                # 요청 전 로그 파일 크기 기록
                log_offset = get_log_size()

                elapsed_ms, candidates, status, error = call_midpoint(selected)

                station1 = candidates[0].get("nearestStation", "없음") if len(candidates) > 0 else "없음"
                station2 = candidates[1].get("nearestStation", "없음") if len(candidates) > 1 else "없음"

                # 요청 후 로그 파싱
                odsay_times, kakao_count_times, kakao_place_times = parse_log_since(log_offset)

                odsay_count = len(odsay_times)
                odsay_total = sum(odsay_times)
                odsay_avg = int(odsay_total / odsay_count) if odsay_count > 0 else 0
                kakao_count_ms = kakao_count_times[0] if kakao_count_times else ""
                kakao_place_count = len(kakao_place_times)
                kakao_place_total = sum(kakao_place_times)

                print(f"  → 결과: {station1} / {station2} | 전체: {elapsed_ms}ms")
                print(f"  → OdSay: {odsay_count}회 호출, 총 {odsay_total}ms, 평균 {odsay_avg}ms")
                print(f"  → Kakao 장소수 조회: {kakao_count_ms}ms")
                print(f"  → Kakao 장소목록: {kakao_place_count}회, 총 {kakao_place_total}ms")
                if error:
                    print(f"  → 에러: {error}")

                writer.writerow([
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                    case_name,
                    len(selected),
                    origins,
                    station1,
                    station2,
                    elapsed_ms,
                    odsay_count,
                    odsay_total,
                    odsay_avg,
                    kakao_count_ms,
                    kakao_place_count,
                    kakao_place_total,
                    status,
                    error
                ])
                f.flush()

                if count < total:
                    print(f"  → {DELAY_SECONDS}초 대기 중...\n")
                    time.sleep(DELAY_SECONDS)

    print(f"\n측정 완료 → {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
