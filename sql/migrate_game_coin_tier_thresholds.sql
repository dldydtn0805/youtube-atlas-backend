update game_season_coin_tiers
set min_coin_balance = case tier_code
    when 'BRONZE' then 0
    when 'SILVER' then 200000
    when 'GOLD' then 1000000
    when 'PLATINUM' then 5000000
    when 'DIAMOND' then 20000000
    else min_coin_balance
end
where tier_code in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND');
