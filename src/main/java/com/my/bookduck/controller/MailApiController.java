package com.my.bookduck.controller;

import com.my.bookduck.controller.request.MailRequest;
import com.my.bookduck.controller.request.MailVerificationRequest;
import com.my.bookduck.service.MailService;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@EnableAsync
@RequestMapping("/mailapi")
public class MailApiController {

    private final MailService mailService;
    private final UserService userService;

    @PostMapping("/sendmail")
    public CompletableFuture<String> mailSend(MailRequest mailRequest) {
        return mailService.sendMail(mailRequest.getMail())
                .thenApply(number -> String.valueOf(number));
    }

    @PostMapping("/verifycode")
    public String verifyCode(MailVerificationRequest verificationRequest) {
        boolean isVerified = mailService.verifyCode(verificationRequest.getMail(), verificationRequest.getCode());
        return isVerified ? "Verified" : "Verification failed";
    }

}
