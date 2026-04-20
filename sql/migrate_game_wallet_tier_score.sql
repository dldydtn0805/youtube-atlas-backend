alter table if exists game_wallets
    add column if not exists tier_score bigint;
