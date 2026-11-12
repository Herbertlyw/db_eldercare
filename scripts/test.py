import json,os,subprocess
from pathlib import Path
from db import sql,CONFIG,ROOT
# Dedicated test database, reset only this explicitly named disposable database.
sql('DROP DATABASE IF EXISTS eldercare_test; CREATE DATABASE eldercare_test CHARACTER SET utf8mb4; GRANT SELECT,INSERT,UPDATE,DELETE,EXECUTE ON eldercare_test.* TO \'elder_app\'@\'localhost\';')
for f in ['01_schema.sql','02_routines.sql','03_views.sql']:sql((ROOT/'database'/f).read_text(),'eldercare_test')
env={**os.environ,'DB_URL':'jdbc:mysql://127.0.0.1:33316/eldercare_test?serverTimezone=Asia/Shanghai','DB_PASSWORD':CONFIG['app'],'FIELD_KEY':CONFIG['key'],'DEMO_PASSWORD':CONFIG['demo']}
with (ROOT/'.local/test-seed.log').open('w') as out:
 r=subprocess.run(['java','-jar','target/eldercare-1.0.0.jar','--app.seed=true','--app.jobs=false','--spring.main.web-application-type=none'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
if r.returncode:raise SystemExit('test seed failed; inspect .local/test-seed.log')
with (ROOT/'evidence/maven-tests.log').open('w') as out:r=subprocess.run(['mvn','-q','test'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
print('Tests exit status:',r.returncode)
raise SystemExit(r.returncode)
