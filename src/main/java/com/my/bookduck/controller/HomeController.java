package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.controller.response.loginUserInfo;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.GroupService;
import com.my.bookduck.service.UserBookService;
import com.my.bookduck.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserService userService;
    private final GroupService groupService;
    private final UserBookService userBookService;

    @GetMapping({"","/","/home"})
    public String home(){return "home";}

    @GetMapping("/logininfo") // 또는 /mypage 등
    public String myPage(Model model, @AuthenticationPrincipal BDUserDetails userDetails, HttpSession session) {
        if (userDetails == null) {
            // 로그인되지 않은 사용자 처리 (로그인 페이지로 리다이렉트 등)
            return "redirect:/login-form";
        }
        //return "home";
        //return "member/del";
//        if(u.getRole() == User.Role.ROLE_ADMIN){
//            return "admin/adminMain";
//        }else{
//            return "member/mypage";
//        }


        Long userId = userDetails.getUser().getId();
        // 세션에서 loginUserInfo 가져오기 (HomeController의 /logininfo 에서 저장한 것을 사용)
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null || !loginUser.getId().equals(userId)) {
            // 세션 정보가 없거나 불일치하면 DB에서 다시 조회하거나 /logininfo 로 보내서 세션 설정 유도
            User user = userService.getUserById(userId); // UserService에 ID로 User 찾는 메소드 필요
            loginUser = new loginUserInfo(user);
            session.setAttribute("loginuser", loginUser); // 세션 갱신
        }

        model.addAttribute("loginuser", loginUser);

        // 1. 내 그룹 목록 가져오기 (최대 3개)
        List<GroupListViewDto> allMyGroups = groupService.findMyGroupsForView(userId);
        List<GroupListViewDto> groupPreview = allMyGroups.stream().limit(3).collect(Collectors.toList());
        model.addAttribute("myGroupsPreview", groupPreview);
        model.addAttribute("hasMoreGroups", allMyGroups.size() > 3); // 더보기 버튼 표시 여부

        // 2. 내 책 목록 가져오기 (최대 3개) - 이 기능을 위한 서비스 메소드 필요
        // 예시: UserBookService 또는 BookService에 findMyBooks(userId, limit) 같은 메소드 추가 가정
        // 반환 타입은 List<Book> 또는 List<MyBookPreviewDto> 등 (cover, title 포함)
        List<Book> allMyBooks = userBookService.findMyBooks(userId); // findMyBooks 구현 필요
        List<Book> bookPreview = allMyBooks.stream().limit(3).collect(Collectors.toList());
        model.addAttribute("myBooksPreview", bookPreview);
        model.addAttribute("hasMoreBooks", allMyBooks.size() > 3); // 더보기 버튼 표시 여부 (나중에 사용)


        return "member/mypage";
    }

    @GetMapping("/login-form")
    public String loginForm(@AuthenticationPrincipal Model model,@CookieValue(value = "saveId", required = false) Cookie cookie){
        model.addAttribute("loginFail", false);

        String userId = "";
        boolean remember = false;

        if(cookie != null) {
            log.info("cookieName: {}", cookie.getName());
            log.info("cookie: {}", cookie.getValue());
            userId = cookie.getValue();
            remember = true;
        }

        model.addAttribute("userId", userId);
        model.addAttribute("remember", remember);

        return "member/login";
    }

    @GetMapping("/loginerror")
    public String loginError(@AuthenticationPrincipal Model model,HttpServletRequest request, HttpServletResponse response){
        model.addAttribute("loginFail", true);
        return "member/login";
    }

    @GetMapping("/sign-up")
    public String signUp(){return "member/joinForm";}

    @GetMapping("/addNickname")
    public String addNickname(@AuthenticationPrincipal BDUserDetails user){
        log.info("user: {}", user);
        return "member/joinSocialForm";
    }

    @GetMapping("/userupdate")
    public String userUpdate(){return "member/fix";}

    @GetMapping("/userinfo")
    public String userInfo(){return "member/mypage";}

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

    @GetMapping("/findinfo")
    public String findUser(){
        return "member/findinfo";
    }

    @GetMapping("/findinfosuccess")
    public String findUserSuccess(){
        return "member/findinfoSuccess";
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

    /**
     * 현재 로그인한 사용자가 속한 전체 그룹 목록을 JSON으로 반환하는 API
     * @param userDetails 현재 인증된 사용자 정보
     * @return 그룹 목록 DTO 리스트 또는 오류 응답
     */
    @GetMapping("/api/my-groups") // AJAX 호출 경로
    @ResponseBody // JSON 응답을 위해 필요 (@RestController 사용 시 생략 가능)
    public ResponseEntity<?> getMyAllGroups(@AuthenticationPrincipal BDUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("API 요청: 사용자가 인증되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        Long userId = userDetails.getUser().getId();
        log.info("API 요청: 사용자 {}의 전체 그룹 목록 조회", userId);
        try {
            List<GroupListViewDto> myGroups = groupService.findMyGroupsForView(userId);
            log.info("API 응답: 사용자 {}에게 {}개의 그룹 목록 반환", userId, myGroups.size());
            return ResponseEntity.ok(myGroups);
        } catch (Exception e) {
            log.error("API 오류: 사용자 {}의 그룹 목록 조회 중 오류 발생", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "그룹 목록 조회 중 오류 발생"));
        }
    }

    /**
     * 현재 로그인한 사용자의 전체 책 목록(Book 엔티티 정보)을 JSON으로 반환하는 API
     * @param userDetails 현재 인증된 사용자 정보
     * @return 책 목록(ID, 제목, 표지) 리스트 또는 오류 응답
     */
    @GetMapping("/api/my-books") // AJAX 호출 경로
    @ResponseBody // JSON 응답을 위해 필요 (@RestController 사용 시 생략 가능)
    public ResponseEntity<?> getMyAllBooks(@AuthenticationPrincipal BDUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("API 요청: 사용자가 인증되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        Long userId = userDetails.getUser().getId();
        log.info("API 요청: 사용자 {}의 전체 책 목록 조회", userId);
        try {
            List<Book> myBooks = userBookService.findMyBooks(userId);
            // 필요한 정보만 추출하여 반환 (순환 참조 방지 및 데이터 최소화)
            List<Map<String, Object>> bookData = myBooks.stream()
                    .map(book -> Map.<String, Object>of(
                            "id", book.getId(),
                            "title", book.getTitle() != null ? book.getTitle() : "제목 없음",
                            "cover", book.getCover() != null ? book.getCover() : "/img/default_book_cover.png",
                            "writer", book.getWriter() != null ? book.getWriter() : "저자 정보 없음"
                    ))
                    .collect(Collectors.toList());
            log.info("API 응답: 사용자 {}에게 {}권의 책 목록 반환", userId, bookData.size());
            return ResponseEntity.ok(bookData);
        } catch (Exception e) {
            log.error("API 오류: 사용자 {}의 책 목록 조회 중 오류 발생", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "책 목록 조회 중 오류 발생"));
        }
    }

}
