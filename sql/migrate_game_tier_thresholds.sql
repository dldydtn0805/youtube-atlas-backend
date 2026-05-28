update game_season_tiers
set min_score = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 60000
    when 'GOLD' then 120000
    when 'PLATINUM' then 360000
    when 'DIAMOND' then 1440000
    when 'MASTER' then 6000000
    when 'LEGEND' then 6000000
    else min_score
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'LEGEND');
