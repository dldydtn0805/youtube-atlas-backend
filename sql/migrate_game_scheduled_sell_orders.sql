create table if not exists game_scheduled_sell_orders (
    id bigserial primary key,
    season_id bigint not null references game_seasons(id),
    user_id bigint not null references app_users(id),
    position_id bigint not null references game_positions(id),
    region_code varchar(10) not null,
    target_rank integer not null,
    quantity integer not null,
    status varchar(20) not null,
    triggered_at timestamp with time zone,
    executed_at timestamp with time zone,
    canceled_at timestamp with time zone,
    failed_at timestamp with time zone,
    failed_reason varchar(500),
    sell_price_points bigint,
    settled_points bigint,
    pnl_points bigint,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_game_scheduled_sell_orders_region_status
    on game_scheduled_sell_orders (region_code, status, created_at, id);

create index if not exists idx_game_scheduled_sell_orders_season_user
    on game_scheduled_sell_orders (season_id, user_id, created_at desc);

create index if not exists idx_game_scheduled_sell_orders_position_status
    on game_scheduled_sell_orders (position_id, status);

alter table game_scheduled_sell_orders
    add column if not exists sell_price_points bigint,
    add column if not exists settled_points bigint,
    add column if not exists pnl_points bigint;
