-- 2026-11-12 integrity audit. Stop the app and take a full backup before applying.
-- Preserves business rows; this file is for an existing database only.
ALTER TABLE device_binding MODIFY bound_at DATETIME(6) NOT NULL, MODIFY unbound_at DATETIME(6) NULL;
ALTER TABLE attendance MODIFY check_in DATETIME(6) NOT NULL, MODIFY check_out DATETIME(6) NULL;
DROP PROCEDURE IF EXISTS assert_plan;
DROP TRIGGER IF EXISTS plan_before_insert;
DROP TRIGGER IF EXISTS plan_before_update;
DROP PROCEDURE IF EXISTS expire_plans;
DROP PROCEDURE IF EXISTS assert_shift;
DROP TRIGGER IF EXISTS shift_before_insert;
DROP TRIGGER IF EXISTS shift_before_update;
DROP PROCEDURE IF EXISTS assert_order;
DROP TRIGGER IF EXISTS order_before_insert;
DROP TRIGGER IF EXISTS order_before_update;
DROP PROCEDURE IF EXISTS assert_worker_order;
DROP PROCEDURE IF EXISTS assign_order;
DROP PROCEDURE IF EXISTS verify_hours;
DROP TRIGGER IF EXISTS vital_before_insert;
DROP TRIGGER IF EXISTS vital_after_insert;
DROP PROCEDURE IF EXISTS escalate_events;
DROP PROCEDURE IF EXISTS submit_claim;
DROP TRIGGER IF EXISTS claim_before_update;
DROP TRIGGER IF EXISTS audit_care_plan_insert;
DROP TRIGGER IF EXISTS audit_care_plan_update;
DROP TRIGGER IF EXISTS audit_service_order_insert;
DROP TRIGGER IF EXISTS audit_service_order_update;
DROP TRIGGER IF EXISTS audit_dispatch_insert;
DROP TRIGGER IF EXISTS audit_dispatch_update;
DROP TRIGGER IF EXISTS audit_claim_insert;
DROP TRIGGER IF EXISTS audit_claim_update;
DROP TRIGGER IF EXISTS audit_attendance_insert;
DROP TRIGGER IF EXISTS audit_attendance_update;
DROP TRIGGER IF EXISTS audit_emergency_insert;
DROP TRIGGER IF EXISTS audit_emergency_update;
DROP TRIGGER IF EXISTS audit_inspection_insert;
DROP TRIGGER IF EXISTS audit_inspection_update;
DROP TRIGGER IF EXISTS keep_assessment;
DROP TRIGGER IF EXISTS keep_dispatch;
DROP TRIGGER IF EXISTS keep_service_record;
DROP TRIGGER IF EXISTS keep_attendance;
DROP TRIGGER IF EXISTS keep_vital;
DROP TRIGGER IF EXISTS keep_claim;
DROP TRIGGER IF EXISTS keep_claim_item;
DROP TRIGGER IF EXISTS keep_claim_review;
DROP TRIGGER IF EXISTS keep_payment;
DROP TRIGGER IF EXISTS keep_audit_log;
DROP TRIGGER IF EXISTS immutable_audit_log;
DROP TRIGGER IF EXISTS immutable_payment;
DROP TRIGGER IF EXISTS immutable_claim_item;
DROP TRIGGER IF EXISTS immutable_claim_review;
DROP TRIGGER IF EXISTS immutable_vital;
DROP TRIGGER IF EXISTS confirmed_record_immutable;
DROP TRIGGER IF EXISTS binding_before_insert;
DROP TRIGGER IF EXISTS binding_before_update;
DROP TRIGGER IF EXISTS keep_binding;
DROP TRIGGER IF EXISTS dispatch_accept_check;
DROP TRIGGER IF EXISTS used_shift_before_update;
DROP TRIGGER IF EXISTS emergency_notify;
DROP TRIGGER IF EXISTS payment_before_insert;
DELIMITER $$
CREATE PROCEDURE assert_plan(IN p_elder BIGINT,IN p_assess BIGINT,IN p_bundle BIGINT,IN p_start DATETIME,IN p_end DATETIME,IN p_id BIGINT)
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM elder WHERE id=p_elder FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM elder WHERE id=p_elder AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ELDER_INACTIVE'; END IF;
 IF NOT EXISTS(SELECT 1 FROM assessment a JOIN care_bundle b ON b.ability_level=a.ability_level WHERE a.id=p_assess AND a.elder_id=p_elder AND b.id=p_bundle AND a.assessed_at<=p_start AND a.valid_until>=p_end) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PLAN_ASSESSMENT_INVALID'; END IF;
 IF EXISTS(SELECT 1 FROM v_plan_context WHERE elder_id=p_elder AND id<>p_id AND status='ACTIVE' AND starts_at<p_end AND ends_at>p_start) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PLAN_OVERLAP'; END IF;
END$$
CREATE TRIGGER plan_before_insert BEFORE INSERT ON care_plan FOR EACH ROW BEGIN IF NEW.status='ACTIVE' THEN CALL assert_plan((SELECT elder_id FROM assessment WHERE id=NEW.assessment_id),NEW.assessment_id,NEW.bundle_id,NEW.starts_at,NEW.ends_at,0); END IF; END$$
CREATE TRIGGER plan_before_update BEFORE UPDATE ON care_plan FOR EACH ROW BEGIN IF NEW.status='ACTIVE' THEN CALL assert_plan((SELECT elder_id FROM assessment WHERE id=NEW.assessment_id),NEW.assessment_id,NEW.bundle_id,NEW.starts_at,NEW.ends_at,NEW.id); END IF; END$$
CREATE PROCEDURE expire_plans()
BEGIN
 UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id SET p.status=CASE WHEN p.ends_at<=NOW() THEN 'EXPIRED' ELSE 'PAUSED' END WHERE p.status='ACTIVE' AND (a.valid_until<=NOW() OR p.ends_at<=NOW());
END$$
CREATE PROCEDURE assert_shift(IN p_worker BIGINT,IN p_start DATETIME,IN p_end DATETIME,IN p_id BIGINT)
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM caregiver WHERE id=p_worker FOR UPDATE;
 IF EXISTS(SELECT 1 FROM shift WHERE caregiver_id=p_worker AND id<>p_id AND status='OPEN' AND starts_at<p_end AND ends_at>p_start) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SHIFT_OVERLAP'; END IF;
END$$
CREATE TRIGGER shift_before_insert BEFORE INSERT ON shift FOR EACH ROW BEGIN CALL assert_shift(NEW.caregiver_id,NEW.starts_at,NEW.ends_at,0); END$$
CREATE TRIGGER shift_before_update BEFORE UPDATE ON shift FOR EACH ROW BEGIN IF NEW.status='OPEN' THEN CALL assert_shift(NEW.caregiver_id,NEW.starts_at,NEW.ends_at,NEW.id); END IF; END$$
CREATE PROCEDURE assert_order(IN p_elder BIGINT,IN p_item BIGINT,IN p_community BIGINT,IN p_start DATETIME,IN p_end DATETIME)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM elder WHERE id=p_elder AND community_id=p_community AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_COMMUNITY_INVALID'; END IF;
 IF NOT EXISTS(SELECT 1 FROM v_plan_context p JOIN assessment a ON a.id=p.assessment_id JOIN plan_item i ON i.plan_id=p.id WHERE p.elder_id=p_elder AND i.item_id=p_item AND p.status='ACTIVE' AND p.starts_at<=p_start AND p.ends_at>=p_end AND a.valid_until>=p_end AND a.valid_until>NOW()) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_PLAN_INVALID'; END IF;
END$$
CREATE TRIGGER order_before_insert BEFORE INSERT ON service_order FOR EACH ROW BEGIN CALL assert_order(NEW.elder_id,NEW.item_id,NEW.community_id,NEW.starts_at,NEW.ends_at); END$$
CREATE TRIGGER order_before_update BEFORE UPDATE ON service_order FOR EACH ROW
BEGIN
 IF OLD.status='COMPLETED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='COMPLETED_ORDER_IMMUTABLE'; END IF;
 IF NEW.status='COMPLETED' AND NOT EXISTS(SELECT 1 FROM v_dispatch_context d JOIN service_record r ON r.dispatch_id=d.id WHERE d.order_id=NEW.id AND d.status='FINISHED' AND r.confirmed_by IS NOT NULL AND r.confirmed_at IS NOT NULL AND r.confirmation IS NOT NULL AND EXISTS(SELECT 1 FROM guardian g JOIN account ac ON ac.id=g.account_id WHERE g.elder_id=NEW.elder_id AND g.account_id=r.confirmed_by AND ac.role='FAMILY')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONFIRMATION_REQUIRED'; END IF;
END$$
-- Assignment, acceptance and check-in share the same eligibility checks.
CREATE PROCEDURE assert_worker_order(IN p_order BIGINT,IN p_shift BIGINT)
BEGIN
 DECLARE v_worker,v_guard,v_elder,v_item,v_community BIGINT;
 DECLARE v_start,v_end DATETIME;
 SELECT elder_id,item_id,community_id,starts_at,ends_at INTO v_elder,v_item,v_community,v_start,v_end FROM service_order WHERE id=p_order;
 CALL assert_order(v_elder,v_item,v_community,v_start,v_end);
 SELECT caregiver_id INTO v_worker FROM shift WHERE id=p_shift;
 SELECT id INTO v_guard FROM caregiver WHERE id=v_worker FOR UPDATE;
 IF v_worker IS NULL OR NOT EXISTS(SELECT 1 FROM caregiver c JOIN account ac ON ac.id=c.account_id JOIN caregiver_area a ON a.caregiver_id=c.id JOIN credential x ON x.caregiver_id=c.id WHERE c.id=v_worker AND c.status='ACTIVE' AND ac.active=1 AND a.community_id=v_community AND x.item_id=v_item AND x.valid_from<=LEAST(NOW(),v_start) AND x.valid_until>=GREATEST(NOW(),v_end)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WORKER_NOT_QUALIFIED'; END IF;
 IF NOT EXISTS(SELECT 1 FROM shift WHERE id=p_shift AND status='OPEN' AND starts_at<=v_start AND ends_at>=v_end) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SHIFT_FULL_OR_OUTSIDE'; END IF;
 IF EXISTS(SELECT 1 FROM inspection i WHERE i.status='OPEN' AND (i.caregiver_id=v_worker OR i.order_id IN(SELECT order_id FROM v_dispatch_context WHERE caregiver_id=v_worker AND status IN('ACCEPTED','FINISHED')))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RECTIFICATION_REQUIRED'; END IF;
 IF EXISTS(SELECT 1 FROM v_dispatch_context d JOIN service_order o ON o.id=d.order_id WHERE d.caregiver_id=v_worker AND d.status IN('WAITING','ACCEPTED') AND o.id<>p_order AND o.starts_at<v_end AND o.ends_at>v_start) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WORKER_TIME_CONFLICT'; END IF;
END$$
CREATE PROCEDURE assign_order(IN p_order BIGINT,IN p_shift BIGINT,IN p_reason VARCHAR(200))
BEGIN
 DECLARE v_elder,v_item,v_community,v_worker,v_guard,v_old_shift BIGINT;
 DECLARE v_start,v_end DATETIME;
 DECLARE v_state VARCHAR(30);
 SELECT elder_id,item_id,community_id,starts_at,ends_at,status INTO v_elder,v_item,v_community,v_start,v_end,v_state FROM service_order WHERE id=p_order FOR UPDATE;
 IF v_state IS NULL OR v_state NOT IN ('PENDING','ASSIGNED','ACCEPTED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_NOT_DISPATCHABLE'; END IF;
 CALL assert_worker_order(p_order,p_shift);
 SET v_old_shift=(SELECT shift_id FROM v_dispatch_context WHERE order_id=p_order AND active_order IS NOT NULL);
 IF v_old_shift IS NOT NULL THEN
  IF p_reason IS NULL OR LENGTH(TRIM(p_reason))<2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='REASSIGN_REASON_REQUIRED'; END IF;
  UPDATE dispatch SET status='REASSIGNED',reason=p_reason WHERE order_id=p_order AND active_order IS NOT NULL;
  UPDATE shift SET used_count=used_count-1 WHERE id=v_old_shift;
 END IF;
 UPDATE shift SET used_count=used_count+1 WHERE id=p_shift AND status='OPEN' AND starts_at<=v_start AND ends_at>=v_end AND used_count<capacity;
 IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SHIFT_FULL_OR_OUTSIDE'; END IF;
 INSERT INTO dispatch(order_id,shift_id,reason) VALUES(p_order,p_shift,COALESCE(p_reason,'首次派单'));
 UPDATE service_order SET status='ASSIGNED' WHERE id=p_order;
END$$
CREATE PROCEDURE verify_hours(IN p_dispatch BIGINT)
BEGIN
 UPDATE attendance a JOIN v_dispatch_context d ON d.id=a.dispatch_id JOIN service_order o ON o.id=d.order_id SET a.status=IF(a.fence_ok AND a.check_in>=o.starts_at-INTERVAL 30 MINUTE AND a.check_out<=o.ends_at+INTERVAL 30 MINUTE,'VALID','REVIEW'),a.effective_minutes=IF(a.fence_ok AND a.check_in>=o.starts_at-INTERVAL 30 MINUTE AND a.check_out<=o.ends_at+INTERVAL 30 MINUTE,TIMESTAMPDIFF(MINUTE,a.check_in,a.check_out),0) WHERE a.dispatch_id=p_dispatch AND a.check_out IS NOT NULL AND a.status='PENDING';
END$$
CREATE TRIGGER vital_before_insert BEFORE INSERT ON vital FOR EACH ROW
BEGIN
 DECLARE v_low,v_high DECIMAL(12,3);
 IF NEW.collected_at>NOW(6) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='VITAL_FUTURE_TIME'; END IF;
 IF NOT EXISTS(SELECT 1 FROM device_binding WHERE id=NEW.binding_id AND bound_at<=NEW.collected_at AND (unbound_at IS NULL OR unbound_at>NEW.collected_at)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='VITAL_BINDING_TIME_INVALID'; END IF;
 SELECT low_value,high_value INTO v_low,v_high FROM metric_rule WHERE id=NEW.metric_rule_id;
 SET NEW.abnormal=(NEW.metric_value<v_low OR NEW.metric_value>v_high);
END$$
CREATE TRIGGER vital_after_insert AFTER INSERT ON vital FOR EACH ROW
BEGIN
 IF NEW.abnormal THEN
 INSERT INTO emergency(elder_id,vital_id,source,deadline,status) SELECT b.elder_id,NEW.id,'DEVICE',DATE_ADD(NOW(),INTERVAL m.response_seconds SECOND),'UNCONFIRMED' FROM device_binding b JOIN metric_rule m ON m.id=NEW.metric_rule_id WHERE b.id=NEW.binding_id;
 END IF;
END$$
CREATE PROCEDURE escalate_events()
BEGIN
 INSERT IGNORE INTO emergency_log(event_id,level,message) SELECT id,level+1,'响应超时，升级并通知值班主管' FROM emergency WHERE status IN('OPEN','UNCONFIRMED') AND deadline<=NOW() AND level<3;
 UPDATE emergency SET level=level+1,deadline=DATE_ADD(NOW(),INTERVAL IF(level=2,180,60) SECOND) WHERE status IN('OPEN','UNCONFIRMED') AND deadline<=NOW() AND level<3;
 INSERT IGNORE INTO notification(event_id,account_id,message)
 SELECT e.id,a.id,CONCAT('应急事件待处理，级别 ',e.level)
 FROM emergency e JOIN elder x ON x.id=e.elder_id JOIN account a ON a.active=1
 WHERE e.status IN('OPEN','UNCONFIRMED') AND (
  (a.community_id=x.community_id AND a.role IN('ADMIN','REGULATOR'))
  OR EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.elder_id AND g.account_id=a.id)
  OR EXISTS(SELECT 1 FROM v_plan_context p JOIN caregiver c ON c.id=p.caregiver_id WHERE p.elder_id=e.elder_id AND p.status='ACTIVE' AND p.starts_at<=NOW() AND p.ends_at>NOW() AND c.account_id=a.id AND c.status='ACTIVE')
  OR EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.elder_id AND d.status IN('WAITING','ACCEPTED') AND c.account_id=a.id AND c.status='ACTIVE')
 );
END$$
CREATE PROCEDURE submit_claim(IN p_elder BIGINT,IN p_policy BIGINT,IN p_period CHAR(7),IN p_orders JSON)
BEGIN
 DECLARE v_guard,v_item,v_claim,v_order BIGINT;
 DECLARE v_rate,v_amount,v_price DECIMAL(12,2);
 DECLARE v_minutes,v_i INT DEFAULT 0;
 DECLARE v_unit VARCHAR(10);
 SELECT id INTO v_guard FROM elder WHERE id=p_elder FOR UPDATE;
 SELECT item_id,rate INTO v_item,v_rate FROM policy WHERE id=p_policy;
 IF v_item IS NULL OR p_orders IS NULL OR JSON_TYPE(p_orders)<>'ARRAY' OR JSON_LENGTH(p_orders)=0 OR p_period NOT REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_INPUT_INVALID'; END IF;
 INSERT INTO claim(elder_id,item_id,period) VALUES(p_elder,v_item,p_period);
 SET v_claim=LAST_INSERT_ID();
 WHILE v_i<JSON_LENGTH(p_orders) DO
 SET v_order=CAST(JSON_UNQUOTE(JSON_EXTRACT(p_orders,CONCAT('$[',v_i,']'))) AS UNSIGNED);
 IF NOT EXISTS(SELECT 1 FROM service_order o JOIN policy p ON p.id=p_policy JOIN elder e ON e.id=o.elder_id JOIN service_item s ON s.id=o.item_id WHERE o.id=v_order AND o.elder_id=p_elder AND o.item_id=v_item AND o.status='COMPLETED' AND o.community_id=p.community_id AND o.starts_at>=p.starts_at AND o.ends_at<=p.ends_at AND DATE_FORMAT(o.starts_at,'%Y-%m')=p_period AND s.subsidizable=1 AND EXISTS(SELECT 1 FROM assessment a WHERE a.elder_id=e.id AND a.ability_level=p.ability_level AND a.assessed_at<=o.starts_at AND a.valid_until>=o.ends_at)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_ORDER_INELIGIBLE'; END IF;
 SET v_minutes=(SELECT a.effective_minutes FROM attendance a JOIN v_dispatch_context d ON d.id=a.dispatch_id JOIN service_record r ON r.dispatch_id=d.id WHERE d.order_id=v_order AND d.status='FINISHED' AND a.status='VALID' AND r.confirmed_by IS NOT NULL LIMIT 1);
 IF v_minutes IS NULL OR v_minutes<=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_EVIDENCE_MISSING'; END IF;
 SELECT o.amount,s.unit INTO v_price,v_unit FROM service_order o JOIN service_item s ON s.id=o.item_id WHERE o.id=v_order;
 SET v_amount=LEAST(v_price,ROUND(IF(v_unit='HOUR',v_minutes/60*v_rate,v_rate),2));
 IF v_amount<=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_ZERO_AMOUNT'; END IF;
 INSERT INTO claim_item(claim_id,policy_id,order_id,amount,effective_minutes,rate_snapshot) VALUES(v_claim,p_policy,v_order,v_amount,v_minutes,v_rate);
 SET v_i=v_i+1;
 END WHILE;
 UPDATE claim SET amount=(SELECT SUM(amount) FROM claim_item WHERE claim_id=v_claim),status='SUBMITTED' WHERE id=v_claim;
END$$
CREATE TRIGGER claim_before_update BEFORE UPDATE ON claim FOR EACH ROW
BEGIN
 IF OLD.status='PAID' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PAID_CLAIM_IMMUTABLE'; END IF;
 IF NEW.status<>OLD.status AND NOT ((OLD.status='DRAFT' AND NEW.status='SUBMITTED') OR (OLD.status='SUBMITTED' AND NEW.status IN('STREET_APPROVED','REJECTED')) OR (OLD.status='STREET_APPROVED' AND NEW.status IN('APPROVED','REJECTED')) OR (OLD.status='APPROVED' AND NEW.status='PAID')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_STATE_INVALID'; END IF;
 IF NEW.status='PAID' AND NOT EXISTS(SELECT 1 FROM payment WHERE claim_id=NEW.id AND amount=NEW.amount) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PAYMENT_EVIDENCE_REQUIRED'; END IF;
 IF NEW.status<>'DRAFT' AND NEW.amount<>(SELECT COALESCE(SUM(amount),0) FROM claim_item WHERE claim_id=NEW.id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLAIM_AMOUNT_MISMATCH'; END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER audit_care_plan_insert AFTER INSERT ON care_plan FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('care_plan',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_care_plan_update AFTER UPDATE ON care_plan FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('care_plan',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_service_order_insert AFTER INSERT ON service_order FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('service_order',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_service_order_update AFTER UPDATE ON service_order FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('service_order',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_dispatch_insert AFTER INSERT ON dispatch FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('dispatch',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_dispatch_update AFTER UPDATE ON dispatch FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('dispatch',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_claim_insert AFTER INSERT ON claim FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('claim',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_claim_update AFTER UPDATE ON claim FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('claim',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_attendance_insert AFTER INSERT ON attendance FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('attendance',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_attendance_update AFTER UPDATE ON attendance FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('attendance',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_emergency_insert AFTER INSERT ON emergency FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('emergency',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_emergency_update AFTER UPDATE ON emergency FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('emergency',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER audit_inspection_insert AFTER INSERT ON inspection FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('inspection',NEW.id,'INSERT',COALESCE(@actor,'database'),NULL,NEW.status)$$
CREATE TRIGGER audit_inspection_update AFTER UPDATE ON inspection FOR EACH ROW INSERT INTO audit_log(table_name,row_id,operation,actor,old_status,new_status) VALUES('inspection',NEW.id,'UPDATE',COALESCE(@actor,'database'),OLD.status,NEW.status)$$
CREATE TRIGGER keep_assessment BEFORE DELETE ON assessment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_dispatch BEFORE DELETE ON dispatch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_service_record BEFORE DELETE ON service_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_attendance BEFORE DELETE ON attendance FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_vital BEFORE DELETE ON vital FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_claim BEFORE DELETE ON claim FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_claim_item BEFORE DELETE ON claim_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_claim_review BEFORE DELETE ON claim_review FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_payment BEFORE DELETE ON payment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER keep_audit_log BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER immutable_audit_log BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
CREATE TRIGGER immutable_payment BEFORE UPDATE ON payment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
CREATE TRIGGER immutable_claim_item BEFORE UPDATE ON claim_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
CREATE TRIGGER immutable_claim_review BEFORE UPDATE ON claim_review FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
CREATE TRIGGER immutable_vital BEFORE UPDATE ON vital FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
CREATE TRIGGER confirmed_record_immutable BEFORE UPDATE ON service_record FOR EACH ROW BEGIN IF OLD.confirmed_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONFIRMED_RECORD_IMMUTABLE'; END IF; END$$
DELIMITER ;

-- Keep historical ownership and time intervals stable after data have been collected.
DELIMITER $$
CREATE TRIGGER binding_before_insert BEFORE INSERT ON device_binding FOR EACH ROW
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM device WHERE id=NEW.device_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM device WHERE id=NEW.device_id AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='DEVICE_INACTIVE'; END IF;
 IF EXISTS(SELECT 1 FROM device_binding b WHERE b.device_id=NEW.device_id AND b.bound_at<COALESCE(NEW.unbound_at,'9999-12-31 23:59:59.999999') AND COALESCE(b.unbound_at,'9999-12-31 23:59:59.999999')>NEW.bound_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BINDING_OVERLAP'; END IF;
END$$
CREATE TRIGGER binding_before_update BEFORE UPDATE ON device_binding FOR EACH ROW
BEGIN
 IF NEW.device_id<>OLD.device_id OR NEW.elder_id<>OLD.elder_id OR NEW.bound_at<>OLD.bound_at OR OLD.unbound_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'; END IF;
 IF NEW.unbound_at IS NULL OR EXISTS(SELECT 1 FROM vital WHERE binding_id=OLD.id AND collected_at>=NEW.unbound_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='VITAL_BINDING_TIME_INVALID'; END IF;
END$$
CREATE TRIGGER keep_binding BEFORE DELETE ON device_binding FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
CREATE TRIGGER dispatch_accept_check BEFORE UPDATE ON dispatch FOR EACH ROW
BEGIN
 IF NEW.order_id<>OLD.order_id OR NEW.shift_id<>OLD.shift_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'; END IF;
 IF NEW.status='ACCEPTED' AND OLD.status<>NEW.status THEN CALL assert_worker_order(NEW.order_id,NEW.shift_id); END IF;
END$$
CREATE TRIGGER used_shift_before_update BEFORE UPDATE ON shift FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM dispatch WHERE shift_id=OLD.id) AND (NEW.caregiver_id<>OLD.caregiver_id OR NEW.starts_at<>OLD.starts_at OR NEW.ends_at<>OLD.ends_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'; END IF;
END$$
CREATE TRIGGER emergency_notify AFTER INSERT ON emergency FOR EACH ROW
BEGIN
 INSERT IGNORE INTO notification(event_id,account_id,message)
 SELECT NEW.id,a.id,CONCAT('应急事件待处理，级别 ',NEW.level) FROM account a JOIN elder e ON e.id=NEW.elder_id
 WHERE a.active=1 AND (
  (a.community_id=e.community_id AND (a.role='ADMIN' OR (a.role='REGULATOR' AND NEW.level>=2)))
  OR EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.id AND g.account_id=a.id)
  OR EXISTS(SELECT 1 FROM v_plan_context p JOIN caregiver c ON c.id=p.caregiver_id WHERE p.elder_id=e.id AND p.status='ACTIVE' AND p.starts_at<=NOW() AND p.ends_at>NOW() AND c.account_id=a.id AND c.status='ACTIVE')
  OR EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.id AND d.status IN('WAITING','ACCEPTED') AND c.account_id=a.id AND c.status='ACTIVE')
 );
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER payment_before_insert BEFORE INSERT ON payment FOR EACH ROW
BEGIN
 DECLARE v_amount DECIMAL(12,2);
 DECLARE v_status VARCHAR(30);
 SELECT amount,status INTO v_amount,v_status FROM claim WHERE id=NEW.claim_id FOR UPDATE;
 IF v_status IS NULL OR v_status<>'APPROVED' OR NEW.amount<>v_amount THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PAYMENT_AMOUNT_OR_STATE_INVALID'; END IF;
END$$
DELIMITER ;

CREATE OR REPLACE VIEW v_assessment_due AS SELECT e.id,e.name,e.community_id,a.id assessment_id,a.valid_until FROM elder e JOIN assessment a ON a.elder_id=e.id WHERE a.valid_until BETWEEN NOW() AND NOW()+INTERVAL 30 DAY;
CREATE OR REPLACE VIEW v_active_plan AS SELECT p.id,p.elder_id,e.name,e.community_id,p.starts_at,p.ends_at,c.name caregiver_name FROM v_plan_context p JOIN elder e ON e.id=p.elder_id JOIN caregiver c ON c.id=p.caregiver_id WHERE p.status='ACTIVE' AND NOW()>=p.starts_at AND NOW()<p.ends_at;
CREATE OR REPLACE VIEW v_family_care AS SELECT g.account_id,e.id elder_id,e.name,e.community_id,p.id plan_id,p.status FROM guardian g JOIN elder e ON e.id=g.elder_id LEFT JOIN v_plan_context p ON p.elder_id=e.id AND p.status='ACTIVE';
CREATE OR REPLACE VIEW v_dispatch_overdue AS SELECT o.id,o.elder_id,o.community_id,o.starts_at,o.status FROM service_order o WHERE (o.status='PENDING' AND o.created_at<NOW()-INTERVAL 15 MINUTE) OR (o.status='ASSIGNED' AND EXISTS(SELECT 1 FROM dispatch d WHERE d.order_id=o.id AND d.status='WAITING' AND d.assigned_at<NOW()-INTERVAL 15 MINUTE));
CREATE OR REPLACE VIEW v_wait_confirmation AS SELECT o.id,o.elder_id,o.community_id,r.id record_id FROM service_order o JOIN dispatch d ON d.order_id=o.id JOIN service_record r ON r.dispatch_id=d.id WHERE o.status='CONFIRM' AND r.confirmed_at IS NULL;
CREATE OR REPLACE VIEW v_reassign_history AS SELECT d.id,d.order_id,d.caregiver_id,c.name,d.reason,d.assigned_at,o.community_id FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id JOIN service_order o ON o.id=d.order_id WHERE d.status='REASSIGNED';
CREATE OR REPLACE VIEW v_credential_due AS SELECT x.id,c.name,c.organization_id,x.item_id,x.valid_until FROM credential x JOIN caregiver c ON c.id=x.caregiver_id WHERE x.valid_until<NOW()+INTERVAL 30 DAY;
CREATE OR REPLACE VIEW v_shift_capacity AS SELECT s.id,s.caregiver_id,c.organization_id,s.starts_at,s.ends_at,s.capacity-s.used_count available FROM shift s JOIN caregiver c ON c.id=s.caregiver_id WHERE s.status='OPEN';
CREATE OR REPLACE VIEW v_hours_review AS SELECT a.id,d.caregiver_id,c.organization_id,d.order_id,a.check_in,a.check_out,a.location_note,a.status FROM attendance a JOIN v_dispatch_context d ON d.id=a.dispatch_id JOIN caregiver c ON c.id=d.caregiver_id WHERE a.status='REVIEW';
CREATE OR REPLACE VIEW v_device_current AS SELECT d.id,d.serial_no,d.status,b.elder_id,e.community_id,b.bound_at FROM device d LEFT JOIN device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL LEFT JOIN elder e ON e.id=b.elder_id;
CREATE OR REPLACE VIEW v_abnormal_vital AS SELECT v.id,b.elder_id,e.community_id,m.metric,v.metric_value,v.collected_at FROM vital v JOIN device_binding b ON b.id=v.binding_id JOIN elder e ON e.id=b.elder_id JOIN metric_rule m ON m.id=v.metric_rule_id WHERE v.abnormal=1;
CREATE OR REPLACE VIEW v_emergency_pending AS SELECT x.id,x.elder_id,e.community_id,x.status,x.level,x.deadline,TIMESTAMPDIFF(SECOND,NOW(),x.deadline) remaining_seconds FROM emergency x JOIN elder e ON e.id=x.elder_id WHERE x.status NOT IN('CLOSED','FALSE_ALARM');
CREATE OR REPLACE VIEW v_claim_eligible AS SELECT o.id,o.elder_id,o.item_id,o.community_id,o.amount,a.effective_minutes FROM service_order o JOIN dispatch d ON d.order_id=o.id JOIN attendance a ON a.dispatch_id=d.id JOIN service_record r ON r.dispatch_id=d.id JOIN service_item s ON s.id=o.item_id WHERE o.status='COMPLETED' AND d.status='FINISHED' AND r.confirmed_at IS NOT NULL AND a.status='VALID' AND s.subsidizable=1 AND NOT EXISTS(SELECT 1 FROM claim_item ci WHERE ci.order_id=o.id);
CREATE OR REPLACE VIEW v_claim_progress AS SELECT c.id,c.elder_id,e.community_id,c.period,c.amount,c.status,p.reference_no,p.paid_at FROM claim c JOIN elder e ON e.id=c.elder_id LEFT JOIN payment p ON p.claim_id=c.id;
CREATE OR REPLACE VIEW v_rectification AS SELECT i.id,i.order_id,i.caregiver_id,i.issue,i.rectification,a.community_id FROM inspection i JOIN account a ON a.id=i.inspector_id WHERE i.status='OPEN';
