package com.my.bookduck.repository;

import com.my.bookduck.domain.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestGroupRepository extends JpaRepository<Group, Long> {
}
