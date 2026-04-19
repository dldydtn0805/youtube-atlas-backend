alter table comments
  add column if not exists user_id bigint;

create index if not exists comments_video_id_user_id_created_at_idx
  on comments (video_id, user_id, created_at desc);
