package cn.iocoder.yudao.module.jijian.service.parseddata;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private Map<String, String> parse(String text) {
        return service.parse("AS20260001租赁合同.pdf", text, "origin.pdf", "origin.pdf").getRow();
    }
}
