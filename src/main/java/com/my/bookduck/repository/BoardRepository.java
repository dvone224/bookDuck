package com.my.bookduck.repository;

import com.my.bookduck.domain.board.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

    // 그룹 ID와 책 ID로 Board 찾기 (존재 여부 및 삭제 시 사용)
    Optional<Board> findByGroupIdAndBookId(Long groupId, Long bookId);

    // 그룹 ID와 책 ID로 Board 삭제 (더 효율적일 수 있음)
    void deleteByGroupIdAndBookId(Long groupId, Long bookId);

    // 그룹 ID에 해당하는 모든 Board의 Book ID 조회 (초기 상태 전달용)
    @Query("SELECT b.book.id FROM Board b WHERE b.group.id = :groupId")
    Set<Long> findBookIdsByGroupId(@Param("groupId") Long groupId);

    // 그룹 ID와 책 ID로 존재 여부 확인 (더 가벼움)
    boolean existsByGroupIdAndBookId(Long groupId, Long bookId);
}