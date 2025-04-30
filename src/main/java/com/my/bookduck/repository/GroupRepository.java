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
            "LEFT JOIN FETCH g.users gu " + // GroupUser를 fetch join (LEFT JOIN으로 그룹에 멤버가 없어도 그룹은 조회)
            "LEFT JOIN FETCH gu.user u " +  // User를 fetch join (LEFT JOIN으로 GroupUser는 있지만 User가 없는 비정상 케이스 대비)
            "WHERE g.id IN (SELECT gu_inner.group.id FROM GroupUser gu_inner WHERE gu_inner.user.id = :userId)") // 사용자가 속한 그룹 ID 목록을 서브쿼리로 조회
    List<Group> findGroupsWithUsersByUserId(@Param("userId") Long userId);

    // 그룹 이름 중복 체크용 (기존 메소드가 있다면 유지)
    boolean existsByName(String name);
}