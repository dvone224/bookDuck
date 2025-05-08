package com.my.bookduck.controller;

import com.my.bookduck.controller.request.AddCommentRequest;
import com.my.bookduck.controller.response.loginUserInfo;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User; // User 엔티티 import
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.BookCommentService;
import jakarta.servlet.http.HttpSession; // HttpSession import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.security.core.annotation.AuthenticationPrincipal; // 더 이상 사용 안 함
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/comments")
@RequiredArgsConstructor
public class BookCommentController {

    private final BookCommentService bookCommentService;
    private final BookService bookService;

    /**
     * 쪽지 작성 폼을 보여주는 메소드 (GET 요청 처리)
     * 이 부분은 세션 사용과 직접적인 관련이 없으므로 이전과 동일하게 유지 가능
     */
    @GetMapping("/new")
    public String showNewCommentForm(
            @RequestParam("bookId") Long bookId,
            @RequestParam("cfi") String cfi,
            @RequestParam("selectedText") String selectedText,
            @RequestParam("chapterHref") String chapterHref,
            Model model,
            HttpSession session) {

        log.info("Showing new comment form for bookId: {}, cfi: {}, chapterHref: {}", bookId, cfi, chapterHref);

        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");

        if (loginUser == null) {
            log.warn("User not logged in. Redirecting to login page before showing comment form.");
            // RedirectAttributes는 GET 메소드에서 직접 사용하기 어려움.
            // 바로 리다이렉트 하거나, 모델에 메시지를 담아 로그인 유도.
            // 여기서는 간단히 로그인 폼으로 리다이렉트 (POST와 동일하게 처리)
            return "redirect:/login-form?message=login_required"; // 로그인 폼 경로 및 메시지 전달
        }

        Book book = bookService.findById(bookId);
        if (book == null) {
            log.warn("Book not found for id: {}", bookId);
            model.addAttribute("errorMessage", "해당 책을 찾을 수 없습니다. ID: " + bookId);
            return "common/errorPage";
        }

        model.addAttribute("bookId", bookId);
        model.addAttribute("bookTitle", book.getTitle());
        model.addAttribute("cfi", cfi);
        model.addAttribute("selectedText", selectedText);
        model.addAttribute("chapterHref", chapterHref);

        AddCommentRequest commentFormDto = new AddCommentRequest();
        commentFormDto.setBookId(bookId);
        commentFormDto.setCfi(cfi);
        commentFormDto.setChapterHref(chapterHref);
        model.addAttribute("commentForm", commentFormDto);

        log.info("Returning comment form view. Model attributes set.");
        return "book/commentForm";
    }

    /**
     * 작성된 쪽지를 저장하는 메소드 (POST 요청 처리)
     * HttpSession에서 "loginUser" 속성을 가져와 사용합니다.
     */
    @PostMapping
    public String saveComment(
            @ModelAttribute("commentForm") AddCommentRequest commentForm,
            HttpSession session, // HttpSession 주입
            RedirectAttributes redirectAttributes,
            Model model) {

        // 세션에서 "loginUser" 속성 가져오기
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        log.info("Attempting to save comment. Form data: {}, Logged in user from session: {}",
                commentForm, (loginUser != null ? loginUser.getId() : "null"));

        if (loginUser == null) {
            log.warn("User not logged in (from session). Redirecting to login page.");
            redirectAttributes.addFlashAttribute("message", "쪽지를 작성하려면 로그인이 필요합니다.");
            return "redirect:/login-form"; // 로그인 폼 경로 확인
        }

        try {
            // 서비스 메소드 호출 시 세션에서 가져온 loginUser 객체 전달
            bookCommentService.saveBookCommentFrom(commentForm, loginUser);
            log.info("Comment saved successfully for bookId: {} by user: {}", commentForm.getBookId(), loginUser.getId());
            redirectAttributes.addFlashAttribute("message", "쪽지가 성공적으로 저장되었습니다.");
            return "redirect:book/viewer/" + commentForm.getBookId();

        } catch (IllegalArgumentException e) {
            log.warn("Failed to save comment due to IllegalArgumentException: {}", e.getMessage());
            Book book = bookService.findById(commentForm.getBookId());
            if (book != null) {
                model.addAttribute("bookTitle", book.getTitle());
            } else {
                model.addAttribute("bookTitle", "알 수 없는 책");
                log.error("Book not found for id: {} when handling saveComment error.", commentForm.getBookId());
            }
            model.addAttribute("bookId", commentForm.getBookId());
            model.addAttribute("cfi", commentForm.getCfi());
            model.addAttribute("chapterHref", commentForm.getChapterHref());
            model.addAttribute("commentForm", commentForm);
            model.addAttribute("errorMessage", "쪽지 저장 실패: " + e.getMessage());
            log.info("Returning to comment form due to error. Model attributes set for error display.");
            return "book/commentForm";

        } catch (Exception e) {
            log.error("An unexpected error occurred while saving comment for bookId: {} by user: {}",
                    (commentForm != null ? commentForm.getBookId() : "N/A"),
                    (loginUser != null ? loginUser.getId() : "N/A"), e);
            redirectAttributes.addFlashAttribute("errorMessage", "쪽지 저장 중 시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/viewer/" + (commentForm != null ? commentForm.getBookId() : "") + "?error=system_error";
        }
    }
}