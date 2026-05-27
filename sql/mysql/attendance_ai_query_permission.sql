-- 智能查询接口权限（可选执行：为管理员角色开通 system:ai-query:attendance）
-- 超级管理员角色通常已拥有全部权限；若普通角色无法访问，可执行本脚本

-- 示例：将权限挂到「系统管理」目录下（parent_id 请按实际 system 菜单 id 调整）
-- INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
-- VALUES ('智能查询考勤', 'system:ai-query:attendance', 3, 999, 1, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), 0);
