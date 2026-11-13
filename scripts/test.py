"""Build and verify against an explicitly disposable local MySQL database."""
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

from db import CONFIG, ROOT, sql

# Separate worktrees can use different test databases. Never accept the display database.
database = os.environ.get('TEST_DATABASE', 'eldercare_test')
if not re.fullmatch(r'eldercare_(?:[a-z0-9_]+_)?test', database):
    raise SystemExit('TEST_DATABASE must be eldercare_test or eldercare_<name>_test')

# Clean removes classes left behind by package moves before seeding the current JAR.
subprocess.run(['mvn', '-q', '-DskipTests', 'clean', 'package'], cwd=ROOT, check=True)
sql(f'DROP DATABASE IF EXISTS `{database}`; '
    f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4; '
    f"GRANT SELECT,INSERT,UPDATE,DELETE,EXECUTE ON `{database}`.* TO 'elder_app'@'localhost';")
for filename in ['01_schema.sql', '02_routines.sql', '03_views.sql']:
    sql((ROOT / 'database' / filename).read_text(), database)
env = {
    **os.environ,
    'DB_URL': f'jdbc:mysql://127.0.0.1:33316/{database}?serverTimezone=Asia/Shanghai',
    'DB_USER': 'elder_app',
    'DB_PASSWORD': CONFIG['app'],
    'FIELD_KEY': CONFIG['key'],
    'DEMO_PASSWORD': CONFIG['demo'],
}
with (ROOT / '.local/test-seed.log').open('w') as out:
    seeded = subprocess.run(
        ['java', '-jar', 'target/eldercare-1.0.0.jar', '--app.seed=true', '--app.jobs=false',
         '--spring.main.web-application-type=none'],
        cwd=ROOT, env=env, stdout=out, stderr=subprocess.STDOUT)
if seeded.returncode:
    raise SystemExit('test seed failed; inspect .local/test-seed.log')

with (ROOT / 'evidence/maven-tests.log').open('w') as out:
    tested = subprocess.run(['mvn', '-q', 'test'], cwd=ROOT, env=env,
                            stdout=out, stderr=subprocess.STDOUT)
suites = []
for report in sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml')):
    suite = ET.parse(report).getroot()
    suites.append({
        'suite': suite.get('name'),
        'kind': 'integration' if 'IntegrationTest' in suite.get('name') else 'unit',
        **{key: int(suite.get(key)) for key in ['tests', 'failures', 'errors', 'skipped']},
        'cases': [case.get('name') for case in suite.findall('testcase')],
    })
    shutil.copy2(report, ROOT / 'evidence' / report.name)
    text_report = report.with_name(report.name.removeprefix('TEST-')).with_suffix('.txt')
    if text_report.exists():
        shutil.copy2(text_report, ROOT / 'evidence' / text_report.name)
report = {
    'time': datetime.datetime.now().isoformat(),
    'database': database,
    'engine': 'native MySQL 8.4.11',
    'suites': suites,
    **{key: sum(s[key] for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']},
}
(ROOT / 'evidence/test-summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
print('Tests exit status:', tested.returncode, '; tests:', report['tests'])
raise SystemExit(tested.returncode)
