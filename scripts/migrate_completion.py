"""Upgrade the earlier 35-table coursework database without resetting it.
The caller must take a private full backup and stop the application first.
Use eldercare_migration for rehearsal; apply to eldercare only after verification.
"""
from pathlib import Path
import re,json,sys,datetime,hashlib
from db import sql,ROOT

def build(database):
    if int(sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='device' AND column_name='simulation_enabled'",database).stdout.splitlines()[-1]):
        raise RuntimeError('Completion migration already applied; refusing to repeat ALTER statements')
    triggers=sql('SHOW TRIGGERS',database).stdout.splitlines()[1:]
    routines=sql("SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE() AND ROUTINE_TYPE='PROCEDURE'",database).stdout.splitlines()[1:]
    before={row.split('\t')[0]:int(row.split('\t')[1]) for row in sql("SELECT 'elder',COUNT(*) FROM elder UNION ALL SELECT 'service_order',COUNT(*) FROM service_order UNION ALL SELECT 'claim',COUNT(*) FROM claim UNION ALL SELECT 'payment',COUNT(*) FROM payment",database).stdout.splitlines()[1:]}
    originals={}
    for table in sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'",database).stdout.splitlines()[1:]:
        cols=sql("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+table+"' ORDER BY ORDINAL_POSITION",database).stdout.splitlines()[1:]
        expressions=[]
        for col in cols:
            name,typ=col.split('\t');expressions.append("DATE_FORMAT(`"+name+"`,'%Y-%m-%dT%H:%i:%s.%f')" if typ in {'datetime','timestamp'} else '`'+name+'`')
        maximum=int(sql('SELECT COALESCE(MAX(id),0) FROM `'+table+'`',database).stdout.splitlines()[-1])
        query='SELECT JSON_ARRAY('+','.join(expressions)+') FROM `'+table+'` WHERE id<='+str(maximum)+' ORDER BY id'
        originals[table]=(query,hashlib.sha256(sql(query,database).stdout.encode()).hexdigest())
    statements=['-- Upgrade from the previous course version; backup and stop the app first.','SET @actor=\'migration:20261113\';']
    statements += ['DROP TRIGGER IF EXISTS `'+row.split('\t')[0]+'`;' for row in triggers]
    statements += ['DROP PROCEDURE IF EXISTS `'+name+'`;' for name in routines]
    schema=(ROOT/'database/01_schema.sql').read_text().split('-- Completion schema 2026-11-13.',1)[1]
    statements.append('-- Completion schema 2026-11-13.'+schema)
    statements.append('''
UPDATE community SET street_name=CONCAT('演示街道',CEIL(id/2)),center_x=id*10000 WHERE area_code LIKE 'DEMO-%';
UPDATE elder e JOIN community c ON c.id=e.community_id SET e.position_x=c.center_x,e.position_y=c.center_y;
UPDATE service_order o JOIN community c ON c.id=o.community_id SET o.position_x=c.center_x,o.position_y=c.center_y;
UPDATE device d SET community_id=COALESCE((SELECT e.community_id FROM device_binding b JOIN elder e ON e.id=b.elder_id WHERE b.device_id=d.id ORDER BY b.bound_at DESC,b.id DESC LIMIT 1),1);
UPDATE claim c JOIN elder e ON e.id=c.elder_id SET c.community_id=e.community_id,c.period_start=STR_TO_DATE(CONCAT(c.period,'-01'),'%Y-%m-%d'),c.period_end=DATE_ADD(STR_TO_DATE(CONCAT(c.period,'-01'),'%Y-%m-%d'),INTERVAL 1 MONTH);
UPDATE complaint SET kind='REVIEW',status='CLOSED',result='迁移前的历史服务评价';
INSERT INTO account_scope(account_id,community_id) SELECT a.id,c.id FROM account a JOIN community h ON h.id=a.community_id JOIN community c ON c.district=h.district WHERE a.role='REGULATOR' OR (a.role='STREET' AND c.street_name=h.street_name);
INSERT INTO policy_area(policy_id,community_id) SELECT id,community_id FROM policy;
INSERT INTO complaint_log(complaint_id,actor_id,status,message) SELECT id,account_id,'CLOSED','迁移前服务评价，保留原内容' FROM complaint;
INSERT INTO recruitment(organization_id,candidate_name,phone_cipher,status,caregiver_id,reviewer_id,review_note,reviewed_at) SELECT c.organization_id,c.name,g.phone_cipher,'HIRED',c.id,a.id,'迁移时补充的虚构历史招聘样例',NOW()-INTERVAL 200 DAY FROM caregiver c JOIN account a ON a.organization_id=c.organization_id AND a.role='MANAGER' JOIN guardian g ON g.id=c.id;
INSERT INTO training(organization_id,title,starts_at,ends_at) SELECT id,'迁移补充：虚构岗前培训',NOW()-INTERVAL 180 DAY,NOW()-INTERVAL 179 DAY FROM organization;
INSERT INTO training_record(training_id,caregiver_id,result,score,recorded_by,recorded_at) SELECT t.id,c.id,'PASS',90,a.id,t.ends_at FROM training t JOIN caregiver c ON c.organization_id=t.organization_id JOIN account a ON a.organization_id=t.organization_id AND a.role='MANAGER';
INSERT INTO organization_report(organization_id,period,summary,evidence,submitted_by) SELECT o.id,DATE_FORMAT(NOW(),'%Y-%m'),'迁移补充：课程虚构月报','演示服务与回访记录',a.id FROM organization o JOIN account a ON a.organization_id=o.id AND a.role='MANAGER';
INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256) SELECT c.id,a.id,'迁移归档说明','原申报订单、工时与家属确认记录保持不变',SHA2('原申报订单、工时与家属确认记录保持不变',256) FROM claim c JOIN account a ON a.community_id=c.community_id AND a.role='ADMIN';
INSERT INTO event_step(event_id,actor_id,action,message) SELECT e.id,a.id,'MIGRATED','保留原事件及升级记录；原系统未保存的首次时限不倒推填写' FROM emergency e JOIN elder old ON old.id=e.elder_id JOIN account a ON a.community_id=old.community_id AND a.role='ADMIN';
''')
    # Add self-contained fictional rectification examples; do not rewrite previous inspection facts.
    for i in range(1,21):
        reg=(i-1)*6+6;manager=(i-1)*6+4
        statements.append(f"""
INSERT INTO inspection_batch(community_id,inspector_id,target_type,starts_at,ends_at,ratio,population,sample_count,sample_seed) VALUES({i},{reg},'CAREGIVER',NOW()-INTERVAL 30 DAY,NOW(),1,1,1,UUID());
SET @batch=LAST_INSERT_ID();
INSERT INTO inspection(caregiver_id,inspector_id,method,score,issue,rectification,status,result,batch_id) VALUES({i},{reg},'PHONE',85,'课程虚构补充场景：回访材料不完整','补充回访说明','CLOSED','虚构整改样例已复核',@batch);
SET @inspection=LAST_INSERT_ID();
INSERT INTO rectification_submission(inspection_id,submitted_by,measures,evidence,decision,reviewer_id,review_note,reviewed_at) VALUES(@inspection,{manager},'补充回访记录','课程虚构回访记录','ACCEPT',{reg},'虚构样例复核通过',NOW());
INSERT INTO public_notice(inspection_id,publisher_id,title,body) VALUES(@inspection,{reg},'养老服务质量抽查结果','课程虚构抽查；评分 85；整改复核完成。不含个人信息。');
""")
    for view in re.findall(r'CREATE VIEW (\w+)',(ROOT/'database/03_views.sql').read_text()):statements.append('DROP VIEW IF EXISTS '+view+';')
    statements += [(ROOT/'database/02_routines.sql').read_text(),(ROOT/'database/03_views.sql').read_text()]
    output='\n'.join(statements)
    path=ROOT/'database/migrations/20261113_completion.sql';path.write_text(output)
    sql(output,database)
    verified={}
    for table,(query,digest) in originals.items():
        actual=hashlib.sha256(sql(query,database).stdout.encode()).hexdigest()
        assert actual==digest,'Original record changed: '+table
        verified[table]=digest
    after={t:int(sql('SELECT COUNT(*) FROM '+t,database).stdout.splitlines()[-1]) for t in before}
    assert before==after,(before,after)
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'",database).stdout.splitlines()[1:]
    counts={t:int(sql('SELECT COUNT(*) FROM `'+t+'`',database).stdout.splitlines()[-1]) for t in tables}
    assert len(tables)==47 and min(counts.values())>=20,counts
    report={'time':datetime.datetime.now().isoformat(),'database':database,'original_core_counts':before,'after_core_counts':after,'table_counts':counts,'legacy_initial_deadlines':'unknown values remain NULL; not fabricated','backup_required':True,'original_record_hashes_verified':verified}
    (ROOT/'evidence'/('migration-'+database+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Migration verified:',database,'47 tables; original elder/order/claim/payment counts preserved')
if __name__=='__main__':
    database=sys.argv[1] if len(sys.argv)>1 else 'eldercare_migration'
    if database not in {'eldercare_migration','eldercare'}:raise SystemExit('Only the dedicated rehearsal or course database is allowed')
    build(database)
