update game_season_coin_tiers
set min_coin_balance = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 100000
    when 'GOLD' then 300000
    when 'PLATINUM' then 1200000
    when 'DIAMOND' then 6000000
    when 'MASTER' then 36000000
    when 'LEGEND' then 252000000
    else min_coin_balance
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
