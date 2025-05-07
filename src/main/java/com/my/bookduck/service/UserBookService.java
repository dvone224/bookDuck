package com.my.bookduck.service; // 실제 패키지 경로

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.repository.UserBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBookService { // 서비스 이름은 실제 프로젝트에 맞게

    private final UserBookRepository userBookRepository;

    /**
     * 사용자가 특정 책을 소장하고 있는지 확인합니다.
     * @param userId 사용자 ID
     * @param bookId 책 ID (ISBN 역할)
     * @return 소장하고 있으면 true, 아니면 false
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public boolean doesUserOwnBook(Long userId, Long bookId) {
        // UserBookRepository에 existsByUserIdAndBookId 메소드가 있다고 가정
        return userBookRepository.existsByUserIdAndBookId(userId, bookId);
    }

    /**
     * 특정 사용자의 서재에 등록된 모든 책 목록(Book 엔티티 리스트)을 조회합니다.
     * @param userId 조회할 사용자의 ID
     * @return 사용자의 서재에 있는 책 목록 (List<Book>), 없을 경우 빈 리스트 반환
     */
    @Transactional(readOnly = true)
    public List<Book> findMyBooks(Long userId) {
        log.info("사용자 ID {} 의 서재 책 목록 조회 시작", userId);
        // UserBookRepository에 추가한 findBooksByUserId 메소드 호출
        List<Book> myBooks = userBookRepository.findBooksByUserId(userId);
        log.info("사용자 ID {} 의 서재에서 {}권의 책을 찾았습니다.", userId, myBooks.size());
        return myBooks;
    }

}