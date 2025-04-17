package com.my.bookduck.config.oauth;


import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.config.oauth.provider.GoogleUserInfo;
import com.my.bookduck.config.oauth.provider.NaverUserInfo;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.net.http.HttpRequest;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {
    HttpServletRequest request; // session 값 확인 필요
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        log.info("user request clientRegistration: {}", userRequest.getClientRegistration());
        log.info("user request getAccssToken: {}", userRequest.getAccessToken().getTokenValue());
        OAuth2User oAuth2User = super.loadUser(userRequest);
        log.info("user Attribute: {}", oAuth2User.getAttributes());

        return processOAuthUser(userRequest,oAuth2User);

    }

    private OAuth2User processOAuthUser(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        OAuth2UserInfo oAuth2UserInfo = null;
        if(userRequest.getClientRegistration().getRegistrationId().equals("google")) {
            log.info("구글 로그인");
            oAuth2UserInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        }else if(userRequest.getClientRegistration().getRegistrationId().equals("naver")) {
            log.info("네이버 로그인");
            oAuth2UserInfo = new NaverUserInfo((Map<String, Object>) oAuth2User.getAttributes().get("response"));
        }else{
            log.info("요청 실패");
        }
        log.info("oAuth2UserInfo.getProvider() : " + oAuth2UserInfo.getProvider());
        log.info("oAuth2UserInfo.getProviderId() : " + oAuth2UserInfo.getProviderId());
        Optional<User> userOptional =
                userRepository.findByProviderAndProviderId(oAuth2UserInfo.getProvider(), oAuth2UserInfo.getProviderId());

        log.info("userOptional.isPresent() = {} : "+userOptional.isPresent());
        User user = null;

        if(userOptional.isEmpty()){
            //log.info("진행여부 판단");
            HttpSession session = request.getSession();
            user = user.builder()
                    .loginId(oAuth2UserInfo.getProvider()+"_"+oAuth2UserInfo.getProviderId())
                    //.name(oAuth2UserInfo.getName())
                    .nickName(session.getAttribute("joinNickName").toString()) // 값이 입력 되는지 확인 할 것.
                    .email(oAuth2UserInfo.getEmail())
                    .password("socialLogin")
                    .provider(oAuth2UserInfo.getProvider())
                    .providerId(oAuth2UserInfo.getProviderId())
                    .build();
            userRepository.save(user);
        }else{
            user = userOptional.get();
        }

        return new BDUserDetails(user, oAuth2User.getAttributes());
    }
}
