package com.my.bookduck.controller;

import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.controller.response.loginUserInfo; // 세션 사용자 정보 DTO
import com.my.bookduck.domain.book.Book; // getGroupBooks 에서 사용
import com.my.bookduck.domain.group.Group;
// import com.my.bookduck.domain.group.GroupBook; // 직접 사용 안 함
import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.user.User; // User 엔티티
import com.my.bookduck.service.BoardService; // BoardService 주입
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.GroupService;
import com.my.bookduck.service.UserService; // UserService 주입
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity; // API 응답에서 사용
import org.springframework.security.access.AccessDeniedException; // Spring Security 예외 사용
import org.springframework.security.core.userdetails.UsernameNotFoundException; // 예외 처리
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal; // Spring Security 사용자 정보
import java.util.*; // Collections, Map, List, Set, Objects 등 사용
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/group") // 클래스 레벨 기본 경로
@RequiredArgsConstructor // final 필드 생성자 주입
public class GroupController {

    private final GroupService groupService;
    private final BookService bookService; // 책 검색 및 정보 조회에 필요
    private final UserService userService; // 사용자 정보 조회에 필요
    private final BoardService boardService; // 공개/비공개 토글 및 정보 조회에 필요

    /**
     * 그룹 추가 폼 페이지 요청
     * @return 뷰 이름 "group/addForm"
     */
    @GetMapping("/addGroup")
    public String showAddGroupForm() {
        log.info("Request received for add group form page.");
        return "group/addForm";
    }

    /**
     * 새 그룹 생성 요청 처리
     */
    @PostMapping("/create")
    public String createGroup(
            @RequestParam String groupName,
            @RequestParam List<Long> memberIds,
            @RequestParam Long bookId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        log.info("Received request to create group. Name: '{}', Initial Member IDs: {}, Book ID: {}",
                groupName, memberIds, bookId);

        if (groupName == null || groupName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            return "redirect:/group/addGroup";
        }
        if (memberIds == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 멤버를 선택해주세요.");
            return "redirect:/group/addGroup";
        }
        if (bookId == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "그룹에 연결할 책을 선택해주세요.");
            return "redirect:/group/addGroup";
        }

        try {
            String loginId = principal.getName();
            if (loginId == null) throw new IllegalStateException("사용자 인증 정보를 가져올 수 없습니다.");
            log.info("Attempting group creation by user with loginId: {}", loginId);

            User creator = userService.findByLoginId(loginId);
            if (creator == null) throw new UsernameNotFoundException("로그인 정보를 찾을 수 없습니다.");
            Long creatorId = creator.getId();
            log.info("Found creator user. ID: {}", creatorId);

            groupService.createGroupWithMembersAndBook(groupName, memberIds, bookId, creatorId);

            log.info("Group '{}' created successfully by user ID {}.", groupName.trim(), creatorId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 '" + groupName.trim() + "' 생성이 완료되었습니다.");
            return "redirect:/group/success";

        } catch (UsernameNotFoundException | IllegalStateException e) {
            log.error("Authentication or User finding error during group creation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/addGroup";
        } catch (IllegalArgumentException e) {
            log.error("Validation error during group creation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/addGroup";
        } catch (Exception e) {
            log.error("Unexpected error during group creation", e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return "redirect:/group/addGroup";
        }
    }

    /**
     * 그룹 생성 성공 페이지 요청
     */
    @GetMapping("/success")
    public String showAddGroupSuccess() {
        log.info("Displaying group creation success page.");
        return "group/success";
    }

    /**
     * 현재 로그인한 사용자가 속한 그룹 목록 페이지 요청
     */
    @GetMapping("/list")
    public String showGroupList(Model model, HttpSession session) {
        log.info("Request received for user's group list page.");
        Object sessionUserObj = session.getAttribute("loginuser");
        loginUserInfo loginUser = null;

        if (sessionUserObj instanceof loginUserInfo) {
            loginUser = (loginUserInfo) sessionUserObj;
            if (loginUser.getId() == null) { // ID null 체크
                log.warn("User ID is null in session.");
                loginUser = null; // ID 없으면 로그인 안된 것으로 간주
            } else {
                log.debug("User info found in session: Nickname='{}', ID={}", loginUser.getNickName(), loginUser.getId());
            }
        } else {
            if (sessionUserObj != null) log.warn("Session attribute 'loginuser' is of unexpected type: {}", sessionUserObj.getClass().getName());
            else log.warn("Session attribute 'loginuser' not found.");
        }

        if (loginUser == null) {
            model.addAttribute("myGroupViews", Collections.emptyList());
            model.addAttribute("loginPrompt", "그룹 목록을 보려면 로그인이 필요합니다.");
            return "group/list";
        }

        Long userId = loginUser.getId();
        log.info("Fetching group list view for logged-in userId: {}", userId);
        try {
            List<GroupListViewDto> myGroupViews = groupService.findMyGroupsForView(userId);
            model.addAttribute("myGroupViews", myGroupViews);
            if (myGroupViews.isEmpty()) model.addAttribute("noGroupsMessage", "속한 그룹이 없습니다.");
        } catch (Exception e) {
            log.error("Error fetching group list for userId: {}", userId, e);
            model.addAttribute("myGroupViews", Collections.emptyList());
            model.addAttribute("errorMsg", "그룹 목록을 불러오는 중 오류가 발생했습니다.");
        }
        return "group/list";
    }

    /**
     * 그룹 이름 중복 확인 (AJAX - GET)
     */
    @GetMapping("/check-name")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkGroupName(@RequestParam String name) {
        log.debug("Received AJAX request to check group name: '{}'", name);
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("isAvailable", false)); // 빈 이름 사용 불가
        }
        boolean isAvailable = !groupService.existsByName(name.trim());
        log.debug("Group name '{}' available: {}", name.trim(), isAvailable);
        return ResponseEntity.ok(Map.of("isAvailable", isAvailable));
    }

    /**
     * 그룹 수정 폼 페이지 요청 처리 (Board 정보 포함)
     */
    @GetMapping("/fix/{groupId}")
    public String fixGroup(@PathVariable Long groupId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        log.info("Request received for fix group form for groupId: {}", groupId);

        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null || loginUser.getId() == null) {
            log.warn("User not logged in or ID is null. Redirecting to login page.");
            redirectAttributes.addFlashAttribute("errorMsg", "로그인이 필요합니다.");
            return "redirect:/login-form";
        }
        Long currentUserId = loginUser.getId();

        try {
            Group group = groupService.findGroupByIdWithBooks(groupId);
            Set<Long> publicBookIds = boardService.getPublicBookIdsForGroup(groupId);
            boolean isLeader = group.getUsers().stream()
                    .anyMatch(gu -> gu.getUserId().equals(currentUserId) && gu.getRole() == GroupUser.Role.ROLE_LEADER);

            model.addAttribute("group", group);
            model.addAttribute("publicBookIds", publicBookIds);
            model.addAttribute("isGroupLeader", isLeader);
            model.addAttribute("groupName", model.containsAttribute("submittedGroupName") ? model.getAttribute("submittedGroupName") : group.getName());

            log.info("Displaying fix form for group: '{}' (ID: {})", group.getName(), groupId);
            return "group/fix";

        } catch (IllegalArgumentException e) {
            log.error("Error fetching group for fix form (groupId: {}): {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/list";
        } catch (AccessDeniedException e) {
            log.warn("Access denied loading fix form for group {}: {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/list";
        } catch (Exception e) {
            log.error("Unexpected error loading fix form for group (groupId: {}): {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보를 불러오는 중 오류가 발생했습니다.");
            return "redirect:/group/list";
        }
    }

    /**
     * 그룹 정보 수정 및 책 추가 요청 처리 (POST)
     */
    @PostMapping("/fix/{groupId}")
    public String processFixGroup(
            @PathVariable Long groupId,
            @RequestParam String groupName,
            @RequestParam(required = false) Long newBookId,
            RedirectAttributes redirectAttributes,
            HttpSession session) { // Principal 불필요 시 제거 가능

        log.info("Received POST request to update group ID: {} with name: '{}'. Attempting to add book ID: {}",
                groupId, groupName, newBookId == null ? "None" : newBookId);

        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null || loginUser.getId() == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "세션이 만료되었거나 로그인이 필요합니다.");
            return "redirect:/group/fix/" + groupId;
        }
        // Long currentUserId = loginUser.getId(); // 서비스에서 권한 체크 시 필요 없을 수 있음

        if (groupName == null || groupName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName);
            return "redirect:/group/fix/" + groupId;
        }

        try {
            // GroupService의 updateNameAndAddBook 메소드 내에서 리더 권한 확인 가정
            groupService.updateNameAndAddBook(groupId, groupName.trim(), newBookId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 정보가 성공적으로 업데이트되었습니다.");
            return "redirect:/group/list";

        } catch (IllegalArgumentException | AccessDeniedException e) { // 유효성 또는 권한 오류
            log.warn("Failed to update group/add book for ID {}: {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName);
            return "redirect:/group/fix/" + groupId;
        } catch (Exception e) {
            log.error("Unexpected error updating group/adding book for ID {}: {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보 업데이트 중 오류가 발생했습니다.");
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName);
            return "redirect:/group/fix/" + groupId;
        }
    }

    /**
     * 그룹 상세 정보 페이지 요청 처리 (현재 사용 안 함 - 모달로 대체됨)
     */
    @GetMapping("/groupForm/{groupId}")
    public String groupForm(@PathVariable Long groupId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        // 이 메소드는 현재 /group/list 에서 모달을 사용하므로 직접 호출되지 않을 수 있음
        log.info("Received request for group detail form for groupId: {} (May be deprecated)", groupId);

        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null || loginUser.getId() == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "로그인이 필요합니다.");
            return "redirect:/login-form";
        }

        try {
            Group group = groupService.findGroupByIdWithBooks(groupId);
            model.addAttribute("group", group);
            return "group/groupForm"; // templates/group/groupForm.html

        } catch (IllegalArgumentException e) {
            log.error("Error fetching group details for groupForm (groupId: {}): {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/list";
        } catch (Exception e) {
            log.error("Error loading groupForm for group ID {}: {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보를 불러오는 중 오류가 발생했습니다.");
            return "redirect:/group/list";
        }
    }

    /**
     * 그룹에서 책 삭제 처리 (AJAX - DELETE)
     */
    @DeleteMapping("/fix/{groupId}/book/{bookId}")
    @ResponseBody
    public ResponseEntity<?> deleteBookFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long bookId,
            Principal principal) {

        log.info("Received DELETE request to remove book ID {} from group ID {}", bookId, groupId);

        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        String loginId = principal.getName();
        User currentUser;
        try {
            currentUser = userService.findByLoginId(loginId);
            if (currentUser == null) throw new UsernameNotFoundException("User not found");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "사용자 정보를 찾을 수 없습니다."));
        }
        Long currentUserId = currentUser.getId();

        try {
            // GroupService의 deleteBookFromGroup 메소드 내에서 리더 권한 확인 가정
            groupService.deleteBookFromGroup(groupId, bookId, currentUserId);
            return ResponseEntity.ok(Map.of("message", "책이 성공적으로 삭제되었습니다."));

        } catch (IllegalArgumentException | AccessDeniedException e) {
            log.warn("Failed to delete book ID {} from group ID {}: {}", bookId, groupId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting book ID {} from group ID {}: {}", bookId, groupId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "책 삭제 중 오류가 발생했습니다."));
        }
    }

    /**
     * 그룹 내 책 공개/비공개 상태 토글 API (AJAX - POST)
     * 요청 URL: /group/api/group/{groupId}/book/{bookId}/toggle-privacy
     */
    @PostMapping("/api/group/{groupId}/book/{bookId}/toggle-privacy")
    @ResponseBody
    public ResponseEntity<?> toggleBookPrivacy(
            @PathVariable Long groupId,
            @PathVariable Long bookId,
            Principal principal) {

        log.info("Received POST request to toggle privacy for book {} in group {}", bookId, groupId);

        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }
        String loginId = principal.getName();
        User currentUser;
        try {
            currentUser = userService.findByLoginId(loginId);
            if (currentUser == null) throw new UsernameNotFoundException("User not found");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "사용자 정보를 찾을 수 없습니다."));
        }
        Long currentUserId = currentUser.getId();
        log.debug("Toggle privacy requested by user ID: {}", currentUserId);

        try {
            // BoardService의 toggleBookPrivacy 메소드 내에서 리더 권한 확인 가정
            boolean newPublicState = boardService.toggleBookPrivacy(groupId, bookId, currentUserId);
            log.info("Successfully toggled privacy for book {} in group {}. New state: {}", bookId, groupId, newPublicState ? "Public" : "Private");
            return ResponseEntity.ok(Map.of("isPublic", newPublicState));

        } catch (IllegalArgumentException | AccessDeniedException e) {
            log.warn("Failed to toggle privacy for book {} in group {}: {}", bookId, groupId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error toggling privacy for book {} in group {}: {}", bookId, groupId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "서버 내부 오류가 발생했습니다."));
        }
    }

    // --- ★★★ 그룹 책 목록 조회 API 엔드포인트 ★★★ ---
    /**
     * 특정 그룹에 속한 책 목록 정보를 조회하는 API (AJAX - GET)
     * 요청 URL: /group/api/group/{groupId}/books
     * @param groupId 조회할 그룹 ID
     * @return 성공 시 책 정보 리스트 (JSON), 실패 시 오류 응답
     */
    @GetMapping("/api/group/{groupId}/books") // JavaScript에서 호출하는 경로
    @ResponseBody
    public ResponseEntity<?> getGroupBooks(@PathVariable Long groupId) {
        log.info("Request received for books in group ID: {}", groupId);

        // (선택적) 권한 확인 로직 추가 가능

        try {
            Group group = groupService.findGroupByIdWithBooks(groupId); // 책 정보 포함 조회

            List<Map<String, Object>> bookList = group.getBooks().stream()
                    .map(groupBook -> {
                        Book book = groupBook.getBook();
                        if (book == null) return null;
                        return Map.<String, Object>of(
                                "id", book.getId(),
                                "title", book.getTitle() != null ? book.getTitle() : "제목 없음",
                                "cover", book.getCover() != null ? book.getCover() : "/images/default-book-cover.png"
                        );
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Returning {} books for group ID: {}", bookList.size(), groupId);
            return ResponseEntity.ok(bookList);

        } catch (IllegalArgumentException e) {
            log.warn("Cannot find group for ID {}: {}", groupId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "그룹 정보를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("Error fetching books for group ID {}: {}", groupId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "책 목록 조회 중 오류가 발생했습니다."));
        }
    }
}