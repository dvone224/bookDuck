package com.my.bookduck.config.oauth.provider;

import com.my.bookduck.config.oauth.OAuth2UserInfo;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;

import java.util.Map;

public class NaverUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;
    public NaverUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }


    @Override
    public String getProviderId() {
        return (String)attributes.get("id");
    }

    @Override
    public String getName() {
        return (String)attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String)attributes.get("email");
    }

    @Override
    public String getProvider() {
        return "naver";
    }
}
