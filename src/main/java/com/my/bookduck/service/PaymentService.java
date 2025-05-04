package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.store.Purchase;
import com.my.bookduck.domain.store.PurchaseItems;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.domain.user.UserBook;
import com.my.bookduck.repository.PurchaseRepository;
import com.my.bookduck.repository.UserBookRepository;
import com.my.bookduck.repository.UserRepository;
import com.my.bookduck.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserBookRepository userBookRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final PurchaseRepository purchaseRepository;
    private final CartService cartService;

    @Transactional
    public void processSuccessfulPayment(String paymentKey, String orderId, Long amount,
                                         User user, List<Book> orderedBooks,
                                         String orderName, String customerName) throws Exception {
        log.info("결제 성공 처리 시작 - orderId: {}", orderId);

        // 1. Toss Payments 결제 검증 (임시로 통과)
        log.warn("결제 승인/검증 로직이 구현되지 않았습니다! (임시 통과) - orderId: {}", orderId);

        // 2. Purchase 중복 체크 및 저장
        log.info("Purchase 정보 저장 시작 - orderId: {}", orderId);
        try {
            // purchase_id 중복 체크
            if (purchaseRepository.existsById(orderId)) {
                log.error("이미 존재하는 purchase_id: {}", orderId);
                throw new IllegalStateException("이미 존재하는 주문번호입니다: " + orderId);
            }

            // 단일 Purchase 객체 생성
            Purchase purchase = new Purchase(orderId, String.valueOf(user.getId()), new ArrayList<>());
            for (Book book : orderedBooks) {
                PurchaseItems item = PurchaseItems.createPurchaseItem(String.valueOf(book.getId()), orderId, purchase);
                purchase.addPurchaseItem(item);
            }
            purchaseRepository.save(purchase);
            log.info("Purchase 정보 저장 성공 - orderId: {}, itemCount: {}", orderId, orderedBooks.size());
        } catch (Exception e) {
            log.error("Purchase 정보 저장 실패 - orderId: {}, error: {}", orderId, e.getMessage());
            throw new RuntimeException("주문 정보(Purchase) 저장 실패", e);
        }

        // 3. UserBook 저장 (사용자 서재에 추가)
        log.info("UserBook 저장 시작 - userId: {}, bookCount: {}", user.getId(), orderedBooks.size());
        for (Book book : orderedBooks) {
            try {
                boolean alreadyOwned = userBookRepository.existsByUserIdAndBookId(user.getId(), book.getId());
                if (!alreadyOwned) {
                    UserBook userBook = new UserBook(user, book);
                    userBookRepository.save(userBook);
                    log.debug("UserBook 저장 성공 - userId: {}, bookId: {}", user.getId(), book.getId());
                } else {
                    log.info("UserBook 이미 존재 - userId: {}, bookId: {}. 저장 건너뜀.", user.getId(), book.getId());
                }
            } catch (Exception e) {
                log.error("UserBook 저장 중 오류 발생 - userId: {}, bookId: {}, error: {}", user.getId(), book.getId(), e.getMessage());
            }
        }
        log.info("UserBook 저장 시도 완료 - userId: {}", user.getId());

        // 4. 장바구니 비우기
        log.info("장바구니 비우기 시작 - userId: {}", user.getId());
        try {
            cartService.clearCart(user.getId());
            log.info("장바구니 비우기 성공 - userId: {}", user.getId());
        } catch (Exception e) {
            log.error("장바구니 비우기 실패 - userId: {}, error: {}", user.getId(), e.getMessage());
        }

        log.info("결제 성공 처리 완료 - orderId: {}", orderId);
    }

    @Transactional
    public void savePurchase(String orderId, Long userId, String isbn, Long amount) {
        try {
            log.info("Attempting to save UserBook. UserID: {}, ISBN: {}", userId, isbn);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            Long bookIdFromIsbn;
            try {
                bookIdFromIsbn = Long.parseLong(isbn);
            } catch (NumberFormatException e) {
                log.error("Invalid ISBN format for Book ID lookup: {}", isbn);
                throw new IllegalArgumentException("Invalid ISBN format provided: " + isbn, e);
            }
            Book book = bookRepository.findById(bookIdFromIsbn)
                    .orElseThrow(() -> new IllegalArgumentException("Book not found with ID (ISBN): " + bookIdFromIsbn));
            boolean alreadyOwned = userBookRepository.existsByUserIdAndBookId(user.getId(), book.getId());
            if (!alreadyOwned) {
                UserBook userBook = new UserBook(user, book);
                userBookRepository.save(userBook);
                log.info("UserBook saved successfully for UserID: {} and BookID (ISBN): {}", user.getId(), book.getId());
            } else {
                log.info("UserBook already exists for UserID: {} and BookID (ISBN): {}. Skipping save.", user.getId(), book.getId());
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to save UserBook due to invalid data. UserID: {}, ISBN: {}. Error: {}", userId, isbn, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while saving UserBook. UserID: {}, ISBN: {}. Error: {}", userId, isbn, e.getMessage(), e);
            throw new RuntimeException("UserBook saving failed", e);
        }
    }
}