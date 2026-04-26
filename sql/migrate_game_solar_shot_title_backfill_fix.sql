delete from user_achievement_titles uat
using achievement_titles at
where uat.title_id = at.id
  and uat.source_type in ('HIGHLIGHT', 'BACKFILL')
  and at.code in (
      'SNIPE_SEEKER',
      'MOON_SEEKER',
      'SOLAR_SEEKER',
      'ATLAS_SEEKER',
      'MOON_FINDER',
      'SOLAR_FINDER',
      'ATLAS_FINDER',
      'SOLAR_WALKER',
      'ATLAS_WALKER',
      'ATLAS_SNIPER'
  );

with highlight_presence as (
    select
        ghs.id,
        ghs.user_id,
        ghs.season_id,
        bool_or(tag.tag = 'SNIPE') as has_snipe,
        bool_or(tag.tag = 'MOONSHOT') as has_moonshot,
        bool_or(tag.tag = 'SOLAR_SHOT') as has_solar_shot,
        bool_or(tag.tag = 'ATLAS_SHOT') as has_atlas_shot
    from game_highlight_states ghs
    left join lateral (
        select trim(tag_value) as tag
        from regexp_split_to_table(coalesce(ghs.strategy_tags, ''), ',') as split(tag_value)
        where trim(tag_value) <> ''
    ) tag on true
    group by ghs.id, ghs.user_id, ghs.season_id
),
highlight_codes as (
    select
        hp.user_id,
        hp.season_id,
        unnest(array_remove(array[
            case when hp.has_snipe then 'SNIPE_SEEKER' end,
            case when hp.has_moonshot then 'MOON_SEEKER' end,
            case when hp.has_solar_shot then 'SOLAR_SEEKER' end,
            case when hp.has_atlas_shot then 'ATLAS_SEEKER' end,
            case when hp.has_snipe and hp.has_moonshot then 'MOON_FINDER' end,
            case when hp.has_moonshot and hp.has_solar_shot then 'SOLAR_FINDER' end,
            case when hp.has_solar_shot and hp.has_atlas_shot then 'ATLAS_FINDER' end,
            case when hp.has_snipe and hp.has_moonshot and hp.has_solar_shot then 'SOLAR_WALKER' end,
            case when hp.has_moonshot and hp.has_solar_shot and hp.has_atlas_shot then 'ATLAS_WALKER' end,
            case when hp.has_snipe and hp.has_moonshot and hp.has_solar_shot and hp.has_atlas_shot then 'ATLAS_SNIPER' end
        ], null)) as code
    from highlight_presence hp
),
derived_codes as (
    select distinct
        hc.user_id,
        hc.season_id,
        hc.code
    from highlight_codes hc
)
insert into user_achievement_titles (
    user_id,
    title_id,
    season_id,
    source_highlight_state_id,
    source_type,
    earned_at
)
select
    dc.user_id,
    at.id,
    dc.season_id,
    null,
    'BACKFILL',
    now()
from derived_codes dc
join achievement_titles at
    on at.code = dc.code
on conflict (user_id, title_id) do nothing;
