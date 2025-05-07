package com.my.bookduck.service;

import com.my.bookduck.domain.board.Board;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.group.GroupUser; // 권한 확인용
import com.my.bookduck.repository.BoardRepository;
import com.my.bookduck.repository.BookRepository;
import com.my.bookduck.repository.GroupRepository;
// import com.my.bookduck.repository.UserRepository; // 필요시 주입
import com.my.bookduck.repository.UserBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// import java.nio.file.AccessDeniedException;
import org.springframework.security.access.AccessDeniedException; // Spring Security 예외 사용

import java.time.LocalDateTime; // Auditing 미사용 시 필요할 수 있음
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor // final 필드 생성자 주입
public class BoardService {

    private final BoardRepository boardRepository;
    private final GroupRepository groupRepository;
    private final BookRepository bookRepository;
    private final UserBookRepository userBookRepository;
    // 필요시: private final UserRepository userRepository;

    /**
     * 특정 그룹의 공개된 책(Board) ID 목록을 조회합니다.
     * @param groupId 그룹 ID
     * @return 공개된 책 ID의 Set
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public Set<Long> getPublicBookIdsForGroup(Long groupId) {
        log.debug("Fetching public book IDs for group ID: {}", groupId);
        return boardRepository.findBookIdsByGroupId(groupId);
    }

    /**
     * 그룹 내 특정 책의 공개 상태를 토글합니다. (공개 -> 비공개, 비공개 -> 공개)
     * 이 작업은 해당 그룹의 리더만 수행할 수 있습니다.
     *
     * @param groupId 그룹 ID
     * @param bookId 책 ID
     * @param currentUserId 작업을 요청한 사용자 ID (권한 확인용)
     * @return 변경 후 공개 상태 (true: 공개됨, false: 비공개됨)
     * @throws IllegalArgumentException 그룹, 책을 찾을 수 없거나 할 때
     * @throws AccessDeniedException 사용자가 리더 권한이 없을 때
     */
    @Transactional // 데이터 변경 트랜잭션
    public boolean toggleBookPrivacy(Long groupId, Long bookId, Long currentUserId) throws AccessDeniedException {
        log.info("User {} attempting to toggle privacy for book {} in group {}", currentUserId, bookId, groupId);

        // 1. 그룹 조회 및 사용자 권한 확인 (리더만 가능)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> {
                    log.warn("Toggle privacy failed: Group not found with ID: {}", groupId);
                    return new IllegalArgumentException("그룹을 찾을 수 없습니다. ID: " + groupId);
                });

        // Fetch users if needed (N+1 방지 위해 Fetch Join 권장)
        if (group.getUsers() != null) group.getUsers().size();

        boolean isLeader = group.getUsers().stream()
                .anyMatch(gu -> gu.getUserId().equals(currentUserId) && gu.getRole() == GroupUser.Role.ROLE_LEADER);

        if (!isLeader) {
            log.warn("Permission denied: User {} is not the leader of group {}", currentUserId, groupId);
            throw new AccessDeniedException("공개/비공개 설정은 그룹 리더만 변경할 수 있습니다.");
        }
        log.debug("User {} confirmed as leader for group {}", currentUserId, groupId);

        // 2. 책 존재 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> {
                    log.warn("Toggle privacy failed: Book not found with ID: {}", bookId);
                    return new IllegalArgumentException("책을 찾을 수 없습니다. ID: " + bookId);
                });
        log.debug("Book found: '{}' (ID: {})", book.getTitle(), bookId);

        // 3. 현재 Board 상태 확인 및 토글
        Optional<Board> existingBoardOpt = boardRepository.findByGroupIdAndBookId(groupId, bookId);

        if (existingBoardOpt.isPresent()) {
            // Case 1: 공개 -> 비공개 (Board 삭제)
            log.info("Book {} in group {} is currently public. Making private by deleting Board entry.", bookId, groupId);
            Board boardToDelete = existingBoardOpt.get();
            boardRepository.delete(boardToDelete);
            log.info("Deleted Board entry for group {}, book {}.", groupId, bookId);
            return false; // 변경 후 상태: 비공개 (false)

        } else {
            // Case 2: 비공개 -> 공개 (Board 생성)
            log.info("Book {} in group {} is currently private. Making public by creating a new Board entry.", bookId, groupId);

            // ★★★ Board 엔티티에 정의된 public 생성자 직접 호출 ★★★
            Board newBoard = new Board(group, book);
            // createdAt은 JPA Auditing 또는 @PrePersist로 처리

            boardRepository.save(newBoard);
            log.info("Created new Board entry (ID: {}) for group {}, book {}. State is now public.", newBoard.getId(), groupId, bookId);
            return true; // 변경 후 상태: 공개 (true)
        }
    }


    /**
     * 필터링 및 정렬 조건에 따라 게시글 목록을 조회합니다.
     *
     * @param userId 현재 로그인한 사용자 ID (내가 구매한 책 필터링 시 필요, null 가능)
     * @param filterMyBooks true면 내가 구매한 책 관련 게시글만, false면 전체 게시글
     * @param sortBy 정렬 기준 ("createdAt", "bookTitle")
     * @param sortDirection 정렬 방향 ("ASC", "DESC")
     * @return Board 엔티티 리스트
     */

    public List<Board> findBoards(Long userId, boolean filterMyBooks, String query, String sortBy, String sortDirection) {
        log.info("게시글 목록 조회 - userId: {}, filterMyBooks: {}, query(책제목): '{}', sortBy: {}, sortDir: {}",
                userId, filterMyBooks, query, sortBy, sortDirection);

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortProperty = "bookTitle".equalsIgnoreCase(sortBy) ? "book.title" : "createdAt";
        Sort sort = Sort.by(direction, sortProperty);

        String searchQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : "";

        if (filterMyBooks && userId != null) {
            List<Long> myBookIds = userBookRepository.findBookIdsByUserId(userId);
            if (myBookIds == null || myBookIds.isEmpty()) {
                return List.of();
            }
            log.debug("사용자(ID:{}) 구매 책 ID 목록: {} 으로 필터링, 책 제목 검색어: '{}'", userId, myBookIds, searchQuery);
            // 검색어가 있든 없든 findByBookIdInAndQuery 호출 (쿼리 내부에서 LIKE '%%' 처리)
            return boardRepository.findByBookIdInAndQuery(myBookIds, searchQuery, sort);
        } else {
            if (!searchQuery.isEmpty()) {
                log.debug("전체 게시글 대상 책 제목 검색어 '{}' 및 정렬 적용.", searchQuery);
                return boardRepository.findAllPublicBoardsWithDetailsAndQuery(searchQuery, sort);
            } else {
                log.debug("전체 게시글 조회 및 정렬 적용 (검색어 없음).");
                return boardRepository.findAllPublicBoardsWithDetails(sort);
            }
        }
    }
}