package com.my.bookduck.repository;

import com.my.bookduck.domain.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    // Fetch Join을 사용하여 GroupUser와 User 정보까지 함께 조회
    // g.books는 필요시 추가로 JOIN FETCH 가능 (gb, b)
    @Query("SELECT DISTINCT g FROM Group g " +
            "LEFT JOIN FETCH g.users gu " +
            "LEFT JOIN FETCH gu.user u " +
            // "LEFT JOIN FETCH g.books gb " +  // 이 줄 제거 또는 주석 처리
            // "LEFT JOIN FETCH gb.book b " +   // 이 줄 제거 또는 주석 처리
            "WHERE g.id IN (SELECT gu_inner.group.id FROM GroupUser gu_inner WHERE gu_inner.user.id = :userId)")
    List<Group> findGroupsWithUsersByUserId(@Param("userId") Long userId); // 메소드 이름 확인

    // 그룹 이름 중복 체크용 (기존 메소드가 있다면 유지)
    boolean existsByName(String name);

    @Query("SELECT DISTINCT g FROM Group g " +
            "LEFT JOIN FETCH g.users gu " +
            "LEFT JOIN FETCH gu.user u " +
            "LEFT JOIN FETCH g.books gb " + // books 컬렉션 fetch
            "LEFT JOIN FETCH gb.book b " +  // books 내부의 book fetch
            "WHERE g.id IN (SELECT gu_inner.group.id FROM GroupUser gu_inner WHERE gu_inner.user.id = :userId)")
    List<Group> findGroupsWithUsersAndBooksByUserId(@Param("userId") Long userId);
}