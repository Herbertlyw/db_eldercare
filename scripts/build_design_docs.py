from pathlib import Path
import csv,json,re
R=Path(__file__).resolve().parents[1];meta=json.loads((R/'docs/diagrams/diagram-index.json').read_text());cn=meta['tables']
cols=[{k.lower():v for k,v in x.items()} for x in csv.DictReader((R/'evidence/schema-columns.tsv').open(),delimiter='\t')];cols=[c for c in cols if not c['table_name'].startswith('v_')]
fks=[{k.lower():v for k,v in x.items()} for x in csv.DictReader((R/'evidence/foreign-keys.tsv').open(),delimiter='\t')]
meaning={'id':'内部唯一编号','name':'业务对象名称','district':'所属演示区县','area_code':'社区服务范围编码','username':'登录账号名','password_hash':'BCrypt 加盐密码摘要','role':'业务角色类别','community_id':'社区或授权辖区编号','organization_id':'服务机构或机构授权范围编号','failures':'累计连续登录失败次数','locked_until':'登录限制截止时间','active':'账号是否启用','identity_hash':'身份证 HMAC 查重值','identity_cipher':'AES GCM 加密身份信息','birth_date':'出生日期','address':'档案地址或订单发生时的地址快照','elder_id':'老人档案编号','status':'业务状态，允许值见枚举','tag':'老人特殊标签','account_id':'关联账号编号','relation_name':'家属与老人关系','phone_cipher':'加密联系电话','scale_name':'能力评估量表名称','score':'评估或抽查得分','ability_level':'能力等级','assessor_id':'评估人员账号','assessed_at':'评估发生时间','valid_until':'有效期结束时刻','category':'服务类别','unit':'计费单位，按次或小时','price':'服务目录指导价','duration_minutes':'标准服务分钟数','subsidizable':'是否允许补贴','bundle_id':'照护服务包编号','item_id':'服务项目编号','weekly_frequency':'每周服务频次','caregiver_id':'护工编号','certificate_no':'职业证书唯一编号','certificate_level':'证书等级说明','valid_from':'资质开始生效时刻','assessment_id':'计划采用的评估记录编号','starts_at':'区间开始时刻，包含边界','ends_at':'区间结束时刻，不含边界','plan_id':'照护计划编号','capacity':'班次可接单数量','used_count':'已占用名额缓存','channel':'下单来源','amount':'订单价格、申报金额或拨付金额','created_by':'创建人账号','created_at':'记录创建时间','order_id':'服务订单编号','shift_id':'护工排班编号','reason':'改派、审核或业务操作原因','assigned_at':'派单发生时间','accepted_at':'护工接单时间','active_order':'仅有效派单有值的生成列，保障一单一有效派单','dispatch_id':'本次派单编号','notes':'服务内容与过程说明','confirmed_by':'老人或家属确认账号','confirmed_at':'服务确认时间','confirmation':'确认凭据说明','check_in':'签到时间','check_out':'签退时间','location_note':'位置说明或模拟定位结果','fence_ok':'模拟地理围栏结果','effective_minutes':'核定有效工时，单位分钟','reviewer_id':'复核或审核人员账号','review_reason':'工时复核原因','serial_no':'设备唯一序列号','type_name':'设备类型','device_id':'监测设备编号','bound_at':'绑定开始时间','unbound_at':'解绑时间，为空表示当前绑定','active_device':'当前绑定设备的生成唯一列','metric':'课程模拟指标名称','low_value':'演示阈值下界','high_value':'演示阈值上界','response_seconds':'初始应急响应时限秒数','binding_id':'采集时有效的绑定历史编号','metric_rule_id':'所用指标规则编号','sample_key':'设备侧采样幂等标识','collected_at':'实际采集时间','metric_value':'采集指标数值','abnormal':'是否越过配置阈值','source':'采集或应急触发来源','vital_id':'触发事件的体征记录','level':'应急级别或升级级别','occurred_at':'事件发生时间','deadline':'本级响应截止时间','responded_by':'响应人员账号','responded_at':'首次响应时间','arrived_at':'到场时间','result':'处置或整改复核结果','event_id':'应急事件编号','message':'通知或事件日志内容','rate':'模拟补贴单价','policy_id':'适用政策编号','period':'按服务发生时间确定的 YYYY-MM 结算月','claim_id':'申报编号','rate_snapshot':'核算时采用的单价快照','stage':'街道或民政审核阶段','decision':'通过或驳回','reference_no':'模拟拨付凭证唯一编号','paid_at':'模拟拨付时间','inspector_id':'抽查人员账号','method':'电话回访或上门核查','issue':'问题说明','rectification':'整改要求','rating':'家属评价分数，1 至 5','content':'评价或投诉内容','table_name':'操作对象表名','row_id':'操作对象记录号','operation':'新增或修改动作','actor':'业务操作者或数据库会话来源','old_status':'变更前状态','new_status':'变更后状态'}
md=['# 数据字典与关系模式','\n本文件根据实际运行数据库的 information_schema 生成。PK 表示主键，FK 表示外键。所有基础表至少有 20 条虚构测试记录；有限角色与状态使用枚举字段，不建立需要凑数的小型字典表。\n']
for t,label in cn.items():
 cs=[c for c in cols if c['table_name']==t]
 md+=['## '+label+' '+t,'\n关系模式：'+t+'('+', '.join(c['column_name']+(' [PK]' if c['column_key']=='PRI' else ' [FK]' if any(f['table_name']==t and f['column_name']==c['column_name'] for f in fks) else '') for c in cs)+')。\n','| 字段 | 粗略类型 | 实际类型 | 允许空值 | 默认值 | 含义与约束 |','| --- | --- | --- | --- | --- | --- |']
 for c in cs:
  typ=c['column_type'];rough='日期时间型' if typ.startswith(('date','time')) else '数字型' if typ.startswith(('bigint','int','decimal','tinyint')) else '字符型'
  fk=next((f for f in fks if f['table_name']==t and f['column_name']==c['column_name']),None)
  desc=meaning.get(c['column_name'],c['column_name'])+('；引用 '+fk['referenced_table_name']+'.'+fk['referenced_column_name'] if fk else '')
  if c['column_key']=='PRI':desc+='；主键、自增'
  if c['column_key']=='UNI':desc+='；唯一索引'
  if c['extra']:desc+='；'+c['extra']
  md.append('| '+' | '.join([c['column_name'],rough,typ,c['is_nullable'],c['column_default'],desc])+' |')
 md.append('')
(R/'docs/数据字典与关系模式.md').write_text('\n'.join(md))
(R/'docs/逻辑设计与范式分析.md').write_text('''# 逻辑设计与范式分析

所有业务实体采用稳定的内部编号作为主键；身份号、设备序列号、证书号等业务唯一键另行约束。多值属性拆成关联表，实体联系通过外键表达。完整关系模式与所有字段见《数据字典与关系模式》。

## 范式判断方法

对每个关系先列出主键与其他候选键，再检查非主属性是否完全依赖候选键，并排除非键属性间的传递依赖。单列代理主键只能帮助标识记录，不能自动证明第三范式。

## 实体关系

community、organization、account、elder、assessment、service_item、care_bundle、caregiver、credential、shift、device、metric_rule、emergency、policy、inspection、complaint、audit_log 均以 id 确定本行事实。姓名不决定住址、机构或身份；评估得分与等级的对应口径随量表和版本而变化，本系统保存评估结论，不把 score 当作独立决定等级的全局函数。account 中 community_id 和 organization_id 表示授权范围，并非根据账号角色推导出的机构归属。

elder_tag 的候选键为 (elder_id,tag)；guardian 为 (elder_id,account_id)；bundle_item 为 (bundle_id,item_id)；caregiver_area 为 (caregiver_id,community_id)；plan_item 为 (plan_id,item_id)。各关联属性依赖完整组合键。外键指向对象的名称、地址等不重复放入关联表。

care_plan 只保存 assessment_id，老人归属从 assessment 派生；因此消除了 assessment_id → elder_id 在计划表内形成的传递依赖。v_plan_context 仅为查询展开归属，不新增事实。dispatch 只保存 shift_id，护工归属从 shift 派生，避免 shift_id → caregiver_id 的重复存储。v_dispatch_context 同理。

service_record 和 attendance 的 dispatch_id 是备用候选键；payment 的 claim_id 和 reference_no 均唯一；device 的 serial_no、credential 的 certificate_no、caregiver 的 account_id、elder 的 identity_hash、account 的 username 是业务候选键。

claim 的业务候选键是 (elder_id,item_id,period)。政策引用位于 claim_item 中，不在 claim 同时保存 policy_id 与可由它决定的 item_id。claim_item 的 order_id 唯一，确保同一服务订单不能重复获补；采用的政策、核定分钟和补贴单价属于该次核算事实。

## 历史事实与物理派生列

service_order 的地址、服务社区和金额是下单时的事实快照，并不由老人当前地址或目录当前指导价永久决定。claim_item 的 rate_snapshot 保存核算时价格，政策未来调整不能改变既有申报。device_binding 记录时间区间，vital 引用采集时的绑定，不由设备当前归属反推。

dispatch.active_order 和 device_binding.active_device 是物理生成列，用于条件唯一性约束；它们不属于独立的概念属性。shift.used_count、claim.amount 为受控物理汇总值；逻辑上可由明细推导，保留是为了容量并发控制和核算校验，必须通过事务同步或触发器核对。这些物理实现选择应与纯逻辑关系模式区别说明，不能把所有缓存都当作独立原始事实。

## 完整性与时间约束

主键、外键、UNIQUE 和 CHECK 约束负责基本合法性。计划和排班采用左闭右开区间，用 start1 < end2 且 end1 > start2 判定重叠。assert_plan 与 assert_shift 先锁定同一老人或护工再检查区间，应用事务使用 READ COMMITTED。派单先锁订单和护工，然后检查资质、区域、整改和预约冲突，并原子占用班次名额。事务失败回滚改派与名额变化。

历史禁止删除触发器保护评估、派单、服务记录、工时、体征、申报明细、审核、拨付和审计。已确认服务与已支付申报禁止普通修改。数据库管理员仍可管理结构与授权，本项目不声称能抵御拥有最高数据库权限的攻击者。
''')
(R/'docs/需求追踪与模板差异.md').write_text('''# 需求追踪与模板差异

2026-11-12 收到小组报告模板、个人报告模板、评分标准后，以三份新材料补充原任务书，不删除原要求。小组模板要求每条业务至少 3 个查询、每条查询至少两种实现，因此提供 15 组共 30 条查询；原任务书要求的至少 5 条三表连接已由 A1、B1、B3、C1、D1、E1 等覆盖。模板要求每业务各有过程和触发器，不能再只按二选一准备。

| 要求 | 实现或证据 |
| --- | --- |
| 用例图与鲁棒图 | docs/diagrams 下 A 至 E 各一套 |
| 全局 UML 及局部实体模型 | global-uml.svg、A-uml 至 E-uml |
| 数据项及物理字典 | 数据字典与关系模式.md，实际数据库导出 |
| 全部建表及约束 | database/01_schema.sql |
| 每表至少 20 条 | Seed.java 与 evidence/row-counts.json |
| 每业务 3 个双实现查询 | database/04_queries.sql、query-comparison.json |
| 至少 15 个业务视图 | database/03_views.sql；另有 2 个内部展开视图 |
| 每业务的过程与触发器 | 02_routines.sql；详见运维说明对应表 |
| Java DAO、分页、批量及事务 | Store、Api、CareService 与真实数据库测试 |
| 并发、越权、回滚测试 | DatabaseIntegrationTest、Surefire 报告 |
| 至少 4 个索引方案 | 主体查询索引及运维说明 |
| 安全与备份恢复 | Crypto、Auth、权限过滤、recovery.json |
| 团队贡献、会议、答辩录像 | 需要组员真实填写和录制，当前未完成 |
| 基础实践 40 分 | 评分文件中的另四次基础实验，不属于本项目替代范围 |

当前技术栈由用户明确为 Java；部署按原生 MySQL 和 Java JAR 设计，Docker 不作为前提。小组信息、个人实际分工与截止日期未提供。个人模板必须与实际分工、答辩一致，不能将 AI 生成内容写成已发生的学生协作。
''')
print('Design docs and complete dictionary written')
