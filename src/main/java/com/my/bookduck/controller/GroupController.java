package com.my.bookduck.controller;

// import com.my.bookduck.controller.request.AddGroupRequest; // 더 이상 직접 사용 안 함
import com.my.bookduck.service.GroupService; // GroupService 임포트
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 리다이렉트 시 메시지 전달

import java.util.List; // List 임포트
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService; // GroupService 주입

    // 그룹 생성 폼을 보여주는 메소드
    @GetMapping("/addGroup")
    public String showAddGroupForm() {
        return "group/addForm"; // 폼 페이지 경로
    }

    // 그룹 생성 요청을 처리하는 메소드
    // HTML form의 action과 method에 맞춰 경로와 @PostMapping 설정
    @PostMapping("/create") // 경로를 /create 로 변경 권장 (RESTful 규칙)
    public String createGroup(
            @RequestParam String groupName, // 그룹 이름 받기
            @RequestParam List<Long> memberIds, // 멤버 ID 목록 받기
            RedirectAttributes redirectAttributes) { // 리다이렉트 시 속성 전달

        log.info("Received request to create group. Name: {}, Member IDs: {}", groupName, memberIds);

        // 서버 측 유효성 검사 (JavaScript 검사 외 추가 검사)
        if (groupName == null || groupName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 이름을 입력해주세요.");
            return "redirect:/group/addGroup"; // 생성 폼으로 리다이렉트
        }
        if (memberIds == null || memberIds.size() < 2 || memberIds.size() > 4) {
            redirectAttributes.addFlashAttribute("errorMsg", "멤버는 2명 이상 4명 이하로 선택해야 합니다.");
            return "redirect:/group/addGroup"; // 생성 폼으로 리다이렉트
        }

        try{
            // GroupService 호출하여 그룹 생성 및 멤버 추가
            groupService.createGroupWithMembers(groupName, memberIds);

            log.info("Group creation successful. Redirecting...");
            redirectAttributes.addFlashAttribute("successMsg", "그룹 '" + groupName + "' 생성이 완료되었습니다.");
            // 성공 시 리다이렉트할 경로 (예: 그룹 목록 페이지, 마이페이지 등)
            return "redirect:/group/success"; // 예시 경로 (실제 경로로 변경 필요)

        }catch (Exception e) { // 그 외 예외 처리
            log.error("Unexpected error during group creation", e);
            redirectAttributes.addFlashAttribute("errorMsg", "그룹 생성 중 오류가 발생했습니다. 다시 시도해주세요.");
            return "redirect:/group/addForm"; // 생성 폼으로 리다이렉트
        }
        // 원래 코드의 successForm으로 가는 부분은 리다이렉트로 대체됨
        // return "group/successForm";
    }

    // 그룹저장 성공시 이동페이지
    @GetMapping("/success")
    public String showAddGroupSuccess() {
        return "group/success";
    }


    // 그룹 목록 페이지 예시 (리다이렉트 타겟)
    @GetMapping("/list")
    public String showGroupList() {
        // TODO: 그룹 목록 조회 로직 추가
        return "group/list"; // 그룹 목록 뷰 이름
    }

    /**
     * 그룹 이름 중복 확인 API (AJAX 호출용)
     * @param name 확인할 그룹 이름
     * @return JSON 형태 {"isAvailable": boolean}
     */
    @GetMapping("/check-name")
    @ResponseBody // JSON 응답을 위해 필요
    public ResponseEntity<Map<String, Boolean>> checkGroupName(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            // 빈 이름은 사용할 수 없다고 간주
            return ResponseEntity.ok(Map.of("isAvailable", false));
        }
        boolean isAvailable = !groupService.existsByName(name.trim());
        log.debug("Checking group name '{}'. Available: {}", name, isAvailable);
        return ResponseEntity.ok(Map.of("isAvailable", isAvailable));
    }
}