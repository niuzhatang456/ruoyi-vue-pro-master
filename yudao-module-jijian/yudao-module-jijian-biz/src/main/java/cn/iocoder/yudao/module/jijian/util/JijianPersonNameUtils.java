package cn.iocoder.yudao.module.jijian.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纪检人员姓名解析工具。
 *
 * <p>历史数据中 applicant_name 可能包含员工编号，例如：
 * <ul>
 *   <li>{@code 毛阳成(78251702)}  — 中文括号</li>
 *   <li>{@code 毛阳成（78251702）} — 全角括号</li>
 *   <li>{@code 毛阳成 78251702}   — 空格分隔</li>
 *   <li>{@code 78251702}          — 纯编号</li>
 * </ul>
 *
 * <p>此工具类用于：
 * <ol>
 *   <li>新导入时，将 "毛阳成(78251702)" 拆分为 name="毛阳成", employeeNo="78251702"。</li>
 *   <li>查询时，从用户输入中提取可能的姓名和编号，用于生成模糊 SQL。</li>
 * </ol>
 */
public final class JijianPersonNameUtils {

    /** 匹配 "姓名(编号)" 或 "姓名（编号）" 格式 */
    private static final Pattern NAME_WITH_NO = Pattern.compile(
            "^(.+?)[(（](\\d{6,12})[)）]\\s*$");

    /** 匹配 "姓名 编号"（空格分隔，编号为6-12位数字）格式 */
    private static final Pattern NAME_SPACE_NO = Pattern.compile(
            "^(.+?)\\s+(\\d{6,12})\\s*$");

    /** 纯数字编号（6-12位） */
    private static final Pattern PURE_NO = Pattern.compile("^\\d{6,12}$");

    private JijianPersonNameUtils() {}

    /**
     * 解析结果，包含原始值、纯姓名和员工编号。
     */
    public static class ParseResult {
        /** 原始输入值 */
        public final String raw;
        /** 纯姓名（无编号），若输入是纯编号则为 null */
        public final String name;
        /** 员工编号，若无则为 null */
        public final String employeeNo;

        private ParseResult(String raw, String name, String employeeNo) {
            this.raw = raw;
            this.name = name;
            this.employeeNo = employeeNo;
        }

        @Override
        public String toString() {
            return "ParseResult{raw='" + raw + "', name='" + name + "', employeeNo='" + employeeNo + "'}";
        }
    }

    /**
     * 解析人员字段，拆分姓名与员工编号。
     *
     * @param raw 原始值，例如 "毛阳成(78251702)"
     * @return 解析结果；raw 为空时返回 null
     */
    public static ParseResult parse(String raw) {
        if (StrUtil.isBlank(raw)) return null;
        String s = raw.trim();

        // 1. "姓名(编号)" 或 "姓名（编号）"
        Matcher m = NAME_WITH_NO.matcher(s);
        if (m.matches()) {
            return new ParseResult(raw, m.group(1).trim(), m.group(2).trim());
        }

        // 2. "姓名 编号"
        m = NAME_SPACE_NO.matcher(s);
        if (m.matches()) {
            return new ParseResult(raw, m.group(1).trim(), m.group(2).trim());
        }

        // 3. 纯编号
        if (PURE_NO.matcher(s).matches()) {
            return new ParseResult(raw, null, s);
        }

        // 4. 纯姓名（无法识别编号）
        return new ParseResult(raw, s, null);
    }

    /**
     * 仅提取纯姓名（去掉括号中的编号）。
     * 例如 "毛阳成(78251702)" → "毛阳成"
     */
    public static String extractName(String raw) {
        ParseResult r = parse(raw);
        return (r == null || r.name == null) ? raw : r.name;
    }

    /**
     * 仅提取员工编号。
     * 例如 "毛阳成(78251702)" → "78251702"
     */
    public static String extractNo(String raw) {
        ParseResult r = parse(raw);
        return (r == null) ? null : r.employeeNo;
    }

    /**
     * 从用户自然语言输入中识别人员信息（用于查询端）。
     *
     * <p>例如：
     * <ul>
     *   <li>"政经部毛阳成疗休养几天" → name="毛阳成", employeeNo=null</li>
     *   <li>"毛阳成(78251702)疗休养" → name="毛阳成", employeeNo="78251702"</li>
     *   <li>"78251702 的考勤"         → name=null, employeeNo="78251702"</li>
     * </ul>
     *
     * @param userInput 用户输入
     * @return 解析结果；若未识别到任何人员信息返回 null
     */
    public static ParseResult parseFromQuery(String userInput) {
        if (StrUtil.isBlank(userInput)) return null;

        // 先尝试识别 "姓名(编号)" 格式（含括号的子串）
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})[(（](\\d{6,12})[)）]")
                .matcher(userInput);
        if (m.find()) {
            return new ParseResult(m.group(0), m.group(1).trim(), m.group(2).trim());
        }

        // 再尝试单独识别6-12位纯数字编号
        m = Pattern.compile("(?<![\\d])(\\d{6,12})(?![\\d])").matcher(userInput);
        String detectedNo = null;
        if (m.find()) {
            detectedNo = m.group(1);
        }

        // 识别2-3个汉字作为姓名（中国人名一般2-3字；4字名极少，贪婪匹配4字易把业务词吸入）
        // 先把输入中 "XX部/室/科/处" 等部门词替换为空格，防止与人名拼在一起
        String cleaned = userInput.replaceAll(
                "[\\u4e00-\\u9fa5]{1,6}(?:部|室|科|处|厅|局|院|所|队|组|中心|委员会|办公室|工作室)",
                " ");
        m = Pattern.compile("[\\u4e00-\\u9fa5]{2,3}").matcher(cleaned);
        String detectedName = null;
        while (m.find()) {
            String candidate = m.group();
            // 跳过常见业务词汇和单独的业务字符
            if (candidate.matches(".*(疗休养|出差|事假|调休|几天|什么|时候|开始|结束|请假|" +
                    "记录|查询|上班|下班|打卡|地点|统计|情况|义乌|杭州|上海|北京|义务|" +
                    "参加|发现|问题|返回|公司|员工|职工|报表|分析|汇总|合计|情况|" +
                    "疗养|休养|补休|缺勤|旷工|迟到|早退|考勤|出勤|日报|汇报).*")) {
                continue;
            }
            // 优先保留 2-3 字候选（更像人名），相同长度时保留先找到的
            if (detectedName == null || candidate.length() < detectedName.length()) {
                detectedName = candidate;
            }
        }

        if (detectedName != null || detectedNo != null) {
            return new ParseResult(userInput, detectedName, detectedNo);
        }
        return null;
    }
}
