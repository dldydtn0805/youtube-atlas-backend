package com.yongsoo.youtubeatlasbackend.auth;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByGoogleSubject(String googleSubject);

    List<AppUser> findTop8ByOrderByCreatedAtDesc();
}
