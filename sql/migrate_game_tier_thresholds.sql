update game_season_tiers
set min_score = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 5000
    when 'GOLD' then 15000
    when 'PLATINUM' then 60000
    when 'DIAMOND' then 300000
    when 'MASTER' then 1800000
    when 'LEGEND' then 12600000
    else min_score
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
