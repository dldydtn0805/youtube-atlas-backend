with recalculated_highlight_scores as (
    select
        ghs.id,
        coalesce(sum(
            case
                when tags.tag is null then 0
                else case tags.tag
                    when 'ATLAS_SHOT' then 50000
                    when 'SOLAR_SHOT' then 35000
                    when 'MOONSHOT' then 20000
                    when 'BIG_CASHOUT' then 5000
                    when 'SMALL_CASHOUT' then 2500
                    when 'SNIPE' then 5000
                    else 0
                end
                + greatest(0, coalesce(ghs.rank_diff, 0)) * 20
                + case
                    when ghs.profit_rate_percent is null then 0
                    else least(
                        5000,
                        greatest(0, round((ghs.profit_rate_percent * 10)::numeric)::bigint)
                    )
                end
                + case
                    when ghs.profit_points is null or ghs.profit_points < 5000 then 0
                    else least(
                        15000,
                        greatest(0, round((sqrt((ghs.profit_points - 5000)::double precision) * 0.75)::numeric)::bigint)
                    )
                end
            end
        ), 0) as highlight_score
    from game_highlight_states ghs
    left join lateral (
        select trim(tag_value) as tag
        from regexp_split_to_table(coalesce(ghs.strategy_tags, ''), ',') as split(tag_value)
        where trim(tag_value) <> ''
    ) tags on true
    group by ghs.id
)
update game_highlight_states ghs
set
    best_settled_highlight_score = rhs.highlight_score,
    updated_at = now()
from recalculated_highlight_scores rhs
where ghs.id = rhs.id
  and ghs.best_settled_highlight_score <> rhs.highlight_score;
