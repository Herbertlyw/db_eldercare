#!/usr/bin/env python3
"""Local Docker lifecycle helper. Secrets stay in .local and are never printed."""
import json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CONFIG=json.loads((ROOT/'.local/config.json').read_text())
def sql(text,database='eldercare',capture=True):
 r=subprocess.run([CONFIG['mysql_bin'],'--no-defaults','--socket='+CONFIG['socket'],'-uroot','--default-character-set=utf8mb4','--batch',database],input=text,text=True,capture_output=capture,env={**__import__('os').environ,'MYSQL_PWD':CONFIG['root']})
 if r.returncode: raise RuntimeError(r.stderr)
 return r
if __name__=='__main__':
 for name in sys.argv[1:]:
  result=sql((ROOT/name).read_text());print(result.stdout)
