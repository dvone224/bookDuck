package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set; // List 대신 Set 사용 가능

public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * 검색어(제목 또는 저자)와 카테고리 ID 목록(IN 절 사용)으로 책 목록을 조회합니다.
     * 카테고리 ID 목록이 비어있거나 null이면 카테고리 필터링을 적용하지 않습니다.
     * 검색어가 비어있거나 null이면 텍스트 검색을 적용하지 않습니다.
     *
     * @param query 검색어 (제목 또는 저자, null 또는 빈 문자열 가능)
     * @param categoryIds 필터링할 카테고리 ID 목록 (null 또는 비어있을 수 있음)
     * @return 조건을 만족하는 책 엔티티 리스트
     */
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN b.categories bc WHERE " +
            "(:categoryIds IS NULL OR bc.categoryId IN :categoryIds) AND " + // categoryIds가 null이면 true, 아니면 IN 절 적용
            "(:query IS NULL OR :query = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(b.writer) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Book> findBooksByQueryAndCategoryIdsIn(@Param("query") String query, @Param("categoryIds") Set<Long> categoryIds); // 파라미터 List 또는 Set 가능

    // 제목 또는 저자 검색 (카테고리 필터 없을 때 사용 가능)
    List<Book> findByTitleContainingIgnoreCaseOrWriterContainingIgnoreCase(String titleQuery, String writerQuery);

    // 특정 카테고리 ID 목록에 해당하는 책 ID만 조회 (Service에서 사용하기 위함)
    @Query("SELECT DISTINCT bc.bookId FROM BookCategory bc WHERE bc.categoryId IN :categoryIds")
    Set<Long> findBookIdsByCategoryIdsIn(@Param("categoryIds") Set<Long> categoryIds);
}