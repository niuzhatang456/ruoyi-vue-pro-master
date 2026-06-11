package cn.iocoder.yudao.module.jijian.service.queryhistory;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo.QueryHistoryPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory.QueryHistoryDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.queryhistory.QueryHistoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.QUERY_HISTORY_NOT_EXISTS;

@Service
@Validated
public class QueryHistoryServiceImpl implements QueryHistoryService {

    @Resource
    private QueryHistoryMapper queryHistoryMapper;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public Long createQueryHistory(JijianQueryChatReqVO reqVO, JijianQueryChatRespVO respVO,
                                   String queryType, boolean success, String errorMessage) {
        QueryHistoryDO history = new QueryHistoryDO();
        history.setUserId(SecurityFrameworkUtils.getLoginUserId());
        history.setUserName(SecurityFrameworkUtils.getLoginUserNickname());
        history.setQuestion(reqVO.getMessage());
        history.setAnswer(respVO == null ? null : respVO.getAnswer());
        history.setQueryType(queryType);
        history.setFormType(respVO == null ? reqVO.getFormType() : respVO.getFormType());
        history.setQuerySql(extractSql(respVO));
        history.setQueryResultJson(serializeQueryResult(respVO));
        history.setDatabaseContextMetaJson(toJson(respVO == null ? null : respVO.getDatabaseContextMeta()));
        history.setModelName(respVO == null ? null : respVO.getAiMode());
        history.setSuccess(success);
        history.setErrorMessage(errorMessage);
        queryHistoryMapper.insert(history);
        return history.getId();
    }

    @Override
    public PageResult<QueryHistoryDO> getQueryHistoryPage(QueryHistoryPageReqVO pageReqVO) {
        return queryHistoryMapper.selectPage(pageReqVO,
                isJijianAdmin() ? null : SecurityFrameworkUtils.getLoginUserId());
    }

    @Override
    public QueryHistoryDO getQueryHistory(Long id) {
        QueryHistoryDO history = isJijianAdmin() ? queryHistoryMapper.selectById(id)
                : queryHistoryMapper.selectByIdAndUserId(id, SecurityFrameworkUtils.getLoginUserId());
        if (history == null) {
            throw exception(QUERY_HISTORY_NOT_EXISTS);
        }
        return history;
    }

    @Override
    public void deleteQueryHistory(Long id) {
        QueryHistoryDO history = getQueryHistory(id);
        queryHistoryMapper.deleteById(history.getId());
    }

    private String extractSql(JijianQueryChatRespVO respVO) {
        if (respVO == null || respVO.getSqlTrace() == null) {
            return null;
        }
        return respVO.getSqlTrace().stream()
                .filter(item -> item.getSql() != null)
                .map(item -> item.getSql())
                .collect(Collectors.joining(";\n"));
    }

    private String serializeQueryResult(JijianQueryChatRespVO respVO) {
        if (respVO == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", respVO.getData());
        result.put("summary", respVO.getSummary());
        result.put("columns", respVO.getColumns());
        result.put("pageResult", respVO.getPageResult());
        result.put("metrics", respVO.getMetrics());
        result.put("charts", respVO.getCharts());
        result.put("tables", respVO.getTables());
        result.put("sqlTrace", respVO.getSqlTrace());
        return toJson(result);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isJijianAdmin() {
        return securityFrameworkService.hasAnyRoles("jijian_admin", "super_admin");
    }
}
