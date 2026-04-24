alter table game_scheduled_sell_orders
    add column if not exists trigger_direction varchar(30) not null default 'RANK_IMPROVES_TO';
