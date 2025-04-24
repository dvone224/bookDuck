package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.controller.response.loginUserInfo;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserService userService;

    @GetMapping({"","/","/home"})
    public String home(){return "home";}

    @GetMapping("/logininfo")
    public String logininfo(@AuthenticationPrincipal BDUserDetails user,HttpSession session){
        log.info("user: {}", user);
        //User u = user.getUser();
        User u = userService.getUserByLoginId(user.getUser().getId());
        log.info("u: {}", u);
        loginUserInfo loginuser = new loginUserInfo(u);
        session.setAttribute("loginuser", loginuser);
        log.info("sesseion: {}", session.getAttribute("loginuser"));
        return "home";
        //return "member/del";
    }

    @GetMapping("/login-form")
    public String loginForm(){return "member/login";}

    @GetMapping("/sign-up")
    public String signUp(){return "member/joinForm";}

    @GetMapping("/addNickname")
    public String addNickname(@AuthenticationPrincipal BDUserDetails user){
        log.info("user: {}", user);
        return "member/joinSocialForm";
    }

    @GetMapping("/userupdate")
    public String userUpdate(){return "member/fix";}

    @GetMapping("/error")
    public String handleError(@RequestParam(value="message",required=false) String message, Model model){
        log.error(message);
        if(message != null){
            model.addAttribute("errorMessage", message);
        } else{
            model.addAttribute("errorMessage", "알 수 없는 오류가 발생했습니다.");
        }
        return "error";
    }

    @GetMapping("/deleteuser")
    public String deleteUser(){
        return "member/del";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) throws Exception{

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null){
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        return "redirect:/";
    }

}
