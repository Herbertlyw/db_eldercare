package edu.bjfu.eldercare;
import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.dao.DataAccessException;import java.util.*;
@RestController @RequestMapping("/api") public class Api {
 private final Store db;private final Auth auth;private final CareService service;
 public Api(Store d,Auth a,CareService s){db=d;auth=a;service=s;}
 @PostMapping("/login") public Object login(@RequestBody Map<String,String> body){return auth.login(body.get("username"),body.get("password"));}
 @PostMapping("/logout") public Object logout(@RequestHeader(value="Authorization",required=false)String token){auth.logout(token);return Map.of("ok",true);}
 @GetMapping("/me") public Object me(@RequestHeader(value="Authorization",required=false)String token){return auth.require(token);}
 @PostMapping("/actions/{action}") public Object action(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String action,@RequestBody Map<String,Object> body){return Map.of("result",service.execute(auth.require(token),action,body));}
 @GetMapping("/data/{kind}") public Object data(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String kind,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
 var u=auth.require(token);if(page<0||page>100000||size<1||size>100)throw new Rule("分页参数无效");
 String sql;List<Object> params=new ArrayList<>();
 switch(kind){
 case "elders":sql="SELECT e.id,e.name,e.birth_date,e.address,e.community_id,e.status FROM elder e WHERE "+elderFilter(u,params);break;
 case "orders":sql="SELECT o.*,e.name elder_name FROM service_order o JOIN elder e ON e.id=o.elder_id WHERE "+elderFilter(u,params);if(u.role().equals("CAREGIVER")){sql+=" AND EXISTS(SELECT 1 FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id WHERE d.order_id=o.id AND c.account_id=?)";params.add(u.id());}break;
 case "plans":sql="SELECT p.*,e.name elder_name FROM v_plan_context p JOIN elder e ON e.id=p.elder_id WHERE "+elderFilter(u,params);break;
 case "assessments":sql="SELECT a.*,e.name elder_name FROM assessment a JOIN elder e ON e.id=a.elder_id WHERE "+elderFilter(u,params);break;
 case "events":sql="SELECT x.*,e.name elder_name FROM emergency x JOIN elder e ON e.id=x.elder_id WHERE "+elderFilter(u,params);break;
 case "claims":Auth.role(u,"ADMIN","FAMILY","STREET","REGULATOR");sql="SELECT c.*,e.name elder_name FROM claim c JOIN elder e ON e.id=c.elder_id WHERE "+elderFilter(u,params);break;
 case "devices":Auth.role(u,"ADMIN","FAMILY","CAREGIVER");sql="SELECT d.*,b.id binding_id,b.elder_id FROM device d JOIN device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL JOIN elder e ON e.id=b.elder_id WHERE "+elderFilter(u,params);break;
 case "vitals":Auth.role(u,"ADMIN","FAMILY","CAREGIVER");sql="SELECT v.*,b.elder_id FROM vital v JOIN device_binding b ON b.id=v.binding_id JOIN elder e ON e.id=b.elder_id WHERE "+elderFilter(u,params);break;
 case "caregivers":Auth.role(u,"ADMIN","MANAGER","CAREGIVER","REGULATOR");sql="SELECT c.*,a.community_id FROM caregiver c JOIN account a ON a.id=c.account_id WHERE "+workerFilter(u,params);break;
 case "shifts":Auth.role(u,"ADMIN","MANAGER","CAREGIVER");sql="SELECT s.*,c.name caregiver_name FROM shift s JOIN caregiver c ON c.id=s.caregiver_id JOIN account a ON a.id=c.account_id WHERE "+workerFilter(u,params);break;
 case "credentials":Auth.role(u,"ADMIN","MANAGER","CAREGIVER");sql="SELECT x.*,c.name caregiver_name FROM credential x JOIN caregiver c ON c.id=x.caregiver_id JOIN account a ON a.id=c.account_id WHERE "+workerFilter(u,params);break;
 case "hours":Auth.role(u,"ADMIN","MANAGER","CAREGIVER");sql="SELECT h.*,d.order_id,c.name caregiver_name FROM attendance h JOIN v_dispatch_context d ON d.id=h.dispatch_id JOIN caregiver c ON c.id=d.caregiver_id JOIN account a ON a.id=c.account_id WHERE "+workerFilter(u,params);break;
 case "inspections":Auth.role(u,"REGULATOR","MANAGER","ADMIN");sql="SELECT i.* FROM inspection i JOIN account a ON a.id=i.inspector_id WHERE a.community_id=?";params.add(u.community());if(u.role().equals("MANAGER")){sql+=" AND i.caregiver_id IN(SELECT id FROM caregiver WHERE organization_id=?)";params.add(u.organization());}break;
 case "catalog":sql="SELECT * FROM service_item WHERE 1=1";break;
 case "bundles":sql="SELECT * FROM care_bundle WHERE 1=1";break;
 case "policies":sql="SELECT * FROM policy WHERE community_id=?";params.add(u.community());break;
 case "notifications":sql="SELECT * FROM notification WHERE account_id=?";params.add(u.id());break;
 default:throw new Rule("未知数据类别");
 }
 long total=db.count("SELECT COUNT(*) FROM ("+sql+") q",params.toArray());sql+=" ORDER BY 1 DESC LIMIT ? OFFSET ?";params.add(size);params.add(page*size);return Map.of("rows",db.rows(sql,params.toArray()),"total",total,"page",page,"size",size);
 }
 private String elderFilter(Auth.User u,List<Object> p){return switch(u.role()){
 case "FAMILY"->{p.add(u.id());yield "EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.id AND g.account_id=?)";}
 case "CAREGIVER"->{p.add(u.id());yield "EXISTS(SELECT 1 FROM service_order z JOIN v_dispatch_context d ON d.order_id=z.id JOIN caregiver c ON c.id=d.caregiver_id WHERE z.elder_id=e.id AND c.account_id=?)";}
 case "MANAGER"->{p.add(u.organization());yield "EXISTS(SELECT 1 FROM service_order z JOIN v_dispatch_context d ON d.order_id=z.id JOIN caregiver c ON c.id=d.caregiver_id WHERE z.elder_id=e.id AND c.organization_id=?)";}
 default->{p.add(u.community());yield "e.community_id=?";}};}
 private String workerFilter(Auth.User u,List<Object>p){if(u.role().equals("MANAGER")){p.add(u.organization());return "c.organization_id=?";}if(u.role().equals("CAREGIVER")){p.add(u.id());return "c.account_id=?";}p.add(u.community());return "a.community_id=?";}
}
@RestControllerAdvice class Errors{
 @ExceptionHandler(Unauthorized.class)ResponseEntity<?> unauth(){return ResponseEntity.status(401).body(Map.of("error","请登录或重新登录"));}
 @ExceptionHandler(Forbidden.class)ResponseEntity<?> forbidden(){return ResponseEntity.status(403).body(Map.of("error","无权操作该数据"));}
 @ExceptionHandler({Rule.class,IllegalArgumentException.class})ResponseEntity<?> bad(RuntimeException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()==null?"输入无效":e.getMessage()));}
 @ExceptionHandler(DataAccessException.class)ResponseEntity<?> data(DataAccessException e){String msg=e.getMostSpecificCause().getMessage();String safe="数据库规则拒绝了操作，请检查关联、重复数据、状态或时间范围";for(String code:List.of("PLAN_OVERLAP","SHIFT_OVERLAP","PLAN_ASSESSMENT_INVALID","WORKER_NOT_QUALIFIED","WORKER_TIME_CONFLICT","SHIFT_FULL_OR_OUTSIDE","ORDER_PLAN_INVALID","CONFIRMATION_REQUIRED","RECTIFICATION_REQUIRED","CLAIM_ORDER_INELIGIBLE","CLAIM_EVIDENCE_MISSING","CLAIM_STATE_INVALID","HISTORY_DELETE_FORBIDDEN","VITAL_BINDING_TIME_INVALID"))if(msg!=null&&msg.contains(code))safe=code;return ResponseEntity.status(409).body(Map.of("error",safe));}
}
