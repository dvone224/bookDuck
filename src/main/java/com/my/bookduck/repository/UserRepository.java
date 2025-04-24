package com.my.bookduck.repository;

import com.my.bookduck.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByLoginId(String loginId);

    User findByEmail(String email);

    User findByid(Long id);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // --- 사용자 이름 검색 메소드 추가 ---
    /**
     * 이름(name 필드)에 특정 문자열이 포함된 사용자를 대소문자 구분 없이 검색합니다.
     * @param nickName 검색할 이름의 일부
     * @param pageable 페이징 정보 (결과 수 제한 등)
     * @return 검색된 User 엔티티 리스트
     */
    List<User> findByNickNameContainingIgnoreCase(String nickName, Pageable pageable);

}
