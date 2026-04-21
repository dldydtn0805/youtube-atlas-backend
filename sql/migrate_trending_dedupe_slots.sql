begin;

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
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

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
)
update game_dividend_payouts payout
set trend_run_id = rewrite.keep_id
from run_rewrites rewrite
where payout.trend_run_id = rewrite.old_id;

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
)
update video_trend_signals signal
set current_run_id = rewrite.keep_id
from run_rewrites rewrite
where signal.current_run_id = rewrite.old_id;

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
)
update video_trend_signals signal
set previous_run_id = rewrite.keep_id
from run_rewrites rewrite
where signal.previous_run_id = rewrite.old_id;

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
)
delete from video_trend_snapshots snapshot
using run_rewrites rewrite
where snapshot.run_id = rewrite.old_id;

with duplicate_runs as (
    select
        id as old_id,
        max(id) over (
            partition by region_code, category_id, source, captured_at
        ) as keep_id
    from video_trend_runs
),
run_rewrites as (
    select old_id, keep_id
    from duplicate_runs
    where old_id <> keep_id
)
delete from video_trend_runs run
using run_rewrites rewrite
where run.id = rewrite.old_id;

with duplicate_snapshots as (
    select
        id,
        row_number() over (
            partition by run_id, video_id
            order by id desc
        ) as row_num
    from video_trend_snapshots
)
delete from video_trend_snapshots snapshot
where snapshot.id in (
    select id
    from duplicate_snapshots
    where row_num > 1
);

create unique index if not exists uk_video_trend_runs_slot
    on video_trend_runs (region_code, category_id, source, captured_at);

create unique index if not exists uk_video_trend_snapshots_run_video
    on video_trend_snapshots (run_id, video_id);

commit;
