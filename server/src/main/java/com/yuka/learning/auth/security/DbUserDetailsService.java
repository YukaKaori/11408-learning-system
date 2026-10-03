package com.yuka.learning.auth.security;

import com.yuka.learning.user.mapper.UserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads accounts for the password login path. The identifier may be a username
 * or an email — indistinguishable to the caller. {@code UsernameNotFoundException}
 * is masked as bad credentials by the authentication provider, so login never
 * leaks whether an account exists.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public DbUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        return userMapper.findByUsernameOrEmail(usernameOrEmail)
                .<UserDetails>map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + usernameOrEmail));
    }
}
