package cn.iocoder.yudao.module.jijian.service.confirm;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 确认写入 Handler 注册中心
 * Spring 自动注入所有 ConfirmWriteHandler 实现，按 formType 建立路由表。
 */
@Component
public class ConfirmWriteHandlerRegistry {

    @Resource
    private List<ConfirmWriteHandler> handlers;

    private final Map<String, ConfirmWriteHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (ConfirmWriteHandler handler : handlers) {
            handlerMap.put(handler.getFormType(), handler);
        }
    }

    public ConfirmWriteHandler getHandler(String formType) {
        return handlerMap.get(formType);
    }

    public boolean supports(String formType) {
        return handlerMap.containsKey(formType);
    }

}
