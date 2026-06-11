package ai.chat2db.plugin.redis;

import ai.chat2db.spi.redis.RedisCommandMonitor;
import ai.chat2db.spi.ssh.SSHManager;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Redis 命令监控服务。
 * <p>
 * 使用原生 RESP 协议发送 MONITOR 命令，实时输出 Redis 命令流。
 * 独立于 Lettuce 连接，通过原始 Socket 实现监控功能。
 */
class RedisCommandMonitorService implements RedisCommandMonitor {

    @Override
    public void monitor(String databaseName, Consumer<String> lineConsumer, BooleanSupplier running) {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        RedisConnectionProvider.RedisConnectionInfo connectionInfo = RedisConnectionProvider.parse(connectInfo);
        Session session = null;
        String host = connectionInfo.host();
        int port = connectionInfo.port();
        if (connectInfo.getSsh() != null && connectInfo.getSsh().isUse()) {
            connectInfo.getSsh().setRHost(host);
            connectInfo.getSsh().setRPort(Integer.toString(port));
            session = SSHManager.getSSHSession(connectInfo.getSsh());
            host = "127.0.0.1";
            port = Integer.parseInt(connectInfo.getSsh().getLocalPort());
        }
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8));
             OutputStream writer = socket.getOutputStream()) {
            socket.setSoTimeout(1000);
            authenticate(connectInfo, writer, reader);
            selectMonitorDatabase(databaseName, writer, reader);
            writeCommand(writer, "MONITOR");
            expectStatus(reader, "OK");
            while (running.getAsBoolean() && !socket.isClosed()) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        return;
                    }
                    lineConsumer.accept(redactMonitorLine(stripRespSimpleString(line)));
                } catch (java.net.SocketTimeoutException ignored) {
                    // allow cancellation check
                }
            }
        } catch (Exception e) {
            if (running.getAsBoolean()) {
                throw new IllegalStateException("Redis monitor failed: " + e.getMessage(), e);
            }
        } finally {
            closeSshSession(session);
        }
    }

    /**
     * 通过原始 RESP 协议发送 AUTH 命令并验证返回结果。
     */
    private void authenticate(ConnectInfo connectInfo, OutputStream writer, BufferedReader reader)
            throws IOException {
        if (StringUtils.isBlank(connectInfo.getPassword())) {
            return;
        }
        if (StringUtils.isNotBlank(connectInfo.getUser())) {
            writeCommand(writer, "AUTH", connectInfo.getUser(), connectInfo.getPassword());
        } else {
            writeCommand(writer, "AUTH", connectInfo.getPassword());
        }
        expectStatus(reader, "OK");
    }

    /**
     * 为监控连接选择指定数据库。
     */
    private void selectMonitorDatabase(String databaseName, OutputStream writer, BufferedReader reader)
            throws IOException {
        if (StringUtils.isBlank(databaseName)) {
            return;
        }
        writeCommand(writer, "SELECT", databaseName);
        expectStatus(reader, "OK");
    }

    private void writeCommand(OutputStream writer, String... args) throws IOException {
        writer.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String arg : args) {
            String value = StringUtils.defaultString(arg);
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writer.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            writer.write(bytes);
            writer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        writer.flush();
    }

    private void expectStatus(BufferedReader reader, String expected) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Redis connection closed");
        }
        if (line.startsWith("-")) {
            throw new IOException(line.substring(1));
        }
        String value = stripRespSimpleString(line);
        if (!expected.equalsIgnoreCase(value)) {
            throw new IOException("Unexpected Redis response: " + value);
        }
    }

    private String stripRespSimpleString(String line) {
        if (StringUtils.isBlank(line)) {
            return "";
        }
        if (line.charAt(0) == '+' || line.charAt(0) == '$') {
            return line.substring(1);
        }
        return line;
    }

    private String redactMonitorLine(String line) {
        return line.replaceAll("(?i)(\"AUTH\"\\s+(\"[^\"]*\"\\s+)?\")([^\"]*)(\")", "$1(redacted)$4");
    }

    private void closeSshSession(Session session) {
        if (session == null) {
            return;
        }
        try {
            session.delPortForwardingL(Integer.parseInt(session.getPortForwardingL()[0].split(":")[0]));
        } catch (JSchException | RuntimeException e) {
            // ignore
        }
        session.disconnect();
    }
}
