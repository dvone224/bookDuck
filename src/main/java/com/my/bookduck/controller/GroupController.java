package com.my.bookduck.controller;

import com.my.bookduck.controller.response.GroupListViewDto;
import com.my.bookduck.controller.response.loginUserInfo; // 로그인 사용자 정보 DTO (세션에 저장된 타입)
import com.my.bookduck.domain.group.Group;                // Group 엔티티
import com.my.bookduck.domain.user.User;                  // User 엔티티 (createGroup에서 필요)
import com.my.bookduck.service.GroupService;              // Group 관련 서비스
import com.my.bookduck.service.UserService;                // User 관련 서비스 (createGroup에서 필요)
import jakarta.servlet.http.HttpSession;                   // 세션 사용
import lombok.RequiredArgsConstructor;                     // 생성자 주입 Lombok 어노테이션
import lombok.extern.slf4j.Slf4j;                         // 로깅 Facade (Lombok)
import org.springframework.http.ResponseEntity;             // @ResponseBody 와 함께 사용
import org.springframework.security.core.userdetails.UsernameNotFoundException; // 예외 처리
import org.springframework.stereotype.Controller;          // 스프링 MVC 컨트롤러
import org.springframework.ui.Model;                       // 뷰에 데이터 전달
import org.springframework.web.bind.annotation.*;           // 요청 매핑 어노테이션
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 리다이렉트 시 데이터 전달

import java.security.Principal;                           // 현재 인증된 사용자 정보
import java.util.Collections;                             // 빈 리스트 생성 시 사용
import java.util.List;                                    // 리스트 사용
import java.util.Map;                                     // Map 사용 (check-name 응답)

@Slf4j // 로깅 사용
@Controller // 이 클래스가 웹 요청을 처리하는 컨트롤러임을 선언
@RequestMapping("/group") // 이 컨트롤러의 모든 메소드는 "/group" 경로 하위에 매핑됨
@RequiredArgsConstructor // final 필드에 대한 생성자를 자동으로 생성 (의존성 주입)
public class GroupController {

    private final GroupService groupService; // Group 서비스 의존성 주입
    private final UserService userService;   // User 서비스 의존성 주입

    /**
     * 그룹 추가 폼을 보여주는 페이지로 이동합니다.
     * HTTP GET 요청 "/group/addGroup" 처리
     * @return 뷰 이름 "group/addForm" (templates/group/addForm.html)
     */
    @GetMapping("/addGroup")
    public String showAddGroupForm() {
        log.debug("Request received for add group form.");
        return "group/addForm"; // 뷰 템플릿 반환
    }

    /**
     * 새 그룹을 생성하는 요청을 처리합니다.
     * HTTP POST 요청 "/group/create" 처리
     * @param groupName 생성할 그룹 이름 (폼 파라미터)
     * @param memberIds 그룹에 추가할 멤버 ID 목록 (폼 파라미터)
     * @param bookId 그룹에 연결할 책 ID (폼 파라미터)
     * @param principal 현재 로그인한 사용자 정보 (Spring Security 제공)
     * @param redirectAttributes 리다이렉트 시 메시지 전달용
     * @return 성공 시 "/group/success"로 리다이렉트, 실패 시 "/group/addGroup"으로 리다이렉트
     */
    @PostMapping("/create")
    public String createGroup(
            @RequestParam String groupName,        // 요청 파라미터 바인딩
            @RequestParam List<Long> memberIds,    // 요청 파라미터 (여러 값 가능) 바인딩
            @RequestParam Long bookId,             // 요청 파라미터 바인딩
            Principal principal,                   // 인증된 사용자 정보 주입
            RedirectAttributes redirectAttributes) { // 리다이렉트 속성 주입

        log.info("Received request to create group. Name: {}, Member IDs: {}, Book ID: {}", groupName, memberIds, bookId);

        // --- 입력값 검증 ---
        if (groupName == null || groupName.trim().isEmpty()) {
            log.warn("Group creation failed: Group name is empty.");
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            return "redirect:/group/addGroup"; // 입력 폼으로 다시 리다이렉트
        }
        // 멤버 수 제한 (예: 1명 이상, 5명 이하 - 본인 포함 계산 필요 시 서비스에서)
        if (memberIds == null || memberIds.isEmpty() || memberIds.size() > 4) {
            log.warn("Group creation failed: Invalid number of members ({}).", memberIds != null ? memberIds.size() : 0);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 멤버는 1명 이상 4명 이하로 선택해야 합니다. (본인 제외)");
            return "redirect:/group/addGroup";
        }
        if (bookId == null) {
            log.warn("Group creation failed: Book ID is null.");
            redirectAttributes.addFlashAttribute("errorMsg", "그룹에 연결할 책을 선택해주세요.");
            return "redirect:/group/addGroup";
        }

        // --- 그룹 생성 로직 ---
        try {
            log.info("Attempting to find creator user info...");

            // 1. Principal에서 로그인 아이디 가져오기 (일반적으로 username 또는 설정된 식별자)
            String loginId = principal.getName();
            if (loginId == null) {
                log.error("Principal name (loginId) is null. User might not be properly authenticated.");
                redirectAttributes.addFlashAttribute("errorMsg", "사용자 인증 정보를 가져올 수 없습니다.");
                return "redirect:/group/addGroup";
            }
            log.info("Creator loginId from Principal: {}", loginId);

            // 2. UserService를 통해 로그인 아이디로 User 엔티티 조회
            // findByLoginId 메소드가 User 객체를 반환하거나, 없으면 예외를 던지거나, Optional<User>를 반환한다고 가정
            User creator = userService.findByLoginId(loginId); // 예: User 반환 또는 UsernameNotFoundException 발생

            // 3. User 객체 확인 및 고유 ID(Long) 추출
            if (creator == null) {
                // 이 경우는 보통 UsernameNotFoundException 등으로 처리되지만, 방어적으로 체크
                log.error("User with loginId '{}' not found in the database (should have thrown exception).", loginId);
                redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성자 정보를 찾을 수 없습니다.");
                return "redirect:/group/addGroup";
            }
            Long creatorId = creator.getId(); // User 객체에서 실제 Long 타입 ID 가져오기
            log.info("Found creator user. ID: {}", creatorId);

            // 4. GroupService를 호출하여 그룹 생성 로직 수행
            groupService.createGroupWithMembersAndBook(groupName.trim(), memberIds, bookId, creatorId); // 실제 생성 로직 호출

            log.info("Group '{}' created successfully by user ID {}.", groupName.trim(), creatorId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 '" + groupName.trim() + "' 생성이 완료되었습니다.");
            return "redirect:/group/success"; // 성공 페이지로 리다이렉트

        } catch (IllegalArgumentException e) { // 서비스 로직 등에서 발생시킨 유효성 검사 예외 처리
            log.error("Error during group creation (IllegalArgumentException): {}", e.getMessage(), e); // 예외 메시지 로깅
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage()); // 사용자에게 보여줄 오류 메시지 전달
            return "redirect:/group/addGroup"; // 입력 폼으로 다시 리다이렉트
        } catch (UsernameNotFoundException e) { // userService.findByLoginId 에서 사용자 못 찾을 경우 예외 처리
            log.error("Error finding creator user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성자 정보를 찾는 중 오류가 발생했습니다.");
            return "redirect:/group/addGroup";
        } catch (Exception e) { // 그 외 예상치 못한 모든 예외 처리
            log.error("Unexpected error during group creation", e); // 전체 스택 트레이스 로깅
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return "redirect:/group/addGroup";
        }
    }

    /**
     * 그룹 생성 성공 페이지를 보여줍니다.
     * HTTP GET 요청 "/group/success" 처리
     * @return 뷰 이름 "group/success" (templates/group/success.html)
     */
    @GetMapping("/success")
    public String showAddGroupSuccess() {
        log.debug("Displaying group creation success page.");
        return "group/success"; // 성공 뷰 템플릿 반환
    }

    /**
     * 현재 로그인한 사용자가 속한 그룹 목록 페이지를 보여줍니다.
     * HTTP GET 요청 "/group/list" 처리
     * @param model 뷰에 데이터를 전달하기 위한 Model 객체
     * @param session 현재 HTTP 세션 객체 (로그인 정보 접근용)
     * @return 뷰 이름 "group/list" (templates/group/list.html)
     */
    @GetMapping("/list")
    public String showGroupList(Model model, HttpSession session) {
        Object sessionUserObj = session.getAttribute("loginuser");
        loginUserInfo loginUser = null;

        if (sessionUserObj instanceof loginUserInfo) {
            loginUser = (loginUserInfo) sessionUserObj;
            log.debug("User info found in session: {}", loginUser);
        } else {
            log.warn("Session attribute 'loginuser' not found or is of incorrect type.");
        }

        if (loginUser == null) {
            model.addAttribute("myGroupViews", Collections.emptyList()); // 모델 속성 이름 변경
        } else {
            Long userId = loginUser.getId();
            if (userId == null) {
                log.error("User ID is null in loginUserInfo object.");
                model.addAttribute("myGroupViews", Collections.emptyList());
            } else {
                log.info("Fetching group list view for userId: {}", userId);
                // 수정된 서비스 메소드 호출 (DTO 리스트 반환)
                List<GroupListViewDto> myGroupViews = groupService.findMyGroupsForView(userId);
                log.info("Found {} group views for user {}", myGroupViews.size(), userId);
                // 모델에 DTO 리스트 추가 (변경된 이름 사용)
                model.addAttribute("myGroupViews", myGroupViews);
            }
        }
        return "group/list"; // 뷰 이름 반환
    }

    /**
     * 그룹 이름 중복 여부를 비동기적으로 확인합니다. (AJAX 요청 처리용)
     * HTTP GET 요청 "/group/check-name" 처리
     * @param name 중복 확인할 그룹 이름 (요청 파라미터)
     * @return 사용 가능 여부를 포함하는 JSON 응답 ({"isAvailable": true/false})
     */
    @GetMapping("/check-name")
    @ResponseBody // 메소드의 반환 값을 HTTP 응답 본문에 직접 작성 (JSON 등으로 변환)
    public ResponseEntity<Map<String, Boolean>> checkGroupName(@RequestParam String name) {
        // 이름이 비어있는 경우 바로 사용 불가 응답
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("isAvailable", false));
        }
        // GroupService를 통해 해당 이름의 그룹이 존재하는지 확인
        boolean exists = groupService.existsByName(name.trim());
        boolean isAvailable = !exists; // 존재하지 않으면 사용 가능
        log.debug("Checking group name '{}'. Exists: {}, Available: {}", name.trim(), exists, isAvailable);
        // 결과를 Map에 담아 ResponseEntity로 감싸서 반환 (JSON으로 변환됨)
        return ResponseEntity.ok(Map.of("isAvailable", isAvailable));
    }
}