package com.my.bookduck.controller;

import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.controller.response.loginUserInfo; // 세션 사용자 정보 DTO
import com.my.bookduck.domain.book.Book; // 필요시 사용
import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.group.GroupBook;
import com.my.bookduck.domain.group.GroupUser;
import com.my.bookduck.domain.user.User; // User 엔티티
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.GroupService;
import com.my.bookduck.service.UserService; // UserService 주입 (createGroup에서 사용)
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity; // check-name에서 사용
import org.springframework.security.core.userdetails.UsernameNotFoundException; // 예외 처리
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.AccessDeniedException;
import java.security.Principal; // Spring Security 사용자 정보
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map; // check-name에서 사용
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/group")
@RequiredArgsConstructor // final 필드 생성자 주입
public class GroupController {

    private final GroupService groupService;
    private final BookService bookService; // 책 검색 및 정보 조회에 필요
    private final UserService userService; // 사용자 정보 조회에 필요 (createGroup)

    /**
     * 그룹 추가 폼 페이지 요청
     * @return 뷰 이름 "group/addForm"
     */
    @GetMapping("/addGroup")
    public String showAddGroupForm() {
        log.info("Request received for add group form page.");
        return "group/addForm"; // templates/group/addForm.html
    }

    /**
     * 새 그룹 생성 요청 처리
     * @param groupName 그룹 이름
     * @param memberIds 추가할 멤버 ID 목록 (폼에서 전달)
     * @param bookId 그룹의 첫 책 ID
     * @param principal 현재 인증된 사용자 정보 (Spring Security)
     * @param redirectAttributes 리다이렉트 시 메시지 전달용
     * @return 성공 시 "/group/success", 실패 시 "/group/addGroup" 리다이렉트
     */
    @PostMapping("/create")
    public String createGroup(
            @RequestParam String groupName,
            @RequestParam List<Long> memberIds, // 폼에서 여러 멤버 선택 가능 가정
            @RequestParam Long bookId,
            Principal principal, // 현재 로그인 사용자 정보 주입
            RedirectAttributes redirectAttributes) {

        log.info("Received request to create group. Name: '{}', Initial Member IDs: {}, Book ID: {}",
                groupName, memberIds, bookId);

        // --- 기본 입력값 검증 (Controller 레벨) ---
        if (groupName == null || groupName.trim().isEmpty()) {
            log.warn("Group creation failed: Group name is empty.");
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            return "redirect:/group/addGroup";
        }
        // 멤버 수 검증은 Service에서 최종적으로 수행 (본인 포함 2~4명)
        if (memberIds == null) { // null 체크 정도는 필요
            log.warn("Group creation failed: Member ID list is null.");
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 멤버를 선택해주세요.");
            return "redirect:/group/addGroup";
        }
        if (bookId == null) {
            log.warn("Group creation failed: Book ID is null.");
            redirectAttributes.addFlashAttribute("errorMsg", "그룹에 연결할 책을 선택해주세요.");
            return "redirect:/group/addGroup";
        }

        // --- 그룹 생성 로직 ---
        try {
            // 1. Principal에서 로그인 아이디(username) 가져오기
            String loginId = principal.getName();
            if (loginId == null) {
                // 이 경우는 거의 없지만 방어적 코딩
                log.error("Principal name (loginId) is null. User might not be properly authenticated.");
                throw new IllegalStateException("사용자 인증 정보를 가져올 수 없습니다.");
            }
            log.info("Attempting group creation by user with loginId: {}", loginId);

            // 2. UserService를 통해 로그인 아이디로 User 엔티티 조회
            User creator = userService.findByLoginId(loginId); // UserService 구현 필요
            Long creatorId = creator.getId(); // User 객체에서 실제 Long 타입 ID 가져오기
            log.info("Found creator user. ID: {}", creatorId);

            // 3. GroupService 호출하여 그룹 생성 로직 수행
            groupService.createGroupWithMembersAndBook(groupName, memberIds, bookId, creatorId);

            log.info("Group '{}' created successfully by user ID {}.", groupName.trim(), creatorId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 '" + groupName.trim() + "' 생성이 완료되었습니다.");
            return "redirect:/group/success"; // 성공 페이지로 리다이렉트

        } catch (UsernameNotFoundException e) { // UserService에서 사용자 못 찾을 경우
            log.error("Error finding creator user (loginId: {}): {}", principal.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성자 정보를 찾는 중 오류가 발생했습니다.");
            return "redirect:/group/addGroup";
        } catch (IllegalArgumentException e) { // Service 로직 등에서 발생시킨 유효성 검사 예외
            log.error("Error during group creation (IllegalArgumentException): {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage()); // 사용자에게 보여줄 오류 메시지 전달
            return "redirect:/group/addGroup";
        } catch (Exception e) { // 그 외 예상치 못한 모든 예외
            log.error("Unexpected error during group creation", e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return "redirect:/group/addGroup";
        }
    }

    /**
     * 그룹 생성 성공 페이지 요청
     * @return 뷰 이름 "group/success"
     */
    @GetMapping("/success")
    public String showAddGroupSuccess() {
        log.info("Displaying group creation success page.");
        return "group/success"; // templates/group/success.html
    }

    /**
     * 현재 로그인한 사용자가 속한 그룹 목록 페이지 요청
     * @param model 뷰에 데이터 전달
     * @param session 현재 HTTP 세션 (로그인 정보 확인용)
     * @return 뷰 이름 "group/list"
     */
    @GetMapping("/list")
    public String showGroupList(Model model, HttpSession session) {
        log.info("Request received for user's group list page.");
        // 세션에서 로그인 사용자 정보 가져오기 (타입 캐스팅 주의)
        Object sessionUserObj = session.getAttribute("loginuser");
        loginUserInfo loginUser = null; // 세션에 저장된 사용자 정보 DTO 타입으로 가정

        if (sessionUserObj instanceof loginUserInfo) {
            loginUser = (loginUserInfo) sessionUserObj;
            log.debug("User info found in session: Nickname='{}', ID={}", loginUser.getNickName(), loginUser.getId());
        } else {
            if (sessionUserObj != null) {
                log.warn("Session attribute 'loginuser' is of unexpected type: {}", sessionUserObj.getClass().getName());
            } else {
                log.warn("Session attribute 'loginuser' not found. User might not be logged in.");
            }
        }

        // 로그인 여부에 따라 처리 분기
        if (loginUser == null || loginUser.getId() == null) {
            log.info("User is not logged in or user ID is missing. Displaying login prompt.");
            // 모델에 빈 리스트와 로그인 안내 메시지 추가
            model.addAttribute("myGroupViews", Collections.emptyList());
            if (loginUser == null) {
                model.addAttribute("loginPrompt", "그룹 목록을 보려면 로그인이 필요합니다.");
            } else {
                model.addAttribute("loginPrompt", "사용자 정보를 가져올 수 없습니다."); // ID가 null인 경우
            }
        } else {
            Long userId = loginUser.getId();
            log.info("Fetching group list view for logged-in userId: {}", userId);
            try {
                // GroupService 호출하여 DTO 목록 가져오기
                List<GroupListViewDto> myGroupViews = groupService.findMyGroupsForView(userId);
                log.info("Found {} group views for user {}", myGroupViews.size(), userId);
                model.addAttribute("myGroupViews", myGroupViews); // 모델에 DTO 리스트 추가
                if (myGroupViews.isEmpty()) {
                    model.addAttribute("noGroupsMessage", "속한 그룹이 없습니다."); // 그룹 없을 때 메시지
                }
            } catch (Exception e) {
                log.error("Error fetching group list for userId: {}", userId, e);
                model.addAttribute("myGroupViews", Collections.emptyList());
                model.addAttribute("errorMsg", "그룹 목록을 불러오는 중 오류가 발생했습니다."); // 오류 메시지 전달
            }
        }
        return "group/list"; // templates/group/list.html
    }

    /**
     * 그룹 이름 중복 확인 (AJAX 요청 처리)
     * @param name 확인할 그룹 이름
     * @return 사용 가능 여부 JSON 응답 {"isAvailable": true/false}
     */
    @GetMapping("/check-name")
    @ResponseBody // 반환값을 HTTP 응답 본문에 직접 작성 (JSON 변환)
    public ResponseEntity<Map<String, Boolean>> checkGroupName(@RequestParam String name) {
        log.debug("Received AJAX request to check group name: '{}'", name);
        // 이름 유효성 검사 (null 또는 빈 문자열)
        if (name == null || name.trim().isEmpty()) {
            log.debug("Group name is empty, responding 'not available'.");
            return ResponseEntity.ok(Map.of("isAvailable", false));
        }
        // GroupService를 통해 중복 확인
        boolean exists = groupService.existsByName(name.trim());
        boolean isAvailable = !exists; // 존재하지 않으면 사용 가능
        log.debug("Group name '{}' exists: {}, Available: {}", name.trim(), exists, isAvailable);
        // 결과를 Map에 담아 ResponseEntity로 반환
        return ResponseEntity.ok(Map.of("isAvailable", isAvailable));
    }

    /**
     * 그룹 수정 폼 페이지 요청 처리
     * @param groupId 수정할 그룹 ID (경로 변수)
     * @param model 뷰에 데이터 전달
     * @param session 로그인 확인용
     * @param redirectAttributes 리다이렉트 시 메시지 전달용
     * @return 뷰 이름 "group/fix" 또는 리다이렉트 경로
     */
    @GetMapping("/fix/{groupId}")
    public String fixGroup(@PathVariable Long groupId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        log.info("Request received for fix group form for groupId: {}", groupId);

        // 1. 로그인 확인
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null) {
            log.warn("User not logged in. Redirecting to login page.");
            redirectAttributes.addFlashAttribute("errorMsg", "로그인이 필요합니다.");
            return "redirect:/login-form"; // 로그인 페이지 경로 확인 필요
        }

        try {
            // 2. 그룹 정보 조회 (책 목록 포함 및 정렬됨)
            log.debug("Fetching group details for ID: {}", groupId);
            Group group = groupService.findGroupByIdWithBooks(groupId);

            // 3. (선택적) 리더 권한 확인 - 뷰에서 이미 처리했다면 생략 가능
            // boolean isLeader = group.getUsers().stream()...
            // if (!isLeader) { ... }

            // 4. 모델에 그룹 정보 추가
            model.addAttribute("group", group); // 그룹 객체 전체 전달

            // (선택적) FlashAttribute로 전달된 이전 입력값 처리 (수정 실패 시)
            if (model.containsAttribute("submittedGroupName")) {
                model.addAttribute("groupName", model.getAttribute("submittedGroupName"));
            } else {
                model.addAttribute("groupName", group.getName()); // 기본값은 현재 그룹 이름
            }


            log.info("Displaying fix form for group: '{}' (ID: {})", group.getName(), groupId);
            return "group/fix"; // templates/group/fix.html

        } catch (IllegalArgumentException e) { // 그룹 조회 실패 등
            log.error("Error fetching group for fix form (groupId: {}): {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage()); // 예: "그룹을 찾을 수 없습니다."
            return "redirect:/group/list"; // 그룹 목록 페이지로 리다이렉트
        } catch (Exception e) { // 기타 예상치 못한 오류
            log.error("Unexpected error loading fix form for group (groupId: {}): {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보를 불러오는 중 오류가 발생했습니다.");
            return "redirect:/group/list";
        }
    }

    /**
     * 그룹 정보 수정 및 책 추가 요청 처리 (POST)
     * @param groupId 수정할 그룹 ID
     * @param groupName 새 그룹 이름
     * @param newBookId 추가할 책 ID (null 가능)
     * @param redirectAttributes 리다이렉트 시 메시지 전달
     * @param session 로그인 사용자 확인 등
     * @return 성공 시 "/group/list", 실패 시 "/group/fix/{groupId}" 리다이렉트
     */
    @PostMapping("/fix/{groupId}")
    public String processFixGroup( // 메소드 이름 변경 (updateGroup -> processFixGroup 등)
                                   @PathVariable Long groupId,
                                   @RequestParam String groupName, // 그룹 이름은 필수
                                   @RequestParam(required = false) Long newBookId, // 추가할 책 ID는 선택 사항
                                   RedirectAttributes redirectAttributes,
                                   HttpSession session) {

        log.info("Received POST request to update group ID: {} with name: '{}'. Attempting to add book ID: {}",
                groupId, groupName, newBookId == null ? "None" : newBookId);

        // 1. 로그인 확인 (필수)
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null) {
            log.warn("User session expired or not logged in during group update attempt for group ID: {}", groupId);
            redirectAttributes.addFlashAttribute("errorMsg", "세션이 만료되었거나 로그인이 필요합니다.");
            // 로그인 페이지로 보낼 수도 있지만, 보통 수정 페이지로 다시 보내는 것이 사용자 경험에 나음
            return "redirect:/group/fix/" + groupId;
        }
        // 2. (권장) 리더 권한 확인 - Service에서 수행하거나 여기서 추가
        // try {
        //     Group group = groupService.findGroupByIdWithBooks(groupId); // 그룹 정보 로드
        //     boolean isLeader = group.getUsers().stream()... ;
        //     if (!isLeader) { throw new AccessDeniedException(...); }
        // } catch (...) { ... }

        // 3. 입력값 기본 검증
        if (groupName == null || groupName.trim().isEmpty()) {
            log.warn("Group update failed for ID {}: Group name is empty.", groupId);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName); // 입력값 유지
            return "redirect:/group/fix/" + groupId;
        }

        try {
            // 4. 서비스 호출하여 그룹 이름 수정 및 책 추가 로직 수행
            log.debug("Calling GroupService.updateNameAndAddBook for group ID: {}", groupId);
            groupService.updateNameAndAddBook(groupId, groupName.trim(), newBookId);

            log.info("Successfully updated group information and potentially added book for group ID: {}", groupId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 정보가 성공적으로 업데이트되었습니다.");
            // 성공 시 그룹 목록 페이지 또는 그룹 상세 페이지로 이동
            return "redirect:/group/list";

        } catch (IllegalArgumentException e) { // 서비스에서 발생한 유효성 오류 (이름 중복, 책 제한 등)
            log.warn("Failed to update group/add book for ID {}: {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            // 실패 시 다시 수정 폼으로 이동 (입력값 유지)
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName); // 사용자가 입력한 이름 유지
            // newBookId는 보통 실패 시 다시 선택해야 하므로 유지하지 않음
            return "redirect:/group/fix/" + groupId;
        } catch (Exception e) { // 기타 예상치 못한 오류
            log.error("Unexpected error updating group/adding book for ID {}: {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보 업데이트 중 오류가 발생했습니다.");
            redirectAttributes.addFlashAttribute("submittedGroupName", groupName); // 입력값 유지 시도
            return "redirect:/group/fix/" + groupId;
        }
    }

    /**
     * 그룹 상세 정보 페이지 요청 처리 (예시)
     * @param groupId 조회할 그룹 ID
     * @param model 뷰에 데이터 전달
     * @return 뷰 이름 "group/groupForm"
     */
    @GetMapping("/groupForm/{groupId}")
    public String groupForm(@PathVariable Long groupId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        log.info("Received request for group detail form for groupId: {}", groupId);

        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "로그인이 필요합니다.");
            return "redirect:/login-form";
        }

        try {
            // 그룹 상세 정보 조회 (책, 멤버 포함)
            Group group = groupService.findGroupByIdWithBooks(groupId);
            // 현재 사용자가 그룹 멤버인지 확인 (선택적)
            // boolean isMember = group.getUsers().stream().anyMatch(gu -> gu.getUserId().equals(loginUser.getId()));
            // if (!isMember) { throw new AccessDeniedException("그룹 멤버만 접근 가능합니다."); }

            model.addAttribute("group", group);
            // 필요하다면 추가 정보 (예: 그룹 게시판 목록 등) 조회 및 모델에 추가
            return "group/groupForm"; // templates/group/groupForm.html

        } catch (IllegalArgumentException e) {
            log.error("Error fetching group details for groupForm (groupId: {}): {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage()); // 그룹 못찾음 등
            return "redirect:/group/list";
        } catch (Exception e) { // AccessDeniedException 등 다른 예외 처리
            log.error("Error loading groupForm for group ID {}: {}", groupId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 정보를 불러오는 중 오류가 발생했습니다.");
            return "redirect:/group/list";
        }
    }

    /**
     * 그룹에서 책 삭제 처리 (AJAX)
     * @param groupId 그룹 ID
     * @param bookId 삭제할 책 ID
     * @param session 현재 세션 (사용자 확인용)
     * @return 성공 시 HTTP 200 OK, 실패 시 적절한 오류 상태 코드 및 메시지
     */
    @DeleteMapping("/fix/{groupId}/book/{bookId}") // ★★★ DELETE 매핑 사용 ★★★
    @ResponseBody // ★★★ JSON 또는 단순 응답 반환 ★★★
    public ResponseEntity<?> deleteBookFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long bookId,
            HttpSession session) {

        log.info("Received DELETE request to remove book ID {} from group ID {}", bookId, groupId);

        // 1. 로그인 확인
        loginUserInfo loginUser = (loginUserInfo) session.getAttribute("loginuser");
        if (loginUser == null || loginUser.getId() == null) {
            log.warn("Unauthorized attempt to delete book from group {}. User not logged in.", groupId);
            // 401 Unauthorized 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }
        Long currentUserId = loginUser.getId();

        try {
            // 2. 서비스 호출하여 삭제 로직 수행 (내부에서 리더 권한 확인)
            groupService.deleteBookFromGroup(groupId, bookId, currentUserId);
            log.info("Successfully deleted book ID {} from group ID {} by user ID {}", bookId, groupId, currentUserId);
            // 성공 시 200 OK 응답 (본문 없이)
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) { // 그룹/책 못 찾음, 마지막 책 삭제 시도 등
            log.warn("Failed to delete book ID {} from group ID {}: {}", bookId, groupId, e.getMessage());
            // 400 Bad Request 또는 404 Not Found 반환 (메시지 포함)
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) { // 기타 예상치 못한 오류
            log.error("Unexpected error deleting book ID {} from group ID {}: {}", bookId, groupId, e.getMessage(), e);
            // 500 Internal Server Error 반환
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("책 삭제 중 오류가 발생했습니다.");
        }
    }
}