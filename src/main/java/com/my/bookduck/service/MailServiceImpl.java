package com.my.bookduck.service;

import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.standard.expression.MessageExpression;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
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
            helper.setTo(senderEmail);
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

    public String findId(String mail) {
        User user = userRepository.findByEmail(mail);
        String result = "";
        if(user.getProvider() == null){
            result = user.getLoginId();
        }else {
            result = user.getProvider();
        }
        return result;
    }

    public void findIdMail(String mail) {
        String id = findId(mail);
        MimeMessage message = javaMailSender.createMimeMessage();

        try{
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"utf-8");
            helper.setFrom(senderEmail);
            helper.setTo(senderEmail);
            helper.setSubject("북덕북덕 아이디 찾기 결과");
            String body = "";
            if(id.equals("naver")||id.equals("google")){
                body = "<h1>북덕북덕을 "+ id +" 로그인을 통해 가입 하셨어요</h1>";
            }else{
                body = "<h2>북덕북덕 가입 아이디 입니다!</h2><h1> ID : " + id + "</h1><h3>감사합니다.</h3>";
            }
            helper.setText(body, true);

        } catch (MessagingException e) {
            throw new RuntimeException(e);

        }

        javaMailSender.send(message);
    }

    @Override
    @Transactional
    public void issuePwMail(String mail) {
        String id = findId(mail);
        MimeMessage message = javaMailSender.createMimeMessage();

        try{
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"utf-8");
            helper.setFrom(senderEmail);
            helper.setTo(senderEmail);
            helper.setSubject("북덕북덕 아이디 확인 및 새 비번 발급");
            String body = "";
            if(id.equals("naver")||id.equals("google")){
                body = "<h1>북덕북덕을 "+ id +" 로그인을 통해 가입 하셨어요</h1>";
            }else{
                User u = userRepository.findByEmail(mail);
                String newPw = createPassword();
                log.info(newPw);
                u.setPassword(passwordEncoder.encode(newPw));
                userRepository.save(u);
                body = "<h2>북덕북덕 가입 아이디와 새로 발급된 비밀번호 입니다!</h2><h1> ID : " + id + "</h1><h1> PW : " + newPw + "</h1><h2>비밀번호는 로그인 후 변경해 주세요.</h2><h3>감사합니다.</h3>";
            }
            helper.setText(body, true);

        } catch (MessagingException e) {
            throw new RuntimeException(e);

        }

        javaMailSender.send(message);
    }

    private String createPassword() {
        String newPw = "";

        for(int i = 0; i < 10; i++){
            int num1 = new Random().nextInt(12);
            if(num1 < 10){
                newPw += num1;
            }else if(num1 == 10){
                int num2 = new Random().nextInt(26) + 65;
                newPw += (char)num2;
            }else if(num1 == 11){
                int num2 = new Random().nextInt(26) + 97;
                newPw += (char)num2;
            }
        }

        return newPw;
    }


}
