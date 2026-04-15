-- Run once after restricting new orders to whole-share quantities.
-- For existing OPEN positions, round any fractional quantity up to the next whole share
-- and expand stake/reserved points consistently to match the rounded quantity.

begin;

with normalized_positions as (
    select
        gp.id,
        gp.season_id,
        gp.user_id,
        gp.quantity as old_quantity,
        gp.stake_points as old_stake_points,
        ((gp.quantity + 99) / 100) * 100 as new_quantity,
        round((gp.stake_points::numeric * 100) / gp.quantity) as estimated_unit_stake_points
    from game_positions gp
    where gp.status = 'OPEN'
      and gp.quantity is not null
      and gp.quantity > 0
      and mod(gp.quantity, 100) <> 0
), recalculated_positions as (
    select
        np.id,
        np.season_id,
        np.user_id,
        np.old_stake_points,
        round((np.estimated_unit_stake_points * np.new_quantity) / 100.0) as new_stake_points
    from normalized_positions np
), updated_positions as (
    update game_positions gp
    set quantity = ((gp.quantity + 99) / 100) * 100,
        stake_points = rp.new_stake_points
    from recalculated_positions rp
    where gp.id = rp.id
    returning
        gp.season_id,
        gp.user_id,
        rp.new_stake_points - rp.old_stake_points as reserved_points_delta
), wallet_deltas as (
    select
        up.season_id,
        up.user_id,
        sum(up.reserved_points_delta) as reserved_points_delta
    from updated_positions up
    group by up.season_id, up.user_id
)
update game_wallets gw
set reserved_points = gw.reserved_points + wd.reserved_points_delta
from wallet_deltas wd
where gw.season_id = wd.season_id
  and gw.user_id = wd.user_id;

commit;
