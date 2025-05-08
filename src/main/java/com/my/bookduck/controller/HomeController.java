package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.controller.response.BoardListViewDto;
import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.controller.response.loginUserInfo;
import com.my.bookduck.domain.board.Board;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.BoardService;
import com.my.bookduck.service.GroupService;
import com.my.bookduck.service.UserBookService;
import com.my.bookduck.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
    private final BoardService boardService;

    // 홈으로 가는 기본 경로
    @GetMapping({"","/","/home"})
    public String home(
            Model model,
            @AuthenticationPrincipal BDUserDetails userDetails,
            HttpSession httpSession,
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "filterMyBooks", required = false, defaultValue = "false") boolean filterMyBooks,
            @RequestParam(name = "sortBy", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", required = false, defaultValue = "DESC") String sortDir
    ) {
        log.info("Home page request (via / or /home) - query: '{}', filterMyBooks: {}, sortBy: {}, sortDir: {}", query, filterMyBooks, sortBy, sortDir);

        Long currentUserId = null;
        loginUserInfo loginUser = null;

        if (userDetails != null) {
            currentUserId = userDetails.getUser().getId();
            loginUser = (loginUserInfo) httpSession.getAttribute("loginuser");

            if (loginUser == null || !loginUser.getId().equals(currentUserId)) {
                log.info("세션(home): loginuser 정보 없거나 불일치. DB 조회. userId: {}", currentUserId);
                User user = userService.getUserById(currentUserId);
                if (user != null) {
                    loginUser = new loginUserInfo(user);
                    httpSession.setAttribute("loginuser", loginUser);
                    // 이미지 세션도 여기서 동기화 가능 (선택적)
                    // httpSession.setAttribute("img", loginUser.getImg());
                    log.info("세션(home): loginuser 정보 저장 완료. Nickname: {}", loginUser.getNickName());

                    if (loginUser.getNickName() == null) {
                        log.info("세션(home): 닉네임 정보 없음. 추가 정보 입력 페이지로 이동.");
                        return "redirect:/addNickname";
                    }
                } else {
                    log.warn("세션(home): DB에서 userId {} 사용자를 찾을 수 없음. 로그아웃 처리.", currentUserId);
                    return "redirect:/logout";
                }
            }
            model.addAttribute("loginuser", loginUser);
        } else {
            model.addAttribute("loginuser", null);
        }

        List<Board> boards = boardService.findBoards(currentUserId, filterMyBooks, query, sortBy, sortDir);
        List<BoardListViewDto> boardDtos = boards.stream()
                .map(BoardListViewDto::new)
                .collect(Collectors.toList());
        model.addAttribute("boards", boardDtos);
        log.info("boards DTO count: {}", boardDtos.size());

        model.addAttribute("currentQuery", query);
        model.addAttribute("currentFilterMyBooks", filterMyBooks);
        model.addAttribute("currentSortBy", sortBy);
        model.addAttribute("currentSortDir", sortDir);

        return "home";
    }

    // `/logininfo` 경로는 이제 홈 화면으로 안내하는 역할을 합니다. (로그인 성공 후 도착지)
    @GetMapping("/logininfo")
    public String processLoginSuccessAndGoToHome(Model model, @AuthenticationPrincipal BDUserDetails userDetails, HttpSession httpSession,
                                                 @RequestParam(name = "query", required = false, defaultValue = "") String query,
                                                 @RequestParam(name = "filterMyBooks", required = false, defaultValue = "false") boolean filterMyBooks,
                                                 @RequestParam(name = "sortBy", required = false, defaultValue = "createdAt") String sortBy,
                                                 @RequestParam(name = "sortDir", required = false, defaultValue = "DESC") String sortDir) {

        log.info("Access to /logininfo, processing for home page.");

        Long currentUserId = null;
        loginUserInfo loginUser = null;

        if (userDetails == null) {
            log.warn("/logininfo 접근: userDetails가 null. 로그인 폼으로 리다이렉트.");
            return "redirect:/login-form"; // 인증되지 않은 접근 시 로그인 페이지로
        }

        currentUserId = userDetails.getUser().getId();
        loginUser = (loginUserInfo) httpSession.getAttribute("loginuser");

        // 세션 정보 확인 및 갱신 (DB에서 사용자 정보 다시 가져와 세션에 저장)
        // 이 로직은 UserSuccessHandler에서도 처리될 수 있지만, 여기서 한 번 더 확인하여 일관성을 보장합니다.
        User userFromDb = userService.getUserById(currentUserId);
        if (userFromDb != null) {
            loginUser = new loginUserInfo(userFromDb);
            httpSession.setAttribute("loginuser", loginUser);
            httpSession.setAttribute("img", loginUser.getImg()); // 이미지 세션도 설정
            log.info("세션(/logininfo): loginuser 정보 설정/갱신 완료. Nickname: {}", loginUser.getNickName());

            if (loginUser.getNickName() == null) {
                log.info("세션(/logininfo): 닉네임 정보 없음. 추가 정보 입력 페이지로 이동.");
                return "redirect:/addNickname";
            }
        } else {
            log.warn("세션(/logininfo): DB에서 userId {} 사용자를 찾을 수 없음. 로그아웃 처리.", currentUserId);
            return "redirect:/logout";
        }

        model.addAttribute("loginuser", loginUser); // 모델에 loginUser 추가

        // 홈 화면 데이터 로드 (boards 등)
        List<Board> boards = boardService.findBoards(currentUserId, filterMyBooks, query, sortBy, sortDir);
        List<BoardListViewDto> boardDtos = boards.stream().map(BoardListViewDto::new).collect(Collectors.toList());
        model.addAttribute("boards", boardDtos);
        log.info("/logininfo -> home: boards DTO count: {}", boardDtos.size());

        model.addAttribute("currentQuery", query);
        model.addAttribute("currentFilterMyBooks", filterMyBooks);
        model.addAttribute("currentSortBy", sortBy);
        model.addAttribute("currentSortDir", sortDir);

        return "home"; // 홈 뷰 반환
    }


    // `/userinfo` 경로가 이제 마이페이지 역할을 합니다.
    @GetMapping("/userinfo")
    public String myPage(Model model, @AuthenticationPrincipal BDUserDetails userDetails, HttpSession session) {
        log.info("Access to /userinfo (My Page)");
        if (userDetails == null) {
            log.info("마이페이지 접근(/userinfo): 로그인되지 않은 사용자. 로그인 폼으로 이동.");
            return "redirect:/login-form";
        }

        Long userId = userDetails.getUser().getId();
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");

        // 이미지 세션 값과 loginUser의 이미지 값 비교
        boolean imageMismatch = false;
        Object sessionImgAttr = session.getAttribute("img");
        String sessionImg = (sessionImgAttr instanceof String) ? (String) sessionImgAttr : null;

        if (loginUser != null) {
            if (loginUser.getImg() == null && sessionImg != null) {
                imageMismatch = true;
            } else if (loginUser.getImg() != null && !loginUser.getImg().equals(sessionImg)) {
                imageMismatch = true;
            }
        }

        // 세션 정보가 없거나, 사용자 ID 불일치, 또는 이미지 정보 불일치 시 DB에서 다시 조회
        if (loginUser == null || !loginUser.getId().equals(userId) || imageMismatch) {
            log.info("세션(myPage via /userinfo): loginuser 정보 없거나 불일치(ID 또는 이미지). DB 조회. userId: {}", userId);
            User user = userService.getUserById(userId);
            if (user != null) {
                loginUser = new loginUserInfo(user);
                session.setAttribute("loginuser", loginUser);
                session.setAttribute("img", loginUser.getImg()); // 세션의 img 속성도 갱신
                log.info("세션(myPage via /userinfo): loginuser 정보 저장 완료. Nickname: {}", loginUser.getNickName());
            } else {
                log.warn("세션(myPage via /userinfo): DB에서 userId {} 사용자를 찾을 수 없음. 로그아웃 처리.", userId);
                return "redirect:/logout";
            }
        }

        // 닉네임 정보가 없는 경우 (소셜 로그인 후 추가 정보 미입력 등)
        if (loginUser.getNickName() == null) {
            log.info("세션(myPage via /userinfo): 닉네임 정보 없음. 추가 정보 입력 페이지로 이동.");
            return "redirect:/addNickname";
        }

        model.addAttribute("loginuser", loginUser); // 모델에 loginUser 추가

        // 마이페이지 데이터 로드 (그룹, 책 목록)
        List<GroupListViewDto> allMyGroups = groupService.findMyGroupsForView(userId);
        model.addAttribute("myGroupsPreview", allMyGroups.stream().limit(3).collect(Collectors.toList()));
        model.addAttribute("hasMoreGroups", allMyGroups.size() > 3);

        List<Book> allMyBooks = userBookService.findMyBooks(userId);
        model.addAttribute("myBooksPreview", allMyBooks.stream().limit(3).collect(Collectors.toList()));
        model.addAttribute("hasMoreBooks", allMyBooks.size() > 3);

        // 역할에 따른 뷰 반환
        if (loginUser.getRole() != null && loginUser.getRole().equals("admin")) {
            return "admin/adminMain";
        } else {
            return "member/mypage"; // 마이페이지 뷰 반환
        }
    }

    @GetMapping("/login-form")
    public String loginForm(Model model, @CookieValue(value = "saveId", required = false) Cookie cookie){
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
    public String loginError(Model model, HttpServletRequest request, HttpServletResponse response){
        model.addAttribute("loginFail", true);
        return "member/login";
    }

    @GetMapping("/sign-up")
    public String signUp(){return "member/joinForm";}

    @GetMapping("/addNickname")
    public String addNickname(@AuthenticationPrincipal BDUserDetails userDetails, Model model){
        if (userDetails != null) {
            User user = userDetails.getUser();
            model.addAttribute("userEmail", user.getEmail());
            // 뷰에서 필요하다면 loginUserInfo 객체를 만들어 전달할 수도 있습니다.
            // loginUserInfo socialLoginUser = new loginUserInfo(user);
            // model.addAttribute("socialUser", socialLoginUser);
        }
        log.info("addNickname page access. UserDetails: {}", userDetails);
        return "member/joinSocialForm";
    }

    @GetMapping("/userupdate")
    public String userUpdate(){return "member/fix";}

    // `/error` 경로 핸들러
    @GetMapping("/error")
    public String handleError(@RequestParam(value="message",required=false) String message, Model model){
        log.error("Error page displayed. Message: {}", message);
        if(message != null){
            model.addAttribute("errorMessage", message);
        } else{
            model.addAttribute("errorMessage", "알 수 없는 오류가 발생했습니다.");
        }
        return "error"; // templates/error.html
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
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        log.info("User logged out successfully.");
        return "redirect:/";
    }

    @GetMapping("/api/my-groups")
    @ResponseBody
    public ResponseEntity<?> getMyAllGroups(@AuthenticationPrincipal BDUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("API 요청(my-groups): 사용자가 인증되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        Long userId = userDetails.getUser().getId();
        log.info("API 요청(my-groups): 사용자 {}의 전체 그룹 목록 조회", userId);
        try {
            List<GroupListViewDto> myGroups = groupService.findMyGroupsForView(userId);
            log.info("API 응답(my-groups): 사용자 {}에게 {}개의 그룹 목록 반환", userId, myGroups.size());
            return ResponseEntity.ok(myGroups);
        } catch (Exception e) {
            log.error("API 오류(my-groups): 사용자 {}의 그룹 목록 조회 중 오류 발생", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "그룹 목록 조회 중 오류 발생"));
        }
    }

    @GetMapping("/api/my-books")
    @ResponseBody
    public ResponseEntity<?> getMyAllBooks(@AuthenticationPrincipal BDUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("API 요청(my-books): 사용자가 인증되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        Long userId = userDetails.getUser().getId();
        log.info("API 요청(my-books): 사용자 {}의 전체 책 목록 조회", userId);
        try {
            List<Book> myBooks = userBookService.findMyBooks(userId);
            List<Map<String, Object>> bookData = myBooks.stream()
                    .map(book -> Map.<String, Object>of(
                            "id", book.getId(),
                            "title", book.getTitle() != null ? book.getTitle() : "제목 없음",
                            "cover", book.getCover() != null ? book.getCover() : "/img/default_book_cover.png",
                            "writer", book.getWriter() != null ? book.getWriter() : "저자 정보 없음"
                    ))
                    .collect(Collectors.toList());
            log.info("API 응답(my-books): 사용자 {}에게 {}권의 책 목록 반환", userId, bookData.size());
            return ResponseEntity.ok(bookData);
        } catch (Exception e) {
            log.error("API 오류(my-books): 사용자 {}의 책 목록 조회 중 오류 발생", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "책 목록 조회 중 오류 발생"));
        }
    }
}