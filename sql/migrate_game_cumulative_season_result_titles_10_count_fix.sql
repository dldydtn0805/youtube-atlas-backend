with eligible_tier_titles as (
    select
        result.user_id,
        result.final_tier_code
    from game_season_results result
    where result.final_tier_code in ('MASTER', 'LEGEND')
    group by result.user_id, result.region_code, result.final_tier_code
    having count(*) >= 10
),
deleted_titles as (
    delete from user_achievement_titles earned
    using achievement_titles title
    where earned.title_id = title.id
      and earned.source_type = 'SEASON_RESULT'
      and (
          (
              title.code = 'MASTER_WALKER'
              and not exists (
                  select 1
                  from eligible_tier_titles eligible
                  where eligible.user_id = earned.user_id
                    and eligible.final_tier_code = 'MASTER'
              )
          )
          or (
              title.code = 'LEGEND_SNIPER'
              and not exists (
                  select 1
                  from eligible_tier_titles eligible
                  where eligible.user_id = earned.user_id
                    and eligible.final_tier_code = 'LEGEND'
              )
          )
      )
    returning earned.user_id
),
affected_users as (
    select distinct user_id
    from deleted_titles
),
best_titles as (
    select distinct on (earned.user_id)
        earned.user_id,
        title.id as title_id
    from user_achievement_titles earned
    join achievement_titles title
        on title.id = earned.title_id
    join affected_users affected
        on affected.user_id = earned.user_id
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
