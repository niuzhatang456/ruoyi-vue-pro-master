package cn.iocoder.yudao.module.jijian.service.query.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证 JijianSqlSafetyValidator.autoInjectDeleted() 的注入逻辑是否正确。
 *
 * <p>不需要 Spring 上下文，直接 new 对象测试。
 */
class SqlSafetyValidatorInjectTest {

    private final JijianSqlSafetyValidator validator = new JijianSqlSafetyValidator();

    /** 无 WHERE 的简单 COUNT，应追加 WHERE deleted = 0 */
    @Test
    void testSimpleCountNoWhere() {
        String sql = "SELECT COUNT(*) FROM jijian_leave_health";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        System.out.println("原 SQL : " + sql);
        System.out.println("注入后 : " + vr.sanitizedSql);
        System.out.println("是否通过: " + vr.valid + " | " + vr.message);
        System.out.println();

        Assertions.assertTrue(vr.valid, "应该通过校验");
        String normalized = vr.sanitizedSql.toLowerCase().replaceAll("\\s+", " ");
        Assertions.assertTrue(normalized.contains("where deleted = 0"),
                "应含 WHERE deleted = 0，实际: " + vr.sanitizedSql);
    }

    /** 已有 WHERE 的 COUNT，应在 GROUP BY 前追加 AND deleted = 0 */
    @Test
    void testWhereBeforeGroupBy() {
        String sql = "SELECT applicant_name, SUM(leave_days) "
                + "FROM jijian_leave_health "
                + "WHERE start_time >= '2026-03-01' "
                + "GROUP BY applicant_name "
                + "ORDER BY SUM(leave_days) DESC "
                + "LIMIT 5";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        System.out.println("原 SQL : " + sql);
        System.out.println("注入后 : " + vr.sanitizedSql);
        System.out.println("是否通过: " + vr.valid + " | " + vr.message);
        System.out.println();

        Assertions.assertTrue(vr.valid, "应该通过校验");
        String normalized = vr.sanitizedSql.toLowerCase().replaceAll("\\s+", " ");
        // deleted=0 应在 GROUP BY 之前
        int deletedIdx = normalized.indexOf("deleted = 0");
        int groupByIdx = normalized.indexOf("group by");
        Assertions.assertTrue(deletedIdx >= 0, "应含 deleted = 0");
        Assertions.assertTrue(deletedIdx < groupByIdx,
                "deleted=0 应在 GROUP BY 之前，实际: " + vr.sanitizedSql);
    }

    /** 无 WHERE 有 GROUP BY，应追加 WHERE deleted = 0 在 GROUP BY 之前 */
    @Test
    void testNoWhereWithGroupBy() {
        String sql = "SELECT department, COUNT(*) "
                + "FROM jijian_attendance_daily "
                + "GROUP BY department";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        System.out.println("原 SQL : " + sql);
        System.out.println("注入后 : " + vr.sanitizedSql);
        System.out.println("是否通过: " + vr.valid + " | " + vr.message);
        System.out.println();

        Assertions.assertTrue(vr.valid, "应该通过校验");
        String normalized = vr.sanitizedSql.toLowerCase().replaceAll("\\s+", " ");
        Assertions.assertTrue(normalized.contains("where deleted = 0"),
                "应含 WHERE deleted = 0，实际: " + vr.sanitizedSql);
        int deletedIdx = normalized.indexOf("deleted = 0");
        int groupByIdx = normalized.indexOf("group by");
        Assertions.assertTrue(deletedIdx < groupByIdx,
                "deleted=0 应在 GROUP BY 之前，实际: " + vr.sanitizedSql);
    }

    /** 已有 deleted=0 不应重复注入 */
    @Test
    void testAlreadyHasDeleted() {
        String sql = "SELECT * FROM jijian_leave_health WHERE deleted = 0 AND leave_days > 3";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        System.out.println("原 SQL : " + sql);
        System.out.println("注入后 : " + vr.sanitizedSql);
        System.out.println("是否通过: " + vr.valid + " | " + vr.message);
        System.out.println();

        Assertions.assertTrue(vr.valid);
        // deleted 不应出现两次
        String normalized = vr.sanitizedSql.toLowerCase();
        long count = normalized.chars().mapToObj(c -> (char) c)
                .reduce("", (a, b) -> a + b, String::concat)
                .split("deleted").length - 1;
        Assertions.assertEquals(1, count, "deleted 应只出现一次，实际: " + vr.sanitizedSql);
    }

    /** 有 WHERE 且有 ORDER BY，注入后语义正确 */
    @Test
    void testWhereBeforeOrderBy() {
        String sql = "SELECT employee_name, checkin_result "
                + "FROM jijian_attendance_daily "
                + "WHERE attendance_date = '2026-04-03' "
                + "ORDER BY employee_name";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        System.out.println("原 SQL : " + sql);
        System.out.println("注入后 : " + vr.sanitizedSql);
        System.out.println("是否通过: " + vr.valid + " | " + vr.message);
        System.out.println();

        Assertions.assertTrue(vr.valid);
        String normalized = vr.sanitizedSql.toLowerCase().replaceAll("\\s+", " ");
        Assertions.assertTrue(normalized.contains("deleted = 0"));
        Assertions.assertTrue(normalized.indexOf("deleted = 0") < normalized.indexOf("order by"),
                "deleted=0 应在 ORDER BY 之前，实际: " + vr.sanitizedSql);
    }

    @Test
    void testPropertyLeaseContractJoinAllowed() {
        String sql = "SELECT p.property_name, c.amount "
                + "FROM jijian_property p "
                + "JOIN jijian_lease_contract c ON p.id = c.property_id "
                + "WHERE p.deleted = 0 AND c.deleted = 0";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        Assertions.assertTrue(vr.valid, vr.message);
    }

    @Test
    void testLesseeWildcardRejected() {
        String sql = "SELECT l.* FROM jijian_lessee l WHERE l.deleted = 0";
        JijianSqlSafetyValidator.ValidationResult vr = validator.validate(sql);
        Assertions.assertFalse(vr.valid);
        Assertions.assertTrue(vr.message.contains("禁止使用 SELECT *"));
    }

    @Test
    void testSchemaJsonValid() throws Exception {
        String schemaJson = new JijianQuerySchemaProvider().schemaJson();
        Assertions.assertTrue(new ObjectMapper().readTree(schemaJson).path("allowedTables").size() >= 9);
    }
}
