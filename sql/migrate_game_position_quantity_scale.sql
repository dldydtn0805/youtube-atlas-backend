-- Run once when switching game position quantities to fixed-point storage.
-- Before: 1 = 1.00 share
-- After: 100 = 1.00 share, 1 = 0.01 share

begin;

update game_positions
set quantity = quantity * 100
where quantity is not null
  and quantity > 0
  and quantity < 100;

commit;
