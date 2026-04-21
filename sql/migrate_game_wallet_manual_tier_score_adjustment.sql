alter table if exists game_wallets
    add column if not exists manual_tier_score_adjustment bigint not null default 0;
