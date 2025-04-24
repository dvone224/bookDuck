package com.my.bookduck.controller;


import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.controller.request.AddUserRequest;
import com.my.bookduck.controller.request.SocialJoinUpdateRequest;
import com.my.bookduck.controller.request.UpdateUserRequest;
import com.my.bookduck.controller.response.UserSearchResultResponse;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping({"","/"})
    public String addUser(final @ModelAttribute AddUserRequest user, Model model) {
        log.info("request User: {}", user);
        try{
            userService.createUser(user);
        }catch(Exception e){
            log.error("errMsg: {}", e.getMessage());
            model.addAttribute("errMsg", e.getMessage());
            return "member/joinForm";
        }

        return "member/joinSuccess";
    }


    @PostMapping("/socialaddinfo")
    public String socialAddInfo(final @ModelAttribute SocialJoinUpdateRequest info,Model model, @AuthenticationPrincipal BDUserDetails userDetails) throws InterruptedException {
        log.info("social update info: {}", info);

        long id = userDetails.getUser().getId();
        String result;

        try{
             result = userService.socialJoinUpdate(id,info);
        }catch(Exception e){
            log.error("errMsg: {}", e.getMessage());
            model.addAttribute("errMsg", e.getMessage());
            return "member/login";
        }


        if(!result.equals("success")){
            return "member/login";
        }else{
            return "redirect:/logininfo";

        }


    }


    @GetMapping("/del/{id}")
    public String delUser(@PathVariable Long id) {
        System.out.println("delUser: " + id);
        return "redirect:/logout";
    }

    @PostMapping("/update")
    public String updateUser(final @ModelAttribute UpdateUserRequest user, Model model, @AuthenticationPrincipal BDUserDetails userDetails){
        log.info("update User: {}", user);

        long id = userDetails.getUser().getId();
        String result;

        try{
            result = userService.userInfoUpdate(id,user);
        }catch(Exception e){
            log.error("errMsg: {}", e.getMessage());
            model.addAttribute("errMsg", e.getMessage());
            return "member/login";
        }

        if(!result.equals("success")){
            return "member/login";
        }else{
            return "redirect:/logininfo";

        }
    }


    // --- 사용자 검색 API 엔드포인트 추가 ---
    /**
     * 닉네임으로 사용자를 검색하는 API (JavaScript에서 호출됨).
     * 요청 파라미터 이름은 'name'이지만 내부적으로 닉네임 검색에 사용됨.
     * @param name 검색어 (닉네임으로 사용됨)
     * @param limit 최대 결과 수
     * @return 검색된 사용자 정보 DTO 리스트 (JSON)
     */
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<UserSearchResultResponse>> searchUsers( // 메소드 이름 일반화 유지 (선택 사항)
                                                                  @RequestParam String name, // *** 파라미터 이름 'name' 유지 ***
                                                                  @RequestParam(defaultValue = "8") int limit) {

        log.info("Received search request with term '{}' (used as nickname) limit {}", name, limit); // *** 로그 메시지 변경 ***
        // *** 호출하는 서비스 메소드 변경: searchUsersByNickname 사용 ***
        List<UserSearchResultResponse> users = userService.searchUsersByNickname(name, limit);
        log.info("Returning {} search results based on nickname", users.size()); // *** 로그 메시지 변경 ***
        return ResponseEntity.ok(users);
    }





}
