package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/shop")
@RequiredArgsConstructor
@Slf4j
public class ShopController {

    @GetMapping("/shopList")
    public String shopList(Model model, @AuthenticationPrincipal BDUserDetails userDetails) {
        boolean isLoggedIn = userDetails != null;
        model.addAttribute("isLoggedIn", isLoggedIn);
        log.info("ShopList accessed, isLoggedIn set to: {}", isLoggedIn);
        return "shop/shopList";
    }

}
