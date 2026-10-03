package com.yuka.learning.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.user.entity.User;
import com.yuka.learning.user.entity.UserStatus;
import com.yuka.learning.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local-development convenience only ({@code dev} profile): registration does
 * not exist until a later phase, so seed one account to log in with. The hash
 * is produced by the real {@link PasswordEncoder} — no hash literals in SQL,
 * and nothing here ever runs in prod.
 */
@Slf4j
@Component
@Profile("dev")
public class DevUserSeeder implements ApplicationRunner {

    private static final String DEV_USERNAME = "demo";
    private static final String DEV_PASSWORD = "Demo123456";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, DEV_USERNAME)) > 0;
        if (exists) {
            return;
        }
        User user = new User();
        user.setUsername(DEV_USERNAME);
        user.setEmail("demo@ai-learning.local");
        user.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        user.setNickname("Demo");
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);
        log.info("Seeded dev user '{}' with password '{}' (dev profile only)", DEV_USERNAME, DEV_PASSWORD);
    }
}
