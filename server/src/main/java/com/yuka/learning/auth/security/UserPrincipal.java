package com.yuka.learning.auth.security;

import com.yuka.learning.user.entity.User;
import com.yuka.learning.user.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Adapts {@link User} to Spring Security's {@link UserDetails} for the login
 * authentication path. Account-state checks are enforced by the framework's
 * {@code DaoAuthenticationProvider} via the flags below.
 * <p>
 * Authorities are empty until the RBAC phase populates roles/permissions.
 */
public record UserPrincipal(User user) implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != UserStatus.DISABLED;
    }
}
