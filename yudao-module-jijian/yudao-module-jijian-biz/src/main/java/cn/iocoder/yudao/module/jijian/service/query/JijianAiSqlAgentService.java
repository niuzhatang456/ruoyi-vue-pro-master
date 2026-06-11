package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAnalysisTableVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO.JijianChartSeriesVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianDatabaseContextMetaVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianMetricVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianSqlTraceVO;
import cn.iocoder.yudao.module.jijian.framework.JijianProperties;
import cn.iocoder.yudao.module.jijian.util.JijianPersonNameUtils;
import cn.iocoder.yudao.module.jijian.service.query.JijianReadonlySqlExecutor.QueryResult;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianQuerySchemaProvider;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianSqlSafetyValidator;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianSqlSafetyValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DeepSeek SQL Agent 服务。
 *
 * <p>工作流程：
 * <ol>
 *   <li>向 DeepSeek 提供数据库 schema 和用户问题</li>
 *   <li>DeepSeek 返回 {"action":"query","purpose":"...","sql":"SELECT ..."}</li>
 *   <li>后端校验 SQL 安全性，执行只读查询，将结果返回给 DeepSeek</li>
 *   <li>重复 2-3 步，最多 {@link #MAX_QUERY_ROUNDS} 轮</li>
 *   <li>DeepSeek 返回 {"action":"answer","answer":"...","metrics":[...],"charts":[...],"tables":[...]}</li>
 *   <li>后端解析结构化结果，组装 {@link JijianQueryChatRespVO} 返回前端</li>
 * </ol>
 *
 * <p><b>Fallback 机制</b>：当 DeepSeek 不可用时，后端基于确定性预查结果生成基础回答，
 * 不再仅返回"AI 服务失败"错误。
 */
@Slf4j
@Service
public class JijianAiSqlAgentService {

    /** 最大 SQL 查询轮数，防止死循环 */
    private static final int MAX_QUERY_ROUNDS = 6;

    private static final DateTimeFormatter TRACE_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 每轮单次返回给 DeepSeek 的最大行数（避免超出模型上下文） */
    private static final int MAX_ROWS_TO_MODEL = 500;

    @Resource
    private JijianQuerySchemaProvider schemaProvider;
    @Resource
    private JijianSqlSafetyValidator sqlSafetyValidator;
    @Resource
    private JijianReadonlySqlExecutor sqlExecutor;
    @Resource
    private JijianProperties jijianProperties;
    @Resource
    private ObjectMapper objectMapper;

    public JijianQueryChatRespVO agentChat(String conversationId, String userMessage,
                                            List<Map<String, Object>> history) {
        log.info("[JijianRuntimeMarker] 2026-06-query-v6-fallback JijianAiSqlAgentService.agentChat loaded");
        long totalStart = System.currentTimeMillis();

        JijianQueryChatRespVO resp = new JijianQueryChatRespVO();
        resp.setConversationId(conversationId != null ? conversationId : UUID.randomUUID().toString());
        resp.setAiMode("DEEPSEEK_SQL_AGENT");

        DeepSeekConfig cfg = buildConfig();

        List<JijianSqlTraceVO> sqlTrace = new ArrayList<>();
        List<Map<String, String>> messages = new ArrayList<>();
        long sqlElapsedTotal = 0L;
        long deepSeekElapsedTotal = 0L;
        int sqlRoundCount = 0;  // 包含所有 SQL 执行（预查 + DeepSeek 驱动）

        // ── Intent 识别：从用户问题中提取姓名/编号 ──
        long intentStart = System.currentTimeMillis();
        JijianPersonNameUtils.ParseResult preResolvedPerson = JijianPersonNameUtils.parseFromQuery(userMessage);
        log.info("[SqlAgent] preResolvedPerson={}", preResolvedPerson);
        long intentElapsed = System.currentTimeMillis() - intentStart;

        // system prompt
        messages.add(msg("system", buildSystemPrompt()));

        // 历史注入（最多 4 条）
        if (history != null) {
            int start = Math.max(0, history.size() - 4);
            for (int i = start; i < history.size(); i++) {
                Map<String, Object> h = history.get(i);
                String role = String.valueOf(h.getOrDefault("role", "user"));
                Object rawContent = h.get("content");
                // 历史 content 必须是 String，Map/List 要序列化
                String content;
                if (rawContent == null) {
                    content = "";
                } else if (rawContent instanceof String) {
                    content = (String) rawContent;
                } else {
                    content = toJson(rawContent);
                }
                if (!content.isBlank()) {
                    messages.add(msg(role, content));
                }
            }
        }

        // ── 人员上下文预注入 ──
        String preResolvedContextJson = null;
        if (preResolvedPerson != null && (preResolvedPerson.name != null || preResolvedPerson.employeeNo != null)) {
            long sqlStart = System.currentTimeMillis();
            preResolvedContextJson = preResolvePersonContext(preResolvedPerson, sqlTrace);
            sqlElapsedTotal += System.currentTimeMillis() - sqlStart;
            if (preResolvedContextJson != null) {
                sqlRoundCount++;  // 预查计入 sqlRoundCount
                log.info("[SqlAgent] preResolvedContext injected, length={}", preResolvedContextJson.length());
            }
        }

        // 用户问题（附加表提示 + 人员提示）
        String userMessageWithHint = appendPersonHintIfNeeded(userMessage);
        userMessageWithHint = appendTableHintIfNeeded(userMessageWithHint, userMessage);
        messages.add(msg("user", userMessageWithHint));

        if (preResolvedContextJson != null) {
            messages.add(msg("user", preResolvedContextJson));
        }

        // ── 最近导入数据查询 ──
        long recentStart = System.currentTimeMillis();
        String recentImportContextJson = buildRecentImportContextIfNeeded(userMessage, sqlTrace);
        sqlElapsedTotal += System.currentTimeMillis() - recentStart;
        if (recentImportContextJson != null) {
            sqlRoundCount++;  // 预查计入 sqlRoundCount
            messages.add(msg("user", recentImportContextJson));
        }

        // ── 聚合预查（缺勤/部门等无人员信息的统计类问题）──
        long aggrStart = System.currentTimeMillis();
        String aggrContextJson = buildAggregationContextIfNeeded(userMessage, sqlTrace);
        sqlElapsedTotal += System.currentTimeMillis() - aggrStart;
        if (aggrContextJson != null) {
            sqlRoundCount++;  // 预查计入 sqlRoundCount
            messages.add(msg("user", aggrContextJson));
        }

        String finalAnswer = null;
        List<JijianMetricVO> finalMetrics = Collections.emptyList();
        List<JijianChartVO> finalCharts = Collections.emptyList();
        List<JijianAnalysisTableVO> finalTables = Collections.emptyList();

        // ── 若 DeepSeek 不可用，先检查能否直接用预查数据回答 ──
        boolean deepSeekEnabled = cfg.enabled && hasUsableKey(cfg.apiKey);
        if (!deepSeekEnabled) {
            finalAnswer = generateFallbackAnswer(userMessage, preResolvedContextJson, recentImportContextJson, aggrContextJson, sqlTrace);
            if (finalAnswer == null) {
                finalAnswer = "AI 分析服务未启用，请配置 DeepSeek API Key 后重试。如需查询特定人员或最近导入数据，请确认问题包含人员姓名或[最近导入/刚刚导入]等关键词。";
            }
        } else {
            // ── DeepSeek SQL Agent 主循环 ──
            // 发送前校验所有 messages 的 content 均为非空字符串
            List<Map<String, String>> normalizedMessages = normalizeMessagesForDeepSeek(messages);
            String requestBodyForDebug = null;
            try {
                requestBodyForDebug = objectMapper.writeValueAsString(buildDeepSeekBody(cfg, normalizedMessages));
            } catch (Exception e) {
                log.warn("[SqlAgent] failed to serialize request body for debug: {}", e.getMessage());
            }
            if (requestBodyForDebug != null) {
                log.info("[SqlAgent] DeepSeek request: msgCount={} bodyBytes={} first200={}",
                        normalizedMessages.size(),
                        requestBodyForDebug.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        requestBodyForDebug.substring(0, Math.min(200, requestBodyForDebug.length())));
            }

            for (int round = 0; round < MAX_QUERY_ROUNDS; round++) {
                String rawReply = null;
                Exception dsException = null;
                long dsStart = System.currentTimeMillis();
                try {
                    rawReply = callDeepSeek(cfg, normalizedMessages);
                } catch (Exception e) {
                    dsException = e;
                } finally {
                    deepSeekElapsedTotal += System.currentTimeMillis() - dsStart;
                }

                if (dsException != null) {
                    log.warn("[SqlAgent] DeepSeek call failed at round {}: {}", round, dsException.getMessage());
                    // DeepSeek 失败时尝试用预查数据回答
                    finalAnswer = generateFallbackAnswer(userMessage, preResolvedContextJson, recentImportContextJson, aggrContextJson, sqlTrace);
                    if (finalAnswer == null) {
                        finalAnswer = "AI 分析服务调用失败（" + dsException.getMessage() + "），请稍后重试。";
                    }
                    break;
                }

                normalizedMessages.add(msg("assistant", rawReply));

                JsonNode replyNode = parseJson(stripCodeFence(rawReply));
                if (replyNode == null) {
                    finalAnswer = rawReply.trim();
                    break;
                }

                String action = textOrNull(replyNode.path("action"));

                if ("query".equals(action)) {
                    sqlRoundCount++;
                    String purpose = textOrNull(replyNode.path("purpose"));
                    String sql = textOrNull(replyNode.path("sql"));
                    if (sql == null || sql.isBlank()) {
                        normalizedMessages.add(msg("user", "{\"error\":\"你返回了 action=query 但 sql 字段为空，请重新生成 SQL\"}"));
                        continue;
                    }

                    // 安全校验（含自动 deleted=0 注入）
                    ValidationResult vr = sqlSafetyValidator.validate(sql);
                    if (!vr.valid) {
                        log.warn("[SqlAgent] SQL rejected: {} | reason: {}", sql, vr.message);
                        JijianSqlTraceVO trace = new JijianSqlTraceVO();
                        trace.setPurpose(purpose != null ? purpose : "（未说明）");
                        trace.setSql(sql);
                        trace.setRowCount(0);
                        trace.setError("[blocked] SQL 安全校验失败: " + vr.message);
                        sqlTrace.add(trace);
                        normalizedMessages.add(msg("user", "{\"error\":\"SQL 安全校验失败: " + vr.message
                                + "，请修正后重新生成（注意：所有业务表查询必须添加 deleted = 0 条件）\"}"));
                        continue;
                    }

                    // 执行只读 SQL（计时）
                    long sqlExecStart = System.currentTimeMillis();
                    QueryResult qr = sqlExecutor.execute(vr.sanitizedSql);
                    long sqlExecMs = System.currentTimeMillis() - sqlExecStart;
                    sqlElapsedTotal += sqlExecMs;

                    JijianSqlTraceVO trace = new JijianSqlTraceVO();
                    trace.setPurpose(purpose != null ? purpose : "（未说明）");
                    trace.setSql(vr.sanitizedSql);
                    trace.setRowCount(qr.getTotalRowCount());
                    trace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
                    trace.setSource("current_database");
                    if (!qr.isSuccess()) {
                        trace.setError("执行失败: " + qr.getErrorMessage());
                        sqlTrace.add(trace);
                        normalizedMessages.add(msg("user", toJson(Map.of(
                                "sqlResult", Map.of(
                                        "error", "SQL 执行失败: " + qr.getErrorMessage(),
                                        "hint", "请检查 SQL 语法和字段名是否正确，所有业务表必须加 deleted = 0"
                                )
                        ))));
                        continue;
                    }

                    sqlTrace.add(trace);

                    // 零结果兜底
                    if (qr.getTotalRowCount() == 0
                            && preResolvedPerson != null
                            && (preResolvedPerson.name != null || preResolvedPerson.employeeNo != null)) {
                        long fbStart = System.currentTimeMillis();
                        String fallbackJson = fallbackPersonQuery(preResolvedPerson, vr.sanitizedSql, sqlTrace);
                        sqlElapsedTotal += System.currentTimeMillis() - fbStart;
                        if (fallbackJson != null) {
                            log.info("[SqlAgent] zeroResult fallback triggered");
                            normalizedMessages.add(msg("user", fallbackJson));
                            continue;
                        }
                    }

                    String resultJson = buildSqlResultJson(qr);
                    normalizedMessages.add(msg("user", resultJson));
                    log.info("[SqlAgent] round={} purpose={} rowCount={} sqlMs={}",
                            round, purpose, qr.getTotalRowCount(), sqlExecMs);

                } else if ("answer".equals(action)) {
                    finalAnswer = textOrNull(replyNode.path("answer"));
                    if (finalAnswer == null || finalAnswer.isBlank()) {
                        finalAnswer = rawReply.trim();
                    }
                    finalMetrics = parseMetrics(replyNode.path("metrics"));
                    finalCharts = parseCharts(replyNode.path("charts"));
                    finalTables = parseTables(replyNode.path("tables"));
                    break;
                } else {
                    finalAnswer = rawReply.trim();
                    break;
                }
            }

            // 所有轮次结束后仍无 answer，强制索取
            if (finalAnswer == null) {
                long dsStart = System.currentTimeMillis();
                try {
                    normalizedMessages.add(msg("user",
                            "{\"instruction\":\"请基于以上所有查询结果，立刻给出最终分析结论，格式为 {\\\"action\\\":\\\"answer\\\",\\\"answer\\\":\\\"...\\\",\\\"metrics\\\":[],\\\"charts\\\":[],\\\"tables\\\":[]}\"}"));
                    String rawReply = callDeepSeek(cfg, normalizedMessages);
                    deepSeekElapsedTotal += System.currentTimeMillis() - dsStart;
                    JsonNode replyNode = parseJson(stripCodeFence(rawReply));
                    if (replyNode != null && "answer".equals(textOrNull(replyNode.path("action")))) {
                        finalAnswer = textOrNull(replyNode.path("answer"));
                        finalMetrics = parseMetrics(replyNode.path("metrics"));
                        finalCharts = parseCharts(replyNode.path("charts"));
                        finalTables = parseTables(replyNode.path("tables"));
                    } else {
                        finalAnswer = rawReply != null ? rawReply.trim() : "分析完成，请查看下方查询记录。";
                    }
                } catch (Exception e) {
                    deepSeekElapsedTotal += System.currentTimeMillis() - dsStart;
                    finalAnswer = generateFallbackAnswer(userMessage, preResolvedContextJson, recentImportContextJson, aggrContextJson, sqlTrace);
                    if (finalAnswer == null) finalAnswer = "分析完成，请查看下方 SQL 查询记录。";
                }
            }
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;
        log.info("[SqlAgent] DONE totalMs={} intentMs={} sqlMs={} deepSeekMs={} rounds={}",
                totalElapsed, intentElapsed, sqlElapsedTotal, deepSeekElapsedTotal, sqlRoundCount);

        List<String> tablesUsed = new ArrayList<>();
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        List<String> knownTables = List.of(
                "jijian_compensatory_leave", "jijian_leave_health", "jijian_leave_personal",
                "jijian_business_trip", "jijian_attendance_daily", "jijian_property",
                "jijian_lessee", "jijian_lease_contract", "jijian_canteen_supplier");
        for (JijianSqlTraceVO t : sqlTrace) {
            if (t.getError() == null) {
                String lowerSql = t.getSql() == null ? "" : t.getSql().toLowerCase();
                boolean matched = false;
                for (String table : knownTables) {
                    if (lowerSql.contains(table)) {
                        if (!tablesUsed.contains(table)) {
                            tablesUsed.add(table);
                        }
                        rowCounts.merge(table, t.getRowCount(), Integer::sum);
                        matched = true;
                    }
                }
                if (!matched) {
                    rowCounts.merge("query_" + sqlTrace.indexOf(t), t.getRowCount(), Integer::sum);
                }
            }
        }

        JijianDatabaseContextMetaVO meta = JijianDatabaseContextMetaVO.builder()
                .tablesUsed(tablesUsed)
                .rowCounts(rowCounts)
                .dataSource("local_database_readonly_sql")
                .sensitiveFieldsRemoved(true)
                .truncated(sqlTrace.stream().anyMatch(t -> t.getRowCount() > MAX_ROWS_TO_MODEL))
                .timeRange("由 AI 自主确定")
                .build();

        resp.setAnswer(finalAnswer != null ? finalAnswer : "分析完成。");
        resp.setMetrics(finalMetrics);
        resp.setCharts(finalCharts);
        resp.setTables(finalTables);
        resp.setSqlTrace(sqlTrace);
        resp.setDatabaseContextMeta(meta);
        resp.setData(Collections.emptyMap());
        resp.setSummary(Collections.emptyMap());
        resp.setTotalElapsedMs(totalElapsed);
        resp.setIntentElapsedMs(intentElapsed);
        resp.setSqlElapsedMs(sqlElapsedTotal);
        resp.setDeepSeekElapsedMs(deepSeekElapsedTotal);
        resp.setSqlRoundCount(sqlRoundCount);
        return resp;
    }

    // ===== Fallback 回答生成（DeepSeek 不可用时基于预查数据作答）=====

    /**
     * 当 DeepSeek 不可用时，尝试基于后端预查结果生成可读回答。
     * 优先级：聚合预查 > 最近导入 > 人员预查。
     */
    private String generateFallbackAnswer(String userMessage,
                                           String preResolvedContextJson,
                                           String recentImportContextJson,
                                           String aggrContextJson,
                                           List<JijianSqlTraceVO> sqlTrace) {
        // 1. 聚合查询（缺勤/部门）
        if (aggrContextJson != null) {
            return buildFallbackFromAggr(userMessage, aggrContextJson);
        }
        // 2. 最近导入
        if (recentImportContextJson != null) {
            return buildFallbackFromRecentImport(userMessage, recentImportContextJson);
        }
        // 3. 人员预查
        if (preResolvedContextJson != null) {
            return buildFallbackFromPerson(userMessage, preResolvedContextJson);
        }
        return null;
    }

    private String buildFallbackFromPerson(String userMessage, String contextJson) {
        try {
            JsonNode node = objectMapper.readTree(contextJson);
            String table = node.path("table").asText("");
            int totalRows = node.path("totalRowCount").asInt(0);
            JsonNode rows = node.path("rows");

            if (totalRows == 0 || !rows.isArray() || rows.size() == 0) {
                return "未在数据库中找到相关人员的记录。";
            }

            StringBuilder sb = new StringBuilder();

            // 疗休养假（leave_health）
            if (table.contains("leave_health")) {
                sb.append("根据数据库记录，查询结果如下：\n\n");
                for (JsonNode row : rows) {
                    String name = row.path("applicant_name").asText("-");
                    String start = row.path("start_time").asText("-");
                    String end = row.path("end_time").asText("-");
                    String days = row.path("leave_days").asText("-");
                    String location = row.path("leave_location").asText("-");
                    sb.append("- 姓名：").append(name)
                      .append("，疗休养天数：").append(days).append(" 天")
                      .append("，开始时间：").append(start.length() > 10 ? start.substring(0, 10) : start)
                      .append("，结束时间：").append(end.length() > 10 ? end.substring(0, 10) : end)
                      .append("，地点：").append(location).append("\n");
                }
                sb.append("\n共找到 ").append(totalRows).append(" 条记录。");
                return sb.toString();
            }

            // 考勤（attendance_daily）
            if (table.contains("attendance_daily")) {
                sb.append("根据数据库记录，考勤查询结果如下：\n\n");
                for (JsonNode row : rows) {
                    String name = row.path("employee_name").asText("-");
                    String date = row.path("attendance_date").asText(
                            row.path("checkin_time").asText("-"));
                    String checkinResult = row.path("checkin_result").asText("-");
                    String checkinLoc = row.path("checkin_location").asText("-");
                    String checkoutResult = row.path("checkout_result").asText("-");
                    sb.append("- 姓名：").append(name)
                      .append("，日期：").append(date)
                      .append("，上班打卡：").append(checkinResult)
                      .append("（地点：").append(checkinLoc).append("）")
                      .append("，下班打卡：").append(checkoutResult).append("\n");
                }
                sb.append("\n共找到 ").append(totalRows).append(" 条记录。");
                return sb.toString();
            }

            // 出差（business_trip）
            if (table.contains("business_trip")) {
                sb.append("根据数据库记录，出差查询结果如下：\n\n");
                for (JsonNode row : rows) {
                    String name = row.path("applicant_name").asText("-");
                    String reason = row.path("trip_reason").asText("-");
                    String from = row.path("departure_place").asText("-");
                    String to = row.path("destination").asText("-");
                    String days = row.path("trip_days").asText("-");
                    sb.append("- 姓名：").append(name)
                      .append("，事由：").append(reason)
                      .append("，出发地：").append(from)
                      .append("，目的地：").append(to)
                      .append("，天数：").append(days).append("\n");
                }
                sb.append("\n共找到 ").append(totalRows).append(" 条记录。");
                return sb.toString();
            }

            // 通用格式
            sb.append("查询到 ").append(totalRows).append(" 条相关记录（表：").append(table).append("）。");
            return sb.toString();

        } catch (Exception e) {
            log.warn("[SqlAgent] buildFallbackFromPerson failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildFallbackFromRecentImport(String userMessage, String contextJson) {
        try {
            JsonNode node = objectMapper.readTree(contextJson);
            JsonNode batches = node.path("recentBatches");
            JsonNode dataRows = node.path("latestBatchData");
            String batchTable = node.path("latestBatchTable").asText("");
            int batchRowCount = node.path("latestBatchRowCount").asInt(0);

            if (!batches.isArray() || batches.size() == 0) {
                return "未找到最近已确认的导入记录。";
            }

            StringBuilder sb = new StringBuilder();
            JsonNode latest = batches.get(0);
            String formType = latest.path("form_type").asText("未知类型");
            String batchId = latest.path("id").asText("-");
            String createTime = latest.path("create_time").asText("-");

            // 是否问总数
            boolean askCount = userMessage.contains("多少条") || userMessage.contains("几条") || userMessage.contains("数量");
            // 是否问人员名单
            boolean askPersons = userMessage.contains("哪些人") || userMessage.contains("名单") || userMessage.contains("人员");

            if (askCount) {
                sb.append("最近一次导入批次（ID：").append(batchId)
                  .append("，类型：").append(formType)
                  .append("，时间：").append(createTime).append("）")
                  .append("共有 ").append(batchRowCount > 0 ? batchRowCount : "0").append(" 条数据。");
                return sb.toString();
            }

            if (askPersons && dataRows.isArray() && dataRows.size() > 0) {
                sb.append("最近导入的").append(formType).append("数据（批次 ID：").append(batchId)
                  .append("）包含以下人员：\n\n");
                for (JsonNode row : dataRows) {
                    String name = row.path("applicant_name").asText(row.path("employee_name").asText("-"));
                    String dept = row.path("department").asText("-");
                    sb.append("- ").append(name).append("（部门：").append(dept).append("）\n");
                }
                sb.append("\n共 ").append(batchRowCount).append(" 条记录。");
                return sb.toString();
            }

            // 通用回答
            sb.append("最近一次导入批次：")
              .append("类型=").append(formType)
              .append("，批次 ID=").append(batchId)
              .append("，时间=").append(createTime)
              .append("，共 ").append(batchRowCount).append(" 条数据。");
            if (dataRows.isArray() && dataRows.size() > 0) {
                sb.append("\n\n前几条记录：\n");
                int limit = Math.min(5, dataRows.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode row = dataRows.get(i);
                    String name = row.path("applicant_name").asText(row.path("employee_name").asText("-"));
                    sb.append("- ").append(name).append("\n");
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.warn("[SqlAgent] buildFallbackFromRecentImport failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildFallbackFromAggr(String userMessage, String aggrContextJson) {
        try {
            JsonNode node = objectMapper.readTree(aggrContextJson);
            String aggrType = node.path("aggrType").asText("");
            JsonNode rows = node.path("rows");

            if (!rows.isArray() || rows.size() == 0) {
                return "未查到相关统计数据。";
            }

            StringBuilder sb = new StringBuilder();

            if ("absence_by_dept".equals(aggrType)) {
                sb.append("根据数据库考勤记录统计，各部门异常出勤（含缺卡/迟到/早退等）情况如下：\n\n");
                for (JsonNode row : rows) {
                    String dept = row.path("department").asText("-");
                    String cnt = row.path("absence_count").asText("0");
                    sb.append("- ").append(dept).append("：").append(cnt).append(" 次\n");
                }
                if (rows.size() > 0) {
                    JsonNode top = rows.get(0);
                    sb.append("\n缺勤（异常出勤）人次最多的部门为：【")
                      .append(top.path("department").asText("-")).append("】，共 ")
                      .append(top.path("absence_count").asText("0")).append(" 次。");
                }
                return sb.toString();
            }

            return "聚合统计结果：已完成查询，请查看 sqlTrace。";

        } catch (Exception e) {
            log.warn("[SqlAgent] buildFallbackFromAggr failed: {}", e.getMessage());
            return null;
        }
    }

    // ===== 聚合预查（缺勤部门等统计类问题）=====

    /**
     * 识别统计类问题（缺勤/部门/最多 等），直接运行 DB 聚合查询，
     * 结果注入 messages 让 DeepSeek 直接基于数据回答，并在 fallback 时直接生成答案。
     */
    private String buildAggregationContextIfNeeded(String userMessage, List<JijianSqlTraceVO> sqlTrace) {
        if (userMessage == null) return null;

        // 缺勤/迟到/早退 + 部门 + 统计（最多/排名/比较）
        boolean isAbsenceQuery = (userMessage.contains("缺勤") || userMessage.contains("旷工")
                || userMessage.contains("异常") || userMessage.contains("迟到") || userMessage.contains("早退"))
                && (userMessage.contains("部门") || userMessage.contains("哪个") || userMessage.contains("最多"));

        if (isAbsenceQuery) {
            // 统计近一年考勤异常（非"正常"、非"休息"）的各部门人次
            String sql = "SELECT department, COUNT(*) AS absence_count "
                    + "FROM jijian_attendance_daily "
                    + "WHERE deleted = 0 "
                    + "AND (checkin_result NOT IN ('正常','休息') OR checkout_result NOT IN ('正常','休息')) "
                    + "GROUP BY department "
                    + "ORDER BY absence_count DESC "
                    + "LIMIT 10";

            QueryResult qr = sqlExecutor.execute(sql);
            if (!qr.isSuccess() || qr.getTotalRowCount() == 0) return null;

            JijianSqlTraceVO trace = new JijianSqlTraceVO();
            trace.setPurpose("[aggrPreQuery] 各部门缺勤/异常出勤统计");
            trace.setSql(sql);
            trace.setRowCount(qr.getTotalRowCount());
            trace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
            trace.setSource("aggr_prequery");
            sqlTrace.add(trace);

            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "aggrContext");
            node.put("aggrType", "absence_by_dept");
            node.put("note", "各部门考勤异常统计（非正常/非休息），已按异常次数降序排列");
            ArrayNode rowsNode = node.putArray("rows");
            for (Map<String, Object> row : qr.getRows()) {
                ObjectNode rn = rowsNode.addObject();
                row.forEach((k, v) -> rn.put(k, v == null ? null : v.toString()));
            }
            try {
                return objectMapper.writeValueAsString(node);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    // ===== 系统 Prompt =====

    private String buildSystemPrompt() {
        return "你是纪检信息系统的 AI 数据分析助手（SQL Agent 模式）。\n"
                + "通过执行只读 SQL 查询本地业务数据库获取真实数据，然后基于真实数据进行分析。\n"
                + "\n"
                + "【数据库 schema】\n"
                + schemaProvider.schemaJson() + "\n"
                + "\n"
                + "【严格规则】\n"
                + "1. 只能查询 schema 中列出的业务表，允许按明确外键进行房产、租赁人员、租赁合同跨表关联\n"
                + "2. 只能生成 SELECT 语句，禁止写库操作\n"
                + "3. 所有查询必须加 deleted = 0 条件\n"
                + "4. jijian_attendance_daily 必须使用 COUNT/GROUP BY/SUM 聚合，禁止 SELECT * 全表\n"
                + "5. 禁止编造数据库中不存在的数据\n"
                + "6. 若查询结果为空，必须明确说明查不到\n"
                + "7. 禁止输出手机号、身份证等敏感字段\n"
                + "8. 多表 JOIN 时每张表都必须使用别名并分别添加 alias.deleted = 0\n"
                + "\n"
                + "【回复格式】每次回复只能是以下两种 JSON 之一：\n"
                + "格式 A：{\"action\":\"query\",\"purpose\":\"查询目的\",\"sql\":\"SELECT ...\"}\n"
                + "格式 B：{\"action\":\"answer\",\"answer\":\"中文结论\","
                + "\"metrics\":[{\"key\":\"k\",\"label\":\"标签\",\"value\":1,\"unit\":\"条\"}],"
                + "\"charts\":[{\"type\":\"bar\",\"title\":\"标题\",\"xAxis\":[\"A\"],\"series\":[{\"name\":\"s\",\"data\":[1]}]}],"
                + "\"tables\":[{\"title\":\"表标题\",\"columns\":[{\"key\":\"k\",\"label\":\"列名\"}],\"rows\":[{\"k\":\"v\"}]}]}\n"
                + "\n"
                + "【人员查询规则】查询 applicant_name 时必须覆盖「姓名(编号)」格式：\n"
                + "(applicant_name='姓名' OR applicant_name LIKE '姓名(%' OR applicant_name LIKE '%姓名%')\n";
    }

    // ===== DeepSeek HTTP 调用 =====

    /**
     * 校验并规范化 messages：确保每条消息的 content 均为非空 String，
     * 防止 Map/List/null 直接作为 content 导致 DeepSeek 400。
     */
    private List<Map<String, String>> normalizeMessagesForDeepSeek(List<Map<String, String>> raw) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, String> m = raw.get(i);
            String role = m.get("role");
            String content = m.get("content");
            if (content == null) {
                log.warn("[SqlAgent] messages[{}].content is null, replacing with empty string", i);
                content = "";
            }
            if (!(content instanceof String)) {
                log.warn("[SqlAgent] messages[{}].content type={}, serializing to JSON", i, content.getClass().getSimpleName());
                content = toJson(content);
            }
            result.add(msg(role != null ? role : "user", content));
        }
        return result;
    }

    /** 构建 DeepSeek 请求 body（单独方法便于调试序列化） */
    private ObjectNode buildDeepSeekBody(DeepSeekConfig cfg, List<Map<String, String>> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.model);
        body.put("temperature", 0.1);
        body.put("stream", false);
        body.put("max_tokens", 4096);
        ArrayNode msgArray = body.putArray("messages");
        for (Map<String, String> m : messages) {
            msgArray.addObject()
                    .put("role", m.get("role"))
                    .put("content", m.get("content"));
        }
        return body;
    }

    private String callDeepSeek(DeepSeekConfig cfg, List<Map<String, String>> messages) throws Exception {
        ObjectNode body = buildDeepSeekBody(cfg, messages);

        // URL 构建：baseUrl 不含 /v1 时自动追加
        String base = cfg.baseUrl.replaceAll("/+$", "");
        String url;
        if (base.endsWith("/v1/chat/completions") || base.endsWith("/chat/completions")) {
            url = base;
        } else if (base.endsWith("/v1")) {
            url = base + "/chat/completions";
        } else {
            // https://api.deepseek.com → https://api.deepseek.com/v1/chat/completions
            url = base + "/v1/chat/completions";
        }
        log.debug("[SqlAgent] DeepSeek endpoint: {}", url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        String bodyJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + cfg.apiKey)
                .timeout(Duration.ofSeconds(Math.max(30, cfg.timeoutSeconds)))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body400 = response.body();
            if (body400.length() > 300) body400 = body400.substring(0, 300);
            throw new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + body400);
        }

        JsonNode content = objectMapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }
        return content.asText().trim();
    }

    // ===== 结果构建 =====

    private String buildSqlResultJson(QueryResult qr) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "sqlResult");
        node.put("totalRowCount", qr.getTotalRowCount());
        node.put("returnedRowCount", Math.min(qr.getReturnedRowCount(), MAX_ROWS_TO_MODEL));
        node.put("truncated", qr.isTruncated() || qr.getReturnedRowCount() > MAX_ROWS_TO_MODEL);
        if (qr.isTruncated()) {
            node.put("truncatedNote", "结果超过 " + MAX_ROWS_TO_MODEL + " 行，已截取前 " + MAX_ROWS_TO_MODEL + " 行");
        }
        ArrayNode rowsNode = node.putArray("rows");
        int limit = Math.min(qr.getReturnedRowCount(), MAX_ROWS_TO_MODEL);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = qr.getRows().get(i);
            ObjectNode rowNode = rowsNode.addObject();
            row.forEach((k, v) -> {
                if (v == null) rowNode.putNull(k);
                else rowNode.put(k, v.toString());
            });
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"type\":\"sqlResult\",\"error\":\"序列化失败\"}";
        }
    }

    // ===== 解析 DeepSeek 返回的 metrics/charts/tables =====

    private List<JijianMetricVO> parseMetrics(JsonNode node) {
        List<JijianMetricVO> list = new ArrayList<>();
        if (!node.isArray()) return list;
        for (JsonNode n : node) {
            try {
                JijianMetricVO m = JijianMetricVO.builder()
                        .key(textOrEmpty(n.path("key")))
                        .label(textOrEmpty(n.path("label")))
                        .value(n.path("value").isNumber() ? n.path("value").numberValue()
                                : textOrEmpty(n.path("value")))
                        .unit(textOrNull(n.path("unit")))
                        .build();
                list.add(m);
            } catch (Exception e) {
                log.debug("[SqlAgent] skip metric: {}", e.getMessage());
            }
        }
        return list;
    }

    private List<JijianChartVO> parseCharts(JsonNode node) {
        List<JijianChartVO> list = new ArrayList<>();
        if (!node.isArray()) return list;
        for (JsonNode n : node) {
            try {
                JijianChartVO chart = new JijianChartVO();
                chart.setType(textOrEmpty(n.path("type")));
                chart.setTitle(textOrEmpty(n.path("title")));
                if (n.has("xAxis") && n.path("xAxis").isArray()) {
                    List<String> xAxis = new ArrayList<>();
                    for (JsonNode x : n.path("xAxis")) xAxis.add(x.asText());
                    chart.setXAxis(xAxis);
                }
                if (n.has("series") && n.path("series").isArray()) {
                    List<JijianChartSeriesVO> seriesList = new ArrayList<>();
                    for (JsonNode s : n.path("series")) {
                        JijianChartSeriesVO series = new JijianChartSeriesVO();
                        series.setName(textOrEmpty(s.path("name")));
                        List<Object> data = new ArrayList<>();
                        if (s.has("data") && s.path("data").isArray()) {
                            for (JsonNode d : s.path("data")) {
                                data.add(d.isNumber() ? d.numberValue() : d.asText());
                            }
                        }
                        series.setData(data);
                        seriesList.add(series);
                    }
                    chart.setSeries(seriesList);
                }
                if (n.has("data") && n.path("data").isArray()) {
                    List<Map<String, Object>> pieData = new ArrayList<>();
                    for (JsonNode d : n.path("data")) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        d.fields().forEachRemaining(e -> {
                            JsonNode v = e.getValue();
                            item.put(e.getKey(), v.isNumber() ? v.numberValue() : v.asText());
                        });
                        pieData.add(item);
                    }
                    chart.setData(pieData);
                }
                list.add(chart);
            } catch (Exception e) {
                log.debug("[SqlAgent] skip chart: {}", e.getMessage());
            }
        }
        return list;
    }

    private List<JijianAnalysisTableVO> parseTables(JsonNode node) {
        List<JijianAnalysisTableVO> list = new ArrayList<>();
        if (!node.isArray()) return list;
        for (JsonNode n : node) {
            try {
                String title = textOrEmpty(n.path("title"));
                List<Map<String, String>> columns = new ArrayList<>();
                if (n.has("columns") && n.path("columns").isArray()) {
                    for (JsonNode c : n.path("columns")) {
                        Map<String, String> col = new LinkedHashMap<>();
                        col.put("key", textOrEmpty(c.path("key")));
                        col.put("label", textOrEmpty(c.path("label")));
                        columns.add(col);
                    }
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                if (n.has("rows") && n.path("rows").isArray()) {
                    for (JsonNode r : n.path("rows")) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        r.fields().forEachRemaining(e -> {
                            JsonNode v = e.getValue();
                            row.put(e.getKey(), v.isNumber() ? v.numberValue() : v.asText());
                        });
                        rows.add(row);
                    }
                }
                list.add(JijianAnalysisTableVO.builder().title(title).columns(columns).rows(rows).build());
            } catch (Exception e) {
                log.debug("[SqlAgent] skip table: {}", e.getMessage());
            }
        }
        return list;
    }

    // ===== 工具方法 =====

    private String appendTableHintIfNeeded(String messageWithHint, String rawMessage) {
        if (rawMessage == null) return messageWithHint;
        List<String> hints = new ArrayList<>();
        if (rawMessage.contains("考勤") || rawMessage.contains("打卡")
                || rawMessage.contains("迟到") || rawMessage.contains("早退")
                || rawMessage.contains("缺卡") || rawMessage.contains("缺勤")
                || rawMessage.contains("旷工") || rawMessage.contains("出勤")) {
            hints.add("优先查 jijian_attendance_daily（考勤/打卡，字段：employee_name/attendance_date/checkin_time/checkin_result/checkin_location/checkout_result/department）");
        }
        if (rawMessage.contains("疗休养") || rawMessage.contains("疗养假")
                || rawMessage.contains("休假地点")) {
            hints.add("优先查 jijian_leave_health（疗休养假，字段：applicant_name/leave_location/start_time/end_time/leave_days）");
        }
        if (rawMessage.contains("事假") || rawMessage.contains("是否出义")
                || (rawMessage.contains("请假") && !rawMessage.contains("疗休养") && !rawMessage.contains("调休"))) {
            hints.add("优先查 jijian_leave_personal（事假，字段：applicant_name/leave_type/leave_reason/start_time/end_time/leave_days）");
        }
        if (rawMessage.contains("出差") || rawMessage.contains("出发地") || rawMessage.contains("目的地")) {
            hints.add("优先查 jijian_business_trip（出差，字段：applicant_name/trip_reason/departure_place/destination/start_date/end_date/trip_days）");
        }
        if (rawMessage.contains("调休") || rawMessage.contains("补休") || rawMessage.contains("调休时长")) {
            hints.add("优先查 jijian_compensatory_leave（调休/加班，字段：applicant_name/overtime_start_time/compensatory_start_time/compensatory_duration）");
        }
        if (hints.isEmpty()) return messageWithHint;
        return messageWithHint + "\n[表提示] " + String.join("；", hints)
                + "\n[强制规则] 所有表查询必须加 deleted = 0 条件";
    }

    private String buildRecentImportContextIfNeeded(String userMessage, List<JijianSqlTraceVO> sqlTrace) {
        if (userMessage == null) return null;
        if (!userMessage.contains("刚刚导入") && !userMessage.contains("最近导入")
                && !userMessage.contains("最近一次导入") && !userMessage.contains("刚才导入")
                && !userMessage.contains("上一次导入") && !userMessage.contains("刚导入")) {
            return null;
        }
        String sql = "SELECT id, form_type, business_table, create_time "
                + "FROM jijian_import_parsed_data "
                + "WHERE deleted = 0 AND confirm_status = 'confirmed' "
                + "ORDER BY create_time DESC LIMIT 10";
        QueryResult qr = sqlExecutor.execute(sql);
        if (!qr.isSuccess() || qr.getTotalRowCount() == 0) return null;

        JijianSqlTraceVO trace = new JijianSqlTraceVO();
        trace.setPurpose("[recentImport] 最近已确认导入批次");
        trace.setSql(sql);
        trace.setRowCount(qr.getTotalRowCount());
        trace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
        trace.setSource("recent_import");
        sqlTrace.add(trace);

        Map<String, Object> latest = qr.getRows().get(0);
        String businessTable = latest.get("business_table") != null ? latest.get("business_table").toString() : null;
        Object parsedDataIdObj = latest.get("id");

        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "recentImportContext");
        node.put("note", "以下是最近一次成功导入的批次信息");
        ArrayNode recent = node.putArray("recentBatches");
        qr.getRows().forEach(r -> {
            ObjectNode rn = recent.addObject();
            r.forEach((k, v) -> rn.put(k, v == null ? null : v.toString()));
        });

        if (businessTable != null && parsedDataIdObj != null
                && schemaProvider.isAllowedTable(businessTable)) {
            String dataSql = "SELECT * FROM " + businessTable
                    + " WHERE deleted = 0 AND source_parsed_data_id = "
                    + parsedDataIdObj + " LIMIT 50";
            QueryResult dataQr = sqlExecutor.execute(dataSql);
            if (dataQr.isSuccess() && dataQr.getTotalRowCount() > 0) {
                JijianSqlTraceVO dTrace = new JijianSqlTraceVO();
                dTrace.setPurpose("[recentImport] 最新批次业务数据 " + businessTable);
                dTrace.setSql(dataSql);
                dTrace.setRowCount(dataQr.getTotalRowCount());
                dTrace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
                dTrace.setSource("recent_import_data");
                sqlTrace.add(dTrace);

                ArrayNode dataRows = node.putArray("latestBatchData");
                dataQr.getRows().forEach(r -> {
                    ObjectNode rn = dataRows.addObject();
                    r.forEach((k, v) -> rn.put(k, v == null ? null : v.toString()));
                });
                node.put("latestBatchTable", businessTable);
                node.put("latestBatchRowCount", dataQr.getTotalRowCount());
            }
        }

        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String appendPersonHintIfNeeded(String userMessage) {
        JijianPersonNameUtils.ParseResult person = JijianPersonNameUtils.parseFromQuery(userMessage);
        if (person == null || (person.name == null && person.employeeNo == null)) {
            return userMessage;
        }
        StringBuilder hint = new StringBuilder("\n[人员提示]");
        if (person.name != null) hint.append(" 姓名=").append(person.name);
        if (person.employeeNo != null) hint.append(" 员工编号=").append(person.employeeNo);
        hint.append("（查询 applicant_name 时请使用模糊条件覆盖「姓名(编号)」格式）");
        return userMessage + hint;
    }

    private String preResolvePersonContext(JijianPersonNameUtils.ParseResult person,
                                            List<JijianSqlTraceVO> sqlTrace) {
        String[][] tableDefs = {
                {"jijian_leave_health", "applicant_name", "employee_no"},
                {"jijian_leave_personal", "applicant_name", "employee_no"},
                {"jijian_compensatory_leave", "applicant_name", "employee_no"},
                {"jijian_business_trip", "applicant_name", "employee_no"},
                {"jijian_attendance_daily", "employee_name", "employee_no"},
        };

        for (String[] tdef : tableDefs) {
            String sql = buildPersonFuzzySelectSql(tdef[0], tdef[1], tdef[2], person, 50);
            QueryResult qr = sqlExecutor.execute(sql);
            if (qr.isSuccess() && qr.getTotalRowCount() > 0) {
                JijianSqlTraceVO trace = new JijianSqlTraceVO();
                trace.setPurpose("[preResolve] 人员模糊预查 " + tdef[0]);
                trace.setSql(sql);
                trace.setRowCount(qr.getTotalRowCount());
                trace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
                trace.setSource("preResolve");
                sqlTrace.add(trace);
                log.info("[SqlAgent] preResolve hit table={} rows={}", tdef[0], qr.getTotalRowCount());

                ObjectNode node = objectMapper.createObjectNode();
                node.put("type", "preResolvedPersonContext");
                node.put("note", "后端已预查到人员数据，请基于以下真实记录回答");
                node.put("table", tdef[0]);
                node.put("totalRowCount", qr.getTotalRowCount());
                ArrayNode rows = node.putArray("rows");
                for (Map<String, Object> r : qr.getRows()) {
                    ObjectNode rowNode = rows.addObject();
                    r.forEach((k, v) -> rowNode.put(k, v == null ? null : v.toString()));
                }
                try {
                    return objectMapper.writeValueAsString(node);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String fallbackPersonQuery(JijianPersonNameUtils.ParseResult person,
                                        String sanitizedSql, List<JijianSqlTraceVO> sqlTrace) {
        String lowerSql = sanitizedSql.toLowerCase();
        String targetTable = null;
        for (String t : new String[]{
                "jijian_leave_health", "jijian_leave_personal",
                "jijian_compensatory_leave", "jijian_business_trip", "jijian_attendance_daily"}) {
            if (lowerSql.contains(t)) { targetTable = t; break; }
        }
        if (targetTable == null) return null;

        String nameCol = "jijian_attendance_daily".equals(targetTable) ? "employee_name" : "applicant_name";
        String fallbackSql = buildPersonFuzzySelectSql(targetTable, nameCol, "employee_no", person, 50);

        QueryResult qr = sqlExecutor.execute(fallbackSql);
        if (!qr.isSuccess() || qr.getTotalRowCount() == 0) return null;

        JijianSqlTraceVO trace = new JijianSqlTraceVO();
        trace.setPurpose("[fallback] 零结果兜底 fuzzy 查询 " + targetTable);
        trace.setSql(fallbackSql);
        trace.setRowCount(qr.getTotalRowCount());
        trace.setExecutedAt(LocalDateTime.now().format(TRACE_DT_FMT));
        trace.setSource("fallback");
        sqlTrace.add(trace);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "zeroResultFallback");
        node.put("hint", "AI 的查询返回 0 行，后端用模糊条件找到以下记录，请基于这些真实数据重新回答");
        node.put("table", targetTable);
        node.put("totalRowCount", qr.getTotalRowCount());
        ArrayNode rows = node.putArray("rows");
        for (Map<String, Object> r : qr.getRows()) {
            ObjectNode rowNode = rows.addObject();
            r.forEach((k, v) -> rowNode.put(k, v == null ? null : v.toString()));
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPersonFuzzySelectSql(String table, String nameCol, String noCol,
                                              JijianPersonNameUtils.ParseResult person, int limit) {
        List<String> conditions = new ArrayList<>();
        String name = person.name;
        String no = person.employeeNo;

        if (name != null && !name.isBlank()) {
            conditions.add(nameCol + " = '" + name.replace("'", "''") + "'");
            conditions.add(nameCol + " LIKE '" + name.replace("'", "''") + "(%'");
            conditions.add(nameCol + " LIKE '" + name.replace("'", "''") + "（%'");
            conditions.add(nameCol + " LIKE '%" + name.replace("'", "''") + "%'");
        }
        if (no != null && !no.isBlank()) {
            conditions.add(nameCol + " LIKE '%" + no.replace("'", "''") + "%'");
            conditions.add(noCol + " = '" + no.replace("'", "''") + "'");
        }

        if (conditions.isEmpty()) return null;

        return "SELECT * FROM " + table
                + " WHERE deleted = 0 AND (" + String.join(" OR ", conditions) + ")"
                + " ORDER BY id DESC LIMIT " + limit;
    }

    private DeepSeekConfig buildConfig() {
        DeepSeekConfig cfg = new DeepSeekConfig();
        JijianProperties.Ai.Deepseek ds = jijianProperties.getAi().getDeepseek();
        cfg.enabled = ds.isEnabled();
        cfg.baseUrl = ds.getBaseUrl() != null ? ds.getBaseUrl() : "https://api.deepseek.com";
        cfg.model = ds.getModel() != null ? ds.getModel() : "deepseek-chat";
        cfg.apiKey = ds.getApiKey();
        cfg.timeoutSeconds = ds.getTimeoutSeconds() > 0 ? ds.getTimeoutSeconds() : 60;
        // 兼容旧配置路径
        if (!cfg.enabled) {
            JijianProperties.Query.Ai legacy = jijianProperties.getQuery().getAi();
            if (legacy.isEnabled()) {
                cfg.enabled = true;
                cfg.baseUrl = legacy.getApiUrl() != null ? legacy.getApiUrl() : cfg.baseUrl;
                cfg.model = legacy.getIntentModel() != null ? legacy.getIntentModel() : cfg.model;
                cfg.apiKey = legacy.getApiKey();
                cfg.timeoutSeconds = legacy.getTimeoutSeconds() > 0 ? legacy.getTimeoutSeconds() : cfg.timeoutSeconds;
            }
        }
        return cfg;
    }

    private boolean hasUsableKey(String key) {
        return key != null && !key.isBlank() && !key.startsWith("${") && !"CHANGE_ME".equals(key);
    }

    private Map<String, String> msg(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content != null ? content : "");
        return m;
    }

    private JsonNode parseJson(String text) {
        try { return objectMapper.readTree(text); }
        catch (Exception e) { return null; }
    }

    private String stripCodeFence(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    private String textOrEmpty(JsonNode node) {
        String v = textOrNull(node);
        return v == null ? "" : v;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private static class DeepSeekConfig {
        boolean enabled;
        String baseUrl;
        String model;
        String apiKey;
        int timeoutSeconds;
    }
}
