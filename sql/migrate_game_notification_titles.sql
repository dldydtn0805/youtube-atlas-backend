alter table game_notifications
    add column if not exists title_code varchar(80);

alter table game_notifications
    add column if not exists title_display_name varchar(80);

alter table game_notifications
    add column if not exists title_grade varchar(20);
