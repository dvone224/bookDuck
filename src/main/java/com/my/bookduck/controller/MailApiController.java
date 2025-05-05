package com.my.bookduck.controller;

import com.my.bookduck.controller.request.MailRequest;
import com.my.bookduck.controller.request.MailVerificationRequest;
import com.my.bookduck.service.MailService;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
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
    public Map<String, String> verifyCode(MailVerificationRequest verificationRequest) {
        boolean isVerified = mailService.verifyCode(verificationRequest.getMail(), verificationRequest.getCode());
        return Map.of("status", isVerified ? "Verified" : "Verification failed");
    }

    @PostMapping("/findid")
    public void sendmailwithid(@RequestBody String mail) {
        log.info("send mail idonly {}", mail);
        mailService.findIdMail(mail);
    }

    @PostMapping("/issuepw")
    public void issuePassword(@RequestBody String mail) {
        log.info("Issue password with mail {}", mail);
        mailService.issuePwMail(mail);
    }

}
