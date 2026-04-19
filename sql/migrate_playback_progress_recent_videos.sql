alter table playback_progress
  drop constraint if exists uk_playback_progress_user;
drop index if exists uk_playback_progress_user;

create unique index if not exists uk_playback_progress_user_video
  on playback_progress (user_id, video_id);

create index if not exists playback_progress_user_updated_at_idx
  on playback_progress (user_id, updated_at desc);
