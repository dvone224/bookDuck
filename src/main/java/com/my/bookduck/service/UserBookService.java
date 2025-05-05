package com.my.bookduck.service; // 실제 패키지 경로

import com.my.bookduck.repository.UserBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}