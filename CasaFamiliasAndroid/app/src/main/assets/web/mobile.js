/* Presentation layer: original Drive business logic remains in app.js. */
window.Mobile=(()=>{
 const $=id=>document.getElementById(id), esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const money=n=>Number(n||0).toLocaleString('es-BO',{maximumFractionDigits:2})+' Bs';
 const paths={home:'M3 10 12 3l9 7v11h-6v-8H9v8H3Z',payments:'M3 5h18v14H3Z M3 10h18 M7 15h3',expense:'M6 3h12v18l-3-2-3 2-3-2-3 2Z M9 8h6 M9 12h6',cleaning:'M15 3l-4 9 M8 11l7 3-4 8-8-3Z',users:'M3 21v-3a5 5 0 0 1 10 0v3 M16 14a5 5 0 0 1 5 4v3 M8 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M17 3a4 4 0 0 1 0 8',bell:'M6 9a6 6 0 0 1 12 0v6l3 3H3l3-3Z M10 21h4',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v3 M12 19v3 M2 12h3 M19 12h3 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2',water:'M12 2S5 11 5 15a7 7 0 0 0 14 0C19 11 12 2 12 2Z',light:'M9 17h6 M10 21h4 M8 14a6 6 0 1 1 8 0l-1 3H9Z',gas:'M12 2c4 6-3 7 2 11 2-2 2-4 2-4 7 8 1 14-4 13C3 20 3 12 8 8c-1 5 4 6 4-6Z',wifi:'M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8 16a6 6 0 0 1 8 0 M12 20h.1',check:'M4 12l5 5L20 6',more:'M4 12h1 M11 12h1 M18 12h1',plus:'M12 4v16 M4 12h16',logout:'M9 3H4v18h5 M9 12h12 M17 8l4 4-4 4',lock:'M5 10h14v11H5Z M8 10V6a4 4 0 0 1 8 0v4',calendar:'M4 5h16v16H4Z M8 3v4 M16 3v4 M4 10h16',chart:'M4 3v18h17 M8 17v-4 M13 17V8 M18 17V5'};
 Object.assign(paths,{arrow:'M5 12h14 M13 6l6 6-6 6',back:'M19 12H5 M11 6l-6 6 6 6',shield:'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6Z M8 12l3 3 5-6',eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',eyeOff:'M3 3l18 18 M10 5a11 11 0 0 1 12 7s-2 3-5 5 M7 7a19 19 0 0 0-5 5s4 7 10 7c1 0 2-.2 3-.6 M10 10a3 3 0 0 0 4 4'});
 const icon=k=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="${paths[k]||paths.expense}"/></svg>`;
 let view='home',filter='pending',month=new Date().toISOString().slice(0,7),borrowed=null,selectedPay=null,reportMode='monthly';
 let demo=new URLSearchParams(location.search).get('demo')==='1',demoRole='family';
 const demoData={families:['García','López','Martínez','Rodríguez','Sánchez','Pérez'].map((x,i)=>({id:i+1,name:'Familia '+x})),profiles:[],periods:[],templates:[],expenses:[['Luz',1200,'2026-10-10'],['Agua',900,'2026-10-08'],['Internet',720,'2026-10-05'],['Gas',1080,'2026-10-02'],['Mantenimiento',600,'2026-10-15'],['Otros',400,'2026-10-20']].map((x,i)=>({id:'e'+i,concept:x[0],total_amount:x[1],expense_date:x[2],category:i<4?'basic':'external',justification:'Gasto del hogar'})),payments:[{id:'p1',family_id:1,amount:150,paid_at:'2026-10-14',status:'pending',note:'Agua'},{id:'p2',family_id:2,amount:120,paid_at:'2026-10-13',status:'pending',note:'Internet'},{id:'p3',family_id:3,amount:180,paid_at:'2026-10-12',status:'pending',note:'Gas'},{id:'p4',family_id:1,amount:120,paid_at:'2026-09-15',status:'approved',note:'Internet'}],shares:[],credits:[],cleaning:[1,2,3,4].map((n,i)=>({id:'t'+n,family_id:n,turn_date:'2026-10-'+String(5+i*7).padStart(2,'0'),status:i===0?'done':'pending',checklist:['Limpieza de salas','Cocina','Baños','Áreas comunes']})),notices:[{title:'Nuevo gasto registrado',body:'Se registró un gasto de mantenimiento.',created_at:'2026-10-14T10:00:00'},{title:'Tu comprobante fue aprobado',body:'Pago de agua – 150 Bs.',created_at:'2026-10-13T10:00:00'},{title:'Nuevo turno de limpieza',body:'Tu turno es del 12 al 18 de octubre.',created_at:'2026-10-12T10:00:00'}],notifications:[],audit:[],qr:[],monthly:[],proposals:[],votes:[]};
 demoData.profiles=demoData.families.map(f=>({id:'u'+f.id,display_name:f.name,contact_email:'familia'+f.id+'@correo.com',family_id:f.id,role:'family'}));demoData.profiles.push({id:'up',display_name:'Carlos',contact_email:'carlos@correo.com',role:'payer'});
 const debts=[['Luz',200],['Agua',150],['Internet',120],['Gas',180]].map((x,i)=>({share_id:'s'+i,concept:x[0],amount_due:x[1],balance:i===2?0:x[1],debt_status:i===2?'paid':'pending',month_start:'2026-10-01',due_date:'2026-10-20'}));
 function state(){return demo?{me:{role:demoRole,family_id:1,display_name:demoRole==='family'?'Familia García':demoRole==='payer'?'Carlos':'Administrador'},D:demoData,familyDebts:debts}:window.CF?.state();}
 function family(id){return state()?.D.families.find(f=>f.id===id)?.name||'Familia';}
 const roles={family:'Familia',payer:'Encargado de pagos',sysadmin:'Administrador',restaurant:'Restaurante'};
 function tone(concept){const s=String(concept||'').toLowerCase();return s.includes('agua')?['water','blue']:s.includes('luz')?['light','orange']:s.includes('gas')?['gas','pink']:s.includes('internet')||s.includes('wifi')?['wifi','green']:['expense','purple'];}
 const statusText={pending:'Pendiente',approved:'Aprobado',rejected:'Rechazado',paid:'Pagado',done:'Completado',review:'En revisión',overdue:'Vencido',active:'En curso'};
 function badge(s,label){return `<span class="badge ${esc(s)}">${esc(label||statusText[s]||s)}</span>`;}
 function tile(k,t){return `<span class="tileIcon ${t}">${icon(k)}</span>`;}
 function empty(text='Todavía no hay registros'){
  return `<div class="empty">${icon('expense')}<strong>${esc(text)}</strong><br><small>Los nuevos registros aparecerán aquí.</small></div>`;
 }
 function formatDate(value, options={day:'numeric',month:'short'}){
  if(!value)return '—';
  const date=new Date(String(value).slice(0,10)+'T12:00:00');
  return Number.isNaN(date.getTime())?String(value):date.toLocaleDateString('es-BO',options);
 }
 function monthbar(){return `<div class="month"><button aria-label="Mes anterior" data-month="-1">‹</button><strong>${new Date(month+'-02T12:00:00').toLocaleDateString('es-BO',{month:'long',year:'numeric'})}</strong><button aria-label="Mes siguiente" data-month="1">›</button></div>`;}
 function tabs(list){return `<div class="tabs line">${list.map(([v,t])=>`<button data-filter="${v}" class="${filter===v?'selected':''}">${t}</button>`).join('')}</div>`;}
 function item(k,t,title,sub,right,action){
  const tag=action?'button':'div';
  return `<${tag} class="item" ${action||''}>${tile(k,t)}<span class="copy"><strong>${esc(title)}</strong><small>${esc(sub||'')}</small></span><span class="right">${right||''}</span>${action?'<span class="chevron">›</span>':''}</${tag}>`;
 }
 function shortcuts(list,fam=false){
  const hints={newPayment:'Adjunta tu comprobante',payments:fam?'Consulta tu historial':'Revisa los comprobantes',dues:'Consulta lo pendiente',cleaning:'Organiza los turnos',newExpense:'Mantén las cuentas claras',balances:'El detalle de cada familia',notices:'Comparte las novedades',reports:'Todo en un solo resumen',users:'Gestiona las cuentas',settings:'Personaliza tu espacio'};
  return `<div class="shortcuts ${fam?'family':''}">${list.map(([v,n,k,t])=>`<button class="shortcut ${t}" data-go="${v}">${icon(k)}${icon('arrow').replace('<svg','<svg class="shortcut-arrow"')}<span>${esc(n)}<small>${esc(hints[v]||'')}</small></span></button>`).join('')}</div>`;
 }
 function debtTotal(s){return demo?50:(s.familyDebts||[]).reduce((a,d)=>a+Number(d.balance||0),0);}
 function debtFamily(f){return demo?[50,0,120,0,0,200][f-1]:window.CF.balanceForFamily(f);}
 function home(s){
  if(s.me.role==='restaurant')return restaurant(s);
  const {me,D}=s;
  const isFamily=me.role==='family';
  const pending=D.payments.filter(p=>p.status==='pending'&&(!isFamily||p.family_id===me.family_id));
  const next=D.cleaning.filter(t=>t.family_id===me.family_id&&t.status==='pending').sort((a,b)=>a.turn_date.localeCompare(b.turn_date))[0];
  const expenses=D.expenses.filter(e=>e.expense_date.startsWith(month));
  const total=isFamily?debtTotal(s):expenses.reduce((a,e)=>a+Number(e.total_amount),0);
  const period=isFamily?'Resumen general':new Date(month+'-02T12:00:00').toLocaleDateString('es-BO',{month:'long',year:'numeric'});
  const date=new Date().toLocaleDateString('es-BO',{weekday:'long',day:'numeric',month:'long'});
  const stat=(k,t,value,label,hint='',extra='')=>`<div class="card"><div class="stat-head">${tile(k,t)}${hint?`<span class="stat-hint">${hint}</span>`:''}</div><div><strong class="${extra}">${esc(value)}</strong><small>${esc(label)}</small></div></div>`;
  let out=`<div class="hello"><span class="eyebrow">${esc(roles[me.role]||'MI HOGAR')}</span><h1>Hola, <span>${esc(me.display_name)}</span></h1><small>${esc(date)}</small></div>`;
  if(!isFamily)out+=monthbar();
  out+=`<section class="balance-hero" aria-label="${isFamily?'Resumen de cuotas':'Resumen de gastos'}"><div class="hero-top"><span class="eyebrow">${isFamily?'MI CUENTA':'LAS CUENTAS DE CASA'}</span><span class="hero-period">${icon('calendar')}${esc(period)}</span></div><p class="hero-label">${isFamily?'Saldo pendiente':'Gastos del mes'}</p><div class="hero-amount"><strong>${esc(money(total).replace(/ Bs$/,''))}</strong><span>Bs</span></div><p class="hero-note">${isFamily?'Tus cuotas y pagos, en un solo lugar.':'Cada gasto registrado, cada cuenta más clara.'}</p>${icon('home').replace('<svg','<svg class="hero-house"')}<button class="hero-action" data-go="${isFamily?'newPayment':'expenses'}"><span>${isFamily?'Registrar un pago':'Ver detalle de gastos'}</span>${icon('arrow')}</button></section>`;
  if(isFamily){
   out+=`<div class="stats">${stat('payments','orange',pending.length,'Comprobantes en revisión')}${stat('calendar','blue',next?formatDate(next.turn_date):'Sin turno','Mi próximo turno','','stat-date')}</div>`;
  }else{
   const approved=D.payments.filter(p=>p.status==='approved'&&p.paid_at.startsWith(month)).length;
   const clear=D.families.filter(f=>debtFamily(f.id)<.01).length;
   out+=`<div class="stats">${stat('check','green',approved,'Pagos aprobados','Este mes')}${stat('payments','orange',pending.length,'Comprobantes por revisar')}${stat('users','purple',clear+'/'+D.families.length,'Familias al día')}${stat('calendar','blue',D.cleaning.filter(t=>t.turn_date.startsWith(month)&&t.status==='pending').length,'Turnos pendientes','Este mes')}</div>`;
  }
  const quick=isFamily?[['newPayment','Registrar pago','payments','green'],['payments','Mis pagos','expense','purple'],['dues','Mis cuotas','expense','blue'],['cleaning','Limpieza','cleaning','orange']]:me.role==='payer'?[['newExpense','Registrar gasto','plus','green'],['payments','Revisar pagos','check','orange'],['balances','Deudas por familia','users','purple'],['cleaning','Limpieza','cleaning','blue'],['notices','Publicar avisos','bell','pink'],['reports','Reportes','chart','purple']]:[['newExpense','Registrar gasto','plus','green'],['payments','Revisar pagos','check','orange'],['users','Usuarios','users','purple'],['cleaning','Limpieza','cleaning','blue'],['reports','Reportes','chart','pink'],['settings','Configuración','settings','purple']];
  out+=`<div class="section-heading"><h3>¿Qué hacemos hoy?</h3><small>ACCESOS RÁPIDOS</small></div>`+shortcuts(quick,isFamily);
  if(isFamily&&!demo)out+=`<p class="home-quiet">Saldo a favor: <strong>${money(CF.creditForFamily(me.family_id))}</strong></p>`;
  out+=`<div class="section-heading"><h3>En nuestra casa</h3><button class="section-link" data-go="notices">Ver avisos →</button></div>`;
  const notices=D.notices.slice(0,2);
  out+=notices.length?`<div class="card">${notices.map(n=>`<button class="home-notice" data-notice="${esc(n.title)}">${tile('bell','orange')}<span class="copy"><strong>${esc(n.title)}</strong><small>${esc(n.body)}</small></span></button>`).join('')}</div>`:empty('Sin novedades por ahora');
  return out+`<div class="home-footer">${icon('home')}Las cuentas claras. El hogar tranquilo.</div>`;
 }
 function restaurant(s){
  const rows=s.D.externalDebts||[];
  const balance=d=>Math.max(Number(d.amount_due||0)-Number(d.amount_paid||0),0);
  const total=rows.reduce((sum,d)=>sum+balance(d),0);
  const now=new Date();
  const today=[now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');
  const records=rows.map(d=>{
   const pending=balance(d),paid=pending<=.005;
   const status=paid?'paid':d.due_date&&d.due_date<today?'overdue':Number(d.amount_paid||0)>0?'partial':'pending';
   const service=d.service==='electricity'?'Luz':'Agua';
   return item(d.service==='electricity'?'light':'water',d.service==='electricity'?'orange':'blue',service,formatDate(d.month_start,{month:'long',year:'numeric'})+' · '+(d.due_date?'Vence '+formatDate(d.due_date):'Sin fecha'),`<strong>${money(pending)}</strong>${badge(status,status==='partial'?'Pago parcial':null)}`);
  }).join('');
  return `<div class="hello"><span class="eyebrow">RESTAURANTE</span><h1>Hola, <span>${esc(s.me.display_name)}</span></h1><small>Tus servicios, claros y al día.</small></div><section class="balance-hero"><span class="eyebrow">MI CUENTA</span><p class="hero-label">Saldo pendiente de servicios</p><div class="hero-amount"><strong>${esc(money(total).replace(/ Bs$/,''))}</strong><span>Bs</span></div><p class="hero-note">Aquí solo aparecen tus importes de luz y agua.</p></section><div class="section-heading"><h3>Mis servicios</h3><button class="section-link" data-click="refreshBtn">Actualizar ↻</button></div>`+(records||empty('No tienes deudas registradas'))+`<button class="ghost" data-source="view-restaurant" data-title="Historial de servicios">Ver historial completo</button>`;
 }
 function payments(s){const manager=s.me.role!=='family';let list=s.D.payments.filter(p=>manager||p.family_id===s.me.family_id);if(manager)list=list.filter(p=>p.status===filter);else if(filter==='receipts')list=list.filter(p=>p.receipt_path||demo);return (manager?tabs([['pending','En revisión'],['approved','Aprobados'],['rejected','Rechazados']]):tabs([['pending','Mis pagos'],['receipts','Comprobantes']]))+(list.length?list.map(p=>{let [k,t]=tone(p.note);return item(k,t,manager?family(p.family_id):p.note||'Pago',formatDate(p.paid_at),`<strong>${money(p.amount)}</strong>${badge(p.status,p.status==='pending'?'En revisión':null)}`,`data-pay="${esc(p.id)}"`);}).join(''):empty('No hay comprobantes en esta sección'))+(!manager?'<button class="primary" data-go="newPayment">＋ Registrar pago</button>':'');}
 function dues(s){let list=s.familyDebts||[];return monthbar()+(list.filter(d=>!d.month_start||d.month_start.startsWith(month)).map(d=>{let [k,t]=tone(d.concept);return item(k,t,d.concept,'Cuota mensual',`<strong>${money(d.amount_due)}</strong>${badge(d.debt_status)}`,`data-debt="${esc(d.share_id||d.id)}"`);}).join('')||empty('No hay cuotas para este mes'));}
 function expenses(s){let list=s.D.expenses.filter(e=>e.expense_date.startsWith(month));if(filter==='services')list=list.filter(e=>e.category==='basic');if(filter==='external')list=list.filter(e=>e.category==='external');return monthbar()+tabs([['all','Todos'],['services','Servicios'],['external','Arreglos']])+(list.map(e=>{let [k,t]=tone(e.concept);return item(k,t,e.concept,formatDate(e.expense_date),`<strong>${money(e.total_amount)}</strong>${badge('active','Registrado')}`,`data-expense="${esc(e.id)}"`);}).join('')||empty())+'<button class="primary" data-go="newExpense">＋ Registrar gasto</button><button class="ghost" data-source="monthlyServicesPanel" data-title="Servicios básicos del mes">Registrar paquete mensual</button><button class="ghost" data-source="externalUtilityPanel" data-title="Aportes del restaurante">Aportes del restaurante</button><button class="ghost" data-source="monthlyServicesBody" data-title="Servicios registrados">Servicios registrados</button>';}
 function balances(s){return monthbar()+s.D.families.map(f=>{const n=debtFamily(f.id);return item('users',n?'pink':'purple',f.name,money(n),badge(n?'pending':'paid',n?'Pendiente':'Al día'),`data-family="${f.id}"`);}).join('');}
 function cleaning(s){return monthbar()+(s.D.cleaning.filter(t=>t.turn_date.startsWith(month)).map(t=>item('users','purple',formatDate(t.turn_date,{day:'numeric',month:'long'}),family(t.family_id),badge(t.status==='done'?'done':'pending'),`data-turn="${esc(t.id)}"`)).join('')||empty('No hay turnos para este mes'))+(s.me.role==='sysadmin'?'<button class="primary" data-source="cleaningCreatePanel" data-title="Nuevo turno">＋ Crear turno</button>':'')+'<button class="ghost" data-source="swapList" data-title="Cambios de turno">Solicitudes de intercambio</button>';}
 function notices(s){return (s.D.notices.map(n=>item('bell','orange',n.title,n.body,`<small>${esc(formatDate(n.created_at))}</small>`,`data-notice="${esc(n.title)}"`)).join('')||empty('Sin avisos'))+(s.me.role!=='family'?'<button class="primary" data-source="noticeCreatePanel" data-title="Publicar aviso">＋ Publicar aviso</button>':'')+'<button class="ghost" data-source="notificationList" data-title="Notificaciones">Mis notificaciones</button>';}
 function users(s){let list=s.D.profiles.filter(p=>filter==='all'||p.role===filter);return tabs([['all','Todos'],['family','Familias'],['payer','Encargado'],['sysadmin','Admin']])+list.map(p=>item('users','purple',p.display_name,p.contact_email,badge('active',roles[p.role]||p.role),`data-user="${esc(p.id)}"`)).join('')+'<button class="primary" data-source="userForm" data-title="Nuevo usuario">＋ Nuevo usuario</button>';}
 function reports(s){let list=s.D.expenses.filter(e=>reportMode==='annual'?e.expense_date.startsWith(month.slice(0,4)):e.expense_date.startsWith(month)),total=list.reduce((a,e)=>a+Number(e.total_amount),0);const by={};list.forEach(e=>by[e.concept]=(by[e.concept]||0)+Number(e.total_amount));let angle=0;const colors=['#4a8b6c','#e5b16a','#85a3b7','#c89981','#9a90af','#b3c79d'];const stops=Object.entries(by).map(([k,n],i)=>{let start=angle;angle+=total?n/total*100:0;return `${colors[i%colors.length]} ${start}% ${angle}%`;}).join(',');return `<div class="tabs line">${[['monthly','Mensual'],['annual','Anual'],['concept','Por concepto']].map(([k,t])=>`<button data-report="${k}" class="${reportMode===k?'selected':''}">${t}</button>`).join('')}</div>`+monthbar()+`<div class="card"><div class="ring" style="background:${stops?'conic-gradient('+stops+')':'#eee'}"><div class="inside"><small>Total</small><strong>${money(total)}</strong></div></div>${Object.entries(by).map(([k,n],i)=>`<div class="reportline"><span><span style="color:${colors[i%colors.length]}">●</span> ${esc(k)}</span><strong>${money(n)}</strong></div>`).join('')||empty()}</div><div class="row"><button class="primary" data-click="exportPdfBtn">Exportar PDF</button><button class="ghost" data-click="exportXlsxBtn">Exportar Excel</button></div>`;}
 function row(k,n,action){return `<button class="setting-row" ${action}>${icon(k)}<span>${n}</span><b>›</b></button>`;}
 function settings(s){return profileCard(s)+`<h3>Cuenta</h3>`+row('users','Mi perfil','data-source="view-settings" data-title="Mi cuenta"')+row('lock','Cambiar contraseña','data-source="passwordForm" data-title="Cambiar contraseña"')+(s.me.role==='sysadmin'?'<h3>Administración</h3>'+row('calendar','Cerrar / Reabrir mes','data-source="periodPanel" data-title="Cierre mensual"')+row('expense','Plantillas de gastos','data-source="mobileTemplates" data-title="Plantillas"')+row('expense','Auditoría','data-source="auditBody" data-title="Auditoría"')+row('payments','Configurar QR','data-source="qrAdminPanel" data-title="QR de pago"')+row('users','Familias del encargado','data-source="payerFamilyConfigPanel" data-title="Familias del encargado"')+row('expense','Respaldos','data-click="backupBtn"'):'')+'<h3>Apariencia</h3>'+row('settings','Tamaño de texto','data-source="view-settings" data-title="Configuración"')+'<h3>Información</h3><div class="setting-row"><span>Versión de la app</span><small>4.1 Android</small></div>'+row('calendar','Actualizar datos','data-click="refreshBtn"')+(['sysadmin','payer'].includes(s.me.role)?row('payments','Registrar pago','data-go="newPayment"')+row('expense','Plantillas','data-source="mobileTemplates" data-title="Plantillas"'):'')+row('logout','Cerrar sesión','data-logout="1"');}
 function more(s){if(s.me.role==='restaurant')return profileCard(s)+row('expense','Mis servicios','data-go="home"')+row('settings','Mi cuenta','data-go="settings"')+row('calendar','Actualizar datos','data-click="refreshBtn"')+row('logout','Cerrar sesión','data-logout="1"');let list=[['home','Inicio','home'],['payments',s.me.role==='family'?'Mis pagos':'Revisar pagos','payments'],['dues','Mis cuotas','expense'],['cleaning','Limpieza','cleaning'],['notices','Avisos','bell'],['proposals','Propuestas y votación','expense'],['settings','Configuración','settings']];if(s.me.role!=='family')list.splice(2,1,['expenses','Gastos','expense'],['balances','Deudas por familia','users'],['reports','Reportes','chart']);if(s.me.role==='sysadmin')list.push(['users','Usuarios','users']);return profileCard(s)+list.map(([v,n,k])=>row(k,n,`data-go="${v}"`)).join('')+row('calendar','Actualizar datos','data-click="refreshBtn"')+(['sysadmin','payer'].includes(s.me.role)?row('payments','Registrar pago','data-go="newPayment"')+row('expense','Plantillas','data-source="mobileTemplates" data-title="Plantillas"'):'')+row('logout','Cerrar sesión','data-logout="1"');}
 function initials(n){return String(n||'CF').trim().split(/\s+/).slice(0,2).map(x=>x[0]).join('').toUpperCase();}
 function profileCard(s){return `<div class="settings-profile"><span class="avatar">${esc(initials(s.me.display_name))}</span><div><strong>${esc(s.me.display_name)}</strong><br><small>${esc(roles[s.me.role]||s.me.role)}</small></div></div>`;}
 const titles={home:'',payments:'Mis pagos',expenses:'Gastos de la casa',dues:'Mis cuotas',balances:'Deudas por familia',cleaning:'Turnos de limpieza',notices:'Avisos de la casa',users:'Usuarios',reports:'Reportes',settings:'Mi cuenta',more:'Tu espacio'};
 const descriptions={payments:'Tu historial, comprobantes y estados de pago.',expenses:'Cada gasto del hogar, en un solo lugar.',dues:'Consulta tus cuotas y el detalle de tus pagos.',balances:'Un resumen claro para cada familia.',cleaning:'Un hogar cuidado entre todos.',notices:'Lo que pasa en casa, siempre a mano.',users:'Las personas que hacen de esta casa un hogar.',reports:'Entiende cómo se distribuyen los gastos.',settings:'Tu perfil, tus preferencias y la administración del hogar.',more:'Todo lo que necesitas, a un toque.'};
 function render(){
  const s=state();
  if(!s?.me)return;
  if(s.me.role==='restaurant'&&!['home','settings','more'].includes(view))view='home';
  if(s.me.role==='family'&&['expenses','users','balances','reports'].includes(view))view='home';
  if(s.me.role!=='sysadmin'&&view==='users')view='home';
  document.querySelectorAll('.center-screen').forEach(x=>x.classList.add('hidden'));
  $('mobileApp').hidden=false;
  const unread=s.D.notifications.some(n=>!n.read_at);
  $('mobileHeader').innerHTML=view==='home'?`<div class="header-brand"><img src="logo.svg" alt=""><div><strong>Casa Familias</strong><small>TU HOGAR, EN ORDEN</small></div></div><div class="header-actions"><button data-go="notices" class="icon-button" aria-label="Ver avisos" ${s.me.role==='restaurant'?'hidden':''}>${icon('bell')}${unread?'<span class="notification-dot"></span>':''}</button><button data-go="settings" class="avatar" aria-label="Abrir mi cuenta">${esc(initials(s.me.display_name))}</button></div>`:`<button data-go="home" class="back-button" aria-label="Volver al inicio">${icon('back')}</button><h2>${s.me.role!=='family'&&view==='payments'?'Revisión de pagos':titles[view]||''}</h2><button data-go="settings" class="avatar" aria-label="Abrir mi cuenta">${esc(initials(s.me.display_name))}</button>`;
  const screens={home,payments,expenses,dues,balances,cleaning,notices,users,reports,settings,more};
  const demoBar=demo?`<div class="demoBanner"><span><strong>Modo demostración</strong><span>Datos de ejemplo</span></span><div class="demoRoles">${[['family','Familia'],['payer','Encargado'],['sysadmin','Admin']].map(([role,label])=>`<button data-role="${role}" aria-pressed="${s.me.role===role}">${label}</button>`).join('')}</div></div>`:'';
  const description=view!=='home'?`<p class="page-heading">${esc(s.me.role!=='family'&&view==='payments'?'Revisa cada comprobante y mantén las cuentas al día.':descriptions[view]||'')}</p>`:'';
  $('mobileContent').innerHTML=demoBar+description+(screens[view]||home)(s);
  const nav=s.me.role==='restaurant'?[['home','Servicios','expense'],['settings','Mi cuenta','users'],['more','Más','more']]:s.me.role==='family'?[['home','Inicio','home'],['payments','Pagos','payments'],['cleaning','Limpieza','cleaning'],['more','Más','more']]:[['home','Inicio','home'],['expenses','Gastos','expense'],['payments','Pagos','payments'],...(s.me.role==='sysadmin'?[['cleaning','Limpieza','cleaning']]:[]),['more','Más','more']];
  const active=nav.some(([v])=>v===view)?view:'more';
  $('mobileNav').innerHTML=nav.map(([v,t,k])=>`<button data-go="${v}" class="${active===v?'active':''}" ${active===v?'aria-current="page"':''}>${icon(k)}<span>${t}</span></button>`).join('');
  bind();
 }
 function go(v){closeSource(false);const s=state();if(!s?.me)return;if(s.me.role==='restaurant'&&!['home','settings','more'].includes(v))return;if(v==='newPayment'){if(demo){showDemoForm('Registrar pago');return;}openSource('paymentForm','Registrar pago');return;}if(v==='newExpense'){if(demo){showDemoForm('Registrar gasto');return;}openSource('expenseCreatePanel','Registrar gasto');return;}if(v==='proposals'){if(demo){modal('<h2>Propuestas y votación</h2><p>No hay propuestas abiertas.</p>');return;}openSource('view-proposals','Propuestas y votación');return;}
 if(s.me.role==='family'&&['expenses','users','balances','reports'].includes(v))return;if(s.me.role!=='sysadmin'&&v==='users')return;view=v;filter=['users','expenses'].includes(v)?'all':'pending';render();window.scrollTo(0,0);}
 function bind(){document.querySelectorAll('#mobileApp [data-go]').forEach(b=>b.onclick=()=>go(b.dataset.go));document.querySelectorAll('#mobileApp [data-role]').forEach(b=>b.onclick=()=>{demoRole=b.dataset.role;go('home');});document.querySelectorAll('[data-month]').forEach(b=>b.onclick=()=>{const d=new Date(month+'-02T12:00:00');d.setMonth(d.getMonth()+Number(b.dataset.month));month=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');if(!demo){$('expenseMonthFilter').value=month;$('reportMonth').value=month;}render();});document.querySelectorAll('#mobileApp [data-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.filter;render();});document.querySelectorAll('[data-report]').forEach(b=>b.onclick=()=>{reportMode=b.dataset.report;render();});document.querySelectorAll('#mobileApp [data-source]').forEach(b=>b.onclick=()=>openSource(b.dataset.source,b.dataset.title));document.querySelectorAll('#mobileApp [data-click]').forEach(b=>b.onclick=()=>{if(demo){notify('Disponible con tu cuenta real');return;}if(['exportPdfBtn','exportXlsxBtn'].includes(b.dataset.click))$('reportMonth').value=month;$(b.dataset.click)?.click();});document.querySelectorAll('[data-pay]').forEach(b=>b.onclick=()=>detailPayment(b.dataset.pay));document.querySelectorAll('[data-turn]').forEach(b=>b.onclick=()=>detailTurn(b.dataset.turn));document.querySelectorAll('[data-debt]').forEach(b=>b.onclick=()=>{if(!demo){$('paymentShare').value=b.dataset.debt;$('paymentShare').dispatchEvent(new Event('change'));}go('newPayment');});document.querySelectorAll('[data-family]').forEach(b=>b.onclick=()=>modal(`<h2>${esc(family(Number(b.dataset.family)))}</h2><p>Deuda pendiente</p><strong class="big-number">${money(debtFamily(Number(b.dataset.family)))}</strong>`));document.querySelectorAll('[data-expense]').forEach(b=>b.onclick=()=>{const e=state().D.expenses.find(x=>x.id===b.dataset.expense);modal(`<h2>${esc(e.concept)}</h2><strong class="big-number">${money(e.total_amount)}</strong><p>${esc(e.justification||'')}</p><p>${esc(e.expense_date)}</p>${!demo?CF.receiptBtn(e.receipt_path):''}${!demo?'<button id="expenseComments" class="ghost">Comentarios</button>':''}`);if(!demo){CF.bindReceiptButtons();$('expenseComments').onclick=()=>CF.openComments(e.id);}});document.querySelectorAll('[data-user]').forEach(b=>b.onclick=()=>{const u=state().D.profiles.find(x=>x.id===b.dataset.user);modal(`<h2>${esc(u.display_name)}</h2><p>${esc(u.contact_email)}</p><p>${esc(roles[u.role])}</p>`);});document.querySelectorAll('[data-notice]').forEach(b=>b.onclick=()=>{const n=state().D.notices.find(x=>x.title===b.dataset.notice);modal(`<h2>${esc(n.title)}</h2><p>${esc(n.body)}</p>`);});document.querySelectorAll('[data-logout]').forEach(b=>b.onclick=()=>demo?location.href='index.html':CF.logout());}
 function notify(t){$('toast').textContent=t;$('toast').classList.remove('hidden');setTimeout(()=>$('toast').classList.add('hidden'),3500);}
 function modal(html){$('modalContent').innerHTML=html;$('modal').classList.remove('hidden');$('modalClose').onclick=()=>$('modal').classList.add('hidden');}
 function detailPayment(id){selectedPay=id;const s=state(),p=s.D.payments.find(x=>x.id===id);const manager=['payer','sysadmin'].includes(s.me.role);modal(`<h2>Detalle de comprobante</h2>${item('payments','orange',family(p.family_id),formatDate(p.paid_at),`<strong>${money(p.amount)}</strong>${badge(p.status)}`)}<p>${esc(p.note||'Sin notas')}</p><div id="receiptDetail"></div>${manager&&p.status==='pending'?'<div class="row"><button id="approvePay" class="primary" style="background:var(--primary);flex:1">✓ Aprobar</button><button id="rejectPay" class="ghost" style="color:var(--red);flex:1">× Rechazar</button></div>':''}`);if(!demo){if(p.receipt_path)CF.receiptURL(p.receipt_path).then(url=>{if(selectedPay===id&&$('receiptDetail')){const img=document.createElement('img');img.src=url;img.alt='Comprobante de pago';img.className='receiptPreview';$('receiptDetail').append(img);}}).catch(()=>{$('receiptDetail').textContent='No se pudo cargar el comprobante';});else $('receiptDetail').textContent='Este pago no tiene foto adjunta';}for(const [button,approve] of [['approvePay',true],['rejectPay',false]])if($(button))$(button).onclick=async()=>{if(demo){notify('Demostración: no se modifican datos');return;}const old=document.querySelector(`#paymentsBody .review-pay[data-id="${CSS.escape(id)}"][data-ok="${approve?'1':'0'}"]`);if(old){await old.onclick();$('modal').classList.add('hidden');render();}else notify('Actualiza los datos e inténtalo otra vez');};}
 function detailTurn(id){const s=state(),t=s.D.cleaning.find(x=>x.id===id),can=s.me.role==='sysadmin'||s.me.family_id===t.family_id;modal(`<h2>Mi turno</h2><h3>${esc(formatDate(t.turn_date,{day:'numeric',month:'long',year:'numeric'}))}</h3>${badge(t.status==='done'?'done':'active')}<h3>Tareas</h3><div class="card">${(t.checklist||[]).map(x=>`<label class="checkrow"><input type="checkbox" ${t.status==='done'?'checked disabled':''}>${esc(x)}</label>`).join('')}</div><p>${esc(t.note||'')}</p>${can&&t.status==='pending'?'<button class="primary" id="completeTurn">Marcar como completado</button><button class="ghost" id="swapTurn">Solicitar intercambio</button>':''}`);if($('completeTurn'))$('completeTurn').onclick=()=>{if(demo){notify('Demostración: no se modifican datos');return;}if([...$('modalContent').querySelectorAll('input[type=checkbox]')].some(x=>!x.checked)){notify('Marca las tareas realizadas antes de completar');return;}CF.openCompleteCleaning(id);};if($('swapTurn'))$('swapTurn').onclick=()=>demo?notify('Disponible con tu cuenta real'):CF.openSwap(id);}
 function openSource(id,title){if(state()?.me?.role==='restaurant'&&!['view-restaurant','view-settings','passwordForm'].includes(id))return;if(demo){showDemoForm(title);return;}closeSource(false);let node=$(id);if(!node){notify('Esta función no está disponible en este momento');return;}const s=state();if(['userForm','view-admin','qrAdminPanel','periodPanel','payerFamilyConfigPanel','auditBody','cleaningCreatePanel'].includes(id)&&s.me.role!=='sysadmin')return;
 if(node.tagName==='TBODY')node=node.closest('table');if(id==='paymentForm'){node=node.parentElement;}const marker=document.createComment('presentation mount');node.parentNode.insertBefore(marker,node);borrowed={node,marker,hidden:node.classList.contains('hidden'),style:node.style.display};node.classList.remove('hidden');node.style.display='block';node.classList.add('source-host');if(node.tagName==='DETAILS')node.open=true;node.querySelectorAll('details').forEach(d=>d.open=true);$('sourceTitle').textContent=title||'Detalle';$('sourceContent').append(node);$('sourceOverlay').hidden=false;
 }
 function closeSource(refresh=true){if(borrowed){let {node,marker,hidden,style}=borrowed;marker.replaceWith(node);node.classList.toggle('hidden',hidden);node.style.display=style;node.classList.remove('source-host');borrowed=null;}$('sourceOverlay').hidden=true;if(refresh)render();}
 function back(){if(!$('modal').classList.contains('hidden')){$('modal').classList.add('hidden');return true;}if(borrowed||!$('sourceOverlay').hidden){closeSource();return true;}if(view!=='home'){go('home');return true;}return false;}
 function showDemoForm(title){modal(`<h2>${esc(title)}</h2><label>Concepto<input value="Luz"></label><label>Monto (Bs)<input type="number" value="200"></label><label>Fecha<input type="date" value="2026-10-14"></label><label>Comprobante<input type="file" accept="image/*"></label><label>Notas (opcional)<textarea placeholder="Añade una nota..."></textarea></label><button class="primary" onclick="Mobile.notify('Vista de demostración: usa tu cuenta para guardar')">Guardar</button>`);}
 window.addEventListener('cf:update',()=>{if(!$('sourceOverlay').hidden)return;render();});
 document.addEventListener('DOMContentLoaded',()=>{
  document.querySelectorAll('.brand-logo').forEach(image=>image.src='logo.svg');
  $('loginForm').querySelector('.primary').textContent='Entrar a mi cuenta';
  const password=$('loginPassword'),toggle=$('toggleLoginPassword');
  toggle.innerHTML=icon('eye');
  toggle.onclick=()=>{
   const visible=password.type==='password';
   password.type=visible?'text':'password';
   toggle.setAttribute('aria-pressed',String(visible));
   toggle.setAttribute('aria-label',visible?'Ocultar contraseña':'Mostrar contraseña');
   toggle.innerHTML=icon(visible?'eyeOff':'eye');
  };
  $('modalClose').onclick=()=>$('modal').classList.add('hidden');
  const settingIcons={secure:'lock',accessibility:'settings',app:'home',help:'bell'};
  document.querySelectorAll('.setting-icon').forEach(node=>{
   const key=Object.keys(settingIcons).find(key=>node.classList.contains(key));
   if(key)node.innerHTML=icon(settingIcons[key]);
  });
  if(window.AndroidFiles)$('settingsInstallBtn')?.classList.add('hidden');
  const sessionHelp=document.querySelector('.settings-actions-card .setting-heading p');
  if(sessionHelp)sessionHelp.textContent='Administra el acceso a tu cuenta en este dispositivo.';
  let activeOverlay=null,previousFocus=null;
  const syncOverlay=()=>{
   if(!window.document?.body)return;
   const next=!$('modal').classList.contains('hidden')?$('modal'):!$('sourceOverlay').hidden?$('sourceOverlay'):null;
   document.body.classList.toggle('overlay-open',Boolean(next));
   if(next===activeOverlay)return;
   if(next){
    if(!activeOverlay)previousFocus=document.activeElement;
    const focusTarget=next.querySelector('input:not([type=hidden]),select,textarea,button');
    focusTarget?.focus({preventScroll:true});
   }else if(previousFocus?.isConnected){previousFocus.focus({preventScroll:true});}
   activeOverlay=next;
  };
  const observer=new MutationObserver(syncOverlay);
  observer.observe($('modal'),{attributes:true,attributeFilter:['class']});
  observer.observe($('sourceOverlay'),{attributes:true,attributeFilter:['hidden']});
  window.addEventListener('pagehide',event=>{if(!event.persisted)observer.disconnect();});
  document.addEventListener('keydown',event=>{
   if(event.key==='Escape'){
    if(!$('modal').classList.contains('hidden')){
     $('modal').classList.add('hidden');event.preventDefault();event.stopImmediatePropagation();return;
    }
    if(!$('sourceOverlay').hidden){
     closeSource();event.preventDefault();event.stopImmediatePropagation();return;
    }
   }
   if(event.key!=='Tab'||!activeOverlay)return;
   const focusable=[...activeOverlay.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]')].filter(node=>node.getClientRects().length&&!node.hidden);
   if(!focusable.length)return;
   const first=focusable[0],last=focusable[focusable.length-1];
   if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
   else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  },true);
  if(demo){month='2026-10';render();}
 // Convert generated Blob downloads into Android's native document picker.
 const click=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){if(window.AndroidFiles&&this.href.startsWith('blob:')){fetch(this.href).then(r=>r.blob()).then(blob=>{const reader=new FileReader();reader.onload=()=>AndroidFiles.save(this.download||'CasaFamilias',blob.type||'application/octet-stream',String(reader.result).split(',')[1]);reader.readAsDataURL(blob);});return;}return click.call(this);};
 });return {go,render,closeSource,openSource,back,notify,isDemo:()=>demo};
})();
