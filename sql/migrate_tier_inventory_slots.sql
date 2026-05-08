alter table if exists game_season_tiers
    add column if not exists inventory_slots integer not null default 5;

update game_season_tiers
set inventory_slots = case tier_code
    when 'BRONZE' then 5
    when 'SILVER' then 7
    when 'GOLD' then 10
    when 'PLATINUM' then 12
    when 'DIAMOND' then 15
    when 'MASTER' then 20
    when 'LEGEND' then 20
    else inventory_slots
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
