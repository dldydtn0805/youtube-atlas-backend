package com.yongsoo.youtubeatlasbackend.game;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDividendPayoutRepository extends JpaRepository<GameDividendPayout, Long> {

    void deleteByUserId(Long userId);
}
