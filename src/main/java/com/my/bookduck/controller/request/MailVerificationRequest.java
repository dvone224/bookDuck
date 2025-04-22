package com.my.bookduck.controller.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailVerificationRequest {

    private String mail;
    private int code;
}
