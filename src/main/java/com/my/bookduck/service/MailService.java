package com.my.bookduck.service;

import jakarta.mail.internet.MimeMessage;

import java.util.concurrent.CompletableFuture;

public interface MailService {

    MimeMessage createMail(String mail);

    boolean verifyCode(String mail, int code);

    CompletableFuture<Integer> sendMail(String mail);

    void findIdMail(String mail);

    void issuePwMail(String mail);
}
