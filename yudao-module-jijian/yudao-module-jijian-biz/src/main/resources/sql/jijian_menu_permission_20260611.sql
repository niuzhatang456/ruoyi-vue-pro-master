-- 纪检信息系统菜单、按钮权限和推荐角色。默认租户为 1，可按部署租户调整。
SET @tenant_id := 1;

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, deleted)
SELECT '纪检信息系统', '', 1, 5, 0, '/jijian', 'ep:warning-filled', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE path = '/jijian' AND deleted = b'0');
SET @root_id := (SELECT id FROM system_menu WHERE path = '/jijian' AND deleted = b'0' ORDER BY id LIMIT 1);

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, deleted)
SELECT '录入数据', '', 1, 1, @root_id, 'input', 'ep:upload', NULL, NULL, 0, b'1', b'1', b'1', 'admin', b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@root_id AND path='input' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, deleted)
SELECT '查询信息', '', 1, 2, @root_id, 'query', 'ep:search', NULL, NULL, 0, b'1', b'1', b'1', 'admin', b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@root_id AND path='query' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, deleted)
SELECT '我的', '', 1, 3, @root_id, 'me', 'ep:user', NULL, NULL, 0, b'1', b'1', b'1', 'admin', b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@root_id AND path='me' AND deleted=b'0');

SET @input_id := (SELECT id FROM system_menu WHERE parent_id=@root_id AND path='input' AND deleted=b'0' ORDER BY id LIMIT 1);
SET @query_id := (SELECT id FROM system_menu WHERE parent_id=@root_id AND path='query' AND deleted=b'0' ORDER BY id LIMIT 1);
SET @me_id := (SELECT id FROM system_menu WHERE parent_id=@root_id AND path='me' AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '拖拽录入','',2,1,@input_id,'drag','ep:folder-add','jijian/input/Drag','JijianInputDrag',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@input_id AND path='drag' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '房产情况','',2,2,@input_id,'property-group','ep:office-building','jijian/input/PropertyGroup','JijianInputPropertyGroup',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@input_id AND path='property-group' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '考勤情况','',2,3,@input_id,'attendance-group','ep:calendar','jijian/input/AttendanceGroup','JijianInputAttendanceGroup',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@input_id AND path='attendance-group' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '食堂供应','',2,4,@input_id,'canteen-supplier','ep:food','jijian/input/CanteenSupplier','JijianInputCanteenSupplier',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@input_id AND path='canteen-supplier' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '智能AI查询','jijian:query:query',2,1,@query_id,'smart','ep:chat-dot-round','jijian/query/Smart','JijianQuerySmart',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jijian:query:query' AND type=2 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '历史查询对话','jijian:query-history:query',2,1,@me_id,'history','ep:clock','jijian/me/History','JijianMeHistory',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jijian:query-history:query' AND type=2 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '处置记录','jijian:disposal:query',2,2,@me_id,'disposal','ep:document-checked','jijian/me/Disposal','JijianMeDisposal',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jijian:disposal:query' AND type=2 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '当前账号信息','jijian:account:query',2,3,@me_id,'account','ep:user-filled','jijian/me/Account','JijianMeAccount',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jijian:account:query' AND type=2 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,deleted)
SELECT '最近导入记录','jijian:import:query',2,4,@me_id,'imports','ep:files','jijian/me/Imports','JijianMeImports',0,b'1',b'1',b'1','admin',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jijian:import:query' AND type=2 AND deleted=b'0');

-- 其余按钮权限统一挂到纪检根菜单；已有同名权限时不重复创建。
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, deleted)
SELECT p.name, p.permission, 3, p.sort, @root_id, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', b'0'
FROM (
  SELECT '导入上传' name,'jijian:import:upload' permission,101 sort UNION ALL
  SELECT '导入更新','jijian:import:update',102 UNION ALL
  SELECT '导入确认','jijian:import:confirm',103 UNION ALL
  SELECT '导入删除','jijian:import:delete',104 UNION ALL
  SELECT '房产查询','jijian:property:query',110 UNION ALL
  SELECT '房产新增','jijian:property:create',111 UNION ALL
  SELECT '房产更新','jijian:property:update',112 UNION ALL
  SELECT '房产删除','jijian:property:delete',113 UNION ALL
  SELECT '房产导出','jijian:property:export',114 UNION ALL
  SELECT '智能问答','jijian:query:chat',120 UNION ALL
  SELECT '历史删除','jijian:query-history:delete',130 UNION ALL
  SELECT '处置创建','jijian:disposal:create',140 UNION ALL
  SELECT '处置删除','jijian:disposal:delete',141
) p
WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission=p.permission AND m.deleted=b'0');

-- 推荐角色。type=2 为自定义角色，角色编号由数据库生成。
INSERT INTO system_role (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,deleted,tenant_id)
SELECT '纪检普通用户','jijian_user',10,1,'',0,2,'录入、查询、本人历史和本人处置记录','admin',b'0',@tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE code='jijian_user' AND tenant_id=@tenant_id AND deleted=b'0');
INSERT INTO system_role (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,deleted,tenant_id)
SELECT '纪检管理员','jijian_admin',11,1,'',0,2,'纪检业务管理及系统账号管理','admin',b'0',@tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE code='jijian_admin' AND tenant_id=@tenant_id AND deleted=b'0');

SET @user_role_id := (SELECT id FROM system_role WHERE code='jijian_user' AND tenant_id=@tenant_id AND deleted=b'0' ORDER BY id LIMIT 1);
SET @admin_role_id := (SELECT id FROM system_role WHERE code='jijian_admin' AND tenant_id=@tenant_id AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT INTO system_role_menu (role_id,menu_id,creator,deleted,tenant_id)
SELECT @user_role_id,m.id,'admin',b'0',@tenant_id FROM system_menu m
WHERE m.deleted=b'0' AND (
  m.id IN (@root_id,@input_id,@query_id,@me_id) OR m.parent_id IN (@input_id,@query_id,@me_id) OR
  m.permission IN ('jijian:import:upload','jijian:import:query','jijian:import:update','jijian:import:confirm',
    'jijian:property:query','jijian:property:create','jijian:property:update','jijian:property:export',
    'jijian:query:chat','jijian:query:query','jijian:query-history:query',
    'jijian:disposal:create','jijian:disposal:query','jijian:account:query')
) AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@user_role_id AND rm.menu_id=m.id AND rm.deleted=b'0');

INSERT INTO system_role_menu (role_id,menu_id,creator,deleted,tenant_id)
SELECT @admin_role_id,m.id,'admin',b'0',@tenant_id FROM system_menu m
WHERE m.deleted=b'0' AND (
  m.id IN (@root_id,@input_id,@query_id,@me_id,1,100) OR m.parent_id IN (@input_id,@query_id,@me_id) OR
  m.permission LIKE 'jijian:%' OR m.permission LIKE 'system:user:%'
) AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@admin_role_id AND rm.menu_id=m.id AND rm.deleted=b'0');
