package com.my.bookduck.repository;

import com.my.bookduck.domain.store.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository; // @Repository 어노테이션 추가 권장

@Repository // Spring Bean으로 등록 (권장)
// JpaRepository<엔티티 클래스, 엔티티 ID 타입>
public interface PurchaseRepository extends JpaRepository<Purchase, String> { // ID 타입을 Integer 에서 String 으로 변경
    // 필요에 따라 추가적인 쿼리 메소드를 정의할 수 있습니다.
    // 예: 특정 사용자의 모든 구매 내역 조회 (Purchase 엔티티에 userId 필드가 String 이므로)
    // List<Purchase> findByUserId(String userId);
}