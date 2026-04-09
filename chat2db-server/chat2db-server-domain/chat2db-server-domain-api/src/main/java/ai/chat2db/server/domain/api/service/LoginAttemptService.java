package ai.chat2db.server.domain.api.service;

public interface LoginAttemptService {
    void validateAttempt(String clientFingerprint);
    void recordFailedAttempt(String clientFingerprint);
    void clearAttempts(String clientFingerprint);
    long getRemainingLockTime(String clientFingerprint);
}
