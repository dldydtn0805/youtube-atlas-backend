package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameWalletRepository extends JpaRepository<GameWallet, Long> {

    Optional<GameWallet> findBySeasonIdAndUserId(Long seasonId, Long userId);

    List<GameWallet> findBySeasonId(Long seasonId);
}
