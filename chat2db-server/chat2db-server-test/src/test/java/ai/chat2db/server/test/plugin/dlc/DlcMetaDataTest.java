package ai.chat2db.server.test.plugin.dlc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ai.chat2db.plugin.dlc.DlcMetaData;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlcMetaDataTest {

    private final DlcMetaData metaData = new DlcMetaData();

    @Test
    void shouldReadSparkTableResult() throws SQLException {
        Connection connection = mockConnection(
                "SHOW TABLES",
                new String[]{"namespace", "tableName", "isTemporary"},
                new String[][]{{"vehicle_data_prod", "tbox_original_message", "false"}}
        );

        List<Table> tables = metaData.tables(connection, "vehicle_data_prod", null, null);

        assertEquals(1, tables.size());
        assertEquals("tbox_original_message", tables.get(0).getName());
        assertEquals("vehicle_data_prod", tables.get(0).getDatabaseName());
        assertNull(tables.get(0).getComment());
    }

    @Test
    void shouldReadGovernanceTableResult() throws SQLException {
        String sql = "SHOW TABLES LIKE 'tbox_original_message'";
        Connection connection = mockConnection(
                sql,
                new String[]{"tbl_name", "comment"},
                new String[][]{{"tbox_original_message", "T-Box original data"}}
        );

        List<Table> tables = metaData.tables(
                connection, "vehicle_data_prod", null, "tbox_original_message");

        assertEquals(1, tables.size());
        assertEquals("tbox_original_message", tables.get(0).getName());
        assertEquals("vehicle_data_prod", tables.get(0).getDatabaseName());
        assertEquals("T-Box original data", tables.get(0).getComment());
        verify(connection.createStatement()).execute(sql);
    }

    @Test
    void shouldReadSparkStructuredDescribeResult() throws SQLException {
        String sql = "DESCRIBE tbox_original_message";
        Connection connection = mockConnection(
                sql,
                new String[]{"col_name", "data_type", "comment"},
                new String[][]{
                        {"vin", "string", null},
                        {"acquisition_time", "timestamp", "collection time"},
                        {"# Partitioning", null, null},
                        {"Part 0", "days(acquisition_time)", null}
                }
        );

        List<TableColumn> columns = metaData.columns(
                connection, "vehicle_data_prod", null, "tbox_original_message");

        assertEquals(2, columns.size());
        assertEquals("vin", columns.get(0).getName());
        assertEquals("string", columns.get(0).getDataType());
        assertEquals(1, columns.get(0).getOrdinalPosition());
        assertEquals("acquisition_time", columns.get(1).getName());
        assertEquals("collection time", columns.get(1).getComment());
        verify(connection.createStatement()).execute(sql);
    }

    @Test
    void shouldKeepReadingSparkPartitionFromDescribeResult() throws SQLException {
        String sql = "DESCRIBE tbox_original_message";
        Connection connection = mockConnection(
                sql,
                new String[]{"col_name", "data_type", "comment"},
                new String[][]{
                        {"vin", "string", null},
                        {"# Partitioning", null, null},
                        {"Part 0", "days(acquisition_time)", null}
                }
        );
        Table table = Table.builder().name("tbox_original_message").build();

        metaData.enrichTable(connection, "vehicle_data_prod", null, table);

        assertEquals("days(acquisition_time)", table.getPartition());
        verify(connection.createStatement()).execute(sql);
    }

    @Test
    void shouldReadGovernanceTextDescribeResult() throws SQLException {
        String sql = "DESCRIBE tbox_original_message";
        String describeText = "# col_name            \tdata_type            \tcomment\n"
                + "\n"
                + "vin                  \tstring               \tvehicle identifier\n"
                + "acquisition_time     \ttimestamp            \t\n"
                + "payload              \tstruct<code:string, message:string>\tmessage payload\n";
        Connection connection = mockConnection(
                sql,
                new String[]{"result"},
                new String[][]{{describeText}}
        );

        List<TableColumn> columns = metaData.columns(
                connection, "vehicle_data_prod", null, "tbox_original_message");

        assertEquals(3, columns.size());
        assertEquals("vin", columns.get(0).getName());
        assertEquals("string", columns.get(0).getDataType());
        assertEquals("vehicle identifier", columns.get(0).getComment());
        assertEquals("timestamp", columns.get(1).getDataType());
        assertEquals("struct<code:string, message:string>", columns.get(2).getDataType());
        assertEquals("message payload", columns.get(2).getComment());
        verify(connection.createStatement()).execute(sql);
    }

    private Connection mockConnection(String sql, String[] columnNames, String[][] rows) throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData resultSetMetaData = mock(ResultSetMetaData.class);
        AtomicInteger rowIndex = new AtomicInteger(-1);

        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(sql)).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        when(resultSetMetaData.getColumnCount()).thenReturn(columnNames.length);
        when(resultSetMetaData.getColumnLabel(anyInt())).thenAnswer(invocation -> {
            int columnIndex = invocation.getArgument(0);
            return columnNames[columnIndex - 1];
        });
        when(resultSetMetaData.getColumnName(anyInt())).thenAnswer(invocation -> {
            int columnIndex = invocation.getArgument(0);
            return columnNames[columnIndex - 1];
        });
        when(resultSet.next()).thenAnswer(invocation -> rowIndex.incrementAndGet() < rows.length);
        when(resultSet.getString(anyInt())).thenAnswer(invocation -> {
            int columnIndex = invocation.getArgument(0);
            return rows[rowIndex.get()][columnIndex - 1];
        });
        return connection;
    }
}
