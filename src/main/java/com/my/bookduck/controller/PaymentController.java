package com.my.bookduck.controller; // ★★ 실제 패키지 경로 ★★

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CartService;       // CartService 주입 (success 처리 시 필요)
import com.my.bookduck.service.PaymentService;    // PaymentService 주입
import com.my.bookduck.service.UserBookService;    // UserBookService 주입
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;        // HttpStatus 임포트
import org.springframework.http.ResponseEntity;    // ResponseEntity 임포트
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*; // GetMapping 외에 ResponseBody 등 사용

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map; // Map 임포트

@Slf4j
@Controller
@RequestMapping("/payment") // 기본 경로 /payment
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;       // 결제 처리 로직 담당 서비스
    private final BookService bookService;             // 책 정보 조회 서비스
    private final CartService cartService;             // 장바구니 관련 서비스 (성공 시 비우기 등)
    private final UserBookService userBookService;       // 사용자 책 소유 여부 확인 서비스

    /**
     * 결제 폼 페이지를 보여주는 메소드.
     * 페이지 로딩 시 소유 여부를 확인하지 않습니다.
     */
    @GetMapping({"", "/"}) // /payment 또는 /payment/ 경로 처리
    public String showPaymentForm(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer price,
            @RequestParam(required = false) String isbn,
            Model model,
            @AuthenticationPrincipal BDUserDetails userDetails) { // 로그인 정보는 여전히 필요

        log.info("결제 폼 요청 수신 - Title: [{}], Price: [{}], ISBN: [{}]", title, price, isbn);

        // Model에 기본 데이터 설정
        int validPrice = (price != null && price > 0) ? price : 0;
        model.addAttribute("productName", title != null ? title : "상품명 없음");
        model.addAttribute("amount", validPrice);
        model.addAttribute("isbn", isbn); // JavaScript에서 사용할 ISBN 값

        // 로그인 상태에 따른 처리 (선택적)
        if (userDetails == null) {
            log.warn("결제 폼 접근 시도 (인증되지 않음).");
            model.addAttribute("errorMessage", "로그인이 필요한 서비스입니다.");
        }

        return "paymentForm"; // templates/paymentForm.html 반환
    }

    /**
     * 사용자가 특정 책을 소장하고 있는지 비동기적으로 확인하는 API 엔드포인트.
     * @param bookId 확인할 책 ID (ISBN 역할, Long 타입)
     * @param userDetails 현재 로그인한 사용자 정보
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "로그인이 필요합니다."));
        }
        Long userId = userDetails.getUser().getId();
        log.info("소유 여부 확인 요청 - userId: {}, bookId: {}", userId, bookId);

        // 2. bookId 유효성 확인 (Controller 레벨에서도 확인)
        if (bookId == null) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "도서 ID가 필요합니다."));
        }

        // 3. 서비스 호출하여 소유 여부 확인
        try {
            boolean isOwned = userBookService.doesUserOwnBook(userId, bookId);
            log.info("소유 여부 확인 결과 - userId: {}, bookId: {}, isOwned: {}", userId, bookId, isOwned);
            // 4. JSON 형태로 결과 반환
            Map<String, Boolean> response = Collections.singletonMap("isOwned", isOwned);
            return ResponseEntity.ok(response); // HTTP 200 OK 와 함께 {"isOwned": true/false} 반환
        } catch (Exception e) {
            log.error("소유 여부 확인 중 오류 발생 - userId: {}, bookId: {}, error: {}", userId, bookId, e.getMessage(), e);
            // 5. 서버 내부 오류 발생 시
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "확인 중 오류가 발생했습니다."));
        }
    }

    /**
     * Toss Payments 결제 성공 콜백 처리 메소드.
     * (내부 로직 변경 없음 - PaymentService 호출 전 책 조회 로직 유지)
     */
    @GetMapping("/success")
    public String paymentSuccessCallback(
            @RequestParam String paymentKey, @RequestParam String orderId, @RequestParam Long amount,
            @RequestParam String userId,     // String 파라미터
            @RequestParam String isbn,       // String 파라미터 (쉼표 구분 가능성 있음)
            @RequestParam String orderName, @RequestParam String customerName,
            Model model, @AuthenticationPrincipal BDUserDetails userDetails) {

        log.info("결제 성공 콜백 수신! orderId: {}, userId: {}, isbn: {}, amount: {}", orderId, userId, isbn, amount);

        // 0. 인증 및 사용자 ID 일치 확인
        if (userDetails == null) { /* ... 인증 실패 처리 ... */ model.addAttribute("errorMessage", "인증 정보 없음."); model.addAttribute("orderId", orderId); return "failForm"; }
        Long sessionUserId = userDetails.getUser().getId(); Long requestUserId;
        try { requestUserId = Long.parseLong(userId); } catch (NumberFormatException e) { /* ... ID 형식 오류 처리 ... */ model.addAttribute("errorMessage", "잘못된 사용자 ID."); model.addAttribute("orderId", orderId); return "failForm"; }
        if (!sessionUserId.equals(requestUserId)) { /* ... 사용자 불일치 처리 ... */ model.addAttribute("errorMessage", "사용자 불일치."); model.addAttribute("orderId", orderId); return "failForm"; }
        User currentUser = userDetails.getUser();

        // 1. ISBN 문자열 -> Book ID(Long) 리스트 변환
        List<String> bookIdStringList = Collections.emptyList();
        if (isbn != null && !isbn.isEmpty() && !isbn.equals("ISBN 정보 없음")) { bookIdStringList = Arrays.asList(isbn.split(",")); }

        // 2. Book 엔티티 목록 조회 (PaymentService 호출 전 필요)
        List<Book> orderedBooks = new ArrayList<>();
        if (!bookIdStringList.isEmpty()) {
            for (String bookIdStr : bookIdStringList) {
                try { Long bookId = Long.parseLong(bookIdStr.trim()); Book book = bookService.getBookById(bookId); if (book != null) orderedBooks.add(book); else log.warn("Book not found: {}", bookId); }
                catch (Exception e) { log.error("Error fetching book: {}", bookIdStr, e); }
            }
        }
        if (orderedBooks.isEmpty() && !bookIdStringList.isEmpty()) { /* ... 책 조회 실패 처리 ... */ model.addAttribute("errorMessage", "주문 상품 정보 처리 불가."); model.addAttribute("orderId", orderId); return "failForm"; }

        // 3. PaymentService로 결제 처리 위임
        try {
            paymentService.processSuccessfulPayment(paymentKey, orderId, amount, currentUser, orderedBooks, orderName, customerName);
            log.info("Payment processing completed for orderId: {}", orderId);

            // 4. 장바구니 비우기 (결제 성공 후, PaymentService 호출 성공 후)
            //    이 로직은 여기에 두거나, PaymentService.processSuccessfulPayment 내부에 포함시킬 수 있음.
            //    주문 ID 패턴 등으로 장바구니 결제인지 확인 후 처리하는 것이 좋음.
            //    if (isCartCheckout(orderId)) { // isCartCheckout 구현 필요
            //       log.info("장바구니 비우기 시작 (가정) - userId: {}", sessionUserId);
            //       try { cartService.clearCart(sessionUserId); log.info("장바구니 비우기 성공 (가정)"); }
            //       catch (Exception e) { log.error("장바구니 비우기 실패", e); }
            //    }


            // 5. 성공 페이지 데이터 설정
            model.addAttribute("orderId", orderId); model.addAttribute("orderName", orderName); model.addAttribute("totalAmount", amount);
            model.addAttribute("customerName", customerName); model.addAttribute("paymentKey", paymentKey); model.addAttribute("orderedItems", orderedBooks);
            return "successForm"; // templates/successForm.html
        } catch (Exception e) {
            log.error("Payment processing failed for orderId: {}", orderId, e);
            model.addAttribute("errorMessage", "결제 처리 오류: " + e.getMessage()); model.addAttribute("orderId", orderId);
            return "failForm"; // templates/failForm.html
        }
    }

    /**
     * Toss Payments 결제 실패 콜백 처리 메소드.
     */
    @GetMapping("/fail")
    public String paymentFailCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam String orderId,
            Model model) {
        log.warn("결제 실패 콜백 수신 - Code: {}, Message: [{}], OrderId: {}", code, message, orderId);
        model.addAttribute("errorCode", code);
        model.addAttribute("errorMessage", message != null ? message : "결제에 실패했습니다.");
        model.addAttribute("orderId", orderId);
        return "failForm"; // templates/failForm.html
    }

    // ✨ isCartCheckout 메소드 제거됨 ✨
}