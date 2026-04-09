package ai.chat2db.server.domain.core.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import ai.chat2db.server.domain.api.service.LoginAttemptService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.LoginAttempt;
import ai.chat2db.server.domain.repository.mapper.LoginAttemptMapper;
import ai.chat2db.server.tools.base.excption.BusinessException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {
    @Value("${security.login.max-attempts:5}")
    private int maxAttempts;
    @Value("${security.login.lock-duration:30}")
    private int lockDurationMinutes;
    @Override
    public void validateAttempt(String clientFingerprint) {
        LoginAttempt attempt = getDbhubLoginAttemptMapper().findByFingerprint(clientFingerprint);
        
        if (attempt != null && attempt.getLockedUntil() != null 
            && attempt.getLockedUntil().after(new Date())) {
            throw new BusinessException("auth.login.locked");
        }
    }
    
    @Override
    public void recordFailedAttempt(String clientFingerprint) {
        LoginAttemptMapper mapper = getDbhubLoginAttemptMapper();
        LoginAttempt attempt = mapper.findByFingerprint(clientFingerprint);
        if (attempt == null) {
            attempt = new LoginAttempt();
            attempt.setClientFingerprint(clientFingerprint);
            attempt.setAttempts(1);
            attempt.setLastAttemptTime(new Date());
            mapper.insert(attempt);
        } else {
            mapper.incrementAttempts(clientFingerprint);
            attempt = mapper.findByFingerprint(clientFingerprint);
        }
        if (attempt.getAttempts() >= maxAttempts) {
            Date lockedUntil = Date.from(Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES));
            attempt.setLockedUntil(lockedUntil);
            mapper.updateById(attempt);
        }
    }
    @Override
    public void clearAttempts(String clientFingerprint) {
        getDbhubLoginAttemptMapper().delete(
            new QueryWrapper<LoginAttempt>()
                .eq("client_fingerprint", clientFingerprint)
        );
    }
    @Override
    public long getRemainingLockTime(String clientFingerprint) {
        LoginAttempt attempt = getDbhubLoginAttemptMapper().findByFingerprint(clientFingerprint);
        if (attempt == null || attempt.getLockedUntil() == null) {
            return 0;
        }
        return Duration.between(Instant.now(), attempt.getLockedUntil().toInstant())
                      .getSeconds() / 60;
    }

    private LoginAttemptMapper getDbhubLoginAttemptMapper() {
        return Dbutils.getMapper(LoginAttemptMapper.class);
    }
}
