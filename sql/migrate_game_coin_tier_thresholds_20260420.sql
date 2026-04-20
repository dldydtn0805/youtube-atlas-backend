update game_season_coin_tiers
set min_coin_balance = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 10000
    when 'GOLD' then 30000
    when 'PLATINUM' then 70000
    when 'DIAMOND' then 130000
    when 'MASTER' then 220000
    when 'LEGEND' then 350000
    else min_coin_balance
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
