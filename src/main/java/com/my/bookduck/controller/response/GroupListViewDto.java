package com.my.bookduck.controller.response;

import com.my.bookduck.domain.group.Group; // Group 엔티티 임포트
import lombok.Getter; // Lombok Getter 사용

@Getter // 필드에 대한 getter 메소드를 자동으로 생성해줍니다.
public class GroupListViewDto {

    // Group 엔티티 객체를 직접 필드로 가집니다.
    private final Group group;

    // 현재 사용자가 해당 그룹의 리더인지 여부를 나타내는 플래그입니다.
    private final boolean isCurrentUserLeader;

    /**
     * 생성자: Group 엔티티와 리더 여부를 받아서 DTO 객체를 생성합니다.
     * @param group 원본 Group 엔티티 객체
     * @param isCurrentUserLeader 현재 사용자가 리더이면 true, 아니면 false
     */
    public GroupListViewDto(Group group, boolean isCurrentUserLeader) {
        this.group = group; // 전달받은 Group 객체를 그대로 저장
        this.isCurrentUserLeader = isCurrentUserLeader;
    }

    // @Getter 어노테이션이 있으므로 아래 getter 메소드들은 자동으로 생성됩니다.
    // public Group getGroup() {
    //     return group;
    // }
    // public boolean isCurrentUserLeader() { // boolean 타입은 isXXX 형태
    //     return isCurrentUserLeader;
    // }
}