package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final Store db;

    public JdbcOrderRepository(Store db) {
        this.db = db;
    }

    @Override
    public Map<String, Object> findElder(Number elderId) {
        return db.one("SELECT * FROM elder WHERE id=?", elderId);
    }

    @Override
    public Map<String, Object> findServiceItem(Number itemId) {
        return db.one("SELECT * FROM service_item WHERE id=?", itemId);
    }

    @Override
    public long insertOrder(
            Number elderId,
            Number itemId,
            Number communityId,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String address,
            String channel,
            Number amount,
            Number createdBy,
            Number x,
            Number y) {
        return db.insert(
                "INSERT INTO"
                    + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                elderId,
                itemId,
                communityId,
                startsAt,
                endsAt,
                address,
                channel,
                amount,
                createdBy,
                x,
                y);
    }

    @Override
    public void assignOrder(Number orderId, Number shiftId, String reason) {
        db.call("assign_order", orderId, shiftId, reason);
    }

    @Override
    public Map<String, Object> findWaitingDispatch(Number orderId) {
        return db.one(
                "SELECT * FROM v_dispatch_context WHERE order_id=? AND status='WAITING'", orderId);
    }

    @Override
    public void assertWorkerEligible(Number orderId, Number shiftId) {
        db.call("assert_worker_order", orderId, shiftId);
    }

    @Override
    public int acceptDispatch(Number dispatchId) {
        return db.update(
                "UPDATE dispatch SET status='ACCEPTED',accepted_at=NOW() WHERE id=?", dispatchId);
    }

    @Override
    public int markAccepted(Number orderId) {
        return db.update("UPDATE service_order SET status='ACCEPTED' WHERE id=?", orderId);
    }

    @Override
    public int recordConfirmation(Number confirmedBy, String confirmation, Number orderId) {
        return db.update(
                "UPDATE service_record r JOIN v_dispatch_context d ON d.id=r.dispatch_id SET"
                    + " r.confirmed_by=?,r.confirmed_at=NOW(),r.confirmation=? WHERE d.order_id=?"
                    + " AND d.status='FINISHED'",
                confirmedBy,
                confirmation,
                orderId);
    }

    @Override
    public int markCompleted(Number orderId) {
        return db.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", orderId);
    }

    @Override
    public List<Map<String, Object>> findActiveDispatches(Number orderId) {
        return db.rows(
                "SELECT * FROM v_dispatch_context WHERE order_id=? AND active_order IS NOT NULL",
                orderId);
    }

    @Override
    public int rejectDispatch(String reason, Number dispatchId) {
        return db.update(
                "UPDATE dispatch SET status='REJECTED',reason=? WHERE id=?", reason, dispatchId);
    }

    @Override
    public int releaseShiftCapacity(Number shiftId) {
        return db.update("UPDATE shift SET used_count=used_count-1 WHERE id=?", shiftId);
    }

    @Override
    public int markCancelled(Number orderId) {
        return db.update("UPDATE service_order SET status='CANCELLED' WHERE id=?", orderId);
    }

    @Override
    public Map<String, Object> lockOrder(Number orderId) {
        return db.one("SELECT id FROM service_order WHERE id=? FOR UPDATE", orderId);
    }

    @Override
    public Map<String, Object> findActiveDispatch(Number orderId) {
        return db.one("SELECT * FROM v_dispatch_context WHERE active_order=?", orderId);
    }

    @Override
    public int markPending(Number orderId) {
        return db.update("UPDATE service_order SET status='PENDING' WHERE id=?", orderId);
    }

    @Override
    public long findFenceRadius(Number communityId) {
        return db.count("SELECT fence_m FROM community WHERE id=?", communityId);
    }

    @Override
    public Map<String, Object> findAcceptedDispatch(Number orderId) {
        return db.one(
                "SELECT * FROM v_dispatch_context WHERE order_id=? AND status='ACCEPTED'", orderId);
    }

    @Override
    public long insertCheckin(
            Number dispatchId, String location, Boolean fenceOk, Number x, Number y) {
        return db.insert(
                "INSERT INTO"
                    + " attendance(dispatch_id,check_in,location_note,fence_ok,checkin_x,checkin_y)"
                    + " VALUES(?,NOW(6),?,?,?,?)",
                dispatchId,
                location,
                fenceOk,
                x,
                y);
    }

    @Override
    public int markServing(Number orderId) {
        return db.update("UPDATE service_order SET status='SERVING' WHERE id=?", orderId);
    }

    @Override
    public int recordCheckout(Number x, Number y, Boolean fenceOk, Number dispatchId) {
        return db.update(
                "UPDATE attendance SET"
                        + " check_out=NOW(6),checkout_x=?,checkout_y=?,checkout_fence_ok=? WHERE"
                        + " dispatch_id=?",
                x,
                y,
                fenceOk,
                dispatchId);
    }

    @Override
    public void verifyHours(Number dispatchId) {
        db.call("verify_hours", dispatchId);
    }

    @Override
    public long insertServiceRecord(Number dispatchId, String notes) {
        return db.insert(
                "INSERT INTO service_record(dispatch_id,notes) VALUES(?,?)", dispatchId, notes);
    }

    @Override
    public int finishDispatch(Number dispatchId) {
        return db.update("UPDATE dispatch SET status='FINISHED' WHERE id=?", dispatchId);
    }

    @Override
    public int markAwaitingConfirmation(Number orderId) {
        return db.update("UPDATE service_order SET status='CONFIRM' WHERE id=?", orderId);
    }

    @Override
    public void assertOrderEligible(
            Number elderId,
            Number itemId,
            Number communityId,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        db.call("assert_order", elderId, itemId, communityId, startsAt, endsAt);
    }

    @Override
    public List<Map<String, Object>> findRecommendations(
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Number activeOrderId,
            Number communityId,
            Number itemId,
            LocalDateTime credentialStart,
            LocalDateTime credentialEnd,
            Number excludedOrderId,
            LocalDateTime overlapEnd,
            LocalDateTime overlapStart) {
        return db.rows(
                """
SELECT s.id shift_id,c.id caregiver_id,c.name,s.starts_at,s.ends_at,s.capacity-s.used_count available
FROM shift s JOIN caregiver c ON c.id=s.caregiver_id JOIN account ac ON ac.id=c.account_id
WHERE c.status='ACTIVE' AND ac.active=1 AND s.status='OPEN' AND s.starts_at<=? AND s.ends_at>=?
AND (s.used_count<s.capacity OR EXISTS(SELECT 1 FROM dispatch d WHERE d.shift_id=s.id AND d.active_order=?))
AND EXISTS(SELECT 1 FROM caregiver_area a WHERE a.caregiver_id=c.id AND a.community_id=?)
AND EXISTS(SELECT 1 FROM training_record tr WHERE tr.caregiver_id=c.id AND tr.result='PASS')
AND EXISTS(SELECT 1 FROM credential x WHERE x.caregiver_id=c.id AND x.item_id=? AND x.valid_from<=LEAST(NOW(),?) AND x.valid_until>=GREATEST(NOW(),?))
AND NOT EXISTS(SELECT 1 FROM inspection i WHERE i.status IN('OPEN','REVIEW') AND (i.caregiver_id=c.id OR i.order_id IN(SELECT order_id FROM v_dispatch_context WHERE caregiver_id=c.id AND status IN('ACCEPTED','FINISHED'))))
AND NOT EXISTS(SELECT 1 FROM v_dispatch_context d JOIN service_order z ON z.id=d.order_id WHERE d.caregiver_id=c.id AND d.status IN('WAITING','ACCEPTED') AND z.id<>? AND z.starts_at<? AND z.ends_at>?)
ORDER BY available DESC,c.id,s.id
""",
                startsAt,
                endsAt,
                activeOrderId,
                communityId,
                itemId,
                credentialStart,
                credentialEnd,
                excludedOrderId,
                overlapEnd,
                overlapStart);
    }
}
