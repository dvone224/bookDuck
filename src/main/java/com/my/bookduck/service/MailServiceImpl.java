package com.my.bookduck.service;

import com.my.bookduck.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.standard.expression.MessageExpression;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JavaMailSender javaMailSender;
    private static final String senderEmail = "ganggom2@gmail.com";
    private static final Map<String, Integer> verificationCodes = new HashMap<>();

    public static void createNumber(String email){
        int number = new Random().nextInt(900000) + 100000;
        verificationCodes.put(email, number);
    }

    @Override
    public MimeMessage createMail(String mail) {

        createNumber(mail);
        MimeMessage message = javaMailSender.createMimeMessage();

        try{
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"utf-8");
            helper.setFrom(senderEmail);
            helper.setTo(mail);
            helper.setSubject("북덕북덕 이메일 인증번호");
            String body = "<h2>북덕북덕 가입을 환영합니다!</h2><h3>아래의 인증번호를 입력해 주세요.</h3><h1>" + verificationCodes.get(mail) + "</h1><h3>감사합니다.</h3>";
            helper.setText(body, true);

        } catch (MessagingException e) {
            throw new RuntimeException(e);

        }

        return message;
    }

    @Override
    public boolean verifyCode(String mail, int code) {
        Integer storedCode = verificationCodes.get(mail);
        System.out.println(storedCode);
        System.out.println(code);
        return storedCode != null && storedCode == code;
    }

    @Override
    public CompletableFuture<Integer> sendMail(String mail) {
        MimeMessage message = createMail(mail);
        javaMailSender.send(message);
        return CompletableFuture.completedFuture(verificationCodes.get(mail));
    }
}
