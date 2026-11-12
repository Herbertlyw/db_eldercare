-- A1 有效评估与计划的重度失能老人
-- 连接去重与 EXISTS 半连接比较，避免一人多记录重复输出。
-- 实现 A
SELECT DISTINCT e.id,e.name FROM elder e JOIN assessment a ON a.elder_id=e.id JOIN care_plan p ON p.assessment_id=a.id WHERE a.ability_level='SEVERE' AND p.status='ACTIVE' AND a.valid_until>NOW();
-- 实现 B
SELECT e.id,e.name FROM elder e WHERE EXISTS(SELECT 1 FROM assessment a JOIN care_plan p ON p.assessment_id=a.id WHERE a.elder_id=e.id AND a.ability_level='SEVERE' AND p.status='ACTIVE' AND a.valid_until>NOW());

-- A2 每个老人的评估次数
-- 左连接聚合与相关子查询；保留零次评估老人。
-- 实现 A
SELECT e.id,COUNT(a.id) n FROM elder e LEFT JOIN assessment a ON a.elder_id=e.id GROUP BY e.id;
-- 实现 B
SELECT e.id,(SELECT COUNT(*) FROM assessment a WHERE a.elder_id=e.id) n FROM elder e;

-- A3 没有生效计划的老人
-- 反连接与 NOT EXISTS，不使用含 NULL 风险的 NOT IN。
-- 实现 A
SELECT e.id FROM elder e LEFT JOIN care_plan p ON p.elder_id=e.id AND p.status='ACTIVE' WHERE p.id IS NULL;
-- 实现 B
SELECT e.id FROM elder e WHERE NOT EXISTS(SELECT 1 FROM care_plan p WHERE p.elder_id=e.id AND p.status='ACTIVE');

-- B1 有改派历史的订单
-- 多表连接去重与 EXISTS，结果粒度为订单。
-- 实现 A
SELECT DISTINCT o.id,e.name FROM service_order o JOIN elder e ON e.id=o.elder_id JOIN dispatch d ON d.order_id=o.id WHERE d.status='REASSIGNED';
-- 实现 B
SELECT o.id,e.name FROM service_order o JOIN elder e ON e.id=o.elder_id WHERE EXISTS(SELECT 1 FROM dispatch d WHERE d.order_id=o.id AND d.status='REASSIGNED');

-- B2 各社区订单金额
-- 聚合与相关子查询，空集合显式转零。
-- 实现 A
SELECT c.id,COALESCE(SUM(o.amount),0) total FROM community c LEFT JOIN service_order o ON o.community_id=c.id GROUP BY c.id;
-- 实现 B
SELECT c.id,COALESCE((SELECT SUM(o.amount) FROM service_order o WHERE o.community_id=c.id),0) total FROM community c;

-- B3 待确认订单
-- 保留订单粒度，防止多次派单重复统计。
-- 实现 A
SELECT DISTINCT o.id FROM service_order o JOIN dispatch d ON d.order_id=o.id JOIN service_record r ON r.dispatch_id=d.id WHERE o.status='CONFIRM' AND r.confirmed_at IS NULL;
-- 实现 B
SELECT o.id FROM service_order o WHERE o.status='CONFIRM' AND EXISTS(SELECT 1 FROM dispatch d JOIN service_record r ON r.dispatch_id=d.id WHERE d.order_id=o.id AND r.confirmed_at IS NULL);

-- C1 护工有效工时
-- 三表聚合与子查询。未服务护工仍返回零。
-- 实现 A
SELECT c.id,COALESCE(SUM(a.effective_minutes),0) minutes FROM caregiver c LEFT JOIN dispatch d ON d.caregiver_id=c.id LEFT JOIN attendance a ON a.dispatch_id=d.id AND a.status='VALID' GROUP BY c.id;
-- 实现 B
SELECT c.id,COALESCE((SELECT SUM(a.effective_minutes) FROM dispatch d JOIN attendance a ON a.dispatch_id=d.id WHERE d.caregiver_id=c.id AND a.status='VALID'),0) minutes FROM caregiver c;

-- C2 有空余名额的护工
-- 该查询只检查容量，不等同于完整派单资格推荐。
-- 实现 A
SELECT DISTINCT c.id FROM caregiver c JOIN shift s ON s.caregiver_id=c.id WHERE s.status='OPEN' AND s.used_count<s.capacity;
-- 实现 B
SELECT c.id FROM caregiver c WHERE EXISTS(SELECT 1 FROM shift s WHERE s.caregiver_id=c.id AND s.status='OPEN' AND s.used_count<s.capacity);

-- C3 证书到期护工
-- 到期清单与完整资质判断分离，一张证书到期不代表所有项目失效。
-- 实现 A
SELECT DISTINCT c.id FROM caregiver c JOIN credential x ON x.caregiver_id=c.id WHERE x.valid_until<NOW();
-- 实现 B
SELECT c.id FROM caregiver c WHERE EXISTS(SELECT 1 FROM credential x WHERE x.caregiver_id=c.id AND x.valid_until<NOW());

-- D1 老人异常体征数量
-- 绑定历史关联，不能仅通过设备当前归属统计。
-- 实现 A
SELECT e.id,COUNT(v.id) n FROM elder e LEFT JOIN device_binding b ON b.elder_id=e.id LEFT JOIN vital v ON v.binding_id=b.id AND v.abnormal=1 GROUP BY e.id;
-- 实现 B
SELECT e.id,(SELECT COUNT(*) FROM device_binding b JOIN vital v ON v.binding_id=b.id WHERE b.elder_id=e.id AND v.abnormal=1) n FROM elder e;

-- D2 存在未关闭事件的老人
-- 连接去重与 EXISTS，待确认事件仍纳入。
-- 实现 A
SELECT DISTINCT e.id FROM elder e JOIN emergency x ON x.elder_id=e.id WHERE x.status NOT IN('CLOSED','FALSE_ALARM');
-- 实现 B
SELECT e.id FROM elder e WHERE EXISTS(SELECT 1 FROM emergency x WHERE x.elder_id=e.id AND x.status NOT IN('CLOSED','FALSE_ALARM'));

-- D3 当前未绑定设备
-- 历史绑定不应排除当前空闲设备。
-- 实现 A
SELECT d.id FROM device d LEFT JOIN device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL WHERE b.id IS NULL;
-- 实现 B
SELECT d.id FROM device d WHERE NOT EXISTS(SELECT 1 FROM device_binding b WHERE b.device_id=d.id AND b.unbound_at IS NULL);

-- E1 按社区汇总已申报金额
-- 当前管理辖区统计口径；历史财政归属应改按政策辖区统计。
-- 实现 A
SELECT co.id,COALESCE(SUM(c.amount),0) amount FROM community co LEFT JOIN elder e ON e.community_id=co.id LEFT JOIN claim c ON c.elder_id=e.id AND c.status<>'DRAFT' GROUP BY co.id;
-- 实现 B
SELECT co.id,COALESCE((SELECT SUM(c.amount) FROM elder e JOIN claim c ON c.elder_id=e.id WHERE e.community_id=co.id AND c.status<>'DRAFT'),0) amount FROM community co;

-- E2 待拨付申报
-- 反连接与 NOT EXISTS。
-- 实现 A
SELECT c.id FROM claim c LEFT JOIN payment p ON p.claim_id=c.id WHERE c.status='APPROVED' AND p.id IS NULL;
-- 实现 B
SELECT c.id FROM claim c WHERE c.status='APPROVED' AND NOT EXISTS(SELECT 1 FROM payment p WHERE p.claim_id=c.id);

-- E3 各申报明细合计
-- 汇总后与主表金额比较，用于核算一致性检查。
-- 实现 A
SELECT c.id,COALESCE(SUM(i.amount),0) amount FROM claim c LEFT JOIN claim_item i ON i.claim_id=c.id GROUP BY c.id;
-- 实现 B
SELECT c.id,COALESCE((SELECT SUM(i.amount) FROM claim_item i WHERE i.claim_id=c.id),0) amount FROM claim c;