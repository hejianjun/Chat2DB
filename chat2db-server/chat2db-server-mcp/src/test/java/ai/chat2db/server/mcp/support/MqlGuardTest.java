package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.chat2db.server.mcp.config.McpProperties.Mongo;
import ai.chat2db.server.mcp.config.McpProperties.Sql;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MqlGuardTest {

    private MqlGuard guard;
    private McpServerConfigService configService;
    private SqlGuard sqlGuard;

    @BeforeEach
    void setUp() {
        configService = mock(McpServerConfigService.class);
        sqlGuard = mock(SqlGuard.class);
        Mongo config = new Mongo();
        when(configService.getEffectiveMongo()).thenReturn(config);
        when(configService.getEffectiveSql()).thenReturn(new Sql());
        guard = new MqlGuard(configService, sqlGuard);
    }

    @Test
    void acceptsSupportedReadOnlyQueries() {
        assertThat(guard.requireSingleReadOnlyMql("db.orders.find({status: 'OPEN'}).sort({createdAt: -1})"))
            .isEqualTo("db.orders.find({status: 'OPEN'}).sort({createdAt: -1})");
        assertThat(guard.requireSingleReadOnlyMql("db.getCollection('audit-log').countDocuments({active: true})"))
            .contains("countDocuments");
        assertThat(guard.requireSingleReadOnlyMql("db.orders.aggregate([{$match: {active: true}}])"))
            .contains("aggregate");
    }

    @Test
    void rejectsMultipleStatementsWritesAndRunCommand() {
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.a.find({}); db.b.find({})"))
            .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.orders.updateMany({active: true}, {$set: {x: 1}})"))
            .hasMessageContaining("Only find/findOne/countDocuments/aggregate");
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.runCommand({find: 'orders'})"))
            .hasMessageContaining("runCommand");
    }

    @Test
    void rejectsDirectAndNestedWritingAggregationStages() {
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.orders.aggregate([{$out: 'archive'}])"))
            .hasMessageContaining("$out");
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql(
            "db.orders.aggregate([{$facet: {items: [{$project: {x: 1}}, {'$merge': 'archive'}]}}])"))
            .hasMessageContaining("$merge");
    }

    @Test
    void doesNotTreatStageNameInsideStringValueAsDocumentKey() {
        assertThat(guard.requireSingleReadOnlyMql(
            "db.orders.aggregate([{$match: {message: '$out is text'}}])"))
            .contains("aggregate");
    }

    @Test
    void rejectsUnsupportedChainsAndUnbalancedMql() {
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.orders.find({}).deleteMany({})"))
            .hasMessageContaining("Unsupported read-only find modifier");
        assertThatThrownBy(() -> guard.requireSingleReadOnlyMql("db.orders.find({"))
            .hasMessageContaining("Unbalanced");
    }

    @Test
    void inheritsSharedSqlFlagsAndEnforcesProductionRestriction() {
        assertThatThrownBy(() -> guard.requireWriteAllowed(1L)).hasMessageContaining("sql.allow-write=true");
        assertThatThrownBy(() -> guard.requireAdminAllowed(1L)).hasMessageContaining("sql.allow-ddl=true");

        Sql enabled = new Sql();
        enabled.setAllowWrite(true);
        enabled.setAllowDdl(true);
        when(configService.getEffectiveSql()).thenReturn(enabled);

        assertThatCode(() -> guard.requireWriteAllowed(1L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAdminAllowed(1L)).doesNotThrowAnyException();

        when(sqlGuard.isReleaseDatasource(1L)).thenReturn(true);

        assertThatThrownBy(() -> guard.requireWriteAllowed(1L)).hasMessageContaining("production");
        assertThatThrownBy(() -> guard.requireAdminAllowed(1L)).hasMessageContaining("production");
    }
}
