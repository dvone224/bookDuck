package com.my.bookduck.repository;

import com.my.bookduck.domain.group.GroupBook;
import com.my.bookduck.domain.group.GroupBookId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupBookRepository  extends JpaRepository<GroupBook, GroupBookId> {
    // 그룹 ID와 책 ID로 특정 GroupBook 엔티티를 찾는 메소드 (삭제 시 사용)
    Optional<GroupBook> findByGroupIdAndBookId(Long groupId, Long bookId);
}
