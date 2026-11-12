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
def para(d,text='',size=12,bold=False,code=False):
 p=d.add_paragraph();p.paragraph_format.line_spacing=1.15 if code else 1.5;p.paragraph_format.space_after=Pt(6);p.paragraph_format.keep_with_next=False;font(p.add_run(text),size,bold,code);return p
def heading(d,text,level=1,newpage=False):
 p=d.add_paragraph(style='Heading '+str(level));p.paragraph_format.page_break_before=newpage;p.paragraph_format.keep_with_next=True;p.paragraph_format.space_before=Pt(12);p.paragraph_format.space_after=Pt(8);font(p.add_run(text),16 if level==1 else 13,True);return p
def picture(d,name,caption,maxh=5.8):
 file=R/name;im=Image.open(file);w,h=im.size;scale=min(6.45/w,maxh/h)
 p=d.add_paragraph();p.paragraph_format.keep_with_next=True;p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.add_run().add_picture(str(file),width=Inches(w*scale),height=Inches(h*scale));p=para(d,caption,10.5);p.alignment=WD_ALIGN_PARAGRAPH.CENTER

def table(d,headers,rows):
 t=d.add_table(rows=1,cols=len(headers));t.autofit=True
 for i,h in enumerate(headers):t.rows[0].cells[i].text=str(h)
 trpr=t.rows[0]._tr.get_or_add_trPr();repeat=OxmlElement('w:tblHeader');trpr.append(repeat)
 for row in rows:
  cells=t.add_row().cells
  for i,v in enumerate(row):cells[i].text=str(v)
 for j,row in enumerate(t.rows):
  for cell in row.cells:
   tc=cell._tc.get_or_add_tcPr();b=OxmlElement('w:tcBorders')
   for edge in ['top','left','bottom','right']:
    e=OxmlElement('w:'+edge);e.set(qn('w:val'),'single');e.set(qn('w:sz'),'4');e.set(qn('w:color'),'D9D9D9');b.append(e)
   tc.append(b)
   if j==0:sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'E8EDE8');tc.append(sh)
   for p in cell.paragraphs:
    p.paragraph_format.line_spacing=1.15;p.paragraph_format.space_after=Pt(3)
    for r in p.runs:font(r,10.5,j==0)
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
 for line in (R/file).read_text().splitlines():
  if not line.strip() or line.startswith('# '):continue
  if line.startswith('## '):heading(d,line[3:],2)
  elif line.startswith('|') or line.startswith('```'):continue
  else:para(d,line)

source=base/'数据库系统课程设计报告模板2026（小组）.docx';d=Document(source)
body=d._element.body
for child in list(body)[2:]:
 if child.tag!=qn('w:sectPr'):body.remove(child)
for t in d._element.xpath('.//w:t'):
 if t.text:
  t.text=t.text.replace('小组名称：','小组名称：邻安养老（待组员确认）').replace('小组成员：张三（组长）、李四','小组成员：待提供真实名单').replace('****年****月***日','2026年11月12日')
heading(d,'目录',1,True)
p=d.add_paragraph();r=p.add_run();beg=OxmlElement('w:fldChar');beg.set(qn('w:fldCharType'),'begin');r._r.append(beg);ins=OxmlElement('w:instrText');ins.set(qn('xml:space'),'preserve');ins.text=' TOC \\o "1-1" \\h \\z \\u ';r._r.append(ins);sep=OxmlElement('w:fldChar');sep.set(qn('w:fldCharType'),'separate');r._r.append(sep)
chapters=['一、成员分工','二、需求分析','三、系统概念结构设计','四、系统的逻辑结构设计','五、系统物理结构设计、持久层设计和关键业务代码','六、项目运维管理和优化','七、附件内容']
for x,page in zip(chapters,[3,4,11,17,22,48,60]):
 p=para(d,x+'\t'+str(page));p.paragraph_format.tab_stops.add_tab_stop(Inches(6.4),WD_TAB_ALIGNMENT.RIGHT,WD_TAB_LEADER.DOTS)
r=d.add_paragraph().add_run();end=OxmlElement('w:fldChar');end.set(qn('w:fldCharType'),'end');r._r.append(end)
update=OxmlElement('w:updateFields');update.set(qn('w:val'),'true');d.settings.element.append(update)
heading(d,chapters[0],1,True)
para(d,'项目题目为社区居家养老智慧服务平台的设计与实现。当前技术成果覆盖五条业务线，采用 Java 17 与 MySQL，提交材料需由实际组员核对、学习和演示。人员名单与实际贡献尚未提供，以下为可分配的责任范围，不作为已发生的分工记录。')
table(d,['责任范围','需要承担的数据库工作','协作关系'],[(v[0], '用例和鲁棒分析、局部模型、3 组双实现 SQL、视图、过程、触发器与测试','与公共模型和关联业务核对字段、状态及验收口径') for v in meta['groups'].values()])
para(d,'组长、4—5 名成员的姓名学号、每人的实际任务和协作情况：待团队填写。不得将上述建议责任范围直接改写为未经发生的个人贡献。')
heading(d,chapters[1],1,True)
para(d,'平台围绕“建档—评估—计划—下单—派单—服务—工时—补贴—监管”组织数据。设备监测与应急处理通过老人档案关联主流程。核心问题包括有效期失效后仍接单、护工超量或重叠服务、改派历史被覆盖、设备换绑导致历史错归属和补贴缺乏服务依据。')
para(d,'参与者包括社区管理员、老人及家属、护工、服务机构负责人、街道与民政监管。街道初审和民政终审在账号角色上细分。家属只访问关联老人，护工只访问本人订单，机构负责人只管理本机构护工，监管人员不能任意修改原始服务事实。')
for key,(title,tables) in meta['groups'].items():
 heading(d,title,2,True)
 para(d,{'A':'管理员建立唯一档案和家属关联，登记有效评估，再选择匹配能力等级的照护包并制定计划。每份计划引用采用的评估，系统检查有效期覆盖与区间重叠。','B':'家属或社区创建预约。管理员按资质、区域、排班和整改状态派单。护工接单、签到、填写记录和签退后进入待确认，家属确认后订单才完成。改派记录保留，失败时恢复原分配。','C':'机构负责人登记项目资质与班次，护工服务前后打卡。容量不得超上限，班次和实际预约不得重叠。模拟围栏或时段异常进入人工复核，未经认定的工时不参与补贴。','D':'设备绑定历史保存老人归属和时间区间。体征上传先校验采集时绑定，再按课程演示阈值标记异常并建立待确认事件。人员响应后记录处置；超时通过定时任务升级并产生通知。','E':'社区按服务月份归集可补贴订单。申报事务逐笔检查确认、有效工时、政策和重复资格，任一不合格整体回滚。街道和民政分级审核，再登记模拟拨付；监管抽查和整改结果影响接单。'}[key])
 picture(d,Path('docs/diagrams')/(key+'-usecase.png'),title+'用例图',2.9)
 picture(d,Path('docs/diagrams')/(key+'-robustness.png'),title+'鲁棒图',2.8)
 para(d,'鲁棒图区分操作入口（boundary）、规则与事务控制（control）、持久化实体（entity）。参与者通过入口发起操作，控制对象校验业务条件后访问实体。主要数据项包括：'+ '、'.join(meta['tables'][x] for x in tables)+'。字段名称、粗略类型和说明见数据字典附件。')
heading(d,'非功能性需求',2)
para(d,'完整性方面使用主外键、唯一、范围检查以及跨表业务校验。并发方面对同老人计划和同护工排班串行检查，对最后一个接单名额原子占用。安全方面实施密码摘要、失败锁定、会话到期、参数化 SQL、角色和行级过滤。历史方面保留改派、设备绑定、审核与审计记录。恢复方面采用全量备份结合 binlog，并在独立库验证。')
heading(d,chapters[2],1,True)
para(d,'全局概念模型包含 35 个持久化实体。老人、服务项目和账号贯穿多个业务域。下图展示全局联系概览，完整可缩放类图随附件提供；随后按业务给出局部模型，字段和联系以实际数据库为准。PK 为主键，FK 为外键，UQ 为唯一键。')
picture(d,'docs/diagrams/global-uml.png','全局 UML 类图概览，完整图见附件 01',8.0)
for key,(title,_) in meta['groups'].items():
 heading(d,title+'局部模型',2,True);picture(d,Path('docs/diagrams')/(key+'-uml.png'),title+'局部 UML 类图',8.0)
 para(d,'关联基数按业务含义和数据库外键标注。多个实体共用主数据时引用同一编号；可选关联使用允许空值的外键；一对多历史记录不通过覆盖旧行实现。')
heading(d,chapters[3],1,True)
mdprose(d,'docs/逻辑设计与范式分析.md')
heading(d,'关系模式清单',2)
for t,label in meta['tables'].items():
 cs=[c for c in cols if c['table_name']==t];para(d,label+'：'+t+'('+', '.join(c['column_name']+(' [PK]' if c['column_key']=='PRI' else '') for c in cs)+')。',10.5)
para(d,'所有外键及其目标列单列在 evidence/foreign-keys.tsv 与数据字典中；模式中的生成列仅服务于物理唯一性约束。')
heading(d,chapters[4],1,True)
heading(d,'运行环境与分层',2)
para(d,'运行环境：Java 17.0.15，Spring Boot 3.4.4，Spring JDBC，MySQL 8.4.11，本机 macOS arm64。应用采用操作入口、业务事务、JDBC DAO、关系数据库四层组织。Windows 端提供 Java JAR 与本地 MySQL 初始化和启动脚本，不依赖 Docker；Windows 目标机运行仍需实测。')
picture(d,'evidence/ui-workspace.png','实际浏览器登录后的社区工作台',4.3)
heading(d,'DAO 基础实现',2)
code(d,(R/'src/main/java/edu/bjfu/eldercare/Store.java').read_text())
para(d,'SQL 的用户值全部参数绑定。插入方法返回生成主键，查询和更新统一经过 JdbcTemplate。分页入口校验页码与大小并使用固定数据类别白名单。体征批量上传在同一事务中逐条校验和写入，任一条越权或无效整体回滚。业务方法使用 @Transactional，过程不提前提交。')
heading(d,'完整建表与索引语句',2)
for line in (R/'database/01_schema.sql').read_text().splitlines():
 if line.startswith('CREATE TABLE'):
  t=line.split('(')[0].split()[-1];heading(d,meta['tables'].get(t,t),2);code(d,line)
heading(d,'业务视图与内部关联视图',2)
para(d,'五条业务线各提供三个不同用途的视图，共 15 个业务视图。另有 v_plan_context 和 v_dispatch_context 两个内部视图，派生老人、护工归属，避免基础表重复保存。视图提供查询结构，具体人员范围仍由业务授权过滤决定。')
for line in (R/'database/03_views.sql').read_text().splitlines():code(d,line)
for line in (R/'database/01_schema.sql').read_text().splitlines():
 if line.startswith('CREATE VIEW'):code(d,line)
heading(d,'各业务 SQL 的两种实现',2)
for q in queries:
 heading(d,q['id']+' '+q['title'],2);para(d,q['comparison']);para(d,'实现 A',10.5,True);code(d,q['a']+';');para(d,'实现 B',10.5,True);code(d,q['b']+';');para(d,'两种实现已对当前测试数据执行并逐行比较，结果一致。执行计划和实际结果见 evidence/query-comparison.json。',10.5)
heading(d,chapters[5],1,True)
mdprose(d,'docs/运行维护与验收.md')
heading(d,'存储过程与触发器代码',2)
para(d,'以下核心例程按业务覆盖有效期、派单、工时、应急与补贴，全部审计和历史保护触发器在源代码附件中提供。')
# Include full executable routines so each business's process and trigger are reviewable.
text=(R/'database/02_routines.sql').read_text().split('CREATE TRIGGER audit_')[0]
code(d,text)
heading(d,'测试与结果',2)
summary=(R/'target/surefire-reports/edu.bjfu.eldercare.DatabaseIntegrationTest.txt').read_text().strip().splitlines()[-1];para(d,summary)
para(d,'测试连接独立的真实 MySQL 测试库。覆盖全部表最低记录数、15 组查询等价、视图执行、外键与唯一性、有效期、重叠区间、派单容量、确认凭据、工时复核、设备历史、异常事件、补贴回滚、身份隔离、登录锁定、会话退出及并发。完整方法名和原始结果保存在 Surefire XML 与测试源代码中。')
table(d,['验证对象','实际验证内容'],[['并发计划','两个事务同时创建同老人重叠计划，只成功一个'],['并发派单','两个订单竞争最后一个班次名额，只成功一个'],['失败改派','新班次不覆盖预约时刻时，旧派单和名额被回滚恢复'],['批量写入','一条合法、一条越权的数据批次整体回滚'],['完整服务','接单、签到、异常工时复核、签退、家属确认走通'],['备份恢复','全量加实际 binlog 增量在独立库恢复，35 表计数一致']])
heading(d,chapters[6],1,True)
table(d,['附件编号','内容与位置'],[['01','docs/diagrams：全局与局部 UML、用例图、鲁棒图，含 DOT、SVG、PNG'],['02','database：全部 DDL、视图、过程、触发器、15 组双实现查询'],['03','src 与 pom.xml：Java 源码、静态界面和测试源码'],['04','docs/数据字典与关系模式.md：所有字段的名称、类型、默认值、关联与含义'],['05','evidence：测试日志、查询执行计划、行数、恢复演练、浏览器截图'],['06','eldercare.jar 与 Windows 初始化、启动脚本'],['07','答辩演示文稿与 5 分钟录制讲稿；全员发言视频待真实录制'],['08','个人模板预填稿；个人实际分工和贡献待本人核对'],['09','教师提问反馈与解决方案待答辩发生后补充']])
para(d,'提交前应核对组名和成员名单、真实分工、个人报告与答辩的一致性，并补齐全员发言的录像及真实协作证据。不得把预设问答当作已发生的教师提问。')
heading(d,'参考资料',2)
for text in ['课程设计任务书 2026，第 1—9 页。','教师任务讲解逐字稿，00:01:58—00:34:23。','数据库系统课程设计报告模板 2026 小组与个人版。','数据库系统课程设计评分标准 2026 版。','MySQL 官方文档：CREATE TRIGGER、CREATE PROCEDURE、SIGNAL，https://dev.mysql.com/doc/refman/8.0/en/create-trigger.html 。','Spring 官方 JDBC 文档：https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html 。']:
 p=para(d,text,10.5);p.paragraph_format.line_spacing=1.15;p.paragraph_format.space_after=Pt(2)
out=R/'outputs/社区居家养老平台小组报告_待补成员信息.docx';d.save(out);preserve(source,out)
# Personal template: only known identity and factual shared technical material.
source=base/'数据库系统课程设计报告模板2026（个人）.docx';d=Document(source);t=d.tables[0]
for row,col,value in [(0,2,'刘禹伟'),(0,6,'计算机243'),(1,2,'241002808'),(1,6,'张晓宇'),(2,2,'待确认')]:t.cell(row,col).text=value
for row in [5,6,7]:t.cell(row,0).text='待提供真实成员';t.cell(row,3).text='待提供';t.cell(row,5).text='待提供'
texts={
9:'本课程设计围绕社区居家养老服务全过程建立关系数据库，覆盖老人档案与能力评估、服务下单与派单、护工排班和工时、健康监测与应急响应、补贴核算与监管五条业务线。系统以 Java 和 MySQL 实现，采用 JDBC 持久层连接业务操作与数据库约束，通过视图、存储过程、触发器及事务保持数据一致性。设计特别关注评估和证书有效期、计划和排班重叠、改派留痕、设备历史归属及补贴追溯。测试使用虚构数据，验证正常流程、拒绝场景、并发竞争、权限隔离和备份恢复。',
10:'待本人确认实际负责的业务模块、数据库设计任务、SQL、测试和完成情况。当前不能把公共系统成果全部写为个人独立完成。可根据实际分工从五条业务线选择对应设计、代码与验证证据，再按模板要求形成 150—200 字的真实描述。',
11:'待提供真实团队成员及协作事实，包括与哪些成员核对公共实体、主外键、状态流转和测试接口，会议或代码评审发生的时间与结果。当前未提供协作记录，因此不编造已经发生的小组会议或个人贡献。',
13:'公共需求已形成五套用例图与鲁棒图，文件位于 docs/diagrams。个人部分应按实际负责业务选用相应 A 至 E 图件，并说明参与者、操作入口、控制逻辑与实体。当前个人模块尚未确定，本栏保留待选择状态。',
14:'系统的共同非功能需求包括身份与角色校验、家属关联和机构辖区过滤、参数化查询、加盐密码摘要和字段加密；完整性方面采用主外键、唯一约束、时间范围检查和事务。计划、排班及派单名额需要并发控制，历史服务和补贴记录需要留痕。个人负责模块的具体拒绝案例与安全边界，须在实际分工确定后补充。',
15:'五个局部 UML 类图和一个全局类图已随工程提供。个人应说明所负责实体的含义及一对多、多对多联系，并与关联模块统一标识。计划引用评估后派生老人归属，派单引用班次后派生护工归属，减少重复存储。个人采用哪张局部图及具体设计贡献待本人确认。',
17:'公共技术材料包括 35 张基础表、15 个业务视图及两个内部关联视图、五条业务线的过程与触发器、15 组共 30 条查询，以及 Java DAO 和事务代码。真实 MySQL 测试证据位于 evidence。个人正式详述应围绕实际完成的关系模式、范式、表结构、查询对比、索引和测试展开，并引用对应文件和结果；不能将本段公共概览直接作为个人 800—1000 字工作记录提交。',
19:'本地 Git 已记录本次工程的实际开发版本，提交作者为工具助手，不能冒充学生本人提交。团队后续应记录实际学习、修改、测试和协作过程，按真实证据撰写个人工程管理情况。远程 GitHub 仓库、组员提交和会议截图尚未提供。',
20:'本项目涉及老人身份、联系方式、服务和体征等敏感数据，因此演示采用虚构记录。应用使用参数化 SQL、权限和数据范围过滤，密码以加盐摘要保存，身份信息加密并使用带密钥摘要查重。原始记录与审核结论分别存储，历史删除受限。个人应结合所负责模块补充具体风险、对应拒绝测试、剩余边界和密钥保管责任。',
21:'项目提供有效期处理、派单、工时核验、应急升级和补贴申报等过程，并用触发器实现关联校验、状态保护与留痕。定时任务负责时间到期事件，业务入口再次校验资格。已进行全量加 binlog 增量恢复演练。个人应在分工确认后补充其过程或触发器代码、执行计划比较和实际验证结果。'}
for row,text in texts.items():
 cell=t.cell(row,1);cell.text=text
 for p in cell.paragraphs:
  p.paragraph_format.line_spacing=1.5
  for r in p.runs:font(r,10.5)
# Remove instruction highlighting throughout, preserve table geometry and original title.
for h in d._element.xpath('.//w:highlight'):h.getparent().remove(h)
out=R/'outputs/刘禹伟_个人报告预填稿_待确认实际分工.docx';d.save(out);preserve(source,out)
print('Created template-based group report and personal prefill')
