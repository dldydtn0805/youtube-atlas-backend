alter table if exists game_coin_payouts
    add column if not exists payout_slot_at timestamptz;

update game_coin_payouts
set payout_slot_at = coalesce(payout_slot_at, created_at)
where payout_slot_at is null;

alter table if exists game_coin_payouts
    alter column payout_slot_at set not null;

drop index if exists idx_game_coin_payouts_season_run;
create index if not exists idx_game_coin_payouts_season_run
    on game_coin_payouts (season_id, payout_slot_at);

alter table if exists game_coin_payouts
    drop constraint if exists uk_game_coin_payouts_season_position_run;

alter table if exists game_coin_payouts
    add constraint uk_game_coin_payouts_season_position_run unique (season_id, position_id, payout_slot_at);
