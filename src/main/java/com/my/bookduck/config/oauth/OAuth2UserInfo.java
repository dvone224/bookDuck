package com.my.bookduck.config.oauth;

public interface OAuth2UserInfo {
    String getProviderId();
    String getName();
    String getEmail();
    String getProvider();
}
