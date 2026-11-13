package edu.bjfu.eldercare;

import org.springframework.stereotype.Service;
import java.util.*;
import java.math.*;
import java.time.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static edu.bjfu.eldercare.Inputs.*;
import static edu.bjfu.eldercare.Store.id;

@Service
public class WorkflowService {
    private final Store db; private final Crypto crypto; private final Auth auth; private final Scope scope;
    public WorkflowService(Store db,Crypto crypto,Auth auth,Scope scope){this.db=db;this.crypto=crypto;this.auth=auth;this.scope=scope;}
    private static final Set<String> ACTIONS=Set.of(
        "elder.create","elder.update","elder.transfer","elder.tags","guardian.update","account.create",
        "plan.create","plan.revise","plan.state","catalog.create","bundle.create","metric.create","policy.create",
        "order.checkin","order.finish","order.release","order.reject","caregiver.area",
        "recruitment.create","recruitment.review","training.create","training.enroll","training.result",
        "review.create","complaint.create","complaint.assign","complaint.reply","complaint.close",
        "device.create","device.unbind","device.status","device.simulation","simulator.run",
        "event.respond","event.arrive","event.close","event.confirm","event.dismiss",
        "claim.evidence","claim.resubmit","inspection.create","inspection.order","inspection.batch","inspection.record","rectification.submit","inspection.close",
        "organization.submit","organization.review","notice.publish","notice.withdraw");
    public boolean handles(String a){return ACTIONS.contains(a);}
    private void noPendingOrders(long elder){if(db.count("SELECT COUNT(*) FROM service_order WHERE elder_id=? AND status NOT IN('COMPLETED','CANCELLED')",elder)>0)throw new Rule("请先处理未完成订单");}
    private void coordinates(long community,BigDecimal x,BigDecimal y){var c=db.one("SELECT * FROM community WHERE id=?",community);if(Math.hypot(x.doubleValue()-((Number)c.get("center_x")).doubleValue(),y.doubleValue()-((Number)c.get("center_y")).doubleValue())>((Number)c.get("radius_m")).doubleValue())throw new Rule("地址坐标超出社区服务范围");}
    private boolean fence(Map<String,Object> order,BigDecimal x,BigDecimal y){long radius=db.count("SELECT fence_m FROM community WHERE id=?",order.get("community_id"));return Math.hypot(x.doubleValue()-((Number)order.get("position_x")).doubleValue(),y.doubleValue()-((Number)order.get("position_y")).doubleValue())<=radius;}
    private String password(Map<String,Object> m){String p=text(m,"password");if(p.length()<10||p.getBytes(StandardCharsets.UTF_8).length>72)throw new Rule("初始密码至少 10 个字符，且不超过 72 字节");return auth.password(p);}
    private void step(Auth.User u,long event,String action,String message){db.insert("INSERT INTO event_step(event_id,actor_id,action,message) VALUES(?,?,?,?)",event,u.id(),action,message);}
    private Map<String,Object> event(Auth.User u,long event){var e=db.one("SELECT * FROM emergency WHERE id=? FOR UPDATE",event);scope.elder(u,id(e,"elder_id"));return e;}
    private void responder(Auth.User u,Map<String,Object> e){if(u.role().equals("CAREGIVER")&&!Objects.equals(e.get("responded_by"),u.id()))throw new Forbidden();}
    private void logComplaint(Auth.User u,long complaint,String status,String message){db.insert("INSERT INTO complaint_log(complaint_id,actor_id,status,message) VALUES(?,?,?,?)",complaint,u.id(),status,message);}
    private Map<String,Object> complaint(Auth.User u,long x){var c=db.one("SELECT * FROM complaint WHERE id=? FOR UPDATE",x);scope.order(u,id(c,"order_id"));if(u.role().equals("MANAGER")&&!Objects.equals(c.get("organization_id"),u.organization()))throw new Forbidden();return c;}

    public Object execute(Auth.User u,String action,Map<String,Object> m){
        long x; Map<String,Object> row;
        switch(action){
            case "account.create": {
                Auth.role(u,"ADMIN");return db.insert("INSERT INTO account(username,password_hash,role,community_id) VALUES(?,?,'FAMILY',?)",text(m,"username"),password(m),u.community());
            }
            case "elder.create": {
                Auth.role(u,"ADMIN");BigDecimal px=decimal(m,"x"),py=decimal(m,"y");coordinates(u.community(),px,py);String identity=text(m,"identity").toUpperCase(Locale.ROOT);
                return db.insert("INSERT INTO elder(name,identity_hash,identity_cipher,birth_date,address,community_id,position_x,position_y) VALUES(?,?,?,?,?,?,?,?)",text(m,"name"),crypto.hash(identity),crypto.encrypt(identity),text(m,"birthDate"),text(m,"address"),u.community(),px,py);
            }
            case "elder.update": case "elder.transfer": {
                Auth.role(u,"ADMIN");x=number(m,"elderId");scope.elder(u,x);db.one("SELECT id FROM elder WHERE id=? FOR UPDATE",x);noPendingOrders(x);
                long community=action.endsWith("transfer")?number(m,"communityId"):u.community();BigDecimal px=decimal(m,"x"),py=decimal(m,"y");coordinates(community,px,py);
                if(community!=u.community() && db.count("SELECT COUNT(*) FROM device_binding WHERE elder_id=? AND unbound_at IS NULL",x)>0)throw new Rule("请先在设备管理中解绑原社区设备，再迁移老人档案");
                if(action.endsWith("transfer"))db.update("UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id SET p.status='PAUSED' WHERE a.elder_id=? AND p.status='ACTIVE'",x);
                return db.update("UPDATE elder SET address=?,community_id=?,position_x=?,position_y=? WHERE id=?",text(m,"address"),community,px,py,x);
            }
            case "elder.tags": {
                Auth.role(u,"ADMIN");x=number(m,"elderId");scope.elder(u,x);String tag=choice(m,"tag","ALONE","DISABLED","LOW_INCOME");return flag(m,"enabled")?db.update("INSERT IGNORE INTO elder_tag(elder_id,tag) VALUES(?,?)",x,tag):db.update("DELETE FROM elder_tag WHERE elder_id=? AND tag=?",x,tag);
            }
            case "guardian.update": {
                Auth.role(u,"ADMIN");x=number(m,"guardianId");row=db.one("SELECT * FROM guardian WHERE id=? FOR UPDATE",x);scope.elder(u,id(row,"elder_id"));return db.update("UPDATE guardian SET relation_name=?,phone_cipher=?,active=? WHERE id=?",text(m,"relation"),crypto.encrypt(text(m,"phone")),flag(m,"active"),x);
            }
            case "plan.create": case "plan.revise": return plan(u,m,action.endsWith("revise"));
            case "plan.state": {
                Auth.role(u,"ADMIN");x=number(m,"planId");row=db.one("SELECT * FROM v_plan_context WHERE id=? FOR UPDATE",x);scope.elder(u,id(row,"elder_id"));noPendingOrders(id(row,"elder_id"));String state=choice(m,"status","ACTIVE","PAUSED","ENDED");String reason=text(m,"reason");if(Set.of("ENDED","EXPIRED").contains(row.get("status")))throw new Rule("已终止或到期计划应重新制定");int changed=db.update("UPDATE care_plan SET status=? WHERE id=?",state,x);db.insert("INSERT INTO audit_log(table_name,row_id,operation,actor,after_data) VALUES('care_plan',?,'STATE_REASON',?,JSON_OBJECT('reason',?))",x,u.name(),reason);return changed;
            }
            case "catalog.create": {
                Auth.role(u,"ADMIN");return db.insert("INSERT INTO service_item(name,category,unit,price,duration_minutes,subsidizable) VALUES(?,?,?,?,?,?)",text(m,"name"),text(m,"category"),choice(m,"unit","VISIT","HOUR"),decimal(m,"price"),number(m,"duration"),flag(m,"subsidizable"));
            }
            case "bundle.create": {
                Auth.role(u,"ADMIN");long bundle=db.insert("INSERT INTO care_bundle(name,ability_level) VALUES(?,?)",text(m,"name"),choice(m,"level","SELF","PARTIAL","SEVERE"));for(var item:objects(m,"items"))db.insert("INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,?,?)",bundle,number(item,"itemId"),number(item,"frequency"));return bundle;
            }
            case "metric.create": {
                Auth.role(u,"ADMIN");return db.insert("INSERT INTO metric_rule(metric,low_value,high_value,response_seconds) VALUES(?,?,?,?)",text(m,"name"),decimal(m,"low"),decimal(m,"high"),number(m,"seconds"));
            }
            case "policy.create": return policy(u,m);
            case "order.checkin": case "order.finish": return attendance(u,m,action.endsWith("finish"));
            case "order.release": case "order.reject": {
                Auth.role(u,"MANAGER","CAREGIVER");x=number(m,"orderId");row=scope.order(u,x);db.one("SELECT id FROM service_order WHERE id=? FOR UPDATE",x);if(!Set.of("ASSIGNED","ACCEPTED").contains(row.get("status")))throw new Rule("当前订单不能退回调度");var d=db.one("SELECT * FROM v_dispatch_context WHERE active_order=?",x);scope.worker(u,id(d,"caregiver_id"));db.update("UPDATE dispatch SET status='REJECTED',reason=? WHERE id=?",text(m,"reason"),d.get("id"));db.update("UPDATE shift SET used_count=used_count-1 WHERE id=?",d.get("shift_id"));return db.update("UPDATE service_order SET status='PENDING' WHERE id=?",x);
            }
            case "caregiver.area": {
                Auth.role(u,"MANAGER");x=number(m,"caregiverId");scope.worker(u,x);long community=number(m,"communityId");db.one("SELECT id FROM community WHERE id=?",community);if(flag(m,"enabled"))return db.update("INSERT IGNORE INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)",x,community);if(db.count("SELECT COUNT(*) FROM v_dispatch_context d JOIN service_order o ON o.id=d.order_id WHERE d.caregiver_id=? AND o.community_id=? AND d.status IN('WAITING','ACCEPTED')",x,community)>0)throw new Rule("该区域还有未完成派单");return db.update("DELETE FROM caregiver_area WHERE caregiver_id=? AND community_id=?",x,community);
            }
            case "recruitment.create": case "recruitment.review": case "training.create": case "training.enroll": case "training.result": return workforce(u,action,m);
            case "review.create": case "complaint.create": {
                Auth.role(u,"FAMILY");x=number(m,"orderId");var order=scope.order(u,x);boolean review=action.equals("review.create");if(review&&!order.get("status").equals("COMPLETED"))throw new Rule("服务完成后才能评价");String status=review?"CLOSED":"SUBMITTED";long complaint=db.insert("INSERT INTO complaint(order_id,account_id,rating,content,kind,status) VALUES(?,?,?,?,?,?)",x,u.id(),number(m,"rating"),text(m,"content"),review?"REVIEW":"COMPLAINT",status);logComplaint(u,complaint,status,text(m,"content"));return complaint;
            }
            case "complaint.assign": {
                Auth.role(u,"ADMIN");x=number(m,"complaintId");row=complaint(u,x);if(!row.get("status").equals("SUBMITTED"))throw new Rule("投诉已受理");Long org=m.get("organizationId")==null?null:number(m,"organizationId");if(org!=null&&db.count("SELECT COUNT(*) FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id WHERE d.order_id=? AND c.organization_id=?",row.get("order_id"),org)==0)throw new Rule("该机构未承接这笔订单");db.update("UPDATE complaint SET status='ASSIGNED',organization_id=?,handler_id=?,updated_at=NOW() WHERE id=?",org,u.id(),x);logComplaint(u,x,"ASSIGNED",text(m,"reason"));return org==null?"已由社区受理":"已交机构处理";
            }
            case "complaint.reply": {
                Auth.role(u,"MANAGER","ADMIN");x=number(m,"complaintId");row=complaint(u,x);if(u.role().equals("ADMIN")&&row.get("organization_id")!=null)throw new Rule("已转交机构的投诉请由机构回复");if(!row.get("status").equals("ASSIGNED"))throw new Rule("投诉不在机构处理中");db.update("UPDATE complaint SET status='REPLIED',result=?,updated_at=NOW() WHERE id=?",text(m,"result"),x);logComplaint(u,x,"REPLIED",text(m,"result"));return "已提交处理结果";
            }
            case "complaint.close": {
                Auth.role(u,"ADMIN","FAMILY");x=number(m,"complaintId");row=complaint(u,x);if(!row.get("status").equals("REPLIED"))throw new Rule("请先由机构回复");db.update("UPDATE complaint SET status='CLOSED',updated_at=NOW() WHERE id=?",x);logComplaint(u,x,"CLOSED",text(m,"reason"));return "投诉已结案";
            }
            case "device.create": {
                Auth.role(u,"ADMIN");return db.insert("INSERT INTO device(serial_no,type_name,location_note,community_id) VALUES(?,?,?,?)",text(m,"serial"),text(m,"type"),text(m,"location"),u.community());
            }
            case "device.unbind": case "device.status": {
                Auth.role(u,"ADMIN");x=number(m,"deviceId");row=db.one("SELECT * FROM device WHERE id=? FOR UPDATE",x);scope.community(u,id(row,"community_id"));if(action.endsWith("unbind"))return db.update("UPDATE device_binding SET unbound_at=NOW(6) WHERE device_id=? AND unbound_at IS NULL",x);String state=choice(m,"status","ACTIVE","OFFLINE","RETIRED");if(state.equals("RETIRED")&&db.count("SELECT COUNT(*) FROM device_binding WHERE device_id=? AND unbound_at IS NULL",x)>0)throw new Rule("请先解绑设备");return db.update("UPDATE device SET status=? WHERE id=?",state,x);
            }
            case "device.simulation": {
                Auth.role(u,"ADMIN");x=number(m,"deviceId");row=db.one("SELECT * FROM device WHERE id=? FOR UPDATE",x);scope.community(u,id(row,"community_id"));boolean enabled=flag(m,"enabled");long metric=number(m,"metricId"),seconds=number(m,"seconds");db.one("SELECT id FROM metric_rule WHERE id=?",metric);if(seconds<30||seconds>3600)throw new Rule("采样间隔应为 30 至 3600 秒");if(enabled&&(!row.get("status").equals("ACTIVE")||db.count("SELECT COUNT(*) FROM device_binding WHERE device_id=? AND unbound_at IS NULL",x)==0))throw new Rule("先启用并绑定设备");return db.update("UPDATE device SET simulation_enabled=?,simulation_metric_id=?,simulation_seconds=?,last_simulated_at=NULL WHERE id=?",enabled,metric,seconds,x);
            }
            case "simulator.run": {
                Auth.role(u,"ADMIN");db.one("SELECT id FROM metric_rule WHERE id=?",number(m,"metricId"));int written=0;for(var sample:db.rows("SELECT b.id FROM device_binding b JOIN device d ON d.id=b.device_id JOIN elder e ON e.id=b.elder_id WHERE d.status='ACTIVE' AND e.community_id=? AND b.unbound_at IS NULL",u.community())){db.insert("INSERT INTO vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source) SELECT ?,id,?,NOW(6),(low_value+high_value)/2,'SIMULATOR' FROM metric_rule WHERE id=?",sample.get("id"),UUID.randomUUID().toString(),number(m,"metricId"));written++;}return written;
            }
            case "event.respond": case "event.arrive": case "event.close": case "event.confirm": case "event.dismiss": return emergency(u,action,m);
            case "claim.evidence": {
                Auth.role(u,"ADMIN");x=number(m,"claimId");scope.claim(u,x);String content=text(m,"content");return db.insert("INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256) VALUES(?,?,?,?,SHA2(?,256))",x,u.id(),text(m,"name"),content,content);
            }
            case "claim.resubmit": {
                Auth.role(u,"ADMIN");x=number(m,"claimId");row=scope.claim(u,x);db.one("SELECT id FROM claim WHERE id=? FOR UPDATE",x);if(!row.get("status").equals("REJECTED"))throw new Rule("只有驳回申报可补资料重报");execute(u,"claim.evidence",Map.of("claimId",x,"name","重新提交说明","content",text(m,"reason")));return db.update("UPDATE claim SET status='SUBMITTED' WHERE id=?",x);
            }
            case "inspection.create": case "inspection.order": case "inspection.batch": case "inspection.record": case "rectification.submit": case "inspection.close": case "organization.submit": case "organization.review": case "notice.publish": case "notice.withdraw":return governance(u,action,m);
            default:throw new Rule("未知业务操作");
        }
    }
    private Object plan(Auth.User u,Map<String,Object> m,boolean revise){
        Auth.role(u,"ADMIN");long elder=number(m,"elderId");scope.elder(u,elder);db.one("SELECT id FROM elder WHERE id=? FOR UPDATE",elder);scope.worker(u,number(m,"caregiverId"));
        if(db.count("SELECT COUNT(*) FROM assessment WHERE id=? AND elder_id=?",number(m,"assessmentId"),elder)!=1)throw new Rule("评估不属于此老人");
        Long old=null;if(revise){old=number(m,"planId");var p=db.one("SELECT * FROM v_plan_context WHERE id=? FOR UPDATE",old);if(id(p,"elder_id")!=elder)throw new Rule("原计划不属于此老人");noPendingOrders(elder);text(m,"reason");db.update("UPDATE care_plan SET status='ENDED' WHERE id=?",old);}
        long plan=db.insert("INSERT INTO care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at) VALUES(?,?,?,?,?)",number(m,"assessmentId"),number(m,"bundleId"),number(m,"caregiverId"),time(m,"startsAt"),time(m,"endsAt"));
        if(m.get("items") instanceof List<?> items&&!items.isEmpty())for(var item:objects(m,"items")){long itemId=number(item,"itemId");if(db.count("SELECT COUNT(*) FROM bundle_item WHERE bundle_id=? AND item_id=?",number(m,"bundleId"),itemId)==0)throw new Rule("项目不属于所选服务包");db.insert("INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,?,?)",plan,itemId,number(item,"frequency"));}
        else db.update("INSERT INTO plan_item(plan_id,item_id,weekly_frequency) SELECT ?,item_id,weekly_frequency FROM bundle_item WHERE bundle_id=?",plan,number(m,"bundleId"));
        if(db.count("SELECT COUNT(*) FROM plan_item WHERE plan_id=?",plan)==0)throw new Rule("照护计划必须包含服务项目");
        if(revise)db.insert("INSERT INTO audit_log(table_name,row_id,operation,actor,after_data) VALUES('care_plan',?,'REVISE',?,JSON_OBJECT('previous_plan',?,'reason',?))",plan,u.name(),old,text(m,"reason"));return plan;
    }
    private Object attendance(Auth.User u,Map<String,Object> m,boolean finish){
        Auth.role(u,"CAREGIVER");long order=number(m,"orderId");var o=scope.order(u,order);db.one("SELECT id FROM service_order WHERE id=? FOR UPDATE",order);if(!o.get("status").equals(finish?"SERVING":"ACCEPTED"))throw new Rule("订单不在当前服务阶段");
        var d=db.one("SELECT * FROM v_dispatch_context WHERE order_id=? AND status='ACCEPTED'",order);scope.worker(u,id(d,"caregiver_id"));BigDecimal x=decimal(m,"x"),y=decimal(m,"y");boolean inside=fence(o,x,y);
        if(!finish){db.call("assert_worker_order",order,d.get("shift_id"));db.insert("INSERT INTO attendance(dispatch_id,check_in,location_note,fence_ok,checkin_x,checkin_y) VALUES(?,NOW(6),?,?,?,?)",d.get("id"),text(m,"location"),inside,x,y);return db.update("UPDATE service_order SET status='SERVING' WHERE id=?",order);}
        db.update("UPDATE attendance SET check_out=NOW(6),checkout_x=?,checkout_y=?,checkout_fence_ok=? WHERE dispatch_id=?",x,y,inside,d.get("id"));db.call("verify_hours",d.get("id"));
        db.insert("INSERT INTO service_record(dispatch_id,notes) VALUES(?,?)",d.get("id"),text(m,"notes"));db.update("UPDATE dispatch SET status='FINISHED' WHERE id=?",d.get("id"));return db.update("UPDATE service_order SET status='CONFIRM' WHERE id=?",order);
    }
    private Object workforce(Auth.User u,String action,Map<String,Object> m){
        Auth.role(u,"MANAGER");long org=u.organization();
        switch(action){
            case "recruitment.create":return db.insert("INSERT INTO recruitment(organization_id,candidate_name,phone_cipher) VALUES(?,?,?)",org,text(m,"name"),crypto.encrypt(text(m,"phone")));
            case "recruitment.review":{
                long id=number(m,"recruitmentId");var r=db.one("SELECT * FROM recruitment WHERE id=? FOR UPDATE",id);scope.organization(u,id(r,"organization_id"));if(!r.get("status").equals("APPLIED"))throw new Rule("招聘申请已经处理");
                if(!flag(m,"approve")){db.update("UPDATE recruitment SET status='REJECTED',reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE id=?",u.id(),text(m,"reason"),id);return "未录用";}
                long community=db.count("SELECT community_id FROM organization WHERE id=?",org);long account=db.insert("INSERT INTO account(username,password_hash,role,community_id,organization_id) VALUES(?,?,'CAREGIVER',?,?)",text(m,"username"),password(m),community,org);
                long worker=db.insert("INSERT INTO caregiver(account_id,organization_id,name) VALUES(?,?,?)",account,org,r.get("candidate_name"));db.insert("INSERT INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)",worker,community);
                db.update("UPDATE recruitment SET status='HIRED',caregiver_id=?,reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE id=?",worker,u.id(),text(m,"reason"),id);return worker;
            }
            case "training.create":return db.insert("INSERT INTO training(organization_id,title,starts_at,ends_at) VALUES(?,?,?,?)",org,text(m,"title"),time(m,"startsAt"),time(m,"endsAt"));
            case "training.enroll":{
                long training=number(m,"trainingId"),worker=number(m,"caregiverId");scope.organization(u,db.count("SELECT organization_id FROM training WHERE id=?",training));scope.worker(u,worker);return db.insert("INSERT INTO training_record(training_id,caregiver_id) VALUES(?,?)",training,worker);
            }
            case "training.result":{
                long record=number(m,"recordId");var tr=db.one("SELECT r.*,t.organization_id,t.ends_at FROM training_record r JOIN training t ON t.id=r.training_id WHERE r.id=? FOR UPDATE",record);scope.organization(u,id(tr,"organization_id"));if(!tr.get("result").equals("PENDING"))throw new Rule("已登记成绩不能覆盖");if(db.count("SELECT COUNT(*) FROM training WHERE id=? AND ends_at<=NOW()",tr.get("training_id"))==0)throw new Rule("培训尚未结束");long score=number(m,"score");return db.update("UPDATE training_record SET score=?,result=?,recorded_by=?,recorded_at=NOW() WHERE id=?",score,score>=60?"PASS":"FAIL",u.id(),record);
            }
            default:throw new Rule("未知培训操作");
        }
    }
    private Object emergency(Auth.User u,String action,Map<String,Object> m){
        Auth.role(u,"ADMIN","CAREGIVER");long id=number(m,"eventId");var e=event(u,id);String state=e.get("status").toString();
        switch(action){
            case "event.confirm":if(!state.equals("UNCONFIRMED"))throw new Rule("事件非待确认");db.update("UPDATE emergency SET status='OPEN' WHERE id=?",id);step(u,id,"CONFIRM",text(m,"reason"));break;
            case "event.dismiss":Auth.role(u,"ADMIN");if(!Set.of("UNCONFIRMED","OPEN").contains(state))throw new Rule("已响应事件应记录处置结果");db.update("UPDATE emergency SET status='FALSE_ALARM',closed_at=NOW(6),close_reason=? WHERE id=?",text(m,"reason"),id);step(u,id,"FALSE_ALARM",text(m,"reason"));break;
            case "event.respond":if(!Set.of("UNCONFIRMED","OPEN").contains(state))throw new Rule("事件已响应或关闭");db.update("UPDATE emergency SET status='RESPONDED',responded_by=?,responded_at=NOW() WHERE id=?",u.id(),id);step(u,id,"RESPOND","已接受处置任务");break;
            case "event.arrive":if(!state.equals("RESPONDED")||e.get("arrived_at")!=null)throw new Rule("事件不能重复登记到场");responder(u,e);db.update("UPDATE emergency SET arrived_at=NOW() WHERE id=?",id);step(u,id,"ARRIVE",text(m,"note"));break;
            case "event.close":if(!state.equals("RESPONDED")||e.get("arrived_at")==null)throw new Rule("请先响应并登记到场");responder(u,e);db.update("UPDATE emergency SET status='CLOSED',closed_at=NOW(6),result=? WHERE id=?",text(m,"result"),id);step(u,id,"CLOSE",text(m,"result"));break;
        }return "事件记录已更新";
    }
    private Object policy(Auth.User u,Map<String,Object> m){
        Auth.role(u,"REGULATOR");List<Long> areas=ids(m,"communityIds");for(long c:areas)scope.community(u,c);
        long p=db.insert("INSERT INTO policy(name,community_id,item_id,ability_level,rate,starts_at,ends_at,settlement_cycle,minimum_age,required_tag) VALUES(?,?,?,?,?,?,?,?,?,?)",text(m,"name"),u.community(),number(m,"itemId"),choice(m,"level","SELF","PARTIAL","SEVERE"),decimal(m,"rate"),time(m,"startsAt"),time(m,"endsAt"),choice(m,"cycle","MONTH","QUARTER","YEAR"),number(m,"minimumAge"),optional(m,"tag","NONE").equals("NONE")?null:choice(m,"tag","ALONE","DISABLED","LOW_INCOME"));
        for(long c:areas)db.insert("INSERT INTO policy_area(policy_id,community_id) VALUES(?,?)",p,c);return p;
    }
    private Object governance(Auth.User u,String action,Map<String,Object> m){
        long x;Map<String,Object> r;
        switch(action){
            case "inspection.create": case "inspection.order":{
                Auth.role(u,"REGULATOR");boolean order=action.equals("inspection.order");long target=number(m,order?"orderId":"caregiverId");if(order){if(!scope.order(u,target).get("status").equals("COMPLETED"))throw new Rule("抽查订单应已完成");}else scope.worker(u,target);long score=number(m,"score");boolean rectify=flag(m,"requiresRectification")||score<60;return db.insert("INSERT INTO inspection("+(order?"order_id":"caregiver_id")+",inspector_id,method,score,issue,rectification,status,result) VALUES(?,?,?,?,?,?,?,?)",target,u.id(),choice(m,"method","PHONE","VISIT"),score,text(m,"issue"),rectify?text(m,"rectification"):"无需整改",rectify?"OPEN":"CLOSED",rectify?null:"抽查通过");
            }
            case "inspection.batch":{
                Auth.role(u,"REGULATOR");long community=number(m,"communityId");scope.community(u,community);String type=choice(m,"target","ORDER","CAREGIVER");var start=time(m,"startsAt");var end=time(m,"endsAt");BigDecimal ratio=decimal(m,"ratio");if(!end.isAfter(start)||ratio.signum()<=0||ratio.compareTo(BigDecimal.ONE)>0)throw new Rule("抽查时间或比例无效");String seed=UUID.randomUUID().toString();
                var population=type.equals("ORDER")?db.rows("SELECT id FROM service_order WHERE community_id=? AND status='COMPLETED' AND starts_at>=? AND starts_at<? ORDER BY SHA2(CONCAT(id,?),256)",community,start,end,seed):db.rows("SELECT DISTINCT c.id FROM caregiver c JOIN v_dispatch_context d ON d.caregiver_id=c.id JOIN service_order o ON o.id=d.order_id WHERE o.community_id=? AND o.status='COMPLETED' AND d.status='FINISHED' AND o.starts_at>=? AND o.starts_at<? ORDER BY SHA2(CONCAT(c.id,?),256)",community,start,end,seed);
                if(population.isEmpty())throw new Rule("抽查范围没有符合条件的对象");int count=ratio.multiply(BigDecimal.valueOf(population.size())).setScale(0,RoundingMode.CEILING).intValue();long batch=db.insert("INSERT INTO inspection_batch(community_id,inspector_id,target_type,starts_at,ends_at,ratio,population,sample_count,sample_seed) VALUES(?,?,?,?,?,?,?,?,?)",community,u.id(),type,start,end,ratio,population.size(),count,seed);
                for(var target:population.subList(0,count))db.insert("INSERT INTO inspection("+(type.equals("ORDER")?"order_id":"caregiver_id")+",inspector_id,method,score,issue,rectification,status,batch_id) VALUES(?,?,'PHONE',NULL,'待抽查','待确定','PENDING',?)",target.get("id"),u.id(),batch);return batch;
            }
            case "inspection.record":{
                Auth.role(u,"REGULATOR");x=number(m,"inspectionId");r=scope.inspection(u,x);db.one("SELECT id FROM inspection WHERE id=? FOR UPDATE",x);if(!r.get("status").equals("PENDING"))throw new Rule("抽查结果已经登记");long score=number(m,"score");boolean rectify=flag(m,"requiresRectification")||score<60;return db.update("UPDATE inspection SET method=?,score=?,issue=?,rectification=?,status=?,result=? WHERE id=?",choice(m,"method","PHONE","VISIT"),score,text(m,"issue"),rectify?text(m,"rectification"):"无需整改",rectify?"OPEN":"CLOSED",rectify?null:"抽查通过",x);
            }
            case "rectification.submit":{
                Auth.role(u,"MANAGER");x=number(m,"inspectionId");r=scope.inspection(u,x);db.one("SELECT id FROM inspection WHERE id=? FOR UPDATE",x);if(!r.get("status").equals("OPEN"))throw new Rule("当前没有待提交整改");long submission=db.insert("INSERT INTO rectification_submission(inspection_id,submitted_by,measures,evidence) VALUES(?,?,?,?)",x,u.id(),text(m,"measures"),text(m,"evidence"));db.update("UPDATE inspection SET status='REVIEW' WHERE id=?",x);return submission;
            }
            case "inspection.close":{
                Auth.role(u,"REGULATOR");x=number(m,"inspectionId");r=scope.inspection(u,x);db.one("SELECT id FROM inspection WHERE id=? FOR UPDATE",x);if(!r.get("status").equals("REVIEW"))throw new Rule("请先由机构提交整改材料");boolean approve=flag(m,"approve");String result=text(m,"result");db.update("UPDATE rectification_submission SET decision=?,reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE inspection_id=? AND decision='PENDING'",approve?"ACCEPT":"RETURN",u.id(),result,x);return db.update("UPDATE inspection SET status=?,result=? WHERE id=?",approve?"CLOSED":"OPEN",result,x);
            }
            case "organization.submit":{
                Auth.role(u,"MANAGER");String period=text(m,"period");if(!period.matches("\\d{4}-(0[1-9]|1[0-2])"))throw new Rule("月份格式应为 YYYY-MM");return db.insert("INSERT INTO organization_report(organization_id,period,summary,evidence,submitted_by) VALUES(?,?,?,?,?)",u.organization(),period,text(m,"summary"),text(m,"evidence"),u.id());
            }
            case "organization.review":{
                Auth.role(u,"REGULATOR");x=number(m,"reportId");r=db.one("SELECT * FROM organization_report WHERE id=? FOR UPDATE",x);scope.organization(u,id(r,"organization_id"));if(!r.get("status").equals("SUBMITTED"))throw new Rule("考核材料已审核，请追加新提交");return db.update("UPDATE organization_report SET status=?,reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE id=?",flag(m,"approve")?"ACCEPTED":"RETURNED",u.id(),text(m,"reason"),x);
            }
            case "notice.publish":{
                Auth.role(u,"REGULATOR");x=number(m,"inspectionId");r=scope.inspection(u,x);if(!r.get("status").equals("CLOSED")||r.get("score")==null)throw new Rule("只公示已完成抽查和整改的结果");if(db.count("SELECT COUNT(*) FROM public_notice WHERE inspection_id=? AND withdrawn_at IS NULL",x)>0)throw new Rule("该抽查结果已公示");String body="抽查方式："+(r.get("method").equals("PHONE")?"电话回访":"上门核查")+"；评分："+r.get("score")+"；处置状态：已完成。个人身份、住址、健康和投诉内容不予公开。";return db.insert("INSERT INTO public_notice(inspection_id,publisher_id,title,body) VALUES(?,?,?,?)",x,u.id(),"养老服务质量抽查结果",body);
            }
            case "notice.withdraw":{
                Auth.role(u,"REGULATOR");x=number(m,"noticeId");r=db.one("SELECT * FROM public_notice WHERE id=? FOR UPDATE",x);scope.inspection(u,id(r,"inspection_id"));if(r.get("withdrawn_at")!=null)throw new Rule("公示已撤回");return db.update("UPDATE public_notice SET withdrawn_at=NOW(),withdraw_reason=? WHERE id=?",text(m,"reason"),x);
            }
            default:throw new Rule("未知监管操作");
        }
    }
}
