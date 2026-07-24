package ai.chat2db.server.domain.core.impl;

import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class DlTemplateServiceImplTest {

    @Test
    void addRowNumberColumnWhenExecutorDoesNotProvideOne() {
        ExecuteResult result = ExecuteResult.builder()
                .success(Boolean.TRUE)
                .sqlType(SqlTypeEnum.SELECT.getCode())
                .pageNo(2)
                .pageSize(2)
                .headerList(List.of(Header.builder().name("_id").dataType(DataTypeEnum.STRING.getCode()).build()))
                .dataList(List.of(List.of("a"), List.of("b")))
                .build();

        DlTemplateServiceImpl.ensureRowNumberColumn(result, "No.");

        Assertions.assertEquals(DataTypeEnum.CHAT2DB_ROW_NUMBER.getCode(),
                result.getHeaderList().get(0).getDataType());
        Assertions.assertEquals(List.of("3", "a"), result.getDataList().get(0));
        Assertions.assertEquals(List.of("4", "b"), result.getDataList().get(1));
    }

    @Test
    void keepExistingRowNumberColumnUnchanged() {
        Header rowNumberHeader = Header.builder()
                .name("No.")
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER.getCode())
                .build();
        List<Header> headers = new ArrayList<>(List.of(rowNumberHeader));
        List<List<String>> rows = new ArrayList<>(List.of(new ArrayList<>(List.of("1"))));
        ExecuteResult result = ExecuteResult.builder()
                .success(Boolean.TRUE)
                .sqlType(SqlTypeEnum.SELECT.getCode())
                .pageNo(1)
                .pageSize(200)
                .headerList(headers)
                .dataList(rows)
                .build();

        DlTemplateServiceImpl.ensureRowNumberColumn(result, "No.");

        Assertions.assertSame(headers, result.getHeaderList());
        Assertions.assertSame(rows, result.getDataList());
    }
}
