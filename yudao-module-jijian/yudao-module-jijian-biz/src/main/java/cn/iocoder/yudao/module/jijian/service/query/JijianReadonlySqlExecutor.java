package cn.iocoder.yudao.module.jijian.service.query;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 只读 SQL 执行器：使用当前数据源以只读事务执行 SELECT 语句，返回结构化结果。
 * 调用方必须先通过 JijianSqlSafetyValidator 校验 SQL 安全性后再调用此类。
 */
@Slf4j
@Service
public class JijianReadonlySqlExecutor {

    /** 单次查询最大返回行数（保护模型上下文）。聚合查询结果行数远小于此值。 */
    private static final int MAX_ROWS = 1000;

    /** 查询超时秒数 */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public QueryResult execute(String sql) {
        log.info("[JijianSqlExecutor] executing: {}", sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
        try {
            jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            boolean truncated = rows.size() > MAX_ROWS;
            List<Map<String, Object>> returnedRows = truncated ? rows.subList(0, MAX_ROWS) : rows;

            List<String> columns = returnedRows.isEmpty()
                    ? Collections.emptyList()
                    : List.copyOf(returnedRows.get(0).keySet());

            QueryResult result = new QueryResult();
            result.setSuccess(true);
            result.setExecutedSql(sql);
            result.setColumns(columns);
            result.setRows(returnedRows);
            result.setTotalRowCount(rows.size());
            result.setReturnedRowCount(returnedRows.size());
            result.setTruncated(truncated);
            return result;
        } catch (Exception e) {
            log.warn("[JijianSqlExecutor] SQL execution failed: {}", e.getMessage());
            QueryResult result = new QueryResult();
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setExecutedSql(sql);
            result.setColumns(Collections.emptyList());
            result.setRows(Collections.emptyList());
            result.setTotalRowCount(0);
            result.setReturnedRowCount(0);
            result.setTruncated(false);
            return result;
        }
    }

    @Data
    public static class QueryResult {
        private boolean success;
        private String errorMessage;
        private String executedSql;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private int totalRowCount;
        private int returnedRowCount;
        private boolean truncated;
    }
}
