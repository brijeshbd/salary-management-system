package com.acme.salary.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrUserRepository extends JpaRepository<HrUser, Long> {

    Optional<HrUser> findByEmail(String email);
}
