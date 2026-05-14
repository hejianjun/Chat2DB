package ai.chat2db.server.web.api.controller.rdb.converter;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.web.api.controller.rdb.vo.DataGenerationRuleVO;
import org.springframework.beans.BeanUtils;

/**
 * 数据生成规则转换器
 */
public class DataGenerationRuleConverter {

    /**
     * VO转保存参数
     */
    public static DataGenerationRuleSaveParam voToSaveParam(DataGenerationRuleVO vo) {
        if (vo == null) {
            return null;
        }
        DataGenerationRuleSaveParam param = new DataGenerationRuleSaveParam();
        BeanUtils.copyProperties(vo, param);
        return param;
    }

    /**
     * 查询参数转VO
     */
    public static DataGenerationRuleVO paramToVO(DataGenerationRuleQueryParam param) {
        if (param == null) {
            return null;
        }
        DataGenerationRuleVO vo = new DataGenerationRuleVO();
        BeanUtils.copyProperties(param, vo);
        return vo;
    }
}
