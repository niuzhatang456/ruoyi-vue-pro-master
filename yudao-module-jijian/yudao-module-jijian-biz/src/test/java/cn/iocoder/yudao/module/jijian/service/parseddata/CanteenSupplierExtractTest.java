package cn.iocoder.yudao.module.jijian.service.parseddata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 食堂供应供应商提取测试（覆盖真实 .xls 合并单元格展开后的重复文本） */
class CanteenSupplierExtractTest {

    private static final String COMPANY = "义乌市弘园农业综合开发有限公司";

    private final ParsedDataServiceImpl service = new ParsedDataServiceImpl();

    @Test
    void shouldExtractFromMergedCellExpandedLine() {
        // 真实 电大4.x.xls：A:B 合并="供应商："，C:H 合并=公司名，展开后整行重复
        String text = "义乌市融媒体中心食堂配送单\n"
                + "日期：2025年4月3号\n"
                + "序号 商品名称 规格 单位 数量 单价 小计 备注\n"
                + "供应商： 供应商： " + COMPANY + " " + COMPANY + " " + COMPANY + " "
                + COMPANY + " " + COMPANY + " " + COMPANY + "\n"
                + "客户签字： 客户签字：";
        assertEquals(COMPANY, service.extractSupplierName(text));
    }

    @Test
    void shouldExtractWithChineseColon() {
        assertEquals(COMPANY, service.extractSupplierName("供应商：" + COMPANY));
    }

    @Test
    void shouldExtractWithEnglishColonAndSpace() {
        assertEquals(COMPANY, service.extractSupplierName("供应商: " + COMPANY));
    }

    @Test
    void shouldExtractFromGongHuoDanWei() {
        assertEquals(COMPANY, service.extractSupplierName("供货单位：" + COMPANY));
    }

    @Test
    void shouldExtractFromPeiSongDanWei() {
        assertEquals(COMPANY, service.extractSupplierName("配送单位: " + COMPANY));
    }

    @Test
    void shouldExtractWithoutColon() {
        assertEquals(COMPANY, service.extractSupplierName("供应商 " + COMPANY));
    }

    @Test
    void shouldStripCustomerSignatureOnSameLine() {
        // OCR 可能把供应商和客户签字识别在同一行
        assertEquals(COMPANY, service.extractSupplierName("供应商：" + COMPANY + " 客户签字："));
    }

    @Test
    void shouldJoinOcrSplitSegments() {
        // OCR 把名称拆成多段
        assertEquals(COMPANY, service.extractSupplierName("供应商：义乌市弘园 农业综合开发有限公司"));
    }

    @Test
    void shouldNotTruncateOnPunctuationInName() {
        assertEquals("义乌市弘园（农业）开发有限公司",
                service.extractSupplierName("供应商：义乌市弘园（农业）开发有限公司"));
    }

    @Test
    void shouldExtractFromOcrSingleSegmentFullText() {
        // OCR fullText 可能是无换行整段文本（含标题/日期/全部数据行），不能误提取为标题
        String text = "义乌市融媒体中心食堂配送单 日期：2025年4月3号 序号 商品名称 规格 单位 数量 单价 小计 备注 "
                + "1 小公鸡 散装 斤 50 15 750 2 牛肋条 散装 斤 30 45 1350 小计 2560 "
                + "13 烤肉粉 袋 袋 10 9 90 14 可惠餐巾纸 箱 箱 4 100 400 小计 490 总计 #REF! "
                + "供应商：" + COMPANY + " 客户签字：";
        assertEquals(COMPANY, service.extractSupplierName(text));
    }

    @Test
    void shouldReturnNullWhenAbsent() {
        assertNull(service.extractSupplierName("义乌市融媒体中心食堂配送单\n日期：2025年4月3号"));
    }

    @Test
    void shouldNotMatchTitleLineContainingPeiSongDan() {
        // 标题"食堂配送单"含"配送单"但不含"配送单位"，不能误提取
        assertNull(service.extractSupplierName("义乌市融媒体中心食堂配送单\n日期：2025年4月3号\n序号 商品名称"));
    }
}
