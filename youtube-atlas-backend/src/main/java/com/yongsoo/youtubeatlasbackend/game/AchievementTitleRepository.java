package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementTitleRepository extends JpaRepository<AchievementTitle, Long> {

    Optional<AchievementTitle> findByCode(String code);

    List<AchievementTitle> findByCodeIn(Collection<String> codes);

    List<AchievementTitle> findByEnabledTrueOrderBySortOrderAsc();

    List<AchievementTitle> findAllByOrderBySortOrderAsc();
}
