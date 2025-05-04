package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.Cart;
// CartId 임포트 제거 가능
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.BookRepository;
import com.my.bookduck.repository.CartRepository;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    @Transactional // 쓰기 작업
    public void createCart(Long userId, Long bookId) throws IllegalStateException {
        log.info("Creating cart entry for userId: {} and bookId: {}", userId, bookId);


        // --- ✨ 서비스 내에서 User와 Book 엔티티 조회 ✨ ---
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found for ID: {}", userId);
                    return new IllegalStateException("사용자 정보를 찾을 수 없습니다.");
                });

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> {
                    log.error("Book not found for ID: {}", bookId);
                    return new IllegalStateException("도서 정보를 찾을 수 없습니다.");
                });

        // --- 중복 체크 (조회된 엔티티 사용) ---
        // 더 효율적인 방법: CartRepository에 existsByUserIdAndBookId 메소드 추가
        boolean exists = cartRepository.existsByUserIdAndBookId(userId, bookId); // Repository 메소드 가정
        // if (cartRepository.findByUserId(userId).stream().anyMatch(cart -> cart.getBookId().equals(bookId))) {
        if (exists) {
            log.warn("Attempted to add duplicate book to cart. userId: {}, bookId: {}", userId, bookId);
            throw new IllegalStateException("이미 카트에 담긴 책입니다.");
        }

        // --- 조회된 영속 상태의 User, Book 엔티티로 Cart 생성 및 저장 ---
        Cart cart = new Cart(user, book);
        cartRepository.save(cart);
        log.info("Cart entry created successfully for userId: {}, bookId: {}", userId, bookId);
    }

    // 장바구니 아이템 삭제 메소드 수정
    @Transactional
    public void removeCartItem(Long userId, Long bookId) {
        log.info("Attempting to remove cart item for userId: {} and bookId: {}", userId, bookId);
        // --- ★★★★★ 문제를 일으키는 existsById 호출 제거 ★★★★★ ---
        // CartId cartId = new CartId(userId, bookId);
        // if (cartRepository.existsById(cartId)) { // <- 이 부분 제거!

        // --- Repository에 새로 추가한 사용자 정의 삭제 메소드만 호출 ---
        cartRepository.deleteByUserIdAndBookId(userId, bookId);
        log.info("Cart item removal query executed for userId: {} and bookId: {}", userId, bookId);

        // --- else 블록도 제거 ---
        // } else {
        //    log.warn("Cart item not found for removal with ID: {}", cartId);
        // }
    }

    public List<Cart> getCartItemsByUserId(Long userId) {
        log.info("Fetching cart items for userId: {}", userId);
        return cartRepository.findWithBookByUserId(userId);
    }

    @Transactional // 쓰기 작업이므로 readOnly=false 적용
    public void clearCart(Long userId) {
        log.info("Attempting to clear cart for userId: {}", userId);
        try {
            // Repository에 추가한 사용자 정의 삭제 메소드 호출
            cartRepository.deleteByUserId(userId);
            log.info("Cart cleared successfully for userId: {}", userId);
        } catch (Exception e) {
            // 데이터베이스 오류 등 예외 발생 시 로깅 (트랜잭션 롤백은 자동 처리될 수 있음)
            log.error("Error occurred while clearing cart for userId: {}", userId, e);
            // 필요시 여기서 예외를 다시 던지거나 다른 처리를 할 수 있습니다.
            // throw new RuntimeException("Failed to clear cart for user " + userId, e);
        }
    }
}