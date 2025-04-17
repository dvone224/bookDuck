package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddUserRequest;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


}
