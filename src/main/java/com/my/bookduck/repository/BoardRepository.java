package com.my.bookduck.repository;

import com.my.bookduck.domain.board.Board;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BoardRepository extends JpaRepository<Board, Long> {

    Optional<Board> findByGroupIdAndBookId(Long groupId, Long bookId);

    @Query("SELECT b.book.id FROM Board b WHERE b.group.id = :groupId")
    Set<Long> findBookIdsByGroupId(@Param("groupId") Long groupId);

    // Book ID 리스트 + 책 제목 검색 + 정렬
    @Query("SELECT DISTINCT b FROM Board b " +
            "JOIN FETCH b.book bk " +
            "JOIN FETCH b.group g " +
            "WHERE bk.id IN :bookIds " +
            // ★★★ 그룹 이름 검색 조건 제거, 책 제목만 검색 ★★★
            "AND (LOWER(bk.title) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Board> findByBookIdInAndQuery(@Param("bookIds") List<Long> bookIds, @Param("query") String query, Sort sort);

    // 책 제목 검색 + 정렬 (Book ID 필터링 없음)
    @Query("SELECT b FROM Board b JOIN FETCH b.book bk JOIN FETCH b.group g " +
            // ★★★ 그룹 이름 검색 조건 제거, 책 제목만 검색 ★★★
            "WHERE LOWER(bk.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Board> findAllPublicBoardsWithDetailsAndQuery(@Param("query") String query, Sort sort);

    // 검색어가 없을 때 (이전과 동일)
    @Query("SELECT b FROM Board b JOIN FETCH b.book JOIN FETCH b.group")
    List<Board> findAllPublicBoardsWithDetails(Sort sort);
}