'use strict';
const $=id=>document.getElementById(id);let token=sessionStorage.getItem('elder_token'),user=null,kind='elders',page=0,total=0;
const names={elders:'老人档案',assessments:'能力评估',plans:'照护计划',orders:'服务订单',caregivers:'护工档案',credentials:'职业证书',shifts:'护工排班',hours:'服务工时',devices:'监测设备',vitals:'体征数据',events:'应急事件',claims:'补贴申报',policies:'补贴政策',inspections:'质量抽查',catalog:'服务项目',bundles:'照护服务包',notifications:'通知消息'};
const menus={ADMIN:['elders','assessments','plans','orders','caregivers','shifts','hours','devices','vitals','events','claims','policies','inspections','catalog','bundles','notifications'],FAMILY:['elders','assessments','plans','orders','devices','vitals','events','claims','policies','catalog','notifications'],CAREGIVER:['orders','shifts','hours','credentials','elders','events','notifications'],MANAGER:['caregivers','credentials','shifts','hours','inspections','notifications'],STREET:['claims','policies','notifications'],REGULATOR:['claims','policies','inspections','events','notifications']};
// [caption, roles, fields]. Field labels remain explicit so each command is reviewable.
const actions={
'elder.create':['建立老人档案',['ADMIN'],['name:姓名','identity:身份证号（演示请用虚构编号）','birthDate:出生日期:date','address:住址']],
'elder.update':['更新老人住址',['ADMIN'],['elderId:老人编号:number','address:新住址']],
'elder.transfer':['迁移所属社区',['ADMIN'],['elderId:老人编号:number','communityId:目标社区编号:number','address:新住址']],
'elder.deactivate':['停用老人档案',['ADMIN'],['elderId:老人编号:number']],
'guardian.create':['关联家属账号',['ADMIN'],['elderId:老人编号:number','accountId:家属账号编号:number','relation:与老人关系','phone:联系电话']],
'assessment.create':['登记能力评估',['ADMIN'],['elderId:老人编号:number','scale:评估量表','score:得分:number','level:能力等级（SELF/PARTIAL/SEVERE）','validUntil:有效期截止:datetime-local']],
'plan.create':['制定照护计划',['ADMIN'],['elderId:老人编号:number','assessmentId:评估编号:number','bundleId:服务包编号:number','caregiverId:责任护工编号:number','startsAt:开始时间:datetime-local','endsAt:结束时间:datetime-local']],
'order.create':['预约上门服务',['ADMIN','FAMILY'],['elderId:老人编号:number','itemId:服务项目编号:number','startsAt:预约时间:datetime-local']],
'order.assign':['派单或改派',['ADMIN'],['orderId:订单编号:number','shiftId:班次编号:number','reason:派单或改派原因']],
'order.accept':['接受派单',['CAREGIVER'],['orderId:订单编号:number']],
'order.checkin':['上门签到',['CAREGIVER'],['orderId:订单编号:number','location:签到位置说明','fenceOk:模拟围栏是否通过（true/false）']],
'order.finish':['结束服务并签退',['CAREGIVER'],['orderId:订单编号:number','notes:服务情况记录']],
'order.confirm':['确认服务完成',['FAMILY'],['orderId:订单编号:number','confirmation:确认说明']],
'order.cancel':['取消未开始的订单',['ADMIN','FAMILY'],['orderId:订单编号:number','reason:取消原因']],
'complaint.create':['服务评价与反馈',['FAMILY'],['orderId:订单编号:number','rating:评分（1至5）:number','content:评价或问题']],
'credential.create':['登记护工资格',['MANAGER'],['caregiverId:护工编号:number','itemId:适用服务项目编号:number','certificateNo:证书编号','level:证书等级','validFrom:生效时间:datetime-local','validUntil:失效时间:datetime-local']],
'caregiver.status':['更新在岗状态',['MANAGER'],['caregiverId:护工编号:number','status:状态（ACTIVE/LEAVE/LEFT）']],
'shift.create':['新增班次',['MANAGER','ADMIN'],['caregiverId:护工编号:number','startsAt:开始时间:datetime-local','endsAt:结束时间:datetime-local','capacity:可接单量:number']],
'shift.delete':['删除未使用的未来班次',['MANAGER'],['shiftId:班次编号:number']],
'hours.review':['复核异常工时',['MANAGER'],['attendanceId:工时编号:number','approve:是否通过（true/false）','reason:复核理由']],
'device.create':['登记设备',['ADMIN'],['serial:设备序列号','type:设备类型','location:安装位置']],
'device.bind':['绑定或换绑设备',['ADMIN'],['deviceId:设备编号:number','elderId:老人编号:number']],
'vital.upload':['批量上传模拟体征',['ADMIN'],['samples:采样数组（见运行文档示例）:json']],
'event.respond':['响应应急事件',['ADMIN','CAREGIVER'],['eventId:事件编号:number']],
'event.close':['填写应急处置结果',['ADMIN','CAREGIVER'],['eventId:事件编号:number','result:到场处置结果']],
'claim.submit':['提交补贴申报',['ADMIN'],['elderId:老人编号:number','policyId:政策编号:number','period:结算月份（YYYY-MM）','orderIds:订单编号列表（例如 [1,3]）:json']],
'claim.review':['审核补贴申报',['STREET','REGULATOR'],['claimId:申报编号:number','approve:是否通过（true/false）','reason:审核意见']],
'claim.pay':['登记模拟拨付',['REGULATOR'],['claimId:申报编号:number','reference:模拟拨付凭证编号']],
'inspection.create':['发起质量抽查',['REGULATOR'],['caregiverId:护工编号:number','method:方式（PHONE/VISIT）','score:评分:number','issue:发现的问题','rectification:整改要求']],
'inspection.close':['复核并关闭整改',['REGULATOR'],['inspectionId:抽查编号:number','result:整改复核结果']],
'maintenance.run':['检查到期和超时事项',['ADMIN'],[]]};
const columns={id:'编号',name:'名称',elder_id:'老人编号',elder_name:'老人',community_id:'社区',status:'状态',starts_at:'开始时间',ends_at:'结束时间',amount:'金额（元）',period:'月份',caregiver_id:'护工编号',caregiver_name:'护工',item_id:'项目编号',address:'地址',birth_date:'出生日期',valid_until:'有效期截止',score:'得分',ability_level:'能力等级',created_at:'创建时间',effective_minutes:'有效分钟',capacity:'可接单量',used_count:'已接单量',deadline:'响应截止',level:'等级',issue:'问题',rectification:'整改要求',result:'处理结果',organization_id:'机构编号',check_in:'签到',check_out:'签退',fence_ok:'围栏结果',notes:'记录',reference_no:'凭证号'};
async function api(url,opt={}){const response=await fetch('/api'+url,{...opt,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...opt.headers}});const data=await response.json();if(!response.ok){if(response.status===401){sessionStorage.removeItem('elder_token');$('workspace').hidden=true;$('login').hidden=false;}throw Error(data.error||'请求失败');}return data;}
function node(tag,text){let e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e;}
function renderActions(){let select=$('action');select.replaceChildren();Object.entries(actions).filter(([k,v])=>v[1].includes(user.role)).forEach(([k,v])=>{let o=node('option',v[0]);o.value=k;select.append(o);});fields();}
function fields(){$('fields').replaceChildren();let a=actions[$('action').value];$('action-form').hidden=!a;if(!a)return;for(let field of a[2]){let [key,label,type]=field.split(':');let l=node('label',label);let input=node('input');input.name=key;input.type=['number','date','datetime-local'].includes(type)?type:'text';input.required=true;input.dataset.type=type||'text';l.append(input);$('fields').append(l);}}
async function load(){try{let data=await api('/data/'+kind+'?page='+page+'&size=10');total=data.total;$('title').textContent=names[kind];$('description').textContent='仅显示当前账号有权访问的数据。';$('total').textContent='共 '+total+' 条记录';$('page').textContent='第 '+(page+1)+' 页';$('prev').disabled=page===0;$('next').disabled=(page+1)*10>=total;$('thead').replaceChildren();$('tbody').replaceChildren();if(data.rows.length){let keys=Object.keys(data.rows[0]);let head=node('tr');keys.forEach(k=>head.append(node('th',columns[k]||k)));$('thead').append(head);for(let row of data.rows){let tr=node('tr');keys.forEach(k=>{let td=node('td',row[k]===null?'—':String(row[k]));td.title=td.textContent;tr.append(td);});$('tbody').append(tr);}}else{$('tbody').append(node('tr','暂无记录'));}document.querySelectorAll('nav button').forEach(e=>e.classList.toggle('active',e.dataset.kind===kind));}catch(e){$('result').textContent=e.message;$('result').className='error';}}
async function enter(){user=await api('/me');$('login').hidden=true;$('workspace').hidden=false;$('identity').textContent=user.name+' · 社区 '+user.community;$('nav').replaceChildren();for(let k of menus[user.role]){let b=node('button',names[k]);b.dataset.kind=k;b.onclick=()=>{kind=k;page=0;load();};$('nav').append(b);}kind=menus[user.role][0];page=0;renderActions();await load();}
$('login-form').onsubmit=async e=>{e.preventDefault();try{let body=Object.fromEntries(new FormData(e.target));let r=await api('/login',{method:'POST',body:JSON.stringify(body)});token=r.token;sessionStorage.setItem('elder_token',token);e.target.password.value='';await enter();}catch(err){$('login-error').textContent=err.message;}};
$('action-form').onsubmit=async e=>{e.preventDefault();let button=e.target.querySelector('button');button.disabled=true;try{let body={};for(let input of e.target.querySelectorAll('input'))body[input.name]=input.dataset.type==='json'?JSON.parse(input.value):input.dataset.type==='number'?Number(input.value):input.value;let r=await api('/actions/'+$('action').value,{method:'POST',body:JSON.stringify(body)});$('result').className='';$('result').textContent='操作成功：'+r.result;await load();}catch(err){$('result').className='error';$('result').textContent=err.message;}finally{button.disabled=false;}};
$('action').onchange=fields;$('refresh').onclick=load;$('prev').onclick=()=>{page--;load();};$('next').onclick=()=>{page++;load();};$('logout').onclick=async()=>{await api('/logout',{method:'POST'});token=null;sessionStorage.removeItem('elder_token');$('workspace').hidden=true;$('login').hidden=false;};if(token)enter().catch(()=>{});
