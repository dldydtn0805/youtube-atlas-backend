begin;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
),
dividend_duplicates as (
    select payout_id
    from (
        select
            payout.id as payout_id,
            row_number() over (
                partition by payout.season_id, payout.position_id, coalesce(rewrite.keep_id, payout.trend_run_id)
                order by payout.id desc
            ) as row_num
        from game_dividend_payouts payout
        left join run_rewrites rewrite on rewrite.old_id = payout.trend_run_id
    ) ranked
    where row_num > 1
)
delete from game_dividend_payouts payout
where payout.id in (select payout_id from dividend_duplicates);

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
update game_coin_payouts payout
set trend_run_id = rewrite.keep_id
from run_rewrites rewrite
where payout.trend_run_id = rewrite.old_id;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
update game_dividend_payouts payout
set trend_run_id = rewrite.keep_id
from run_rewrites rewrite
where payout.trend_run_id = rewrite.old_id;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
update video_trend_signals signal
set current_run_id = rewrite.keep_id
from run_rewrites rewrite
where signal.current_run_id = rewrite.old_id;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
update video_trend_signals signal
set previous_run_id = rewrite.keep_id
from run_rewrites rewrite
where signal.previous_run_id = rewrite.old_id;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
delete from video_trend_snapshots snapshot
using run_rewrites rewrite
where snapshot.run_id = rewrite.old_id;

with bucketed_runs as (
    select
        id,
        region_code,
        category_id,
        source,
        to_timestamp(floor(extract(epoch from captured_at) / 3600) * 3600) at time zone 'utc' as capture_slot_at
    from video_trend_runs
),
run_rewrites as (
    select
        current_run.id as old_id,
        keeper.id as keep_id
    from bucketed_runs current_run
    join lateral (
        select candidate.id
        from bucketed_runs candidate
        where candidate.region_code = current_run.region_code
          and candidate.category_id = current_run.category_id
          and candidate.source = current_run.source
          and candidate.capture_slot_at = current_run.capture_slot_at
        order by candidate.id desc
        limit 1
    ) keeper on true
    where current_run.id <> keeper.id
)
delete from video_trend_runs run
using run_rewrites rewrite
where run.id = rewrite.old_id;

commit;
