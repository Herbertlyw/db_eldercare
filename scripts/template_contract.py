from pathlib import Path
from zipfile import ZipFile
import hashlib,json
from docx import Document
R=Path(__file__).resolve().parents[1];base=Path('/Users/herbert/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_zcqy6xqd6p9q22_43fb/msg/file/2026-11/课设')
contracts=[]
for kind,name in [('group','数据库系统课程设计报告模板2026（小组）.docx'),('personal','数据库系统课程设计报告模板2026（个人）.docx')]:
 p=base/name;d=Document(p)
 inventory={}
 with ZipFile(p) as z:
  for info in z.infolist():inventory[info.filename]={'size':info.file_size,'sha256':hashlib.sha256(z.read(info.filename)).hexdigest()}
 (R/'.local'/f'{kind}-template-inventory.json').write_text(json.dumps(inventory,indent=2))
 s=d.sections[0]
 contracts.append(f'''## {kind}
Reference: {p}
SHA256: {hashlib.sha256(p.read_bytes()).hexdigest()}
Sections: {len(d.sections)}. Geometry cm: width {s.page_width.cm:.3f}, height {s.page_height.cm:.3f}, top {s.top_margin.cm:.3f}, bottom {s.bottom_margin.cm:.3f}, left {s.left_margin.cm:.3f}, right {s.right_margin.cm:.3f}.
Reference renders: .local/template-{kind}-font. Fontconfig maps 宋体/SimSun to Songti SC in local render; exported DOCX retains 宋体. Original files unchanged.
Preserve-only: all original package parts except word/document.xml, word/_rels/document.xml.rels, word/settings.xml, [Content_Types].xml. Existing images, styles, numbering, headers, footers, theme and source relationships must remain. New image parts and relationships may be added.
''')
contracts.append('''## Editable slots and content flow
Group: preserve body child 0 (cover drawing), child 1 (cover table). Replace only cover metadata text. Remove remaining instructional body paragraphs and their yellow highlighting; insert the seven teacher-defined chapters in the same order. Use existing Heading 1 and Heading 2 with direct formatting because source defaults include oversized values. Body 小四宋体 12 pt, 1.5 lines; headings black 16/13 pt. SQL uses 10.5 pt Consolas, 1.15 lines to preserve code legibility; this is a documented code-only format adaptation. Add native TOC field with cached text and updateFields. Source headers, footers and page geometry retained. Remove accidental blank page created by consecutive page breaks.
Personal: retain title, original single-table layout, row/column grid and merged cells. Fill metadata cells with known user identity and pending group fields. Replace yellow instruction text with factual common material and clearly pending personal contribution fields; no invented participation. Personal body 五号宋体 10.5 pt, 1.5 lines. Rows may expand and continue across pages; no fixed heights added. No changes to source table boundaries.
Group content can grow across pages. Images fit printable width and height with preserved aspect ratio. No source instructions remain in final body. All template render patterns reviewed; full reports require render and visual QA. Original source parts are restored after high-level editing except authorized body, relationships, new media, settings and content types.
''')
(R/'.local/artifact.md').write_text('\n'.join(contracts))
print('Template contract and package inventories saved')
