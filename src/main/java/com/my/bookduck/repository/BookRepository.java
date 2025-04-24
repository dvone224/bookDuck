// BookRepository.java 인터페이스에 추가
package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    // 다른 메소드들...

    /**
     * 검색어(제목 또는 저자)와 카테고리 ID 목록으로 책을 검색합니다. (ManyToMany 고려)
     * @param query 검색어 (null 이거나 비어있으면 무시)
     * @param categoryIds 필터링할 카테고리 ID 목록 (null 이거나 비어있으면 카테고리 필터링 안 함)
     * @return 검색 조건에 맞는 책 목록
     */
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN b.categories bc " + // BookCategory 조인 (bc)
            "WHERE (:query IS NULL OR :query = '' OR LOWER(b.title) LIKE LOWER(concat('%', :query, '%')) OR LOWER(b.writer) LIKE LOWER(concat('%', :query, '%'))) " + // 검색어 조건
            "AND (:categoryIds IS NULL OR bc.categoryId IN :categoryIds)") // 카테고리 조건 (조인된 BookCategory의 categoryId 사용)
    List<Book> findBooksByQueryAndCategoryIdsIn(
            @Param("query") String query,
            @Param("categoryIds") Set<Long> categoryIds
    );

    // 만약 카테고리 필터링 없이 검색어만으로 검색하는 경우 (카테고리 선택 안했을 때)
    @Query("SELECT b FROM Book b WHERE :query IS NULL OR :query = '' OR LOWER(b.title) LIKE LOWER(concat('%', :query, '%')) OR LOWER(b.writer) LIKE LOWER(concat('%', :query, '%'))")
    List<Book> findBooksByQuery(@Param("query") String query);

    // 만약 검색어 없이 카테고리만으로 필터링하는 경우
    @Query("SELECT DISTINCT b FROM Book b JOIN b.categories bc WHERE bc.categoryId IN :categoryIds")
    List<Book> findBooksByCategoryIdsIn(@Param("categoryIds") Set<Long> categoryIds);

    // 모든 책 조회 (findAll() 사용 가능)
}