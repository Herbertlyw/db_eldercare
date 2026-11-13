-- Back up the database and stop all application writers before running.
DELIMITER $$
DROP PROCEDURE IF EXISTS `assert_plan`$$
CREATE PROCEDURE assert_plan(IN p_elder BIGINT,IN p_assess BIGINT,IN p_bundle BIGINT,IN p_start DATETIME,IN p_end DATETIME,IN p_id BIGINT)
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM elder WHERE id=p_elder FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM elder WHERE id=p_elder AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ELDER_INACTIVE'; END IF;
 IF NOT EXISTS(SELECT 1 FROM assessment a JOIN care_bundle b ON b.ability_level=a.ability_level WHERE a.id=p_assess AND a.elder_id=p_elder AND b.id=p_bundle AND a.assessed_at<=p_start AND a.valid_until>=p_end) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PLAN_ASSESSMENT_INVALID'; END IF;
 IF NOT(p_assess<=>(SELECT id FROM assessment WHERE elder_id=p_elder AND assessed_at<=NOW() ORDER BY assessed_at DESC,id DESC LIMIT 1)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PLAN_ASSESSMENT_SUPERSEDED'; END IF;
 IF EXISTS(SELECT 1 FROM v_plan_context WHERE elder_id=p_elder AND id<>p_id AND status='ACTIVE' AND starts_at<p_end AND ends_at>p_start) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PLAN_OVERLAP'; END IF;
END$$
DROP PROCEDURE IF EXISTS `expire_plans`$$
CREATE PROCEDURE expire_plans()
BEGIN
 UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id SET p.status=CASE WHEN a.valid_until<=NOW() THEN 'PAUSED' WHEN p.ends_at<=NOW() THEN 'EXPIRED' ELSE 'PAUSED' END WHERE p.status='ACTIVE' AND (a.valid_until<=NOW() OR p.ends_at<=NOW() OR EXISTS(SELECT 1 FROM assessment newer WHERE newer.elder_id=a.elder_id AND newer.assessed_at<=NOW() AND (newer.assessed_at>a.assessed_at OR (newer.assessed_at=a.assessed_at AND newer.id>a.id))));
END$$
DROP PROCEDURE IF EXISTS `assert_order`$$
CREATE PROCEDURE assert_order(IN p_elder BIGINT,IN p_item BIGINT,IN p_community BIGINT,IN p_start DATETIME,IN p_end DATETIME)
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM elder WHERE id=p_elder FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM elder WHERE id=p_elder AND community_id=p_community AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_COMMUNITY_INVALID'; END IF;
 IF NOT EXISTS(SELECT 1 FROM v_plan_context p JOIN assessment a ON a.id=p.assessment_id JOIN plan_item i ON i.plan_id=p.id WHERE p.elder_id=p_elder AND i.item_id=p_item AND p.status='ACTIVE' AND p.starts_at<=p_start AND p.ends_at>=p_end AND a.valid_until>=p_end AND a.valid_until>NOW() AND a.id=(SELECT id FROM assessment WHERE elder_id=p_elder AND assessed_at<=NOW() ORDER BY assessed_at DESC,id DESC LIMIT 1)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_PLAN_INVALID'; END IF;
END$$
DROP PROCEDURE IF EXISTS `escalate_events`$$
CREATE PROCEDURE escalate_events()
BEGIN
 INSERT IGNORE INTO emergency_log(event_id,level,message) SELECT id,level+1,'响应超时，升级并通知值班主管' FROM emergency WHERE status IN('OPEN','UNCONFIRMED') AND deadline<=NOW() AND level<3;
 UPDATE emergency SET level=level+1,deadline=DATE_ADD(NOW(),INTERVAL IF(level=2,180,60) SECOND) WHERE status IN('OPEN','UNCONFIRMED') AND deadline<=NOW() AND level<3;
 INSERT IGNORE INTO notification(event_id,account_id,message)
 SELECT e.id,a.id,CONCAT('应急事件待处理，级别 ',e.level)
 FROM emergency e JOIN elder x ON x.id=e.elder_id JOIN account a ON a.active=1
 WHERE e.status IN('OPEN','UNCONFIRMED') AND (
  (a.community_id=x.community_id AND (a.role='ADMIN' OR (a.role='REGULATOR' AND e.level>=2)))
  OR EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.elder_id AND g.account_id=a.id AND g.active=1)
  OR EXISTS(SELECT 1 FROM v_plan_context p JOIN caregiver c ON c.id=p.caregiver_id WHERE p.elder_id=e.elder_id AND p.status='ACTIVE' AND p.starts_at<=NOW() AND p.ends_at>NOW() AND c.account_id=a.id AND c.status='ACTIVE')
  OR EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.elder_id AND d.status IN('WAITING','ACCEPTED') AND c.account_id=a.id AND c.status='ACTIVE')
 );
END$$
DROP TRIGGER IF EXISTS `assessment_before_insert`$$
CREATE TRIGGER assessment_before_insert BEFORE INSERT ON assessment FOR EACH ROW
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM elder WHERE id=NEW.elder_id FOR UPDATE;
END$$
DROP TRIGGER IF EXISTS `assessment_pause_old_plans`$$
CREATE TRIGGER assessment_pause_old_plans AFTER INSERT ON assessment FOR EACH ROW
BEGIN
 IF NEW.assessed_at<=NOW() THEN
  UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id SET p.status='PAUSED'
  WHERE a.elder_id=NEW.elder_id AND p.status='ACTIVE' AND (a.assessed_at<NEW.assessed_at OR (a.assessed_at=NEW.assessed_at AND a.id<NEW.id));
 END IF;
END$$
DROP TRIGGER IF EXISTS `elder_transfer_binding_check`$$
CREATE TRIGGER elder_transfer_binding_check BEFORE UPDATE ON elder FOR EACH ROW
BEGIN
 IF NEW.community_id<>OLD.community_id AND EXISTS(SELECT 1 FROM device_binding WHERE elder_id=OLD.id AND unbound_at IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TRANSFER_DEVICE_BOUND'; END IF;
END$$
DROP TRIGGER IF EXISTS `immutable_emergency_log`$$
CREATE TRIGGER immutable_emergency_log BEFORE UPDATE ON emergency_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'$$
DROP TRIGGER IF EXISTS `keep_emergency_log`$$
CREATE TRIGGER keep_emergency_log BEFORE DELETE ON emergency_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORY_DELETE_FORBIDDEN'$$
DROP TRIGGER IF EXISTS `binding_before_insert`$$
CREATE TRIGGER binding_before_insert BEFORE INSERT ON device_binding FOR EACH ROW
BEGIN
 DECLARE guard_id BIGINT;
 SELECT id INTO guard_id FROM elder WHERE id=NEW.elder_id FOR UPDATE;
 SELECT id INTO guard_id FROM device WHERE id=NEW.device_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM device WHERE id=NEW.device_id AND status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='DEVICE_INACTIVE'; END IF;
 IF NEW.unbound_at IS NULL AND NOT EXISTS(SELECT 1 FROM elder e JOIN device d ON d.community_id=e.community_id WHERE e.id=NEW.elder_id AND d.id=NEW.device_id AND e.status='ACTIVE') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BINDING_COMMUNITY_INVALID'; END IF;
 IF EXISTS(SELECT 1 FROM device_binding b WHERE b.device_id=NEW.device_id AND b.bound_at<COALESCE(NEW.unbound_at,'9999-12-31 23:59:59.999999') AND COALESCE(b.unbound_at,'9999-12-31 23:59:59.999999')>NEW.bound_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BINDING_OVERLAP'; END IF;
END$$
DROP TRIGGER IF EXISTS `dispatch_accept_check`$$
CREATE TRIGGER dispatch_accept_check BEFORE UPDATE ON dispatch FOR EACH ROW
BEGIN
 IF OLD.status IN('FINISHED','REASSIGNED','REJECTED') OR NEW.order_id<>OLD.order_id OR NEW.shift_id<>OLD.shift_id OR NEW.assigned_at<>OLD.assigned_at OR (OLD.accepted_at IS NOT NULL AND NOT(NEW.accepted_at<=>OLD.accepted_at)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HISTORICAL_FACT_IMMUTABLE'; END IF;
 IF (OLD.status='WAITING' AND NEW.status NOT IN('WAITING','ACCEPTED','REASSIGNED','REJECTED')) OR (OLD.status='ACCEPTED' AND NEW.status NOT IN('ACCEPTED','FINISHED','REASSIGNED','REJECTED')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='DISPATCH_STATE_INVALID'; END IF;
 IF NEW.status='ACCEPTED' AND OLD.status<>NEW.status THEN CALL assert_worker_order(NEW.order_id,NEW.shift_id); END IF;
END$$
DELIMITER ;
