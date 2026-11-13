package edu.bjfu.eldercare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.jobs=false"})
class BusinessRequirementAuditTest {
 @Autowired Store d; @Autowired CareService service; @Autowired Scope scope;
 @Autowired ReadService reads; @Autowired PlatformTransactionManager tm;
 final Auth.User admin1=new Auth.User(1,"admin1","ADMIN",1,1L);
 final Auth.User admin2=new Auth.User(7,"admin2","ADMIN",2,2L);
 final Auth.User regulator1=new Auth.User(6,"regulator1","REGULATOR",1,1L);
 void rollback(Runnable work){new TransactionTemplate(tm).execute(tx->{tx.setRollbackOnly();work.run();return null;});}

 @Test void reassessmentMustNotLeaveContradictoryServiceEligibility(){rollback(()->{
  service.execute(admin1,"assessment.create",Map.of("elderId",1,"scale","课程复评","score",95,"level","SELF","validUntil",LocalDateTime.now().plusDays(200).toString()));
  assertEquals("SELF",d.one("SELECT ability_level FROM assessment WHERE elder_id=1 ORDER BY assessed_at DESC,id DESC LIMIT 1").get("ability_level"));
  assertThrows(RuntimeException.class,()->service.execute(admin1,"order.create",Map.of("elderId",1,"itemId",1,"startsAt",LocalDateTime.now().plusDays(2).toString())),"新评估已变为 SELF，旧 SEVERE 服务包仍可下单");
 });}

 @Test void transferredElderMustNotRetainUnmanageableDeviceBinding(){rollback(()->{
  for(var o:d.rows("SELECT id FROM service_order WHERE elder_id=1 AND status IN('PENDING','ASSIGNED','ACCEPTED')"))service.execute(admin1,"order.cancel",Map.of("orderId",o.get("id"),"reason","迁移前处理未完订单"));
  service.execute(admin1,"elder.transfer",Map.of("elderId",1,"communityId",2,"address","演示社区2新住址","x",20000,"y",0));
  assertEquals(2,d.count("SELECT community_id FROM elder WHERE id=1"));
  if(d.count("SELECT COUNT(*) FROM device_binding WHERE device_id=1 AND unbound_at IS NULL")>0)
   assertDoesNotThrow(()->service.execute(admin2,"device.unbind",Map.of("deviceId",1)),"迁入管理员无法处理仍绑定在迁入老人身上的设备");
 });}

 @Test void scopedRegulatorMustReceiveEscalationFromOtherCoveredCommunity(){rollback(()->{
  scope.community(regulator1,2);
  long event=((Number)service.execute(admin2,"event.create",Map.of("elderId",2,"level",1,"description","跨社区应急升级核验"))).longValue();
  assertNotNull(reads.details(regulator1,"events",event));
  d.update("UPDATE emergency SET deadline=NOW()-INTERVAL 1 SECOND WHERE id=?",event);
  d.call("escalate_events");
  assertEquals(2,d.count("SELECT level FROM emergency WHERE id=?",event));
  assertTrue(d.count("SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=6",event)>0,"监管员可查询辖区事件，却收不到升级通知");
 });}

 @Test void finishedDispatchMustNotLoseHistoricalCompletion(){rollback(()->{
  long dispatch=d.count("SELECT id FROM dispatch WHERE order_id=1 AND status='FINISHED'");
  assertThrows(RuntimeException.class,()->d.update("UPDATE dispatch SET status='REJECTED',reason='改写历史服务状态' WHERE id=?",dispatch),"已完成订单的派单可被改回拒接，服务证据链被破坏");
 });}

 @Test void escalationHistoryMustNotBeDeletable(){rollback(()->{
  assertTrue(d.count("SELECT COUNT(*) FROM emergency_log WHERE event_id=1")>0);
  assertThrows(RuntimeException.class,()->d.update("DELETE FROM emergency_log WHERE event_id=1"),"应急上报和升级日志可被应用数据库账户直接删除");
 });}
}
