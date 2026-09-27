package ai.chat2db.server.start.exception;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.ConnectInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionErrorTest {

    @Test
    void connectionFailureShowsDataSourceNameInLocalizedMessage() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setAlias("测试数据源");
        connectInfo.setDataSourceId(42L);
        connectInfo.setSsh(new SSHInfo());

        BusinessException error = assertThrows(BusinessException.class,
            () -> new DefaultDBManage().getConnection(connectInfo));

        assertEquals("connection.dataSourceError", error.getCode());
        assertEquals("测试数据源", error.getArgs()[0]);
        assertNotNull(error.getCause());
        String pattern = ResourceBundle.getBundle("i18n.messages", Locale.CHINA).getString(error.getCode());
        String message = MessageFormat.format(pattern, error.getArgs());
        assertTrue(message.contains("测试数据源"));
        assertFalse(message.contains("{0}"));
    }

    @Test
    void missingAliasFallsBackToDataSourceId() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDataSourceId(42L);
        connectInfo.setSsh(new SSHInfo());

        BusinessException error = assertThrows(BusinessException.class,
            () -> new DefaultDBManage().getConnection(connectInfo));

        assertEquals("42", error.getArgs()[0]);
    }
}
