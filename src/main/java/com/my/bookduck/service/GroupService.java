package com.my.bookduck.service;

// ... (다른 import)

import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.GroupRepository;
import com.my.bookduck.repository.GroupUserRepository;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupUserRepository groupUserRepository; // 여전히 필요할 수 있음 (조회 등)
    private final UserRepository userRepository;

    @Transactional
    public Group createGroupWithMembers(String groupName, List<Long> memberIds) {
        log.info("Creating group '{}' with member IDs: {}", groupName, memberIds);

        // 1. 그룹 생성 (아직 저장member 전)
        Group newGroup = Group.builder()
                .name(groupName)
                .build();
        // 리스트가 필드에서 초기화되지 않았다면 여기서 초기화
        // if (newGroup.getUsers() == null) { newGroup.setUsers(new ArrayList<>()); } // Group에 setter 필요
        groupRepository.save(newGroup);
        // 2. 멤버 ID 리스트를 순회하며 GroupUser 생성 및 Group에 추가
        for (Long memberId : memberIds) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> {
                        log.error("User not found with ID: {}", memberId);
                        return new IllegalArgumentException("Invalid user ID: " + memberId);
                    });

            GroupUser groupUser = GroupUser.builder()
                    // .group(newGroup) // 아래 addGroupUser에서 설정됨
                    .user(member)
                    .build();
            groupUser.setGroupId(newGroup.getId());
            groupUser.setUserId(member.getId());
            // *** 중요 변경: GroupUser를 Group의 리스트에 추가 (양방향 설정 포함) ***
            newGroup.addGroupUser(groupUser); // 편의 메소드 사용
            // 또는 직접 추가:
            // newGroup.getUsers().add(groupUser);
            // groupUser.setGroup(newGroup); // 양방향 설정

            // *** groupUserRepository.save(groupUser); 호출 제거 ***
            log.info("Prepared user {} for group {}", memberId, groupName);
        }

        // 3. Group 저장 -> CascadeType.ALL에 의해 users 리스트의 GroupUser들도 함께 저장됨
        for(GroupUser groupUser : newGroup.getUsers()) {
            log.info("group user {} for group {}", groupUser.getUser().getId(), groupName);
        }
        Group savedGroup = groupRepository.save(newGroup);
        log.info("Successfully saved group '{}' with ID {} and {} members.", savedGroup.getName(), savedGroup.getId(), savedGroup.getUsers().size());

        return savedGroup;
    }
}