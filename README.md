# youtube-atlas-backend

`World-Best-YouTube`의 YouTube 조회, 실시간 댓글, 급상승 스냅샷 기능을 Spring Boot로 제공하는 백엔드입니다.

## 현재 구현 범위

- 국가별 카테고리 조회
- 국가/카테고리별 인기 영상 조회
- 영상별 댓글 조회/생성
- STOMP WebSocket 기반 실시간 댓글 브로드캐스트
- 급상승 스냅샷 동기화
- 급상승 시그널 조회
- H2 로컬 실행 + PostgreSQL 전환 가능한 기본 설정

## 실행

프로젝트 루트에서:

```bash
cd youtube-atlas-backend
./gradlew bootRun
```

테스트:

```bash
cd youtube-atlas-backend
./gradlew test
```

기본 포트는 `8080`입니다.

## 환경 변수

필수:

```bash
YOUTUBE_API_KEY=your_youtube_api_key
```

주요 선택값:

```bash
PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/youtube_atlas
DB_USERNAME=postgres
DB_PASSWORD=postgres
ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
YOUTUBE_CATEGORY_LANGUAGE=ko
TRENDING_SCHEDULER_ENABLED=false
TRENDING_SYNC_CRON=0 0/30 * * * *
TRENDING_SYNC_MAX_PAGES_PER_SOURCE=3
```

- `DB_*`를 비워 두면 로컬에서는 H2 인메모리 DB로 실행됩니다.
- `ALLOWED_ORIGINS` 기본값에는 로컬 개발 주소와 Vercel 배포 주소 패턴이 포함됩니다.
- `TRENDING_SYNC_MAX_PAGES_PER_SOURCE` 는 급상승 동기화 시 소스 카테고리별로 몇 페이지까지 수집할지 결정합니다.

## API 요약

- `GET /api/catalog/regions/{regionCode}/categories`
- `GET /api/catalog/regions/{regionCode}/categories/{categoryId}/videos?pageToken=...`
- `GET /api/videos/{videoId}/comments`
- `POST /api/videos/{videoId}/comments`
- `GET /api/trending/signals?regionCode=KR&categoryId=0&videoIds=abc&videoIds=def`
- `POST /api/trending/sync`

## 카테고리 API

### `GET /api/catalog/regions/{regionCode}/categories`

현재 카테고리 병합은 해제되어 있습니다. 백엔드는 YouTube 원본 카테고리 ID를 그대로 사용합니다.

- 응답 첫 번째 항목은 항상 전체 카테고리이며 `id` 는 `0`입니다.
- 그 외 카테고리는 YouTube 원본 `id` 를 그대로 반환합니다.
- 예: 음악 `10`, 코미디 `23`, 엔터테인먼트 `24`
- `sourceIds` 는 전체 카테고리에서는 빈 배열이고, 일반 카테고리에서는 현재 원본 카테고리 ID 1개만 담습니다.
- 현재 `27`(Education), `42`(Shorts)는 목록에서 제외됩니다.

예시:

```json
[
  {
    "id": "0",
    "label": "전체",
    "description": "카테고리 구분 없이 현재 국가 전체 인기 영상을 보여줍니다.",
    "sourceIds": []
  },
  {
    "id": "10",
    "label": "Music",
    "description": "뮤직비디오, 라이브, 음원 관련 인기 영상을 확인할 수 있습니다.",
    "sourceIds": ["10"]
  }
]
```

### `GET /api/catalog/regions/{regionCode}/categories/{categoryId}/videos`

- `categoryId=0` 이면 해당 국가 전체 인기 영상을 조회합니다.
- 그 외에는 `GET /categories` 에서 받은 원본 카테고리 ID를 그대로 사용하면 됩니다.
- `pageToken` 은 다음 페이지가 있을 때만 전달합니다.
- 각 영상 항목에는 저장된 급상승 시그널이 있으면 `trend` 가 함께 붙습니다.

응답 예시:

```json
{
  "categoryId": "10",
  "label": "Music",
  "description": "뮤직비디오, 라이브, 음원 관련 인기 영상을 확인할 수 있습니다.",
  "items": [
    {
      "id": "abc123",
      "contentDetails": {
        "duration": "PT3M42S"
      },
      "snippet": {
        "title": "Sample title",
        "channelTitle": "Sample channel",
        "categoryId": "10",
        "publishedAt": "2026-03-24T10:00:00Z",
        "thumbnails": {
          "default": {
            "url": "https://example.com/default.jpg",
            "width": 120,
            "height": 90
          }
        }
      },
      "statistics": {
        "viewCount": 123456
      },
      "trend": {
        "categoryLabel": "Music",
        "currentRank": 3,
        "previousRank": 5,
        "rankChange": 2,
        "currentViewCount": 123456,
        "previousViewCount": 120000,
        "viewCountDelta": 3456,
        "isNew": false,
        "capturedAt": "2026-03-24T12:00:00Z"
      }
    }
  ],
  "nextPageToken": "CAoQAA"
}
```

## 댓글 API

### `GET /api/videos/{videoId}/comments`

영상의 댓글을 생성 시각 오름차순으로 반환합니다.

### `POST /api/videos/{videoId}/comments`

요청 본문:

```json
{
  "author": "yongsoo",
  "content": "지금 보고 있어요",
  "clientId": "web-session-123"
}
```

- `author` 가 비어 있으면 `"익명"` 으로 저장됩니다.
- 같은 `clientId` 기준으로 5초 쿨다운이 있습니다.
- 같은 `clientId` 가 같은 메시지를 30초 안에 다시 보내면 중복으로 막습니다.
- 댓글 응답 JSON은 `snake_case` 입니다.

실시간 브로드캐스트:

- WebSocket endpoint: `/ws`
- subscribe topic: `/topic/videos/{videoId}/comments`

## 급상승 API

### `GET /api/trending/signals`

쿼리 파라미터:

- `regionCode`
- `categoryId`
- `videoIds` 반복 전달

예시:

```text
/api/trending/signals?regionCode=KR&categoryId=0&videoIds=abc&videoIds=def
```

- 트렌드 시그널은 현재 `전체(0)` 기준으로만 저장하고 조회합니다.
- 카테고리별 영상 응답에는 트렌드가 붙지 않고, 전체 인기 영상 응답에서만 트렌드가 붙습니다.

### `POST /api/trending/sync`

요청 본문:

```json
{
  "regionCode": "KR",
  "categoryId": "0",
  "categoryLabel": "전체",
  "sourceCategoryIds": []
}
```

- `regionCode`, `categoryId`, `categoryLabel` 은 필수입니다.
- 현재 트렌드 동기화는 국가 전체 인기 영상 기준으로만 동작합니다.
- `sourceCategoryIds` 값은 보내더라도 전체 인기 영상 기준으로 동기화합니다.

응답 예시:

```json
{
  "regionCode": "KR",
  "categoryId": "0",
  "syncedVideos": 50,
  "signalCount": 50,
  "capturedAt": "2026-03-24T12:00:00Z"
}
```

## 에러 응답

공통 에러 응답 형식:

```json
{
  "code": "bad_request",
  "message": "categoryId는 필수입니다.",
  "retryAfterSeconds": null
}
```

- 댓글 정책 위반은 `409 Conflict`
- 잘못된 요청은 `400 Bad Request`
- 존재하지 않는 카테고리는 `404 Not Found`
- YouTube API 연동 실패는 `502 Bad Gateway`
