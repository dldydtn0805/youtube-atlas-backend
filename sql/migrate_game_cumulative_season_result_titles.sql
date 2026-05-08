insert into achievement_titles (
    code,
    display_name,
    short_name,
    grade,
    description,
    sort_order,
    enabled,
    created_at,
    updated_at
)
values
    ('DIAMOND_SEEKER', 'Diamond Seeker', 'D. Seeker', 'NORMAL', '시즌 종료 티어를 다이아몬드로 1회 마무리한 플레이어입니다.', 140, true, now(), now()),
    ('DIAMOND_FINDER', 'Diamond Finder', 'D. Finder', 'RARE', '시즌 종료 티어를 다이아몬드로 5회 마무리한 플레이어입니다.', 150, true, now(), now()),
    ('MASTER_FINDER', 'Master Finder', 'M. Finder', 'RARE', '시즌 종료 티어를 마스터로 1회 마무리한 플레이어입니다.', 160, true, now(), now()),
    ('MASTER_WALKER', 'Master Walker', 'M. Walker', 'SUPER', '시즌 종료 티어를 마스터로 10회 마무리한 플레이어입니다.', 170, true, now(), now()),
    ('LEGEND_WALKER', 'Legend Walker', 'L. Walker', 'SUPER', '시즌 종료 티어를 레전드로 1회 마무리한 플레이어입니다.', 180, true, now(), now()),
    ('LEGEND_SNIPER', 'Legend Sniper', 'L. Sniper', 'ULTIMATE', '시즌 종료 티어를 레전드로 10회 마무리한 플레이어입니다.', 190, true, now(), now())
on conflict (code) do update
set display_name = excluded.display_name,
    short_name = excluded.short_name,
    grade = excluded.grade,
    description = excluded.description,
    sort_order = excluded.sort_order,
    enabled = excluded.enabled,
    updated_at = now();

with ranked_tier_results as (
    select
        result.user_id,
        result.season_id,
        result.final_tier_code,
        row_number() over (
            partition by result.user_id, result.region_code, result.final_tier_code
            order by result.season_end_at, result.created_at, result.id
        ) as tier_count
    from game_season_results result
    where result.final_tier_code in ('DIAMOND', 'MASTER', 'LEGEND')
),
earned_codes as (
    select
        ranked.user_id,
        ranked.season_id,
        unnest(array_remove(array[
            case when ranked.final_tier_code = 'DIAMOND' and ranked.tier_count = 1 then 'DIAMOND_SEEKER' end,
            case when ranked.final_tier_code = 'DIAMOND' and ranked.tier_count = 5 then 'DIAMOND_FINDER' end,
            case when ranked.final_tier_code = 'MASTER' and ranked.tier_count = 1 then 'MASTER_FINDER' end,
            case when ranked.final_tier_code = 'MASTER' and ranked.tier_count = 10 then 'MASTER_WALKER' end,
            case when ranked.final_tier_code = 'LEGEND' and ranked.tier_count = 1 then 'LEGEND_WALKER' end,
            case when ranked.final_tier_code = 'LEGEND' and ranked.tier_count = 10 then 'LEGEND_SNIPER' end
        ], null)) as code
    from ranked_tier_results ranked
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
    earned.user_id,
    title.id,
    earned.season_id,
    null,
    'SEASON_RESULT',
    now()
from earned_codes earned
join achievement_titles title
    on title.code = earned.code
on conflict (user_id, title_id) do nothing;

with best_titles as (
    select distinct on (earned.user_id)
        earned.user_id,
        title.id as title_id
    from user_achievement_titles earned
    join achievement_titles title
        on title.id = earned.title_id
    where earned.revoked_at is null
      and title.enabled = true
    order by
        earned.user_id,
        case title.grade
            when 'ULTIMATE' then 4
            when 'SUPER' then 3
            when 'RARE' then 2
            else 1
        end desc,
        title.sort_order desc,
        title.code
)
update user_achievement_title_settings setting
set selected_title_id = best.title_id,
    selection_mode = 'AUTO',
    updated_at = now()
from best_titles best
where setting.user_id = best.user_id
  and setting.selection_mode = 'AUTO'
  and (
      setting.selected_title_id is null
      or setting.selected_title_id <> best.title_id
  );
