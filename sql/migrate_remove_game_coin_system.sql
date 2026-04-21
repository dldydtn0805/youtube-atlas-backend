alter table if exists game_wallets
    drop column if exists coin_balance;

drop table if exists game_coin_payouts;
drop table if exists game_season_coin_results;

do $$
begin
    if to_regclass('game_season_coin_tiers') is not null
       and to_regclass('game_season_tiers') is null then
        alter table game_season_coin_tiers
            rename to game_season_tiers;
    end if;
end $$;

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_name = 'game_season_tiers'
          and column_name = 'min_coin_balance'
    ) then
        alter table game_season_tiers
            rename column min_coin_balance to min_score;
    end if;
end $$;

create table if not exists game_season_tiers (
    id bigserial primary key,
    season_id bigint not null,
    tier_code varchar(50) not null,
    display_name varchar(100) not null,
    min_score bigint not null,
    badge_code varchar(100) not null,
    title_code varchar(100) not null,
    profile_theme_code varchar(100) not null,
    sort_order integer not null,
    created_at timestamptz not null,
    constraint fk_game_season_tiers_season foreign key (season_id) references game_seasons (id)
);

alter table if exists game_season_tiers
    drop constraint if exists uk_game_season_coin_tiers_season_code;
alter table if exists game_season_tiers
    drop constraint if exists uk_game_season_coin_tiers_season_order;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uk_game_season_tiers_season_code'
    ) then
        alter table game_season_tiers
            add constraint uk_game_season_tiers_season_code unique (season_id, tier_code);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uk_game_season_tiers_season_order'
    ) then
        alter table game_season_tiers
            add constraint uk_game_season_tiers_season_order unique (season_id, sort_order);
    end if;
end $$;

drop index if exists idx_game_season_coin_tiers_season;
create index if not exists idx_game_season_tiers_season
    on game_season_tiers (season_id, sort_order);
