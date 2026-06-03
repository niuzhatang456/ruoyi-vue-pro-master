package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询 Handler 注册中心。
 *
 * <p>Spring 启动时自动注入所有 {@link JijianFormQueryHandler} 实现，
 * 按 formType 建立映射。Controller 和 ChatService 通过此 Registry
 * 按 formType 路由到对应 Handler，不再硬编码具体 Service。
 *
 * <p>约定：
 * <ul>
 *   <li>每个 formType 最多注册一个 Handler。</li>
 *   <li>未注册的 formType → {@link #getHandlerOrNull} 返回 null，由调用方处理友好提示。</li>
 *   <li>不对 unsupported 类型抛出系统异常，只返回业务友好信息。</li>
 * </ul>
 */
@Slf4j
@Component
public class JijianFormQueryHandlerRegistry {

    @Resource
    private List<JijianFormQueryHandler> handlers;

    private Map<String, JijianFormQueryHandler> handlerMap;

    @PostConstruct
    public void init() {
        handlerMap = new LinkedHashMap<>();
        for (JijianFormQueryHandler h : handlers) {
            String key = h.getFormType().getValue();
            if (handlerMap.containsKey(key)) {
                log.warn("[QueryHandlerRegistry] Duplicate handler for formType={}, keeping first: {}",
                        key, handlerMap.get(key).getClass().getSimpleName());
                continue;
            }
            handlerMap.put(key, h);
            log.info("[QueryHandlerRegistry] Registered handler: formType={}, class={}, supported={}",
                    key, h.getClass().getSimpleName(), h.isSupported());
        }
    }

    /**
     * 返回指定 formType 的 Handler，不存在时返回 null。
     * 调用方负责判断 null 并给出友好提示，不抛系统异常。
     */
    public JijianFormQueryHandler getHandlerOrNull(String formType) {
        return handlerMap.get(formType);
    }

    /**
     * 返回指定 formType 的 Handler。
     * formType 未注册或为 null 时抛出业务异常（非系统异常）。
     */
    public JijianFormQueryHandler getRequiredHandler(String formType) {
        JijianFormQueryHandler h = handlerMap.get(formType);
        if (h == null) {
            throw new IllegalArgumentException("不支持的数据类型：" + formType);
        }
        return h;
    }

    /**
     * 返回所有已注册的支持查询的 formType 列表。
     */
    public List<String> listSupportedFormTypes() {
        return handlerMap.values().stream()
                .filter(JijianFormQueryHandler::isSupported)
                .map(h -> h.getFormType().getValue())
                .collect(Collectors.toList());
    }

    /**
     * 判断指定 formType 是否已注册且支持查询。
     */
    public boolean isSupported(String formType) {
        JijianFormQueryHandler h = handlerMap.get(formType);
        return h != null && h.isSupported();
    }
}
