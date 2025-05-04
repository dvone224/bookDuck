package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book; // Book 임포트 추가 (findbyUserAndBook 용도)
import com.my.bookduck.domain.user.Cart;
import com.my.bookduck.domain.user.CartId; // CartId 임포트 추가 (메소드 파라미터는 Long이지만, 내부 ID구조가 CartId일 수 있음)
import com.my.bookduck.domain.user.User; // User 임포트 추가 (findByUserAndBook 용도)
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository; // Repository 어노테이션 추가 권장
// import org.springframework.transaction.annotation.Transactional; // Repository에서는 보통 필요 없음 (Service에서 관리)

import java.util.List;

@Repository // Spring Bean으로 등록 권장
// --- ID 타입 확인: Cart 엔티티가 CartId 복합키를 사용한다면 JpaRepository<Cart, CartId> 가 맞습니다. ---
// --- 현재는 Long으로 되어있어, Cart 엔티티에 @Id가 하나만 있고 그 타입이 Long이라고 가정합니다. ---
// --- 만약 Cart 엔티티가 @IdClass(CartId.class) 를 사용한다면 아래 라인을 JpaRepository<Cart, CartId> 로 바꿔야 합니다. ---
public interface CartRepository extends JpaRepository<Cart, Long> { // ID 타입을 Long으로 가정 (만약 CartId 복합키면 CartId로 변경)

    // userId로 Cart 조회 (중복체크 등에서 사용될 수 있음)
    List<Cart> findByUserId(Long userId);

    // User와 Book 객체로 Cart 조회 (존재 여부 확인 등에 사용 가능)
    Cart findByUserAndBook(User user, Book book);

    // 사용자 ID로 장바구니 목록 조회 + Book 정보 Fetch Join (N+1 방지)
    @Query("SELECT c FROM Cart c JOIN FETCH c.book WHERE c.userId = :userId")
    List<Cart> findWithBookByUserId(@Param("userId") Long userId);

    // userId와 bookId로 특정 Cart 항목 삭제
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.userId = :userId AND c.bookId = :bookId")
    void deleteByUserIdAndBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    // 특정 사용자의 모든 장바구니 항목 삭제
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // ✨ --- 중복 확인 메소드 추가 --- ✨
    /**
     * 특정 사용자의 장바구니에 특정 책이 이미 존재하는지 확인합니다.
     * Cart 엔티티의 userId 필드와 bookId 필드를 기준으로 검색합니다.
     *
     * @param userId 확인할 사용자 ID
     * @param bookId 확인할 책 ID (ISBN 역할)
     * @return 해당 항목이 존재하면 true, 그렇지 않으면 false
     */
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
}