package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAchievementTitleRepository extends JpaRepository<UserAchievementTitle, Long> {

    void deleteByUserId(Long userId);

    @Query("""
        select count(userTitle) > 0
        from UserAchievementTitle userTitle
        where userTitle.user.id = :userId
          and userTitle.title.code = :titleCode
          and userTitle.revokedAt is null
    """)
    boolean existsActiveByUserIdAndTitleCode(Long userId, String titleCode);

    @Query("""
        select userTitle
        from UserAchievementTitle userTitle
        where userTitle.user.id = :userId
          and userTitle.title.code = :titleCode
          and userTitle.revokedAt is null
    """)
    Optional<UserAchievementTitle> findActiveByUserIdAndTitleCode(Long userId, String titleCode);

    List<UserAchievementTitle> findByUserIdAndRevokedAtIsNull(Long userId);

    List<UserAchievementTitle> findByUserIdInAndRevokedAtIsNull(Collection<Long> userIds);
}
