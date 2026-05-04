create table if not exists game_season_results (
    id bigserial primary key,
    season_id bigint not null references game_seasons(id),
    user_id bigint not null references app_users(id),
    region_code varchar(10) not null,
    season_name varchar(100) not null,
    season_start_at timestamp with time zone not null,
    season_end_at timestamp with time zone not null,
    final_rank integer not null,
    final_asset_points bigint not null,
    final_balance_points bigint not null,
    realized_pnl_points bigint not null,
    position_count bigint not null,
    best_position_id bigint,
    best_position_video_id varchar(50),
    best_position_title varchar(255),
    best_position_channel_title varchar(255),
    best_position_thumbnail_url varchar(2000),
    best_position_profit_points bigint,
    title_code varchar(80),
    created_at timestamp with time zone not null,
    constraint uk_game_season_results_season_user unique (season_id, user_id)
);

create index if not exists idx_game_season_results_user_region_end
    on game_season_results (user_id, region_code, season_end_at desc, created_at desc);

create index if not exists idx_game_season_results_season_rank
    on game_season_results (season_id, final_rank);
