package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.UserBook;
import com.my.bookduck.domain.user.UserBookId; // ★★★ UserBookId 임포트 (복합 키 클래스) ★★★
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository; // @Repository 어노테이션 추가 권장

import java.util.List; // 필요시 List 임포트

@Repository // Spring Bean으로 등록하기 위한 어노테이션 추가
// ★★★ JpaRepository의 두 번째 제네릭 타입을 엔티티의 ID 타입인 UserBookId로 변경 ★★★
public interface UserBookRepository extends JpaRepository<UserBook, UserBookId> {

    /**
     * 특정 사용자의 서재에 특정 책이 등록되어 있는지 확인합니다.
     * BookService의 isBookInUserLibrary 메소드에서 사용됩니다.
     * Spring Data JPA의 메소드 이름 규칙 ('existsBy' + 필드명 + 'And' + 필드명)에 따라
     * WHERE userId = ? AND bookId = ? 조건을 만족하는 데이터가 있는지 확인하는 쿼리가 자동 생성됩니다.
     *
     * @param userId 확인할 사용자 ID (UserBookId의 userId 필드에 해당)
     * @param bookId 확인할 책 ID (UserBookId의 bookId 필드에 해당)
     * @return 해당 UserBook 데이터가 존재하면 true, 그렇지 않으면 false
     */
    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    @Query("SELECT CASE WHEN COUNT(ub) > 0 THEN TRUE ELSE FALSE END " +
            "FROM UserBook ub JOIN ub.book b " +
            "WHERE ub.user.id = :userId AND b.id = :isbn")
    boolean existsByUserIdAndBookIsbn(@Param("userId") Long userId, @Param("isbn") Long isbn);


    // 필요에 따라 다른 UserBook 관련 쿼리 메소드를 추가할 수 있습니다.
    // 예: 특정 사용자의 모든 UserBook 엔티티 조회 (연관된 User, Book 정보 포함)
    // List<UserBook> findByUserId(Long userId);

    // 예: 특정 책을 등록한 모든 UserBook 엔티티 조회
    // List<UserBook> findByBookId(Long bookId);

    /**
     * 특정 사용자의 서재에 등록된 모든 책(Book 엔티티) 목록을 조회합니다.
     * UserBook을 거쳐 연관된 Book 객체만 선택하여 반환합니다.
     * @param userId 사용자 ID
     * @return 해당 사용자가 등록한 Book 엔티티 리스트 (없으면 빈 리스트 반환)
     */
    @Query("SELECT ub.book FROM UserBook ub WHERE ub.user.id = :userId")
    List<Book> findBooksByUserId(@Param("userId") Long userId);
}