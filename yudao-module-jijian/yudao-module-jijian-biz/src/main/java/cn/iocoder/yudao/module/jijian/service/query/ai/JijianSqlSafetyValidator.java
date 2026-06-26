package cn.iocoder.yudao.module.jijian.service.query.ai;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器：只允许对业务表执行只读 SELECT，禁止写库和访问系统表/敏感字段。
 */
@Component
public class JijianSqlSafetyValidator {

    private static final List<String> FORBIDDEN_WRITE_KEYWORDS = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "REPLACE", "MERGE", "CALL", "EXEC", "EXECUTE",
            "GRANT", "REVOKE", "LOAD", "LOCK", "UNLOCK",
            "COMMIT", "ROLLBACK", "SAVEPOINT"
    );

    private static final List<String> FORBIDDEN_TABLE_PREFIXES = Arrays.asList(
            "system_", "infra_", "oauth", "member_", "pay_"
    );

    private static final List<String> FORBIDDEN_SYSTEM_SCHEMAS = Arrays.asList(
            "mysql.", "information_schema.", "performance_schema.", "sys."
    );

    private static final List<String> ALLOWED_TABLES = Arrays.asList(
            "jijian_compensatory_leave",
            "jijian_leave_health",
            "jijian_leave_personal",
            "jijian_business_trip",
            "jijian_attendance_daily",
            "jijian_property",
            "jijian_lessee",
            "jijian_lease_contract",
            "jijian_canteen_supplier",
            "jijian_canteen_market_price"
    );

    private static final List<String> SENSITIVE_FIELD_PATTERNS = Arrays.asList(
            "id_card", "id_number", "identity_card", "id_no",
            "phone", "mobile", "telephone", "tel",
            "license_no", "business_license", "credit_code",
            "bank_account", "bank_card",
            "password", "passwd", "api_key", "secret_key"
    );

    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.fail("SQL 不能为空");
        }

        // 去除首尾空白和末尾分号
        String cleaned = sql.trim();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        // 禁止多语句（分号只能在末尾）
        if (cleaned.contains(";")) {
            return ValidationResult.fail("不允许多条 SQL 语句（含多个分号）");
        }

        String upper = cleaned.toUpperCase(Locale.ENGLISH);

        // 只允许以 SELECT 或 WITH 开头
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return ValidationResult.fail("只允许 SELECT 查询，实际开头: "
                    + cleaned.substring(0, Math.min(30, cleaned.length())));
        }

        // 禁止写库关键字（作为独立单词）
        for (String kw : FORBIDDEN_WRITE_KEYWORDS) {
            if (Pattern.compile("\\b" + kw + "\\b").matcher(upper).find()) {
                return ValidationResult.fail("SQL 包含禁止的写库关键字: " + kw);
            }
        }

        // 禁止注释注入和危险特征
        if (upper.contains("--") || upper.contains("/*") || upper.contains("*/")
                || upper.contains("XP_") || upper.contains("SP_")) {
            return ValidationResult.fail("SQL 包含注释或危险特征");
        }

        // 禁止 INTO（包含 INTO OUTFILE/DUMPFILE）
        if (Pattern.compile("\\bINTO\\b").matcher(upper).find()) {
            return ValidationResult.fail("SQL 包含 INTO，不允许");
        }

        String lower = cleaned.toLowerCase(Locale.ENGLISH);

        // 禁止访问系统 schema
        for (String schema : FORBIDDEN_SYSTEM_SCHEMAS) {
            if (lower.contains(schema.toLowerCase())) {
                return ValidationResult.fail("SQL 包含禁止的系统表: " + schema);
            }
        }

        // 禁止访问敏感业务表
        for (String prefix : FORBIDDEN_TABLE_PREFIXES) {
            if (lower.contains(prefix.toLowerCase())) {
                return ValidationResult.fail("SQL 包含禁止访问的表: " + prefix);
            }
        }

        // 禁止查询敏感字段
        for (String field : SENSITIVE_FIELD_PATTERNS) {
            if (Pattern.compile("\\b" + field.replace("_", "[_]") + "\\b").matcher(lower).find()) {
                return ValidationResult.fail("SQL 包含敏感字段: " + field);
            }
        }
        if (lower.contains("jijian_lessee")
                && (Pattern.compile("\\bselect\\s+\\*").matcher(lower).find()
                || Pattern.compile("\\b[a-z][a-z0-9_]*\\.\\*").matcher(lower).find())) {
            return ValidationResult.fail("租赁人员表包含敏感字段，禁止使用 SELECT *，请显式选择非敏感白名单字段");
        }

        // 必须引用至少一张允许的业务表
        boolean hasAllowedTable = false;
        String hitTable = null;
        for (String table : ALLOWED_TABLES) {
            if (lower.contains(table.toLowerCase())) {
                hasAllowedTable = true;
                hitTable = table;
                break;
            }
        }
        if (!hasAllowedTable) {
            return ValidationResult.fail("SQL 未引用任何允许的业务表。允许的表: " + String.join(", ", ALLOWED_TABLES));
        }

        // ── deleted=0 自动检查：若业务表存在但 SQL 未包含 deleted 条件，自动注入 ──
        // 仅对有 WHERE 子句的情况注入；没有 WHERE 则直接拒绝（全表扫描危险）
        if (hitTable != null && !lower.contains("deleted")) {
            // 尝试自动注入 deleted=0
            String autoFixed = autoInjectDeleted(cleaned, hitTable);
            if (autoFixed != null) {
                return ValidationResult.okWithNote(autoFixed,
                        "SQL 缺少 deleted=0 条件，已自动注入（原始 SQL：" + cleaned.substring(0, Math.min(80, cleaned.length())) + "...）");
            } else {
                // 无法自动注入时，拒绝并提示 AI 重写
                return ValidationResult.fail("SQL 访问业务表 " + hitTable + " 但缺少 deleted = 0 条件，请重写 SQL 加上 WHERE/AND deleted = 0");
            }
        }

        return ValidationResult.ok(cleaned);
    }

    /**
     * 尝试向 SQL 自动追加 deleted=0 条件。
     * 有 WHERE → 在第一个 GROUP BY/ORDER BY/LIMIT 前追加 AND deleted=0。
     * 无 WHERE → 追加 WHERE deleted=0。
     * 包含 UNION/子查询 → 返回 null（复杂情况不自动注入，由 AI 重写）。
     */
    private String autoInjectDeleted(String sql, String table) {
        String lower = sql.toLowerCase(Locale.ENGLISH);
        // 复杂查询不自动注入
        if (lower.contains("union") || (lower.indexOf("select", 1) > 0 && lower.contains("from ("))) {
            return null;
        }

        int whereIdx  = findKeywordIndex(lower, "where");
        int groupIdx  = findKeywordIndex(lower, "group by");
        int orderIdx  = findKeywordIndex(lower, "order by");
        int havingIdx = findKeywordIndex(lower, "having");
        int limitIdx  = findKeywordIndex(lower, "limit");

        // 找到 WHERE 条件结束的位置（第一个 GROUP BY / ORDER BY / HAVING / LIMIT）
        int insertBefore = sql.length();
        for (int idx : new int[]{groupIdx, orderIdx, havingIdx, limitIdx}) {
            if (idx > 0 && idx < insertBefore) insertBefore = idx;
        }

        String before = sql.substring(0, insertBefore).stripTrailing();
        String after  = sql.substring(insertBefore).trim();
        String suffix = after.isEmpty() ? "" : " " + after;

        if (whereIdx > 0) {
            return before + " AND deleted = 0" + suffix;
        } else {
            return before + " WHERE deleted = 0" + suffix;
        }
    }

    private int findKeywordIndex(String lowerSql, String keyword) {
        Pattern p = Pattern.compile("\\b" + keyword.replace(" ", "\\s+") + "\\b");
        java.util.regex.Matcher m = p.matcher(lowerSql);
        return m.find() ? m.start() : -1;
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final String sanitizedSql;

        private ValidationResult(boolean valid, String message, String sanitizedSql) {
            this.valid = valid;
            this.message = message;
            this.sanitizedSql = sanitizedSql;
        }

        public static ValidationResult ok(String sql) {
            return new ValidationResult(true, "OK", sql);
        }

        /** SQL 合法但经过自动修正（如追加 deleted=0），message 说明修正内容 */
        public static ValidationResult okWithNote(String fixedSql, String note) {
            return new ValidationResult(true, note, fixedSql);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message, null);
        }
    }
}
