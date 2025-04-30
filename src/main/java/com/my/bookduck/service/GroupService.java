package com.my.bookduck.service;

import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.group.GroupBook;
import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.GroupRepository;
import com.my.bookduck.repository.GroupUserRepository;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupUserRepository groupUserRepository;
    private final UserRepository userRepository;
    private final BookService bookService;

    @Transactional
    public Group createGroupWithMembersAndBook(String groupName, List<Long> memberIds, Long bookId, Long creatorId) {
        log.info("Creating group '{}' with member IDs: {} and book ID: {}", groupName, memberIds, bookId);

        // 유효성 검사
        if (groupRepository.existsByName(groupName)) {
            throw new IllegalArgumentException("Group name already exists: " + groupName);
        }
        if (!memberIds.contains(creatorId)) {
            memberIds.add(creatorId);
        }
        if (memberIds.size() < 2 || memberIds.size() > 4) {
            throw new IllegalArgumentException("Member count must be between 2 and 4, including creator");
        }

        // 그룹 생성
        Group newGroup = Group.builder()
                .name(groupName)
                .build();
        newGroup.setUsers(new ArrayList<>());
        newGroup.setBooks(new ArrayList<>());
        groupRepository.save(newGroup);

        // 멤버 추가
        for (Long memberId : memberIds) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> {
                        log.error("User not found with ID: {}", memberId);
                        return new IllegalArgumentException("Invalid user ID: " + memberId);
                    });

            GroupUser groupUser = new GroupUser(newGroup, member, memberId.equals(creatorId) ? GroupUser.Role.ROLE_LEADER : GroupUser.Role.ROLE_USER);
            newGroup.addGroupUser(groupUser);
            log.info("Added user {} to group {} with role {}", memberId, groupName, groupUser.getRole());
        }

        // 책 추가
        Book book = bookService.findById(bookId);
        GroupBook groupBook = new GroupBook();
        groupBook.setGroup(newGroup);
        groupBook.setBook(book);
        groupBook.setCreatedAt(LocalDateTime.now());
        newGroup.getBooks().add(groupBook);

        // 그룹 저장
        Group savedGroup = groupRepository.save(newGroup);
        log.info("Successfully saved group '{}' with ID {}, {} members, and book ID {}", savedGroup.getName(), savedGroup.getId(), savedGroup.getUsers().size(), bookId);

        return savedGroup;
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return groupRepository.existsByName(name.trim());
    }

    /**
     * 현재 로그인한 사용자가 속한 그룹 목록을 뷰(DTO) 형태로 조회합니다.
     * 각 그룹에 대해 현재 사용자가 리더인지 여부를 계산하고, 멤버 목록을 정렬합니다.
     * @param currentUserId 현재 로그인한 사용자의 ID
     * @return 그룹 목록 DTO 리스트 (GroupListViewDto)
     */
    public List<GroupListViewDto> findMyGroupsForView(Long currentUserId) {
        if (currentUserId == null) {
            log.warn("Cannot find groups for null userId.");
            return List.of(); // 빈 리스트 반환
        }

        log.info("Finding groups with users for userId: {}", currentUserId);
        // Fetch Join을 사용하는 리포지토리 메소드 호출
        List<Group> groups = groupRepository.findGroupsWithUsersByUserId(currentUserId);
        log.info("Found {} groups raw for userId: {}", groups.size(), currentUserId);

        // 조회된 그룹 목록을 순회하며 DTO로 변환
        List<GroupListViewDto> groupViews = groups.stream()
                .map(group -> {
                    // 현재 사용자가 이 그룹의 리더인지 확인
                    boolean isLeader = group.getUsers() != null && group.getUsers().stream()
                            .anyMatch(gu -> gu.getRole() == GroupUser.Role.ROLE_LEADER && gu.getUserId().equals(currentUserId));
                    log.trace("Group ID: {}, User ID: {}, Is Leader: {}", group.getId(), currentUserId, isLeader);

                    // 멤버 리스트 정렬 (리더 우선, 다음은 닉네임 순)
                    if (group.getUsers() != null) {
                        group.getUsers().sort(Comparator.comparing((GroupUser gu) -> gu.getRole() == GroupUser.Role.ROLE_LEADER ? 0 : 1) // 리더(0) < 유저(1)
                                .thenComparing(gu -> gu.getUser() != null ? gu.getUser().getNickName() : "")); // 닉네임 가나다순 (null 처리)
                    }

                    // DTO 생성 및 반환
                    return new GroupListViewDto(group, isLeader);
                })
                .collect(Collectors.toList()); // 결과를 List<GroupListViewDto> 로 수집

        log.info("Returning {} group views for userId: {}", groupViews.size(), currentUserId);
        return groupViews;
    }
}