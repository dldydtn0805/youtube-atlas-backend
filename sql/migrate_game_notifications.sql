create table if not exists game_notifications (
    id bigserial primary key,
    user_id bigint not null references app_users(id),
    season_id bigint not null references game_seasons(id),
    event_key varchar(120) not null,
    notification_type varchar(40) not null,
    title varchar(120) not null,
    message varchar(1000) not null,
    position_id bigint,
    video_id varchar(50),
    video_title varchar(255),
    channel_title varchar(255),
    thumbnail_url varchar(2000),
    strategy_tags varchar(200),
    highlight_score bigint,
    read_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create unique index if not exists uk_game_notifications_user_event
    on game_notifications(user_id, event_key);

create index if not exists idx_game_notifications_user_created
    on game_notifications(user_id, created_at);

create index if not exists idx_game_notifications_season_user
    on game_notifications(season_id, user_id);
