package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddCommentRequest;
import com.my.bookduck.controller.response.BookCommentHighlightDto;
import com.my.bookduck.controller.response.loginUserInfo;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.BookComment;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.BookCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Slf4j 로거 추가
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j // Slf4j 로거 추가
@Service
@RequiredArgsConstructor // final 필드에 대한 생성자 주입
@Transactional(readOnly = true)
public class BookCommentService {

    private final BookService bookService;
    private final UserService userService;
    private final BookCommentRepository bookCommentRepository;

    /**
     * AddCommentRequest DTO로부터 BookComment를 저장하는 메소드
     */
    @Transactional // 데이터 변경 작업이므로 클래스 레벨의 readOnly=true 오버라이드
    public void saveBookCommentFrom(AddCommentRequest formDto, loginUserInfo loginUserFromController) {
        log.debug("Service: Attempting to save comment. Form DTO: {}, Login User from Controller ID: {}",
                formDto, (loginUserFromController != null ? loginUserFromController.getId() : "null"));

        if (formDto == null || loginUserFromController == null) {
            log.error("Service: Form DTO or Login User Info from Controller is null.");
            throw new IllegalArgumentException("필수 정보(요청 데이터 또는 사용자 정보)가 누락되었습니다.");
        }

        // 1. DTO에서 Book 엔티티 조회
        Book book = bookService.findById(formDto.getBookId());
        if (book == null) {
            log.warn("Service: Book not found for id: {}", formDto.getBookId());
            throw new IllegalArgumentException("유효하지 않은 책 ID입니다: " + formDto.getBookId());
        }
        log.debug("Service: Book found: {}", book.getId());

        // 2. 로그인 정보에서 User 엔티티 조회
        // 컨트롤러에서 @AuthenticationPrincipal User loginUser로 받은 객체는
        // 이미 DB에서 조회된 User 엔티티일 수도 있고, UserDetails를 구현한 커스텀 객체일 수도 있습니다.
        // 만약 loginUserFromController가 이미 완전한 User 엔티티라면, 다시 조회할 필요가 없을 수 있습니다.
        // 하지만, detached 상태이거나 프록시 객체일 가능성을 고려하여 ID로 다시 조회하는 것이 안전할 수 있습니다.
        // UserService.getUserById()는 해당 ID의 사용자가 없으면 예외를 던지거나 null을 반환할 수 있습니다.
        // 여기서는 null을 반환한다고 가정하고 null 체크를 합니다.
        User userEntity = userService.getUserById(loginUserFromController.getId());
        if (userEntity == null) {
            log.error("Service: User not found in DB for ID: {}", loginUserFromController.getId());
            // 이 경우는 loginUserFromController (인증된 사용자)가 DB에 없는 이상한 상황이므로,
            // 시스템 레벨의 오류로 간주하거나, 더 구체적인 예외를 던질 수 있습니다.
            throw new IllegalArgumentException("인증된 사용자 정보를 데이터베이스에서 찾을 수 없습니다. 사용자 ID: " + loginUserFromController.getId());
        }
        log.debug("Service: User entity fetched from DB: {}", userEntity.getId());


        // 3. BookComment 엔티티 생성 및 값 설정
        BookComment newComment = BookComment.builder()
                .comment(formDto.getComment())
                .chapterHref(formDto.getChapterHref())
                .locationCfi(formDto.getCfi())
                .noteColor(formDto.getNoteColor()) // DTO에서 noteColor 가져와 설정
                // .fontFamily(formDto.getFontFamily()) // (선택) 글꼴 정보도 저장
                .createdAt(LocalDateTime.now())
                .book(book)
                .user(userEntity)
                .build();

        bookCommentRepository.save(newComment);
        log.info("Service: BookComment saved successfully. Book ID: {}, User ID: {}, Comment ID: {}",
                book.getId(), userEntity.getId(), newComment.getId());
    }

    public List<BookCommentHighlightDto> findHighlightsByBookAndChapter(Long bookId, String chapterHref) {
        // Repository를 사용하여 bookId와 chapterHref로 BookComment 엔티티 목록 조회
        List<BookComment> comments = bookCommentRepository.findByBookIdAndChapterHref(bookId, chapterHref);
        // 엔티티 목록을 DTO 목록으로 변환하여 반환
        return comments.stream()
                .map(BookCommentHighlightDto::fromEntity)
                .collect(Collectors.toList());
    }
}