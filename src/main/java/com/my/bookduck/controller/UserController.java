package com.my.bookduck.controller;


import com.my.bookduck.controller.request.AddUserRequest;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping({"","/"})
    public String addUser(@RequestParam AddUserRequest user, Model model) {
        log.info("request User: {}", user);
        try{
            userService.createUser(user);
        }catch(Exception e){
            log.error("errMsg: {}", e.getMessage());
            model.addAttribute("errMsg", e.getMessage());
            return "member/joinForm";
        }

        return "redirect:/home";

    }


}
