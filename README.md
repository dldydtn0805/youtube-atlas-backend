# youtube-atlas-backend

`World-Best-YouTube`의 YouTube 조회, Google 로그인, 실시간 댓글, 급상승 스냅샷, 랭킹 기반 포인트 게임과 시즌 코인 기능을 Spring Boot로 제공하는 백엔드입니다.

## 현재 구현 범위

- 국가별 카테고리 조회
- 국가/카테고리별 인기 영상 조회
- Google OAuth authorization code 기반 로그인
- 사용자별 스트리머 즐겨찾기
- 영상별 댓글 조회/생성
- STOMP WebSocket 기반 실시간 댓글 브로드캐스트
- 급상승 스냅샷 동기화
- 급상승 시그널 조회
- 랭킹 기반 포인트 게임 시즌/지갑/매수/매도/리더보드
- Top 200 시즌 코인 생산과 코인 개요 조회
- 시즌 종료 시 오픈 포지션 자동 청산
- H2 로컬 실행 + PostgreSQL 전환 가능한 기본 설정

## 실행

프로젝트 루트에서:

```bash
cd youtube-atlas-backend
./gradlew bootRun
```

로컬 시크릿을 파일로 관리하면서 실행하려면:

```bash
cp youtube-atlas-backend/.env.local.example youtube-atlas-backend/.env.local
# .env.local 에 로컬 시크릿 입력
./scripts/boot-local.sh
```

로컬 백엔드를 원격 PostgreSQL에 붙여서 실행하려면 `.env.local`에 아래 값을 채운 뒤 같은 스크립트를 사용하면 됩니다:

```bash
REMOTE_DB_URL=jdbc:postgresql://your-remote-host:5432/your_remote_db?sslmode=require
REMOTE_DB_USERNAME=your_remote_user
REMOTE_DB_PASSWORD=your_remote_password
REMOTE_DB_DRIVER=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

`REMOTE_DB_URL` 이 있으면 `./scripts/boot-local.sh` 는 H2 대신 원격 PostgreSQL에 연결합니다.

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
SPRING_JPA_HIBERNATE_DDL_AUTO=update
ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
YOUTUBE_CATEGORY_LANGUAGE=ko
AUTH_SESSION_TTL_DAYS=30
ADMIN_ALLOWED_EMAILS=admin@example.com,owner@example.com
GAME_SCHEDULER_ENABLED=false
GAME_SETTLEMENT_CRON=0 */5 * * * *
GAME_PAYOUT_SLOT_MINUTES=5
TRENDING_SCHEDULER_ENABLED=false
TRENDING_SYNC_CRON=0 0 * * * *
TRENDING_SYNC_MAX_PAGES_PER_SOURCE=4
```

- `DB_*`를 비워 두면 로컬에서는 H2 인메모리 DB로 실행됩니다.
- 배포 DB에 로컬 앱을 직접 붙일 때는 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` 또는 `none` 으로 두는 편이 안전합니다.
- `ALLOWED_ORIGINS` 기본값에는 로컬 개발 주소와 Vercel 배포 주소 패턴이 포함됩니다.
- `GOOGLE_CLIENT_ID` 는 프론트의 Google OAuth Client ID와 동일해야 합니다.
- `GOOGLE_CLIENT_SECRET` 는 같은 Google OAuth Web Client의 secret 이어야 합니다.
- `GAME_SCHEDULER_ENABLED=true` 로 두면 종료 시즌 자동 정리와 다음 시즌 생성이 주기적으로 실행됩니다.
- 로컬에서 빠르게 확인하려면 `GAME_SETTLEMENT_CRON=0 */1 * * * *` 로 두면 1분마다 시즌 정리를 테스트할 수 있습니다.
- `ADMIN_ALLOWED_EMAILS` 에 관리자 이메일을 쉼표로 구분해서 넣으면 `/api/admin/*` 엔드포인트 접근을 허용합니다.
- `TRENDING_SYNC_MAX_PAGES_PER_SOURCE` 는 급상승 동기화 시 소스 카테고리별로 몇 페이지까지 수집할지 결정합니다.

## API 요약

- `GET /api/catalog/regions/{regionCode}/categories`
- `GET /api/catalog/regions/{regionCode}/categories/{categoryId}/videos?pageToken=...`
- `POST /api/auth/google`
- `GET /api/auth/me`
- `DELETE /api/auth/session`
- `GET /api/me/playback-progress`
- `POST /api/me/playback-progress`
- `GET /api/me/favorite-streamers`
- `POST /api/me/favorite-streamers`
- `DELETE /api/me/favorite-streamers/{channelId}`
- `GET /api/comments`
- `POST /api/comments`
- `GET /api/trending/signals?regionCode=KR&categoryId=0&videoIds=abc&videoIds=def`
- `GET /api/trending/top-videos?regionCode=KR&pageToken=50`
- `GET /api/trending/realtime-surging?regionCode=KR`
- `POST /api/trending/sync`
- `GET /api/admin/dashboard`
- `PATCH /api/admin/seasons/{seasonId}`
- `POST /api/admin/seasons/{seasonId}/close`
- `GET /api/admin/users?q=atlas&limit=20`
- `GET /api/admin/users/{userId}`
- `PATCH /api/admin/users/{userId}/wallet`
- `DELETE /api/admin/users/{userId}`
- `GET /api/game/seasons/current`
- `GET /api/game/wallet`
- `GET /api/game/market`
- `GET /api/game/leaderboard`
- `GET /api/game/coins/overview`
- `GET /api/game/notifications`
- `GET /api/game/positions/me?status=OPEN`
- `POST /api/game/positions`
- `POST /api/game/positions/{positionId}/sell`

## 게임 기능 변경점

이번 변경으로 아래 기능이 추가되었습니다.

- 시즌 기반 포인트 게임 도메인 추가
- 유저별 게임 지갑 자동 생성
- 실시간 랭킹 기반 영상 매수/매도
- 랭킹별 가격 곡선 기반 손익 정산
- 거래 가능 마켓 목록 조회
- 실시간 평가손익 반영 리더보드
- Top 200 보유 포지션 대상 시즌 코인 생산
- 시즌 코인 개요/예상 생산량 조회
- 시즌 종료 시 오픈 포지션 자동 청산 스케줄러

시즌 코인 규칙:

- 코인은 돈이 아니라 시즌 명예 재화입니다.
- 실시간 차트 1위부터 200위까지의 보유 포지션이 코인을 생산합니다.
- 코인 생산률은 순위에 따라 감쇠하며, 1위는 `1.0%`, 10위는 `0.46%`, 50위는 `0.3%`, 100위는 `0.15%`, 200위는 `0%`입니다.
- 순위가 높을수록 코인 생산률이 높습니다.
- 최소 보유 시간이 지난 포지션만 코인 생산에 반영됩니다.
- 차트아웃되면 코인 생산이 중단됩니다.
- 시즌 종료 시 코인 자체는 소멸하고, 최종 성과는 티어/뱃지 같은 시즌 결과로 남기는 방향을 전제로 합니다.

핵심 정산 규칙:

```text
anchorPrices = {200: 3000, 190: 4000, ..., 20: 750000, 10: 1050000, 2: 1333333, 1: 2000000}
basePricePoints = anchorPrices 를 기준으로 rank 구간별 기하보간
momentumRankChange = clamp(rankChange, -30, 30)
momentumMultiplier = rankChange > 0 이면 exp(0.002 * momentumRankChange), rankChange < 0 이면 exp(0.003 * momentumRankChange)
currentPricePoints = round(basePricePoints * momentumMultiplier)
buyPricePoints = buy 시점 currentPricePoints
sellPricePoints = sell 시점 currentPricePoints
sellFeePoints = floor(sellPricePoints * 0.003)
settledPoints = max(0, sellPricePoints - sellFeePoints)
profitPoints = settledPoints - buyPricePoints
```

- `rankChange > 0` 인 실시간 급상승 영상은 프리미엄 가격이 적용됩니다.
- `rankChange < 0` 인 실시간 급하락 영상은 세일 가격이 적용됩니다.
- 프리미엄/세일은 마켓 가격, 매수 가격 검증, 오픈 포지션 평가, 매도 정산에 동일하게 반영됩니다.
- 랭킹 스냅샷만 있고 `rankChange` 를 알 수 없는 fallback 정산은 기존 순위 기반 가격을 사용합니다.

예시:

- `170위` 매수 -> `160위` 매도
- 순위가 `10`단계 상승했으므로 가격은 `7500 -> 10000`으로 보정
- `buyPricePoints = 7500`
- `sellPricePoints = 10000`
- `sellFeePoints = 30`
- `settledPoints = 9970`
- `profitPoints = 2470`

반대 방향도 동일하게 적용됩니다.

- `160위` 매수 -> `170위` 매도
- 순위가 `10`단계 하락했으므로 가격은 `10000 -> 7500`으로 보정
- `buyPricePoints = 10000`
- `sellPricePoints = 7500`
- `sellFeePoints = 22`
- `settledPoints = 7478`
- `profitPoints = -2522`

실시간 모멘텀 예시:

- `171위`, `rankChange = 20`이면 기본 순위 가격에 약 `4.1%` 프리미엄 적용
- `171위`, `rankChange = -20`이면 기본 순위 가격에 약 `5.8%` 세일 적용

## 프론트 연동 순서

게임 화면 MVP는 아래 순서로 붙이면 됩니다.

1. 로그인 후 `GET /api/game/seasons/current` 호출
2. `GET /api/game/market` 으로 거래 가능 영상 목록 조회
3. `GET /api/game/coins/overview` 로 내 시즌 코인 현황 조회
4. `GET /api/game/positions/me?status=OPEN` 으로 내 보유 포지션 조회
5. `GET /api/game/leaderboard` 로 랭킹 조회
6. 매수 시 `POST /api/game/positions`
7. 매도 시 `POST /api/game/positions/{positionId}/sell`

모든 게임 API는 아래 헤더가 필요합니다.

```text
Authorization: Bearer {accessToken}
```

## 게임 시즌 준비

게임 API를 실제로 사용하려면 `ACTIVE` 시즌이 최소 1개 필요합니다.

예시 SQL:

```sql
insert into game_seasons
(name, status, region_code, start_at, end_at, starting_balance_points, min_hold_seconds, max_open_positions, rank_point_multiplier, created_at)
values
('KR Daily Season', 'ACTIVE', 'KR', now(), now() + interval '7 day', 10000, 600, 5, 100, now());
```

권장 기본값:

- `starting_balance_points = 10000`
- `min_hold_seconds = 600`
- `max_open_positions = 5`
- `rank_point_multiplier = 100`

## 게임 API 명세

### `GET /api/game/seasons/current`

현재 활성 시즌과 내 지갑 정보를 반환합니다. 지갑이 아직 없으면 첫 호출 시 자동 생성됩니다.

응답 예시:

```json
{
  "seasonId": 1,
  "seasonName": "KR Daily Season",
  "status": "ACTIVE",
  "regionCode": "KR",
  "startAt": "2026-04-01T00:00:00Z",
  "endAt": "2026-04-08T00:00:00Z",
  "startingBalancePoints": 10000,
  "minHoldSeconds": 600,
  "maxOpenPositions": 5,
  "rankPointMultiplier": 100,
  "wallet": {
    "seasonId": 1,
    "balancePoints": 10000,
    "reservedPoints": 0,
    "realizedPnlPoints": 0,
    "coinBalance": 0,
    "totalAssetPoints": 10000
  },
  "notifications": []
}
```

### `GET /api/game/wallet`

현재 시즌 기준 내 게임 지갑 정보를 반환합니다.

### `GET /api/game/coins/overview`

현재 시즌 기준 내 시즌 코인 현황을 반환합니다.

- `myCoinBalance`: 현재까지 누적된 시즌 코인
- `myEstimatedCoinYield`: 지금 상태가 유지될 때 예상되는 코인 생산량
- `myActiveProducerCount`: 현재 생산 중인 포지션 수
- `myWarmingUpPositionCount`: Top 200 안에 있지만 최소 보유 시간을 아직 채우지 못한 포지션 수
- 최소 보유 시간 이후부터 보유 시간이 길수록 채굴 부스트가 붙습니다.
- 기본 부스트 규칙은 `10분마다 +10%` 이며, 최대 `2배` 까지 증가합니다.
- 각 포지션에는 기본 수익률 `coinRatePercent`, 보유 시간 부스트 `holdBoostPercent`, 최종 적용 수익률 `effectiveCoinRatePercent` 가 함께 내려갑니다.

후속 티어/뱃지 API 설계와 배포 체크리스트는 [docs/season-coin-roadmap.md](docs/season-coin-roadmap.md) 에 정리되어 있습니다.

### `GET /api/game/market`

현재 거래 가능한 영상 목록을 반환합니다. 각 항목에는 현재 매수 가능 여부와 차단 사유가 포함됩니다.

응답 예시:

```json
[
  {
    "videoId": "abc123",
    "title": "Sample title",
    "channelTitle": "Sample channel",
      "thumbnailUrl": "https://example.com/thumb.jpg",
      "currentRank": 3,
      "previousRank": 5,
      "rankChange": 2,
      "basePricePoints": 1320000,
      "currentPricePoints": 1320000,
      "momentumPriceDeltaPoints": 0,
      "momentumPriceDeltaPercent": 0.0,
      "momentumPriceType": "NONE",
      "currentViewCount": 123456,
      "viewCountDelta": 3456,
      "isNew": false,
    "canBuy": true,
    "buyBlockedReason": null,
    "capturedAt": "2026-04-04T00:00:00Z"
  }
]
```

- `basePricePoints` 는 현재 순위만 반영한 기본 가격입니다.
- `currentPricePoints` 는 급상승 프리미엄/급하락 세일까지 반영한 실제 거래 가격입니다.
- `momentumPriceType` 은 `PREMIUM`, `DISCOUNT`, `NONE` 중 하나입니다.
- 프론트 배지는 `momentumPriceType != NONE` 일 때 `momentumPriceDeltaPercent` 로 표시하면 됩니다.

`buyBlockedReason` 예시:

- `이미 보유 중인 영상입니다.`
- `동시 보유 가능 포지션 수를 초과했습니다.`
- `현재 가격 기준 보유 포인트가 부족합니다.`

### `GET /api/game/leaderboard`

현재 시즌 리더보드를 반환합니다.

- 정렬 기준은 `coinBalance desc` 입니다.
- 동률이면 `totalAssetPoints desc`, 이후 `realizedPnlPoints desc` 순서로 비교합니다.
- `coinBalance` 는 현재 시즌 누적 코인 보유량입니다.
- `totalStakePoints` 는 현재 오픈 포지션의 총 매수금액입니다.
- `totalEvaluationPoints` 는 현재 오픈 포지션의 총 평가금액입니다.
- `profitRatePercent` 는 현재 오픈 포지션 기준 실시간 수익률이며, 계산식은 `(totalEvaluationPoints - totalStakePoints) / totalStakePoints * 100` 입니다.
- `totalAssetPoints = balancePoints + 오픈 포지션 평가금액`
- 오픈 포지션은 현재 랭킹 기준 가격으로 평가손익을 반영합니다.

응답 예시:

```json
[
  {
    "rank": 1,
    "userId": 7,
    "displayName": "Atlas User",
    "pictureUrl": "https://lh3.googleusercontent.com/...",
    "coinBalance": 900000,
    "totalAssetPoints": 12400,
    "balancePoints": 8000,
    "reservedPoints": 2000,
    "totalStakePoints": 10000,
    "totalEvaluationPoints": 12400,
    "profitRatePercent": 24.0,
    "realizedPnlPoints": 0,
    "unrealizedPnlPoints": 2400,
    "openPositionCount": 1,
    "me": true
  }
]
```

### `GET /api/game/notifications`

현재 로그인 사용자의 활성 시즌 게임 알림 중 삭제되지 않은 항목을 반환합니다.

- `MOONSHOT`, `BIG_CASHOUT`, `SMALL_CASHOUT`, `SNIPE` 조건을 만족하면 알림 항목으로 내려갑니다.
- 한 포지션에서 여러 조건이 동시에 성립하면 조건별로 각각 반환합니다.
- 알림은 서버에 저장되며 `readAt`, `deletedAt` 상태로 읽음/삭제를 관리합니다.
- 로그인 직후 `GET /api/game/seasons/current` 응답의 `notifications` 를 사용하거나 이 API를 따로 호출하면 됩니다.
- 로그인 상태에서 WebSocket을 연결하면 같은 알림이 `/user/queue/game/notifications` 로 실시간 전달됩니다.

응답 예시:

```json
[
  {
    "id": "42",
    "notificationType": "MOONSHOT",
    "title": "문샷 적중",
    "message": "150위에서 잡은 영상이 10위까지 올라왔습니다.",
    "positionId": 300,
    "videoId": "video-1",
    "videoTitle": "Title video-1",
    "channelTitle": "Channel",
    "thumbnailUrl": "https://example.com/video-1.jpg",
    "strategyTags": ["MOONSHOT", "BIG_CASHOUT", "SNIPE"],
    "highlightScore": 20000,
    "readAt": null,
    "createdAt": "2026-04-01T06:00:00Z"
  }
]
```

### `PATCH /api/game/notifications/read`

현재 로그인 사용자의 활성 시즌 알림을 모두 읽음 처리합니다.

Query:

- `regionCode`: 활성 시즌 지역 코드

### `DELETE /api/game/notifications`

현재 로그인 사용자의 활성 시즌 알림을 모두 삭제 처리합니다.

### `DELETE /api/game/notifications/{notificationId}`

현재 로그인 사용자의 단일 알림을 삭제 처리합니다.

### `GET /api/game/positions/me?status=OPEN`

내 포지션 목록을 반환합니다.

- `status` 는 선택값입니다.
- 사용 가능 값: `OPEN`, `CLOSED`, `AUTO_CLOSED`
- `status` 를 생략하면 현재 시즌의 전체 포지션을 반환합니다.

응답 예시:

```json
[
  {
    "id": 200,
    "videoId": "abc123",
    "title": "Sample title",
    "channelTitle": "Sample channel",
    "thumbnailUrl": "https://example.com/thumb.jpg",
    "buyRank": 170,
    "currentRank": 160,
    "rankDiff": 10,
    "stakePoints": 7500,
    "currentPricePoints": 10000,
    "profitPoints": 2500,
    "status": "OPEN",
    "buyCapturedAt": "2026-04-04T00:00:00Z",
    "createdAt": "2026-04-04T00:01:00Z",
    "closedAt": null
  }
]
```

### `POST /api/game/positions`

영상 매수 API입니다.

요청 본문:

```json
{
  "regionCode": "KR",
  "categoryId": "0",
  "videoId": "abc123",
  "stakePoints": 7500,
  "quantity": 100
}
```

제약:

- `stakePoints` 는 현재 마켓의 `currentPricePoints` 와 같아야 함
- `quantity` 는 고정 소수점 수량입니다. `100 = 1.00개`
- 주문은 `1개` 단위로만 가능하므로 `quantity` 는 `100`, `200`, `300` 처럼 `100`의 배수여야 함
- 같은 시즌에 동일 영상 중복 보유 불가
- 시즌의 `maxOpenPositions` 초과 불가
- 시즌 `regionCode` 와 다른 값으로는 매수 불가

응답은 `PositionResponse` 형식입니다.

### `POST /api/game/positions/{positionId}/sell`

영상 매도 API입니다.

- 최소 보유 시간(`minHoldSeconds`) 이전에는 매도할 수 없습니다.
- 매도 시 최신 랭킹 기준으로 손익이 확정됩니다.

응답 예시:

```json
{
  "positionId": 200,
  "videoId": "abc123",
  "buyRank": 170,
  "sellRank": 160,
  "rankDiff": 10,
  "quantity": 100,
  "stakePoints": 7500,
  "sellPricePoints": 10000,
  "pnlPoints": 2470,
  "settledPoints": 9970,
  "highlightScore": 42000,
  "balancePoints": 12470,
  "soldAt": "2026-04-04T00:20:00Z"
}
```

## 시즌 종료 자동 청산

`GAME_SCHEDULER_ENABLED=true` 일 때 종료 시간이 지난 `ACTIVE` 시즌을 주기적으로 정리합니다.

동작 방식:

- `endAt <= now` 인 `ACTIVE` 시즌 조회
- 해당 시즌의 `OPEN` 포지션을 모두 `AUTO_CLOSED` 처리
- 현재 랭킹이 남아 있으면 해당 랭킹으로 정산
- 랭킹에서 이미 사라진 영상이면 마지막 랭킹 뒤 번호(`maxRank + 1`)로 패널티 정산
- 시즌 상태를 `ENDED` 로 변경

프론트에서 유의할 점:

- `AUTO_CLOSED` 도 종료 포지션 상태로 표시해야 합니다.
- 시즌 종료 직후에는 `GET /api/game/seasons/current` 가 실패할 수 있으니, 다음 시즌이 아직 없으면 “다음 시즌 준비 중” 상태를 표시하는 게 좋습니다.

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
    "createdAt": "2026-04-01T06:00:00Z",
    "lastLoginAt": "2026-04-01T06:00:00Z",
    "favoriteCount": 4,
    "commentCount": 18,
    "tradeCount": 42,
    "lastPlaybackProgress": {
      "videoId": "abc123",
      "videoTitle": "Sample title",
      "channelTitle": "Sample channel",
      "thumbnailUrl": "https://example.com/thumb.jpg",
      "positionSeconds": 184,
      "updatedAt": "2026-04-01T05:50:00Z"
    },
    "recentPlaybackProgresses": [
      {
        "videoId": "abc123",
        "videoTitle": "Sample title",
        "channelTitle": "Sample channel",
        "thumbnailUrl": "https://example.com/thumb.jpg",
        "positionSeconds": 184,
        "updatedAt": "2026-04-01T05:50:00Z"
      }
    ]
  }
}
```

### `GET /api/auth/me`

헤더:

```text
Authorization: Bearer {accessToken}
```

현재 로그인한 사용자 정보를 반환합니다.
- `favoriteCount`, `commentCount`, `tradeCount` 로 사용자의 주요 활동 누적 수치를 함께 내려옵니다.
- 최근 재생 위치는 최신 1개를 `user.lastPlaybackProgress`, 최신 5개 목록을 `user.recentPlaybackProgresses` 로 함께 내려옵니다.

### `DELETE /api/auth/session`

헤더:

```text
Authorization: Bearer {accessToken}
```

현재 세션을 로그아웃합니다.

## 관리자 API

모든 관리자 API는 아래 헤더가 필요합니다.

```text
Authorization: Bearer {accessToken}
```

- 로그인 사용자 이메일이 `ADMIN_ALLOWED_EMAILS` 에 포함되어 있어야 합니다.

### `GET /api/admin/dashboard`

기존 관리자 대시보드 데이터를 반환합니다.

- `activeSeason`: 기존 호환용 대표 활성 시즌 1건
- `activeSeasons`: 현재 활성 시즌 목록 전체

예시:

```json
{
  "metrics": {
    "totalUsers": 12,
    "totalComments": 34,
    "totalFavorites": 5,
    "totalTrendRuns": 7,
    "totalTradeHistories": 21
  },
  "activeSeason": {
    "id": 4,
    "name": "KR Daily Season",
    "status": "ACTIVE",
    "regionCode": "KR",
    "startAt": "2026-04-01T00:00:00Z",
    "endAt": "2026-04-10T00:00:00Z",
    "createdAt": "2026-03-31T00:00:00Z"
  },
  "activeSeasons": [
    {
      "id": 4,
      "name": "KR Daily Season",
      "status": "ACTIVE",
      "regionCode": "KR",
      "startAt": "2026-04-01T00:00:00Z",
      "endAt": "2026-04-10T00:00:00Z",
      "createdAt": "2026-03-31T00:00:00Z"
    },
    {
      "id": 8,
      "name": "US Daily Season",
      "status": "ACTIVE",
      "regionCode": "US",
      "startAt": "2026-04-02T00:00:00Z",
      "endAt": "2026-04-11T00:00:00Z",
      "createdAt": "2026-04-01T00:00:00Z"
    }
  ]
}
```

### `POST /api/admin/comments/purge`

관리자가 기준 시각보다 오래된 채팅 로그를 일괄 삭제합니다.

요청 본문:

```json
{
  "deleteBefore": "2026-03-01T00:00:00Z"
}
```

- `deleteBefore` 는 필수입니다.
- 미래 시각은 허용되지 않습니다.
- 해당 시각보다 `이전` 에 생성된 댓글만 삭제됩니다.

응답 예시:

```json
{
  "deleteBefore": "2026-03-01T00:00:00Z",
  "deletedAt": "2026-04-15T03:00:00Z",
  "deletedCount": 128
}
```

### `POST /api/admin/trade-history/purge`

관리자가 기준 시각보다 오래된 차트 거래내역을 일괄 삭제합니다.

요청 본문:

```json
{
  "deleteBefore": "2026-03-01T00:00:00Z"
}
```

- `deleteBefore` 는 필수입니다.
- 미래 시각은 허용되지 않습니다.
- `deleteBefore` 보다 이전에 종료된 `CLOSED`, `AUTO_CLOSED` 거래내역만 삭제됩니다.
- 해당 거래내역에 연결된 원장, 코인 지급, 배당 지급 데이터도 함께 삭제됩니다.
- `OPEN` 포지션은 삭제 대상이 아닙니다.

응답 예시:

```json
{
  "deleteBefore": "2026-03-01T00:00:00Z",
  "deletedAt": "2026-04-15T03:00:00Z",
  "deletedPositionCount": 42,
  "deletedLedgerCount": 84,
  "deletedCoinPayoutCount": 128,
  "deletedDividendPayoutCount": 56
}
```

### `PATCH /api/admin/seasons/{seasonId}`

관리자가 활성 시즌의 시작/종료 시각을 수정합니다.

요청 본문:

```json
{
  "startAt": "2026-04-09T00:00:00Z",
  "endAt": "2026-04-12T00:00:00Z"
}
```

- `endAt` 은 `startAt` 이후여야 합니다.
- `ACTIVE` 시즌은 `startAt` 을 미래 시각으로 바꿀 수 없습니다.
- `ACTIVE` 시즌의 `endAt` 을 현재 시각 이전으로 바꾸려면 수정 대신 수동 종료를 사용해야 합니다.

### `POST /api/admin/seasons/{seasonId}/close`

관리자가 활성 시즌을 즉시 종료합니다.

- 오픈 포지션 자동 정산과 시즌 코인 결과 확정이 바로 수행됩니다.
- 종료 후 관리 대상 지역에 활성 시즌이 없으면 후속 활성 시즌이 자동 생성됩니다.

### `GET /api/admin/users`

관리자용 유저 목록을 반환합니다.

쿼리 파라미터:

- `q` 선택값: 이메일 또는 닉네임 부분 검색
- `limit` 선택값: 기본 `20`, 최대 `100`

응답 예시:

```json
{
  "query": "atlas",
  "limit": 20,
  "count": 1,
  "users": [
    {
      "id": 1,
      "email": "atlas@example.com",
      "displayName": "Atlas User",
      "pictureUrl": "https://lh3.googleusercontent.com/...",
      "admin": true,
      "createdAt": "2026-04-01T06:00:00Z",
      "lastLoginAt": "2026-04-08T02:00:00Z"
    }
  ]
}
```

### `GET /api/admin/users/{userId}`

관리자용 유저 상세 정보를 반환합니다.

응답에는 아래 정보가 포함됩니다.

- 기본 프로필
- 관리자 여부
- 즐겨찾기 스트리머 개수
- 마지막 재생 위치
- 현재 활성 시즌별 게임 참여 여부와 지갑/포지션 요약

응답 예시:

```json
{
  "id": 1,
  "email": "atlas@example.com",
  "displayName": "Atlas User",
  "pictureUrl": "https://lh3.googleusercontent.com/...",
  "admin": true,
  "createdAt": "2026-04-01T06:00:00Z",
  "lastLoginAt": "2026-04-08T02:00:00Z",
  "favoriteCount": 4,
  "lastPlaybackProgress": {
    "videoId": "abc123",
    "videoTitle": "Sample title",
    "channelTitle": "Sample channel",
    "thumbnailUrl": "https://example.com/thumb.jpg",
    "positionSeconds": 184,
    "updatedAt": "2026-04-08T01:50:00Z"
  },
  "activeSeasonGame": {
    "seasonId": 3,
    "seasonName": "Season 3",
    "regionCode": "KR",
    "participating": true,
    "balancePoints": 12000,
    "reservedPoints": 3000,
    "realizedPnlPoints": 1500,
    "tierScore": 130000,
    "coinBalance": 900000,
    "totalAssetPoints": 15000,
    "openPositionCount": 2,
    "closedPositionCount": 5
  },
  "activeSeasonGames": [
    {
      "seasonId": 3,
      "seasonName": "Season 3",
      "regionCode": "KR",
      "participating": true,
      "balancePoints": 12000,
      "reservedPoints": 3000,
      "realizedPnlPoints": 1500,
      "tierScore": 130000,
      "coinBalance": 900000,
      "totalAssetPoints": 15000,
      "openPositionCount": 2,
      "closedPositionCount": 5
    },
    {
      "seasonId": 8,
      "seasonName": "US Season 8",
      "regionCode": "US",
      "participating": false,
      "balancePoints": 10000,
      "reservedPoints": 0,
      "realizedPnlPoints": 0,
      "tierScore": 0,
      "coinBalance": 0,
      "totalAssetPoints": 10000,
      "openPositionCount": 0,
      "closedPositionCount": 0
    }
  ]
}
```

### `GET /api/admin/users/{userId}/positions`

관리자가 특정 시즌에서 유저가 현재 보유 중인 `OPEN` 포지션 목록을 조회합니다.

쿼리 파라미터:

- `seasonId` 필수값: 조회할 시즌 ID

### `PATCH /api/admin/users/{userId}/positions/{positionId}`

관리자가 유저의 보유 중인 포지션 수치를 직접 수정합니다.

요청 본문:

```json
{
  "quantity": 300,
  "stakePoints": 1500
}
```

- 현재 `OPEN` 상태 포지션만 수정할 수 있습니다.
- `quantity` 는 100 단위로만 수정할 수 있습니다.
- `stakePoints` 수정 시 지갑의 `balancePoints` 와 `reservedPoints` 도 함께 보정됩니다.

### `PATCH /api/admin/users/{userId}/wallet`

관리자가 선택한 활성 시즌 기준으로 해당 유저의 지갑 수치를 직접 수정합니다.

요청 본문:

```json
{
  "seasonId": 3,
  "balancePoints": 12000,
  "reservedPoints": 3000,
  "realizedPnlPoints": 1500,
  "tierScore": 130000
}
```

- `seasonId` 가 주어지면 해당 시즌이 현재 `ACTIVE` 상태인지 검증합니다.
- `seasonId` 를 생략하면 가장 최근 활성 시즌 하나를 대상으로 동작합니다.
- 아직 활성 시즌 지갑이 없는 유저여도, 수정 시 관리자 값으로 새 지갑을 생성합니다.
- `tierScore` 는 티어 판정에 우선 사용되는 관리자 지정 점수입니다.
- `reservedPoints` 는 오픈 포지션과 연결될 수 있으므로 운영 목적에서만 수동 조정해야 합니다.

### `DELETE /api/admin/users/{userId}`

관리자가 특정 유저를 탈퇴 처리합니다.

- 인증 세션
- 최근 재생 위치
- 즐겨찾기
- 게임 지갑/포지션/원장

위 데이터가 함께 삭제된 뒤 유저 계정이 제거됩니다.
- 현재 댓글은 유저 FK가 아니라 작성자 문자열 기반이라, 기존 댓글 데이터는 유지됩니다.

## 최근 재생 위치 API

모든 최근 재생 위치 API는 아래 헤더가 필요합니다.

```text
Authorization: Bearer {accessToken}
```

### `GET /api/me/playback-progress`

현재 로그인한 사용자의 마지막 재생 위치를 반환합니다.

- 저장된 재생 위치가 있으면 `200 OK`
- 아직 없으면 `204 No Content`

### `POST /api/me/playback-progress`

요청 본문:

```json
{
  "videoId": "abc123",
  "videoTitle": "Sample title",
  "channelTitle": "Sample channel",
  "thumbnailUrl": "https://example.com/thumb.jpg",
  "positionSeconds": 184
}
```

- 같은 사용자는 마지막 재생 위치 1건만 유지합니다.
- 프론트에서 재생 중 주기적으로 호출하면 마지막 시청 위치를 계속 갱신할 수 있습니다.
- 로그인 직후 `POST /api/auth/google` 또는 `GET /api/auth/me` 의 `user.lastPlaybackProgress` 를 읽어서 해당 영상/시점으로 바로 이동할 수 있습니다.

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

### `GET /api/comments`

전역 채팅방의 댓글을 생성 시각 오름차순으로 반환합니다.
`since` 쿼리 파라미터에 ISO-8601 시각을 전달하면 해당 시각 이후에 생성된 댓글만 반환합니다.

### `POST /api/comments`

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
- 로그인 사용자가 보내면 응답과 실시간 메시지에 `user_id` 가 포함되어 다른 기기에서도 내 메시지로 구분할 수 있습니다.
- 로그인 사용자는 계정 기준, 비로그인 사용자는 같은 `clientId` 기준으로 5초 쿨다운이 있습니다.
- 로그인 사용자는 계정 기준, 비로그인 사용자는 같은 `clientId` 기준으로 같은 메시지를 30초 안에 다시 보내면 중복으로 막습니다.
- 댓글 응답 JSON은 `snake_case` 입니다.
- 기존 `/api/videos/{videoId}/comments` 경로도 호환용으로 유지되지만, 같은 전역 채팅방을 조회/작성합니다.

실시간 브로드캐스트:

- WebSocket endpoint: `/ws`
- subscribe topic: `/topic/comments`
- game update topic: `/topic/game/{regionCode}`
- personal game notifications: `/user/queue/game/notifications`
- 개인 게임 알림을 받으려면 STOMP `CONNECT` native header에 `Authorization: Bearer {accessToken}` 을 포함합니다.

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

### `GET /api/trending/top-videos`

쿼리 파라미터:

- `regionCode`
- `pageToken` 선택, 50개 단위 오프셋

예시:

```text
/api/trending/top-videos?regionCode=KR&pageToken=50
```

- 최신 트렌드 스냅샷 기준 TOP 200 영상을 50개씩 반환합니다.
- 게임용 메인 차트에서 사용하기 위한 읽기 전용 API입니다.
- 응답은 `currentRank` 오름차순이며, 각 아이템의 `trend` 에 현재/이전 랭크와 조회수 변동이 함께 포함됩니다.

응답 예시:

```json
{
  "categoryId": "0",
  "label": "전체",
  "description": "카테고리 구분 없이 현재 국가 전체 인기 영상을 보여줍니다.",
  "items": [
    {
      "id": "abc",
      "contentDetails": {
        "duration": ""
      },
      "snippet": {
        "title": "Example",
        "channelTitle": "Atlas",
        "channelId": "channel-1",
        "categoryId": "0",
        "publishedAt": "2026-04-01T04:50:00Z",
        "thumbnails": {
          "default": {
            "url": "https://example.com/thumb.jpg",
            "width": null,
            "height": null
          },
          "medium": {
            "url": "https://example.com/thumb.jpg",
            "width": null,
            "height": null
          },
          "high": {
            "url": "https://example.com/thumb.jpg",
            "width": null,
            "height": null
          },
          "standard": {
            "url": "https://example.com/thumb.jpg",
            "width": null,
            "height": null
          },
          "maxres": {
            "url": "https://example.com/thumb.jpg",
            "width": null,
            "height": null
          }
        }
      },
      "statistics": {
        "viewCount": 1900000
      },
      "trend": {
        "categoryLabel": "전체",
        "currentRank": 3,
        "previousRank": 11,
        "rankChange": 8,
        "currentViewCount": 1900000,
        "previousViewCount": 1700000,
        "viewCountDelta": 200000,
        "isNew": false,
        "capturedAt": "2026-04-01T05:30:00Z"
      }
    }
  ],
  "nextPageToken": "100"
}
```

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
