create table if not exists comment_highlight_fetches (
    video_id varchar(255) primary key,
    fetched_at timestamp with time zone not null,
    expires_at timestamp with time zone not null
);

create table if not exists comment_highlights (
    id bigserial primary key,
    video_id varchar(255) not null,
    comment_id varchar(255) not null,
    author_name varchar(255) not null,
    text varchar(1000) not null,
    like_count bigint not null,
    fetched_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    constraint comment_highlights_video_comment_uk unique (video_id, comment_id)
);

create index if not exists comment_highlights_video_expires_like_idx
    on comment_highlights (video_id, expires_at, like_count desc);

create index if not exists comment_highlights_expires_at_idx
    on comment_highlights (expires_at);

create index if not exists comment_highlight_fetches_expires_at_idx
    on comment_highlight_fetches (expires_at);
