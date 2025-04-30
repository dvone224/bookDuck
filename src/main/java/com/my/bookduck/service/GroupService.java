package com.my.bookduck.service;

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
import java.util.List;

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
}