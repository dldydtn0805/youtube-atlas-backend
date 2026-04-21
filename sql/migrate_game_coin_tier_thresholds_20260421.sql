update game_season_coin_tiers
set min_coin_balance = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 10000
    when 'GOLD' then 30000
    when 'PLATINUM' then 120000
    when 'DIAMOND' then 600000
    when 'MASTER' then 3600000
    when 'LEGEND' then 25200000
    else min_coin_balance
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
