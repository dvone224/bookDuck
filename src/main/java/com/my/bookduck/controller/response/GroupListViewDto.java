package com.my.bookduck.controller.response;

import com.my.bookduck.domain.group.Group;
import lombok.Getter;

@Getter
public class GroupListViewDto {
    private final Group group; // 원본 Group 객체
    private final boolean isCurrentUserLeader; // 현재 사용자가 리더인지 여부

    public GroupListViewDto(Group group, boolean isCurrentUserLeader) {
        this.group = group;
        this.isCurrentUserLeader = isCurrentUserLeader;
    }
}
