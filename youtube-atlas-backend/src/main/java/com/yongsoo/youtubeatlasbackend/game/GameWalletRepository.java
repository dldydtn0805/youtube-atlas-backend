package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GameWalletRepository extends JpaRepository<GameWallet, Long> {

    Optional<GameWallet> findBySeasonIdAndUserId(Long seasonId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select wallet
        from GameWallet wallet
        where wallet.season.id = :seasonId
          and wallet.user.id = :userId
    """)
    Optional<GameWallet> findBySeasonIdAndUserIdForUpdate(Long seasonId, Long userId);

    List<GameWallet> findBySeasonId(Long seasonId);

    void deleteByUserId(Long userId);
}
