package ai.chat2db.server.mcp.support;

import java.nio.file.Path;
import java.util.Locale;

import ai.chat2db.server.tools.common.util.I18nUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

public final class McpTestSupport {

    private McpTestSupport() {
    }

    public static void initializeMessages() {
        ReloadableResourceBundleMessageSource messages = new ReloadableResourceBundleMessageSource();
        Path bundle = Path.of(System.getProperty("basedir", "."), "..", "chat2db-server-start",
            "src", "main", "resources", "i18n", "messages").toAbsolutePath().normalize();
        messages.setBasename(bundle.toUri().toString());
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(I18nUtils.class, "messageSourceStatic", messages);
        LocaleContextHolder.setLocale(Locale.US);
    }
}
