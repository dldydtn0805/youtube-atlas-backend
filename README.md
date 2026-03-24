# youtube-atlas-backend

`World-Best-YouTube`의 YouTube 조회, 실시간 댓글, 급상승 스냅샷 기능을 Spring Boot로 완전 이전하기 위한 백엔드 프로젝트입니다.

## 현재 구현 범위

- 국가별 카테고리 조회 API
- 국가/카테고리별 인기 영상 조회 API
- 영상별 댓글 조회/생성 API
- STOMP WebSocket 기반 실시간 댓글 브로드캐스트
- 급상승 스냅샷 동기화 API
- 급상승 시그널 조회 API
- H2 로컬 실행 + PostgreSQL 전환 가능한 기본 설정

## 실행

```bash
cd youtube-atlas-backend
./gradlew bootRun
```

테스트:

```bash
cd youtube-atlas-backend
./gradlew test
```

## 환경 변수

```bash
YOUTUBE_API_KEY=your_youtube_api_key
DB_URL=jdbc:postgresql://localhost:5432/youtube_atlas
DB_USERNAME=postgres
DB_PASSWORD=postgres
ALLOWED_ORIGINS=http://localhost:5173
TRENDING_SCHEDULER_ENABLED=false
TRENDING_SYNC_CRON=0 0/30 * * * *
TRENDING_SYNC_MAX_PAGES_PER_SOURCE=3
```

`DB_*`를 비워 두면 로컬에선 H2 인메모리 DB로 실행됩니다.

## API

- `GET /api/catalog/regions/{regionCode}/categories`
- `GET /api/catalog/regions/{regionCode}/categories/{categoryId}/videos?pageToken=...`
- `GET /api/videos/{videoId}/comments`
- `POST /api/videos/{videoId}/comments`
- `GET /api/trending/signals?regionCode=KR&categoryId=10&videoIds=abc&videoIds=def`
- `POST /api/trending/sync`

카테고리 조회 관련 참고:

- 백엔드는 카테고리를 병합하지 않고 YouTube 원본 카테고리 ID를 그대로 사용합니다.
- 예를 들어 음악은 `10`, 엔터테인먼트는 `24`, 코미디는 `23`처럼 응답에 내려온 `id` 값을 그대로 다음 요청에 사용하면 됩니다.
- `GET /api/catalog/regions/{regionCode}/categories` 응답의 `sourceIds` 는 현재 원본 카테고리 ID 1개만 담습니다.

급상승 동기화 참고:

- `POST /api/trending/sync` 요청의 `categoryId` 도 원본 YouTube 카테고리 ID 기준으로 맞추는 것을 권장합니다.
- `sourceCategoryIds` 는 필요할 때만 직접 여러 개를 전달할 수 있습니다.

## 실시간 댓글

- WebSocket endpoint: `/ws`
- subscribe topic: `/topic/videos/{videoId}/comments`
