package com.yongsoo.youtubeatlasbackend.auth;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsById(Long id);

    Optional<AppUser> findByGoogleSubject(String googleSubject);

    List<AppUser> findTop8ByOrderByCreatedAtDesc();

    List<AppUser> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AppUser> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(
        String email,
        String displayName,
        Pageable pageable
    );
}
