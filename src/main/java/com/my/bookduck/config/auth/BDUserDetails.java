package com.my.bookduck.config.auth;

import com.my.bookduck.domain.user.User;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Data
public class BDUserDetails implements UserDetails, OAuth2User {
    private User user;
    private Map<String, Object> attributes;

    public BDUserDetails(User user) {
        this.user = user;
    }

    public BDUserDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                return user.getRole().name();
            }
        });

        return collection;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getLoginId();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * User 엔티티의 고유 ID(PK)를 반환합니다.
     * @return 사용자 ID (Long 타입)
     */
    public Long getId() {
        // User 객체가 null이 아닐 경우 User의 ID를 반환
        return this.user != null ? this.user.getId() : null;
    }

    /**
     * User 엔티티의 닉네임을 반환합니다. (편의 메소드 추가)
     * @return 사용자 닉네임 (String 타입)
     */
    public String getNickname() {
        return this.user != null ? this.user.getNickName() : null;
    }
}
