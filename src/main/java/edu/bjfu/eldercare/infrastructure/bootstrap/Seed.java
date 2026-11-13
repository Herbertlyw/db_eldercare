package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class Seed implements CommandLineRunner {

    private final Store d;

    private final Crypto crypto;

    private final Auth auth;

    public Seed(Store d, Crypto c, Auth a) {
        this.d = d;
        crypto = c;
        auth = a;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (d.count("SELECT COUNT(*) FROM community") > 0) {
            throw new Rule("种子数据只允许导入空库");
        }
        d.update("SET @actor='seed:synthetic'");
        String pass = System.getenv("DEMO_PASSWORD");
        if (pass == null) {
            throw new Rule("DEMO_PASSWORD 缺失");
        }
        String hash = auth.password(pass);
        LocalDateTime now = LocalDateTime.now().withNano(0),
                past = now.minusDays(10).withHour(10).withMinute(0).withSecond(0),
                future = now.plusDays(1).withHour(10).withMinute(0).withSecond(0);
        String[] roles = {"ADMIN", "FAMILY", "CAREGIVER", "MANAGER", "STREET", "REGULATOR"};
        for (int i = 1; i <= 24; i++) {
            String ability = i <= 20 ? "SEVERE" : i % 2 == 0 ? "PARTIAL" : "SELF";
            d.update(
                    "INSERT INTO community(id,name,district,area_code,street_name,center_x)"
                            + " VALUES(?,?,?,?,?,?)",
                    i,
                    "演示社区" + i,
                    "演示区县" + ((i - 1) / 4 + 1),
                    "DEMO-" + i,
                    "演示街道" + ((i - 1) / 2 + 1),
                    i * 10000);
            d.update(
                    "INSERT INTO organization(id,name,community_id) VALUES(?,?,?)",
                    i,
                    "演示服务机构" + i,
                    i);
            for (int j = 0; j < roles.length; j++) {
                d.update(
                        "INSERT INTO"
                            + " account(id,username,password_hash,role,community_id,organization_id)"
                            + " VALUES(?,?,?,?,?,?)",
                        (i - 1) * 6 + j + 1,
                        roles[j].toLowerCase() + i,
                        hash,
                        roles[j],
                        i,
                        i);
            }
            long admin = (i - 1) * 6 + 1,
                    family = admin + 1,
                    worker = admin + 2,
                    regulator = admin + 5;
            d.update(
                    "INSERT INTO"
                        + " elder(id,name,identity_hash,identity_cipher,birth_date,address,community_id,position_x)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                    i,
                    "演示老人" + i,
                    crypto.hash("SYNTHETIC-ID-" + i),
                    crypto.encrypt("SYNTHETIC-ID-" + i),
                    LocalDate.of(1940 + i % 20, 1, 1),
                    "演示社区" + i + "一号楼",
                    i,
                    i * 10000);
            d.update("INSERT INTO elder_tag(elder_id,tag) VALUES(?,'ALONE')", i);
            d.update(
                    "INSERT INTO guardian(elder_id,account_id,relation_name,phone_cipher)"
                            + " VALUES(?,?,'子女',?)",
                    i,
                    family,
                    crypto.encrypt("DEMO-PHONE-" + i));
            d.update(
                    "INSERT INTO"
                        + " assessment(id,elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                        + " VALUES(?,?,?,30,?,?,?,?)",
                    i,
                    i,
                    "课程模拟量表",
                    ability,
                    admin,
                    now.minusDays(120),
                    now.plusDays(365));
            d.update(
                    "INSERT INTO"
                        + " service_item(id,name,category,unit,price,duration_minutes,subsidizable)"
                        + " VALUES(?,?,?,'VISIT',80,60,1)",
                    i,
                    "上门服务方案" + i,
                    new String[] {"助餐", "助浴", "陪诊", "家政", "康复"}[(i - 1) % 5]);
            d.update(
                    "INSERT INTO care_bundle(id,name,ability_level) VALUES(?,?,?)",
                    i,
                    "演示照护包" + i,
                    ability);
            d.update(
                    "INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,?,2)",
                    i,
                    i);
            d.update(
                    "INSERT INTO caregiver(id,account_id,organization_id,name) VALUES(?,?,?,?)",
                    i,
                    worker,
                    i,
                    "演示护工" + i);
            d.update(
                    "INSERT INTO"
                        + " credential(caregiver_id,item_id,certificate_no,certificate_level,valid_from,valid_until)"
                        + " VALUES(?,?,?,'课程模拟资格',?,?)",
                    i,
                    i,
                    "DEMO-CERT-" + i,
                    now.minusDays(365),
                    now.plusDays(365));
            d.update("INSERT INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)", i, i);
            d.update(
                    "INSERT INTO"
                        + " care_plan(id,assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                        + " VALUES(?,?,?,?,?,?)",
                    i,
                    i,
                    i,
                    i,
                    now.minusDays(90),
                    now.plusDays(90));
            d.update("INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,?,2)", i, i);
            long shift =
                    d.insert(
                            "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity,used_count)"
                                    + " VALUES(?,?,?,3,1)",
                            i,
                            past.minusHours(2),
                            past.plusHours(8));
            d.insert(
                    "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity) VALUES(?,?,?,3)",
                    i,
                    future.minusHours(2),
                    future.plusHours(8));
            long order =
                    d.insert(
                            "INSERT INTO"
                                + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x)"
                                + " VALUES(?,?,?,?,?,?,'COMMUNITY',80,?,?)",
                            i,
                            i,
                            i,
                            past,
                            past.plusHours(1),
                            "演示地址" + i,
                            admin,
                            i * 10000);
            long old =
                    d.insert("INSERT INTO dispatch(order_id,shift_id) VALUES(?,?)", order, shift);
            d.update(
                    "UPDATE dispatch SET status='REASSIGNED',reason='演示历史改派后重新安排' WHERE id=?", old);
            long dispatch =
                    d.insert(
                            "INSERT INTO dispatch(order_id,shift_id,status,accepted_at)"
                                    + " VALUES(?,?,'ACCEPTED',?)",
                            order,
                            shift,
                            past.minusHours(1));
            d.insert(
                    "INSERT INTO"
                        + " attendance(dispatch_id,check_in,check_out,location_note,fence_ok,checkout_fence_ok)"
                        + " VALUES(?,?,?,'模拟地址围栏内',1,1)",
                    dispatch,
                    past,
                    past.plusHours(1));
            d.call("verify_hours", dispatch);
            d.insert(
                    "INSERT INTO"
                        + " service_record(dispatch_id,notes,confirmed_by,confirmed_at,confirmation)"
                        + " VALUES(?,'模拟服务记录',?,?,'演示家属确认')",
                    dispatch,
                    family,
                    past.plusHours(1));
            d.update("UPDATE dispatch SET status='FINISHED' WHERE id=?", dispatch);
            d.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", order);
            d.insert(
                    "INSERT INTO"
                        + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x)"
                        + " VALUES(?,?,?,?,?,?,'COMMUNITY',80,?,?)",
                    i,
                    i,
                    i,
                    future,
                    future.plusHours(1),
                    "演示地址" + i,
                    admin,
                    i * 10000);
            d.update(
                    "INSERT INTO device(id,serial_no,type_name,location_note,community_id)"
                            + " VALUES(?,?,?,'演示住所',?)",
                    i,
                    "DEMO-DEVICE-" + i,
                    "模拟采集器",
                    i);
            d.update(
                    "INSERT INTO device_binding(id,device_id,elder_id,bound_at) VALUES(?,?,?,?)",
                    i,
                    i,
                    i,
                    now.minusDays(30));
            d.update(
                    "INSERT INTO metric_rule(id,metric,low_value,high_value,response_seconds)"
                            + " VALUES(?,?,10,90,300)",
                    i,
                    "课程演示指标" + i);
            d.insert(
                    "INSERT INTO"
                        + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                        + " VALUES(?,?,'sample-1',?,95,'SIMULATOR')",
                    i,
                    i,
                    now.minusMinutes(1));
            long event = d.count("SELECT MAX(id) FROM emergency");
            d.insert(
                    "INSERT INTO emergency_log(event_id,level,message) VALUES(?,1,'模拟异常待确认')",
                    event);
            d.insert(
                    "INSERT INTO notification(event_id,account_id,message)"
                            + " VALUES(?,?,'模拟异常，请及时确认')",
                    event,
                    admin);
            d.update(
                    "INSERT INTO"
                        + " policy(id,name,community_id,item_id,ability_level,rate,starts_at,ends_at)"
                        + " VALUES(?,?,?,?,?,30,?,?)",
                    i,
                    "课程虚构补贴政策" + i,
                    i,
                    i,
                    ability,
                    now.minusDays(365),
                    now.plusDays(365));
            d.insert("INSERT INTO policy_area(policy_id,community_id) VALUES(?,?)", i, i);
            d.call("submit_claim", i, i, past.toString().substring(0, 7), "[" + order + "]");
            long claim = d.count("SELECT MAX(id) FROM claim");
            d.insert(
                    "INSERT INTO claim_review(claim_id,reviewer_id,stage,decision,reason)"
                            + " VALUES(?,?,'STREET','APPROVE','演示审核')",
                    claim,
                    admin + 4);
            d.update("UPDATE claim SET status='STREET_APPROVED' WHERE id=?", claim);
            d.insert(
                    "INSERT INTO claim_review(claim_id,reviewer_id,stage,decision,reason)"
                            + " VALUES(?,?,'REGULATOR','APPROVE','演示审核')",
                    claim,
                    regulator);
            d.update("UPDATE claim SET status='APPROVED' WHERE id=?", claim);
            d.insert(
                    "INSERT INTO payment(claim_id,reference_no,amount) VALUES(?,?,30)",
                    claim,
                    "DEMO-PAY-" + i);
            d.update("UPDATE claim SET status='PAID' WHERE id=?", claim);
            d.insert(
                    "INSERT INTO"
                        + " inspection(caregiver_id,inspector_id,method,score,issue,rectification,status,result)"
                        + " VALUES(?,?,'PHONE',95,'历史服务记录不全','补齐回访记录','CLOSED','模拟整改复核通过')",
                    i,
                    regulator);
            long feedback =
                    d.insert(
                            "INSERT INTO complaint(order_id,account_id,rating,content,status,kind)"
                                    + " VALUES(?,?,5,'课程模拟服务评价','CLOSED','REVIEW')",
                            order,
                            family);
            d.insert(
                    "INSERT INTO complaint_log(complaint_id,actor_id,status,message)"
                            + " VALUES(?,?,'CLOSED','虚构评价，无待处理投诉')",
                    feedback,
                    family);
            d.insert(
                    "INSERT INTO"
                        + " recruitment(organization_id,candidate_name,phone_cipher,status,caregiver_id,reviewer_id,review_note,reviewed_at)"
                        + " VALUES(?,?,?,'HIRED',?,?,'虚构历史招聘记录',?)",
                    i,
                    "演示护工" + i,
                    crypto.encrypt("DEMO-PHONE-" + i),
                    i,
                    admin + 3,
                    now.minusDays(200));
            long training =
                    d.insert(
                            "INSERT INTO training(organization_id,title,starts_at,ends_at)"
                                    + " VALUES(?,'课程模拟岗前培训',?,?)",
                            i,
                            now.minusDays(180),
                            now.minusDays(179));
            d.insert(
                    "INSERT INTO"
                        + " training_record(training_id,caregiver_id,result,score,recorded_by,recorded_at)"
                        + " VALUES(?,?,'PASS',90,?,?)",
                    training,
                    i,
                    admin + 3,
                    now.minusDays(179));
            long batch =
                    d.insert(
                            "INSERT INTO"
                                + " inspection_batch(community_id,inspector_id,target_type,starts_at,ends_at,ratio,population,sample_count,sample_seed)"
                                + " VALUES(?,?,'CAREGIVER',?,?,1,1,1,?)",
                            i,
                            regulator,
                            past.minusDays(1),
                            past.plusDays(1),
                            java.util.UUID.randomUUID().toString());
            d.update("UPDATE inspection SET batch_id=? WHERE id=?", batch, i);
            d.insert(
                    "INSERT INTO"
                        + " rectification_submission(inspection_id,submitted_by,measures,evidence,decision,reviewer_id,review_note,reviewed_at)"
                        + " VALUES(?,?,'虚构历史整改措施','虚构回访记录','ACCEPT',?,'历史材料核验通过',?)",
                    i,
                    admin + 3,
                    regulator,
                    past);
            d.insert(
                    "INSERT INTO"
                        + " organization_report(organization_id,period,summary,evidence,submitted_by)"
                        + " VALUES(?,?,'课程模拟机构月报','服务记录及工时明细',?)",
                    i,
                    past.toString().substring(0, 7),
                    admin + 3);
            d.insert(
                    "INSERT INTO public_notice(inspection_id,publisher_id,title,body)"
                            + " VALUES(?,?,'养老服务质量抽查结果','课程虚构抽查；评分 95；处置状态：已完成。无个人信息。')",
                    i,
                    regulator);
            d.insert(
                    "INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256)"
                            + " VALUES(?,?,'虚构归档说明','关联服务记录、工时及家属确认',SHA2('关联服务记录、工时及家属确认',256))",
                    claim,
                    admin);
            d.insert(
                    "INSERT INTO event_step(event_id,actor_id,action,message)"
                            + " VALUES(?,?,'REPORT','课程模拟告警上报')",
                    event,
                    admin);
        }
        d.update(
                "INSERT INTO account_scope(account_id,community_id) SELECT a.id,c.id FROM account a"
                    + " JOIN community home ON home.id=a.community_id JOIN community c ON"
                    + " c.district=home.district WHERE a.role='REGULATOR' OR (a.role='STREET' AND"
                    + " c.street_name=home.street_name)");
        System.out.println(
                "Synthetic seed: 24 communities, three ability groups; all records are fictional.");
    }
}
