# Season Coin Roadmap

## 목적

시즌 코인은 `Top 200` 종목을 얼마나 잘 보유하고 운용했는지를 보여주는 시즌 명예 재화다.

- 포인트와 분리된 시즌 성과 지표로 사용한다.
- 시즌 종료 후 코인 자체는 소멸한다.
- 최종 코인 성과는 티어, 뱃지, 칭호, 프로필 꾸미기 같은 시즌 결과물로 남긴다.

## 현재 구현 상태

현재 백엔드/프론트에는 아래 범위가 반영되어 있다.

- `GET /api/game/coins/overview` 제공
- `game_wallets.coin_balance` 로 시즌 코인 누적 저장
- `game_coin_payouts` 로 런 단위 코인 생산 이력 저장
- Top 200, 최소 보유 시간, 차트아웃 중단 규칙 반영
- 프론트 요약 카드/모달/선택 영상 카피를 시즌 코인 기준으로 변경

## 다음 단계 데이터 모델 초안

### 1. 시즌 티어 설정

`game_season_coin_tiers`

- `id`
- `season_id`
- `tier_code`
- `display_name`
- `min_coin_balance`
- `badge_code`
- `title_code`
- `profile_theme_code`
- `sort_order`
- `created_at`

용도:

- 시즌별 티어 컷라인 정의
- 같은 시즌 안에서도 티어 정책을 독립적으로 조정 가능

### 2. 시즌 결과 스냅샷

`game_season_coin_results`

- `id`
- `season_id`
- `user_id`
- `final_coin_balance`
- `final_tier_code`
- `badge_code`
- `title_code`
- `profile_theme_code`
- `created_at`

용도:

- 시즌 종료 시 최종 결과를 영구 보존
- 다음 시즌 시작 후에도 지난 시즌 성과 조회 가능

### 3. 시즌 뱃지 메타

`game_season_badges`

- `id`
- `season_id`
- `badge_code`
- `display_name`
- `description`
- `icon_url`
- `created_at`

용도:

- 프론트가 뱃지 라벨과 아이콘을 안정적으로 렌더링

## API 초안

### 플레이어용

`GET /api/game/tiers/current`

- 현재 시즌 티어 테이블 조회
- 응답:
  - `seasonId`
  - `myCoinBalance`
  - `currentTier`
  - `nextTier`
  - `tiers[]`

`GET /api/game/rewards/current`

- 현재 시즌 기준 내가 해금한 명예 보상 조회
- 응답:
  - `seasonId`
  - `coinBalance`
  - `unlockedTier`
  - `badges[]`
  - `titles[]`
  - `profileThemes[]`

`GET /api/game/seasons/{seasonId}/results/me`

- 종료된 시즌의 내 최종 성과 조회
- 응답:
  - `seasonId`
  - `finalCoinBalance`
  - `finalTier`
  - `badges[]`
  - `titles[]`
  - `profileThemes[]`

`GET /api/game/seasons/{seasonId}/results/leaderboard`

- 해당 시즌 코인 기준 명예 리더보드 조회
- 응답:
  - `rank`
  - `userId`
  - `displayName`
  - `finalCoinBalance`
  - `finalTier`
  - `badgeCode`

### 관리자용

`GET /api/admin/game/seasons/{seasonId}/coin-tiers`

- 해당 시즌 티어 정책 조회

`PUT /api/admin/game/seasons/{seasonId}/coin-tiers`

- 해당 시즌 티어 정책 일괄 저장

`POST /api/admin/game/seasons/{seasonId}/finalize-coins`

- 시즌 종료 직후 최종 코인 성과를 결과 테이블에 확정
- 중복 실행 방지를 위해 idempotent 하게 구현

## 시즌 종료 처리 제안

1. 시즌 종료 시점에 오픈 포지션 자동 청산을 먼저 수행한다.
2. 각 유저의 `coin_balance` 를 읽어 최종 코인 값을 계산한다.
3. `game_season_coin_tiers` 기준으로 최종 티어를 결정한다.
4. `game_season_coin_results` 에 최종 결과를 저장한다.
5. 다음 시즌 지갑 생성 시 `coin_balance = 0` 으로 시작한다.

주의:

- 결과 확정 후에는 지난 시즌 코인을 다시 재계산하지 않는 쪽이 안전하다.
- 시즌 종료 배치가 여러 번 실행되어도 같은 결과만 남도록 유니크 키나 상태 플래그가 필요하다.

## 배포 체크리스트

### 백엔드

1. `sql/migrate_game_coin_system.sql` 이 배포 DB에 반영되었는지 확인한다.
2. `game_wallets.coin_balance` 기본값이 `0` 으로 채워졌는지 확인한다.
3. `game_coin_payouts` 테이블과 인덱스가 생성되었는지 확인한다.
4. `sql/migrate_game_coin_tier_thresholds.sql`, `sql/migrate_game_coin_add_master_legend_tiers.sql`, `sql/migrate_game_coin_tier_thresholds_20260415.sql`, `sql/migrate_game_coin_tier_thresholds_20260420.sql`, `sql/migrate_game_coin_tier_thresholds_20260421.sql` 이 배포 DB에 반영되어 시즌 티어 컷라인이 `BRONZE 0 / SILVER 10,000 / GOLD 30,000 / PLATINUM 120,000 / DIAMOND 600,000 / MASTER 3,600,000 / LEGEND 25,200,000` 으로 맞춰졌는지 확인한다.
5. 배포 후 `GET /api/game/coins/overview?regionCode=KR` 가 `200` 을 반환하는지 확인한다.
6. 스케줄러 사용 시 Top 200 보유 포지션에 대해 `coin_balance` 가 증가하는지 확인한다.
7. 매수/매도와 리더보드 계산이 기존 포인트 경제를 깨지 않았는지 확인한다.

### 프론트

1. 홈 화면에서 배당 문구가 시즌 코인 문구로 바뀌었는지 확인한다.
2. 코인 요약 카드에 `보유 코인`, `예상 생산`, `생산 중`, `준비 중` 값이 보이는지 확인한다.
3. 코인 모달에서 랭크별 생산률과 내 포지션 상태가 정상 노출되는지 확인한다.
4. Top 200 바깥 영상은 코인 생산 문구가 노출되지 않는지 확인한다.
5. 최소 보유 시간 전 포지션은 `준비 중` 상태로 보이는지 확인한다.

### 운영

1. 백엔드 먼저 배포하고 이후 프론트를 배포한다.
2. 배포 직후 로그인 사용자 기준으로 홈 화면 진입이 깨지지 않는지 확인한다.
3. 시즌 종료 직전과 직후에 코인 조회, 포지션 정산, 다음 시즌 생성이 서로 충돌하지 않는지 점검한다.
