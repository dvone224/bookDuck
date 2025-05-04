package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.Cart;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CartService;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserService userService;
    private final BookService bookService;

    @PostMapping({"", "/"})
    public ResponseEntity<?> addCartAjax(Long userId, Long bookId, @AuthenticationPrincipal BDUserDetails userDetails) {
        log.info("장바구니 추가 요청 - Received userId: {}, bookId: {}", userId, bookId);
        if (userDetails == null) {
            log.warn("인증되지 않은 사용자의 장바구니 추가 시도.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        Long sessionUserId = userDetails.getUser().getId();
        if (userId == null || !sessionUserId.equals(userId)) {
            log.warn("요청된 userId({})와 세션 userId({}) 불일치.", userId, sessionUserId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "잘못된 사용자 접근입니다."));
        }
        if (bookId == null) {
            log.warn("bookId가 null입니다.");
            return ResponseEntity.badRequest().body(Map.of("message", "도서 ID가 필요합니다."));
        }
        User user = userDetails.getUser();
        Book book = bookService.getBookById(bookId);
        if (book == null) {
            log.warn("Book not found for ID: {}", bookId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "도서를 찾을 수 없습니다."));
        }
        try {
            log.info("1111111111111111111111111");
            log.info("userId: {}", userId);
            log.info("bookId: {}", bookId);
            log.info("user: {}", user);
            log.info("book: {}", book);
            cartService.createCart(userId, bookId); // User 객체 대신 userId 전달
            log.info("장바구니 추가 성공 - userId: {}, bookId: {}", userId, bookId);
            return ResponseEntity.ok(Map.of("message", "장바구니에 추가되었습니다."));
        } catch (Exception e) {
            log.error("장바구니 추가 중 오류 발생 - userId: {}, bookId: {}, error: {}", userId, bookId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "장바구니 추가 중 오류가 발생했습니다."));
        }
    }

    @GetMapping("/cartList")
    public String showCartList(@AuthenticationPrincipal BDUserDetails userDetails, Model model) {
        if (userDetails == null) {
            log.warn("인증되지 않은 사용자가 장바구니 목록 접근 시도. 로그인 페이지로 리다이렉트.");
            return "redirect:/login-form";
        }
        Long userId = userDetails.getUser().getId();
        String userNickName = userDetails.getUser().getNickName();
        log.info("장바구니 목록 페이지 요청 - userId: {}", userId);
        model.addAttribute("currentUserId", String.valueOf(userId));
        model.addAttribute("currentUserNickName", userNickName);
        try {
            List<Cart> cartItems = cartService.getCartItemsByUserId(userId);
            long totalAmount = 0;
            if (cartItems != null) {
                for (Cart item : cartItems) {
                    if (item.getBook() != null) {
                        totalAmount += item.getBook().getPrice();
                    } else {
                        log.warn("장바구니 아이템에 연결된 책 정보가 없습니다.");
                    }
                }
            } else {
                cartItems = Collections.emptyList();
            }
            log.info("장바구니 정보 조회 완료 - userId: {}, itemCount: {}, totalAmount: {}", userId, cartItems.size(), totalAmount);
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("totalAmount", totalAmount);
        } catch (Exception e) {
            log.error("장바구니 정보 조회 중 오류 발생 - userId: {}", userId, e);
            model.addAttribute("errorMessage", "장바구니 정보를 가져오는 중 오류가 발생했습니다.");
            model.addAttribute("cartItems", Collections.emptyList());
            model.addAttribute("totalAmount", 0L);
        }
        return "cart/cartList";
    }

    @DeleteMapping("/remove/{userId}/{bookId}")
    public ResponseEntity<?> removeCartItem(
            @PathVariable Long userId,
            @PathVariable Long bookId,
            @AuthenticationPrincipal BDUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("인증되지 않은 사용자의 장바구니 삭제 시도.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        Long sessionUserId = userDetails.getUser().getId();
        if (!sessionUserId.equals(userId)) {
            log.warn("권한 없는 장바구니 삭제 시도. SessionUserId={}, RequestedUserId={}", sessionUserId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "자신의 장바구니 상품만 삭제할 수 있습니다."));
        }
        log.info("장바구니 아이템 삭제 요청 - userId: {}, bookId: {}", userId, bookId);
        try {
            cartService.removeCartItem(userId, bookId);
            log.info("장바구니 아이템 삭제 완료 - userId: {}, bookId: {}", userId, bookId);
            return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
        } catch (Exception e) {
            log.error("장바구니 아이템 삭제 중 오류 발생 - userId: {}, bookId: {}", userId, bookId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "삭제 중 오류가 발생했습니다."));
        }
    }
}