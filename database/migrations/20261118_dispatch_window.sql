DELIMITER $$
DROP PROCEDURE IF EXISTS assign_order$$
CREATE PROCEDURE assign_order(IN p_order BIGINT,IN p_shift BIGINT,IN p_reason VARCHAR(200))
BEGIN
 DECLARE v_elder,v_item,v_community,v_worker,v_guard,v_old_shift BIGINT;
 DECLARE v_start,v_end DATETIME;
 DECLARE v_state VARCHAR(30);
 SELECT elder_id,item_id,community_id,starts_at,ends_at,status INTO v_elder,v_item,v_community,v_start,v_end,v_state FROM service_order WHERE id=p_order FOR UPDATE;
 IF v_state IS NULL OR v_state NOT IN ('PENDING','ASSIGNED','ACCEPTED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_NOT_DISPATCHABLE'; END IF;
 IF v_end<=NOW() THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_WINDOW_ENDED'; END IF;
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
DELIMITER ;
