package ai.chat2db.server.domain.core.converter;

import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.domain.repository.entity.DeprecatedTableDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class DeprecatedTableConverter {

    public abstract DeprecatedTableDO param2do(DeprecatedTableParam param);
}
