package com.my.bookduck.service; // 실제 패키지 경로

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.UserBook;
import com.my.bookduck.domain.user.UserBookId;
import com.my.bookduck.repository.UserBookRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBookService {

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

    /**
     * 특정 사용자의 특정 책에 대한 읽은 위치(mark/CFI)를 업데이트합니다.
     *
     * @param userId 사용자 ID
     * @param bookId 책 ID
     * @param cfi    업데이트할 CFI 문자열
     * @throws EntityNotFoundException 해당 userId와 bookId로 UserBook을 찾을 수 없는 경우
     * @throws IllegalArgumentException userId 또는 bookId가 null인 경우
     */
    @Transactional // 이 메소드 내의 데이터베이스 작업들은 하나의 트랜잭션으로 묶입니다.
    // 성공적으로 완료되면 커밋되고, 예외 발생 시 롤백됩니다.
    public void updateUserBookMark(Long userId, Long bookId, String cfi) {
        if (userId == null || bookId == null) {
            log.warn("userId 또는 bookId가 null입니다. 페이지 표시를 업데이트할 수 없습니다.");
            throw new IllegalArgumentException("사용자 ID와 책 ID는 반드시 제공되어야 합니다.");
        }
        // cfi가 null이거나 비어있는 경우에 대한 처리는 컨트롤러 단에서 이미 수행되었거나,
        // 여기서 추가적으로 정책에 따라 처리할 수 있습니다.
        // 예를 들어, 빈 cfi는 무시하거나, 특정 기본값으로 설정할 수 있습니다.
        // 여기서는 컨트롤러에서 이미 비어있지 않은 cfi를 전달한다고 가정합니다.

        UserBookId userBookId = new UserBookId(userId, bookId); // 복합키 객체 생성

        // 복합키를 사용하여 UserBook 엔티티를 데이터베이스에서 조회합니다.
        UserBook userBook = userBookRepository.findById(userBookId)
                .orElseThrow(() -> { // 엔티티가 존재하지 않으면 EntityNotFoundException 발생
                    String errorMessage = String.format("UserBook (userId: %d, bookId: %d)을(를) 찾을 수 없습니다.", userId, bookId);
                    log.warn(errorMessage);
                    return new EntityNotFoundException(errorMessage);
                });

        // UserBook 엔티티의 mark 필드를 업데이트합니다.
        // UserBook 엔티티 내에 updateMark(String newMark) 메소드가 정의되어 있다고 가정합니다.
        userBook.updateMark(cfi);
        userBookRepository.save(userBook);

        // @Transactional 어노테이션이 붙어있고, userBook 엔티티가 영속성 컨텍스트에 의해 관리되고 있으므로,
        // 메소드 종료 시 변경된 userBook 엔티티의 상태가 감지되어(dirty checking) 자동으로 UPDATE SQL이 실행됩니다.
        // 따라서 userBookRepository.save(userBook)을 명시적으로 호출할 필요는 대부분 없습니다.
        // (JPA 구현체 및 설정에 따라 다를 수 있으나, 일반적인 Hibernate 사용 시 이렇습니다.)

        log.info("UserBook (userId: {}, bookId: {})의 mark가 '{}'(으)로 성공적으로 업데이트되었습니다.", userId, bookId, cfi);
    }

}