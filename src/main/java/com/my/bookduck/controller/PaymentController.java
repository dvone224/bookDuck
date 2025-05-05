package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CartService;
import com.my.bookduck.service.PaymentService;
import com.my.bookduck.service.UserBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BookService bookService;
    private final CartService cartService; // CartService 주입
    private final UserBookService userBookService;

    /**
     * (단일 상품) 결제 폼 페이지를 보여주는 메소드. bookId로 책 정보 조회.
     */
    @GetMapping({"", "/"})
    public String showPaymentForm(
            @RequestParam Long bookId, // 단일 상품 ID만 받음
            Model model,
            @AuthenticationPrincipal BDUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        log.info("결제 폼 요청 수신 (단일 상품) - Book ID: [{}]", bookId);

        if (userDetails == null) { /* ... 로그인 확인 및 리다이렉트 ... */ redirectAttributes.addFlashAttribute("errorMessage", "로그인이 필요합니다."); return "redirect:/login-form"; }
        if (bookId == null) { /* ... bookId 없음 오류 처리 및 리다이렉트 ... */ redirectAttributes.addFlashAttribute("errorMessage", "상품 정보가 없습니다."); return "redirect:/book/books"; }

        try {
            Book book = bookService.getBookById(bookId);
            if (book == null) { /* ... 책 없음 오류 처리 및 리다이렉트 ... */ redirectAttributes.addFlashAttribute("errorMessage", "상품 정보를 찾을 수 없습니다."); return "redirect:/book/books"; }

            // 모델에 단일 상품 정보 설정
            model.addAttribute("productName", book.getTitle());
            model.addAttribute("amount", book.getPrice());
            model.addAttribute("isbn", String.valueOf(book.getId())); // 단일 ID 전달

            return "paymentForm";
        } catch (Exception e) { /* ... 예외 처리 및 리다이렉트 ... */ redirectAttributes.addFlashAttribute("errorMessage", "오류 발생"); return "redirect:/book/books"; }
    }

    /**
     * 사용자가 특정 책을 소장하고 있는지 비동기적으로 확인하는 API 엔드포인트.
     * @param bookId 확인할 책 ID (ISBN 역할, Long 타입)
     * @param userDetails 현재 로그인한 사용자 정보 (Spring Security)
     * @return JSON 형태의 응답 ({"isOwned": true/false}) 또는 오류 응답
     */
    @GetMapping("/check-ownership") // GET /payment/check-ownership 경로
    @ResponseBody // 결과를 HTTP 응답 본문으로 직접 반환 (JSON 변환)
    public ResponseEntity<?> checkOwnership(
            @RequestParam Long bookId, // 요청 파라미터 'bookId' 를 Long 타입으로 받음
            @AuthenticationPrincipal BDUserDetails userDetails) {

        // 1. 로그인 확인
        if (userDetails == null) {
            log.warn("소유 여부 확인 시도 중 인증되지 않은 사용자 접근.");
            // 오류 응답: 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "로그인이 필요합니다."));
            // JavaScript에서 오류 처리를 위해 {"error": "메시지"} 형태로 반환
        }
        Long userId = userDetails.getUser().getId();
        log.info("소유 여부 확인 요청 - userId: {}, bookId: {}", userId, bookId);

        // 2. bookId 유효성 확인
        if (bookId == null) {
            log.warn("소유 여부 확인 요청 시 bookId 누락 - userId: {}", userId);
            // 오류 응답: 400 Bad Request
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "도서 ID가 필요합니다."));
        }

        // 3. 서비스 호출하여 소유 여부 확인
        try {
            // UserBookService의 doesUserOwnBook 메소드를 호출합니다.
            // 이 메소드는 boolean 값을 반환한다고 가정합니다.
            boolean isOwned = userBookService.doesUserOwnBook(userId, bookId);
            log.info("소유 여부 확인 결과 - userId: {}, bookId: {}, isOwned: {}", userId, bookId, isOwned);

            // 4. 성공 응답: JSON 형태로 결과 반환
            // {"isOwned": true} 또는 {"isOwned": false} 형태
            Map<String, Boolean> response = Collections.singletonMap("isOwned", isOwned);
            return ResponseEntity.ok(response); // HTTP 200 OK

        } catch (Exception e) {
            // 5. 서비스 호출 중 예외 발생 시 처리
            log.error("소유 여부 확인 중 오류 발생 - userId: {}, bookId: {}, error: {}", userId, bookId, e.getMessage(), e);
            // 오류 응답: 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "확인 중 서버 오류가 발생했습니다."));
        }
    }
    /**
     * Toss Payments 결제 성공 콜백 처리 메소드 (수정됨).
     * 단일 상품 및 장바구니(여러 상품) 결제 모두 처리.
     */
    @GetMapping("/success")
    public String paymentSuccessCallback(
            @RequestParam String paymentKey, @RequestParam String orderId, @RequestParam Long amount,
            @RequestParam String userId,     // String 파라미터
            @RequestParam String isbn,       // String 파라미터 (단일 ID 또는 쉼표 구분 ID 목록)
            @RequestParam String orderName, @RequestParam String customerName,
            Model model, @AuthenticationPrincipal BDUserDetails userDetails) {

        log.info("결제 성공 콜백 수신! orderId: {}, userId: {}, isbn(bookId): [{}], amount: {}", orderId, userId, isbn, amount);

        // 0. 인증 및 사용자 ID 일치 확인 (이전 코드 유지)
        if (userDetails == null || !userDetails.getUser().getId().equals(Long.parseLong(userId))) {
            model.addAttribute("errorMessage", "사용자 인증 오류 또는 불일치.");
            model.addAttribute("orderId", orderId); return "failForm";
        }
        User currentUser = userDetails.getUser();
        Long sessionUserId = currentUser.getId();

        // 1. 쉼표로 구분된 ISBN(bookId) 문자열 파싱 (여러 ID 처리)
        List<Long> bookIds = new ArrayList<>();
        List<String> notParsedIsbns = new ArrayList<>(); // 파싱 실패한 문자열 기록 (선택적)
        if (isbn != null && !isbn.isEmpty() && !isbn.equals("ISBN 정보 없음")) {
            for (String idStr : isbn.split(",")) {
                try {
                    if (!idStr.trim().isEmpty()) {
                        bookIds.add(Long.parseLong(idStr.trim()));
                    }
                } catch (NumberFormatException e) {
                    log.error("Book ID 파싱 오류: '{}'", idStr, e);
                    notParsedIsbns.add(idStr); // 파싱 실패 기록
                }
            }
        }
        if (bookIds.isEmpty()) { /* ... 유효한 bookId 없음 오류 처리 ... */ model.addAttribute("errorMessage", "주문 상품 정보 오류."); model.addAttribute("orderId", orderId); return "failForm"; }
        if (!notParsedIsbns.isEmpty()) { log.warn("일부 Book ID 파싱 실패: {}", notParsedIsbns); /* 필요시 추가 처리 */ }
        log.info("처리할 Book IDs: {}", bookIds);

        // 2. Book 엔티티 목록 조회
        List<Book> orderedBooks = new ArrayList<>();
        List<Long> notFoundBookIds = new ArrayList<>();
        for (Long currentBookId : bookIds) {
            try {
                Book book = bookService.getBookById(currentBookId);
                if (book != null) orderedBooks.add(book);
                else notFoundBookIds.add(currentBookId);
            } catch (Exception e) { log.error("Book ID {} 조회 오류", currentBookId, e); notFoundBookIds.add(currentBookId); }
        }
        if (orderedBooks.isEmpty()) { /* ... 조회된 책 없음 오류 처리 ... */ model.addAttribute("errorMessage", "주문 상품 조회 불가."); model.addAttribute("orderId", orderId); return "failForm"; }
        if (!notFoundBookIds.isEmpty()) { log.warn("일부 Book ID 조회 실패: {}", notFoundBookIds); }

        // 3. PaymentService로 결제 처리 위임
        try {
            paymentService.processSuccessfulPayment(paymentKey, orderId, amount, currentUser, orderedBooks, orderName, customerName);
            log.info("Payment processing completed for orderId: {}", orderId);

            // 4. 장바구니 비우기 (결제된 상품들만)
            // orderId 패턴 등으로 장바구니 결제인지 더 정확히 판단하는 것이 좋음
            boolean isCartOrder = orderId != null && orderId.contains("cart"); // 예시 판단 로직
            if (isCartOrder && !bookIds.isEmpty()) {
                log.info("장바구니 결제로 판단, 카트에서 상품 제거 시작 - userId: {}, bookIds: {}", sessionUserId, bookIds);
                try {
                    cartService.removeItemsFromCart(sessionUserId, bookIds); // CartService에 구현 필요
                    log.info("장바구니에서 결제된 상품 제거 완료.");
                } catch (Exception e) {
                    log.error("장바구니 상품 제거 중 오류 발생 - userId: {}, bookIds: {}", sessionUserId, bookIds, e);
                    // 비우기 실패가 전체 결제 성공에 영향을 주지 않도록 처리
                }
            }

            // 5. 성공 페이지 데이터 설정
            model.addAttribute("orderId", orderId);
            model.addAttribute("orderName", orderName); // 장바구니면 "상품명 외 N건" 등
            model.addAttribute("totalAmount", amount);
            model.addAttribute("customerName", customerName);
            model.addAttribute("paymentKey", paymentKey);
            model.addAttribute("orderedItems", orderedBooks); // 실제 처리된 책 목록 전달
            return "successForm";
        } catch (Exception e) {
            log.error("Payment processing failed for orderId: {}", orderId, e);
            // TODO: 결제 승인 후 처리 실패 시 보상 트랜잭션(결제 취소) 로직 필요
            model.addAttribute("errorMessage", "결제 처리 중 시스템 오류 발생: " + e.getMessage());
            model.addAttribute("orderId", orderId);
            return "failForm";
        }
    }

    // ... (fail 메소드는 변경 없음) ...
    @GetMapping("/fail")
    public String paymentFailCallback(/*...*/) { /* 이전 코드 유지 */ return "failForm"; }
}