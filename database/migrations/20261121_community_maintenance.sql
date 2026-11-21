-- Pause scheduled and manual maintenance while replacing these routines.
DELIMITER $$
DROP PROCEDURE IF EXISTS expire_plans$$
DROP PROCEDURE IF EXISTS expire_community_plans$$
CREATE PROCEDURE expire_community_plans(IN p_community BIGINT)
BEGIN
 UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id JOIN elder e ON e.id=a.elder_id
 SET p.status=CASE WHEN a.valid_until<=NOW() THEN 'PAUSED' WHEN p.ends_at<=NOW() THEN 'EXPIRED' ELSE 'PAUSED' END
 WHERE p.status='ACTIVE' AND (p_community IS NULL OR e.community_id=p_community)
 AND (a.valid_until<=NOW() OR p.ends_at<=NOW() OR EXISTS(SELECT 1 FROM assessment newer WHERE newer.elder_id=a.elder_id AND newer.assessed_at<=NOW() AND (newer.assessed_at>a.assessed_at OR (newer.assessed_at=a.assessed_at AND newer.id>a.id))));
END$$
CREATE PROCEDURE expire_plans()
BEGIN
 CALL expire_community_plans(NULL);
END$$
DROP PROCEDURE IF EXISTS escalate_events$$
DROP PROCEDURE IF EXISTS escalate_community_events$$
CREATE PROCEDURE escalate_community_events(IN p_community BIGINT)
BEGIN
 INSERT IGNORE INTO emergency_log(event_id,level,message)
 SELECT e.id,e.level+1,'响应超时，升级并通知值班主管' FROM emergency e JOIN elder x ON x.id=e.elder_id
 WHERE e.status IN('OPEN','UNCONFIRMED') AND e.deadline<=NOW() AND e.level<3
 AND (p_community IS NULL OR x.community_id=p_community);
 UPDATE emergency SET deadline=DATE_ADD(NOW(),INTERVAL IF(level=1,180,60) SECOND),level=level+1
 WHERE status IN('OPEN','UNCONFIRMED') AND deadline<=NOW() AND level<3
 AND (p_community IS NULL OR EXISTS(SELECT 1 FROM elder x WHERE x.id=emergency.elder_id AND x.community_id=p_community));
 INSERT IGNORE INTO notification(event_id,account_id,message)
 SELECT e.id,a.id,CONCAT('应急事件待处理，级别 ',e.level)
 FROM emergency e JOIN elder x ON x.id=e.elder_id JOIN account a ON a.active=1
 WHERE e.status IN('OPEN','UNCONFIRMED') AND (p_community IS NULL OR x.community_id=p_community) AND (
  (a.community_id=x.community_id AND (a.role='ADMIN' OR (a.role='REGULATOR' AND e.level>=2)))
  OR EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.elder_id AND g.account_id=a.id AND g.active=1)
  OR EXISTS(SELECT 1 FROM v_plan_context p JOIN caregiver c ON c.id=p.caregiver_id WHERE p.elder_id=e.elder_id AND p.status='ACTIVE' AND p.starts_at<=NOW() AND p.ends_at>NOW() AND c.account_id=a.id AND c.status='ACTIVE')
  OR EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.elder_id AND d.status IN('WAITING','ACCEPTED') AND c.account_id=a.id AND c.status='ACTIVE')
 );
END$$
CREATE PROCEDURE escalate_events()
BEGIN
 CALL escalate_community_events(NULL);
END$$
DELIMITER ;
