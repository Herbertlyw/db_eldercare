package edu.bjfu.eldercare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for the 2026-11-13 audit. Runs using the limited application DB account. */
@SpringBootTest(properties={"app.jobs=false"})
class HistoryRulesIntegrationTest {
 @Autowired Store d; @Autowired CareService service; @Autowired ReadService reads;
 @Autowired PlatformTransactionManager tm;
 final Auth.User admin=new Auth.User(1,"admin1","ADMIN",1,1L);
 final Auth.User worker=new Auth.User(3,"caregiver1","CAREGIVER",1,1L);
 final Auth.User regulator=new Auth.User(6,"regulator1","REGULATOR",1,1L);
 Map<String,Object> m(Object... a){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<a.length;i+=2)out.put(a[i].toString(),a[i+1]);return out;}
 Object run(Auth.User u,String action,Object... a){return service.execute(u,action,m(a));}
 void rollback(Runnable r){new TransactionTemplate(tm).execute(tx->{tx.setRollbackOnly();r.run();return null;});}
 void rejected(String code,Runnable r){var e=assertThrows(DataAccessException.class,r::run);assertTrue(e.getMostSpecificCause().getMessage().contains(code),e.getMostSpecificCause().getMessage());}
 long reassess(){return ((Number)run(admin,"assessment.create","elderId",1,"scale","课程复评","score",95,"level","SELF","validUntil",LocalDateTime.now().plusDays(200).toString())).longValue();}
 long assign(){run(admin,"order.assign","orderId",2,"shiftId",2,"reason","回归测试派单");return d.count("SELECT id FROM dispatch WHERE active_order=2");}
 Object snapshot(String table,long id){return d.one("SELECT * FROM "+table+" WHERE id=?",id);}
 void transfer(){run(admin,"elder.transfer","elderId",1,"communityId",2,"address","课程迁移地址","x",20000,"y",0);}

 @Test void reassessmentPausesPreviousPlanAndBlocksNewOrder(){rollback(()->{
  reassess();assertEquals("PAUSED",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));
  rejected("ORDER_PLAN_INVALID",()->run(admin,"order.create","elderId",1,"itemId",1,"startsAt",LocalDateTime.now().plusDays(3).toString(),"channel","COMMUNITY"));
  rejected("ORDER_PLAN_INVALID",()->assign());
 });}
 @Test void latestAssessmentCanCreateRevisedPlanAfterPendingOrdersHandled(){rollback(()->{
  long assessment=reassess();run(admin,"order.cancel","orderId",2,"reason","回归测试取消");
  long bundle=d.insert("INSERT INTO care_bundle(name,ability_level) VALUES('复评后的课程服务包','SELF')");
  d.insert("INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,1,1)",bundle);
  long plan=((Number)run(admin,"plan.revise","planId",1,"elderId",1,"assessmentId",assessment,"bundleId",bundle,"caregiverId",1,"startsAt",LocalDateTime.now().toString(),"endsAt",LocalDateTime.now().plusDays(20).toString(),"items",List.of(m("itemId",1,"frequency",1)),"reason","根据最新复评调整")).longValue();
  assertEquals("ENDED",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));
  assertEquals(assessment,d.count("SELECT assessment_id FROM care_plan WHERE id=?",plan));
  assertNotNull(run(admin,"order.create","elderId",1,"itemId",1,"startsAt",LocalDateTime.now().plusDays(3).toString(),"channel","COMMUNITY"));
 });}
 @Test void obsoleteAssessmentCannotBeReusedForNewOrResumedPlan(){rollback(()->{
  reassess();
  rejected("PLAN_ASSESSMENT_SUPERSEDED",()->d.update("UPDATE care_plan SET status='ACTIVE' WHERE id=1"));
  rejected("PLAN_ASSESSMENT_SUPERSEDED",()->d.insert("INSERT INTO care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at) VALUES(1,1,1,NOW(),NOW()+INTERVAL 1 DAY)"));
 });}
 @Test void historicalAssessmentImportDoesNotPauseLatestPlan(){rollback(()->{
  d.insert("INSERT INTO assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until) SELECT elder_id,'历史补录',score,ability_level,assessor_id,assessed_at-INTERVAL 1 DAY,valid_until FROM assessment WHERE id=1");
  assertEquals("ACTIVE",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));
 });}
 @Test void futureAssessmentPausesOnlyWhenItBecomesEffective(){rollback(()->{
  long a=d.insert("INSERT INTO assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until) VALUES(1,'未来评估',90,'SELF',1,NOW()+INTERVAL 1 DAY,NOW()+INTERVAL 200 DAY)");
  d.call("expire_plans");assertEquals("ACTIVE",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));
  long future=d.count("SELECT UNIX_TIMESTAMP(assessed_at)+1 FROM assessment WHERE id=?",a);d.update("SET timestamp=?",future);
  try{d.call("expire_plans");assertEquals("PAUSED",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));}finally{d.update("SET timestamp=0");}
 });}
 @Test void reassessmentPreservesCompletedServicesAndPaidClaims(){rollback(()->{
  var before=List.of(snapshot("service_order",1),snapshot("attendance",1),snapshot("service_record",1),snapshot("claim",1),snapshot("claim_item",1),snapshot("payment",1));
  reassess();assertEquals(before,List.of(snapshot("service_order",1),snapshot("attendance",1),snapshot("service_record",1),snapshot("claim",1),snapshot("claim_item",1),snapshot("payment",1)));
 });}
 @Test void transferRequiresUnbindingBeforeAnyMutation(){rollback(()->{
  run(admin,"order.cancel","orderId",2,"reason","回归测试取消");var elder=snapshot("elder",1);var plan=snapshot("care_plan",1);
  var error=assertThrows(Rule.class,this::transfer);assertTrue(error.getMessage().contains("解绑"));
  assertEquals(elder,snapshot("elder",1));assertEquals(plan,snapshot("care_plan",1));
  rejected("TRANSFER_DEVICE_BOUND",()->d.update("UPDATE elder SET community_id=2,position_x=20000 WHERE id=1"));
 });}
 @Test void unboundTransferKeepsOldDeviceOwnershipAndSampleHistory(){rollback(()->{
  run(admin,"order.cancel","orderId",2,"reason","回归测试取消");var vital=snapshot("vital",1);var order=snapshot("service_order",1);
  run(admin,"device.unbind","deviceId",1);transfer();
  assertEquals(2,d.count("SELECT community_id FROM elder WHERE id=1"));assertEquals(1,d.count("SELECT community_id FROM device WHERE id=1"));
  assertEquals(vital,snapshot("vital",1));assertEquals(order,snapshot("service_order",1));
  assertEquals("PAUSED",d.one("SELECT status FROM care_plan WHERE id=1").get("status"));
  var newAdmin=new Auth.User(7,"admin2","ADMIN",2,2L);
  long device=((Number)run(newAdmin,"device.create","serial","REGRESSION-TRANSFER","type","课程设备","location","新住址")).longValue();
  run(newAdmin,"device.bind","deviceId",device,"elderId",1);assertEquals(1,d.count("SELECT COUNT(*) FROM device_binding WHERE device_id=? AND elder_id=1 AND unbound_at IS NULL",device));
 });}
 @Test void directBindingCannotReconnectOldCommunityDeviceAfterTransfer(){rollback(()->{
  run(admin,"order.cancel","orderId",2,"reason","回归测试取消");run(admin,"device.unbind","deviceId",1);transfer();
  rejected("BINDING_COMMUNITY_INVALID",()->d.insert("INSERT INTO device_binding(device_id,elder_id,bound_at) VALUES(1,1,NOW(6))"));
 });}
 @Test void localDutyStaffReceiveEscalationsWithoutBroadcastToAllJurisdictionReaders(){rollback(()->{
  long e=d.insert("INSERT INTO emergency(elder_id,source,deadline,status) VALUES(2,'MANUAL',NOW()+INTERVAL 1 HOUR,'OPEN')");
  assertEquals(1,d.count("SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=7",e));
  d.call("escalate_events");assertEquals(0,d.count("SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=12",e));
  d.update("UPDATE emergency SET deadline=NOW()-INTERVAL 1 SECOND WHERE id=?",e);d.call("escalate_events");
  assertEquals(1,d.count("SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=12",e));
  assertEquals(0,d.count("SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=6",e));
  assertNotNull(reads.details(regulator,"events",e));
  d.update("UPDATE emergency SET deadline=NOW()-INTERVAL 1 SECOND WHERE id=?",e);d.call("escalate_events");d.call("escalate_events");
  assertEquals(2,d.count("SELECT COUNT(*) FROM emergency_log WHERE event_id=?",e));assertEquals(3,d.count("SELECT level FROM emergency WHERE id=?",e));
 });}
 @Test void finishedDispatchCannotChangeStatusReasonOrTimes(){rollback(()->{
  long id=d.count("SELECT id FROM dispatch WHERE order_id=1 AND status='FINISHED'");var before=snapshot("dispatch",id);
  for(String change:List.of("status='REJECTED'","reason='篡改'","assigned_at=NOW()","accepted_at=NULL"))rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE dispatch SET "+change+" WHERE id=?",id));
  assertEquals(before,snapshot("dispatch",id));
 });}
 @Test void reassignedAndRejectedDispatchesAreAlsoImmutable(){rollback(()->{
  long first=assign();assign();rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE dispatch SET reason='改写历史' WHERE id=?",first));
  long active=d.count("SELECT id FROM dispatch WHERE active_order=2");run(admin,"order.cancel","orderId",2,"reason","回归测试取消");
  rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE dispatch SET status='WAITING' WHERE id=?",active));assertEquals(0,d.count("SELECT used_count FROM shift WHERE id=2"));
 });}
 @Test void dispatchCannotSkipAcceptanceOrRewindAcceptedState(){rollback(()->{
  long id=assign();rejected("DISPATCH_STATE_INVALID",()->d.update("UPDATE dispatch SET status='FINISHED' WHERE id=?",id));
  run(worker,"order.accept","orderId",2);rejected("DISPATCH_STATE_INVALID",()->d.update("UPDATE dispatch SET status='WAITING' WHERE id=?",id));
 });}
 @Test void activeDispatchCannotRewriteOriginalAssignmentOrAcceptanceTime(){rollback(()->{
  long id=assign();rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE dispatch SET assigned_at=assigned_at-INTERVAL 1 HOUR WHERE id=?",id));
  run(worker,"order.accept","orderId",2);rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE dispatch SET accepted_at=accepted_at-INTERVAL 1 HOUR WHERE id=?",id));
 });}
 @Test void emergencyLogCannotBeUpdated(){rollback(()->{
  var before=snapshot("emergency_log",1);rejected("HISTORICAL_FACT_IMMUTABLE",()->d.update("UPDATE emergency_log SET message='改写升级过程' WHERE id=1"));assertEquals(before,snapshot("emergency_log",1));
 });}
 @Test void emergencyLogCannotBeDeleted(){rollback(()->{
  var before=snapshot("emergency_log",1);rejected("HISTORY_DELETE_FORBIDDEN",()->d.update("DELETE FROM emergency_log WHERE id=1"));assertEquals(before,snapshot("emergency_log",1));
 });}
}
