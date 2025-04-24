package com.my.bookduck;

import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialDataLoader {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @PostConstruct
    public void loadInitialData() {
        if (userRepository.findByLoginId("admin") == null) {
            User admin = User.builder()
                    .loginId("admin")
                    .password(bCryptPasswordEncoder.encode("4321"))
                    .name("admin")
                    .email("bookduck@buckduck.com")
                    .nickName("admin")
                    .role(String.valueOf(User.Role.ROLE_ADMIN))
                    .build();
            userRepository.save(admin);
            System.out.println("관리자 추가 완료");
        }

        for (int i = 1; i < 11; i++) {
            String name = "t" + i;
            if (userRepository.findByLoginId(name) == null) {
                User user = User.builder()
                        .loginId(name)
                        .password(bCryptPasswordEncoder.encode("1234"))
                        .name(name)
                        .email(name + "@bookduck.com")
                        .nickName(name)
                        .role(String.valueOf(User.Role.ROLE_USER))
                        .build();
                userRepository.save(user);
                System.out.println(name + "사용자 추가 완료");
            }

        }
    }
}
