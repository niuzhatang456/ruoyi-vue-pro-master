package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class JijianQueryFieldWhitelist {

    private static final List<String> FORBID_CANTEEN = Arrays.asList(
            "\u90e8\u95e8", "\u6d6a\u8d39", "\u8fdd\u89c4", "\u5f02\u5e38", "\u4eba\u5458", "\u5ba1\u6279", "\u5907\u6ce8", "\u4f9b\u5e94\u6b21\u6570");
    private static final List<String> FORBID_PROPERTY = Arrays.asList("\u8fdd\u89c4", "\u8fdd\u89c4\u62db\u79df", "\u8fdd\u7eaa", "\u8fdd\u6cd5");
    private static final List<String> FORBID_LESSEE = Arrays.asList("\u624b\u673a\u53f7\u539f\u6587", "\u8eab\u4efd\u8bc1\u539f\u6587", "\u8425\u4e1a\u6267\u7167\u539f\u6587", "\u5b8c\u6574\u624b\u673a", "\u5b8c\u6574\u8eab\u4efd\u8bc1");

    @Resource
    private ObjectMapper objectMapper;

    public List<String> fields(String formType) {
        switch (normalize(formType)) {
            case "PROPERTY_INFO":
                return Arrays.asList("\u623f\u4ea7\u5730\u5740", "\u623f\u4ea7\u540d\u79f0", "\u4ea7\u6743\u4fe1\u606f", "\u5efa\u7b51\u65f6\u95f4", "\u9762\u79ef", "\u79df\u8d41\u60c5\u51b5", "\u5907\u6ce8");
            case "LESSEE":
                return Arrays.asList("\u4e2a\u4eba/\u7ec4\u7ec7", "\u8054\u7cfb\u4eba", "\u624b\u673a\u53f7\u662f\u5426\u586b\u5199", "\u8eab\u4efd\u8bc1\u662f\u5426\u586b\u5199", "\u8425\u4e1a\u6267\u7167\u662f\u5426\u586b\u5199", "\u662f\u5426\u5355\u4f4d\u5185\u90e8\u4eba\u5458", "\u5907\u6ce8");
            case "LEASE_CONTRACT":
                return Arrays.asList("\u623f\u4ea7", "\u79df\u8d41\u4eba\u5458", "\u5408\u540c\u5f00\u59cb\u65f6\u95f4", "\u5408\u540c\u7ed3\u675f\u65f6\u95f4", "\u91d1\u989d", "\u652f\u4ed8\u60c5\u51b5", "\u6c34\u7535\u8d39\u7ba1\u7406", "\u5408\u540c\u5185\u5bb9\u6458\u8981", "\u5907\u6ce8");
            case "ATTENDANCE_DAILY":
                return Arrays.asList("\u90e8\u95e8", "\u59d3\u540d", "\u5458\u5de5\u7f16\u53f7", "\u7b7e\u5230\u65f6\u95f4", "\u7b7e\u5230\u7ed3\u679c", "\u7b7e\u5230\u5730\u70b9", "\u7b7e\u9000\u65f6\u95f4", "\u7b7e\u9000\u7ed3\u679c", "\u7b7e\u9000\u5730\u70b9", "\u8003\u52e4\u65e5\u671f");
            case "RECUPERATION_LEAVE":
                return Arrays.asList("\u90e8\u95e8", "\u7533\u8bf7\u4eba", "\u5458\u5de5\u7f16\u53f7", "\u7597\u4f11\u517b\u5730\u70b9", "\u5f00\u59cb\u65f6\u95f4", "\u7ed3\u675f\u65f6\u95f4", "\u8bf7\u5047\u5929\u6570", "\u5de5\u4f5c\u5e74\u9650", "\u53c2\u52a0\u5de5\u4f5c\u65f6\u95f4", "\u5907\u6ce8");
            case "PERSONAL_LEAVE":
                return Arrays.asList("\u90e8\u95e8", "\u7533\u8bf7\u4eba", "\u5458\u5de5\u7f16\u53f7", "\u8bf7\u5047\u7c7b\u578b", "\u8bf7\u5047\u4e8b\u7531", "\u5f00\u59cb\u65f6\u95f4", "\u7ed3\u675f\u65f6\u95f4", "\u8bf7\u5047\u5929\u6570", "\u662f\u5426\u51fa\u4e49", "\u51fa\u4e49\u5730\u70b9", "\u8bf7\u5047\u72b6\u6001", "\u6240\u5c5e\u6708\u4efd", "\u5907\u6ce8");
            case "BUSINESS_TRIP":
                return Arrays.asList("\u90e8\u95e8", "\u7533\u8bf7\u4eba", "\u5458\u5de5\u7f16\u53f7", "\u51fa\u5dee\u7c7b\u578b", "\u51fa\u5dee\u4e8b\u7531", "\u5f00\u59cb\u65f6\u95f4", "\u7ed3\u675f\u65f6\u95f4", "\u51fa\u5dee\u5929\u6570", "\u662f\u5426\u51fa\u4e49", "\u51fa\u4e49\u5730\u70b9", "\u51fa\u5dee\u72b6\u6001", "\u6240\u5c5e\u6708\u4efd", "\u5907\u6ce8");
            case "COMPENSATORY_LEAVE":
                return Arrays.asList("\u7533\u8bf7\u4eba", "\u5458\u5de5\u7f16\u53f7", "\u90e8\u95e8", "\u52a0\u73ed\u5f00\u59cb\u65f6\u95f4", "\u52a0\u73ed\u7ed3\u675f\u65f6\u95f4", "\u8c03\u4f11\u5f00\u59cb\u65f6\u95f4", "\u8c03\u4f11\u7ed3\u675f\u65f6\u95f4", "\u8c03\u4f11\u65f6\u957f", "\u662f\u5426\u51fa\u4e49", "\u51fa\u4e49\u5730\u70b9", "\u5907\u6ce8");
            case "CANTEEN_SUPPLIER":
                return Arrays.asList("\u9879\u76ee\u540d\u79f0", "\u89c4\u683c/\u7b49\u7ea7", "\u5355\u4f4d", "\u4ef7\u683c", "\u91c7\u4ef7\u70b9", "\u6700\u9ad8\u4ef7", "\u6700\u4f4e\u4ef7", "\u5747\u4ef7", "\u5dee\u989d", "\u5dee\u5f02\u6bd4\u4f8b", "\u4e0d\u540c\u91c7\u4ef7\u70b9\u4ef7\u683c\u5dee\u5f02");
            default:
                return Collections.emptyList();
        }
    }

    public List<String> analyses(String formType) {
        switch (normalize(formType)) {
            case "PROPERTY_INFO":
                return Arrays.asList("\u623f\u4ea7\u6570\u91cf", "\u9762\u79ef\u6c47\u603b", "\u6309\u79df\u8d41\u60c5\u51b5\u5206\u7ec4", "\u6309\u4ea7\u6743\u4fe1\u606f\u5206\u7ec4", "\u5efa\u7b51\u65f6\u95f4\u5206\u5e03");
            case "LESSEE":
                return Arrays.asList("\u79df\u8d41\u4eba\u5458\u6570\u91cf", "\u4e2a\u4eba/\u7ec4\u7ec7\u6570\u91cf", "\u5355\u4f4d\u5185\u90e8\u4eba\u5458\u6570\u91cf", "\u975e\u5355\u4f4d\u5185\u90e8\u4eba\u5458\u6570\u91cf", "\u8bc1\u4ef6\u4fe1\u606f\u5b8c\u6574\u6027\u7edf\u8ba1");
            case "LEASE_CONTRACT":
                return Arrays.asList("\u5408\u540c\u6570\u91cf", "\u5408\u540c\u91d1\u989d\u6c47\u603b", "\u6309\u652f\u4ed8\u60c5\u51b5\u5206\u7ec4", "\u6309\u6c34\u7535\u8d39\u7ba1\u7406\u5206\u7ec4", "\u5df2\u5230\u671f\u5408\u540c\u6570\u91cf", "\u5373\u5c06\u5230\u671f\u5408\u540c\u6570\u91cf", "\u6700\u9ad8/\u6700\u4f4e/\u5e73\u5747\u5408\u540c\u91d1\u989d");
            case "CANTEEN_SUPPLIER":
                return Arrays.asList("\u603b\u8bb0\u5f55\u6570", "\u9879\u76ee\u6570\u91cf", "\u5b58\u5728\u4ef7\u683c\u5dee\u5f02\u7684\u9879\u76ee\u6570\u91cf", "\u6309\u9879\u76ee\u540d\u79f0\u5206\u7ec4", "\u6309\u91c7\u4ef7\u70b9\u5206\u7ec4", "\u6700\u9ad8\u4ef7", "\u6700\u4f4e\u4ef7", "\u5747\u4ef7", "\u5dee\u989d", "\u5dee\u5f02\u6bd4\u4f8b");
            default:
                return Arrays.asList("\u603b\u8bb0\u5f55\u6570", "\u6309\u90e8\u95e8\u5206\u7ec4", "\u6309\u72b6\u6001\u6216\u7c7b\u578b\u5206\u7ec4", "\u65f6\u95f4\u8303\u56f4\u5185\u6c47\u603b");
        }
    }

    public List<String> forbiddenAnalyses(String formType) {
        switch (normalize(formType)) {
            case "CANTEEN_SUPPLIER":
                return FORBID_CANTEEN;
            case "PROPERTY_INFO":
                return FORBID_PROPERTY;
            case "LESSEE":
                return FORBID_LESSEE;
            default:
                return Collections.emptyList();
        }
    }

    public String describe(String formType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("formType", normalize(formType));
        addArray(root, "fields", fields(formType));
        addArray(root, "allowedAnalyses", analyses(formType));
        addArray(root, "forbiddenAnalyses", forbiddenAnalyses(formType));
        if ("LESSEE".equals(normalize(formType))) {
            root.put("sensitiveRule", "\u624b\u673a\u53f7\u3001\u8eab\u4efd\u8bc1\u3001\u8425\u4e1a\u6267\u7167\u53ea\u80fd\u505a\u662f\u5426\u586b\u5199\u6216\u5b8c\u6574\u6027\u7edf\u8ba1\uff0c\u4e0d\u5f97\u8f93\u51fa\u539f\u59cb\u503c\u3002");
        }
        return root.toString();
    }

    public Map<String, List<String>> primaryFormTypeFields() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                result.put(e.getValue(), fields(e.getValue()));
            }
        }
        return result;
    }

    private String normalize(String formType) {
        if ("ATTENDANCE".equals(formType)) {
            return "ATTENDANCE_DAILY";
        }
        if ("REAL_ESTATE".equals(formType)) {
            return "PROPERTY_INFO";
        }
        if ("CANTEEN_SUPPLY".equals(formType)) {
            return "CANTEEN_SUPPLIER";
        }
        if ("LEAVE_HEALTH".equals(formType)) {
            return "RECUPERATION_LEAVE";
        }
        if ("LEAVE_PERSONAL".equals(formType)) {
            return "PERSONAL_LEAVE";
        }
        if ("COMPENSATORY".equals(formType)) {
            return "COMPENSATORY_LEAVE";
        }
        return formType == null ? "" : formType;
    }

    private void addArray(ObjectNode root, String name, List<String> values) {
        ArrayNode array = root.putArray(name);
        for (String value : values) {
            array.add(value);
        }
    }
}
