package ai.chat2db.server.domain.repository.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import ai.chat2db.server.domain.repository.entity.LoginAttempt;

public interface LoginAttemptMapper extends BaseMapper<LoginAttempt> {
    @Update("UPDATE login_attempt SET attempts = attempts + 1, last_attempt_time = NOW()" +
            " WHERE client_fingerprint = #{clientFingerprint}")
    int incrementAttempts(@Param("clientFingerprint") String clientFingerprint);
    
    @Select("SELECT * FROM login_attempt WHERE client_fingerprint = #{clientFingerprint}")
    LoginAttempt findByFingerprint(String clientFingerprint);
}
