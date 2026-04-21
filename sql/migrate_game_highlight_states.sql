create table if not exists game_highlight_states (
    id bigserial primary key,
    season_id bigint not null,
    user_id bigint not null,
    root_position_id bigint not null,
    best_settled_position_id bigint,
    best_settled_highlight_type varchar(40),
    best_settled_title varchar(120),
    best_settled_description varchar(1000),
    video_id varchar(50),
    video_title varchar(255),
    channel_title varchar(255),
    thumbnail_url varchar(2000),
    buy_rank integer,
    highlight_rank integer,
    sell_rank integer,
    rank_diff integer,
    quantity integer,
    stake_points bigint,
    current_price_points bigint,
    profit_points bigint,
    profit_rate_percent double precision,
    strategy_tags varchar(200),
    best_settled_highlight_score bigint not null default 0,
    best_settled_created_at timestamp with time zone,
    best_projected_notification_score bigint not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_game_highlight_states_root unique (season_id, user_id, root_position_id),
    constraint fk_game_highlight_states_season foreign key (season_id) references game_seasons (id),
    constraint fk_game_highlight_states_user foreign key (user_id) references app_users (id)
);

create index if not exists idx_game_highlight_states_season_user
    on game_highlight_states (season_id, user_id);

create index if not exists idx_game_highlight_states_season_score
    on game_highlight_states (season_id, best_settled_highlight_score);
