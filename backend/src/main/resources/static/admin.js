'use strict';
let token='',content={},report={},chart,usersCache=[];
const $=id=>document.getElementById(id);
const status=message=>$('status').textContent=message;
async function api(path,method='GET',body){
 const response=await fetch('/api/'+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:body===undefined?undefined:JSON.stringify(body)});
 const text=await response.text();let data;try{data=text?JSON.parse(text):{};}catch{throw Error('Máy chủ trả dữ liệu không hợp lệ.');}
 if(!response.ok){if(response.status===401){token='';$('workspace').hidden=true;$('login').hidden=false;}throw Error(data.error||'Không thể thực hiện thao tác.');}return data;
}
async function run(action){status('');try{await action();}catch(e){status(e.message);}}
function row(label,actions=[]){const el=document.createElement('div');el.className='row';const title=document.createElement('span');title.textContent=label;el.append(title);for(const [name,fn]of actions){const button=document.createElement('button');button.textContent=name;button.onclick=()=>run(fn);el.append(button);}return el;}
function fill(formId,values){const form=$(formId);for(const [key,value]of Object.entries(values)){const input=form.elements.namedItem(key);if(input){if(input.type==='checkbox')input.checked=!!value;else input.value=value??'';}}form.scrollIntoView({behavior:'smooth',block:'center'});}
function table(target,headers,rows){const el=document.createElement('table'),head=document.createElement('tr');for(const h of headers){const th=document.createElement('th');th.textContent=h;head.append(th);}el.append(head);for(const r of rows){const tr=document.createElement('tr');for(const v of r){const td=document.createElement('td');td.textContent=v;tr.append(td);}el.append(tr);}$(target).replaceChildren(el);}
async function reload(){
 content=await api('admin/content');
 $('stat-courses').textContent=content.courses.length;
 $('stat-lessons').textContent=content.lessons.length;
 for(const select of document.querySelectorAll('select[name=courseId],select[name=chapterId]')){const old=select.value;const data=select.name==='courseId'?content.courses:content.chapters;select.replaceChildren(...data.map(x=>{const o=document.createElement('option');o.value=x.id;o.textContent=x.title;return o;}));if(data.some(x=>String(x.id)===old))select.value=old;}
 const courseFilter=$('course-filter')?.value||'all';const visibleCourses=content.courses.filter(c=>courseFilter==='all'||(courseFilter==='published'?c.published:!c.published));$('course-count').textContent=`${visibleCourses.length} / ${content.courses.length} khóa học`;
 $('course-list').replaceChildren(...visibleCourses.map(c=>row(c.title+(c.published?' · Đã xuất bản':' · Bản nháp'),[['Sửa',()=>fill('course-form',c)],[c.published?'Ẩn':'Duyệt & xuất bản',async()=>{if(c.published)await api('admin/courses/'+c.id,'DELETE');else await api('admin/courses/'+c.id,'PUT',{title:c.title,description:c.description,published:true});await reload();status(c.published?'Đã ẩn khóa học.':'Đã duyệt và xuất bản khóa học.');}]])));
 $('chapter-list').replaceChildren(...content.chapters.map(c=>row(c.title,[['Sửa',()=>fill('chapter-form',{...c,courseId:c.course_id})]])));
 $('lesson-list').replaceChildren(...content.lessons.map(l=>row(l.title,[['Sửa',()=>fill('lesson-form',{...l,chapterId:l.chapter_id,materialUrl:l.material_url})],['Xóa',async()=>{if(confirm('Xóa bài học này? Bài đã có tiến độ học sẽ được giữ lại.')){await api('admin/lessons/'+l.id,'DELETE');await reload();}}]])));
 $('question-list').replaceChildren(...content.questions.filter(q=>q.active).map(q=>row(q.prompt,[['Sửa',()=>fill('question-form',{...q,chapterId:q.chapter_id,a:q.option_a,b:q.option_b,c:q.option_c,d:q.option_d,correctOption:q.correct_option})],['Ngừng dùng',async()=>{await api('admin/questions/'+q.id,'DELETE');await reload();}]])));
 usersCache=await api('admin/users');$('stat-students').textContent=usersCache.filter(u=>u.role==='STUDENT').length;renderUsers();
 report=await api('admin/reports');
 const scored=report.students.filter(s=>s.average_score!=null);$('stat-average').textContent=scored.length?`${(scored.reduce((sum,s)=>sum+Number(s.average_score),0)/scored.length).toFixed(0)}%`:'—';
 table('student-report',['Sinh viên','Số bài nộp','Điểm TB (%)'],report.students.map(s=>[s.username,s.attempts,s.average_score==null?'Chưa có dữ liệu':Number(s.average_score).toFixed(1)]));
 table('question-report',['Câu hỏi','Lượt trả lời','Số lần sai'],report.questions.map(q=>[q.prompt,q.attempts,q.wrong]));
 if(window.Chart){chart?.destroy();chart=new Chart($('score-chart'),{type:'bar',data:{labels:report.students.map(s=>s.username),datasets:[{label:'Điểm trung bình (%)',data:report.students.map(s=>s.average_score),backgroundColor:'#4f46e5'}]},options:{responsive:true,maintainAspectRatio:false,scales:{y:{min:0,max:100}}}});}
 const audit=await api('admin/audit');table('audit-list',['Thời điểm','Người thực hiện','Thao tác','Đối tượng','Chi tiết'],audit.map(x=>[new Date(Number(x.created_at)).toLocaleString('vi-VN'),x.actor_name,x.action,`${x.target_type} #${x.target_id}`,x.detail||'—']));
}
function renderUsers(){const q=$('user-search').value.trim().toLocaleLowerCase('vi'),role=$('user-role-filter').value,state=$('user-state-filter').value;const rows=usersCache.filter(u=>(role==='all'||u.role===role)&&(state==='all'||(state==='enabled')===u.enabled)&&`${u.username} ${u.display_name||''}`.toLocaleLowerCase('vi').includes(q));$('user-list').replaceChildren(...rows.map(u=>row(`${u.display_name||u.username} · ${u.username} · ${{STUDENT:'Học viên',TEACHER:'Giảng viên',ADMIN:'Quản trị viên'}[u.role]} · ${(u.enabled?'Hoạt động':'Đã khóa')}`,u.role==='ADMIN'?[]:[[u.role==='TEACHER'?'Chuyển thành học viên':'Gán giảng viên',async()=>{await api('admin/users/'+u.id+'/role','PUT',{role:u.role==='TEACHER'?'STUDENT':'TEACHER'});await reload();}],[u.enabled?'Khóa':'Mở khóa',async()=>{await api('admin/users/'+u.id,'PUT',{enabled:!u.enabled});await reload();}]])));}
$('login').onsubmit=e=>{e.preventDefault();location.assign('/login.html?staff=1');};
$('logout').onclick=()=>run(async()=>{await api('auth/logout','POST');token='';sessionStorage.removeItem('studyspace_admin_session');location.reload();});
for(const button of document.querySelectorAll('[data-tab]'))button.onclick=()=>{for(const section of document.querySelectorAll('#workspace>section'))section.hidden=section.id!==button.dataset.tab;for(const item of document.querySelectorAll('[data-tab]')){const active=item===button;item.classList.toggle('is-active',active);if(active)item.setAttribute('aria-current','page');else item.removeAttribute('aria-current');}};
for(const [formId,endpoint,convert]of [
 ['course-form','courses',f=>({title:f.title,description:f.description,published:f.published==='on'})],
 ['chapter-form','chapters',f=>({title:f.title,courseId:+f.courseId,position:+f.position})],
 ['lesson-form','lessons',f=>({title:f.title,chapterId:+f.chapterId,content:f.content,materialUrl:f.materialUrl,position:+f.position})],
 ['question-form','questions',f=>({chapterId:+f.chapterId,prompt:f.prompt,options:[f.a,f.b,f.c,f.d],correctOption:+f.correctOption,explanation:f.explanation,difficulty:+f.difficulty})]
])$(formId).onsubmit=e=>{e.preventDefault();run(async()=>{const f=Object.fromEntries(new FormData(e.target));await api('admin/'+endpoint+(f.id?'/'+f.id:''),f.id?'PUT':'POST',convert(f));e.target.reset();e.target.elements.id.value='';await reload();status('Đã lưu.');});};
$('export').onclick=()=>{const escape=v=>'"'+String(v??'').replace(/^[=+@-]/,"'$&").replaceAll('"','""')+'"';const lines=[['Sinh viên','Số bài nộp','Điểm TB'],...report.students.map(s=>[s.username,s.attempts,s.average_score])];const url=URL.createObjectURL(new Blob(['\uFEFF'+lines.map(r=>r.map(escape).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='studyspace-results.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
async function restoreAdmin(){const saved=sessionStorage.getItem('studyspace_admin_session');if(!saved){location.replace('/login.html?staff=1');return;}token=saved;try{const account=await api('auth/me');if(account.role!=='ADMIN')throw Error('Tài khoản không có quyền quản trị.');sessionStorage.setItem('studyspace_active_role','ADMIN');$('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;document.querySelector('[data-tab="courses"]')?.classList.add('is-active');await reload();}catch{sessionStorage.removeItem('studyspace_admin_session');token='';location.replace('/login.html?staff=1');}}
restoreAdmin();
$('course-filter').onchange=()=>run(reload);for(const id of ['user-search','user-role-filter','user-state-filter'])$(id).addEventListener(id==='user-search'?'input':'change',renderUsers);
