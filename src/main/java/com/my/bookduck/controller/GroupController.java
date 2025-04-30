package com.my.bookduck.controller;

import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.GroupService;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;

    @GetMapping("/addGroup")
    public String showAddGroupForm() {
        return "group/addForm";
    }

    @PostMapping("/create")
    public String createGroup(
            @RequestParam String groupName,
            @RequestParam List<Long> memberIds,
            @RequestParam Long bookId,
            Principal principal, // 현재 로그인 사용자 정보
            RedirectAttributes redirectAttributes) {

        log.info("Received request to create group. Name: {}, Member IDs: {}, Book ID: {}", groupName, memberIds, bookId);

        // --- 입력값 검증 (기존 코드 유지) ---
        if (groupName == null || groupName.trim().isEmpty()) {
            // ...
            return "redirect:/group/addGroup";
        }
        if (memberIds == null || memberIds.size() < 1 || memberIds.size() > 4) {
            // ...
            return "redirect:/group/addGroup";
        }
        if (bookId == null) {
            // ...
            return "redirect:/group/addGroup";
        }

        // --- 그룹 생성 로직 ---
        try {
            log.info("Attempting to find creator user info...");

            // 1. Principal에서 로그인 아이디(username 또는 loginId) 가져오기
            String loginId = principal.getName();
            if (loginId == null) {
                log.error("Principal name (loginId) is null. User might not be properly authenticated.");
                redirectAttributes.addFlashAttribute("errorMsg", "사용자 인증 정보를 가져올 수 없습니다.");
                return "redirect:/group/addGroup";
            }
            log.info("Creator loginId from Principal: {}", loginId);

            // 2. UserService를 통해 로그인 아이디로 User 객체 조회
            User creator = userService.findByLoginId(loginId); // UserService에 추가한 메소드 호출

            // 3. User 객체 확인 및 고유 ID(Long) 추출
            if (creator == null) {
                // 해당 loginId를 가진 사용자가 DB에 없는 경우 (보통 발생하면 안 됨)
                log.error("User with loginId '{}' not found in the database.", loginId);
                redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성자 정보를 찾을 수 없습니다.");
                return "redirect:/group/addGroup";
            }
            Long creatorId = creator.getId(); // User 객체에서 실제 Long 타입 ID 가져오기
            log.info("Found creator user. ID: {}", creatorId);

            // 4. 추출한 creatorId를 사용하여 그룹 생성 서비스 호출 (주석 해제 및 수정)
            groupService.createGroupWithMembersAndBook(groupName, memberIds, bookId, creatorId); // creatorId 사용

            log.info("Group '{}' created successfully by user ID {}.", groupName, creatorId);
            redirectAttributes.addFlashAttribute("successMsg", "그룹 '" + groupName + "' 생성이 완료되었습니다.");
            return "redirect:/group/success"; // 성공 페이지로 리다이렉트

        } catch (IllegalArgumentException e) { // GroupService 등에서 발생시킨 특정 예외 처리
            log.error("Error during group creation (IllegalArgumentException): {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/group/addGroup";
        } catch (UsernameNotFoundException e) { // 만약 UserService.findByLoginId가 Optional 대신 예외를 던진 경우
            log.error("Error finding creator user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성자 정보를 찾는 중 오류가 발생했습니다.");
            return "redirect:/group/addGroup";
        } catch (Exception e) { // 그 외 예상치 못한 예외 처리
            log.error("Unexpected error during group creation", e); // 스택 트레이스 로깅
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성 중 오류가 발생했습니다. 다시 시도해주세요.");
            return "redirect:/group/addGroup";
        }
    }

    @GetMapping("/success")
    public String showAddGroupSuccess() {
        return "group/success";
    }

    @GetMapping("/list")
    public String showGroupList() {
        return "group/list";
    }

    @GetMapping("/check-name")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkGroupName(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("isAvailable", false));
        }
        boolean isAvailable = !groupService.existsByName(name.trim());
        log.debug("Checking group name '{}'. Available: {}", name, isAvailable);
        return ResponseEntity.ok(Map.of("isAvailable", isAvailable));
    }
}