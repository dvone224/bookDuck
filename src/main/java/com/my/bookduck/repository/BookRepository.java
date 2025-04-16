package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// Book 엔티티의 ID 타입이 Long이라고 가정합니다. 실제 타입에 맞게 수정하세요.
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * 제목 또는 저자에 주어진 검색어(대소문자 무시)를 포함하는 책 목록을 찾습니다.
     * @param titleQuery 제목에서 검색할 문자열
     * @param authorQuery 저자에서 검색할 문자열
     * @return 조건을 만족하는 책 엔티티 리스트
     */
    List<Book> findByTitleContainingIgnoreCaseOrWriterContainingIgnoreCase(String titleQuery, String authorQuery);

    // 필요에 따라 다른 검색 조건 메서드를 추가할 수 있습니다.
    // 예: 제목으로만 검색
    // List<Book> findByTitleContainingIgnoreCase(String titleQuery);
}
