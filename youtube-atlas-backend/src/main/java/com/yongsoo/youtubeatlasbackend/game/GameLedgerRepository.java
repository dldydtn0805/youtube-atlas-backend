package com.yongsoo.youtubeatlasbackend.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameLedgerRepository extends JpaRepository<GameLedger, Long> {

    @Query("""
        select coalesce(sum(ledger.amountPoints), 0)
        from GameLedger ledger
        where ledger.season.id = :seasonId
          and ledger.user.id = :userId
          and ledger.type = :type
    """)
    long sumAmountPointsBySeasonIdAndUserIdAndType(Long seasonId, Long userId, LedgerType type);

    void deleteByUserId(Long userId);
}
