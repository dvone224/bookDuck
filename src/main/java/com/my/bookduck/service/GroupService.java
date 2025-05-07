package com.my.bookduck.service;

import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.group.GroupBook;
import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.GroupBookRepository;
import com.my.bookduck.repository.GroupRepository;
import com.my.bookduck.repository.GroupUserRepository; // 필요시 사용
import com.my.bookduck.repository.UserRepository;
// import com.my.bookduck.repository.GroupBookRepository; // 필요시 주입
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // createGroup에서 사용될 수 있음
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional; // Optional 임포트
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupUserRepository groupUserRepository; // 필요에 따라 사용
    private final UserRepository userRepository; // User 정보 조회에 필요
    private final BookService bookService; // Book 정보 조회에 필요
    private final GroupBookRepository groupBookRepository;
    // private final GroupBookRepository groupBookRepository; // 직접 GroupBook 조작 시 필요


    /**
     * 그룹에서 특정 책을 삭제합니다.
     * @param groupId 그룹 ID
     * @param bookId 삭제할 책 ID
     * @param currentUserId 현재 작업을 요청한 사용자 ID (리더 권한 확인용)
     * @throws IllegalArgumentException 그룹 또는 책 정보를 찾을 수 없거나, 마지막 책을 삭제하려는 경우
     * @throws AccessDeniedException 리더가 아닌 사용자가 삭제를 시도하는 경우
     */
    @Transactional
    public void deleteBookFromGroup(Long groupId, Long bookId, Long currentUserId) {
        log.info("Attempting to delete book ID {} from group ID {} by user ID {}", bookId, groupId, currentUserId);

        // 1. 그룹 조회 (멤버 정보 포함 - 권한 확인용)
        // Fetch Join 사용 권장
        Group group = groupRepository.findById(groupId) // findByIdWithUsers 같은 메소드 사용 권장
                .orElseThrow(() -> {
                    log.error("Cannot delete book. Group not found with ID: {}", groupId);
                    return new IllegalArgumentException("그룹 정보를 찾을 수 없습니다 (ID: " + groupId + ")");
                });

        // 사용자 정보 Lazy Loading (findById 사용 시)
        if (group.getUsers() != null) group.getUsers().size();


        // 3. 그룹의 현재 책 목록 확인 및 마지막 책 삭제 방지
        List<GroupBook> currentBooks = group.getBooks();
        // 책 목록 Lazy Loading (Group 조회 시 Fetch Join 안했을 경우)
        if (currentBooks != null) currentBooks.size();
        else { // books 컬렉션 자체가 null인 비정상 상황 처리
            log.error("Book collection is null for group ID: {}", groupId);
            throw new IllegalStateException("그룹의 도서 정보를 로드할 수 없습니다.");
        }


        log.debug("Group {} currently has {} books.", groupId, currentBooks.size());
        if (currentBooks.size() <= 1) {
            log.warn("Cannot delete book ID {}. Group {} must have at least one book.", bookId, groupId);
            throw new IllegalArgumentException("그룹에는 최소 1권의 책이 있어야 합니다. 마지막 책은 삭제할 수 없습니다.");
        }

        // 4. 삭제 대상 GroupBook 찾기
        // 방법 A: 컬렉션에서 직접 찾기 (Group 조회 시 books 를 Fetch Join 한 경우 효율적)
        GroupBook bookToDelete = currentBooks.stream()
                .filter(gb -> gb.getBook() != null && gb.getBook().getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Book ID {} not found within the books of group ID {}.", bookId, groupId);
                    return new IllegalArgumentException("삭제하려는 책이 그룹에 존재하지 않습니다.");
                });

        // 방법 B: GroupBookRepository 사용 (더 명확할 수 있음)
        // GroupBook bookToDelete = groupBookRepository.findByGroupIdAndBookId(groupId, bookId)
        //         .orElseThrow(() -> new IllegalArgumentException("삭제하려는 그룹 도서 정보를 찾을 수 없습니다."));

        // 5. GroupBook 엔티티 삭제
        // orphanRemoval=true 설정 덕분에 컬렉션에서 제거하면 DB에서도 삭제됨
        boolean removed = group.getBooks().remove(bookToDelete);
        if (removed) {
            log.info("Removed book ID {} from group ID {}. Remaining books: {}", bookId, groupId, group.getBooks().size());
            // groupRepository.save(group); // 변경 감지로 처리되거나 명시적 저장 (필요시)
        } else {
            // remove 가 실패하는 경우는 거의 없지만 로깅
            log.error("Failed to remove GroupBook (Book ID: {}) from collection for group ID: {}. This might indicate an issue.", bookId, groupId);
            // throw new IllegalStateException("그룹에서 책을 제거하는 데 실패했습니다."); // 필요시 예외 발생
        }

        // 만약 GroupBookRepository를 사용했다면:
        // groupBookRepository.delete(bookToDelete);
        // log.info("Deleted GroupBook entry for group ID {} and book ID {}", groupId, bookId);
    }

    /**
     * 새 그룹 생성 (멤버, 책 1권 포함)
     * @param groupName 그룹 이름
     * @param memberIds 멤버 사용자 ID 목록 (생성자 ID 포함될 수도, 안될 수도 있음)
     * @param bookId 그룹에 연결할 첫 번째 책 ID
     * @param creatorId 그룹 생성자(리더)의 사용자 ID
     * @return 생성된 Group 엔티티
     * @throws IllegalArgumentException 유효성 검사 실패 시
     * @throws UsernameNotFoundException 사용자 ID로 User를 찾지 못했을 경우 (UserRepository 구현에 따라 다름)
     */
    @Transactional
    public Group createGroupWithMembersAndBook(String groupName, List<Long> memberIds, Long bookId, Long creatorId) {
        log.info("Creating group '{}' with potential member IDs: {}, initial book ID: {}, creator ID: {}",
                groupName, memberIds, bookId, creatorId);

        String trimmedGroupName = groupName.trim();
        // 유효성 검사: 그룹 이름 중복
        if (groupRepository.existsByName(trimmedGroupName)) {
            log.warn("Group creation failed. Name '{}' already exists.", trimmedGroupName);
            throw new IllegalArgumentException("이미 사용 중인 그룹 이름입니다: " + trimmedGroupName);
        }

        // 유효성 검사: 멤버 목록 처리 및 최종 멤버 수 확인 (2~4명)
        List<Long> finalMemberIds = new ArrayList<>(memberIds); // 원본 리스트 변경 방지
        if (!finalMemberIds.contains(creatorId)) {
            log.debug("Creator ID {} not found in initial member list, adding it.", creatorId);
            finalMemberIds.add(creatorId); // 생성자 ID가 없으면 명시적으로 추가
        }
        // 중복 제거 (만약 있을 경우 대비)
        finalMemberIds = finalMemberIds.stream().distinct().collect(Collectors.toList());

        if (finalMemberIds.size() < 2 || finalMemberIds.size() > 4) {
            log.warn("Group creation failed. Final member count ({}) is invalid. Expected 2-4.", finalMemberIds.size());
            throw new IllegalArgumentException("그룹 멤버는 생성자를 포함하여 2명 이상 4명 이하이어야 합니다.");
        }
        log.info("Final member IDs for group '{}': {}", trimmedGroupName, finalMemberIds);

        // 그룹 엔티티 생성
        Group newGroup = Group.builder()
                .name(trimmedGroupName)
                .build();
        // Group 엔티티 내에서 List 필드들이 new ArrayList<>() 로 초기화되었다고 가정

        // 멤버(GroupUser) 추가
        for (Long memberId : finalMemberIds) {
            // UserRepository를 사용하여 User 엔티티 조회
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> {
                        // User를 찾지 못한 경우, 적절한 예외 발생
                        // UsernameNotFoundException 또는 IllegalArgumentException 등
                        log.error("User not found with ID: {} while creating group.", memberId);
                        return new IllegalArgumentException("그룹 멤버로 추가하려는 사용자를 찾을 수 없습니다 (ID: " + memberId + ")");
                    });

            // GroupUser 생성 및 역할 부여 (생성자는 리더)
            GroupUser.Role role = memberId.equals(creatorId) ? GroupUser.Role.ROLE_LEADER : GroupUser.Role.ROLE_USER;
            GroupUser groupUser = new GroupUser(newGroup, member, role);
            // Group 엔티티의 addGroupUser 편의 메소드를 통해 양방향 관계 설정 가정
            newGroup.addGroupUser(groupUser);
            log.debug("Added user {} to group {} with role {}", memberId, trimmedGroupName, role);
        }

        // 책(GroupBook) 추가 (첫번째 책)
        Book book = bookService.findById(bookId); // BookService를 통해 Book 엔티티 조회
        GroupBook groupBook = new GroupBook();
        groupBook.setGroup(newGroup);
        groupBook.setBook(book);
        // groupBook.setCreatedAt(LocalDateTime.now()); // @CreatedDate 어노테이션 사용 시 자동 설정됨

        // Group 엔티티의 books 리스트에 추가 (CascadeType.ALL 설정 가정)
        newGroup.getBooks().add(groupBook);
        log.debug("Added initial book ID {} to group {}", bookId, trimmedGroupName);

        // 그룹 저장 (Cascade 설정으로 GroupUser, GroupBook 도 함께 저장됨)
        Group savedGroup = groupRepository.save(newGroup);
        log.info("Successfully saved group '{}' with ID {}, {} members, and initial book ID {}",
                savedGroup.getName(), savedGroup.getId(), savedGroup.getUsers().size(), bookId);

        return savedGroup;
    }

    /**
     * 그룹 이름 중복 확인
     * @param name 확인할 그룹 이름
     * @return 중복 시 true, 아니면 false
     */
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        boolean exists = groupRepository.existsByName(name.trim());
        log.debug("Group name '{}' exists check: {}", name.trim(), exists);
        return exists;
    }

    /**
     * 현재 로그인한 사용자가 속한 그룹 목록을 DTO 형태로 조회.
     * 각 그룹 정보에는 리더 여부, 정렬된 멤버 목록이 포함됨.
     * @param currentUserId 현재 로그인한 사용자의 ID
     * @return 그룹 목록 DTO 리스트 (GroupListViewDto)
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<GroupListViewDto> findMyGroupsForView(Long currentUserId) {
        if (currentUserId == null) {
            log.warn("Cannot find groups for null userId.");
            return List.of(); // 빈 리스트 반환
        }

        log.info("Finding groups with user details for userId: {}", currentUserId);
        // Fetch Join을 사용하는 리포지토리 메소드 호출 권장 (N+1 문제 방지)
        // 예: groupRepository.findGroupsWithUsersByUserId(currentUserId);
        List<Group> groups = groupRepository.findGroupsWithUsersByUserId(currentUserId);
        log.info("Found {} groups raw for userId: {}", groups.size(), currentUserId);

        if (groups.isEmpty()) {
            log.info("No groups found for userId: {}", currentUserId);
            return List.of();
        }

        // 조회된 그룹 목록을 순회하며 DTO로 변환
        List<GroupListViewDto> groupViews = groups.stream()
                .map(group -> {
                    log.trace("Processing group ID: {} for DTO mapping", group.getId());
                    // 현재 사용자가 이 그룹의 리더인지 확인
                    boolean isLeader = group.getUsers() != null && group.getUsers().stream()
                            .anyMatch(gu -> gu.getRole() == GroupUser.Role.ROLE_LEADER && gu.getUserId().equals(currentUserId));
                    log.trace("Group ID: {}, User ID: {}, Is Leader: {}", group.getId(), currentUserId, isLeader);

                    // 멤버 리스트 정렬 (리더 우선, 다음은 닉네임 순)
                    List<GroupUser> sortedUsers = new ArrayList<>();
                    if (group.getUsers() != null && !group.getUsers().isEmpty()) {
                        sortedUsers = group.getUsers().stream()
                                .sorted(Comparator.comparing((GroupUser gu) -> gu.getRole() == GroupUser.Role.ROLE_LEADER ? 0 : 1) // 리더(0) < 유저(1)
                                        .thenComparing(gu -> gu.getUser() != null ? gu.getUser().getNickName() : "", Comparator.nullsLast(String::compareToIgnoreCase))) // 닉네임 가나다순 (null 처리)
                                .collect(Collectors.toList());
                        // 정렬된 리스트를 다시 group 객체에 설정할 필요는 없음 (DTO 생성 시 사용)
                        group.setUsers(sortedUsers); // 필요하다면 Group 객체 자체를 변경
                    } else {
                        log.warn("Group ID: {} has no users associated.", group.getId());
                    }

                    // DTO 생성 및 반환
                    // GroupListViewDto 생성자에서 필요한 데이터를 group 객체로부터 가져가도록 구현되어야 함
                    return new GroupListViewDto(group, isLeader);
                })
                .collect(Collectors.toList()); // 결과를 List<GroupListViewDto> 로 수집

        log.info("Returning {} processed group views for userId: {}", groupViews.size(), currentUserId);
        return groupViews;
    }

    /**
     * 특정 그룹 ID로 그룹 정보를 조회 (연관된 책과 사용자 정보 포함)
     * N+1 문제 방지를 위해 Fetch Join 또는 EntityGraph 사용 권장.
     * 책 목록은 생성 시간(createdAt) 기준 내림차순으로 정렬됨.
     * @param groupId 조회할 그룹 ID
     * @return Group 엔티티 (연관 데이터 로드됨)
     * @throws IllegalArgumentException 그룹을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public Group findGroupByIdWithBooks(Long groupId) {
        log.info("Fetching group details for ID: {} including books and users", groupId);

        // 방법 1: Repository에서 Fetch Join 사용 (권장)
        // 예: GroupRepository에 findByIdWithDetails(@Param("groupId") Long groupId) 메소드 구현 및 호출
        // Group group = groupRepository.findByIdWithDetails(groupId)
        //         .orElseThrow(() -> new IllegalArgumentException("그룹을 찾을 수 없습니다 (ID: " + groupId + ")"));

        // 방법 2: 기본 findById 사용 후 프록시 초기화 (N+1 발생 가능성 있음)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> {
                    log.error("Group not found with ID: {}", groupId);
                    return new IllegalArgumentException("그룹을 찾을 수 없습니다 (ID: " + groupId + ")");
                });

        // Lazy Loding 프록시 강제 초기화 (Fetch Join 사용 시 불필요)
        log.debug("Initializing lazy-loaded collections for group ID: {}", groupId);
        // 사용자 정보 초기화
        if (group.getUsers() != null) {
            group.getUsers().size(); // GroupUser 컬렉션 로드
            // 각 User 프록시 초기화 (닉네임 접근 등)
            group.getUsers().forEach(gu -> { if (gu.getUser() != null) gu.getUser().getNickName(); });
            log.debug("Initialized {} users for group ID: {}", group.getUsers().size(), groupId);
        } else {
            log.warn("User list is null for group ID: {}", groupId);
        }

        // 책 정보 초기화 및 정렬
        if (group.getBooks() != null) {
            group.getBooks().size(); // GroupBook 컬렉션 로드
            // ★★★ 책 목록 정렬 (createdAt 기준 내림차순) ★★★
            // JPA Auditing(@CreatedDate) 사용 가정. nullsLast는 createdAt이 null일 경우 대비
            group.getBooks().sort(Comparator.comparing(GroupBook::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            log.debug("Sorted book list for group ID: {} based on createdAt descending.", groupId);
            // 각 Book 프록시 초기화 (제목 접근 등)
            group.getBooks().forEach(gb -> { if (gb.getBook() != null) gb.getBook().getTitle(); });
            log.debug("Initialized {} books for group ID: {}", group.getBooks().size(), groupId);
        } else {
            log.warn("Book list is null for group ID: {}", groupId);
        }
        log.debug("Finished initializing collections for group ID: {}.", groupId);

        return group;
    }

    /**
     * 그룹 이름 수정 및 새 책 추가 (최대 3권까지)
     * @param groupId 수정할 그룹 ID
     * @param groupName 새 그룹 이름 (공백 제거됨)
     * @param newBookId 추가할 책 ID (null이면 책 추가 안 함)
     * @throws IllegalArgumentException 유효성 검사 실패 시 (ID 오류, 이름 중복, 책 제한 등)
     */
    @Transactional
    public void updateNameAndAddBook(Long groupId, String groupName, Long newBookId) {
        log.info("Attempting to update group name and potentially add book. Group ID: {}, New Name: '{}', New Book ID: {}",
                groupId, groupName, newBookId == null ? "None" : newBookId);

        // 1. 그룹 조회 (연관 데이터 포함 및 정렬된 책 목록 로드)
        Group group = findGroupByIdWithBooks(groupId); // 내부적으로 책 로드 및 정렬 완료

        // 2. 그룹 이름 변경 (필요시) 및 중복 검사
        String trimmedGroupName = groupName.trim();
        if (!group.getName().equals(trimmedGroupName)) { // 이름이 실제로 변경되었는지 확인
            log.info("Group name changed from '{}' to '{}'. Checking for duplicates.", group.getName(), trimmedGroupName);
            if (groupRepository.existsByName(trimmedGroupName)) {
                log.warn("Group name update failed. Name '{}' already exists.", trimmedGroupName);
                throw new IllegalArgumentException("이미 사용 중인 그룹 이름입니다: " + trimmedGroupName);
            }
            log.info("Updating group name for ID {} to '{}'", groupId, trimmedGroupName);
            group.setName(trimmedGroupName);
            // 변경 감지(dirty checking)에 의해 업데이트되거나, 아래 save 호출 시 처리됨
        } else {
            log.info("Group name '{}' is the same as the current name. No name update needed.", trimmedGroupName);
        }

        // 3. 책 추가 로직 (newBookId가 null이 아닐 경우 실행)
        if (newBookId != null) {
            log.info("Proceeding to add book with ID {} to group ID {}", newBookId, groupId);
            // 3a. 추가할 책 조회 (BookService 사용, 없으면 예외 발생)
            Book bookToAdd = bookService.findById(newBookId);
            log.debug("Found book to add: '{}' (ID: {})", bookToAdd.getTitle(), newBookId);
            List<GroupBook> currentBooks = group.getBooks();
            // 3b. 유효성 검사: 최대 책 개수 (현재 3권)
//            final int MAX_BOOKS = 3;
//            List<GroupBook> currentBooks = group.getBooks(); // 이미 로드 및 정렬된 상태
//            log.debug("Current book count in group {}: {}", groupId, currentBooks.size());
//
//            if (currentBooks.size() >= MAX_BOOKS) {
//                log.warn("Cannot add book ID {}. Group {} already has {} books (max is {}).", newBookId, groupId, currentBooks.size(), MAX_BOOKS);
//                throw new IllegalArgumentException("그룹에는 최대 " + MAX_BOOKS + "권의 책만 등록할 수 있습니다.");
//            }

            // 3c. 유효성 검사: 중복 추가 방지
            boolean alreadyExists = currentBooks.stream()
                    .anyMatch(gb -> gb.getBook() != null && gb.getBook().getId().equals(newBookId));
            if (alreadyExists) {
                log.warn("Cannot add book ID {}. Book '{}' already exists in group {}.", newBookId, bookToAdd.getTitle(), groupId);
                throw new IllegalArgumentException("'" + bookToAdd.getTitle() + "' 책은 이미 그룹에 등록되어 있습니다.");
            }

            // 3d. 새 GroupBook 생성 및 그룹에 추가
            log.debug("Creating new GroupBook entry for group {} and book {}", groupId, newBookId);
            GroupBook newGroupBook = new GroupBook();
            newGroupBook.setGroup(group);
            newGroupBook.setBook(bookToAdd);
            // newGroupBook.setCreatedAt(LocalDateTime.now()); // @CreatedDate 사용 시 불필요

            group.getBooks().add(newGroupBook); // 그룹의 책 목록에 추가 (Cascade Persist 동작)
            log.info("Successfully prepared to add book (ID: {}) to group (ID: {}). New total books: {}", newBookId, groupId, group.getBooks().size());
        } else {
            log.info("No new book ID provided. Skipping book addition process.");
        }

        // 4. 그룹 저장 (변경 감지 또는 명시적 저장)
        // CascadeType.ALL 또는 PERSIST 설정 덕분에 Group을 저장하면
        // 새로 추가된 GroupBook 및 Group 이름 변경 사항이 함께 처리됨.
        log.debug("Saving group entity (ID: {}) to persist changes.", groupId);
        groupRepository.save(group);
        log.info("Successfully processed update/add request for group ID: {}. Final book count: {}", groupId, group.getBooks().size());
    }

    /* --- 기존 updateGroup 메소드 (참고용 또는 삭제 가능) ---
    @Transactional
    public Group updateGroup(Long groupId, String groupName, Long bookId, List<Long> memberIds) {
        // ... 이전 버전의 로직 ...
        // 이 메소드는 책을 덮어쓰고 멤버 목록을 직접 처리하는 방식이었음.
        // 현재 요구사항(책 추가, 멤버 수정 불가)과는 맞지 않음.
    }
    */
}