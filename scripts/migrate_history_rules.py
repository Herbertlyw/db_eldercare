"""Install the 2026-11-13 history rules without changing business rows.

Stop the course app and take a private full backup first. Rehearse on a clone.
The generated SQL is repeatable; it replaces only the affected stored objects.
"""
import datetime
import hashlib
import json
import re
import sys
from db import ROOT, sql

OBJECTS = {
    'assert_plan', 'expire_plans', 'assert_order', 'escalate_events',
    'binding_before_insert', 'dispatch_accept_check', 'assessment_before_insert',
    'assessment_pause_old_plans', 'elder_transfer_binding_check',
    'immutable_emergency_log', 'keep_emergency_log',
}

def migration():
    source = (ROOT / 'database/02_routines.sql').read_text()
    statements = []
    for match in re.finditer(r'CREATE (PROCEDURE|TRIGGER) (\w+)\b.*?\$\$', source, re.S):
        kind, name = match.group(1, 2)
        if name in OBJECTS:
            statements.append(f'DROP {kind} IF EXISTS `{name}`$$\n' + match.group())
    assert len(statements) == len(OBJECTS)
    return '-- Back up the database and stop all application writers before running.\nDELIMITER $$\n' + '\n'.join(statements) + '\nDELIMITER ;\n'

def snapshot(database):
    tables = sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name", database).stdout.splitlines()[1:]
    return {table: {'rows': int(sql(f'SELECT COUNT(*) FROM `{table}`', database).stdout.splitlines()[-1]),
                    'sha256': hashlib.sha256(sql(f'SELECT * FROM `{table}` ORDER BY id', database).stdout.encode()).hexdigest()}
            for table in tables}

def apply(database):
    if database not in {'eldercare', 'eldercare_history_rehearsal'}:
        raise ValueError('Only the course DB and its dedicated rehearsal clone are allowed')
    before = snapshot(database)
    assert len(before) == 47, 'Expected the completed 47-table database'
    script = migration()
    (ROOT / 'database/migrations/20261113_history_rules.sql').write_text(script)
    sql(script, database)
    assert snapshot(database) == before, 'Business rows changed during DDL migration'
    report = {'time': datetime.datetime.now().isoformat(), 'database': database,
              'business_rows_unchanged': True, 'tables': before,
              'objects_replaced': sorted(OBJECTS), 'sql_sha256': hashlib.sha256(script.encode()).hexdigest()}
    (ROOT / f'evidence/history-migration-{database}.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'{database}: 47 tables unchanged; {len(OBJECTS)} stored objects installed')

if __name__ == '__main__':
    apply(sys.argv[1] if len(sys.argv) > 1 else 'eldercare_history_rehearsal')
