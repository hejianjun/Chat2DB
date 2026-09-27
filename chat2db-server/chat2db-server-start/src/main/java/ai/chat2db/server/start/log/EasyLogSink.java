package ai.chat2db.server.start.log;

import ai.chat2db.server.tools.common.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;

/**
 * log
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Component
public class EasyLogSink implements Sink {

    @Override
    public void write(final Precorrelation precorrelation, final HttpRequest request) {
    }

    @Override
    public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) {
        try {
            printLog(correlation, request, response);
        } catch (Exception e) {
            log.error("记录日志异常", e);
        }
    }

    public void printLog(final Correlation correlation, final HttpRequest request, final HttpResponse response) {
        log.info("http : {}|{}|{}|{}ms", request.getMethod(), LogUtils.cutLog(request.getPath()),
            response.getStatus(), correlation.getDuration().toMillis());
    }

}
