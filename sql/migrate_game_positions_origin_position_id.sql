alter table game_positions
    add column if not exists origin_position_id bigint;
