package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddUserRequest;
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
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public void createUser(AddUserRequest userDto) throws IllegalStateException {
        log.info("userDto: {}", userDto);
        userDto.setPw(bCryptPasswordEncoder.encode(userDto.getPw()));
        User u = GetUserId(userDto.toEntity(userDto));
        userRepository.save(u);
    }

    private User GetUserId(User user) throws IllegalStateException {
        log.info("getUserId: {}", user);
        if(userRepository.findById(user.getId()) != null){
            throw new IllegalStateException("이미 존재하는 회원 아이디가 있습니다.");
        }
        if(userRepository.findByEmail(user.getEmail()) != null){
            throw new IllegalStateException("이미 가입된 이메일 정보입니다.");
        }
        return user;
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
