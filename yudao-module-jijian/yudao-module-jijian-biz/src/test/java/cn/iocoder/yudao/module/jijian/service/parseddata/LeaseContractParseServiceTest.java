package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseContractParseServiceTest {

    private final LeaseContractParseService service = new LeaseContractParseService();

    @Test
    void shouldPreferLesseePhoneWhenBothPartiesHavePhones() {
        Map<String, String> row = parse("房屋租赁合同\n"
                + "出租方：义乌市甲方公司 联系电话：13800001111\n"
                + "承租方：张三 身份证号：330782199001011234 乙方联系电话：13900002222\n"
                + "房屋座落于义乌市测试路1号。租赁期自2025年1月1日至2025年12月31日。房屋年租金为10000元。");

        assertEquals("13900002222", row.get("承租人联系电话"));
    }

    @Test
    void shouldKeepPhoneAndClearIdCardForCompanyLessee() {
        Map<String, String> row = parse("出租合同\n"
                + "甲方：义乌市资产公司 电话：13800001111\n"
                + "乙方：义乌市某某商贸有限公司 承租方联系电话：13700003333\n"
                + "房屋坐落于义乌市市场路。租赁期限为2025年1月1日至2026年12月31日，租金20000元。");

        assertEquals("", row.get("承租人身份证号"));
        assertEquals("13700003333", row.get("承租人联系电话"));
    }

    @Test
    void shouldExtractLesseePhoneFromTailSignatureArea() {
        Map<String, String> row = parse("房屋租赁合同\n"
                + "出租方：甲方单位 联系电话：13800001111\n"
                + "承租方：李四 身份证号：330782199201011234\n"
                + "房屋座落于义乌市江东街道。租赁期自2025年3月1日至2026年2月28日。房屋年租金为15000元。\n"
                + "甲方签字：王五\n"
                + "乙方签字：李四 联系电话：13600004444\n"
                + "合同签订日期：2025年2月20日");

        assertEquals("13600004444", row.get("承租人联系电话"));
    }

    @Test
    void shouldExtractLesseeFixedPhoneFromTailSignatureArea() {
        Map<String, String> row = parse("租赁合同\n"
                + "出租方：（以下简称甲方）义乌市融媒体中心\n"
                + "承租方：（以下简称乙方）义乌广阔传媒有限公司\n"
                + "房屋座落于义乌市江东东路33号。租赁期自2025年5月22日至2026年5月21日。房屋年租金为303000元。\n"
                + "甲 方：义乌市融媒体中心 乙 方：义乌广阔传媒有限公司\n"
                + "电 话： 电 话：0579--85516315\n"
                + "签订日期：2025年5月21日");

        assertEquals("0579-85516315", row.get("承租人联系电话"));
    }

    @Test
    void shouldParseAs20250007TwelveYearRentDetails() {
        Map<String, String> row = service.parse(
                "AS20250007 乡镇站 综合楼出租合同(苏溪3-5层) 20250430.pdf",
                "房屋租赁合同\n"
                        + "出租方：义乌市融媒体中心 以下简称甲方\n"
                        + "承租方：金林炳 330725197309223119 乙方联系电话：18857973838\n"
                        + "一、房屋状况 甲方出租给乙方的房屋座落于苏溪镇广播电视站3-5层，约1361平方米（不含301、302室）。\n"
                        + "二、租赁期限 租赁期自2025年4月30日起至2037年4月29日止。\n"
                        + "三、租赁用途 出租房的用途约定为：公寓楼。\n"
                        + "四、房屋租金 2025年、2026年、2027年每年房屋年租金分别为￥182000.00元；"
                        + "2028年、2029年、2030年每年房屋年租金分别为￥191100.00元；"
                        + "2031年、2032年、2033年每年房屋年租金分别为Y200655.00元；"
                        + "2034年、2035年、2036年每年房屋年租金分别为￥210687.75元。\n"
                        + "五、租金交纳 第一年的房屋年租金乙方应于本合同签订之前一次性付清；后续年份的房屋年租金乙方应在每年4月15日前一次性付清。\n"
                        + "六、履约保证金：￥30000.00元。水费按6元/吨收取，电费按1.2元/度收取。\n"
                        + "甲方（盖章）：义乌市融媒体中心 乙方签字：金林炳\n"
                        + "合同签订日期：2025年4月30日",
                "origin.pdf", "origin.pdf").getRow();

        assertEquals("AS20250007", row.get("合同编号"));
        assertEquals("2025-04-30", row.get("合同签订日期"));
        assertEquals("义乌市融媒体中心", row.get("出租方"));
        assertEquals("金林炳", row.get("承租方"));
        assertEquals("330725197309223119", row.get("承租人身份证号"));
        assertEquals("18857973838", row.get("承租人联系电话"));
        assertTrue(row.get("房屋状况").contains("苏溪镇广播电视站3-5层"));
        assertEquals("2025-04-30", row.get("租赁开始时间"));
        assertEquals("2037-04-29", row.get("租赁结束时间"));
        assertEquals("12年", row.get("租赁年份"));
        assertEquals("公寓楼", row.get("租赁用途"));
        assertEquals("30000.00元", row.get("保证金"));
        assertEquals("6元/吨", row.get("水费"));
        assertEquals("1.2元/度", row.get("电费"));

        JSONArray rent = JSONUtil.parseArray(row.get("租金明细JSON"));
        assertEquals(12, rent.size());
        assertEquals("2025", rent.getJSONObject(0).getStr("year"));
        assertEquals("182000.00元", rent.getJSONObject(0).getStr("rentAmount"));
        assertEquals("合同签订日期前", rent.getJSONObject(0).getStr("paymentDate"));
        assertEquals("2036", rent.getJSONObject(11).getStr("year"));
        assertEquals("210687.75元", rent.getJSONObject(11).getStr("rentAmount"));
        assertEquals("2036-04-15前", rent.getJSONObject(11).getStr("paymentDate"));
    }

    @Test
    void shouldExpandYearRangeRentDetails() {
        Map<String, String> row = parse("出租方：甲方\n承租方：王五\n"
                + "租赁期限为2025年1月1日至2027年12月31日。"
                + "房屋租金：2025年至2027年每年房屋年租金为12000元。"
                + "租金交纳：后续年份每年3月10日前支付。合同签订日期：2025年1月1日");

        JSONArray rent = JSONUtil.parseArray(row.get("租金明细JSON"));
        assertEquals(3, rent.size());
        assertEquals("2027", rent.getJSONObject(2).getStr("year"));
        assertEquals("2027-03-10前", rent.getJSONObject(2).getStr("paymentDate"));
    }

    @Test
    void shouldExtractCompanyLesseeAfterAliasAndExpandAnnualRentByLeasePeriod() {
        Map<String, String> row = service.parse("AS20250011 义乌市佰事德职业技能培训学校有限公司房屋租赁合同 20250604.pdf",
                "租赁合同 出租方：（以下简称甲方）义乌市融媒体中心 "
                        + "承租方：（以下简称乙方）_义乌市佰事德职业技能培训学校有限公司 "
                        + "一、房屋的坐落及面积 甲方将其合法拥有的坐落在_义乌市江东东路33号第19层办公用房_出租给乙方使用。"
                        + "三、租赁期限 该房屋租赁期为三年，自2025年3月21日起至2028年3月20日止。"
                        + "四、租金及支付方式 1、该房屋每年的租金为￥606000元整。"
                        + "2、租金每年一付，第一年租金应于乙方营业执照证办理完成10个工作日内一次性付清，"
                        + "以后年度租金分别在2026年3月5日、2027年3月5日前一次性汇入甲方指定账户。",
                "origin.pdf", "origin.pdf").getRow();

        assertEquals("义乌市融媒体中心", row.get("出租方"));
        assertEquals("义乌市佰事德职业技能培训学校有限公司", row.get("承租方"));
        assertTrue(row.get("房屋状况").contains("义乌市江东东路33号第19层办公用房"));
        JSONArray rent = JSONUtil.parseArray(row.get("租金明细JSON"));
        assertEquals(3, rent.size());
        assertEquals("2025", rent.getJSONObject(0).getStr("year"));
        assertEquals("606000.00元", rent.getJSONObject(0).getStr("rentAmount"));
        assertEquals("2027", rent.getJSONObject(2).getStr("year"));
        assertEquals("2027-03-05前", rent.getJSONObject(2).getStr("paymentDate"));
    }

    @Test
    void shouldReadAnnualRentFromPaymentSectionBeforeUtilityFees() {
        Map<String, String> row = service.parse("AS20250010 义乌广阔传媒有限公司房屋租赁合同 20250521.pdf",
                "租赁合同 出租方：（以下简称甲方）义乌市融媒体中心 "
                        + "承租方：（以下简称乙方）义乌广阔传媒有限公司 "
                        + "一、房屋的坐落及面积 甲方将其合法拥有的坐落在_义乌市江东东路33号第23层办公用房_出租给乙方使用。"
                        + "三、租赁期限 该房屋租赁期为一年，自2025年5月22日起至2026年5月21日止。"
                        + "四、租金及支付方式 1、该房屋每年的租金为￥303000元整。"
                        + "2、房屋租金应于本合同签订后15日内一次性汇入甲方指定账户。"
                        + "五、其他费用 1、电费按义乌市供电局核准供电价格加公共能耗1.2元/度收取，水费按3000元/年收取。",
                "origin.pdf", "origin.pdf").getRow();

        assertEquals("义乌广阔传媒有限公司", row.get("承租方"));
        assertEquals("303000.00元", row.get("房屋租金1"));
        assertEquals("3000元/年", row.get("水费"));
        assertEquals("1.2元/度", row.get("电费"));
        JSONArray rent = JSONUtil.parseArray(row.get("租金明细JSON"));
        assertEquals("303000.00元", rent.getJSONObject(0).getStr("rentAmount"));
    }

    @Test
    void shouldExtractTailSignDateBeforeFallingBackToFileName() {
        Map<String, String> row = service.parse("AS20250001 租赁合同 20250101.pdf",
                "出租方：甲方\n承租方：赵六\n租赁期自2025年1月1日至2025年12月31日。房屋年租金为10000元。\n"
                        + "甲方签字：张三 乙方签字：赵六\n签订日期：2025年2月3日",
                "origin.pdf", "origin.pdf").getRow();

        assertEquals("2025-02-03", row.get("合同签订日期"));
    }

    @Test
    void shouldScoreLeaseContractAndRejectClearTableHeaders() {
        assertTrue(LeaseContractParseService.scoreLeaseContract(
                "AS20250007 综合楼出租合同.pdf",
                "出租方：甲方 承租方：乙方 租赁期自2025年1月1日至2025年12月31日 房屋租金 保证金",
                java.util.Collections.emptyList()) >= 7);

        assertEquals(0, LeaseContractParseService.scoreLeaseContract(
                "食堂供应.xlsx",
                "商品名称 规格 单位 数量 单价 小计 备注",
                java.util.Arrays.asList("商品名称", "规格", "单位", "数量", "单价", "小计", "备注")));
    }

    private Map<String, String> parse(String text) {
        return service.parse("AS20260001租赁合同.pdf", text, "origin.pdf", "origin.pdf").getRow();
    }
}
