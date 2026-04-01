package com.example.auth.repo;

import com.example.auth.model.AuthUser;
import com.example.auth.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {
    Optional<AuthUser> findByEmail(String email);

    List<AuthUser> findAllByRole(Role role);
}

