package ai.chat2db.server.domain.repository.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("login_attempt")
public class LoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientFingerprint;
    private Integer attempts;
    private Date lastAttemptTime;
    private Date lockedUntil;
}
