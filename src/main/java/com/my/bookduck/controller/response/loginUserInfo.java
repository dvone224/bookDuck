package com.my.bookduck.controller.response;

import com.my.bookduck.domain.user.User;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@Builder
@ToString
public class loginUserInfo {
    private final Long id;
    private final String nickName;
    private final String img;
    private final String email;
    private final String provider;
    private final String role;
    public loginUserInfo(User user) {
        this.id = user.getId();
        this.nickName = user.getNickName();
        this.img = user.getImg();
        this.email = user.getEmail();
        this.provider = user.getProvider();
        if(user.getRole() == User.Role.ROLE_ADMIN) {
            this.role = "admin";
        }else{
            this.role = "user";
        }
    }
}
