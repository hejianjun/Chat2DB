package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.ssh.SSHManager;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;

import java.util.List;

public final class MongodbConnectionProvider {

    private MongodbConnectionProvider() {
    }

    public static MongodbConnectionContext open(ConnectInfo connectInfo) {
        ConnectionString connectionString = parse(connectInfo);
        MongoClientSettings.Builder settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString);
        applyFormCredential(settings, connectionString, connectInfo);

        Session session = null;
        SSHInfo ssh = connectInfo.getSsh();
        if (ssh != null && ssh.isUse()) {
            if (connectionString.isSrvProtocol()) {
                throw new IllegalArgumentException("MongoDB SRV connection does not support SSH tunneling");
            }
            List<String> hosts = connectionString.getHosts();
            if (hosts.size() != 1) {
                throw new IllegalArgumentException("MongoDB SSH tunneling requires exactly one host");
            }
            ServerAddress remote = new ServerAddress(hosts.get(0));
            ssh.setRHost(remote.getHost());
            ssh.setRPort(Integer.toString(remote.getPort()));
            session = SSHManager.getSSHSession(ssh);
            int localPort = Integer.parseInt(ssh.getLocalPort());
            settings.applyToClusterSettings(builder ->
                    builder.hosts(List.of(new ServerAddress("127.0.0.1", localPort))));
        }

        MongoClient client = MongoClients.create(settings.build());
        return new MongodbConnectionContext(client, session);
    }

    public static void testConnect(ConnectInfo connectInfo) {
        try (MongodbConnectionContext context = open(connectInfo)) {
            String database = databaseName(connectInfo, parse(connectInfo));
            context.client().getDatabase(database).runCommand(new Document("ping", 1));
        }
    }

    public static ConnectionString parse(ConnectInfo connectInfo) {
        if (connectInfo == null || StringUtils.isBlank(connectInfo.getUrl())) {
            throw new IllegalArgumentException("MongoDB url is required");
        }
        String url = connectInfo.getUrl().trim();
        if (!StringUtils.startsWithIgnoreCase(url, "mongodb://")
                && !StringUtils.startsWithIgnoreCase(url, "mongodb+srv://")) {
            throw new IllegalArgumentException("MongoDB url must use mongodb:// or mongodb+srv://");
        }
        return new ConnectionString(url);
    }

    public static String databaseName(ConnectInfo connectInfo, ConnectionString connectionString) {
        if (StringUtils.isNotBlank(connectInfo.getDatabaseName())) {
            return connectInfo.getDatabaseName();
        }
        if (StringUtils.isNotBlank(connectionString.getDatabase())) {
            return connectionString.getDatabase();
        }
        return "admin";
    }

    private static void applyFormCredential(MongoClientSettings.Builder settings, ConnectionString connectionString,
                                            ConnectInfo connectInfo) {
        if (connectionString.getCredential() != null || StringUtils.isBlank(connectInfo.getUser())) {
            return;
        }
        String authenticationDatabase = StringUtils.defaultIfBlank(connectionString.getDatabase(), "admin");
        char[] password = StringUtils.defaultString(connectInfo.getPassword()).toCharArray();
        settings.credential(MongoCredential.createCredential(connectInfo.getUser(), authenticationDatabase, password));
    }

    public record MongodbConnectionContext(MongoClient client, Session session) implements AutoCloseable {

        @Override
        public void close() {
            if (client != null) {
                client.close();
            }
            if (session != null) {
                try {
                    session.delPortForwardingL(Integer.parseInt(session.getPortForwardingL()[0].split(":")[0]));
                } catch (JSchException | RuntimeException e) {
                    // ignore cleanup failures
                }
                session.disconnect();
            }
        }
    }
}
