package edu.bjfu.eldercare;

import org.springframework.stereotype.Service;
import java.util.*;
import static edu.bjfu.eldercare.Store.id;

@Service
public class ReadService {
    private final Store db;private final Scope scope;private final Crypto crypto;
    public ReadService(Store db,Scope scope,Crypto crypto){this.db=db;this.scope=scope;this.crypto=crypto;}
    private static final Set<String> KINDS=Set.of("elders","devices","currentDevices","claims","claimProgress","policies","inspections","organizationQuality","complaints","recruitments","trainings","trainingRecords","inspectionBatches","organizationReports","publicNotices","jurisdictionStats","communities","accounts","metrics","areas","managerDispatchPool");
    public boolean handles(String kind){return KINDS.contains(kind);}
    private String organization(Auth.User u,List<Object> p,String column){if(u.role().equals("MANAGER")){p.add(u.organization());return column+"=?";}return column+" IN(SELECT org.id FROM organization org WHERE "+scope.communityFilter(u,"org.community_id",p)+")";}
    private String claimFilter(Auth.User u,List<Object> p){if(u.role().equals("FAMILY")){p.add(u.id());return "EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=c.elder_id AND g.account_id=? AND g.active=1)";}return scope.communityFilter(u,"c.community_id",p);}
    public Object list(Auth.User u,String kind,int page,int size){
        List<Object> p=new ArrayList<>();String sql;
        switch(kind){
            case "elders":sql="SELECT e.id,e.name,e.birth_date,e.address,e.community_id,e.status,e.position_x,e.position_y,(SELECT GROUP_CONCAT(tag ORDER BY tag) FROM elder_tag t WHERE t.elder_id=e.id) tags FROM elder e WHERE "+scope.elderFilter(u,p);break;
            case "devices":case "currentDevices":Auth.role(u,"ADMIN","FAMILY","CAREGIVER");if(u.role().equals("ADMIN")){sql="SELECT d.*,b.id binding_id,b.elder_id FROM device d LEFT JOIN device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL WHERE d.community_id=?";p.add(u.community());}else sql="SELECT d.*,b.id binding_id,b.elder_id FROM device d JOIN device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL JOIN elder e ON e.id=b.elder_id WHERE "+scope.elderFilter(u,p);break;
            case "claims":case "claimProgress":Auth.role(u,"ADMIN","FAMILY","STREET","REGULATOR");sql="SELECT c.*,e.name elder_name,p.reference_no,p.paid_at FROM claim c JOIN elder e ON e.id=c.elder_id LEFT JOIN payment p ON p.claim_id=c.id WHERE "+claimFilter(u,p);break;
            case "policies":{
                sql="SELECT DISTINCT pol.* FROM policy pol JOIN policy_area pa ON pa.policy_id=pol.id WHERE ";
                if(u.role().equals("FAMILY")){p.add(u.id());sql+="EXISTS(SELECT 1 FROM guardian g JOIN elder e ON e.id=g.elder_id WHERE g.account_id=? AND g.active=1 AND e.community_id=pa.community_id)";}else sql+=scope.communityFilter(u,"pa.community_id",p);break;
            }
            case "inspections":Auth.role(u,"ADMIN","MANAGER","REGULATOR");sql="SELECT i.* FROM inspection i LEFT JOIN service_order o ON o.id=i.order_id LEFT JOIN caregiver c ON c.id=i.caregiver_id LEFT JOIN organization org ON org.id=c.organization_id WHERE ";if(u.role().equals("MANAGER")){p.add(u.organization());p.add(u.organization());sql+="(c.organization_id=? OR EXISTS(SELECT 1 FROM v_dispatch_context d JOIN caregiver w ON w.id=d.caregiver_id WHERE d.order_id=i.order_id AND d.status='FINISHED' AND w.organization_id=?))";}else sql+=scope.communityFilter(u,"COALESCE(o.community_id,org.community_id)",p);break;
            case "organizationQuality":Auth.role(u,"ADMIN","MANAGER","REGULATOR");sql="SELECT org.id,org.name,(SELECT ROUND(AVG(i.score),2) FROM inspection i WHERE i.caregiver_id IN(SELECT id FROM caregiver WHERE organization_id=org.id) OR EXISTS(SELECT 1 FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id WHERE d.order_id=i.order_id AND d.status='FINISHED' AND c.organization_id=org.id)) inspection_score FROM organization org WHERE "+organization(u,p,"org.id");break;
            case "complaints":Auth.role(u,"ADMIN","FAMILY","MANAGER","REGULATOR");sql="SELECT c.*,e.name elder_name FROM complaint c JOIN service_order o ON o.id=c.order_id JOIN elder e ON e.id=o.elder_id WHERE ";if(u.role().equals("MANAGER")){sql+="c.organization_id=?";p.add(u.organization());}else if(u.role().equals("FAMILY"))sql+=scope.elderFilter(u,p);else sql+=scope.communityFilter(u,"o.community_id",p);break;
            case "recruitments":Auth.role(u,"MANAGER");sql="SELECT id,candidate_name,status,caregiver_id,review_note,created_at,reviewed_at FROM recruitment WHERE organization_id=?";p.add(u.organization());break;
            case "trainings":Auth.role(u,"MANAGER","CAREGIVER");sql="SELECT * FROM training WHERE organization_id=?";p.add(u.organization());break;
            case "trainingRecords":Auth.role(u,"MANAGER","CAREGIVER");sql="SELECT r.*,t.title,c.name FROM training_record r JOIN training t ON t.id=r.training_id JOIN caregiver c ON c.id=r.caregiver_id WHERE ";if(u.role().equals("MANAGER")){sql+="t.organization_id=?";p.add(u.organization());}else{sql+="c.account_id=?";p.add(u.id());}break;
            case "inspectionBatches":Auth.role(u,"REGULATOR");sql="SELECT * FROM inspection_batch WHERE "+scope.communityFilter(u,"community_id",p);break;
            case "organizationReports":Auth.role(u,"MANAGER","REGULATOR");sql="SELECT r.* FROM organization_report r WHERE "+organization(u,p,"r.organization_id");break;
            case "publicNotices":sql="SELECT id,title,body,published_at FROM public_notice WHERE withdrawn_at IS NULL";break;
            case "communities":sql="SELECT * FROM community WHERE ";if(Set.of("STREET","REGULATOR").contains(u.role()))sql+=scope.communityFilter(u,"id",p);else sql+="1=1";break;
            case "accounts":Auth.role(u,"ADMIN");sql="SELECT id,username,role,active FROM account WHERE community_id=? AND role='FAMILY'";p.add(u.community());break;
            case "metrics":Auth.role(u,"ADMIN");sql="SELECT * FROM metric_rule";break;
            case "areas":Auth.role(u,"MANAGER","CAREGIVER","ADMIN");sql="SELECT a.id,a.caregiver_id,c.name,co.name community_name,a.community_id FROM caregiver_area a JOIN caregiver c ON c.id=a.caregiver_id JOIN community co ON co.id=a.community_id WHERE ";if(u.role().equals("CAREGIVER")){sql+="c.account_id=?";p.add(u.id());}else sql+=organization(u,p,"c.organization_id");break;
            case "managerDispatchPool":Auth.role(u,"MANAGER");sql="SELECT o.id,o.elder_id,o.starts_at,d.assigned_at,c.name caregiver_name FROM v_dispatch_overdue v JOIN service_order o ON o.id=v.id JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE d.status='WAITING' AND c.organization_id=?";p.add(u.organization());break;
            case "jurisdictionStats":Auth.role(u,"STREET","REGULATOR");sql=stats(u,p);break;
            default:throw new Rule("未知数据类别");
        }
        long total=db.count("SELECT COUNT(*) FROM ("+sql+") q",p.toArray());p.add(size);p.add(page*size);var rows=db.rows(sql+" ORDER BY 1 DESC LIMIT ? OFFSET ?",p.toArray());if(kind.equals("jurisdictionStats"))for(var row:rows){long elders=id(row,"active_elders"),events=id(row,"response_denominator");row.put("coverage_rate",elders==0?null:Math.round(id(row,"served_elders")*10000.0/elders)/100.0);row.put("response_rate",events==0?null:Math.round(id(row,"timely_responses")*10000.0/events)/100.0);}return Map.of("rows",rows,"total",total,"page",page,"size",size);
    }
    private String stats(Auth.User u,List<Object> p){
        return "SELECT co.id,co.name,co.district,co.street_name,DATE_FORMAT(CURRENT_DATE,'%Y-%m') period,"+
        "(SELECT COUNT(*) FROM elder e WHERE e.community_id=co.id AND e.status='ACTIVE') active_elders,"+
        "(SELECT COUNT(DISTINCT e.id) FROM elder e JOIN service_order o ON o.elder_id=e.id WHERE e.community_id=co.id AND e.status='ACTIVE' AND o.status='COMPLETED' AND DATE_FORMAT(o.starts_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')) served_elders,"+
        "(SELECT COUNT(*) FROM emergency x JOIN elder e ON e.id=x.elder_id WHERE e.community_id=co.id AND DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')) events_total,"+
        "(SELECT COUNT(*) FROM emergency x JOIN elder e ON e.id=x.elder_id WHERE e.community_id=co.id AND x.status<>'FALSE_ALARM' AND x.initial_deadline IS NOT NULL AND DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')) response_denominator,"+
        "(SELECT COUNT(*) FROM emergency x JOIN elder e ON e.id=x.elder_id WHERE e.community_id=co.id AND x.status<>'FALSE_ALARM' AND x.responded_at<=x.initial_deadline AND DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')) timely_responses,"+
        "(SELECT COALESCE(SUM(c.amount),0) FROM claim c JOIN payment pay ON pay.claim_id=c.id WHERE c.community_id=co.id AND c.status='PAID' AND DATE_FORMAT(pay.paid_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')) paid_subsidy FROM community co WHERE "+scope.communityFilter(u,"co.id",p);
    }
    public Object details(Auth.User u,String kind,long x){
        LinkedHashMap<String,Object> out=new LinkedHashMap<>();
        switch(kind){
            case "elders":{
                Auth.role(u,"ADMIN","FAMILY","CAREGIVER");scope.elder(u,x);out.put("档案",db.one("SELECT id,name,birth_date,address,community_id,status,position_x,position_y FROM elder WHERE id=?",x));out.put("标签",db.rows("SELECT id,tag FROM elder_tag WHERE elder_id=?",x));var guardians=db.rows("SELECT id,relation_name,phone_cipher,active FROM guardian WHERE elder_id=?",x);for(var g:guardians)g.put("phone",crypto.decrypt((String)g.remove("phone_cipher")));out.put("家属联系方式",guardians);break;
            }
            case "plans":{
                var plan=db.one("SELECT p.*,b.name bundle_name,c.name caregiver_name FROM v_plan_context p JOIN care_bundle b ON b.id=p.bundle_id JOIN caregiver c ON c.id=p.caregiver_id WHERE p.id=?",x);scope.elder(u,id(plan,"elder_id"));out.put("照护计划",plan);out.put("服务项目与频次",db.rows("SELECT i.id,i.item_id,s.name,i.weekly_frequency FROM plan_item i JOIN service_item s ON s.id=i.item_id WHERE i.plan_id=?",x));break;
            }
            case "bundles":out.put("服务包",db.one("SELECT * FROM care_bundle WHERE id=?",x));out.put("项目清单",db.rows("SELECT i.*,s.name FROM bundle_item i JOIN service_item s ON s.id=i.item_id WHERE i.bundle_id=?",x));break;
            case "orders":{
                out.put("订单",scope.order(u,x));List<Object> p=new ArrayList<>(List.of(x));String restriction="";if(u.role().equals("CAREGIVER")){restriction=" AND c.account_id=?";p.add(u.id());}if(u.role().equals("MANAGER")){restriction=" AND c.organization_id=?";p.add(u.organization());}
                out.put("派单与服务过程",db.rows("SELECT d.id dispatch_id,c.name caregiver_name,d.status,d.reason,d.assigned_at,d.accepted_at,a.check_in,a.check_out,a.location_note,a.fence_ok,a.checkout_fence_ok,a.effective_minutes,a.status hours_status,a.review_reason,r.notes,r.confirmation,r.confirmed_at FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id LEFT JOIN attendance a ON a.dispatch_id=d.id LEFT JOIN service_record r ON r.dispatch_id=d.id WHERE d.order_id=?"+restriction+" ORDER BY d.id",p.toArray()));break;
            }
            case "claims":case "claimProgress":{
                Auth.role(u,"ADMIN","FAMILY","STREET","REGULATOR");out.put("补贴申报",scope.claim(u,x));out.put("订单与核算依据",db.rows("SELECT ci.*,o.starts_at,o.amount order_amount,s.name item_name,r.notes,r.confirmation,r.confirmed_at,a.check_in,a.check_out,a.review_reason FROM claim_item ci JOIN service_order o ON o.id=ci.order_id JOIN service_item s ON s.id=o.item_id JOIN v_dispatch_context d ON d.order_id=o.id AND d.status='FINISHED' JOIN service_record r ON r.dispatch_id=d.id JOIN attendance a ON a.dispatch_id=d.id WHERE ci.claim_id=?",x));out.put("审核记录",db.rows("SELECT cr.*,a.username reviewer_name FROM claim_review cr JOIN account a ON a.id=cr.reviewer_id WHERE cr.claim_id=? ORDER BY cr.id",x));out.put("归档材料",db.rows("SELECT id,name,content,content_sha256,created_at FROM claim_evidence WHERE claim_id=?",x));out.put("拨付凭据",db.rows("SELECT reference_no,amount,paid_at FROM payment WHERE claim_id=?",x));break;
            }
            case "complaints":{
                Auth.role(u,"ADMIN","FAMILY","MANAGER","REGULATOR");var c=db.one("SELECT * FROM complaint WHERE id=?",x);scope.order(u,id(c,"order_id"));if(u.role().equals("MANAGER")&&!Objects.equals(c.get("organization_id"),u.organization()))throw new Forbidden();out.put("评价与投诉",c);out.put("处理过程",db.rows("SELECT l.*,a.username actor_name FROM complaint_log l JOIN account a ON a.id=l.actor_id WHERE l.complaint_id=? ORDER BY l.id",x));break;
            }
            case "devices":case "currentDevices":{
                Auth.role(u,"ADMIN","FAMILY","CAREGIVER");var device=db.one("SELECT * FROM device WHERE id=?",x);if(u.role().equals("ADMIN")){scope.community(u,id(device,"community_id"));out.put("绑定历史",db.rows("SELECT b.*,e.name elder_name FROM device_binding b JOIN elder e ON e.id=b.elder_id WHERE b.device_id=? ORDER BY b.id",x));}else{List<Object> p=new ArrayList<>(List.of(x));String filter=scope.elderFilter(u,p);var bindings=db.rows("SELECT b.* FROM device_binding b JOIN elder e ON e.id=b.elder_id WHERE b.device_id=? AND "+filter,p.toArray());if(bindings.isEmpty())throw new Forbidden();out.put("本人的绑定历史",bindings);}out.put("设备",device);break;
            }
            case "events":case "pendingEvents":{
                var e=db.one("SELECT * FROM emergency WHERE id=?",x);scope.elder(u,id(e,"elder_id"));out.put("应急事件",e);out.put("升级记录",db.rows("SELECT level,created_at,message FROM emergency_log WHERE event_id=? ORDER BY id",x));out.put("处置记录",db.rows("SELECT s.*,a.username actor_name FROM event_step s JOIN account a ON a.id=s.actor_id WHERE s.event_id=? ORDER BY s.id",x));break;
            }
            case "inspections":case "rectifications":{
                Auth.role(u,"ADMIN","MANAGER","REGULATOR");out.put("质量抽查",scope.inspection(u,x));out.put("整改与复核",db.rows("SELECT * FROM rectification_submission WHERE inspection_id=? ORDER BY id",x));break;
            }
            case "inspectionBatches":{
                Auth.role(u,"REGULATOR");var batch=db.one("SELECT * FROM inspection_batch WHERE id=?",x);scope.community(u,id(batch,"community_id"));out.put("抽样依据",batch);out.put("抽中对象",db.rows("SELECT * FROM inspection WHERE batch_id=? ORDER BY id",x));break;
            }
            case "caregivers":{
                Auth.role(u,"ADMIN","MANAGER","CAREGIVER","REGULATOR");scope.worker(u,x);out.put("护工档案",db.one("SELECT * FROM caregiver WHERE id=?",x));out.put("服务区域",db.rows("SELECT a.id,c.name,c.id community_id FROM caregiver_area a JOIN community c ON c.id=a.community_id WHERE a.caregiver_id=?",x));out.put("项目资格",db.rows("SELECT x.*,s.name item_name FROM credential x JOIN service_item s ON s.id=x.item_id WHERE x.caregiver_id=?",x));out.put("培训记录",db.rows("SELECT r.*,t.title FROM training_record r JOIN training t ON t.id=r.training_id WHERE r.caregiver_id=?",x));out.put("累计有效工时",Map.of("effective_minutes",db.count("SELECT COALESCE(SUM(a.effective_minutes),0) FROM attendance a JOIN v_dispatch_context d ON d.id=a.dispatch_id WHERE d.caregiver_id=? AND a.status='VALID'",x)));break;
            }
            default:throw new Rule("该类别不提供详情");
        }return out;
    }
}
