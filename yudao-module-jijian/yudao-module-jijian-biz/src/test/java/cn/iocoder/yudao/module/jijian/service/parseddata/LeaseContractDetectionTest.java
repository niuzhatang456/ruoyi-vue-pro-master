package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaseContractDetectionTest {

    private final ParsedDataServiceImpl service = new ParsedDataServiceImpl();

    @Test
    void shouldPreferLeaseContractOverLesseeWhenContractHasPhone() throws Exception {
        String text = "房屋租赁协议\n"
                + "出租方（甲方）：义乌市某资产公司 联系电话：0579-85170105\n"
                + "承租方（乙方）：某贸易有限公司 联系电话：13800138000\n"
                + "租赁房屋坐落于外贸商务大厦608室，租赁期限自2026年1月1日起至2026年12月31日止。\n"
                + "年租金人民币120000元，履约保证金20000元。\n"
                + "合同签订日期：2025年12月20日\n"
                + "甲方签章： 乙方签章：";

        assertEquals(FormTypeConstants.LEASE_CONTRACT, detectFormType(text));
    }

    @Test
    void shouldNotTreatPlainLesseeRosterAsLeaseContract() throws Exception {
        String text = "租赁人员信息表\n姓名 身份证号 联系电话 营业执照 是否内部人员 地址";

        assertEquals(FormTypeConstants.LESSEE, detectFormType(text));
    }

    private String detectFormType(String text) throws Exception {
        Method method = ParsedDataServiceImpl.class.getDeclaredMethod("detectFormType", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, text);
    }
}
