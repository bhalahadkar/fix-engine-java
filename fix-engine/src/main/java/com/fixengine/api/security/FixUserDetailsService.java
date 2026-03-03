package com.fixengine.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FixUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT username, password_hash, role, enabled FROM fix_users WHERE username = ?",
                (rs, rowNum) -> User.builder()
                        .username(rs.getString("username"))
                        .password(rs.getString("password_hash"))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rs.getString("role"))))
                        .disabled(!rs.getBoolean("enabled"))
                        .build(),
                username
            );
        } catch (Exception e) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }
}
