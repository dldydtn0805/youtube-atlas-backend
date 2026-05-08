update game_season_tiers
set min_score = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 5000
    when 'GOLD' then 10000
    when 'PLATINUM' then 30000
    when 'DIAMOND' then 120000
    when 'MASTER' then 500000
    when 'LEGEND' then 12600000
    else min_score
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
