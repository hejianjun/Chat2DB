package ai.chat2db.server.start.config.config;

import ai.chat2db.server.tools.common.util.HttpLogMasking;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.QueryFilter;

/**
 * log config
 *
 * @author Jiaju Zhuang
 */
@Configuration
public class WebLogConfiguration {

    @Bean
    public BodyFilter bodyFilter() {
        return HttpLogMasking.bodyFilter();
    }

    @Bean
    public HeaderFilter headerFilter() {
        return HttpLogMasking.headerFilter();
    }

    @Bean
    public QueryFilter queryFilter() {
        return HttpLogMasking.queryFilter();
    }
}
