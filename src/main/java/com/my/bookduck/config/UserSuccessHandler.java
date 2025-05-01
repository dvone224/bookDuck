package com.my.bookduck.config;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserSuccessHandler implements AuthenticationSuccessHandler {
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String rememberId = request.getParameter("rememberid");
        String saveId = request.getParameter("username");
        log.info("rememberId: {}", rememberId);
        log.info("saveId: {}", saveId);

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        BDUserDetails userDetails = (BDUserDetails) oAuth2User;
        log.info("UserDetails: {}", userDetails);
        log.info("oAuth2User: {}", oAuth2User);
        User user = userDetails.getUser();
        log.info("user: {}", user);
        log.info("user.getProvider: {}", user.getProvider());
        log.info("user.getProviderId: {}", user.getProviderId());
        if(user.getNickName() != null) {

            if(rememberId != null && rememberId.equals("on")&& user.getProvider() == null) {
                Cookie cookie = new Cookie("saveId", saveId);
                cookie.setMaxAge(60*60*24*7);
                cookie.setPath("/");
                response.addCookie(cookie);
            }else{
                Cookie cookie = new Cookie("saveId", "");
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
            }

            response.sendRedirect("/logininfo");

        }else{
            Cookie cookie = new Cookie("saveId", "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
            response.sendRedirect("/addNickname");
        }
    }
}
