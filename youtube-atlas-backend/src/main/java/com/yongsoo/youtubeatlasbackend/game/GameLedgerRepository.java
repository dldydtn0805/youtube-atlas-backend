package com.yongsoo.youtubeatlasbackend.game;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameLedgerRepository extends JpaRepository<GameLedger, Long> {

    void deleteByUserId(Long userId);
}
