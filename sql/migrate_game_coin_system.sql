alter table if exists game_wallets
    add column if not exists coin_balance bigint not null default 0;

create table if not exists game_coin_payouts (
    id bigserial primary key,
    season_id bigint not null,
    user_id bigint not null,
    position_id bigint not null,
    trend_run_id bigint not null,
    payout_slot_at timestamptz not null,
    rank_at_payout integer not null,
    coin_rate_basis_points integer not null,
    amount_coins bigint not null,
    created_at timestamptz not null,
    constraint uk_game_coin_payouts_season_position_run unique (season_id, position_id, payout_slot_at),
    constraint fk_game_coin_payouts_season foreign key (season_id) references game_seasons (id),
    constraint fk_game_coin_payouts_user foreign key (user_id) references app_users (id),
    constraint fk_game_coin_payouts_position foreign key (position_id) references game_positions (id)
);

create index if not exists idx_game_coin_payouts_season_run
    on game_coin_payouts (season_id, payout_slot_at);
