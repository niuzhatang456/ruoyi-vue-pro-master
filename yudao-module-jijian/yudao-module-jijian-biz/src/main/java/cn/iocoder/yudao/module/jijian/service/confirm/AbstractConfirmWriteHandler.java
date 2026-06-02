package cn.iocoder.yudao.module.jijian.service.confirm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确认写入 Handler 抽象基类，提供 JSON 提取、字段别名匹配等通用工具方法。
 */
public abstract class AbstractConfirmWriteHandler implements ConfirmWriteHandler {

    /**
     * 获取活跃 JSON 字符串：优先使用用户校正数据，退化为原始解析数据。
     */
    protected String getActiveJson(ParsedDataDO parsedData) {
        String corrected = parsedData.getCorrectedJson();
        return StrUtil.isNotBlank(corrected) ? corrected : parsedData.getParsedJson();
    }

    /**
     * 从活跃 JSON 中提取所有数据行（Map<中文列名, 值>）。
     * 支持 Excel rows 格式（rows:[...]）和 key:value 文本格式（textPreview）。
     */
    protected List<Map<String, String>> extractAllRows(ParsedDataDO parsedData) {
        String json = getActiveJson(parsedData);
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            JSONObject root = JSONUtil.parseObj(json);
            // Excel rows 格式
            JSONArray rows = root.getJSONArray("rows");
            if (rows != null && !rows.isEmpty()) {
                List<Map<String, String>> result = new ArrayList<>(rows.size());
                for (int i = 0; i < rows.size(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    if (row == null) continue;
                    Map<String, String> rowMap = new LinkedHashMap<>();
                    for (String key : row.keySet()) {
                        Object val = row.get(key);
                        if (val != null) {
                            rowMap.put(StrUtil.trim(key), StrUtil.trim(val.toString()));
                        }
                    }
                    result.add(rowMap);
                }
                return result;
            }
            // 文本格式降级：key：value
            String textPreview = root.getStr("textPreview", "");
            if (StrUtil.isNotBlank(textPreview)) {
                return Collections.singletonList(parseKeyValue(textPreview));
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    /**
     * 按别名列表从行数据中取第一个非空值（子串模糊匹配中文列名）。
     */
    protected String get(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String k = StrUtil.trim(entry.getKey());
                if (k.contains(alias) || alias.contains(k)) {
                    String v = StrUtil.trim(entry.getValue());
                    if (StrUtil.isNotBlank(v)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 与 get() 相同，但先对 key 进行标点符号标准化再匹配（处理"规格、等级"→"规格等级"等情形）。
     */
    protected String getWithNormalize(Map<String, String> row, String... aliases) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String k = normalize(StrUtil.trim(entry.getKey()));
            String v = StrUtil.trim(entry.getValue());
            if (StrUtil.isBlank(v)) continue;
            for (String alias : aliases) {
                String a = normalize(alias);
                if (k.contains(a) || a.contains(k)) {
                    return v;
                }
            }
        }
        // fallback to standard get
        return get(row, aliases);
    }

    /** 去除中文标点、全半角空格，便于列名模糊匹配 */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[、，,。．\\s　]+", "");
    }

    /**
     * 从"key：value"或"key:value"多行文本中解析键值对。
     */
    private Map<String, String> parseKeyValue(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int idx = line.indexOf('：');
            if (idx < 0) idx = line.indexOf(':');
            if (idx > 0 && idx < line.length() - 1) {
                String k = StrUtil.trim(line.substring(0, idx));
                String v = StrUtil.trim(line.substring(idx + 1));
                if (StrUtil.isNotBlank(k) && StrUtil.isNotBlank(v)) {
                    result.put(k, v);
                }
            }
        }
        return result;
    }

    /**
     * 将 DO 的 toString 风格转为摘要 Map，子类可 override 提供更友好的字段名。
     */
    protected Map<String, String> toSummaryMap(String... kvPairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (StrUtil.isNotBlank(kvPairs[i + 1])) {
                map.put(kvPairs[i], kvPairs[i + 1]);
            }
        }
        return map;
    }

}
