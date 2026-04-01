# youtube-atlas-backend

`World-Best-YouTube`의 YouTube 조회, Google 로그인, 실시간 댓글, 급상승 스냅샷 기능을 Spring Boot로 제공하는 백엔드입니다.

## 현재 구현 범위

- 국가별 카테고리 조회
- 국가/카테고리별 인기 영상 조회
- Google OAuth authorization code 기반 로그인
- 사용자별 스트리머 즐겨찾기
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
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret
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
AUTH_SESSION_TTL_DAYS=30
TRENDING_SCHEDULER_ENABLED=false
TRENDING_SYNC_CRON=0 0 * * * *
TRENDING_SYNC_MAX_PAGES_PER_SOURCE=4
```

- `DB_*`를 비워 두면 로컬에서는 H2 인메모리 DB로 실행됩니다.
- `ALLOWED_ORIGINS` 기본값에는 로컬 개발 주소와 Vercel 배포 주소 패턴이 포함됩니다.
- `GOOGLE_CLIENT_ID` 는 프론트의 Google OAuth Client ID와 동일해야 합니다.
- `GOOGLE_CLIENT_SECRET` 는 같은 Google OAuth Web Client의 secret 이어야 합니다.
- `TRENDING_SYNC_MAX_PAGES_PER_SOURCE` 는 급상승 동기화 시 소스 카테고리별로 몇 페이지까지 수집할지 결정합니다.

## API 요약

- `GET /api/catalog/regions/{regionCode}/categories`
- `GET /api/catalog/regions/{regionCode}/categories/{categoryId}/videos?pageToken=...`
- `POST /api/auth/google`
- `GET /api/auth/me`
- `DELETE /api/auth/session`
- `GET /api/me/favorite-streamers`
- `POST /api/me/favorite-streamers`
- `DELETE /api/me/favorite-streamers/{channelId}`
- `GET /api/videos/{videoId}/comments`
- `POST /api/videos/{videoId}/comments`
- `GET /api/trending/signals?regionCode=KR&categoryId=0&videoIds=abc&videoIds=def`
- `GET /api/trending/realtime-surging?regionCode=KR`
- `POST /api/trending/sync`

## 로그인 API

### `POST /api/auth/google`

프론트에서 Google OAuth popup으로 받은 authorization `code` 와 현재 페이지 `redirectUri` 를 전달하면, 백엔드가 Google token endpoint와 코드를 교환한 뒤 자체 세션 토큰을 발급합니다.

요청 본문:

```json
{
  "code": "google-authorization-code-from-frontend",
  "redirectUri": "https://youtube-atlas.vercel.app"
}
```

요청 헤더:

```text
Content-Type: application/json
X-Requested-With: XmlHttpRequest
Origin: https://youtube-atlas.vercel.app
```

응답 예시:

```json
{
  "accessToken": "our-session-token",
  "tokenType": "Bearer",
  "expiresAt": "2026-05-01T06:00:00Z",
  "user": {
    "id": 1,
    "email": "atlas@example.com",
    "displayName": "Atlas User",
    "pictureUrl": "https://lh3.googleusercontent.com/...",
    "lastLoginAt": "2026-04-01T06:00:00Z"
  }
}
```

### `GET /api/auth/me`

헤더:

```text
Authorization: Bearer {accessToken}
```

현재 로그인한 사용자 정보를 반환합니다.

### `DELETE /api/auth/session`

헤더:

```text
Authorization: Bearer {accessToken}
```

현재 세션을 로그아웃합니다.

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
        "channelId": "UCxxxxxxxxxxxx",
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

## 즐겨찾기 API

모든 즐겨찾기 API는 아래 헤더가 필요합니다.

```text
Authorization: Bearer {accessToken}
```

### `GET /api/me/favorite-streamers`

현재 로그인한 사용자의 스트리머 즐겨찾기 목록을 최근 추가 순으로 반환합니다.

### `GET /api/me/favorite-streamers/videos`

쿼리 파라미터:

- `regionCode`
- `pageToken` 선택

전체 인기 영상 기준으로 페이지를 훑으면서, 현재 로그인한 사용자가 즐겨찾기한 채널의 영상만 모아서 반환합니다.
- 응답 형식은 `GET /api/catalog/regions/{regionCode}/categories/0/videos` 와 동일합니다.
- 트렌드 시그널도 전체 인기 영상 기준으로 함께 붙습니다.
- 즐겨찾기 채널이 없으면 빈 목록과 `nextPageToken = null` 을 반환합니다.

### `POST /api/me/favorite-streamers`

요청 본문:

```json
{
  "channelId": "UCxxxxxxxxxxxx",
  "channelTitle": "Sample channel",
  "thumbnailUrl": "https://example.com/channel.jpg"
}
```

- 같은 사용자가 같은 `channelId` 를 다시 저장하면 기존 항목을 재사용하고 최신 제목/썸네일로 갱신합니다.

### `DELETE /api/me/favorite-streamers/{channelId}`

해당 스트리머 즐겨찾기를 삭제합니다. 항목이 없어도 `204 No Content` 를 반환합니다.

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
- `Authorization: Bearer {accessToken}` 헤더가 있으면 댓글 작성자는 로그인 사용자 이름으로 고정됩니다.
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

### `GET /api/trending/realtime-surging`

쿼리 파라미터:

- `regionCode`

예시:

```text
/api/trending/realtime-surging?regionCode=KR
```

- 전체 인기 영상 기준으로 저장된 트렌드 시그널 중 `rankChange >= 5` 인 영상만 반환합니다.
- 응답은 이미 `rankChange` 내림차순, `currentRank` 오름차순으로 정렬되어 있습니다.
- `totalCount` 는 현재 실시간 급상승 전체 개수입니다.

응답 예시:

```json
{
  "regionCode": "KR",
  "categoryId": "0",
  "categoryLabel": "전체",
  "rankChangeThreshold": 5,
  "totalCount": 12,
  "capturedAt": "2026-04-01T05:30:00Z",
  "items": [
    {
      "regionCode": "KR",
      "categoryId": "0",
      "categoryLabel": "전체",
      "videoId": "abc",
      "currentRank": 3,
      "previousRank": 11,
      "rankChange": 8,
      "currentViewCount": 1900000,
      "previousViewCount": 1700000,
      "viewCountDelta": 200000,
      "isNew": false,
      "title": "Example",
      "channelTitle": "Atlas",
      "thumbnailUrl": "https://example.com/thumb.jpg",
      "capturedAt": "2026-04-01T05:30:00Z"
    }
  ]
}
```

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
