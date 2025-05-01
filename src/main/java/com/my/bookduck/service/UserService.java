package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddUserRequest;
import com.my.bookduck.controller.request.SocialJoinUpdateRequest;
import com.my.bookduck.controller.request.UpdateUserRequest;
import com.my.bookduck.controller.response.UserSearchResultResponse;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional
    public void createUser(AddUserRequest userDto) throws IllegalStateException {
        log.info("userDto: {}", userDto);
        userDto.setPw(bCryptPasswordEncoder.encode(userDto.getPw()));
        User u = GetUserId(userDto.toEntity(userDto));
        userRepository.save(u);
    }

    private User GetUserId(User user) throws IllegalStateException {
        log.info("getUserId: {}", user);
        if(userRepository.findByLoginId(user.getLoginId()) != null){
            throw new IllegalStateException("이미 존재하는 회원 아이디가 있습니다.");
        }
        if(userRepository.findByEmail(user.getEmail()) != null){
            throw new IllegalStateException("이미 가입된 이메일 정보입니다.");
        }
        return user;
    }


    public User getUserByLoginId(Long id) throws IllegalStateException {
        log.info("getUserByLoginId: {}", id);
        User user = userRepository.findByid(id);
        if(user == null){
            throw new IllegalStateException("회원 정보를 가져오는데 실패했습니다.");
        }
        return user;
    }

    /**
     * 로그인 아이디(String)로 사용자를 조회합니다.
     * @param loginId 조회할 사용자의 로그인 아이디
     * @return 해당 User 객체, 없으면 null 반환 (또는 Optional<User> 반환 후 컨트롤러에서 처리)
     */
    public User findByLoginId(String loginId) {
        log.debug("Finding user by loginId: {}", loginId);
        // UserRepository의 findByLoginId 메소드 사용
        return userRepository.findByLoginId(loginId);
        /*
        // Optional을 사용하는 경우 (UserNotFoundException 등 예외 처리 권장)
        return userRepository.findByLoginId(loginId)
               .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));
        */
    }

    @Transactional
    public String userInfoUpdate(Long userId, UpdateUserRequest info) throws IllegalStateException {
        User user = userRepository.findById(userId).orElse(null);

        if(user == null){
            throw new IllegalStateException("회원 정보 수정에 실패하였습니다.");
            //return "false";
        }
        log.info("socialJoinUpdateRequest: {}", info);

        if(info.getPw() != null) user.setPassword(bCryptPasswordEncoder.encode(info.getPw()));
        if(info.getEmail() != null) user.setEmail(info.getEmail());
        if(info.getNickName() != null) user.setNickName(info.getNickName());
        if(info.getImg() != null) user.setImg(info.getImg());

        log.info("new user: {}", user);
        userRepository.save(user);

        return "success";

    }

    @Transactional
    public String socialJoinUpdate(Long userId, SocialJoinUpdateRequest socialJoinUpdateRequest) throws IllegalStateException {
        User user = userRepository.findById(userId).orElse(null);

        if(user == null){
            throw new IllegalStateException("추가 정보 입력에 실패하였습니다.");
            //return "false";
        }
        log.info("socialJoinUpdateRequest: {}", socialJoinUpdateRequest);

        if(socialJoinUpdateRequest.getNickName() != null) user.setNickName(socialJoinUpdateRequest.getNickName());
        if(socialJoinUpdateRequest.getImg() != null) user.setImg(socialJoinUpdateRequest.getImg());

        log.info("new user: {}", user);
        userRepository.save(user);


        return "success";

    }

    // --- 사용자 이름 검색 서비스 메소드 추가 ---
    /**
     * 닉네임으로 사용자를 검색하고 결과를 DTO 리스트로 반환합니다.
     * @param nickname 검색할 닉네임
     * @param limit 최대 결과 수
     * @return 검색된 사용자 DTO 리스트 (ID와 NickName 포함)
     */
    public List<UserSearchResultResponse> searchUsersByNickname(String nickname, int limit) {
        log.debug("Searching users by nickname containing '{}' with limit {}", nickname, limit);
        Pageable pageable = PageRequest.of(0, limit);

        List<User> foundUsers = userRepository.findByNickNameContainingIgnoreCase(nickname, pageable);
        log.debug("Found {} users from repository based on nickname", foundUsers.size());

        // --- Entity 리스트 -> DTO 리스트 변환 수정 ---
        List<UserSearchResultResponse> result = foundUsers.stream()
                // *** 여기를 수정: user.getName() -> user.getNickName() ***
                .map(user -> new UserSearchResultResponse(user.getId(), user.getNickName()))
                .collect(Collectors.toList());
        // -------------------------------------------

        log.debug("Returning {} DTOs (ID, NickName) after nickname search", result.size());
        return result;
    }

}
