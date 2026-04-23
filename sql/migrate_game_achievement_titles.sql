create table if not exists achievement_titles (
    id bigserial primary key,
    code varchar(80) not null,
    display_name varchar(80) not null,
    short_name varchar(40) not null,
    grade varchar(20) not null,
    description varchar(500) not null,
    sort_order integer not null,
    enabled boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_achievement_titles_code unique (code)
);

create table if not exists user_achievement_titles (
    id bigserial primary key,
    user_id bigint not null,
    title_id bigint not null,
    season_id bigint,
    source_highlight_state_id bigint,
    source_type varchar(30) not null,
    earned_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    revoked_reason varchar(500),
    constraint uk_user_achievement_titles_user_title unique (user_id, title_id),
    constraint fk_user_achievement_titles_user foreign key (user_id) references app_users (id),
    constraint fk_user_achievement_titles_title foreign key (title_id) references achievement_titles (id),
    constraint fk_user_achievement_titles_season foreign key (season_id) references game_seasons (id)
);

create table if not exists user_achievement_title_settings (
    id bigserial primary key,
    user_id bigint not null,
    selected_title_id bigint,
    selection_mode varchar(20) not null default 'AUTO',
    updated_at timestamp with time zone not null,
    constraint uk_user_achievement_title_settings_user unique (user_id),
    constraint fk_user_achievement_title_settings_user foreign key (user_id) references app_users (id),
    constraint fk_user_achievement_title_settings_title foreign key (selected_title_id) references achievement_titles (id)
);

create index if not exists idx_user_achievement_titles_user
    on user_achievement_titles (user_id);

create index if not exists idx_user_achievement_titles_title
    on user_achievement_titles (title_id);

update achievement_titles
set code = 'MOON_SEEKER',
    updated_at = now()
where code = 'MOON_HUNTER'
  and not exists (select 1 from achievement_titles where code = 'MOON_SEEKER');

update achievement_titles
set code = 'SNIPE_SEEKER',
    updated_at = now()
where code = 'TREND_SNIPER'
  and not exists (select 1 from achievement_titles where code = 'SNIPE_SEEKER');

update achievement_titles
set code = 'ATLAS_FINDER',
    updated_at = now()
where code = 'ATLAS_HUNTER'
  and not exists (select 1 from achievement_titles where code = 'ATLAS_FINDER');

update achievement_titles
set code = 'MOON_FINDER',
    updated_at = now()
where code = 'MOON_SNIPER'
  and not exists (select 1 from achievement_titles where code = 'MOON_FINDER');

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
    (
        'ATLAS_SEEKER',
        'Atlas Seeker',
        'A. Seeker',
        'NORMAL',
        '50위 밖에서 잡은 영상이 10위 안까지 올라온 아틀라스 샷 달성자입니다.',
        10,
        true,
        now(),
        now()
    ),
    (
        'MOON_SEEKER',
        'Moon Seeker',
        'M. Seeker',
        'NORMAL',
        '100위 밖에서 발견한 영상이 50위 안까지 올라온 문샷 달성자입니다.',
        20,
        true,
        now(),
        now()
    ),
    (
        'SNIPE_SEEKER',
        'Snipe Seeker',
        'S. Seeker',
        'NORMAL',
        '150위 밖에서 진입해 100위 안까지 끌어올린 스나이프 달성자입니다.',
        30,
        true,
        now(),
        now()
    ),
    (
        'ATLAS_FINDER',
        'Atlas Finder',
        'A. Finder',
        'RARE',
        '100위 밖에서 발견한 영상이 10위 안까지 올라온 아틀라스 샷 + 문샷 복합 달성자입니다.',
        35,
        true,
        now(),
        now()
    ),
    (
        'MOON_FINDER',
        'Moon Finder',
        'M. Finder',
        'RARE',
        '150위 밖에서 진입해 50위 안까지 끌어올린 문샷 + 스나이프 복합 달성자입니다.',
        36,
        true,
        now(),
        now()
    ),
    (
        'ATLAS_SNIPER',
        'Atlas Sniper',
        'A. Sniper',
        'SUPER',
        '150위 밖에서 잡은 영상이 10위 안까지 올라온 복합 하이라이트 달성자입니다.',
        40,
        true,
        now(),
        now()
    )
on conflict (code) do update
set display_name = excluded.display_name,
    short_name = excluded.short_name,
    grade = excluded.grade,
    description = excluded.description,
    sort_order = excluded.sort_order,
    enabled = excluded.enabled,
    updated_at = now();
