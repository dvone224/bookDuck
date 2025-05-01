package com.my.bookduck.repository;

import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.group.GroupUserId;
import io.micrometer.observation.ObservationFilter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupUserRepository extends JpaRepository<GroupUser, GroupUserId> {


}