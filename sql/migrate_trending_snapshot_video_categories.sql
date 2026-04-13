alter table public.video_trend_snapshots
  add column if not exists video_category_id text;

alter table public.video_trend_snapshots
  add column if not exists video_category_label text;
