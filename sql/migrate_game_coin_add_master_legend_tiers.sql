insert into game_season_coin_tiers (
    season_id,
    tier_code,
    display_name,
    min_coin_balance,
    badge_code,
    title_code,
    profile_theme_code,
    sort_order,
    created_at
)
select
    seasons.id,
    'MASTER',
    '마스터',
    36000000,
    'season-master',
    'master-investor',
    'master',
    6,
    now()
from game_seasons seasons
where not exists (
    select 1
    from game_season_coin_tiers tiers
    where tiers.season_id = seasons.id
      and tiers.tier_code = 'MASTER'
);

insert into game_season_coin_tiers (
    season_id,
    tier_code,
    display_name,
    min_coin_balance,
    badge_code,
    title_code,
    profile_theme_code,
    sort_order,
    created_at
)
select
    seasons.id,
    'LEGEND',
    '레전드',
    252000000,
    'season-legend',
    'legend-investor',
    'legend',
    7,
    now()
from game_seasons seasons
where not exists (
    select 1
    from game_season_coin_tiers tiers
    where tiers.season_id = seasons.id
      and tiers.tier_code = 'LEGEND'
);
