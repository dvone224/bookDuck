package com.my.bookduck.controller.request;

import com.my.bookduck.domain.user.User;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class SocialJoinUpdateRequest {

    private String nickName;
    private String img;

    public User toEntity(AddUserRequest dto){
        return User.builder()
                .nickName(dto.getNickName())
                .img(dto.getImg())
                .build();
    }

}
