alter table if exists game_positions
    alter column quantity type bigint;

alter table if exists game_scheduled_sell_orders
    alter column quantity type bigint;

alter table if exists game_highlight_states
    alter column quantity type bigint;
