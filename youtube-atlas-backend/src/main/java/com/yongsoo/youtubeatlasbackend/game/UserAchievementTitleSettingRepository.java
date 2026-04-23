package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAchievementTitleSettingRepository extends JpaRepository<UserAchievementTitleSetting, Long> {

    Optional<UserAchievementTitleSetting> findByUserId(Long userId);

    List<UserAchievementTitleSetting> findByUserIdIn(Collection<Long> userIds);
}
