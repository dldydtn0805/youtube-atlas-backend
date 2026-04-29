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
    ('SNIPE_SEEKER', 'Snipe Seeker', 'Sn. Seeker', 'NORMAL', '150위 밖에서 진입해 100위 안까지 끌어올린 스나이프 달성자입니다.', 10, true, now(), now()),
    ('MOON_SEEKER', 'Moon Seeker', 'M. Seeker', 'NORMAL', '100위 밖에서 발견한 영상이 50위 안까지 올라온 문샷 달성자입니다.', 20, true, now(), now()),
    ('SOLAR_SEEKER', 'Solar Seeker', 'So. Seeker', 'NORMAL', '50위 밖에서 잡은 영상이 20위 안까지 올라온 솔라 샷 달성자입니다.', 30, true, now(), now()),
    ('GALAXY_SEEKER', 'Galaxy Seeker', 'G. Seeker', 'NORMAL', '20위권에서 잡은 영상이 5위권까지 올라온 갤럭시 샷 달성자입니다.', 40, true, now(), now()),
    ('ATLAS_SEEKER', 'Atlas Seeker', 'A. Seeker', 'NORMAL', '5위권에서 잡은 영상이 1위까지 올라온 아틀라스 샷 달성자입니다.', 50, true, now(), now()),
    ('MOON_FINDER', 'Moon Finder', 'M. Finder', 'RARE', '150위 밖에서 진입해 50위 안까지 끌어올린 문샷 + 스나이프 복합 달성자입니다.', 60, true, now(), now()),
    ('SOLAR_FINDER', 'Solar Finder', 'S. Finder', 'RARE', '100위 밖에서 발견한 영상이 20위 안까지 올라온 문샷 + 솔라 샷 복합 달성자입니다.', 70, true, now(), now()),
    ('GALAXY_FINDER', 'Galaxy Finder', 'G. Finder', 'RARE', '50위권에서 5위권까지 올라온 솔라 샷 + 갤럭시 샷 복합 달성자입니다.', 80, true, now(), now()),
    ('ATLAS_FINDER', 'Atlas Finder', 'A. Finder', 'RARE', '20위권에서 1위까지 올라온 갤럭시 샷 + 아틀라스 샷 복합 달성자입니다.', 90, true, now(), now()),
    ('SOLAR_WALKER', 'Solar Walker', 'S. Walker', 'SUPER', '150위 밖에서 진입한 영상이 20위 안까지 올라온 스나이프 + 문샷 + 솔라 샷 연속 달성자입니다.', 100, true, now(), now()),
    ('GALAXY_WALKER', 'Galaxy Walker', 'G. Walker', 'SUPER', '100위권에서 5위권까지 올라온 문샷 + 솔라 샷 + 갤럭시 샷 연속 달성자입니다.', 110, true, now(), now()),
    ('ATLAS_WALKER', 'Atlas Walker', 'A. Walker', 'SUPER', '50위권에서 1위까지 올라온 솔라 샷 + 갤럭시 샷 + 아틀라스 샷 연속 달성자입니다.', 120, true, now(), now()),
    ('ATLAS_SNIPER', 'Atlas Sniper', 'A. Sniper', 'ULTIMATE', '150위 밖에서 잡은 영상이 1위까지 올라온 전 구간 복합 하이라이트 달성자입니다.', 130, true, now(), now())
on conflict (code) do update
set display_name = excluded.display_name,
    short_name = excluded.short_name,
    grade = excluded.grade,
    description = excluded.description,
    sort_order = excluded.sort_order,
    enabled = excluded.enabled,
    updated_at = now();

with recalculated_highlights as (
    select
        ghs.id,
        nullif(array_to_string(tags.strategy_tag_array, ','), '') as strategy_tags,
        case
            when cardinality(tags.strategy_tag_array) = 0 then null
            when 'ATLAS_SHOT' = any(tags.strategy_tag_array) then 'ATLAS_SHOT'
            when 'GALAXY_SHOT' = any(tags.strategy_tag_array) then 'GALAXY_SHOT'
            when 'SOLAR_SHOT' = any(tags.strategy_tag_array) then 'SOLAR_SHOT'
            when 'MOONSHOT' = any(tags.strategy_tag_array) then 'MOONSHOT'
            when 'BIG_CASHOUT' = any(tags.strategy_tag_array) then 'BIG_CASHOUT'
            when 'SMALL_CASHOUT' = any(tags.strategy_tag_array) then 'SMALL_CASHOUT'
            when 'SNIPE' = any(tags.strategy_tag_array) then 'SNIPE'
            else null
        end as best_settled_highlight_type,
        case
            when cardinality(tags.strategy_tag_array) = 0 then null
            when 'ATLAS_SHOT' = any(tags.strategy_tag_array) then '아틀라스 샷 적중'
            when 'GALAXY_SHOT' = any(tags.strategy_tag_array) then '갤럭시 샷 적중'
            when 'SOLAR_SHOT' = any(tags.strategy_tag_array) then '솔라 샷 적중'
            when 'MOONSHOT' = any(tags.strategy_tag_array) then '문샷 적중'
            when 'BIG_CASHOUT' = any(tags.strategy_tag_array) then '빅 캐시아웃'
            when 'SMALL_CASHOUT' = any(tags.strategy_tag_array) then '스몰 캐시아웃'
            when 'SNIPE' = any(tags.strategy_tag_array) then '스나이프 성공'
            else null
        end as best_settled_title,
        case
            when cardinality(tags.strategy_tag_array) = 0 then null
            when 'ATLAS_SHOT' = any(tags.strategy_tag_array) then concat(ghs.buy_rank, '위에서 잡은 영상이 ', ghs.highlight_rank, '위까지 올라왔습니다.')
            when 'GALAXY_SHOT' = any(tags.strategy_tag_array) then concat(ghs.buy_rank, '위에서 잡은 영상이 ', ghs.highlight_rank, '위까지 올라왔습니다.')
            when 'SOLAR_SHOT' = any(tags.strategy_tag_array) then concat(ghs.buy_rank, '위에서 잡은 영상이 ', ghs.highlight_rank, '위까지 올라왔습니다.')
            when 'MOONSHOT' = any(tags.strategy_tag_array) then concat(ghs.buy_rank, '위에서 잡은 영상이 ', ghs.highlight_rank, '위까지 올라왔습니다.')
            when 'BIG_CASHOUT' = any(tags.strategy_tag_array) then concat('수익률 ', ghs.profit_rate_percent, '% 플레이가 기록됐습니다.')
            when 'SMALL_CASHOUT' = any(tags.strategy_tag_array) then concat('수익률 ', ghs.profit_rate_percent, '% 플레이가 기록됐습니다.')
            when 'SNIPE' = any(tags.strategy_tag_array) then concat(ghs.buy_rank, '위에서 진입해 ', coalesce(ghs.rank_diff, 0), '계단을 앞질렀습니다.')
            else null
        end as best_settled_description,
        coalesce(sum(
            case
                when tag.tag is null then 0
                else case tag.tag
                    when 'ATLAS_SHOT' then 60000
                    when 'GALAXY_SHOT' then 45000
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
                    else least(5000, greatest(0, round((ghs.profit_rate_percent * 10)::numeric)::bigint))
                end
                + case
                    when ghs.profit_points is null or ghs.profit_points < 5000 then 0
                    else least(15000, greatest(0, round((sqrt((ghs.profit_points - 5000)::double precision) * 0.75)::numeric)::bigint))
                end
            end
        ), 0) as highlight_score
    from game_highlight_states ghs
    cross join lateral (
        select array_remove(array[
            case when ghs.buy_rank is not null and ghs.highlight_rank is not null and ghs.buy_rank >= 5 and ghs.highlight_rank <= 1 then 'ATLAS_SHOT' end,
            case when ghs.buy_rank is not null and ghs.highlight_rank is not null and ghs.buy_rank >= 20 and ghs.highlight_rank <= 5 then 'GALAXY_SHOT' end,
            case when ghs.buy_rank is not null and ghs.highlight_rank is not null and ghs.buy_rank >= 50 and ghs.highlight_rank <= 20 then 'SOLAR_SHOT' end,
            case when ghs.buy_rank is not null and ghs.highlight_rank is not null and ghs.buy_rank >= 100 and ghs.highlight_rank <= 50 then 'MOONSHOT' end,
            case
                when ghs.profit_rate_percent is not null and ghs.profit_rate_percent >= 1000 then 'BIG_CASHOUT'
                when ghs.profit_rate_percent is not null and ghs.profit_rate_percent >= 300 then 'SMALL_CASHOUT'
            end,
            case when ghs.buy_rank is not null and ghs.highlight_rank is not null and ghs.buy_rank >= 150 and ghs.highlight_rank <= 100 then 'SNIPE' end
        ], null) as strategy_tag_array
    ) tags
    left join lateral unnest(tags.strategy_tag_array) as tag(tag) on true
    group by ghs.id, tags.strategy_tag_array
)
update game_highlight_states ghs
set
    strategy_tags = rh.strategy_tags,
    best_settled_highlight_type = rh.best_settled_highlight_type,
    best_settled_title = rh.best_settled_title,
    best_settled_description = rh.best_settled_description,
    best_settled_highlight_score = rh.highlight_score,
    updated_at = now()
from recalculated_highlights rh
where ghs.id = rh.id;

delete from user_achievement_titles uat
using achievement_titles at
where uat.title_id = at.id
  and uat.source_type in ('HIGHLIGHT', 'BACKFILL')
  and at.code in (
      'SNIPE_SEEKER',
      'MOON_SEEKER',
      'SOLAR_SEEKER',
      'GALAXY_SEEKER',
      'ATLAS_SEEKER',
      'MOON_FINDER',
      'SOLAR_FINDER',
      'GALAXY_FINDER',
      'ATLAS_FINDER',
      'SOLAR_WALKER',
      'GALAXY_WALKER',
      'ATLAS_WALKER',
      'ATLAS_SNIPER'
  );

with highlight_tag_presence as (
    select
        ghs.id,
        ghs.user_id,
        ghs.season_id,
        bool_or(tag.tag = 'SNIPE') as has_snipe,
        bool_or(tag.tag = 'MOONSHOT') as has_moonshot,
        bool_or(tag.tag = 'SOLAR_SHOT') as has_solar_shot,
        bool_or(tag.tag = 'GALAXY_SHOT') as has_galaxy_shot,
        bool_or(tag.tag = 'ATLAS_SHOT') as has_atlas_shot
    from game_highlight_states ghs
    left join lateral (
        select trim(tag_value) as tag
        from regexp_split_to_table(coalesce(ghs.strategy_tags, ''), ',') as split(tag_value)
        where trim(tag_value) <> ''
    ) tag on true
    group by ghs.id, ghs.user_id, ghs.season_id
),
derived_codes as (
    select
        htp.id as source_highlight_state_id,
        htp.user_id,
        htp.season_id,
        unnest(array_remove(array[
            case when htp.has_snipe then 'SNIPE_SEEKER' end,
            case when htp.has_moonshot then 'MOON_SEEKER' end,
            case when htp.has_solar_shot then 'SOLAR_SEEKER' end,
            case when htp.has_galaxy_shot then 'GALAXY_SEEKER' end,
            case when htp.has_atlas_shot then 'ATLAS_SEEKER' end,
            case when htp.has_snipe and htp.has_moonshot then 'MOON_FINDER' end,
            case when htp.has_moonshot and htp.has_solar_shot then 'SOLAR_FINDER' end,
            case when htp.has_solar_shot and htp.has_galaxy_shot then 'GALAXY_FINDER' end,
            case when htp.has_galaxy_shot and htp.has_atlas_shot then 'ATLAS_FINDER' end,
            case when htp.has_snipe and htp.has_moonshot and htp.has_solar_shot then 'SOLAR_WALKER' end,
            case when htp.has_moonshot and htp.has_solar_shot and htp.has_galaxy_shot then 'GALAXY_WALKER' end,
            case when htp.has_solar_shot and htp.has_galaxy_shot and htp.has_atlas_shot then 'ATLAS_WALKER' end,
            case when htp.has_snipe and htp.has_moonshot and htp.has_solar_shot and htp.has_galaxy_shot and htp.has_atlas_shot then 'ATLAS_SNIPER' end
        ], null)) as code
    from highlight_tag_presence htp
),
distinct_earned_codes as (
    select distinct on (dc.user_id, dc.code)
        dc.user_id,
        dc.season_id,
        dc.source_highlight_state_id,
        dc.code
    from derived_codes dc
    order by dc.user_id, dc.code, dc.source_highlight_state_id
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
    dec.user_id,
    at.id,
    dec.season_id,
    dec.source_highlight_state_id,
    'BACKFILL',
    now()
from distinct_earned_codes dec
join achievement_titles at
    on at.code = dec.code
on conflict (user_id, title_id) do nothing;
