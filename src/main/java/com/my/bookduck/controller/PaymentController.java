package com.my.bookduck.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    @GetMapping({"","/"})
    public String payment(Model model) {
        return "paymentForm";
    }

    @GetMapping("/success")
    public String sucess(Model model) {
        return "successForm";
    }

    @GetMapping("/fail")
    public String fail(Model model) {
        return "failForm";
    }

}
