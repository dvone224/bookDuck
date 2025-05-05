package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.Cart;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.BookRepository;
import com.my.bookduck.repository.CartRepository;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // List 임포트

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
        log.info("장바구니 추가 요청 - userId: {}, bookId: {}", userId, bookId);

        // 사용자 및 도서 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다. ID: " + userId));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalStateException("도서 정보를 찾을 수 없습니다. ID: " + bookId));

        // 중복 체크
        boolean exists = cartRepository.existsByUserIdAndBookId(userId, bookId);
        if (exists) {
            log.warn("이미 장바구니에 있는 상품 추가 시도 - userId: {}, bookId: {}", userId, bookId);
            throw new IllegalStateException("이미 장바구니에 담긴 책입니다.");
        }

        // 장바구니 생성 및 저장
        Cart cart = new Cart(user, book);
        cartRepository.save(cart);
        log.info("장바구니 추가 완료 - userId: {}, bookId: {}", userId, bookId);
    }

    // 단일 아이템 삭제
    @Transactional
    public void removeCartItem(Long userId, Long bookId) {
        log.info("장바구니 단일 항목 삭제 요청 - userId: {}, bookId: {}", userId, bookId);
        cartRepository.deleteByUserIdAndBookId(userId, bookId);
        log.info("장바구니 단일 항목 삭제 쿼리 실행 완료 - userId: {}, bookId: {}", userId, bookId);
    }

    // 사용자의 장바구니 목록 조회 (책 정보 포함)
    public List<Cart> getCartItemsByUserId(Long userId) {
        log.info("장바구니 목록 조회 요청 - userId: {}", userId);
        return cartRepository.findWithBookByUserId(userId); // Fetch Join 사용 가정
    }

    // 사용자의 전체 장바구니 비우기
    @Transactional
    public void clearCart(Long userId) {
        log.info("장바구니 전체 비우기 요청 - userId: {}", userId);
        try {
            cartRepository.deleteByUserId(userId);
            log.info("장바구니 전체 비우기 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("장바구니 비우기 중 오류 발생 - userId: {}", userId, e);
            // 필요시 예외 처리
        }
    }

    // ★★★ 결제된 여러 상품 삭제 메소드 추가 ★★★
    /**
     * 특정 사용자의 장바구니에서 주어진 bookId 리스트에 해당하는 상품들을 삭제합니다.
     * @param userId 사용자 ID
     * @param bookIds 삭제할 책 ID 목록
     */
    @Transactional
    public void removeItemsFromCart(Long userId, List<Long> bookIds) {
        if (userId == null || bookIds == null || bookIds.isEmpty()) {
            log.warn("잘못된 파라미터로 장바구니 항목 삭제 시도. userId={}, bookIds={}", userId, bookIds);
            return; // 또는 예외 발생
        }
        log.info("장바구니에서 여러 항목 삭제 요청 - userId: {}, bookIds: {}", userId, bookIds);
        try {
            // CartRepository에 해당 메소드가 구현되어 있다고 가정
            int deletedCount = cartRepository.deleteByUserIdAndBookIdIn(userId, bookIds);
            log.info("장바구니에서 {}개 항목 삭제 완료 (요청: {}개) - userId: {}", deletedCount, bookIds.size(), userId);
        } catch (Exception e) {
            log.error("장바구니에서 여러 항목 삭제 중 오류 발생 - userId: {}, bookIds: {}", userId, bookIds, e);
            // 필요시 예외 처리
        }
    }
}