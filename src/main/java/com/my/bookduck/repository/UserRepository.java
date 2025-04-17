package com.my.bookduck.repository;

import com.my.bookduck.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByLoginId(String loginId);

    User findByEmail(String email);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);



}
