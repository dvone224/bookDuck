package com.my.bookduck.controller;

import com.my.bookduck.service.PaymentService; // PaymentService import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService; // PaymentService 주입

    @GetMapping({"","/"})
    public String payment(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer price,
            @RequestParam(required = false) String isbn,
            Model model) {

        log.info("Payment form requested. Title: {}, Price: {}, ISBN: {}", title, price, isbn);

        int validPrice = (price != null && price > 0) ? price : 0;

        model.addAttribute("productName", title != null ? title : "상품명 없음");
        model.addAttribute("amount", validPrice);
        model.addAttribute("isbn", isbn);

        return "paymentForm";
    }

    @GetMapping("/success")
    public String success(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount,
            @RequestParam String userId,
            @RequestParam(required = false) String isbn,
            // ★ 추가된 파라미터 받기 ★
            @RequestParam String orderName,
            @RequestParam String customerName,
            Model model) {

        log.info("Payment success! paymentKey: {}, orderId: {}, amount: {}, userId: {}, isbn: {}, orderName: {}, customerName: {}",
                paymentKey, orderId, amount, userId, isbn, orderName, customerName); // 로그 업데이트

        // ISBN Null/유효성 체크 (필요시)
        if (isbn == null || isbn.trim().isEmpty() || isbn.equals("ISBN 정보 없음")) {
            log.warn("ISBN is missing or invalid in the success callback for orderId: {}", orderId);
            // ISBN 없이 저장은 가능하지만, 상품 특정 어려움. 여기서는 일단 진행.
            // 실제 서비스에서는 ISBN이 필수일 수 있음.
        }

        try {
            // 서비스 호출 시 필요하다면 orderName, customerName도 전달 가능
            paymentService.savePurchase(orderId, userId, isbn, amount /*, orderName, customerName */);

            log.info("Purchase details saved successfully for orderId: {}", orderId);

            // === ★ Model에 상품명과 구매자 이름 추가 ===
            model.addAttribute("orderId", orderId);
            model.addAttribute("productName", orderName); // 상품명 추가
            model.addAttribute("customerName", customerName); // 구매자 이름 추가
            model.addAttribute("amount", amount); // 결제 금액도 추가하면 좋음
            // =====================================

            return "successForm";

        } catch (Exception e) {
            log.error("Failed to save purchase details after successful payment. orderId: {}. Error: {}",
                    orderId, e.getMessage(), e); // 상세 로그
            model.addAttribute("errorMessage", "결제는 완료되었으나 주문 정보를 저장하는 중 오류가 발생했습니다. 관리자에게 문의하세요. (주문번호: " + orderId + ")");
            // 실패 시에도 필요한 정보 전달 시도
            model.addAttribute("orderId", orderId);
            model.addAttribute("productName", orderName);
            model.addAttribute("customerName", customerName);
            return "successForm"; // 오류 발생 시에도 일단 successForm으로 (메시지 표시)
        }
    }

    @GetMapping("/fail")
    public String fail(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       @RequestParam String orderId, // 실패 시에도 orderId는 넘어옴
                       Model model) {
        log.warn("Payment failed. Code: {}, Message: {}, OrderId: {}", code, message, orderId);
        model.addAttribute("errorCode", code);
        model.addAttribute("errorMessage", message);
        model.addAttribute("orderId", orderId);
        return "failForm";
    }
}