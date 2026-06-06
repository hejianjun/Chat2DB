package ai.chat2db.plugin.redis;

import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.ssh.SSHManager;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.Duration;

public final class RedisConnectionProvider {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private RedisConnectionProvider() {
    }

    public static RedisConnectionContext open(ConnectInfo connectInfo) {
        RedisConnectionInfo connectionInfo = parse(connectInfo);
        Session session = null;
        String host = connectionInfo.host();
        int port = connectionInfo.port();
        SSHInfo ssh = connectInfo.getSsh();
        if (ssh != null && ssh.isUse()) {
            ssh.setRHost(host);
            ssh.setRPort(Integer.toString(port));
            session = SSHManager.getSSHSession(ssh);
            host = "127.0.0.1";
            port = Integer.parseInt(ssh.getLocalPort());
        }

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(connectionInfo.database())
                .withTimeout(COMMAND_TIMEOUT);
        if (StringUtils.isNotBlank(connectInfo.getPassword())) {
            if (StringUtils.isNotBlank(connectInfo.getUser())) {
                builder.withAuthentication(connectInfo.getUser(), connectInfo.getPassword().toCharArray());
            } else {
                builder.withPassword(connectInfo.getPassword().toCharArray());
            }
        }

        RedisClient client = RedisClient.create(builder.build());
        StatefulRedisConnection<String, String> connection = client.connect();
        return new RedisConnectionContext(client, connection, session);
    }

    public static void testConnect(ConnectInfo connectInfo) {
        try (RedisConnectionContext context = open(connectInfo)) {
            context.connection().sync().ping();
        }
    }

    public static RedisConnectionInfo parse(ConnectInfo connectInfo) {
        String url = connectInfo.getUrl();
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("Redis url is required");
        }
        URI uri = URI.create(url);
        if (!"redis".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Redis url must use redis://host:port/db");
        }
        if (StringUtils.isBlank(uri.getHost())) {
            throw new IllegalArgumentException("Redis host is required");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        int database = parseDatabase(uri.getPath(), connectInfo.getDatabaseName());
        return new RedisConnectionInfo(uri.getHost(), port, database);
    }

    private static int parseDatabase(String path, String databaseName) {
        String database = StringUtils.stripStart(path, "/");
        if (StringUtils.isBlank(database)) {
            database = databaseName;
        }
        if (StringUtils.isBlank(database)) {
            return 0;
        }
        try {
            int db = Integer.parseInt(database);
            if (db < 0) {
                throw new IllegalArgumentException("Redis database must be greater than or equal to 0");
            }
            return db;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Redis database must be a number", e);
        }
    }

    public record RedisConnectionInfo(String host, int port, int database) {
    }

    public record RedisConnectionContext(
            RedisClient client,
            StatefulRedisConnection<String, String> connection,
            Session session
    ) implements AutoCloseable {

        @Override
        public void close() {
            if (connection != null) {
                connection.close();
            }
            if (client != null) {
                client.shutdown();
            }
            if (session != null) {
                try {
                    session.delPortForwardingL(Integer.parseInt(session.getPortForwardingL()[0].split(":")[0]));
                } catch (JSchException | RuntimeException e) {
                    // ignore
                }
                session.disconnect();
            }
        }
    }
}
