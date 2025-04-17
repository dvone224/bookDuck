package com.my.bookduck.controller.request;

import com.my.bookduck.domain.user.User;
import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class AddUserRequest {

    private String id;
    //private String name;
    private String email;
    private String pw;
    private String nickName;

    public User toEntity(AddUserRequest dto){
        return User.builder()
                .loginId(dto.getId())
                //.name(dto.getName())
                .email(dto.getEmail())
                .password(dto.getPw())
                .nickName(dto.getNickName())
                .build();
    }

}
