package com.quhealthy.catalog_service.config;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class CustomAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final Long planId;
    private final String onboardingStatus;
    private final String kycStatus;

    public CustomAuthenticationToken(
            Object principal, // UserId
            Object credentials, // Null (ya autenticado por token)
            Collection<? extends GrantedAuthority> authorities,
            Long planId,
            String onboardingStatus,
            String kycStatus
    ) {
        super(principal, credentials, authorities);
        this.planId = planId;
        this.onboardingStatus = onboardingStatus;
        this.kycStatus = kycStatus;
    }
}