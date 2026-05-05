alter table game_scheduled_sell_orders
    add column if not exists trigger_type varchar(30) not null default 'RANK',
    add column if not exists target_profit_rate_percent double precision;

alter table game_scheduled_sell_orders
    alter column target_profit_rate_percent type double precision
    using target_profit_rate_percent::double precision;

alter table game_scheduled_sell_orders
    alter column target_rank drop not null;

update game_scheduled_sell_orders
set trigger_type = 'RANK'
where trigger_type is null;
