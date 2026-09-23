
package com.capstone.login.repository;

import com.capstone.login.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    // username == email (set at registration) — primary auth lookup
    Optional<AppUser> findByUsername(String username);
}

