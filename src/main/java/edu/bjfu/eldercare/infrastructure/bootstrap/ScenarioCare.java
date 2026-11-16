package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** People, qualifications and plans used by the linked business examples. */
final class ScenarioCare {
    record Person(
            long id,
            int community,
            String level,
            long worker,
            long family,
            long admin,
            long manager,
            long street,
            long regulator,
            long assessment) {}

    final LocalDateTime now;
    final List<Person> people = new ArrayList<>();
    final List<Long> workers = new ArrayList<>();
    final List<Long> items = new ArrayList<>();
    private final Store db;
    private final Crypto crypto;

    ScenarioCare(Store db, Crypto crypto, LocalDateTime now) {
        this.db = db;
        this.crypto = crypto;
        this.now = now;
    }

    void create() {
        createItems();
        createWorkers();
        String[] names = {"周惠琴", "何德明", "蒋淑芳", "陆永祥", "顾美珍", "潘庆生", "陶桂英"};
        for (int i = 0; i < names.length; i++) {
            int community = i < 4 || i == 6 ? 1 : 2;
            long base = (community - 1) * 6L;
            long elder = 21001 + i;
            String level = new String[] {"SELF", "PARTIAL", "SEVERE"}[i % 3];
            db.update(
                    "INSERT INTO"
                        + " elder(id,name,identity_hash,identity_cipher,birth_date,address,community_id,position_x,position_y,status)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                    elder,
                    names[i],
                    crypto.hash("COURSE-SCENE-PERSON-" + elder),
                    crypto.encrypt("COURSE-SCENE-PERSON-" + elder),
                    LocalDate.of(1943 + i * 3, 2 + i, 9 + i),
                    "邻里小区" + (i + 2) + "栋" + (201 + i) + "室",
                    community,
                    community * 10000 + 80 * i,
                    60 * i,
                    i == 6 ? "INACTIVE" : "ACTIVE");
            db.update(
                    "INSERT INTO elder_tag(elder_id,tag) VALUES(?,?)",
                    elder,
                    new String[] {"ALONE", "DISABLED", "LOW_INCOME"}[i % 3]);
            db.update(
                    "INSERT INTO guardian(elder_id,account_id,relation_name,phone_cipher,active)"
                            + " VALUES(?,?,?,?,?)",
                    elder,
                    base + 2,
                    i % 2 == 0 ? "女儿" : "儿子",
                    crypto.encrypt("COURSE-CONTACT-" + elder),
                    i != 6);
            long assessment =
                    db.insert(
                            "INSERT INTO"
                                + " assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                                + " VALUES(?,'日常生活能力评估',?,?,?,?,?)",
                            elder,
                            new int[] {92, 62, 28}[i % 3],
                            level,
                            base + 1,
                            now.minusDays(400),
                            now.plusDays(i == 5 ? 20 : 400));
            if (i == 6) {
                continue;
            }
            long worker = workers.get(i < 2 ? 0 : i < 4 ? 1 : 4);
            Person person =
                    new Person(
                            elder,
                            community,
                            level,
                            worker,
                            base + 2,
                            base + 1,
                            base + 4,
                            base + 5,
                            base + 6,
                            assessment);
            people.add(person);
            long bundle =
                    db.insert(
                            "INSERT INTO care_bundle(name,ability_level) VALUES(?,?)",
                            new String[] {"日常生活支持", "居家协助照护", "专人上门照护"}[i % 3]
                                    + (community == 1 ? "·邻里站" : "·社区站"),
                            level);
            long plan =
                    db.insert(
                            "INSERT INTO"
                                + " care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                                + " VALUES(?,?,?,?,?)",
                            assessment,
                            bundle,
                            worker,
                            now.minusDays(90),
                            now.plusDays(i == 5 ? 19 : 90));
            for (int j = 0; j < items.size(); j++) {
                db.update(
                        "INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,?,?)",
                        bundle,
                        items.get(j),
                        j == 0 ? 5 : 2);
                db.update(
                        "INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,?,?)",
                        plan,
                        items.get(j),
                        j == 0 ? 5 : 2);
            }
            if (i < 3) {
                String state = new String[] {"PAUSED", "EXPIRED", "ENDED"}[i];
                long historic =
                        db.insert(
                                "INSERT INTO"
                                    + " care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at,status)"
                                    + " VALUES(?,?,?,?,?,?)",
                                assessment,
                                bundle,
                                worker,
                                i == 0 ? now.plusDays(100) : now.minusDays(200),
                                i == 0 ? now.plusDays(120) : now.minusDays(100),
                                state);
                db.update(
                        "INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,?,2)",
                        historic,
                        items.get(0));
            }
        }
    }

    private void createItems() {
        String[] names = {"午间送餐与用餐协助", "居家清洁与整理", "门诊陪同与取药", "上门助浴", "陪伴散步与交流", "居家康复训练协助"};
        String[] categories = {"助餐", "家政", "陪诊", "助浴", "陪伴", "康复"};
        for (int i = 0; i < names.length; i++) {
            items.add(
                    db.insert(
                            "INSERT INTO"
                                + " service_item(name,category,unit,price,duration_minutes,subsidizable)"
                                + " VALUES(?,?,?,?,?,?)",
                            names[i],
                            categories[i],
                            i % 2 == 0 ? "VISIT" : "HOUR",
                            new int[] {28, 45, 80, 65, 35, 60}[i],
                            i == 2 ? 90 : 60,
                            i != 4));
        }
    }

    private void createWorkers() {
        String hash =
                db.one("SELECT password_hash FROM account WHERE id=2")
                        .get("password_hash")
                        .toString();
        String[] names = {"沈丽萍", "邱志强", "吴晓梅", "马文斌", "叶春华"};
        for (int i = 0; i < names.length; i++) {
            int community = i == 4 ? 2 : 1;
            String state = i == 2 ? "LEAVE" : i == 3 ? "LEFT" : "ACTIVE";
            long account =
                    db.insert(
                            "INSERT INTO"
                                + " account(username,password_hash,role,community_id,organization_id,active)"
                                + " VALUES(?,?,'CAREGIVER',?,?,?)",
                            "scene_caregiver" + (i + 1),
                            hash,
                            community,
                            community,
                            !state.equals("LEFT"));
            long worker =
                    db.insert(
                            "INSERT INTO caregiver(account_id,organization_id,name,status)"
                                    + " VALUES(?,?,?,?)",
                            account,
                            community,
                            names[i],
                            state);
            workers.add(worker);
            db.update(
                    "INSERT INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)",
                    worker,
                    community);
            if (i == 0) {
                db.update(
                        "INSERT INTO caregiver_area(caregiver_id,community_id) VALUES(?,2)",
                        worker);
            }
            long manager = (community - 1) * 6L + 4;
            db.update(
                    "INSERT INTO"
                        + " recruitment(organization_id,candidate_name,phone_cipher,status,caregiver_id,reviewer_id,review_note,created_at,reviewed_at)"
                        + " VALUES(?,?,?,'HIRED',?,?,'面试及材料核验通过',?,?)",
                    community,
                    names[i],
                    crypto.encrypt("COURSE-WORKER-PHONE-" + i),
                    worker,
                    manager,
                    now.minusDays(180),
                    now.minusDays(178));
            long training =
                    db.insert(
                            "INSERT INTO training(organization_id,title,starts_at,ends_at)"
                                    + " VALUES(?,'上门服务规范与应急处置',?,?)",
                            community,
                            now.minusDays(170),
                            now.minusDays(170).plusHours(4));
            db.update(
                    "INSERT INTO"
                        + " training_record(training_id,caregiver_id,result,score,recorded_by,recorded_at)"
                        + " VALUES(?,?,'PASS',?,?,?)",
                    training,
                    worker,
                    82 + i * 3,
                    manager,
                    now.minusDays(169));
            for (int j = 0; j < items.size(); j++) {
                db.update(
                        "INSERT INTO"
                            + " credential(caregiver_id,item_id,certificate_no,certificate_type,certificate_level,valid_from,valid_until)"
                            + " VALUES(?,?,?,'养老护理员',?,?,?)",
                        worker,
                        items.get(j),
                        "COURSE-SCENE-CERT-" + i + "-" + j,
                        j < 3 ? "基础照护" : "专项照护",
                        now.minusDays(160),
                        now.plusDays(i == 2 ? 8 : 365));
            }
        }
        for (int i = 0; i < 4; i++) {
            boolean pending = i < 2;
            db.update(
                    "INSERT INTO"
                        + " recruitment(organization_id,candidate_name,phone_cipher,status,reviewer_id,review_note,created_at,reviewed_at)"
                        + " VALUES(1,?,?,?,?,?,?,?)",
                    new String[] {"罗燕", "丁海波", "许小敏", "郑志成"}[i],
                    crypto.encrypt("COURSE-APPLICANT-" + i),
                    pending ? "APPLIED" : "REJECTED",
                    pending ? null : 4L,
                    pending ? null : "专项证书材料不全，可补齐后重新应聘",
                    now.minusDays(3),
                    pending ? null : now.minusDays(1));
        }
        long upcoming =
                db.insert(
                        "INSERT INTO training(organization_id,title,starts_at,ends_at)"
                                + " VALUES(1,'认知障碍沟通与照护',?,?)",
                        now.plusDays(7),
                        now.plusDays(7).plusHours(3));
        db.update(
                "INSERT INTO training_record(training_id,caregiver_id,result)"
                        + " VALUES(?,?,'PENDING')",
                upcoming,
                workers.get(0));
        long refresher =
                db.insert(
                        "INSERT INTO training(organization_id,title,starts_at,ends_at)"
                                + " VALUES(1,'助浴安全操作复训',?,?)",
                        now.minusDays(5),
                        now.minusDays(5).plusHours(3));
        db.update(
                "INSERT INTO"
                    + " training_record(training_id,caregiver_id,result,score,recorded_by,recorded_at)"
                    + " VALUES(?,?,'FAIL',58,4,?)",
                refresher,
                workers.get(2),
                now.minusDays(4));
        db.update(
                "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity,status)"
                        + " VALUES(?,?,?,2,'CLOSED')",
                workers.get(2),
                now.minusDays(8),
                now.minusDays(8).plusHours(4));
    }
}
