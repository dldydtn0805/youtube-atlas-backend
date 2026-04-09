create table if not exists game_season_coin_tiers (
    id bigserial primary key,
    season_id bigint not null,
    tier_code varchar(50) not null,
    display_name varchar(100) not null,
    min_coin_balance bigint not null,
    badge_code varchar(100) not null,
    title_code varchar(100) not null,
    profile_theme_code varchar(100) not null,
    sort_order integer not null,
    created_at timestamptz not null,
    constraint uk_game_season_coin_tiers_season_code unique (season_id, tier_code),
    constraint uk_game_season_coin_tiers_season_order unique (season_id, sort_order),
    constraint fk_game_season_coin_tiers_season foreign key (season_id) references game_seasons (id)
);

create index if not exists idx_game_season_coin_tiers_season
    on game_season_coin_tiers (season_id, sort_order);

create table if not exists game_season_coin_results (
    id bigserial primary key,
    season_id bigint not null,
    user_id bigint not null,
    final_coin_balance bigint not null,
    final_tier_code varchar(50) not null,
    final_tier_display_name varchar(100) not null,
    final_tier_min_coin_balance bigint not null,
    badge_code varchar(100) not null,
    title_code varchar(100) not null,
    profile_theme_code varchar(100) not null,
    created_at timestamptz not null,
    constraint uk_game_season_coin_results_season_user unique (season_id, user_id),
    constraint fk_game_season_coin_results_season foreign key (season_id) references game_seasons (id),
    constraint fk_game_season_coin_results_user foreign key (user_id) references app_users (id)
);

create index if not exists idx_game_season_coin_results_season
    on game_season_coin_results (season_id, final_coin_balance desc);
