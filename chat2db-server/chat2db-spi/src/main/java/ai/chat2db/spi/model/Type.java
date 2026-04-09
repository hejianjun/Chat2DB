package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 表示数据库支持的数据类型信息。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Type {

    /**
     * 数据类型的名称（如 VARCHAR, INTEGER 等）。
     */
    @JsonAlias("TYPE_NAME")
    private String typeName;

    /**
     * 对应的 SQL 类型（如 12 对应 VARCHAR, 4 对应 INTEGER 等）。
     */
    @JsonAlias("DATA_TYPE")
    private Integer dataType;

    /**
     * 数据类型的精度（对于数值类型，表示数字的总位数；对于字符类型，表示字符的最大长度）。
     */
    @JsonAlias("PRECISION")
    private Integer precision;

    /**
     * 用于表示该类型的字面量前缀（如 N' 用于 Unicode 字符串）。
     */
    @JsonAlias("LITERAL_PREFIX")
    private String literalPrefix;

    /**
     * 用于表示该类型的字面量后缀（如 '）。
     */
    @JsonAlias("LITERAL_SUFFIX")
    private String literalSuffix;

    /**
     * 创建该类型时所需的参数（如长度）。
     */
    @JsonAlias("CREATE_PARAMS")
    private String createParams;

    /**
     * 指示该类型是否可以为 NULL。
     */
    @JsonAlias("NULLABLE")
    private Short nullable;

    /**
     * 指示该类型是否区分大小写。
     */
    @JsonAlias("CASE_SENSITIVE")
    private Boolean caseSensitive;

    /**
     * 指示该类型是否可以用于搜索。
     */
    @JsonAlias("SEARCHABLE")
    private Short searchable;

    /**
     * 指示该类型是否支持无符号值。
     */
    @JsonAlias("UNSIGNED_ATTRIBUTE")
    private Boolean unsignedAttribute;

    /**
     * 指示该类型是否具有固定的精度和标度。
     */
    @JsonAlias("FIXED_PREC_SCALE")
    private Boolean fixedPrecScale;

    /**
     * 指示该类型是否支持自动递增。
     */
    @JsonAlias("AUTO_INCREMENT")
    private Boolean autoIncrement;

    /**
     * 本地数据类型的名称。
     */
    @JsonAlias("LOCAL_TYPE_NAME")
    private String localTypeName;

    /**
     * 数据类型的最小标度。
     */
    @JsonAlias("MINIMUM_SCALE")
    private Short minimumScale;

    /**
     * 数据类型的最大标度。
     */
    @JsonAlias("MAXIMUM_SCALE")
    private Short maximumScale;

    /**
     * SQL 数据类型的值。
     */
    @JsonAlias("SQL_DATA_TYPE")
    private Integer sqlDataType;

    /**
     * SQL 日期时间子类型的值。
     */
    @JsonAlias("SQL_DATETIME_SUB")
    private Integer sqlDatetimeSub;

    /**
     * 数值精度的基数。
     */
    @JsonAlias("NUM_PREC_RADIX")
    private Integer numPrecRadix;



//    TYPE_NAME String => Type name
//    DATA_TYPE int => SQL data type from java.sql.Types
//    PRECISION int => maximum precision
//    LITERAL_PREFIX String => prefix used to quote a literal (may be null)
//    LITERAL_SUFFIX String => suffix used to quote a literal (may be null)
//    CREATE_PARAMS String => parameters used in creating the type (may be null)
//    NULLABLE short => can you use NULL for this type.
//            typeNoNulls - does not allow NULL values
//    typeNullable - allows NULL values
//    typeNullableUnknown - nullability unknown
//    CASE_SENSITIVE boolean=> is it case sensitive.
//            SEARCHABLE short => can you use "WHERE" based on this type:
//    typePredNone - No support
//    typePredChar - Only supported with WHERE .. LIKE
//    typePredBasic - Supported except for WHERE .. LIKE
//    typeSearchable - Supported for all WHERE ..
//    UNSIGNED_ATTRIBUTE boolean => is it unsigned.
//            FIXED_PREC_SCALE boolean => can it be a money value.
//    AUTO_INCREMENT boolean => can it be used for an auto-increment value.
//    LOCAL_TYPE_NAME String => localized version of type name (may be null)
//    MINIMUM_SCALE short => minimum scale supported
//    MAXIMUM_SCALE short => maximum scale supported
//    SQL_DATA_TYPE int => unused
//    SQL_DATETIME_SUB int => unused
//    NUM_PREC_RADIX int => usually 2 or 10
}
