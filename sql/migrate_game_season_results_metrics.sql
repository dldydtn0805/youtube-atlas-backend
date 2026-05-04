alter table if exists game_season_results
    add column if not exists starting_balance_points bigint not null default 0,
    add column if not exists profit_rate_percent double precision,
    add column if not exists final_highlight_score bigint not null default 0,
    add column if not exists final_tier_code varchar(50),
    add column if not exists final_tier_name varchar(100),
    add column if not exists final_tier_badge_code varchar(100),
    add column if not exists final_tier_title_code varchar(100),
    add column if not exists best_position_profit_rate_percent double precision,
    add column if not exists best_position_rank_diff integer,
    add column if not exists best_position_buy_rank integer,
    add column if not exists best_position_sell_rank integer;

update game_season_results result
set starting_balance_points = coalesce(season.starting_balance_points, 0)
from game_seasons season
where result.season_id = season.id
  and result.starting_balance_points = 0;

update game_season_results
set profit_rate_percent = (round(((final_asset_points - starting_balance_points)::numeric * 1000) / starting_balance_points) / 10.0)::double precision
where starting_balance_points > 0
  and profit_rate_percent is null;

update game_season_results result
set final_highlight_score = scores.highlight_score
from (
    select season_id, user_id, coalesce(sum(best_settled_highlight_score), 0) as highlight_score
    from game_highlight_states
    where best_settled_highlight_score > 0
    group by season_id, user_id
) scores
where result.season_id = scores.season_id
  and result.user_id = scores.user_id
  and result.final_highlight_score = 0;

update game_season_results result
set
    final_tier_code = tier.tier_code,
    final_tier_name = tier.display_name,
    final_tier_badge_code = tier.badge_code,
    final_tier_title_code = tier.title_code
from game_season_tiers tier
where result.season_id = tier.season_id
  and result.final_highlight_score >= tier.min_score
  and result.final_tier_code is null
  and not exists (
      select 1
      from game_season_tiers higher
      where higher.season_id = tier.season_id
        and result.final_highlight_score >= higher.min_score
        and (
            higher.min_score > tier.min_score
            or (higher.min_score = tier.min_score and higher.sort_order > tier.sort_order)
        )
  );

update game_season_results result
set
    best_position_profit_rate_percent = case
        when position.stake_points > 0 then (round((position.pnl_points::numeric * 1000) / position.stake_points) / 10.0)::double precision
        else null
    end,
    best_position_rank_diff = position.rank_diff,
    best_position_buy_rank = position.buy_rank,
    best_position_sell_rank = position.sell_rank
from game_positions position
where result.best_position_id = position.id
  and result.best_position_profit_rate_percent is null;
