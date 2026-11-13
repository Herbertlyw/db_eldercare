from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree
from docx import Document
from docx.shared import Pt,Inches,RGBColor,Cm
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from PIL import Image
import json,csv,re,hashlib,textwrap
R=Path(__file__).resolve().parents[1];base=Path('/Users/herbert/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_zcqy6xqd6p9q22_43fb/msg/file/2026-11/课设')
meta=json.loads((R/'docs/diagrams/diagram-index.json').read_text());queries=json.loads((R/'database/queries.json').read_text());counts=json.loads((R/'evidence/row-counts.json').read_text());cols=[{k.lower():v for k,v in x.items()} for x in csv.DictReader((R/'evidence/schema-columns.tsv').open(),delimiter='\t')];cols=[c for c in cols if not c['table_name'].startswith('v_')]
fks=[{k.lower():v for k,v in x.items()} for x in csv.DictReader((R/'evidence/foreign-keys.tsv').open(),delimiter='\t')]
fk_fields={(f['table_name'],f['column_name']) for f in fks}
# Source originals remain byte-identical; restore untouched package parts after editing.
def preserve(source,target):
 with ZipFile(source) as z:original={n:z.read(n) for n in z.namelist()}
 with ZipFile(target) as z:edited={n:z.read(n) for n in z.namelist()}
 allowed={'word/document.xml','word/_rels/document.xml.rels','word/settings.xml','[Content_Types].xml'}
 for n,b in original.items():
  if n not in allowed:edited[n]=b
 with ZipFile(target,'w',ZIP_DEFLATED) as z:
  for n,b in edited.items():z.writestr(n,b)
 with ZipFile(target) as z:assert all(z.read(n)==b for n,b in original.items() if n not in allowed)
def font(run,size=12,bold=False,code=False):
 run.font.name='Consolas' if code else '宋体';run.font.size=Pt(size);run.font.bold=bold;run.font.color.rgb=RGBColor(0,0,0)
 run._element.get_or_add_rPr().get_or_add_rFonts().set(qn('w:eastAsia'),'宋体')
BLUE='1565C0'
def add_text(p,text,size=12,bold=False,code=False):
 for part in re.split(r'(\[\[BLUE\]\].*?\[\[/BLUE\]\])',text,flags=re.S):
  if not part:continue
  pending=part.startswith('[[BLUE]]');part=part.removeprefix('[[BLUE]]').removesuffix('[[/BLUE]]') if pending else part
  r=p.add_run(part);font(r,size,bold,code)
  if pending:r.font.color.rgb=RGBColor.from_string(BLUE)
def para(d,text='',size=12,bold=False,code=False):
 p=d.add_paragraph();p.paragraph_format.line_spacing=1.15 if code else 1.5;p.paragraph_format.space_after=Pt(6);p.paragraph_format.keep_with_next=False;add_text(p,text,size,bold,code);return p
def heading(d,text,level=1,newpage=False):
 p=d.add_paragraph(style='Heading '+str(level));p.paragraph_format.page_break_before=newpage;p.paragraph_format.keep_with_next=True;p.paragraph_format.space_before=Pt(12);p.paragraph_format.space_after=Pt(8);font(p.add_run(text),16 if level==1 else 13,True);return p
figure_number=0
def picture(d,name,caption,maxh=5.8):
 global figure_number
 figure_number+=1
 file=R/name;im=Image.open(file);w,h=im.size;scale=min(6.45/w,maxh/h)
 p=d.add_paragraph();p.paragraph_format.keep_with_next=True;p.paragraph_format.space_before=Pt(4);p.paragraph_format.space_after=Pt(3);p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.add_run().add_picture(str(file),width=Inches(w*scale),height=Inches(h*scale));p=para(d,f'图 {figure_number}  '+caption,10.5);p.paragraph_format.space_after=Pt(9);p.alignment=WD_ALIGN_PARAGRAPH.CENTER

def table(d,headers,rows):
 t=d.add_table(rows=1,cols=len(headers));t.autofit=True
 if headers==['字段','类型及范围','默认值','约束','含义']:
  t.autofit=False
  for col,width in zip(t.columns,[1.22,1.48,0.67,1.34,1.74]):col.width=Inches(width)
 for i,h in enumerate(headers):t.rows[0].cells[i].text=str(h)
 trpr=t.rows[0]._tr.get_or_add_trPr();repeat=OxmlElement('w:tblHeader');trpr.append(repeat)
 for row in rows:
  cells=t.add_row().cells
  for i,v in enumerate(row):cells[i].text=str(v)
 for j,row in enumerate(t.rows):
  if headers==['字段','类型及范围','默认值','约束','含义']:
   for cell,width in zip(row.cells,[1.22,1.48,0.67,1.34,1.74]):cell.width=Inches(width)
  trpr=row._tr.get_or_add_trPr();nosplit=OxmlElement('w:cantSplit');trpr.append(nosplit)
  for cell in row.cells:
   tc=cell._tc.get_or_add_tcPr();b=OxmlElement('w:tcBorders')
   for edge in ['top','left','bottom','right']:
    e=OxmlElement('w:'+edge);e.set(qn('w:val'),'single');e.set(qn('w:sz'),'4');e.set(qn('w:color'),'D9D9D9');b.append(e)
   tc.append(b)
   if j==0:sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'E9EEF1');tc.append(sh)
   margins=OxmlElement('w:tcMar')
   for edge in ['top','left','bottom','right']:
    m=OxmlElement('w:'+edge);m.set(qn('w:w'),'90');m.set(qn('w:type'),'dxa');margins.append(m)
   tc.append(margins)
   for p in cell.paragraphs:
    p.paragraph_format.line_spacing=1.15;p.paragraph_format.space_after=Pt(3)
    text=p.text;p.clear();add_text(p,text,10.5,j==0)
 return t

def code(d,text):
 # Keep SQL semantics unchanged, insert line breaks at top-level commas for readable DDL.
 if text.startswith('CREATE TABLE'):
  out=[];depth=0;quote=False
  for ch in text:
   if ch=="'":quote=not quote
   if not quote:
    if ch=='(':depth+=1
    if ch==')':depth-=1
   out.append(ch)
   if ch==',' and depth==1 and not quote:out.append('\n  ')
  text=''.join(out)
 for line in text.splitlines():
  if line.strip():para(d,line,10.5,code=True)

def mdprose(d,file):
 pending=[]
 def flush_table():
  if pending:
   parsed=[[cell.strip() for cell in line.strip('|').split('|')] for line in pending]
   table(d,parsed[0],[row for row in parsed[1:] if not all(re.fullmatch(r'[: -]+',cell) for cell in row)])
   pending.clear()
 for line in (R/file).read_text().splitlines():
  if line.startswith('|'):
   pending.append(line);continue
  flush_table()
  if not line.strip() or line.startswith('# '):continue
  if line.startswith('## '):heading(d,line[3:],2)
  elif line.startswith('```'):continue
  else:para(d,'[[BLUE]]'+line+'[[/BLUE]]' if line.startswith('【待确认】') or line.startswith('【待补充】') else line)
 flush_table()

source=base/'数据库系统课程设计报告模板2026（小组）.docx';d=Document(source)
body=d._element.body
for child in list(body)[2:]:
 if child.tag!=qn('w:sectPr'):body.remove(child)
for t in d._element.xpath('.//w:t'):
 if t.text:
  t.text=t.text.replace('小组名称：','小组名称：待填写真实小组名称').replace('小组成员：张三（组长）、李四','小组成员：待提供真实名单').replace('****年****月***日','2026年11月13日')
for p in d._element.xpath('.//w:p'):
 if any(k in ''.join(p.xpath('.//w:t/text()')) for k in ['小组名称：','小组成员：']):
  for r in p.xpath('./w:r'):
   props=r.find(qn('w:rPr'))
   if props is None:props=OxmlElement('w:rPr');r.insert(0,props)
   color=props.find(qn('w:color'))
   if color is None:color=OxmlElement('w:color');props.append(color)
   color.set(qn('w:val'),BLUE)
heading(d,'目录',1,True)
p=d.add_paragraph();r=p.add_run();beg=OxmlElement('w:fldChar');beg.set(qn('w:fldCharType'),'begin');r._r.append(beg);ins=OxmlElement('w:instrText');ins.set(qn('xml:space'),'preserve');ins.text=' TOC \\o "1-1" \\h \\z \\u ';r._r.append(ins);sep=OxmlElement('w:fldChar');sep.set(qn('w:fldCharType'),'separate');r._r.append(sep)
chapters=['一、成员分工','二、需求分析','三、系统概念结构设计','四、系统的逻辑结构设计','五、系统物理结构设计、持久层设计和关键业务代码','六、项目运维管理和优化','七、附件内容']
toc_pages=json.loads((R/'.local/report-toc.json').read_text()) if (R/'.local/report-toc.json').exists() else [3,4,11,17,22,46,58]
for x,page in zip(chapters,toc_pages):
 p=para(d,x+'\t'+str(page));p.paragraph_format.tab_stops.add_tab_stop(Inches(6.4),WD_TAB_ALIGNMENT.RIGHT,WD_TAB_LEADER.DOTS)
r=d.add_paragraph().add_run();end=OxmlElement('w:fldChar');end.set(qn('w:fldCharType'),'end');r._r.append(end)
update=OxmlElement('w:updateFields');update.set(qn('w:val'),'true');d.settings.element.append(update)
heading(d,chapters[0],1,True)
para(d,'本项目的网站名称为“左邻右舍”，使用 Java 和 MySQL 实现社区居家养老服务管理，按档案评估、服务派单、护工排班、健康监测和补贴监管五个模块组织。')
para(d,'[[BLUE]]【待确认】下面为分工建议。请填写组长、成员姓名学号，并按实际完成的工作调整。全文蓝色文字为待确认或待补充内容。[[/BLUE]]',10.5)
table(d,['业务模块','建议负责内容','负责人'],[('[[BLUE]]'+v[0]+'[[/BLUE]]', '[[BLUE]]用例与鲁棒图、局部数据模型、SQL、视图、过程、触发器和测试[[/BLUE]]','[[BLUE]]待填写[[/BLUE]]') for v in meta['groups'].values()])
para(d,'[[BLUE]]【待补充】小组共 4—5 人，成员名单、各人完成情况和协作记录待填写。[[/BLUE]]')
heading(d,chapters[1],1,True)
para(d,'老人完成建档和能力评估后，社区为其制定照护计划。家属依据计划预约服务，管理员安排护工上门。服务结束后，系统保存服务记录、工时和家属确认结果，作为后续补贴核算的依据。设备监测和应急处置也关联到老人档案。设计中需要重点处理评估过期、重复派单、服务时间冲突、设备换绑和补贴重复申报等问题。')
para(d,'参与者包括社区管理员、老人及家属、护工、服务机构负责人、街道与民政监管。街道初审和民政终审在账号角色上细分。家属只访问关联老人，护工只访问本人订单，机构负责人只管理本机构护工，监管人员不能任意修改原始服务事实。')
for key,(title,tables) in meta['groups'].items():
 heading(d,title,2,True)
 para(d,{'A':'管理员建立唯一档案和家属关联，登记有效评估，再选择匹配能力等级的照护包并制定计划。每份计划引用采用的评估，系统检查有效期覆盖与区间重叠。调整计划建立新版本并保留旧频次。登记当前复评后暂停旧计划，后续服务采用最新已生效评估；复评通知只针对最新有效评估生成；家属可查看授权联系方式和计划项目。','B':'家属或社区创建预约。管理员按资质、区域、排班和整改状态派单。护工接单、签到、填写记录和签退后进入待确认，家属确认后订单才完成。改派记录保留，失败时恢复原分配。普通评价在服务完成后提交；投诉则经过社区受理、机构或社区回复和家属或社区结案。各步均保存办理人和说明。','C':'机构负责人登记招聘申请，录用后建立护工账号，安排岗前培训并登记成绩，再维护项目证书、服务区域与班次。护工服务前后打卡。容量不得超上限，班次和实际预约不得重叠。服务端计算签到签退的围栏距离，围栏、时段或服务时长异常进入人工复核，未经认定的工时不参与补贴。','D':'设备绑定历史保存老人归属和时间区间。跨社区迁移前先解绑原设备，设备本身仍归原社区管理。体征上传先校验采集时绑定，再按课程演示阈值标记异常并建立待确认事件。设备定时模拟采样由管理员明确开启。人员确认异常、响应、登记到场后才能记录处置并关闭；误报单独留存核实原因。未及时响应时由定时任务升级并产生通知。二级及以上事件通知本社区监管人员；其他辖区监管员可按授权查询，不自动订阅全部通知。','E':'社区按政策的月、季或年周期归集可补贴订单。申报事务逐笔检查确认、有效工时、政策和重复资格，任一不合格整体回滚。街道和民政分级审核，再登记模拟拨付；监管按比例建立抽查批次，机构提交整改和考核材料，监管复核通过后发布脱敏公示。未完成整改限制接单。'}[key])
 picture(d,Path('docs/diagrams')/(key+'-usecase.png'),title+'用例图',3.45)
 picture(d,Path('docs/diagrams')/(key+'-robustness.png'),title+'鲁棒图',3.0)
heading(d,'投诉办理与整改复核',2,True)
picture(d,'docs/diagrams/B-complaint-flow.png','投诉受理、回复和结案',2.5)
picture(d,'docs/diagrams/E-quality-flow.png','比例抽查、整改复核与公示',2.5)
para(d,'投诉和整改采用独立的办理记录表。经办机构或社区回复后由家属或社区确认结案；整改材料被退回后重新提交，旧材料与审核意见仍可查看。公示从已完成抽查中生成，只保留方式、评分和处置状态。')
heading(d,'非功能性需求',2)
para(d,'系统除了支持正常业务，还要拒绝不符合条件的操作。主外键和唯一约束防止无效关联、重复记录；跨表校验检查计划、资质和订单状态。多人同时操作时，同一老人不能产生重叠计划，同一班次也不能超额接单。查询结果按账号的数据范围过滤，改派、换绑和审核记录保留历史。数据库恢复采用全量备份与 binlog，并在独立数据库中验证。')
heading(d,chapters[2],1,True)
para(d,'全局模型包含 47 个实体。下图按主要用途归类，方便定位各表；后续局部 UML 图再展开主外键联系。完整 47 实体全局 UML 另附矢量 PDF 和 SVG，可放大查看。局部图展示主键与外键，完整属性和约束见数据字典。PK 表示主键，FK 表示外键；↗ 表示目标实体位于另一张局部图。')
picture(d,'docs/diagrams/model-overview.png','全局数据模型分区及表清单',7.0)
heading(d,'公共基础模型',2,True)
picture(d,'docs/diagrams/foundation-uml.png','社区、机构、账号及审计实体',5.5)
para(d,'社区、服务机构和账号为业务模块提供归属依据。审计表以表名和记录编号保存变更对象，因此不对各业务表建立直接外键。图中 1 表示必须对应一条父记录，0..1 表示可选且最多一条，0..* 表示可以没有或有多条。')
for key,(title,_) in meta['groups'].items():
 heading(d,title+'局部模型',2,True);picture(d,Path('docs/diagrams')/(key+'-uml.png'),title+'局部 UML 类图',7.5)
 para(d,{'A':'一位老人可以有多次能力评估，每份照护计划引用所采用的评估。家属通过关联表与老人建立联系。','B':'一张订单可以有多条派单记录，改派时保留旧记录。服务记录和签到签退记录引用具体派单。','C':'护工通过资质表关联可提供的服务项目，通过排班表保存工作时段。班次记录容量和已占用名额。','D':'设备与老人通过绑定记录建立联系。体征引用采集时的绑定编号，换绑不会改变旧数据的老人归属。','E':'补贴申报包含订单明细，审核记录和拨付记录分别保存。明细保留核算时的政策、单价和有效工时。'}[key])
 if key=='A':
  heading(d,'服务包与计划明细',2,True);picture(d,'docs/diagrams/A-bundle-uml.png','服务包、项目与计划明细的关系',7.2)
  para(d,'服务包通过 bundle_item 关联多个服务项目；计划通过 plan_item 保存本次采用的项目与频次。计划引用服务包，同时引用制定时采用的能力评估。')
 if key=='E':
  heading(d,'申报明细与核算依据',2,True);picture(d,'docs/diagrams/E-detail-uml.png','订单、政策与补贴明细的关系',7.2)
  para(d,'每条申报明细关联一个订单和一项政策，保存核算单价及有效工时。质量抽查引用订单与护工，抽查结论会影响后续接单资格。')
for title,file,explanation in [
 ('评价投诉记录','B-feedback-uml','评价与投诉引用服务订单，办理日志引用反馈记录并保存经办账号。'),
 ('招聘与岗前培训','C-training-uml','招聘归属于机构；培训与护工通过参训记录形成多对多联系，成绩只在培训结束后登记。'),
 ('政策适用范围','E-policy-uml','同一政策可覆盖多个授权社区。适用条件还包括能力等级、年龄和标签，结算周期分别为月、季或年。'),
 ('抽查与机构材料','E-quality-uml','批次保存抽样范围及数量；每条检查明确关联订单或护工，整改提交和公示引用检查记录。机构考核材料单独保留每次提交。'),
 ('申报证明与审核','E-evidence-uml','证明材料保存文本及内容摘要。驳回后在原申报补件重报，保留明细与历次审核。')]:
 heading(d,title,2,True);picture(d,'docs/diagrams/'+file+'.png',title+'局部 UML 类图',7.2);para(d,explanation)
heading(d,chapters[3],1,True)
mdprose(d,'docs/逻辑设计与范式分析.md')
heading(d,'关系模式清单',2)
for t,label in meta['tables'].items():
 cs=[c for c in cols if c['table_name']==t];para(d,label+'：'+t+'('+', '.join(c['column_name']+(' [PK]' if c['column_key']=='PRI' else ' [FK]' if (t,c['column_name']) in fk_fields else '') for c in cs)+')。',10.5)
para(d,'所有外键及其目标列单列在 evidence/foreign-keys.tsv 与数据字典中；模式中的生成列仅服务于物理唯一性约束。')
heading(d,chapters[4],1,True)
heading(d,'运行环境与分层',2)
para(d,'程序在 macOS arm64 环境运行，使用 Java 17.0.15、Spring Boot 3.4.4、Spring JDBC 和 MySQL 8.4.11。浏览器提交请求后，由接口层交给业务方法处理，再通过 DAO 访问数据库。Windows 版本提供 JAR、数据库初始化脚本和启动脚本，不依赖 Docker。\n[[BLUE]]【待验证】在 Windows 目标电脑上完成初始化、启动和主要业务操作检查。[[/BLUE]]')
picture(d,'evidence/ui-workspace-completion.png','实际浏览器登录后的社区工作台',4.3)
heading(d,'DAO 基础实现',2)
code(d,(R/'src/main/java/edu/bjfu/eldercare/Store.java').read_text())
para(d,'SQL 的用户值全部参数绑定。插入方法返回生成主键，查询和更新统一经过 JdbcTemplate。分页入口校验页码与大小并使用固定数据类别白名单。体征批量上传在同一事务中逐条校验和写入，任一条越权或无效整体回滚。业务方法使用 @Transactional，过程不提前提交。')
heading(d,'派单与改派的事务设计',2)
picture(d,'docs/diagrams/dispatch-transaction.png','派单事务及失败回滚',3.3)
para(d,'派单过程先锁定订单和护工记录，再检查项目资质、服务区域及预约时段。改派时释放旧名额并保留旧派单，随后通过条件更新检查班次容量并写入新派单。各步在同一事务中完成，后续检查失败时恢复原有分配，避免派单与名额不一致。')
heading(d,'完整建表与索引语句',2)
for line in (R/'database/01_schema.sql').read_text().splitlines():
 if line.startswith('CREATE TABLE'):
  t=line.split('(')[0].split()[-1];heading(d,meta['tables'].get(t,t),2);code(d,line)
 elif line.startswith(('ALTER TABLE','CREATE INDEX')):code(d,line)
heading(d,'业务视图与内部关联视图',2)
para(d,'五条业务线各提供三个不同用途的视图，共 15 个业务视图。另有 v_plan_context 和 v_dispatch_context 两个内部视图，派生老人、护工归属，避免基础表重复保存。视图提供查询结构，具体人员范围仍由业务授权过滤决定。')
for line in (R/'database/03_views.sql').read_text().splitlines():code(d,line)
for line in (R/'database/01_schema.sql').read_text().splitlines():
 if line.startswith('CREATE VIEW'):code(d,line)
heading(d,'业务查询及两种实现方式',2)
para(d,'以下 15 组查询均已在同一测试数据集上执行，两种实现的结果逐行比较一致。各组的查询结果和 EXPLAIN 输出保存在 evidence/query-comparison.json。')
for q in queries:
 heading(d,q['id']+' '+q['title'],2);para(d,q['comparison']);para(d,'实现 A',10.5,True);code(d,q['a']+';');para(d,'实现 B',10.5,True);code(d,q['b']+';')
heading(d,chapters[5],1,True)
mdprose(d,'docs/运行维护与验收.md')
heading(d,'存储过程与触发器代码',2)
para(d,'以下核心例程按业务覆盖有效期、派单、工时、应急与补贴，全部审计和历史保护触发器在源代码附件中提供。')
# Include full executable routines so each business's process and trigger are reviewable.
text=(R/'database/02_routines.sql').read_text().split('CREATE TRIGGER audit_')[0]
code(d,text)
heading(d,'测试与结果',2)
test_result=json.loads((R/'evidence/test-summary.json').read_text())
para(d,f"真实 MySQL 集成测试共 {test_result['tests']} 项，失败 {test_result['failures']} 项、错误 {test_result['errors']} 项、跳过 {test_result['skipped']} 项。")
for suite in test_result['suites']:para(d,suite['suite'].split('.')[-1]+'：'+str(suite['tests'])+' 项。',10.5)
para(d,'测试连接独立的真实 MySQL 测试库。覆盖全部表最低记录数、15 组查询等价、视图执行、外键与唯一性、有效期、重叠区间、派单容量、确认凭据、工时复核、设备历史、异常事件、补贴回滚、身份隔离、登录锁定、会话退出及并发。补充测试覆盖招聘录用和拒绝、培训成绩边界、投诉完整办理、比例抽查、整改退回重提、机构月报、公示隐私、跨社区权限、定时模拟、坐标校验、三种补贴周期和补件重报。完整方法名和原始结果保存在 Surefire XML 与测试源代码中。')
table(d,['验证对象','实际验证内容'],[['并发计划','两个事务同时创建同老人重叠计划，只成功一个'],['并发派单','两个订单竞争最后一个班次名额，只成功一个'],['失败改派','新班次不覆盖预约时刻时，旧派单和名额被回滚恢复'],['批量写入','一条合法、一条越权的数据批次整体回滚'],['完整服务','接单、签到、异常工时复核、签退、家属确认走通'],['历史保护','终态派单和应急升级日志不能改写或删除'],['复评与迁移','旧计划暂停后按最新评估修订；设备先解绑再迁移'],['备份恢复','全量加实际 binlog 增量在独立库恢复，47 表逐行内容一致']])
browser=json.loads((R/'evidence/completion-browser.json').read_text())
para(d,f"浏览器验证覆盖六种角色的 {sum(x['readRoutes'] for x in browser['checks'])} 个角色查询入口，以及 {len(browser['actionsDone'])} 次实际表单操作。投诉、培训、考核、整改、公示和模拟采样均完成提交；详情、免登录公示和手机宽度布局一并检查。")
picture(d,'evidence/ui-complaint-completion.png','浏览器中的投诉办理记录',4.5)
picture(d,'evidence/ui-regulation-completion.png','民政监管按授权辖区查看统计',4.5)
heading(d,chapters[6],1,True)
table(d,['附件编号','内容与位置'],[['01','docs/diagrams：全局与局部 UML、用例图、鲁棒图，含 DOT、SVG、PNG'],['02','database：全部 DDL、视图、过程、触发器、15 组双实现查询'],['03','src 与 pom.xml：Java 源码、静态界面和测试源码'],['04','docs/数据字典与关系模式.md：所有字段的名称、类型、默认值、关联与含义'],['05','evidence：测试日志、查询执行计划、行数、恢复演练、浏览器截图'],['06','eldercare.jar 与 Windows 初始化、启动脚本'],['07','答辩演示文稿与 5 分钟录制讲稿；[[BLUE]]【待补充】全员发言视频[[/BLUE]]'],['08','个人报告预填稿；[[BLUE]]【待确认】个人分工和完成情况[[/BLUE]]'],['09','[[BLUE]]【待补充】答辩后的教师提问、修改内容及验证结果[[/BLUE]]']])
para(d,'[[BLUE]]【待补充】成员与分工确认后更新个人报告，补入协作记录、答辩录像和教师反馈。[[/BLUE]]')
heading(d,'完整字段字典',2,True)
para(d,'以下字段依据实际 MySQL 结构导出。类型中的枚举值给出允许的状态范围，空值、默认值、主外键及检查条件分别列出；含义不明的字段不以英文名代替说明。')
meanings=json.loads((R/'evidence/field-meanings.json').read_text())
checks=[{k.lower():v for k,v in x.items()} for x in csv.DictReader((R/'evidence/check-constraints.tsv').open(),delimiter='\t')]
for name,label in meta['tables'].items():
 heading(d,label+' '+name,2)
 rows=[]
 for c in [c for c in cols if c['table_name']==name]:
  fk=next((f for f in fks if f['table_name']==name and f['column_name']==c['column_name']),None)
  constraint=('PK；' if c['column_key']=='PRI' else '')+('唯一；' if c['column_key']=='UNI' else '')+('可空' if c['is_nullable']=='YES' else '非空')+('；FK '+fk['referenced_table_name']+'.'+fk['referenced_column_name'] if fk else '')
  default='自动编号' if 'auto_increment' in c['extra'] else '无默认值' if c['column_default']=='NULL' and c['is_nullable']=='NO' else '当前时间' if c['column_default'].startswith('CURRENT_TIMESTAMP') else c['column_default']
  rows.append([c['column_name'],c['column_type'],default,constraint,meanings[c['column_name']]])
 table(d,['字段','类型及范围','默认值','约束','含义'],rows)
 for check in [x for x in checks if x['table_name']==name]:para(d,'检查条件：'+check['check_clause'],9.5,code=True)
heading(d,'参考资料',2,True)
for text in ['课程设计任务书 2026，第 1—9 页。','教师任务讲解逐字稿，00:01:58—00:34:23。','数据库系统课程设计报告模板 2026 小组与个人版。','数据库系统课程设计评分标准 2026 版。','MySQL 官方文档：CREATE TRIGGER、CREATE PROCEDURE、SIGNAL，https://dev.mysql.com/doc/refman/8.0/en/create-trigger.html 。','Spring 官方 JDBC 文档：https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html 。']:
 p=para(d,text,10.5);p.paragraph_format.line_spacing=1.15;p.paragraph_format.space_after=Pt(2)
out=R/'outputs/社区居家养老平台小组报告_待补成员信息.docx';d.save(out);preserve(source,out)
# Personal template: only known identity and factual shared technical material.
source=base/'数据库系统课程设计报告模板2026（个人）.docx';d=Document(source);t=d.tables[0]
# Preserve fields and merged cells; remove blank form's oversized minimum heights.
for height in t._tbl.xpath('.//w:trHeight'):height.getparent().remove(height)
for row,col,value in [(0,2,'刘禹伟'),(0,6,'计算机243'),(1,2,'241002808'),(1,6,'张晓宇'),(2,2,'待确认')]:t.cell(row,col).text=value
for row in [5,6,7]:t.cell(row,0).text='待提供真实成员';t.cell(row,3).text='待提供';t.cell(row,5).text='待提供'
texts={
9:'本课程设计实现一个社区居家养老服务管理系统。管理员登记老人档案和能力评估结果，制定照护计划；家属预约服务后，由管理员安排护工。系统记录护工签到、签退及家属确认结果，并据此计算有效工时和补贴。健康监测部分保存设备绑定、体征采样和应急处置记录。程序使用 Java 和 MySQL，通过 JDBC 访问数据库。测试采用虚构数据，检查正常业务、错误输入、权限范围、并发操作和备份恢复。',
10:'[[BLUE]]【待补充】实际负责的模块、数据库设计任务、编写的 SQL 和测试情况，按模板要求写 150—200 字。[[/BLUE]]',
11:'[[BLUE]]【待补充】与哪些成员协作、共同解决了什么问题，以及对应的讨论或修改记录。[[/BLUE]]',
13:'[[BLUE]]【待补充】根据个人负责模块，从 docs/diagrams 选用对应的用例图和鲁棒图，说明参与者、操作步骤及涉及的数据。[[/BLUE]]',
14:'系统按角色和数据范围控制访问。家属查询关联老人，护工查询本人订单，机构负责人管理本机构人员。输入值通过参数绑定传入 SQL。数据库使用主外键、唯一约束和时间校验保持记录一致，计划和派单操作还需要处理并发冲突。\n[[BLUE]]【待补充】个人负责模块的具体要求和对应测试。[[/BLUE]]',
15:'系统按五条业务线绘制局部模型，另提供全局模型和关系明细图。计划引用能力评估，再由评估确定老人归属；派单引用班次，再由班次确定护工归属，减少重复字段。\n[[BLUE]]【待补充】个人负责模块的实体、联系、局部 UML 图及设计说明。[[/BLUE]]',
17:'系统共包含 47 张基础表、15 个业务视图和 2 个内部视图。五个模块均有存储过程、触发器及业务查询，Java 代码通过 DAO 读写数据库，并在业务方法中管理事务。\n[[BLUE]]【待补充】结合个人完成的内容，说明关系模式、范式、表结构、SQL 对比、索引和测试结果，按模板要求写 800—1000 字。[[/BLUE]]',
19:'[[BLUE]]【待补充】个人任务安排、版本提交、代码检查及团队协作记录。请注明实际完成时间和修改内容。[[/BLUE]]',
20:'演示数据均为虚构记录。账号密码以加盐摘要保存，身份字段加密后入库，并通过带密钥的摘要查重。查询结果按登录账号过滤，服务记录和审核记录分开保存，历史删除受到限制。\n[[BLUE]]【待补充】个人负责模块的权限检查、异常案例和测试结果。[[/BLUE]]',
21:'定时任务检查评估到期和应急超时，业务操作时再次校验有效期。数据库已完成全量备份加 binlog 增量恢复演练，恢复后核对了 47 张基础表的记录数。\n[[BLUE]]【待补充】个人编写或修改的过程、触发器、索引，以及实际验证结果。[[/BLUE]]'}
for row,text in texts.items():
 cell=t.cell(row,1);cell.text=''
 for i,line in enumerate(text.split('\n')):
  p=cell.paragraphs[0] if i==0 else cell.add_paragraph();p.paragraph_format.line_spacing=1.5;add_text(p,line,10.5)
# Metadata that still needs team confirmation uses the same blue as editorial notes.
for row,col in [(2,2)]+[(row,col) for row in [5,6,7] for col in [0,3,5]]:
 for p in t.cell(row,col).paragraphs:
  for run in p.runs:run.font.color.rgb=RGBColor.from_string(BLUE)

# The template's narrow labels previously snapped each character to the document
# grid, stretching short pending rows over whole pages. Keep its cells and fields.
for p in d._element.xpath('.//w:p'):
 pp=p.get_or_add_pPr();snap=pp.find(qn('w:snapToGrid'))
 if snap is None:snap=OxmlElement('w:snapToGrid');pp.append(snap)
 snap.set(qn('w:val'),'0')
for i,row in enumerate(t.rows):
 for cell in row.cells:
  for p in cell.paragraphs:
   p.paragraph_format.space_before=Pt(3);p.paragraph_format.space_after=Pt(3)
   if i in [8,12,16,18]:p.paragraph_format.keep_with_next=True
 if i in texts:
  label=row.cells[0]
  for p in label.paragraphs:
   p.paragraph_format.line_spacing=1.0
   for run in p.runs:font(run,10.5)
  if i!=17:
   trpr=row._tr.get_or_add_trPr();flag=OxmlElement('w:cantSplit');trpr.append(flag)

# Remove instruction highlighting throughout, preserve table geometry and original title.
for h in d._element.xpath('.//w:highlight'):h.getparent().remove(h)
out=R/'outputs/刘禹伟_个人报告预填稿_待确认实际分工.docx';d.save(out);preserve(source,out)
print('Created template-based group report and personal prefill')
