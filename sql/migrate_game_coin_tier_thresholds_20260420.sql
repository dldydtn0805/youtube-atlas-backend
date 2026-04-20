update game_season_coin_tiers
set min_coin_balance = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 8000
    when 'GOLD' then 20000
    when 'PLATINUM' then 35000
    when 'DIAMOND' then 55000
    when 'MASTER' then 85000
    when 'LEGEND' then 130000
    else min_coin_balance
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
