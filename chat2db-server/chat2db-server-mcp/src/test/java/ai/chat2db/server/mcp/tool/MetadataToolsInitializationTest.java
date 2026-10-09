package ai.chat2db.server.mcp.tool;

import java.util.List;
import java.util.function.Function;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataToolsInitializationTest {

    @Test
    void toolRegistrationDefersBusinessServicesUntilFirstInvocation() {
        DatabaseService databaseService = mock(DatabaseService.class);
        ConnectionScope connectionScope = mock(ConnectionScope.class);
        when(databaseService.queryAll(any())).thenReturn(List.of());
        when(connectionScope.run(eq("chat2db://datasources/1"), isNull(), isNull(), any()))
            .thenAnswer(invocation -> {
                Function<ResolvedDataSource, String> query = invocation.getArgument(3);
                return query.apply(ResolvedDataSource.builder().dataSourceId(1L).build());
            });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("databaseService", DatabaseService.class, () -> databaseService,
                definition -> definition.setLazyInit(true));
            context.registerBean("tableService", TableService.class, () -> mock(TableService.class),
                definition -> definition.setLazyInit(true));
            context.registerBean(ConnectionScope.class, () -> connectionScope);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(ResultFormatter.class, MetadataTools.class);
            context.refresh();

            MetadataTools tools = context.getBean(MetadataTools.class);
            assertFalse(SyncMcpAnnotationProviders.toolSpecifications(List.of(tools)).isEmpty());
            assertFalse(context.getBeanFactory().containsSingleton("databaseService"));
            assertFalse(context.getBeanFactory().containsSingleton("tableService"));

            tools.listDatabases("chat2db://datasources/1");

            assertTrue(context.getBeanFactory().containsSingleton("databaseService"));
            assertFalse(context.getBeanFactory().containsSingleton("tableService"));
            verify(databaseService).queryAll(any());
        }
    }
}
