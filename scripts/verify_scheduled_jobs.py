"""Exercise the actual 30-second scheduler on an idle disposable MySQL test database.
Mutates fixtures and launches its own application; never accepts the display database.
"""
import datetime
import json
import os
import re
import subprocess
import time
from db import CONFIG, ROOT, sql

database = os.environ.get('TEST_DATABASE', '')
if not re.fullmatch(r'eldercare_[a-z0-9_]+_test', database):
    raise SystemExit('Set TEST_DATABASE to an explicitly disposable test database')
def query(statement):
    return sql(statement, database).stdout.splitlines()[1:]
def scalar(statement):
    return query(statement)[-1]
if scalar(f"SELECT COUNT(*) FROM information_schema.processlist WHERE db='{database}' AND id<>CONNECTION_ID()") != '0':
    raise SystemExit('Test database is currently in use')
# Future reassessment becomes effective while the application is running.
assessment = scalar("INSERT INTO assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until) VALUES(1,'调度验收',20,'SEVERE',1,NOW()+INTERVAL 5 SECOND,NOW()+INTERVAL 15 DAY); SELECT LAST_INSERT_ID()")
active_plans = query("SELECT id FROM v_plan_context WHERE elder_id=1 AND status='ACTIVE'")
assert active_plans, 'fixture needs an active plan before future reassessment'
# A short-lived assessment/plan expires naturally; no manual procedure call or clock patch.
expiring = scalar("INSERT INTO assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until) VALUES(2,'调度到期验收',20,'SEVERE',7,NOW(),NOW()+INTERVAL 20 SECOND); SELECT LAST_INSERT_ID()")
plan = scalar(f"INSERT INTO care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at) SELECT id,(SELECT MIN(id) FROM care_bundle WHERE ability_level='SEVERE'),2,assessed_at,valid_until FROM assessment WHERE id={expiring}; SELECT LAST_INSERT_ID()")
events = []
for level in (1, 2):
    events.append(scalar(f"INSERT INTO emergency(elder_id,source,level,deadline,status,initial_deadline) VALUES(1,'调度验收',{level},NOW()-INTERVAL 1 SECOND,'OPEN',NOW()-INTERVAL 1 SECOND); SELECT LAST_INSERT_ID()"))
query("UPDATE device SET simulation_enabled=0; UPDATE device SET simulation_enabled=1,simulation_metric_id=1,simulation_seconds=30,last_simulated_at=NULL WHERE id=1")
before = int(scalar("SELECT COUNT(*) FROM vital v JOIN device_binding b ON b.id=v.binding_id WHERE b.device_id=1 AND v.source='SIMULATOR'"))
env = {**os.environ, 'DB_URL':f'jdbc:mysql://127.0.0.1:33316/{database}?serverTimezone=Asia/Shanghai', 'DB_USER':'elder_app', 'DB_PASSWORD':CONFIG['app'], 'FIELD_KEY':CONFIG['key']}
log = ROOT/'.local/scheduled-jobs-test.log'
started = time.monotonic()
with log.open('w') as output:
    process = subprocess.Popen(['java','-jar',str(ROOT/'target/eldercare-1.0.0.jar'),'--app.jobs=true','--spring.main.web-application-type=none'],env=env,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
    try:
        for cycle in (1, 2):
            while True:
                if process.poll() is not None:
                    raise RuntimeError('Scheduler process exited; inspect local log')
                samples = int(scalar("SELECT COUNT(*) FROM vital v JOIN device_binding b ON b.id=v.binding_id WHERE b.device_id=1 AND v.source='SIMULATOR'"))-before
                if samples >= cycle:
                    break
                if time.monotonic()-started > 100:
                    raise AssertionError('Scheduled samples did not arrive within 100 seconds')
                time.sleep(1)
            assert all(scalar(f"SELECT status FROM care_plan WHERE id={p}")=='PAUSED' for p in active_plans)
            assert scalar(f"SELECT status FROM care_plan WHERE id={plan}")=='PAUSED'
            assert scalar(f"SELECT COUNT(*) FROM notification WHERE assessment_id={assessment} AND account_id=1")=='1'
            for event, level in zip(events, (2, 3)):
                assert scalar(f"SELECT level FROM emergency WHERE id={event}")==str(level)
                assert scalar(f"SELECT COUNT(*) FROM emergency_log WHERE event_id={event} AND level={level}")=='1'
                assert scalar(f"SELECT COUNT(*) FROM notification WHERE event_id={event}")!='0'
            print(f'Automatic scheduler cycle {cycle} verified', flush=True)
        report = {'time':datetime.datetime.now().isoformat(),'database':database,'actual_scheduler':True,'manual_procedure_calls':False,'cycles':2,'assessment_reminder_deduplicated':True,'expired_assessment_plan_paused':True,'superseded_plan_paused':True,'levels':[2,3],'simulation_samples':samples,'elapsed_seconds':round(time.monotonic()-started,2)}
        (ROOT/'evidence/scheduled-jobs.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait()
if 'ERROR' in log.read_text():
    raise AssertionError('Scheduler log contains errors')
