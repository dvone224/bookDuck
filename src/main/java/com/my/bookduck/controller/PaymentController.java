package com.my.bookduck.controller;

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


    @GetMapping({"","/"})
    public String payment(
            @RequestParam(required = false) String title, // 제목 받기
            @RequestParam(required = false) Integer price, // 가격 받기
            // isbn 파라미터 제거
            Model model) {

        log.info("Payment form requested. Title: {}, Price: {}", title, price); // 로그에서 ISBN 제거

        // 받은 정보만 모델에 추가
        int validPrice = (price != null && price > 0) ? price : 0;

        model.addAttribute("productName", title != null ? title : "상품명 없음");
        model.addAttribute("amount", validPrice);
        // model.addAttribute("isbn", isbn); 제거

        // ISBN 기반 DB 조회 로직 제거

        return "paymentForm";
    }

    @GetMapping("/success")
    public String success(Model model) { // success 오타 수정됨
        return "successForm";
    }

    @GetMapping("/fail")
    public String fail(Model model) {
        return "failForm";
    }
}