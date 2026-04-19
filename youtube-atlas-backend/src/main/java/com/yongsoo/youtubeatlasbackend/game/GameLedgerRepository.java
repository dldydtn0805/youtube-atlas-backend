package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameLedgerRepository extends JpaRepository<GameLedger, Long> {

    @Modifying
    @Query("""
        delete from GameLedger ledger
        where ledger.position.id in :positionIds
    """)
    long deleteByPositionIds(Collection<Long> positionIds);

    @Query("""
        select coalesce(sum(ledger.amountPoints), 0)
        from GameLedger ledger
        where ledger.season.id = :seasonId
          and ledger.user.id = :userId
          and ledger.type = :type
    """)
    long sumAmountPointsBySeasonIdAndUserIdAndType(Long seasonId, Long userId, LedgerType type);

    long countByUserIdAndTypeIn(Long userId, Collection<LedgerType> types);

    void deleteByUserId(Long userId);
}
