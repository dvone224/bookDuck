package com.my.bookduck.repository;

import com.my.bookduck.domain.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * 그룹 이름으로 그룹 존재 여부를 확인합니다.
     * @param name 확인할 그룹 이름
     * @return 해당 이름의 그룹이 존재하면 true, 그렇지 않으면 false
     */
    boolean existsByName(String name); // 이 메소드 추가

}