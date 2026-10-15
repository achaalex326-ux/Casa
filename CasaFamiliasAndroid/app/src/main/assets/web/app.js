
const cfg=window.APP_CONFIG||{};
const configured=cfg.SUPABASE_URL&&cfg.SUPABASE_PUBLISHABLE_KEY&&!cfg.SUPABASE_URL.includes("PEGA_AQUI")&&!cfg.SUPABASE_PUBLISHABLE_KEY.includes("PEGA_AQUI");
if(window.Mobile?.isDemo()){}
else if(!configured){document.getElementById("setupScreen").classList.remove("hidden");}
else if(!window.supabase){showStartupError();}
else{boot().catch(showStartupError);}
function showStartupError(){
 const screen=document.getElementById("setupScreen");
 document.getElementById("mobileApp").hidden=true;
 document.getElementById("loginScreen").classList.add("hidden");
 document.getElementById("resetScreen").classList.add("hidden");
 screen.classList.remove("hidden");
 screen.querySelector("h2").textContent="No se pudo iniciar la aplicación";
 screen.querySelector("p").textContent=navigator.onLine===false?"No tienes conexión. Conéctate a internet y vuelve a intentarlo.":"Vuelve a abrir la aplicación. Si el problema continúa, instala la versión más reciente.";
 if(!screen.querySelector("[data-startup-retry]")){
  const retry=document.createElement("button");retry.className="primary";retry.dataset.startupRetry="true";retry.textContent="Reintentar";retry.onclick=()=>location.reload();screen.querySelector(".panel").append(retry);
 }
}

async function boot(){
  let recoveryMode=new URLSearchParams(location.hash.slice(1)).get("type")==="recovery";
  const sb=window.supabase.createClient(cfg.SUPABASE_URL,cfg.SUPABASE_PUBLISHABLE_KEY);
  const $=id=>document.getElementById(id);
  const money=n=>`${Number(n||0).toFixed(2)} Bs`;
  const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;};
  const monthNow=()=>today().slice(0,7);
  const roleName={sysadmin:"Ingeniero de Sistemas",payer:"Encargado de pagos",family:"Familia / usuario",restaurant:"Restaurante"};
  const iconPaths={"dashboard": "M3 10 12 3l9 7v11H3Z M9 21v-8h6v8", "restaurant": "M3 3h18v6H3Z M5 9v12h14V9 M9 21v-7h6v7", "expenses": "M6 3h12v18l-3-2-3 2-3-2-3 2Z M9 8h6 M9 12h6", "payments": "M3 5h18v14H3Z M3 10h18 M6 15h4", "balances": "M3 21v-3a5 5 0 0 1 10 0v3 M16 14a5 5 0 0 1 5 4v3 M8 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M17 3a4 4 0 0 1 0 8", "cleaning": "M4 5h16v16H4Z M8 3v4 M16 3v4 M4 10h16 M8 15l3 3 5-5", "proposals": "M5 3h14v18H5Z M8 8h8 M8 12h8 M8 16h5", "notices": "M4 10v5h4l10 4V6L8 10Z M8 15l2 6", "reports": "M4 3v18h17 M8 16v-4 M13 16V8 M18 16V5", "admin": "M5 4v16 M12 4v16 M19 4v16 M2 8h6 M9 15h6 M16 9h6", "settings": "M5 4v16 M12 4v16 M19 4v16 M2 8h6 M9 15h6 M16 9h6", "more": "M4 12h2 M11 12h2 M18 12h2"};
  const navIcon=Object.fromEntries(Object.entries(iconPaths).map(([key,path])=>[key,`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`]));
  const catName={basic:"Servicio básico",external:"Gasto externo / arreglo"};
  const utilityName={water:"Agua",electricity:"Luz",gas:"Gas",wifi:"WiFi"};
  let session=null,me=null,deferredPrompt=null,realtimeChannel=null,reloadTimer=null,syncTimer=null,reloadInFlight=null,familyDebts=[];
  const emptyData=()=>({families:[],profiles:[],periods:[],templates:[],expenses:[],monthly:[],shares:[],credits:[],payments:[],cleaning:[],swaps:[],proposals:[],votes:[],notices:[],comments:[],notifications:[],audit:[],qr:[],externalDebts:[],utilityBaselines:[],utilityCredits:[]});
  let D=emptyData();
  let qrDraft=false,familyNameDraft=false,assignmentDraft=false,qrPreviewURL=null;

  ["expenseDate","paymentDate","cleaningDate"].forEach(id=>$(id).value=today());
  $("expenseDueDate").value=today();
  $("basicMonth").value=monthNow();
  $("basicDueDate").value=today();
  $("expenseMonthFilter").value=monthNow();
  $("reportMonth").value=monthNow();
  $("closeMonth").value=monthNow();
  $("reportYear").value=new Date().getFullYear();

  window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e;$("installBtn")?.classList.remove("hidden");$("settingsInstallBtn")?.classList.remove("hidden");});
  async function promptInstall(){
    if(!deferredPrompt){
      toast("Para instalarla, abre el menú del navegador y elige “Instalar Casa Familias”.");
      return;
    }
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    deferredPrompt=null;
    $("installBtn")?.classList.add("hidden");
    $("settingsInstallBtn")?.classList.add("hidden");
  }
  $("installBtn").onclick=promptInstall;
  $("settingsInstallBtn")?.addEventListener("click",promptInstall);

  const largeTextKey="cf_large_text",fontSizeKey="cf_font_size";
  const savedSize=localStorage.getItem(fontSizeKey)||(localStorage.getItem(largeTextKey)==="1"?"large":"normal");
  function applyFontSize(size,save=true){
    const value=["normal","large","extra"].includes(size)?size:"normal";
    document.documentElement.classList.toggle("large-text",value!=="normal");
    document.documentElement.classList.toggle("extra-large-text",value==="extra");
    if(save){localStorage.setItem(fontSizeKey,value);localStorage.setItem(largeTextKey,value!=="normal"?"1":"0");}
    document.querySelectorAll('input[name="fontSize"]').forEach(r=>{r.checked=r.value===value;});
    const b=$("largeTextBtn");
    if(b){b.textContent=value==="normal"?"Texto grande":"Texto normal";b.setAttribute("aria-pressed",String(value!=="normal"));}
    return value;
  }
  applyFontSize(savedSize,false);
  if($("largeTextBtn"))$("largeTextBtn").onclick=()=>applyFontSize(document.documentElement.classList.contains("large-text")?"normal":"large");
  if($("saveFontBtn"))$("saveFontBtn").onclick=()=>{const selected=document.querySelector('input[name="fontSize"]:checked')?.value||"normal";applyFontSize(selected);toast("Tamaño de letra guardado en este dispositivo.");};

  function setMenuOpen(open){
    const side=$("appSidebar"),backdrop=$("menuBackdrop"),toggle=$("menuToggle");
    if(!side)return;
    side.classList.toggle("menu-open",open);
    backdrop?.classList.toggle("hidden",!open);
    toggle?.setAttribute("aria-expanded",String(open));
    document.body.classList.toggle("menu-is-open",open);
  }
  $("menuToggle")?.addEventListener("click",()=>setMenuOpen(true));
  $("profileQuick")?.addEventListener("click",()=>{
    const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="settings");
    if(navButton)showView("settings","Configuración",navButton);
  });
  function setMoreSheetOpen(open){
    const sheet=$("moreSheet"),backdrop=$("moreSheetBackdrop");
    sheet?.classList.toggle("hidden",!open);
    backdrop?.classList.toggle("hidden",!open);
    document.body.classList.toggle("more-sheet-is-open",open);
  }
  $("closeMoreSheet")?.addEventListener("click",()=>setMoreSheetOpen(false));
  $("moreSheetBackdrop")?.addEventListener("click",()=>setMoreSheetOpen(false));
  $("closeMenuBtn")?.addEventListener("click",()=>setMenuOpen(false));
  $("menuBackdrop")?.addEventListener("click",()=>setMenuOpen(false));
  document.addEventListener("keydown",e=>{if(e.key==="Escape"){setMenuOpen(false);setMoreSheetOpen(false);$("modal").classList.add("hidden");$("notificationDrawer").classList.add("hidden");}});

  let toastTimer;
  function toast(msg){clearTimeout(toastTimer);$("toast").textContent=msg;$("toast").classList.remove("hidden");toastTimer=setTimeout(()=>$("toast").classList.add("hidden"),3800);}
  async function edgeErrorMessage(error,fallback="No se pudo completar la operación."){
    if(error?.context&&typeof error.context.clone==="function"){
      try{
        const body=await error.context.clone().json();
        if(body?.error)return String(body.error);
        if(body?.message)return String(body.message);
      }catch(_){/* La respuesta puede no ser JSON. */}
    }
    return error?.message||fallback;
  }
  function esc(v){return String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]));}
  function familyName(id){return D.families.find(f=>f.id===id)?.name||`Familia ${id}`;}
  function currentQr(){return D.qr?.[0]||null;}
  function paymentMethodLabel(v){return v==="cash"?"Efectivo":"QR";}
  function expenseById(id){return D.expenses.find(x=>x.id===id);}
  function shareById(id){return D.shares.find(x=>x.id===id);}
  function creditForFamily(id){return Number(D.credits.find(c=>c.family_id===id)?.balance||0);}
  function payerFamilyIds(){
    const payer=D.profiles.find(p=>p.role==="payer");
    return payer?[...new Set([payer.family_id,payer.second_family_id].filter(Boolean))]:[];
  }
  function balanceForFamily(id){
    return D.shares.filter(s=>s.family_id===id).reduce((a,s)=>a+shareBalance(s),0);
  }
  function approvedPaid(shareId){return D.payments.filter(p=>p.share_id===shareId&&p.status==="approved").reduce((a,p)=>a+Number(p.applied_amount||0),0);}
  function pendingAmount(shareId){return D.payments.filter(p=>p.share_id===shareId&&p.status==="pending").reduce((a,p)=>a+Number(p.amount||0),0);}
  function shareBalance(s){return Math.max(Number(s.amount_due)-approvedPaid(s.id),0);}
  function periodClosed(date){const m=date?.slice(0,7)+"-01";return D.periods.some(p=>p.month_start===m&&p.status==="closed");}
  function statusForShare(s){
    const bal=shareBalance(s),paid=approvedPaid(s.id),review=pendingAmount(s.id),e=expenseById(s.expense_id);
    if(bal<=0.005)return ["Pagado","ok"];
    if(review>0)return ["En revisión","review"];
    if(paid>0)return ["Pago parcial","partial"];
    if(e?.due_date<today())return ["Vencido","overdue"];
    return ["Pendiente","pending"];
  }
  function paymentMonthLabel(value){
    if(!value)return "";
    const raw=String(value).slice(0,10),month=raw.slice(0,7);
    if(!/^\d{4}-\d{2}$/.test(month))return raw;
    const d=new Date(`${month}-01T00:00:00`);
    if(Number.isNaN(d.getTime()))return raw;
    const label=d.toLocaleDateString("es-BO",{month:"long",year:"numeric"});
    return label.charAt(0).toUpperCase()+label.slice(1);
  }
  function paymentStatusInfo(info){
    if(info?.statusLabel)return [info.statusLabel,info.statusClass||"pending"];
    const balance=Number(info?.balance||0);
    if(balance<=0.005)return ["Pagado","ok"];
    if(Number(info?.pending||0)>0)return ["En revisión","review"];
    if(Number(info?.amountPaid||0)>0)return ["Pago parcial","partial"];
    return ["Pendiente","pending"];
  }
  function selectedPaymentInfo(){
    const id=$("paymentShare")?.value;
    if(!id)return null;
    if(me?.role==="family"){
      const d=familyDebts.find(x=>String(x.share_id)===String(id));
      if(!d)return null;
      const statusMap={paid:["Pagado","ok"],review:["En revisión","review"],partial:["Pago parcial","partial"],overdue:["Vencido","overdue"]};
      const status=statusMap[d.debt_status]||["Pendiente","pending"];
      return {id:d.share_id,concept:d.concept||"Cuota mensual",balance:Number(d.balance||0),amountDue:Number(d.amount_due||d.balance||0),amountPaid:Number(d.amount_paid||0),pending:Number(d.pending_submitted||0),dueDate:d.due_date,monthStart:d.month_start,statusLabel:status[0],statusClass:status[1]};
    }
    const s=shareById(id),e=s?expenseById(s.expense_id):null;
    if(!s)return null;
    const status=statusForShare(s);
    return {id:s.id,concept:e?.concept||"Cuota mensual",balance:shareBalance(s),amountDue:Number(s.amount_due||0),amountPaid:approvedPaid(s.id),pending:pendingAmount(s.id),dueDate:e?.due_date,monthStart:e?.expense_date,statusLabel:status[0],statusClass:status[1]};
  }
  function renderPaymentSelectionCard(){
    const info=selectedPaymentInfo(),card=$("paymentDebtCard");
    if(!card)return;
    const count=[...($("paymentShare")?.options||[])].filter(o=>o.value).length;
    if($("paymentDebtCount"))$("paymentDebtCount").textContent=count?`${count} deuda${count===1?"":"s"} pendiente${count===1?"":"s"}`:"Sin deudas pendientes";
    if(!info){
      if($("paymentSelectedConcept"))$("paymentSelectedConcept").textContent="No hay una deuda seleccionada";
      if($("paymentSelectedAmount"))$("paymentSelectedAmount").textContent="0,00 Bs";
      if($("paymentSelectedDue"))$("paymentSelectedDue").textContent="Cuando exista una deuda aparecerá aquí.";
      if($("paymentSelectedStatus")){$("paymentSelectedStatus").textContent="Sin deuda";$('paymentSelectedStatus').className="payment-status-chip ok";}
      renderPaymentAllocationInfo();
      return;
    }
    if($("paymentSelectedConcept"))$("paymentSelectedConcept").textContent=info.concept;
    if($("paymentSelectedAmount"))$("paymentSelectedAmount").textContent=money(info.balance);
    if($("paymentSelectedDue"))$("paymentSelectedDue").textContent=info.dueDate?`Vence el ${info.dueDate}`:(info.monthStart?paymentMonthLabel(info.monthStart):"Deuda pendiente");
    const status=paymentStatusInfo(info);
    if($("paymentSelectedStatus")){$("paymentSelectedStatus").textContent=status[0];$("paymentSelectedStatus").className=`payment-status-chip ${status[1]}`;}
    renderPaymentAllocationInfo();
  }
  function renderPaymentAllocationInfo(){
    const box=$("paymentAllocationInfo"),info=selectedPaymentInfo();
    if(!box)return;
    const amount=Number($("paymentAmount")?.value||0),balance=Number(info?.balance||0);
    if(!info||!amount||amount<=0){box.innerHTML="";return;}
    const label=esc(info.concept||"la deuda seleccionada");
    if(amount>balance+0.005){
      const credit=amount-balance;
      box.innerHTML=`<strong>Así se aplicará:</strong> ${money(balance)} a ${label} · ${money(credit)} quedará como crédito para la próxima cuota.`;
    }else if(amount<balance-0.005){
      box.innerHTML=`Se aplicarán ${money(amount)} a ${label}. Después quedarán ${money(balance-amount)} pendientes.`;
    }else{
      box.innerHTML=`Se cubrirá completamente ${label} con ${money(amount)}.`;
    }
  }
  function receiptBtn(path){return path?`<button class="small-btn receipt-btn" data-path="${encodeURIComponent(path)}">Ver foto</button>`:"-";}
  function bindReceiptButtons(){
    document.querySelectorAll(".receipt-btn").forEach(b=>b.onclick=async()=>{
      const path=decodeURIComponent(b.dataset.path);
      const {data,error}=await sb.storage.from(cfg.RECEIPTS_BUCKET).createSignedUrl(path,300);
      if(error)return toast("No se pudo abrir el comprobante.");
      document.getElementById("modalContent").innerHTML="<h2>Comprobante</h2>";const img=document.createElement("img");img.src=data.signedUrl;img.alt="Comprobante";img.className="receipt-image";document.getElementById("modalContent").append(img);document.getElementById("modal").classList.remove("hidden");
    });
  }
  async function upload(file){
    if(!file)return null;
    const safe=file.name.replace(/[^a-zA-Z0-9._-]/g,"_");
    const path=`${session.user.id}/${Date.now()}-${safe}`;
    const {error}=await sb.storage.from(cfg.RECEIPTS_BUCKET).upload(path,file,{upsert:false,contentType:file.type||"image/jpeg"});
    if(error)throw error;
    return path;
  }

  $("loginForm").onsubmit=async e=>{
    e.preventDefault();const button=e.currentTarget.querySelector('[type="submit"]');
    if(button.disabled)return;
    button.disabled=true;button.textContent="Ingresando…";$("loginError").textContent="";
    try{
      const {data,error}=await sb.auth.signInWithPassword({email:$("loginEmail").value.trim(),password:$("loginPassword").value});
      if(error){$("loginError").textContent="No se pudo ingresar. Revisa tu correo y contraseña.";return;}
      session=data.session;await enter(data.user);
    }catch(error){$("loginError").textContent="No se pudo conectar. Revisa internet y vuelve a intentar.";}
    finally{button.disabled=false;button.textContent="Entrar a mi cuenta";}
  };
  $("forgotBtn").onclick=async()=>{
    const email=$("loginEmail").value.trim();
    if(!email)return toast("Escribe primero tu correo.");
    if(!$("loginEmail").checkValidity())return toast("Revisa que el correo esté bien escrito.");
    const button=$("forgotBtn");
    if(button.disabled)return;
    const redirectTo=window.AndroidFiles?"casafamilias://auth":location.origin+location.pathname;
    button.disabled=true;
    try{
      const {error}=await sb.auth.resetPasswordForEmail(email,{redirectTo});
      toast(error?"No se pudo solicitar el enlace. Vuelve a intentarlo.":"Si el correo tiene una cuenta, recibirás un enlace de recuperación.");
    }catch(_){toast("No se pudo conectar. Revisa internet y vuelve a intentar.");}
    finally{button.disabled=false;}
  };
  $("resetForm").onsubmit=async e=>{
    e.preventDefault();
    const button=$("resetForm").querySelector('[type="submit"]');
    if(button.disabled)return;
    const password=$("resetPassword").value;
    $("resetError").textContent="";
    if(password.length<8)return $("resetError").textContent="Usa una contraseña de al menos 8 caracteres.";
    if(password!==$("resetPasswordConfirm").value)return $("resetError").textContent="Las contraseñas no coinciden.";
    button.disabled=true;button.textContent="Guardando…";
    try{
      const {error}=await sb.auth.updateUser({password});
      if(error){$("resetError").textContent="No se pudo guardar la contraseña. Solicita otro enlace si el actual venció.";return;}
      recoveryMode=false;
      $("resetForm").reset();
      const {data,error:sessionError}=await sb.auth.getSession();
      if(sessionError||!data?.session){showAuthScreen("loginScreen");$("loginError").textContent="Contraseña actualizada. Ingresa con tu nueva contraseña.";return;}
      session=data.session;
      await enter(session.user);
      toast("Contraseña actualizada.");
    }catch(_){
      if(recoveryMode)$("resetError").textContent="No se pudo conectar. Revisa internet y vuelve a intentar.";
      else{showAuthScreen("loginScreen");$("loginError").textContent="Vuelve a ingresar para continuar.";}
    }finally{button.disabled=false;button.textContent="Guardar contraseña";}
  };
  $("passwordForm")?.addEventListener("submit",async e=>{
    e.preventDefault();
    const button=$("passwordForm").querySelector('[type="submit"]');
    if(button.disabled)return;
    const password=$("settingsPassword").value;
    const confirmation=$("settingsPasswordConfirm").value;
    $("passwordError").textContent="";
    if(password.length<8)return $("passwordError").textContent="La contraseña debe tener al menos 8 caracteres.";
    if(password!==confirmation)return $("passwordError").textContent="Las contraseñas no coinciden.";
    button.disabled=true;
    try{
      const {error}=await sb.auth.updateUser({password});
      if(error){$("passwordError").textContent="No se pudo cambiar la contraseña. Vuelve a intentarlo.";return;}
      $("passwordForm").reset();
      toast("Contraseña cambiada correctamente.");
    }catch(_){$("passwordError").textContent="No se pudo conectar. Revisa internet y vuelve a intentar.";}
    finally{button.disabled=false;}
  });
  function showAuthScreen(id){
    me=null;
    D=emptyData();familyDebts=[];
    window.Mobile?.closeSource(false);
    $("modal").classList.add("hidden");
    $("mobileApp").hidden=true;
    $("app").classList.add("hidden");
    ["setupScreen","loginScreen","resetScreen"].forEach(screen=>$(screen).classList.toggle("hidden",screen!==id));
  }
  sb.auth.onAuthStateChange((event,s)=>{
    if(event==="PASSWORD_RECOVERY"){
      recoveryMode=true;session=s;
      showAuthScreen("resetScreen");
    }
  });
  let loggingOut=false;
  async function logout(){
    if(loggingOut)return;
    loggingOut=true;
    try{
      const {error}=await sb.auth.signOut();
      if(error)return toast("No se pudo cerrar la sesión. Vuelve a intentarlo.");
      location.reload();
    }catch(_){toast("No se pudo conectar. Revisa internet y vuelve a intentar.");}
    finally{loggingOut=false;}
  }
  $("logoutBtn").onclick=logout;
  $("settingsLogoutBtn")?.addEventListener("click",logout);

  async function enter(user){
    const {data:p,error}=await sb.from("profiles").select("*").eq("id",user.id).single();
    if(error||!p){
      showAuthScreen("loginScreen");
      $("loginError").textContent="La cuenta no tiene un perfil disponible. Pide ayuda al administrador.";
      await sb.auth.signOut();return;
    }
    me=p;
    $("loginScreen").classList.add("hidden");$("resetScreen").classList.add("hidden");$("app").classList.remove("hidden");
    $("app").dataset.role=me.role;
    $("roleText").textContent=roleName[me.role];$("userText").textContent=me.display_name||roleName[me.role];
    $("profileQuick").textContent=(me.display_name||"CF").trim().split(/\s+/).slice(0,2).map(x=>x[0]).join("").toUpperCase();
    if($("settingsName"))$("settingsName").textContent=me.display_name||"Mi cuenta";
    if($("settingsRole"))$("settingsRole").textContent=roleName[me.role]||"Usuario";
    if($("settingsEmail"))$("settingsEmail").textContent=me.contact_email||user.email||"";
    if($("settingsAvatar"))$("settingsAvatar").textContent=(me.display_name||"CF").trim().split(/\s+/).slice(0,2).map(x=>x[0]).join("").toUpperCase();
    buildNav();applyRoleUI();window.dispatchEvent(new Event("cf:update"));
    if(["sysadmin","payer"].includes(me.role)) await loadFamiliesFirst();
    if(["sysadmin","payer"].includes(me.role)) await sb.rpc("generate_due_notifications");
    await loadAll();
    if(me.role==="restaurant"){
      const first=$("nav")?.querySelector("button");
      showView("restaurant","Mis deudas",first);
    }
    setupRealtime();
  }

  function buildNav(){
    let items;
    if(me.role==="restaurant"){
      items=[["restaurant","Mis deudas","Deudas"],["settings","Configuración","Configuración"]];
    }else if(me.role==="family"){
      items=[
        ["dashboard","Mi cuenta","Inicio"],
        ["payments","Pagar / comprobante","Pagos"],
        ["cleaning","Mi limpieza","Limpieza"],
        ["notices","Comunicados","Comunicados"],
        ["settings","Configuración","Configuración"]
      ];
    }else{
      items=[
        ["dashboard","Resumen","Inicio"],
        ["expenses","Registrar gastos","Gastos"],
        ["payments","Aprobar pagos","Pagos"],
        ["balances","Deudas por familia","Saldos"],
        ["cleaning","Turnos de limpieza","Limpieza"],
        ["proposals","Propuestas","Propuestas"],
        ["notices","Avisos","Avisos"],
        ["reports","Reportes","Reportes"]
      ];
      if(me.role==="sysadmin")items.push(["admin","Administración","Administración"]);
      items.push(["settings","Configuración","Configuración"]);
    }
    $("nav").innerHTML=items.map((x,i)=>`<button class="nav-btn ${i===0?"active":""}" data-view="${x[0]}" data-title="${x[2]||x[1]}" aria-label="${x[1]}"><span class="nav-icon" aria-hidden="true">${navIcon[x[0]]||"•"}</span><span class="nav-label">${x[1]}</span></button>`).join("");
    $("nav").querySelectorAll("button").forEach(b=>b.onclick=()=>{showView(b.dataset.view,b.dataset.title,b);setMenuOpen(false);});
    const bottomItems=me.role==="restaurant"
      ?[["restaurant","Inicio"],["more","Más"]]
      :me.role==="family"
        ?[["dashboard","Inicio"],["payments","Mis pagos"],["cleaning","Limpieza"],["notices","Comunicados"],["more","Más"]]
        :me.role==="payer"
          ?[["dashboard","Inicio"],["expenses","Gastos"],["payments","Pagos"],["cleaning","Limpieza"],["more","Más"]]
          :[["dashboard","Inicio"],["expenses","Gastos"],["payments","Pagos"],["cleaning","Limpieza"],["more","Más"]];
    const bottom=$("bottomNav");
    if(bottom){
      bottom.innerHTML=bottomItems.map((x,i)=>`<button class="bottom-nav-btn ${i===0?"active":""}" data-view="${x[0]}" data-title="${x[1]}" type="button"><span class="bottom-nav-icon" aria-hidden="true">${navIcon[x[0]]||"•"}</span><span>${x[1]}</span></button>`).join("");
      bottom.querySelectorAll("button").forEach(b=>b.onclick=()=>{
        if(b.dataset.view==="more"){
          setMoreSheetOpen(true);
          return;
        }
        if(b.dataset.view==="settings"){
          const navBtn=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="settings");
          showView("settings","Configuración",navBtn);
          return;
        }
        const navBtn=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view===b.dataset.view);
        showView(b.dataset.view,b.dataset.title,navBtn);
      });
    }
    const bottomCoreViews=new Set(bottomItems.slice(0,-1).map(x=>x[0]));
    const sheetItems=items.filter(x=>!bottomCoreViews.has(x[0]));
    const sheet=$("moreSheetNav");
    if(sheet){
      sheet.innerHTML=sheetItems.map(x=>`<button class="more-sheet-btn" type="button" data-view="${x[0]}" data-title="${x[2]||x[1]}"><span class="more-sheet-icon" aria-hidden="true">${navIcon[x[0]]||"•"}</span><span>${x[1]}</span></button>`).join("");
      sheet.querySelectorAll("button").forEach(b=>b.onclick=()=>{
        const navBtn=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view===b.dataset.view);
        setMoreSheetOpen(false);
        showView(b.dataset.view,b.dataset.title,navBtn);
      });
    }
    if(items[0])$("pageTitle").textContent=items[0][2]||items[0][1];
    renderQuickActions();
  }
  function showView(id,title,btn){
    const authorized=$("nav").querySelector(`[data-view="${id}"]`);
    if(!authorized||!$(`view-${id}`))return;
    setMoreSheetOpen(false);setMenuOpen(false);
    $("notificationDrawer").classList.add("hidden");
    document.querySelectorAll(".nav-btn").forEach(x=>{if(x.dataset.view===id)x.setAttribute("aria-current","page");else x.removeAttribute("aria-current");});
    document.querySelectorAll(".view").forEach(v=>v.classList.add("hidden"));
    $(`view-${id}`).classList.remove("hidden");$("pageTitle").textContent=title;
    if(btn){document.querySelectorAll(".nav-btn").forEach(x=>x.classList.remove("active"));btn.classList.add("active");}
    document.querySelectorAll(".bottom-nav-btn").forEach(x=>x.classList.toggle("active",x.dataset.view===id));
    if(id==="reports")renderReports();
    if(id==="restaurant")renderRestaurantDebts();
    window.scrollTo({top:0,behavior:"instant"});
    $("pageTitle").focus({preventScroll:true});
  }
  document.querySelectorAll(".view-back-btn").forEach(button=>button.addEventListener("click",()=>{
    const view=button.dataset.backView||"dashboard";
    const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view===view);
    showView(view,view==="dashboard"?"Inicio":(navButton?.dataset.title||"Inicio"),navButton);
  }));
  function renderQuickActions(){
    const box=$("quickActions");
    if(!box||me.role==="restaurant")return;
    const actions=me.role==="family"
      ?[["payments","Pagar mi cuota","💳"],["cleaning","Ver mi limpieza","🧹"],["notices","Leer comunicados","📣"]]
      :me.role==="payer"
        ?[["expenses","Registrar gasto","🧾"],["payments","Aprobar pagos","✓"],["balances","Deudas por familia","👥"]]
        :[["expenses","Registrar gasto","🧾"],["payments","Aprobar pagos","✓"],["balances","Deudas por familia","👥"],["admin","Administración","⚙️"]];
    box.innerHTML=actions.map(([view,title,icon])=>`<button type="button" class="quick-action" data-quick-view="${view}" data-quick-title="${title}"><span class="quick-icon" aria-hidden="true">${icon}</span><span>${title}</span><span class="quick-arrow" aria-hidden="true">→</span></button>`).join("");
    box.querySelectorAll(".quick-action").forEach(b=>b.onclick=()=>{
      const navBtn=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view===b.dataset.quickView);
      showView(b.dataset.quickView,b.dataset.quickTitle,navBtn);
    });
  }
  function cleaningWeekLabel(date){
    if(!date)return "Turno pendiente";
    const start=isoWeekInfo(today()).key,target=isoWeekInfo(String(date).slice(0,10)).key;
    const weeks=Math.round((new Date(`${target}T00:00:00`)-new Date(`${start}T00:00:00`))/604800000);
    if(weeks<0)return "Pendiente atrasado";
    if(weeks===0)return "Esta semana";
    if(weeks===1)return "Próxima semana";
    return `En ${weeks} semanas`;
  }
  function cleaningDateText(date){
    if(!date)return "Fecha por definir";
    const d=new Date(`${String(date).slice(0,10)}T12:00:00`),day=(d.getDay()+6)%7;
    const monday=new Date(d);monday.setDate(d.getDate()-day);
    const sunday=new Date(monday);sunday.setDate(monday.getDate()+6);
    const monthName=x=>x.toLocaleDateString("es-BO",{month:"long"});
    const sameMonth=monday.getMonth()===sunday.getMonth()&&monday.getFullYear()===sunday.getFullYear();
    const range=sameMonth
      ?`${monday.getDate()} al ${sunday.getDate()} de ${monthName(sunday)}`
      :`${monday.getDate()} de ${monthName(monday)} al ${sunday.getDate()} de ${monthName(sunday)}`;
    return `Semana del ${range}`;
  }
  function renderHomeCleaning(){
    const panel=$("homeCleaningPanel"),body=$("homeCleaningBody");
    if(!panel||!body)return;
    if(me.role==="restaurant"){
      panel.classList.add("hidden");
      return;
    }
    panel.classList.remove("hidden");
    const turns=[...D.cleaning].filter(t=>t.status!=="done").sort((a,b)=>String(a.turn_date).localeCompare(String(b.turn_date)));
    const configuredPayers=payerFamilyIds();
    const payerIds=configuredPayers.length?configuredPayers:[5,6];

    if(me.role==="family"){
      const own=turns.filter(t=>t.family_id===me.family_id);
      const next=own[0];
      $("homeCleaningTitle").textContent="Turno de limpieza";
      $("homeCleaningBadge").textContent="Por semanas";
      body.innerHTML=`<div class="home-cleaning-focus">
        <div class="home-cleaning-icon" aria-hidden="true">🧹</div>
        <div><strong>${next?`${cleaningWeekLabel(next.turn_date)}: te toca a ti`:"Turno pendiente de configurar"}</strong><span>${next?cleaningDateText(next.turn_date):"El administrador todavía no asignó una semana."}</span><small>Los turnos se organizan por semanas para toda la casa.</small></div>
      </div>`;
      return;
    }

    if(me.role==="payer"){
      const own=turns.filter(t=>payerIds.includes(t.family_id));
      const next=own[0];
      const timeline=turns.slice(0,5).map((t,i)=>`<div class="cleaning-week-item ${i===0?"current":""}"><span class="cleaning-week-dot"></span><div><strong>${i===0?cleaningWeekLabel(t.turn_date):`Semana ${i+1}`}</strong><small>${esc(familyName(t.family_id))}</small></div></div>`).join("");
      $("homeCleaningTitle").textContent="Limpieza de la familia pagadora";
      $("homeCleaningBadge").textContent="También le toca";
      body.innerHTML=`<div class="home-cleaning-payer"><div class="home-cleaning-payer-top"><div class="home-cleaning-icon" aria-hidden="true">🧹</div><div><strong>${next?`${cleaningWeekLabel(next.turn_date)}: le toca a la familia pagadora`:"Turno pendiente de configurar"}</strong><span>${next?cleaningDateText(next.turn_date):"El administrador todavía no asignó una semana."}</span></div></div><div class="cleaning-week-line">${timeline||`<span class="muted">Todavía no hay turnos asignados.</span>`}</div></div>`;
      return;
    }

    $("homeCleaningTitle").textContent="Calendario de limpieza";
    $("homeCleaningBadge").textContent="Administración semanal";
    const schedule=turns.slice(0,5).map((t,i)=>`<div class="cleaning-admin-row"><div class="home-cleaning-icon small" aria-hidden="true">🧹</div><div><strong>${i===0?cleaningWeekLabel(t.turn_date):i===1?"Próxima semana":`En ${i} semanas`}</strong><span>${esc(familyName(t.family_id))} · ${cleaningDateText(t.turn_date)}</span></div></div>`).join("");
    body.innerHTML=`<div class="home-cleaning-admin"><div>${schedule||`<p class="muted">Todavía no hay turnos asignados.</p>`}</div><button class="small-btn home-cleaning-config" type="button">Configurar turnos</button></div>`;
    $("homeCleaningBody").querySelector(".home-cleaning-config")?.addEventListener("click",()=>{
      const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="cleaning");
      showView("cleaning","Limpieza",navButton);
    });
  }
  function applyRoleUI(){
    const staff=["sysadmin","payer"].includes(me.role);
    const family=me.role==="family";
    const restaurant=me.role==="restaurant";

    $("monthlyServicesPanel").classList.toggle("hidden",!staff);
    $("expenseCreatePanel").classList.toggle("hidden",!staff);
    $("homeCleaningPanel")?.classList.toggle("hidden",!family);
    $("cleaningCreatePanel").classList.toggle("hidden",me.role!=="sysadmin");
    $("noticeCreatePanel").classList.toggle("hidden",!staff);
    $("periodPanel").classList.toggle("hidden",me.role!=="sysadmin");
    if($("externalUtilityPanel"))$("externalUtilityPanel").classList.toggle("hidden",!staff);
    if($("utilityBaselinePanel"))$("utilityBaselinePanel").classList.toggle("hidden",me.role!=="sysadmin");
    if($("notificationBtn"))$("notificationBtn").classList.toggle("hidden",restaurant);
    if($("refreshBtn"))$("refreshBtn").classList.remove("hidden");
    if($("familyAssignmentWrap"))$("familyAssignmentWrap").classList.toggle("hidden",restaurant||me.role==="sysadmin");

    if($("payerFamilyConfigPanel"))$("payerFamilyConfigPanel").classList.toggle("hidden",me.role!=="sysadmin");
    ["familyNamesPanel","userFamiliesPanel"].forEach(id=>$(id).classList.toggle("hidden",me.role!=="sysadmin"));
    if($("notificationBtn"))$("notificationBtn").classList.toggle("hidden",restaurant);
    if($("paymentFormTitle"))$("paymentFormTitle").textContent=family?"Subir comprobante de pago":"Registrar pago";
    if($("paymentHelp"))$("paymentHelp").textContent=family
      ?"Selecciona el mes que deseas pagar, escribe el monto y sube tu comprobante. Si pagas de más, el excedente queda solo para tu familia."
      :"Registra pagos y revisa los comprobantes. Los pagos de más quedan como crédito exclusivo de la familia que los realizó.";

    if($("dashboardEyebrow"))$("dashboardEyebrow").textContent="";
    if($("dashboardHeroTitle"))$("dashboardHeroTitle").textContent="Inicio";
    if($("dashboardHeroText"))$("dashboardHeroText").textContent=family
      ?`Familia · ${familyName(me.family_id)}`
      :me.role==="payer"?"Encargado de pagos":"Ingeniero de Sistemas";

    const obligations=$("obligationsPanel"), notices=$("noticesPanel"), recent=$("recentActivityPanel"), comparison=$("comparisonPanel");
    if(obligations)obligations.classList.toggle("hidden",!family);
    if(notices)notices.classList.toggle("hidden",!family);
    if(comparison)comparison.classList.add("hidden");
    if(recent)recent.classList.toggle("hidden",family);
    if($("recentActivityTitle"))$("recentActivityTitle").textContent=me.role==="sysadmin"?"Actividad del sistema":"Últimos pagos";

    if(family && $("swapList"))$("swapList").parentElement.classList.add("hidden");
    if(restaurant){
      $("pageTitle").textContent="Mis deudas";
      $("notificationBtn")?.classList.add("hidden");
    }
  }

  async function loadAllData(){
    if(me.role==="restaurant"){
      const {data,error}=await sb.from("external_utility_debts").select("*").eq("profile_id",session.user.id).order("month_start",{ascending:false}).order("service");
      if(error){console.error(error);return toast("No se pudieron cargar tus deudas.");}
      D={families:[],profiles:[],periods:[],templates:[],expenses:[],monthly:[],shares:[],credits:[],payments:[],cleaning:[],swaps:[],proposals:[],votes:[],notices:[],comments:[],notifications:[],audit:[],qr:[],externalDebts:data||[],utilityBaselines:[],utilityCredits:[]};
      renderRestaurantDebts();
      return;
    }
    if(me.role==="family"){
      const calls=[
        sb.from("families").select("*").order("id"),
        sb.rpc("family_portal_debts"),
        sb.from("payments").select("*").order("created_at",{ascending:false}),
        sb.from("cleaning_turns").select("*").order("turn_date",{ascending:false}),
        sb.from("notices").select("*").order("created_at",{ascending:false}),
        sb.from("payment_qr_settings").select("*").limit(1),
        sb.from("notifications").select("*").order("created_at",{ascending:false}).limit(80),
        sb.from("family_credits").select("balance").eq("family_id",me.family_id).maybeSingle(),
        sb.from("proposals").select("*").order("created_at",{ascending:false}),
        sb.from("proposal_votes").select("*"),
        sb.from("cleaning_swap_requests").select("*").order("created_at",{ascending:false})
      ];
      const r=await Promise.all(calls);
      const bad=r.slice(0,7).find(x=>x.error);
      if(bad){console.error(bad.error);return toast("No se pudo cargar tu información.");}

      D={
        families:r[0].data||[],profiles:[],periods:[],templates:[],expenses:[],monthly:[],
        shares:[],credits:r[7]?.error||!r[7]?.data?[]:[{family_id:me.family_id,balance:Number(r[7].data.balance||0)}],payments:r[2].data||[],cleaning:r[3].data||[],
        swaps:r[10]?.data||[],proposals:r[8]?.data||[],votes:r[9]?.data||[],notices:r[4].data||[],comments:[],notifications:r[6].data||[],audit:[],qr:r[5].data||[],externalDebts:[],utilityBaselines:[],utilityCredits:[]
      };
      familyDebts=r[1].data||[];
      populateFamilyPortal();
      renderFamilyPortal();
      renderProposals();renderSwaps();
      renderNotifications();
      await browserNotifyUnread();
      return;
    }

    const calls=[
      sb.from("families").select("*").order("id"),
      sb.from("profiles").select("*").order("display_name"),
      sb.from("periods").select("*").order("month_start",{ascending:false}),
      sb.from("recurring_expense_templates").select("*").order("concept"),
      sb.from("expenses").select("*").order("expense_date",{ascending:false}),
      sb.from("monthly_basic_services").select("*").order("month_start",{ascending:false}),
      sb.from("external_utility_debts").select("*").order("month_start",{ascending:false}).order("service"),
      sb.from("utility_baselines").select("*").order("service"),
      sb.from("expense_shares").select("*"),
      sb.from("family_credits").select("*"),
      sb.from("payments").select("*").order("created_at",{ascending:false}),
      sb.from("cleaning_turns").select("*").order("turn_date",{ascending:false}),
      sb.from("cleaning_swap_requests").select("*").order("created_at",{ascending:false}),
      sb.from("proposals").select("*").order("created_at",{ascending:false}),
      sb.from("proposal_votes").select("*"),
      sb.from("notices").select("*").order("created_at",{ascending:false}),
      sb.from("expense_comments").select("*").order("created_at"),
      sb.from("notifications").select("*").order("created_at",{ascending:false}),
      sb.from("payment_qr_settings").select("*").limit(1),
      sb.from("utility_credits").select("*").order("created_at",{ascending:false})
    ];
    if(me.role==="sysadmin")calls.push(sb.from("audit_log").select("*").order("created_at",{ascending:false}).limit(300));

    const r=await Promise.all(calls);
    const queryNames=[
      "families","profiles","periods","recurring_expense_templates","expenses",
      "monthly_basic_services","external_utility_debts","utility_baselines",
      "expense_shares","family_credits","payments",
      "cleaning_turns","cleaning_swap_requests","proposals","proposal_votes",
      "notices","expense_comments","notifications","payment_qr_settings","utility_credits","audit_log"
    ];
    const failures=r.map((x,i)=>x.error?{index:i,error:x.error}:null).filter(Boolean);
    if(failures.length){
      console.error("No se pudieron actualizar todos los datos",failures);
      toast("Información incompleta. No se actualizaron los saldos; vuelve a intentar con Actualizar.");
      return;
    }

    const rows=r.slice(0,21).map(x=>x.data||[]);
    if(!r[0]?.error) D.families=rows[0];
    [
      D.profiles,D.periods,D.templates,D.expenses,D.monthly,
      D.externalDebts,D.utilityBaselines,D.shares,D.credits,
      D.payments,D.cleaning,D.swaps,D.proposals,D.votes,D.notices,D.comments,D.notifications,D.qr,D.utilityCredits
    ]=rows.slice(1);
    D.audit=me.role==="sysadmin"?(r[20]?.data||[]):[];

    populate();
    renderAll();
    if(failures.length){
      const names=failures.map(f=>queryNames[f.index]||`consulta ${f.index+1}`);
      toast(`No se pudo cargar: ${names.join(", ")}. Las familias sí están disponibles.`);
    }
    await browserNotifyUnread();
  }
  async function loadAll(){
    if(reloadInFlight)return reloadInFlight;
    const selected=[...document.querySelectorAll("select")].filter(el=>!el.closest('#familyNameForm,#userFamiliesForm')&&!el.disabled).map(el=>[el,el.value]);
    const refresh=$("refreshBtn");refresh.disabled=true;refresh.setAttribute("aria-busy","true");
    reloadInFlight=(async()=>{
      // La asignación también debe actualizarse en cuentas que ya están abiertas.
      if(session?.user&&me){
        const {data:profile,error}=await sb.from("profiles").select("*").eq("id",session.user.id).single();
        if(error||!profile)throw error||new Error("Perfil no disponible");
        if(profile.role!==me.role){
          // El cambio de rol necesita reconstruir toda la navegación y sus permisos.
          location.reload();return;
        }
        const changed=profile.family_id!==me.family_id||profile.second_family_id!==me.second_family_id;
        me=profile;
        if(changed){D=emptyData();familyDebts=[];applyRoleUI();}
      }
      await loadAllData();
    })().catch(error=>{console.error(error);toast("No se pudo actualizar. Revisa tu conexión e inténtalo de nuevo.");}).finally(()=>{
      selected.forEach(([el,value])=>{if(value&&[...el.options].some(o=>o.value===value))el.value=value;});
      renderPaymentSelectionCard();renderPaymentAllocationInfo();
      refresh.disabled=false;refresh.removeAttribute("aria-busy");reloadInFlight=null;window.dispatchEvent(new Event("cf:update"));
    });
    return reloadInFlight;
  }
  $("refreshBtn").onclick=loadAll;

  function setupRealtime(){
    if(realtimeChannel)return;
    const status=$('systemStatus');
    realtimeChannel=sb.channel("casa-familias-live")
      .on("postgres_changes",{event:"*",schema:"public"},()=>{
        clearTimeout(reloadTimer);
        reloadTimer=setTimeout(()=>loadAll(),500);
      })
      .subscribe(channelStatus=>{
        if(!status)return;
        if(channelStatus==="SUBSCRIBED")status.innerHTML='<span class="status-dot" aria-hidden="true"></span> Sincronización en vivo';
        if(channelStatus==="CHANNEL_ERROR"||channelStatus==="TIMED_OUT")status.innerHTML='<span class="status-dot warning-dot" aria-hidden="true"></span> Actualización automática';
      });

    if(!syncTimer)syncTimer=setInterval(()=>{
      if(session&&document.visibilityState==="visible")loadAll();
    },15000);
    window.addEventListener("focus",()=>{if(session)loadAll();});
    document.addEventListener("visibilitychange",()=>{
      if(session&&document.visibilityState==="visible")loadAll();
    });
  }

  function populateFamilySelects(){
    const opts=D.families.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join("");

    ["paidByFamily","cleaningFamily","newFamily","newSecondFamily","payerFamily1","payerFamily2"].forEach(id=>{
      if($(id))$(id).innerHTML=(id==="newFamily"?`<option value="">Sin familia</option>`:"")+opts;
    });
  }

  async function loadFamiliesFirst(){
    const {data,error}=await sb.from("families").select("id,name").order("id");
    if(error){
      console.error("No se pudieron cargar las familias:",error);
      return false;
    }
    D.families=data||[];
    populateFamilySelects();
    return true;
  }

  function populate(){
    populateFamilySelects();

    if(me.role==="payer"&&me.family_id){
      $("paidByFamily").value=me.family_id;
      $("paidByFamily").disabled=true;
    }else{
      $("paidByFamily").disabled=false;
    }

    $("recurringTemplate").innerHTML=`<option value="">Ninguna</option>`+
      D.templates.filter(t=>t.active&&t.category==="external").map(t=>`<option value="${t.id}">${esc(t.concept)}</option>`).join("");

    let shares=D.shares.filter(s=>shareBalance(s)>0.005);
    $("paymentShare").innerHTML=shares.length?shares.map(s=>{
      const e=expenseById(s.expense_id),st=statusForShare(s)[0];
      return `<option value="${s.id}">${esc(familyName(s.family_id))} · ${esc(e?.concept)} · saldo ${money(shareBalance(s))} · ${st}</option>`;
    }).join(""):`<option value="">No hay deudas pendientes</option>`;
    renderPaymentSelectionCard();

    const payer=D.profiles.find(p=>p.role==="payer");
    if(payer){
      if($("payerFamiliesInfo")){
        $("payerFamiliesInfo").innerHTML=`<div class="notice-card"><strong>Familias cubiertas automáticamente:</strong> ${esc(familyName(payer.family_id))} y ${payer.second_family_id?esc(familyName(payer.second_family_id)):"FALTA CONFIGURAR"}</div>`;
      }
      if($("payerFamily1"))$("payerFamily1").value=String(payer.family_id||1);
      if($("payerFamily2"))$("payerFamily2").value=String(payer.second_family_id||2);
      if($("payerConfigStatus"))$("payerConfigStatus").textContent=payer.second_family_id
        ? `Actualmente: ${familyName(payer.family_id)} + ${familyName(payer.second_family_id)}`
        : "Falta seleccionar la segunda familia del Encargado de Pagos.";
    }else{
      if($("payerFamiliesInfo"))$("payerFamiliesInfo").innerHTML=`<div class="notice-card danger">Todavía no existe un Encargado de Pagos. Créalo desde Administración.</div>`;
      if($("payerConfigStatus"))$("payerConfigStatus").textContent="Todavía no existe un Encargado de Pagos.";
    }

    const waterAverage=D.utilityBaselines.find(x=>x.service==="water");
    const electricityAverage=D.utilityBaselines.find(x=>x.service==="electricity");
    if($("waterAutoAverage"))$("waterAutoAverage").textContent=waterAverage?.base_amount!=null?money(waterAverage.base_amount):"Sin historial";
    if($("electricityAutoAverage"))$("electricityAutoAverage").textContent=electricityAverage?.base_amount!=null?money(electricityAverage.base_amount):"Sin historial";
    if($("utilityAverageInfo")){
      const source=Math.max(Number(waterAverage?.source_months||0),Number(electricityAverage?.source_months||0));
      $("utilityAverageInfo").textContent=source>0
        ?`Promedio automático calculado con ${source} factura${source===1?"":"s"} histórica${source===1?"":"s"}. Se actualizará al registrar cada nuevo mes.`
        :"Todavía no hay facturas históricas. Marca una factura anterior como “factura histórica” para crear la primera referencia.";
    }
    updatePendingCreditsInfo();
    if($("newRole"))toggleUserRoleFields();
  }

  function populateFamilyPortal(){
    const open=familyDebts.filter(d=>Number(d.balance)>0.005);
    $("paymentShare").innerHTML=open.length
      ? open.map(d=>`<option value="${d.share_id}">${esc(d.concept)} · pendiente ${money(d.balance)}</option>`).join("")
      : `<option value="">No tienes deudas pendientes</option>`;
    renderPaymentSelectionCard();
  }

  function toggleUserRoleFields(){
    const role=$("newRole")?.value||"family";
    const needsFamily=["family","payer"].includes(role);
    if($("familyAssignmentWrap"))$("familyAssignmentWrap").classList.toggle("hidden",!needsFamily);
    if($("newFamily"))$("newFamily").required=needsFamily;
    if(!$("secondFamilyWrap"))return;
    const payer=role==="payer";
    $("secondFamilyWrap").classList.toggle("hidden",!payer);
    if($("newSecondFamily"))$("newSecondFamily").required=payer;
  }

  if($("newRole"))$("newRole").addEventListener("change",toggleUserRoleFields);

  function toggleReferenceOnlyFields(){
    const reference=Boolean($("referenceOnly")?.checked);
    ["gasReceipt","wifiReceipt"].forEach(id=>{if($(id))$(id).required=!reference;});
    if($("otherWaterContribution"))$("otherWaterContribution").disabled=reference;
    if($("otherElectricityContribution"))$("otherElectricityContribution").disabled=reference;
  }
  if($("referenceOnly")){
    $("referenceOnly").addEventListener("change",toggleReferenceOnlyFields);
    toggleReferenceOnlyFields();
  }

  function togglePaymentReceiptRule(){
    if(!$("paymentMethod"))return;
    const method=$("paymentMethod").value||"qr";
    const qr=method==="qr";
    if($("paymentReceipt"))$("paymentReceipt").required=qr;
    if($("paymentReceiptHint"))$("paymentReceiptHint").textContent=qr
      ?"Pago por QR: debes subir obligatoriamente la foto del comprobante."
      :"Pago en efectivo: la foto del comprobante es opcional.";
    if($("paymentQrBox"))$("paymentQrBox").classList.toggle("hidden",!qr);
    syncPaymentMethodButtons();
  }
  if($("paymentMethod"))$("paymentMethod").addEventListener("change",togglePaymentReceiptRule);
  function syncPaymentMethodButtons(){
    const method=$("paymentMethod")?.value||"qr";
    document.querySelectorAll(".payment-method-option").forEach(button=>{
      const selected=button.dataset.paymentMethod===method;
      button.classList.toggle("selected",selected);
      button.setAttribute("aria-pressed",String(selected));
      const check=button.querySelector(".payment-method-check");
      if(check)check.textContent=selected?"✓":"○";
    });
  }
  document.querySelectorAll(".payment-method-option").forEach(button=>button.addEventListener("click",()=>{
    const select=$("paymentMethod");
    if(!select)return;
    select.value=button.dataset.paymentMethod||"qr";
    select.dispatchEvent(new Event("change",{bubbles:true}));
  }));
  $("paymentShare")?.addEventListener("change",renderPaymentSelectionCard);
  $("paymentAmount")?.addEventListener("input",renderPaymentAllocationInfo);
  $("paymentReceipt")?.addEventListener("change",()=>{
    const file=$("paymentReceipt").files?.[0];
    if($("paymentFileText"))$("paymentFileText").textContent=file?file.name:"Toca para subir una foto";
    if($("paymentDropzone"))$("paymentDropzone").classList.toggle("has-file",Boolean(file));
  });
  $("paymentBackBtn")?.addEventListener("click",()=>{
    const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="dashboard");
    showView("dashboard","Inicio",navButton);
  });
  $("expensesBackBtn")?.addEventListener("click",()=>{
    const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="dashboard");
    showView("dashboard","Inicio",navButton);
  });
  $("cleaningBackBtn")?.addEventListener("click",()=>{
    const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="dashboard");
    showView("dashboard","Inicio",navButton);
  });

  function renderFamilyPortal(){
    renderFamilyDashboard();
    renderFamilyCleaning();
    renderNotices();
    renderFamilyPayments();
    renderPaymentQr();
    togglePaymentReceiptRule();
  }

  function renderFamilyDashboard(){
    const total=familyDebts.reduce((a,d)=>a+Number(d.balance||0),0);
    const review=familyDebts.reduce((a,d)=>a+Number(d.pending_submitted||0),0);
    const next=[...D.cleaning]
      .filter(t=>t.family_id===me.family_id&&t.status==="pending")
      .sort((a,b)=>a.turn_date.localeCompare(b.turn_date))[0];

    const metrics=document.querySelector("#view-dashboard .metrics");
    metrics.innerHTML=`
      <article class="metric"><span>Total que debo</span><strong>${money(total)}</strong></article>
      <article class="metric"><span>Comprobantes en revisión</span><strong>${money(review)}</strong></article>
      <article class="metric"><span>Próximo turno de limpieza</span><strong>${next?.turn_date||"-"}</strong></article>
    `;

    const grids=document.querySelectorAll("#view-dashboard .grid2");
    grids.forEach((g,i)=>{
      if(i===0){
        g.classList.remove("hidden");
        g.style.gridTemplateColumns="1fr";
        const cards=g.querySelectorAll(".panel");
        if(cards[1])cards[1].classList.add("hidden");
      }else{
        g.classList.add("hidden");
      }
    });

    $("myDebtsList").innerHTML=familyDebts.length?familyDebts.map(d=>{
      const balance=Number(d.balance||0);
      const status=d.debt_status==="paid"
        ?["Pagado","ok"]
        :d.debt_status==="review"
          ?["En revisión","review"]
          :d.debt_status==="overdue"
            ?["Vencido","overdue"]
            :d.debt_status==="partial"
              ?["Pago parcial","partial"]
              :["Pendiente","pending"];

      const breakdown=d.month_start?`
        <div class="muted">
          Mes ${String(d.month_start).slice(0,7)} ·
          Agua ${money(d.water_amount)} ·
          Luz ${money(d.electricity_amount)} ·
          Gas ${money(d.gas_amount)} ·
          WiFi ${money(d.wifi_amount)}
        </div>`:"";

      return `<div class="debt-row">
        <div>
          <strong>${esc(d.concept)}</strong>
          ${breakdown}
          <div class="muted">Cuota ${money(d.amount_due)} · pagado ${money(d.amount_paid)} · vence ${d.due_date}</div>
        </div>
        <div>
          <span class="badge ${status[1]}">${status[0]}</span>
          <div class="big-number ${balance>0?"danger":"success"}">${balance>0?money(balance):"0.00 Bs"}</div>
        </div>
      </div>`;
    }).join(""):`<div class="family-card"><div class="big-number success">No tienes deuda pendiente</div></div>`;
    renderHomeCleaning();
  }

  function cleaningTurnsSorted(){
    return [...D.cleaning].filter(t=>t.status!=="done").sort((a,b)=>String(a.turn_date).localeCompare(String(b.turn_date)));
  }
  function cleaningTaskMarkup(turn){
    const tasks=Array.isArray(turn?.checklist)?turn.checklist:[];
    return tasks.length
      ? tasks.map(task=>`<div class="cleaning-task-item"><span class="cleaning-task-box" aria-hidden="true">□</span><span>${esc(task)}</span></div>`).join("")
      : `<p class="muted cleaning-empty-tasks">El administrador todavía no registró tareas para esta semana.</p>`;
  }
  function cleaningFamilyIdsForView(){
    if(me?.role!=="payer")return me?.family_id?[me.family_id]:[];
    const ids=payerFamilyIds();
    return ids.length?ids:[me.family_id,me.second_family_id].filter(Boolean);
  }
  function renderCleaningFamilyWorkspace(){
    const focus=$("cleaningFocusCard"),tasksPanel=$("cleaningTasksPanel"),upcomingPanel=$("cleaningUpcomingPanel"),admin=$("cleaningAdminWorkspace");
    if(!focus||!tasksPanel||!upcomingPanel)return;
    focus.classList.remove("hidden");tasksPanel.classList.remove("hidden");upcomingPanel.classList.remove("hidden");admin?.classList.add("hidden");
    if($("cleaningViewTitle"))$("cleaningViewTitle").textContent="Mi limpieza";
    if($("cleaningViewSubtitle"))$("cleaningViewSubtitle").textContent=me?.role==="payer"?"Familias 5 + 6 · turnos por semanas":"Tu turno semanal en la casa";
    const all=cleaningTurnsSorted(),ownIds=new Set(cleaningFamilyIdsForView()),own=all.filter(t=>ownIds.has(t.family_id)),next=own[0];
    const ownLabel=me?.role==="payer"?cleaningFamilyIdsForView().map(id=>familyName(id)).join(" + "):familyName(me?.family_id);
    if(next){
      const label=cleaningWeekLabel(next.turn_date),headline=label==="Esta semana"?"Esta semana te toca a ti":`Tu próximo turno es ${label.toLowerCase()}`;
      focus.innerHTML=`<div class="cleaning-focus-icon" aria-hidden="true">🧹</div><div class="cleaning-focus-copy"><span class="cleaning-focus-kicker">${esc(label)}</span><h3>${esc(headline)}</h3><p>${esc(cleaningDateText(next.turn_date))}</p><span class="cleaning-family-chip">${esc(ownLabel)}</span></div>`;
      $("cleaningTasksBody").innerHTML=cleaningTaskMarkup(next);
      $("cleaningCompleteBtn").disabled=false;$("cleaningSwapBtn").disabled=false;
      $("cleaningCompleteBtn").onclick=()=>openCompleteCleaning(next.id);
      $("cleaningSwapBtn").onclick=()=>openSwap(next.id);
    }else{
      focus.innerHTML=`<div class="cleaning-focus-icon" aria-hidden="true">🧹</div><div class="cleaning-focus-copy"><span class="cleaning-focus-kicker">SIN TURNO ASIGNADO</span><h3>Turno pendiente de configurar</h3><p>El administrador todavía no asignó una semana para ${esc(ownLabel)}.</p></div>`;
      $("cleaningTasksBody").innerHTML=`<p class="muted cleaning-empty-tasks">Cuando se asigne un turno, aquí aparecerán las tareas de la semana.</p>`;
      $("cleaningCompleteBtn").disabled=true;$("cleaningSwapBtn").disabled=true;
      $("cleaningCompleteBtn").onclick=null;$("cleaningSwapBtn").onclick=null;
    }
    const list=$("cleaningUpcomingBody");
    list.innerHTML=all.length?all.slice(0,5).map((turn,index)=>{
      const label=index===0?cleaningWeekLabel(turn.turn_date):index===1?"Próxima semana":`En ${index} semanas`;
      return `<div class="cleaning-upcoming-item ${index===0?"current":""}"><span class="cleaning-upcoming-dot" aria-hidden="true"></span><div><strong>${esc(label)}</strong><span>${esc(familyName(turn.family_id))}</span></div></div>`;
    }).join(""):`<p class="muted">Todavía no hay turnos asignados.</p>`;
  }
  function renderFamilyCleaning(){
    renderCleaningFamilyWorkspace();
  }

  function renderFamilyPayments(){
    const ownPayments=D.payments.filter(p=>p.family_id===me.family_id);
    $("paymentsBody").innerHTML=ownPayments.length?ownPayments.map(p=>`
      <tr>
        <td>${p.paid_at}</td>
        <td>${esc(familyName(p.family_id))}</td>
        <td>${money(p.amount)}</td>
        <td>${money(p.applied_amount)}</td>
        <td>${money(p.credit_amount)}</td>
        <td><span class="badge ${p.status==="approved"?"ok":p.status==="rejected"?"rejected":"review"}">${p.status==="approved"?"Aprobado":p.status==="rejected"?"Rechazado":"En revisión"}</span></td>
        <td>${esc(p.note||"-")}</td>
        <td>${receiptBtn(p.receipt_path)}</td>
        <td>-</td>
      </tr>`).join(""):`<tr><td colspan="9">Todavía no subiste comprobantes.</td></tr>`;
    bindReceiptButtons();
  }

  function renderDashboard(){
    renderPayerCommandPanel();
    renderHomeCleaning();
    const myShares=me.family_id?D.shares.filter(s=>s.family_id===me.family_id):[];
    $("myDebt").textContent=money(myShares.reduce((a,s)=>a+shareBalance(s),0));
    const myPendingPayments=me.family_id
      ? D.payments.filter(p=>p.family_id===me.family_id&&p.status==="pending").length
      : 0;
    if($("myPending"))$("myPending").textContent=String(myPendingPayments);
    const next=[...D.cleaning].filter(t=>t.status==="pending").sort((a,b)=>a.turn_date.localeCompare(b.turn_date))[0];
    $("nextTurn").textContent=next?`${familyName(next.family_id)} · ${next.turn_date}`:"Sin turno";
    $("myDebtsList").innerHTML=myShares.length?myShares.map(s=>{
      const e=expenseById(s.expense_id),[label,cls]=statusForShare(s);
      return `<div class="debt-row"><div><strong>${esc(e?.concept||"Gasto")}</strong><div class="muted">Cuota ${money(s.amount_due)} · vence ${e?.due_date||"-"}</div></div><div><span class="badge ${cls}">${label}</span><div class="big-number">${money(shareBalance(s))}</div></div></div>`;
    }).join(""):`<p class="muted">No tienes obligaciones registradas.</p>`;

    const activeNotices=D.notices.filter(n=>!n.visible_until||n.visible_until>=today()).slice(0,5);
    $("noticeList").innerHTML=activeNotices.length?activeNotices.map(n=>`<div class="notice-card"><h3>${esc(n.title)}</h3><p>${esc(n.body)}</p>${n.visible_until?`<small class="muted">Visible hasta ${n.visible_until}</small>`:""}</div>`).join(""):`<p class="muted">No hay avisos activos.</p>`;

    const recentPayments=me.role==="family"?D.payments.filter(p=>p.family_id===me.family_id):D.payments;
    $("recentActivity").innerHTML=recentPayments.slice(0,7).map(p=>`<div class="activity-row"><div><strong>${esc(familyName(p.family_id))}</strong><div class="muted">${p.paid_at} · ${p.kind}</div></div><div><strong>${money(p.amount)}</strong><br><span class="badge ${p.status==="approved"?"ok":p.status==="rejected"?"rejected":"review"}">${({approved:"Aprobado",rejected:"Rechazado",pending:"En revisión"})[p.status]||esc(p.status)}</span></div></div>`).join("")||`<p class="muted">Sin movimientos.</p>`;
    renderComparison();
  }
  function renderPayerCommandPanel(){
    const panel=$("payerCommandPanel");
    if(!panel)return;
    const staff=["sysadmin","payer"].includes(me.role);
    panel.classList.toggle("hidden",!staff);
    if(!staff)return;

    const configuredIds=payerFamilyIds();
    const payerIds=configuredIds;
    const payerNames=payerIds.map(id=>familyName(id)).join(" + ");
    const ownDebt=payerIds.reduce((total,id)=>total+balanceForFamily(id),0);
    $("payerFamilyNames").textContent=payerNames||"Sin familia pagadora configurada";
    $("payerOwnDebt").textContent=`Deuda propia: ${money(ownDebt)}`;
    const paidByPayer=D.payments
      .filter(p=>payerIds.includes(p.family_id)&&p.status==="approved"&&String(p.paid_at||"").startsWith(monthNow()))
      .reduce((total,p)=>total+Number(p.amount||0),0);
    const pending=D.payments.filter(p=>p.status==="pending");
    const otherDebt=D.families
      .filter(f=>!payerIds.includes(f.id))
      .reduce((total,f)=>total+balanceForFamily(f.id),0);

    $("payerCommandMetrics").innerHTML=`
      <div class="payer-command-metric"><span>Familia pagadora</span><strong>${esc(payerNames||"Familias 5 + 6")}</strong><small>Un hogar · dos cuartos</small></div>
      <div class="payer-command-metric"><span>Pagado este mes</span><strong>${money(paidByPayer)}</strong><small>Pagos aprobados de la familia pagadora</small></div>
      <div class="payer-command-metric"><span>Por aprobar</span><strong>${money(pending.reduce((a,p)=>a+Number(p.amount||0),0))}</strong><small>${pending.length} comprobante${pending.length===1?"":"s"} en revisión</small></div>
      <div class="payer-command-metric"><span>Pendiente por familias</span><strong>${money(otherDebt)}</strong><small>Montos pendientes de las demás familias</small></div>`;

    document.querySelectorAll(".payer-command-btn").forEach(button=>button.onclick=()=>{
      const view=button.dataset.commandView;
      const navButton=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view===view);
      showView(view,view==="payments"?"Pagos":"Saldos",navButton);
    });
  }
  function renderComparison(){
    const months=[...D.monthly].sort((a,b)=>b.month_start.localeCompare(a.month_start)).slice(0,2);
    if(!months.length){
      $("comparisonBox").innerHTML=`<p class="muted">Aún no hay paquetes mensuales de servicios básicos.</p>`;
      return;
    }
    const current=months[0],previous=months[1];
    const rows=[
      ["Agua","water_amount"],
      ["Luz","electricity_amount"],
      ["Gas","gas_amount"],
      ["WiFi","wifi_amount"]
    ];
    $("comparisonBox").innerHTML=`<table><thead><tr><th>Servicio</th><th>${previous?.month_start?.slice(0,7)||"-"}</th><th>${current.month_start.slice(0,7)}</th><th>Diferencia</th></tr></thead><tbody>`+
      rows.map(([label,key])=>{
        const a=Number(previous?.[key]||0),b=Number(current[key]||0);
        return `<tr><td>${label}</td><td>${money(a)}</td><td>${money(b)}</td><td class="${b>a?"danger":"success"}">${money(b-a)}</td></tr>`;
      }).join("")+`</tbody></table>`;
  }

  function parseOcr(text){
    const flat=text.replace(/\s+/g," ").trim(),lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
    const likely=lines.filter(l=>/(total|importe|monto|pagar|bs\b|boliv)/i.test(l));
    const nums=[];(likely.length?likely:lines.slice(-12)).forEach(line=>(line.match(/\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?/g)||[]).forEach(m=>{const n=toAmount(m);if(n>0&&n<1000000)nums.push(n);}));
    let date=null;const ymd=flat.match(/\b(20\d{2})[\/\-.]([01]?\d)[\/\-.]([0-3]?\d)\b/),dmy=flat.match(/\b([0-3]?\d)[\/\-.]([01]?\d)[\/\-.](20\d{2}|\d{2})\b/);
    if(ymd)date=`${ymd[1]}-${String(ymd[2]).padStart(2,"0")}-${String(ymd[3]).padStart(2,"0")}`;
    else if(dmy){let y=dmy[3];if(y.length===2)y="20"+y;date=`${y}-${String(dmy[2]).padStart(2,"0")}-${String(dmy[1]).padStart(2,"0")}`;}
    const l=flat.toLowerCase();let concept="";
    if(l.includes("electric")||l.includes("elfec")||l.includes("luz"))concept="Luz";else if(l.includes("agua"))concept="Agua";else if(l.includes("internet"))concept="Internet";else if(l.includes("gas"))concept="Gas";
    return {amount:nums.length?Math.max(...nums):null,date,concept};
  }
  function toAmount(value){
    const s=String(value).replace(/[^\d.,-]/g,"");
    if(!s)return null;
    if(s.includes(",")&&s.includes("."))return Number(s.replace(/,/g,"").replace(".","."));
    if(s.includes(",")){const parts=s.split(",");return parts[parts.length-1].length===3?Number(s.replace(/,/g,"")):Number(s.replace(",","."));}
    if(s.includes(".")){const parts=s.split(".");return parts[parts.length-1].length===3?Number(s.replace(/\./g,"")):Number(s);}
    return Number(s);
  }
  function parseReceiptAmount(data,service){
    const raw=String(data?.text||"");
    const lines=raw.split(/\r?\n/).map(x=>x.replace(/\s+/g," ").trim()).filter(Boolean);
    const labels=service==="electricity"
      ?/(total\s*(a\s*)?pagar|importe\s*(total)?|total\s*factura)/i
      :/(total\s*(a\s*)?(cancelar|pagar)|debe\s*\(?bs\.?\)?|importe\s*(total)?)/i;
    const candidates=[];
    lines.filter(line=>labels.test(line)).forEach(line=>{
      (line.match(/\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?/g)||[]).forEach(value=>{
        const amount=toAmount(value);if(amount>0&&amount<1000000&&![2024,2025,2026,2027].includes(amount))candidates.push(amount);
      });
    });
    if(candidates.length)return Math.max(...candidates);
    const words=data?.words||[],height=Number(data?.image?.height||0),width=Number(data?.image?.width||0);
    const lowerRight=words.filter(word=>{
      const t=String(word.text||"").replace(/[^\d.,]/g,"");
      const amount=toAmount(t),box=word.bbox||{};
      return amount>0&&amount<1000000&&!/^(19|20)\d{2}$/.test(t)&&box.x0>width*.48&&box.y0>height*.5;
    }).map(word=>toAmount(String(word.text||""))).filter(Boolean);
    return lowerRight.length?Math.max(...lowerRight):parseOcr(raw).amount;
  }
  async function doOcr(file,status,textEl,apply){
    if(!file)return toast("Selecciona una imagen.");
    status.textContent="Leyendo imagen...";
    try{
      const worker=await Tesseract.createWorker("spa");
      const {data}=await worker.recognize(file,{rotateAuto:true});
      await worker.terminate();
      textEl.value=data.text||"";textEl.classList.remove("hidden");apply(parseOcr(data.text||""),data);
      status.textContent="Lectura terminada. Revisa antes de guardar.";
    }catch(e){console.error(e);status.textContent="No se pudo leer la foto.";}
  }
  $("expenseOcrBtn").onclick=()=>doOcr($("expenseReceipt").files[0],$("expenseOcrStatus"),$("expenseOcrText"),p=>{if(p.amount)$("expenseAmount").value=p.amount;if(p.date){$("expenseDate").value=p.date;$("expenseDueDate").value=p.date;}if(p.concept)$("expenseConcept").value=p.concept;});
  $("paymentOcrBtn").onclick=()=>doOcr($("paymentReceipt").files[0],$("paymentOcrStatus"),$("paymentOcrText"),p=>{if(p.amount)$("paymentAmount").value=p.amount;if(p.date)$("paymentDate").value=p.date;});
  async function createReceiptFocusCanvas(file){
    if(typeof createImageBitmap!=="function")return file;
    const bitmap=await createImageBitmap(file);
    const sx=Math.floor(bitmap.width*.38),sy=Math.floor(bitmap.height*.42),sw=Math.floor(bitmap.width*.60),sh=Math.floor(bitmap.height*.53);
    const canvas=document.createElement("canvas");canvas.width=Math.min(1500,sw*2);canvas.height=Math.min(1500,sh*2);
    const ctx=canvas.getContext("2d");ctx.imageSmoothingEnabled=true;ctx.filter="grayscale(1) contrast(1.35) brightness(1.08)";ctx.drawImage(bitmap,sx,sy,sw,sh,0,0,canvas.width,canvas.height);bitmap.close();return canvas;
  }
  async function readMonthlyReceipt(file,service){
    if(!file)throw new Error(service==="electricity"?"Selecciona primero la factura de luz.":"Selecciona primero la factura de agua.");
    const worker=await Tesseract.createWorker("spa");
    try{
      const {data}=await worker.recognize(file,{rotateAuto:true});
      const focusData=(await worker.recognize(await createReceiptFocusCanvas(file),{rotateAuto:true})).data;
      const amount=parseReceiptAmount(focusData,service)??parseReceiptAmount(data,service);
      if(!amount)throw new Error(service==="electricity"?"No se encontró el total de la factura de luz.":"No se encontró el total de la factura de agua.");
      return amount;
    }finally{await worker.terminate();}
  }
  if($("monthlyServicesOcrBtn"))$("monthlyServicesOcrBtn").onclick=async()=>{
    const status=$("monthlyServicesOcrStatus");status.textContent="Leyendo luz y agua...";
    try{
      const electricity=await readMonthlyReceipt($("electricityReceipt").files[0],"electricity");
      const water=await readMonthlyReceipt($("waterReceipt").files[0],"water");
      $("electricityAmount").value=electricity;$("waterAmount").value=water;
      status.textContent=`Listo: Luz ${money(electricity)} · Agua ${money(water)}. Revisa antes de guardar.`;
    }catch(error){status.textContent=error.message||"No se pudieron leer las facturas.";}
  };

  $("monthlyServicesForm").onsubmit=async e=>{
    e.preventDefault();
    if(!["sysadmin","payer"].includes(me.role))return;

    const month=$("basicMonth").value;
    if(!month)return toast("Selecciona el mes.");

    try{
      const [waterPath,electricityPath,gasPath,wifiPath]=await Promise.all([
        upload($("waterReceipt").files[0]),
        upload($("electricityReceipt").files[0]),
        upload($("gasReceipt").files[0]),
        upload($("wifiReceipt").files[0])
      ]);

      const {error}=await sb.rpc("create_monthly_basic_services",{
        p_month:month+"-01",
        p_water:Number($("waterAmount").value||0),
        p_electricity:Number($("electricityAmount").value||0),
        p_gas:Number($("gasAmount").value||0),
        p_wifi:Number($("wifiAmount").value||0),
        p_due_date:$("basicDueDate").value,
        p_water_receipt:waterPath,
        p_electricity_receipt:electricityPath,
        p_gas_receipt:gasPath,
        p_wifi_receipt:wifiPath,
        p_other_water:Number($("referenceOnly")?.checked?0:$("otherWaterContribution").value||0),
        p_other_electricity:Number($("referenceOnly")?.checked?0:$("otherElectricityContribution").value||0),
        p_reference_only:Boolean($("referenceOnly")?.checked)
      });
      if(error)throw error;

      toast($("referenceOnly")?.checked
        ?"Factura histórica guardada. Se actualizaron los promedios y no se generó ninguna deuda."
        :"Servicios básicos del mes registrados. Las dos familias del encargado quedaron pagadas automáticamente.");
      e.target.reset();
      $("basicMonth").value=monthNow();
      $("basicDueDate").value=today();
      $("otherWaterContribution").value=0;
      $("otherElectricityContribution").value=0;
      toggleReferenceOnlyFields();
      await loadAll();
    }catch(err){
      toast(err.message||"No se pudieron registrar los servicios básicos.");
    }
  };

  function renderMonthlyServices(){
    if(!["sysadmin","payer"].includes(me.role))return;
    $("monthlyServicesBody").innerHTML=D.monthly.length?D.monthly.map(m=>{
      const total=Number(m.water_amount)+Number(m.electricity_amount)+Number(m.gas_amount)+Number(m.wifi_amount);
      const reference=Boolean(m.is_reference_only);
      const otherTotal=reference?0:Number(m.other_water_contribution||0)+Number(m.other_electricity_contribution||0);
      const restaurantTotal=reference?0:Number(m.restaurant_water_amount||0)+Number(m.restaurant_electricity_amount||0);
      const familyTotal=reference?0:Number(m.family_total_amount??(total-restaurantTotal));
      return `<tr>
        <td>${m.month_start.slice(0,7)} ${reference?'<span class="badge review">Historial</span>':''}</td>
        <td>${money(m.water_amount)}</td>
        <td>${money(m.electricity_amount)}</td>
        <td>${money(m.gas_amount)}</td>
        <td>${money(m.wifi_amount)}</td>
        <td><strong>${money(total)}</strong></td>
        <td class="success">${money(otherTotal)}</td>
        <td class="success">${money(restaurantTotal)}</td>
        <td><strong>${money(familyTotal)}</strong></td>
        <td>${money(familyTotal/6)}</td>
        <td>${reference?"Solo historial":m.due_date}</td>
      </tr>`;
    }).join(""):`<tr><td colspan="11">Todavía no hay servicios básicos mensuales registrados.</td></tr>`;
  }

  function utilityStatus(status,amountDue,amountPaid,dueDate){
    const balance=Math.max(Number(amountDue||0)-Number(amountPaid||0),0);
    if(balance<=0.005)return ["Pagado","ok"];
    if(dueDate&&dueDate<today())return ["Vencido","overdue"];
    if(Number(amountPaid||0)>0)return ["Parcial","partial"];
    return [status==="paid"?"Pagado":"Pendiente",status==="paid"?"ok":"pending"];
  }

  function renderExternalDebts(){
    if(!$("externalUtilityBody")||!["sysadmin","payer"].includes(me.role))return;
    const rows=D.externalDebts||[];
    $("externalUtilityBody").innerHTML=rows.length?rows.map(d=>{
      const [label,cls]=utilityStatus(d.status,d.amount_due,d.amount_paid,d.due_date);
      const balance=Math.max(Number(d.amount_due||0)-Number(d.amount_paid||0),0);
      return `<tr><td>${String(d.month_start||"").slice(0,7)}</td><td>${d.service==="electricity"?"Luz":"Agua"}</td><td>${money(d.bill_amount)}</td><td>${money(d.family_base_amount)}</td><td><strong>${money(balance)}</strong></td><td><span class="badge ${cls}">${label}</span></td><td>${balance>0.005?`<button class="small-btn mark-external-paid" data-id="${d.id}" data-amount="${balance}">Registrar pago</button>`:`${esc(d.received_by||"Recibido")}`}</td></tr>`;
    }).join(""):`<tr><td colspan="7">Todavía no hay deudas del restaurante.</td></tr>`;
    document.querySelectorAll(".mark-external-paid").forEach(b=>b.onclick=async()=>{
      const receivedBy=prompt("¿Quién recibió el pago?","Tu tía");
      if(receivedBy===null)return;
      const {error}=await sb.rpc("mark_external_utility_paid",{p_debt_id:b.dataset.id,p_received_by:receivedBy.trim()||"No indicado",p_paid_amount:Number(b.dataset.amount)});
      if(error)return toast(error.message);
      toast("Pago del restaurante registrado.");
      await loadAll();
    });
  }

  function renderRestaurantDebts(){
    if(!$("restaurantDebtsBody"))return;
    const rows=D.externalDebts||[];
    const withBalance=rows.map(d=>({...d,balance:Math.max(Number(d.amount_due||0)-Number(d.amount_paid||0),0)}));
    const total=withBalance.reduce((sum,d)=>sum+d.balance,0);
    const next=withBalance.filter(d=>d.balance>0.005).sort((a,b)=>String(a.due_date||"").localeCompare(String(b.due_date||"")))[0];
    $("restaurantDebtTotal").textContent=money(total);
    $("restaurantNextDue").textContent=next?.due_date||"Sin pendientes";
    $("restaurantDebtsBody").innerHTML=withBalance.length?withBalance.map(d=>{
      const [label,cls]=utilityStatus(d.status,d.amount_due,d.amount_paid,d.due_date);
      return `<tr><td>${String(d.month_start||"").slice(0,7)}</td><td>${d.service==="electricity"?"Luz":"Agua"}</td><td><strong>${money(d.balance)}</strong></td><td>${d.due_date||"-"}</td><td><span class="badge ${cls}">${label}</span></td></tr>`;
    }).join(""):`<tr><td colspan="5">No tienes deudas registradas.</td></tr>`;
  }

  $("expenseCategory").onchange=()=>{$("expenseJustification").required=$("expenseCategory").value==="external";};
  $("recurringTemplate").onchange=()=>{
    const t=D.templates.find(x=>x.id===$("recurringTemplate").value);if(!t)return;
    $("expenseConcept").value=t.concept;$("expenseCategory").value=t.category;$("expenseJustification").value=t.justification||"";
    const d=new Date($("expenseDate").value+"T12:00:00");if(!isNaN(d)){d.setDate(Math.min(t.default_due_day,28));$("expenseDueDate").value=d.toISOString().slice(0,10);}
    $("expenseCategory").dispatchEvent(new Event("change"));
  };

  const expenseModeRadios=document.querySelectorAll('input[name="expenseMode"]');
  function currentExpenseMode(){
    const checked=document.querySelector('input[name="expenseMode"]:checked');
    return checked?checked.value:"split";
  }
  function toggleExpenseModeFields(){
    const mode=currentExpenseMode();
    const isDiscount=mode==="discount";
    if($("expenseSplitFields"))$("expenseSplitFields").classList.toggle("hidden",isDiscount);
    if($("expenseDiscountFields"))$("expenseDiscountFields").classList.toggle("hidden",!isDiscount);
    if($("paidByFamily"))$("paidByFamily").required=!isDiscount;
    if($("utilityCreditService"))$("utilityCreditService").required=isDiscount;
  }
  expenseModeRadios.forEach(r=>r.addEventListener("change",toggleExpenseModeFields));
  toggleExpenseModeFields();

  function updatePendingCreditsInfo(){
    const box=$("pendingCreditsInfo");if(!box)return;
    const list=D.utilityCredits||[];
    const pending=list.filter(c=>!c.applied_month_start);
    if(!pending.length){box.innerHTML='<p class="muted">No hay descuentos pendientes todavía.</p>';return;}
    const byService={};pending.forEach(c=>{byService[c.service]=(byService[c.service]||0)+Number(c.amount||0);});
    box.innerHTML=`<div class="notice-card"><strong>Descuentos pendientes:</strong> ${Object.entries(byService).map(([s,v])=>`${utilityName[s]||s}: ${money(v)}`).join(" · ")}. Se aplicarán automáticamente en el próximo paquete mensual de cada servicio.</div>`;
  }

  $("expenseForm").onsubmit=async e=>{
    e.preventDefault();if(!["sysadmin","payer"].includes(me.role))return;
    const mode=currentExpenseMode();
    try{
      if(mode==="discount"){
        const service=$("utilityCreditService").value;
        const amount=Number($("expenseAmount").value);
        if(!service)return toast("Elige a qué servicio se descuenta.");
        const path=await upload($("expenseReceipt").files[0]);
        const {error}=await sb.rpc("create_utility_credit",{p_service:service,p_amount:amount,p_note:$("expenseConcept").value.trim()||null,p_receipt_path:path});
        if(error)throw error;
        toast(`Se descontará ${money(amount)} de la próxima factura de ${utilityName[service]||service}.`);
      }else{
        if(periodClosed($("expenseDate").value))return toast("Ese mes está cerrado.");
        if($("expenseJustification").value.trim().length<3)return toast("El gasto externo necesita justificación.");
        const path=await upload($("expenseReceipt").files[0]);
        const {error}=await sb.rpc("create_expense",{p_concept:$("expenseConcept").value.trim(),p_category:$("expenseCategory").value,p_total_amount:Number($("expenseAmount").value),p_expense_date:$("expenseDate").value,p_due_date:$("expenseDueDate").value,p_justification:$("expenseJustification").value.trim()||null,p_receipt_path:path,p_ocr_text:$("expenseOcrText").value||null,p_paid_by_family_id:Number($("paidByFamily").value)});
        if(error)throw error;
        toast("Gasto registrado y distribuido entre las seis familias.");
      }
      e.target.reset();$("expenseDate").value=today();$("expenseDueDate").value=today();
      toggleExpenseModeFields();
      await loadAll();
    }catch(err){toast(err.message);}
  };
  $("expenseMonthFilter").onchange=()=>{renderExpenses();renderExpensesWorkspace();};
  function renderExpenses(){
    const m=$("expenseMonthFilter").value;
    const list=D.expenses.filter(e=>e.category==="external"&&(!m||e.expense_date.startsWith(m)));
    $("expensesBody").innerHTML=list.length?list.map(e=>{
      const sh=D.shares.filter(s=>s.expense_id===e.id),due=sh.reduce((a,s)=>a+Number(s.amount_due),0),paid=sh.reduce((a,s)=>a+approvedPaid(s.id),0);
      let st=due<=paid+.005?["Pagado","ok"]:e.due_date<today()?["Vencido","overdue"]:paid>0?["Parcial","partial"]:["Pendiente","pending"];
      return `<tr><td>${e.expense_date}</td><td>${e.due_date}</td><td><span class="badge ${st[1]}">${st[0]}</span></td><td>${esc(e.concept)}</td><td>${money(e.total_amount)}</td><td>${money(sh.length?due/6:0)}</td><td>${esc(e.justification||"-")}</td><td>${receiptBtn(e.receipt_path)}</td><td><button class="small-btn comments-btn" data-id="${e.id}">Ver (${D.comments.filter(c=>c.expense_id===e.id).length})</button></td></tr>`;
    }).join(""):`<tr><td colspan="9">No hay gastos para este mes.</td></tr>`;
    bindReceiptButtons();document.querySelectorAll(".comments-btn").forEach(b=>b.onclick=()=>openComments(b.dataset.id));
  }
  function renderExpensesWorkspace(){
    const title=$("expensesViewTitle"),subtitle=$("expensesViewSubtitle"),summary=$("expensesRoleSummary"),actions=$("expensesActionCards"),recent=$("expensesRecentBody");
    if(!title||!summary||!actions||!recent)return;
    const staff=["sysadmin","payer"].includes(me?.role);
    if(!staff)$("view-expenses")?.classList.add("hidden");
    if(!staff)return;

    const month=$("expenseMonthFilter")?.value||monthNow();
    const external=(D.expenses||[]).filter(e=>e.category==="external"&&String(e.expense_date||"").startsWith(month));
    const monthly=(D.monthly||[]).filter(m=>String(m.month_start||"").startsWith(month));
    const externalTotal=external.reduce((sum,e)=>sum+Number(e.total_amount||0),0);
    const basicTotal=monthly.reduce((sum,m)=>sum+Number(m.water_amount||0)+Number(m.electricity_amount||0)+Number(m.gas_amount||0)+Number(m.wifi_amount||0),0);
    const total=externalTotal+basicTotal;
    const pending=(D.payments||[]).filter(p=>p.status==="pending");
    const payerIds=typeof payerFamilyIds==="function"?payerFamilyIds():[];
    const familyDebtCount=(D.families||[]).filter(f=>!payerIds.includes(f.id)&&typeof balanceForFamily==="function"&&balanceForFamily(f.id)>0.005).length;
    const registered=external.length+monthly.length;

    title.textContent=me.role==="payer"?"Gastos de la casa":"Gastos";
    subtitle.textContent=me.role==="payer"
      ?"Registra los gastos del hogar de cada mes."
      :"Registra, revisa y controla los gastos de la casa.";

    const metrics=me.role==="payer"
      ?[
        ["Total del mes",money(total),"tone-green","Incluye servicios y extras"],
        ["Gastos registrados",String(registered),"tone-purple",registered===1?"Registro de este mes":"Registros de este mes"],
        ["Pendientes de aprobar",String(pending.length),"tone-coral",pending.length===1?"Comprobante en revisión":"Comprobantes en revisión"]
      ]
      :[
        ["Total del mes",money(total),"tone-green","Servicios y gastos registrados"],
        ["Pendientes de aprobar",String(pending.length),"tone-amber",pending.length===1?"Comprobante en revisión":"Comprobantes en revisión"],
        ["Familias con deuda",String(familyDebtCount),"tone-coral",familyDebtCount===1?"Familia pendiente":"Familias pendientes"]
      ];
    summary.innerHTML=metrics.map(([label,value,tone,detail])=>`<article class="expenses-summary-card ${tone}"><span>${label}</span><strong>${value}</strong><small>${detail}</small></article>`).join("");

    const actionsData=me.role==="payer"
      ?[
        ["expenseCreatePanel","＋","Registrar gasto del mes","Añade un gasto de la casa","tone-purple"],
        ["expenseScannerArea","⌕","Escáner con IA","Lee datos del comprobante","tone-lilac"],
        ["monthlyServicesPanel","⌁","Servicios básicos","Agua, luz, gas e internet","tone-blue"]
      ]
      :[
        ["expenseScannerArea","⌕","Escáner con IA","Lee datos del comprobante","tone-lilac"],
        ["expenseCreatePanel","＋","Gastos extras","Arreglos y pagos ocasionales","tone-coral"],
        ["monthlyServicesPanel","⌁","Servicios básicos","Agua, luz, gas e internet","tone-blue"]
      ];
    actions.innerHTML=actionsData.map(([target,icon,label,detail,tone])=>`<button type="button" class="expenses-action-card ${tone}" data-expense-target="${target}"><span class="expenses-action-icon" aria-hidden="true">${icon}</span><span class="expenses-action-copy"><strong>${label}</strong><small>${detail}</small></span><span class="expenses-action-arrow" aria-hidden="true">→</span></button>`).join("");
    actions.querySelectorAll("[data-expense-target]").forEach(button=>button.onclick=()=>{
      const target=document.getElementById(button.dataset.expenseTarget);
      if(!target||target.classList.contains("hidden"))return;
      const disclosure=target.closest("details");if(disclosure)disclosure.open=true;
      target.scrollIntoView({behavior:"smooth",block:"start"});
      target.querySelector("input,select,textarea,button")?.focus({preventScroll:true});
    });

    const statusForExpense=e=>{
      const shares=(D.shares||[]).filter(s=>s.expense_id===e.id);
      const due=shares.reduce((sum,s)=>sum+Number(s.amount_due||0),0);
      const paid=shares.reduce((sum,s)=>sum+(typeof approvedPaid==="function"?approvedPaid(s.id):0),0);
      if(due>0&&due<=paid+.005)return ["Pagado","ok"];
      if(paid>0)return ["Parcial","partial"];
      return ["Pendiente","pending"];
    };
    const recentRows=[
      ...external.map(e=>{const [status,cls]=statusForExpense(e);return {date:e.expense_date||"",label:e.concept||"Gasto",amount:Number(e.total_amount||0),status,cls,icon:"🧾"};}),
      ...monthly.map(m=>({date:m.month_start||"",label:"Servicios básicos",amount:Number(m.water_amount||0)+Number(m.electricity_amount||0)+Number(m.gas_amount||0)+Number(m.wifi_amount||0),status:"Registrado",cls:"ok",icon:"⌁"}))
    ].sort((a,b)=>String(b.date).localeCompare(String(a.date))).slice(0,3);
    recent.innerHTML=recentRows.length?recentRows.map(row=>`<div class="expenses-recent-row"><span class="expenses-recent-icon" aria-hidden="true">${row.icon}</span><div class="expenses-recent-copy"><strong>${esc(row.label)}</strong><small>${String(row.date).slice(0,7)||"Este mes"}</small></div><div class="expenses-recent-value"><strong>${money(row.amount)}</strong><span class="badge ${row.cls}">${row.status}</span></div></div>`).join(""):`<p class="muted expenses-empty">Todavía no hay gastos registrados este mes.</p>`;
  }
  function renderTemplateAdmin(){
    const can=["sysadmin","payer"].includes(me.role);
    if(!$("templateAdminBox"))return;
    $("templateAdminBox").innerHTML=(can?`<form id="templateForm" class="form grid2form"><label>Concepto<input id="tplConcept" required></label><label>Tipo<select id="tplCategory"><option value="basic">Servicio básico</option><option value="external">Gasto externo</option></select></label><label>Dia límite habitual<input id="tplDay" type="number" min="1" max="28" value="10" required></label><label>Justificación base<input id="tplJust"></label><button class="primary span2">Guardar plantilla</button></form><hr>`:"")+D.templates.map(t=>{const exists=D.expenses.some(e=>e.concept===t.concept&&e.expense_date.startsWith(monthNow()));return `<div class="activity-row"><div><strong>${esc(t.concept)}</strong><div class="muted">${catName[t.category]} · vence normalmente día ${t.default_due_day} · ${exists?"ya registrado este mes":"pendiente este mes"}</div></div><div>${can&&!exists?`<button class="small-btn tpl-use" data-id="${t.id}">Usar este mes</button> `:""}${can?`<button class="small-btn tpl-delete" data-id="${t.id}">Eliminar</button>`:""}</div></div>`;}).join("");
    if(can){
      $("templateForm").onsubmit=async e=>{e.preventDefault();const {error}=await sb.from("recurring_expense_templates").insert({concept:$("tplConcept").value.trim(),category:$("tplCategory").value,default_due_day:Number($("tplDay").value),justification:$("tplJust").value.trim()||null,created_by:session.user.id});if(error)return toast(error.message);toast("Plantilla guardada.");await loadAll();};
      document.querySelectorAll(".tpl-delete").forEach(b=>b.onclick=async()=>{const {error}=await sb.from("recurring_expense_templates").delete().eq("id",b.dataset.id);if(error)return toast(error.message);await loadAll();});
      document.querySelectorAll(".tpl-use").forEach(b=>b.onclick=()=>{const t=D.templates.find(x=>x.id===b.dataset.id);if(!t)return;$("expenseConcept").value=t.concept;$("expenseCategory").value=t.category;$("expenseJustification").value=t.justification||"";$("expenseDate").value=today();const d=new Date();d.setDate(Math.min(t.default_due_day,28));$("expenseDueDate").value=d.toISOString().slice(0,10);$("expenseCategory").dispatchEvent(new Event("change"));window.scrollTo({top:0,behavior:"smooth"});toast("Plantilla cargada. Solo falta colocar el monto y comprobante.");});
    }
  }
  function openComments(expenseId){
    const e=expenseById(expenseId),comments=D.comments.filter(c=>c.expense_id===expenseId);
    $("modalContent").innerHTML=`<h2>Comentarios: ${esc(e?.concept)}</h2><div>${comments.map(c=>`<div class="activity-row"><div>${esc(c.body)}</div><small class="muted">${new Date(c.created_at).toLocaleString("es-BO")}</small></div>`).join("")||'<p class="muted">Sin comentarios.</p>'}</div><form id="commentForm" class="form"><label>Nuevo comentario<textarea id="commentBody" required></textarea></label><button class="primary">Enviar</button></form>`;
    $("modal").classList.remove("hidden");$("commentForm").onsubmit=async ev=>{ev.preventDefault();const {error}=await sb.rpc("add_expense_comment",{p_expense:expenseId,p_body:$("commentBody").value});if(error)return toast(error.message);$("modal").classList.add("hidden");await loadAll();};
  }

  async function renderPaymentQr(){
    const box=$("paymentQrBox");
    if(!box)return;
    const qr=currentQr();
    if(!qr||!qr.qr_image_path){
      box.innerHTML=`<div class="qr-empty"><div class="qr-empty-icon">QR</div><div><strong>QR todavía no configurado</strong><div class="muted">El administrador debe cargar el QR de pago.</div></div></div>`;
      return;
    }
    box.innerHTML=`<div class="qr-payment-card"><div class="qr-copy"><span class="eyebrow">PAGO RÁPIDO</span><h3>${esc(qr.qr_title||"Pago por QR")}</h3><p>${esc(qr.qr_note||"Escanea el QR, realiza el pago y luego sube la captura del comprobante.")}</p><div class="qr-rule">Después de pagar por QR, sube la foto del comprobante para revisión.</div></div><div class="qr-image-wrap"><div class="muted">Cargando QR...</div></div></div>`;
    try{
      const {data,error}=await sb.storage.from("payment-qr").createSignedUrl(qr.qr_image_path,300);
      if(error)throw error;
      const wrap=box.querySelector(".qr-image-wrap");
      if(wrap)wrap.innerHTML=`<img class="qr-image" src="${data.signedUrl}" alt="QR de pago">`;
    }catch(err){
      box.innerHTML=`<div class="qr-empty"><div class="qr-empty-icon">!</div><div><strong>No se pudo abrir el QR</strong><div class="muted">Pide al administrador que vuelva a cargarlo.</div></div></div>`;
    }
  }

  async function uploadPaymentQr(file){
    if(!file)return currentQr()?.qr_image_path||null;
    validateQrFile(file);
    const safe=file.name.replace(/[^a-zA-Z0-9._-]/g,"_");
    const path=`qr/${Date.now()}-${safe}`;
    const {error}=await sb.storage.from("payment-qr").upload(path,file,{upsert:false,contentType:file.type||"image/jpeg"});
    if(error)throw error;
    return path;
  }

  function validateQrFile(file){
    if(!["image/png","image/jpeg","image/webp"].includes(file.type))throw new Error("Elige una imagen PNG, JPG o WebP del QR.");
    if(!file.size||file.size>5*1024*1024)throw new Error("La imagen debe pesar entre 1 byte y 5 MB.");
  }
  function formFeedback(id,message,tone="success"){
    const box=$(id);box.textContent=message;box.dataset.tone=tone;box.hidden=!message;
  }
  function adminError(error){
    const message=error?.message||"No se pudo guardar. Revisa tu conexión y vuelve a intentar.";
    if(/bucket not found|NoSuchBucket/i.test(message))return "Falta activar el almacenamiento del QR en Supabase. El administrador del proyecto debe aplicar la configuración de la versión 4.2 y volver a intentar.";
    if(error?.code==="PGRST202"||/could not find the function|function .* does not exist/i.test(message))return "Falta actualizar la configuración de Supabase para esta versión. Aplica la configuración de la versión 4.2 y vuelve a intentar.";
    if(/row.level security|permission denied|unauthorized/i.test(message))return "No tienes permiso para guardar este cambio. Ingresa como administrador y comprueba la configuración de Supabase.";
    return message;
  }
  function busyForm(form,busy,label){
    const button=form.querySelector('button[type="submit"]');
    if(busy){button.dataset.label=button.textContent;button.textContent=label;}
    else button.textContent=button.dataset.label||button.textContent;
    button.disabled=busy;form.dataset.saving=String(busy);form.setAttribute("aria-busy",String(busy));
  }
  function familyOptions(id,placeholder="Selecciona una familia"){
    const select=$(id),value=select.value;
    select.innerHTML=`<option value="">${placeholder}</option>`+D.families.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join("");
    select.value=value;
  }
  function syncFamilyName(){
    const family=D.families.find(f=>String(f.id)===$("renameFamily").value);
    $("familyNameInput").value=family?.name||"";familyNameDraft=false;
  }
  function syncUserAssignment(){
    const user=D.profiles.find(p=>p.id===$("assignUser").value);
    const payer=user?.role==="payer";
    $("assignmentRole").textContent=user?roleName[user.role]:"Selecciona la cuenta que deseas actualizar.";
    $("assignFamily").value=user?.family_id?String(user.family_id):"";
    $("assignSecondFamily").value=payer&&user.second_family_id?String(user.second_family_id):"";
    $("assignSecondFamilyWrap").hidden=!payer;
    $("assignSecondFamily").required=payer;
    $("assignSecondFamily").disabled=!payer;
    $("assignmentHelp").textContent=payer?"El encargado necesita dos familias diferentes. La asignación se utilizará en los próximos registros de servicios básicos; no modifica pagos anteriores.":"El usuario verá la familia seleccionada al actualizar sus datos. Sus credenciales y su rol se conservan.";
    assignmentDraft=false;
  }
  function prepareUserAssignment(userId){
    if(me?.role!=="sysadmin")return false;
    const user=D.profiles.find(p=>p.id===userId);
    if(!user||!["family","payer"].includes(user.role))return false;
    $("assignUser").value=userId;syncUserAssignment();formFeedback("userFamiliesStatus","");return true;
  }
  $("renameFamily").onchange=()=>{syncFamilyName();formFeedback("familyNameStatus","");};
  $("familyNameInput").oninput=()=>{familyNameDraft=true;};
  $("assignUser").onchange=()=>{syncUserAssignment();formFeedback("userFamiliesStatus","");};
  ["assignFamily","assignSecondFamily"].forEach(id=>$(id).onchange=()=>{assignmentDraft=true;});
  ["qrTitle","qrNote"].forEach(id=>$(id).oninput=()=>{qrDraft=true;});
  $("qrImage").onchange=()=>{
    qrDraft=true;formFeedback("qrSaveStatus","");
    if(qrPreviewURL){URL.revokeObjectURL(qrPreviewURL);qrPreviewURL=null;}
    const box=$("qrPreview");box.replaceChildren();box.hidden=true;
    const file=$("qrImage").files[0];if(!file)return;
    try{
      validateQrFile(file);qrPreviewURL=URL.createObjectURL(file);
      const image=document.createElement("img");image.src=qrPreviewURL;image.alt="Vista previa del QR seleccionado";
      const caption=document.createElement("small");caption.textContent=file.name;
      box.append(image,caption);box.hidden=false;
    }catch(error){$("qrImage").value="";formFeedback("qrSaveStatus",error.message,"error");}
  };
  $("familyNameForm").onsubmit=async e=>{
    e.preventDefault();const form=e.currentTarget;
    if(me?.role!=="sysadmin"||form.dataset.saving==="true")return;
    const id=Number($("renameFamily").value),name=$("familyNameInput").value.trim();
    if(!id||!name)return formFeedback("familyNameStatus","Selecciona una familia y escribe su nombre.","error");
    busyForm(form,true,"Guardando nombre…");
    try{
      const {error}=await sb.rpc("casa_admin_rename_family",{p_family_id:id,p_name:name});if(error)throw error;
      familyNameDraft=false;await loadAll();formFeedback("familyNameStatus","Nombre guardado. Los datos de la familia se conservan.");
    }catch(error){formFeedback("familyNameStatus",adminError(error),"error");}
    finally{busyForm(form,false);}
  };
  $("userFamiliesForm").onsubmit=async e=>{
    e.preventDefault();const form=e.currentTarget;
    if(me?.role!=="sysadmin"||form.dataset.saving==="true")return;
    const user=D.profiles.find(p=>p.id===$("assignUser").value),first=Number($("assignFamily").value),second=user?.role==="payer"?Number($("assignSecondFamily").value):null;
    if(!user||!first||(user.role==="payer"&&(!second||first===second)))return formFeedback("userFamiliesStatus","Selecciona un usuario y sus familias. El encargado necesita dos familias diferentes.","error");
    busyForm(form,true,"Guardando asignación…");
    try{
      const {error}=await sb.rpc("casa_admin_assign_user_families",{p_user_id:user.id,p_family_id:first,p_second_family_id:second});if(error)throw error;
      assignmentDraft=false;await loadAll();formFeedback("userFamiliesStatus","Asignación guardada. El usuario puede pulsar Actualizar para ver su familia.");
    }catch(error){formFeedback("userFamiliesStatus",adminError(error),"error");}
    finally{busyForm(form,false);}
  };

  $("paymentForm").onsubmit=async e=>{
    e.preventDefault();
    if(!$("paymentShare").value)return toast("No hay una deuda seleccionada.");
    const method=$("paymentMethod")?.value||"qr";
    if(method==="qr"&&!$("paymentReceipt").files[0])return toast("Si pagaste por QR, debes subir la foto del comprobante.");
    try{
      const path=await upload($("paymentReceipt").files[0]);
      const {data:paymentId,error}=await sb.rpc("record_payment_v7",{
        p_share_id:$("paymentShare").value,
        p_amount:Number($("paymentAmount").value),
        p_paid_at:$("paymentDate").value,
        p_method:method,
        p_receipt_path:path,
        p_note:$("paymentNote").value.trim()||null,
        p_ocr_text:$("paymentOcrText").value||null
      });
      if(error)throw error;

      if(paymentId){
        sb.functions.invoke("notify-payment-upload",{body:{payment_id:paymentId}})
          .then(({data,error})=>{
            if(error)console.warn("Pago guardado; no se pudo ejecutar el correo",error);
            else if(data?.email_skipped)console.info(data.email_skipped);
          })
          .catch(console.warn);
      }

      toast(me.role==="family"?"Pago enviado para revisión. Todos recibieron un aviso.":"Pago registrado. Todos recibieron un aviso.");
      e.target.reset();
      $("paymentDate").value=today();
      $("paymentMethod").value="qr";
      togglePaymentReceiptRule();
      if($("paymentFileText"))$("paymentFileText").textContent="Toca para subir una foto";
      if($("paymentDropzone"))$("paymentDropzone").classList.remove("has-file");
      renderPaymentSelectionCard();
      await loadAll();
    }catch(err){toast(err.message);}
  };
  function renderPayments(){
    $("paymentsBody").innerHTML=D.payments.length?D.payments.map(p=>`<tr><td>${p.paid_at}</td><td>${esc(familyName(p.family_id))}</td><td>${money(p.amount)}</td><td>${money(p.applied_amount)}</td><td>${money(p.credit_amount)}</td><td><span class="badge ${p.status==="approved"?"ok":p.status==="rejected"?"rejected":"review"}">${p.status==="approved"?"Aprobado":p.status==="rejected"?"Rechazado":"En revisión"}</span></td><td>${esc(p.note||"-")}</td><td>${receiptBtn(p.receipt_path)}</td><td>${p.status==="pending"&&["sysadmin","payer"].includes(me.role)?`<button class="small-btn review-pay" data-id="${p.id}" data-ok="1">Aprobar</button> <button class="small-btn review-pay" data-id="${p.id}" data-ok="0">Rechazar</button>`:"-"}</td></tr>`).join(""):`<tr><td colspan="9">No hay pagos.</td></tr>`;
    bindReceiptButtons();document.querySelectorAll(".review-pay").forEach(b=>b.onclick=async()=>{const {error}=await sb.rpc("review_payment",{p_payment_id:b.dataset.id,p_approve:b.dataset.ok==="1"});if(error)return toast(error.message);toast("Pago revisado.");await loadAll();});
  }

  function renderBalances(){
    if(!["sysadmin","payer"].includes(me.role))return;
    const payerIds=payerFamilyIds();
    const visibleFamilies=D.families.filter(f=>!payerIds.includes(f.id));
    const payerDebt=payerIds.reduce((sum,id)=>sum+balanceForFamily(id),0);
    const payerCard=payerIds.length?`<div class="family-card payer-family-card"><h3>Familia pagadora · ${esc(payerIds.map(id=>familyName(id)).join(" + "))}</h3><div class="muted">Una sola familia con dos cuartos</div><div class="big-number ${payerDebt>0.005?"danger":"success"}">${payerDebt>0.005?money(payerDebt):"Al día"}</div><div class="muted">Deuda propia: ${money(payerDebt)}</div><div class="muted">Sus gastos registrados aparecen en el resumen mensual.</div></div>`:"";
    $("balancesCards").innerHTML=payerCard+visibleFamilies.map(f=>{
      const sh=D.shares.filter(s=>s.family_id===f.id),assigned=sh.reduce((a,s)=>a+Number(s.amount_due),0),paid=sh.reduce((a,s)=>a+approvedPaid(s.id),0),bal=Math.max(assigned-paid,0),credit=creditForFamily(f.id);
      return `<div class="family-card"><h3>${esc(f.name)}</h3><div class="muted">Asignado: ${money(assigned)}</div><div class="muted">Aplicado: ${money(paid)}</div><div class="big-number ${bal>0?"danger":"success"}">${bal>0?money(bal):"Al día"}</div><div class="success">Saldo a favor: ${money(credit)}</div>${credit>0.005?`<div class="muted credit-explanation">Se descontará en el siguiente gasto de esta familia.</div>`:""}</div>`;
    }).join("");
  }

  $("cleaningForm").onsubmit=async e=>{
    e.preventDefault();const checklist=$("cleaningChecklist").value.split("\n").map(x=>x.trim()).filter(Boolean);
    const {error}=await sb.rpc("create_cleaning_turn",{p_family_id:Number($("cleaningFamily").value),p_turn_date:$("cleaningDate").value,p_checklist:checklist,p_note:$("cleaningNote").value.trim()||null});
    if(error)return toast(error.message);toast("Turno creado.");e.target.reset();$("cleaningDate").value=today();await loadAll();
  };
  function renderCleaning(){
    if(["family","payer"].includes(me?.role)){
      renderCleaningFamilyWorkspace();
      return;
    }
    $("cleaningFocusCard")?.classList.add("hidden");
    $("cleaningTasksPanel")?.classList.add("hidden");
    $("cleaningUpcomingPanel")?.classList.add("hidden");
    $("cleaningAdminWorkspace")?.classList.remove("hidden");
    if($("cleaningViewTitle"))$("cleaningViewTitle").textContent="Calendario de limpieza";
    if($("cleaningViewSubtitle"))$("cleaningViewSubtitle").textContent="Administra los turnos semanales de la casa";
    $("cleaningBody").innerHTML=D.cleaning.length?D.cleaning.map(t=>{
      const own=me.role==="sysadmin"||me.family_id===t.family_id;
      const tasks=Array.isArray(t.checklist)?t.checklist:[];
      return `<tr><td>${t.turn_date}</td><td>${esc(familyName(t.family_id))}</td><td><span class="badge ${t.status==="done"?"ok":"pending"}">${t.status==="done"?"Realizado":"Pendiente"}</span></td><td><ul class="checklist">${tasks.map(x=>`<li>${esc(x)}</li>`).join("")}</ul></td><td>${esc(t.note||"-")}</td><td>${receiptBtn(t.completion_receipt_path)}</td><td>${own&&t.status==="pending"?`<button class="small-btn complete-clean" data-id="${t.id}">Completar</button> <button class="small-btn swap-clean" data-id="${t.id}">Cambiar turno</button>`:"-"}</td></tr>`;
    }).join(""):`<tr><td colspan="7">No hay turnos.</td></tr>`;
    document.querySelectorAll(".complete-clean").forEach(b=>b.onclick=()=>openCompleteCleaning(b.dataset.id));
    document.querySelectorAll(".swap-clean").forEach(b=>b.onclick=()=>openSwap(b.dataset.id));
    renderCleaningCalendar();
    renderSwaps();
  }

  // Colores fijos por familia (uno por familia, siempre el mismo mientras
  // no cambie el orden de families.id). Si hay más de 8 familias, se repiten.
  const FAMILY_COLORS=[
    {bg:"#fde8e8",border:"#f3b4b4",text:"#8a2323"},
    {bg:"#e8f3fd",border:"#b4d6f3",text:"#1c4e7a"},
    {bg:"#e9fbe9",border:"#b7e8b7",text:"#1f6b28"},
    {bg:"#fff4d6",border:"#f2d68a",text:"#8a5c00"},
    {bg:"#f1e8fd",border:"#d0b4f3",text:"#5a2489"},
    {bg:"#e6fbf6",border:"#a9e8dc",text:"#0f6b57"},
    {bg:"#fde8f4",border:"#f3b4dd",text:"#8a1f63"},
    {bg:"#eef0f6",border:"#c7cde0",text:"#39415e"}
  ];
  function familyColor(familyId){
    const idx=D.families.findIndex(f=>f.id===familyId);
    return FAMILY_COLORS[(idx<0?0:idx)%FAMILY_COLORS.length];
  }
  function isoWeekInfo(dateStr){
    const d=new Date(dateStr+"T12:00:00");
    const day=(d.getDay()+6)%7; // 0=lunes
    const monday=new Date(d);monday.setDate(d.getDate()-day);
    const sunday=new Date(monday);sunday.setDate(monday.getDate()+6);
    const target=new Date(monday);target.setDate(monday.getDate()+3);
    const firstThursday=new Date(target.getFullYear(),0,4);
    const weekNo=1+Math.round(((target-firstThursday)/86400000-3+((firstThursday.getDay()+6)%7))/7);
    const fmt=x=>`${String(x.getDate()).padStart(2,"0")}/${String(x.getMonth()+1).padStart(2,"0")}`;
    return {key:monday.toISOString().slice(0,10),label:`Semana ${weekNo} · ${fmt(monday)}–${fmt(sunday)}`};
  }
  function renderCleaningCalendar(){
    const box=$("cleaningCalendar");
    if(!box)return;
    if(!D.cleaning.length){box.innerHTML=`<p class="muted">Todavía no hay turnos para mostrar en el calendario.</p>`;return;}
    const byWeek={};
    D.cleaning.forEach(t=>{
      const w=isoWeekInfo(t.turn_date);
      if(!byWeek[w.key])byWeek[w.key]={label:w.label,turns:[]};
      byWeek[w.key].turns.push(t);
    });
    const weeks=Object.entries(byWeek).sort(([a],[b])=>a.localeCompare(b));
    box.innerHTML=`<div class="cleaning-calendar">`+weeks.map(([,w])=>{
      const cards=w.turns.map(t=>{
        const c=familyColor(t.family_id);
        return `<div class="week-family-card" style="background:${c.bg};border-color:${c.border};color:${c.text}">
          <strong>${esc(familyName(t.family_id))}</strong>
          <span class="badge ${t.status==="done"?"ok":"pending"}">${t.status==="done"?"Realizado":"Pendiente"}</span>
        </div>`;
      }).join("");
      return `<div class="calendar-week"><div class="calendar-week-label">${w.label}</div><div class="calendar-week-cards">${cards}</div></div>`;
    }).join("")+`</div>
    <div class="calendar-legend">${D.families.map(f=>{const c=familyColor(f.id);return `<span class="legend-chip" style="background:${c.bg};border-color:${c.border};color:${c.text}">${esc(f.name)}</span>`;}).join("")}</div>`;
  }
  function openCompleteCleaning(id){
    $("modalContent").innerHTML=`<h2>Completar limpieza</h2><form id="completeForm" class="form"><label>Foto final (opcional)<input id="completePhoto" type="file" accept="image/*"></label><label>Observación<input id="completeNote"></label><button class="primary">Marcar realizado</button></form>`;$("modal").classList.remove("hidden");
    $("completeForm").onsubmit=async e=>{e.preventDefault();try{const path=await upload($("completePhoto").files[0]);const {error}=await sb.rpc("complete_cleaning_turn",{p_turn_id:id,p_receipt_path:path,p_note:$("completeNote").value.trim()||null});if(error)throw error;$("modal").classList.add("hidden");toast("Limpieza completada.");await loadAll();}catch(err){toast(err.message);}};
  }
  function openSwap(id){
    const own=D.cleaning.find(t=>t.id===id),targets=D.cleaning.filter(t=>t.status==="pending"&&t.id!==id&&t.family_id!==own.family_id);
    $("modalContent").innerHTML=`<h2>Solicitar cambio de turno</h2>${targets.length?`<form id="swapForm" class="form"><label>Intercambiar con<select id="targetTurn">${targets.map(t=>`<option value="${t.id}">${esc(familyName(t.family_id))} · ${t.turn_date}</option>`).join("")}</select></label><button class="primary">Enviar solicitud</button></form>`:`<p>No hay otro turno pendiente disponible.</p>`}`;$("modal").classList.remove("hidden");
    if(targets.length)$("swapForm").onsubmit=async e=>{e.preventDefault();const {error}=await sb.rpc("request_cleaning_swap",{p_requester_turn:id,p_target_turn:$("targetTurn").value});if(error)return toast(error.message);$("modal").classList.add("hidden");toast("Solicitud enviada.");await loadAll();};
  }
  function renderSwaps(){
    $("swapList").innerHTML=D.swaps.length?D.swaps.map(s=>{
      const canReview=s.status==="pending"&&(me.role==="sysadmin"||me.family_id===s.target_family_id);
      return `<div class="swap-row"><div><strong>${esc(familyName(s.requester_family_id))} ↔ ${esc(familyName(s.target_family_id))}</strong><div class="muted">${s.status}</div></div>${canReview?`<div><button class="small-btn review-swap" data-id="${s.id}" data-ok="1">Aceptar</button> <button class="small-btn review-swap" data-id="${s.id}" data-ok="0">Rechazar</button></div>`:""}</div>`;
    }).join(""):`<p class="muted">No hay solicitudes.</p>`;
    document.querySelectorAll(".review-swap").forEach(b=>b.onclick=async()=>{const {error}=await sb.rpc("review_cleaning_swap",{p_request:b.dataset.id,p_accept:b.dataset.ok==="1"});if(error)return toast(error.message);await loadAll();});
  }

  $("proposalForm").onsubmit=async e=>{
    e.preventDefault();try{const path=await upload($("proposalReceipt").files[0]);const {error}=await sb.rpc("create_proposal",{p_concept:$("proposalConcept").value.trim(),p_amount:Number($("proposalAmount").value),p_justification:$("proposalJustification").value.trim(),p_receipt_path:path});if(error)throw error;toast("Propuesta publicada.");e.target.reset();await loadAll();}catch(err){toast(err.message);}
  };
  function renderProposals(){
    $("proposalList").innerHTML=D.proposals.length?D.proposals.map(p=>{
      const vs=D.votes.filter(v=>v.proposal_id===p.id),yes=vs.filter(v=>v.vote).length,no=vs.filter(v=>!v.vote).length,my=me.family_id?vs.find(v=>v.family_id===me.family_id):null;
      return `<div class="proposal-card"><div class="panel-head"><div><h3>${esc(p.concept)}</h3><div class="muted">Presupuesto ${money(p.estimated_amount)}</div></div><span class="badge ${p.status==="approved"?"ok":p.status==="rejected"?"rejected":"pending"}">${p.status==="approved"?"Aprobada":p.status==="rejected"?"Rechazada":"Abierta"}</span></div><p>${esc(p.justification)}</p><div class="vote-line"><strong>Sí: ${yes}</strong><strong>No: ${no}</strong>${receiptBtn(p.receipt_path)}</div>${p.status==="open"&&me.family_id?`<div class="row"><button class="small-btn vote-btn" data-id="${p.id}" data-v="1">Votar Sí${my?.vote===true?" ✓":""}</button><button class="small-btn vote-btn" data-id="${p.id}" data-v="0">Votar No${my?.vote===false?" ✓":""}</button></div>`:""}${p.status==="approved"&&["sysadmin","payer"].includes(me.role)?`<div class="row"><button class="small-btn proposal-to-expense" data-id="${p.id}">Convertir en gasto</button></div>`:""}</div>`;
    }).join(""):`<p class="muted">No hay propuestas.</p>`;
    document.querySelectorAll(".vote-btn").forEach(b=>b.onclick=async()=>{const {error}=await sb.rpc("cast_proposal_vote",{p_proposal:b.dataset.id,p_vote:b.dataset.v==="1"});if(error)return toast(error.message);await loadAll();});
    document.querySelectorAll(".proposal-to-expense").forEach(b=>b.onclick=()=>{const p=D.proposals.find(x=>x.id===b.dataset.id);if(!p)return;$("expenseConcept").value=p.concept;$("expenseCategory").value="external";$("expenseAmount").value=p.estimated_amount;$("expenseJustification").value=p.justification;$("expenseDate").value=today();$("expenseDueDate").value=today();const navBtn=[...document.querySelectorAll(".nav-btn")].find(x=>x.dataset.view==="expenses");showView("expenses","Gastos",navBtn);$("expenseCreatePanel").open=true;$("expenseCreatePanel").scrollIntoView({block:"start"});$("expenseCategory").dispatchEvent(new Event("change"));toast("Propuesta cargada como gasto. Revisa el monto real antes de guardar.");});
  }

  $("noticeForm").onsubmit=async e=>{e.preventDefault();const {error}=await sb.rpc("create_notice",{p_title:$("noticeTitle").value.trim(),p_body:$("noticeBody").value.trim(),p_until:$("noticeUntil").value||null});if(error)return toast(error.message);toast("Aviso publicado.");e.target.reset();await loadAll();};
  function renderNotices(){
    $("allNotices").innerHTML=D.notices.length?D.notices.map(n=>`<div class="notice-card"><h3>${esc(n.title)}</h3><p>${esc(n.body)}</p><small class="muted">${new Date(n.created_at).toLocaleString("es-BO")}${n.visible_until?" · hasta "+n.visible_until:""}</small></div>`).join(""):`<p class="muted">Sin avisos.</p>`;
  }

  $("notificationBtn").onclick=async()=>{
    $("notificationDrawer").classList.remove("hidden");
    const unread=D.notifications.filter(n=>!n.read_at).map(n=>n.id);
    if(unread.length){await sb.from("notifications").update({read_at:new Date().toISOString()}).in("id",unread);D.notifications.forEach(n=>{if(unread.includes(n.id))n.read_at=new Date().toISOString();});renderNotifications();}
  };
  $("closeNotifBtn").onclick=()=> $("notificationDrawer").classList.add("hidden");
  function renderNotifications(){
    const unread=D.notifications.filter(n=>!n.read_at).length;$("notifCount").textContent=unread;
    $("notificationList").innerHTML=D.notifications.length?D.notifications.map(n=>`<div class="notif-row"><div><strong>${esc(n.title)}</strong><p>${esc(n.body)}</p><small class="muted">${new Date(n.created_at).toLocaleString("es-BO")}</small></div>${!n.read_at?'<span class="badge review">Nuevo</span>':""}</div>`).join(""):`<p class="muted">Sin notificaciones.</p>`;
  }
  async function requestPhoneNotifications(){
    if(!window.isSecureContext && location.hostname!=="localhost" && location.hostname!=="127.0.0.1"){
      return toast("En el celular, las notificaciones se activan cuando publiquemos la app con HTTPS.");
    }
    if(!("Notification" in window))return toast("Este navegador no admite notificaciones.");
    const p=await Notification.requestPermission();
    toast(p==="granted"?"Avisos activados en este dispositivo.":"No se concedió el permiso de notificaciones.");
  }
  if($("browserNotifyBtn"))$("browserNotifyBtn").onclick=requestPhoneNotifications;
  if($("phoneNotifyBtn"))$("phoneNotifyBtn").onclick=requestPhoneNotifications;

  async function showSystemNotification(title,options={}){
    if(!("Notification" in window)||Notification.permission!=="granted")return;
    try{
      const reg=("serviceWorker" in navigator)?await navigator.serviceWorker.getRegistration():null;
      if(reg){
        await reg.showNotification(title,{icon:"icons/icon-192.png",badge:"icons/icon-192.png",tag:"casa-familias-payment",renotify:true,...options});
      }else{
        new Notification(title,{icon:"icons/icon-192.png",...options});
      }
    }catch(err){console.warn("No se pudo mostrar la notificación del sistema",err);}
  }

  async function browserNotifyUnread(){
    if(!("Notification" in window)||Notification.permission!=="granted")return;
    const last=localStorage.getItem("cf_last_notification");
    const latest=D.notifications.find(n=>!n.read_at&&n.id!==last);
    if(latest){
      await showSystemNotification(latest.title,{body:latest.body,data:{url:location.origin+location.pathname}});
      localStorage.setItem("cf_last_notification",latest.id);
    }
  }

  function monthRows(month){
    const ex=D.expenses.filter(e=>e.expense_date.startsWith(month));
    return ex.map(e=>{
      const sh=D.shares.filter(s=>s.expense_id===e.id),assigned=sh.reduce((a,s)=>a+Number(s.amount_due),0),paid=sh.reduce((a,s)=>a+approvedPaid(s.id),0);
      return {Fecha:e.expense_date,Concepto:e.concept,Tipo:catName[e.category],Total:Number(e.total_amount),Asignado:assigned,Reembolsado:paid,Pendiente:Math.max(assigned-paid,0),Justificación:e.justification||""};
    });
  }
  function renderReports(){
    const y=String($("reportYear").value||new Date().getFullYear());
    const list=D.expenses.filter(e=>e.expense_date.startsWith(y));
    const by={};list.forEach(e=>{const k=e.concept;by[k]=(by[k]||0)+Number(e.total_amount);});
    $("annualSummary").innerHTML=`<div class="big-number">${money(list.reduce((a,e)=>a+Number(e.total_amount),0))}</div>`+Object.entries(by).sort((a,b)=>b[1]-a[1]).map(([k,v])=>`<div class="activity-row"><span>${esc(k)}</span><strong>${money(v)}</strong></div>`).join("");
    $("periodList").innerHTML=D.periods.map(p=>`<div class="activity-row"><div><strong>${p.month_start.slice(0,7)}</strong> <span class="badge ${p.status}">${p.status==="closed"?"Cerrado":"Abierto"}</span></div>${me.role==="sysadmin"&&p.status==="closed"?`<button class="small-btn reopen-period" data-m="${p.month_start}">Reabrir</button>`:""}</div>`).join("");
    document.querySelectorAll(".reopen-period").forEach(b=>b.onclick=async()=>{const {error}=await sb.rpc("reopen_period",{p_month:b.dataset.m});if(error)return toast(error.message);await loadAll();});
  }
  $("reportYear").onchange=renderReports;
  $("closeMonthBtn").onclick=async()=>{const m=$("closeMonth").value;if(!m)return;const {error}=await sb.rpc("close_period",{p_month:m+"-01"});if(error)return toast(error.message);toast("Mes cerrado.");await loadAll();};
  $("exportXlsxBtn").onclick=()=>{
    const month=$("reportMonth").value,rows=monthRows(month),wb=XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb,XLSX.utils.json_to_sheet(rows),"Gastos");
    const payments=D.payments.filter(p=>p.paid_at.startsWith(month)).map(p=>({Fecha:p.paid_at,Familia:familyName(p.family_id),Monto:Number(p.amount),Aplicado:Number(p.applied_amount),Crédito:Number(p.credit_amount),Estado:p.status,Nota:p.note||""}));
    XLSX.utils.book_append_sheet(wb,XLSX.utils.json_to_sheet(payments),"Pagos");
    const balances=D.families.map(f=>{const sh=D.shares.filter(s=>s.family_id===f.id&&expenseById(s.expense_id)?.expense_date.startsWith(month));const due=sh.reduce((a,s)=>a+Number(s.amount_due),0),paid=sh.reduce((a,s)=>a+approvedPaid(s.id),0);return {Familia:f.name,Asignado:due,Pagado:paid,Pendiente:Math.max(due-paid,0),Saldo_a_favor:creditForFamily(f.id)};});
    XLSX.utils.book_append_sheet(wb,XLSX.utils.json_to_sheet(balances),"Familias");
    if(window.AndroidFiles)AndroidFiles.save(`Casa_Familias_${month}.xlsx`,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",XLSX.write(wb,{bookType:"xlsx",type:"base64",compression:true}));else XLSX.writeFile(wb,`Casa_Familias_${month}.xlsx`,{compression:true});
  };
  $("exportPdfBtn").onclick=()=>{
    const month=$("reportMonth").value,rows=monthRows(month),{jsPDF}=window.jspdf,doc=new jsPDF();
    doc.setFontSize(16);doc.text(`Casa Familias - Reporte ${month}`,14,16);doc.setFontSize(10);doc.text(`Generado: ${new Date().toLocaleString("es-BO")}`,14,23);
    doc.autoTable({startY:29,head:[["Fecha","Concepto","Tipo","Total","Pendiente"]],body:rows.map(r=>[r.Fecha,r.Concepto,r.Tipo,money(r.Total),money(r.Pendiente)])});
    if(window.AndroidFiles)AndroidFiles.save(`Casa_Familias_${month}.pdf`,"application/pdf",doc.output("datauristring").split(",")[1]);else doc.save(`Casa_Familias_${month}.pdf`);
  };

  $("userForm").onsubmit=async e=>{
    e.preventDefault();
    const role=$("newRole").value;
    const body={
      action:"create",
      display_name:$("newDisplayName").value.trim(),
      username:$("newUsername").value.trim(),
      email:$("newEmail").value.trim(),
      password:$("newPassword").value,
      role,
      family_id:["family","payer"].includes(role)&&$("newFamily").value?Number($("newFamily").value):null,
      second_family_id:role==="payer"&&$("newSecondFamily").value?Number($("newSecondFamily").value):null
    };
    const {data,error}=await sb.functions.invoke("admin-users",{body});if(error)return toast(await edgeErrorMessage(error,"No se pudo crear el usuario."));if(data?.error)return toast(data.error);toast("Usuario creado.");e.target.reset();await loadAll();
  };
  if($("payerFamiliesForm"))$("payerFamiliesForm").onsubmit=async e=>{
    e.preventDefault();
    if(me.role!=="sysadmin")return;
    const f1=Number($("payerFamily1").value),f2=Number($("payerFamily2").value);
    if(f1===f2)return toast("Las dos familias deben ser diferentes.");
    const {error}=await sb.rpc("set_payer_families",{p_first_family:f1,p_second_family:f2});
    if(error)return toast(error.message);
    toast("Familias del Encargado de Pagos actualizadas.");
    await loadAll();
  };


  if($("qrSettingsForm"))$("qrSettingsForm").onsubmit=async e=>{
    e.preventDefault();
    const form=e.currentTarget;
    if(me?.role!=="sysadmin"||form.dataset.saving==="true")return;
    const file=$("qrImage").files[0];let uploadedPath=null,saved=false;
    formFeedback("qrSaveStatus","");busyForm(form,true,"Guardando QR…");
    try{
      const qrPath=await uploadPaymentQr(file);
      if(!qrPath)throw new Error("Selecciona una imagen del QR.");
      if(file)uploadedPath=qrPath;
      const {error}=await sb.rpc("save_payment_qr_settings",{
        p_qr_image_path:qrPath,
        p_qr_title:$("qrTitle").value.trim()||"Pago por QR",
        p_qr_note:$("qrNote").value.trim()||null
      });
      if(error)throw error;
      saved=true;qrDraft=false;
      $("qrImage").value="";
      if(qrPreviewURL){URL.revokeObjectURL(qrPreviewURL);qrPreviewURL=null;}
      $("qrPreview").replaceChildren();$("qrPreview").hidden=true;
      await loadAll();
      formFeedback("qrSaveStatus","QR guardado. Las familias ya pueden verlo en Pagos.");
    }catch(err){
      // Limpia solo rechazos confirmados del servidor. Si se perdió la respuesta,
      // la referencia podría haberse guardado: conservar su imagen evita romperla.
      if(uploadedPath&&!saved&&["PGRST202","42501","22023"].includes(err?.code)){
        try{await sb.storage.from("payment-qr").remove([uploadedPath]);}catch(_){}
      }
      formFeedback("qrSaveStatus",adminError(err),"error");
    }finally{busyForm(form,false);}
  };

  $("backupBtn").onclick=()=>{
    const backup={exported_at:new Date().toISOString(),families:D.families,periods:D.periods,templates:D.templates,expenses:D.expenses,monthly_basic_services:D.monthly,utility_baselines:D.utilityBaselines,utility_credits:D.utilityCredits,external_utility_debts:D.externalDebts,shares:D.shares,credits:D.credits,payments:D.payments,cleaning_turns:D.cleaning,cleaning_swaps:D.swaps,proposals:D.proposals,votes:D.votes,notices:D.notices,comments:D.comments,audit:D.audit,receipt_paths:[...new Set([...D.expenses,...D.monthly,...D.externalDebts,...D.payments,...D.proposals,...D.cleaning].flatMap(x=>[x.receipt_path,x.completion_receipt_path,x.water_receipt_path,x.electricity_receipt_path,x.gas_receipt_path,x.wifi_receipt_path]).filter(Boolean))]};
    const blob=new Blob([JSON.stringify(backup,null,2)],{type:"application/json"}),a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download=`casa-familias-backup-${today()}.json`;a.click();URL.revokeObjectURL(a.href);
  };
  function renderAdmin(){
    if(me.role!=="sysadmin")return;
    $("usersBody").innerHTML=D.profiles.map(p=>`<tr><td>${esc(p.display_name)}</td><td>${esc(p.username)}</td><td>${esc(p.contact_email)}</td><td>${roleName[p.role]}</td><td>${p.family_id?esc(familyName(p.family_id)):"-"}</td><td>${p.second_family_id?esc(familyName(p.second_family_id)):"-"}</td></tr>`).join("");
    $("auditBody").innerHTML=D.audit.map(a=>`<tr><td>${new Date(a.created_at).toLocaleString("es-BO")}</td><td>${a.user_id||"-"}</td><td>${esc(a.action)}</td><td>${esc(a.entity)}</td><td>${esc(JSON.stringify(a.details||{}))}</td></tr>`).join("")||`<tr><td colspan="5">Sin registros.</td></tr>`;
    const qr=currentQr();
    if(!qrDraft){$("qrTitle").value=qr?.qr_title||"Pago por QR";$("qrNote").value=qr?.qr_note||"";}
    if($("currentQrAdminInfo"))$("currentQrAdminInfo").textContent=qr?.qr_image_path
      ?"Ya existe un QR cargado. Si no eliges otra imagen, se conservará el actual."
      :"Aún no hay un QR cargado.";
    ["renameFamily","assignFamily","assignSecondFamily"].forEach(id=>familyOptions(id));
    const userSelect=$("assignUser"),selected=userSelect.value;
    userSelect.innerHTML='<option value="">Selecciona un usuario</option>'+D.profiles.filter(p=>["family","payer"].includes(p.role)).map(p=>`<option value="${esc(p.id)}">${esc(p.display_name||p.username)} · ${roleName[p.role]}</option>`).join("");
    userSelect.value=selected;
    if(!familyNameDraft)syncFamilyName();
    if(!assignmentDraft)syncUserAssignment();
  }

  $("modalClose").onclick=()=> $("modal").classList.add("hidden");
  $("modal").onclick=e=>{if(e.target===$("modal"))$("modal").classList.add("hidden");};

  function renderAll(){
    renderDashboard();renderMonthlyServices();renderExternalDebts();renderExpenses();renderExpensesWorkspace();renderPayments();renderBalances();renderCleaning();renderProposals();renderNotices();renderNotifications();renderReports();renderAdmin();renderTemplateAdmin();populate();renderPaymentQr();togglePaymentReceiptRule();updatePendingCreditsInfo();
  }

  window.CF={state:()=>({me,D,familyDebts}),prepareUserAssignment,openCompleteCleaning,openSwap,openComments,loadAll,logout,receiptBtn,receiptURL:async path=>{const {data,error}=await sb.storage.from(cfg.RECEIPTS_BUCKET||"receipts").createSignedUrl(path,300);if(error)throw error;return data.signedUrl;},bindReceiptButtons,showView,renderReports,toast,balanceForFamily,creditForFamily};
  window.dispatchEvent(new Event("cf:ready"));
  const {data,error:sessionError}=await sb.auth.getSession();
  const s=data?.session;
  if(recoveryMode&&s&&!sessionError){session=s;showAuthScreen("resetScreen");}
  else if(s&&!sessionError){
    session=s;
    try{await enter(s.user);}catch(_){showAuthScreen("loginScreen");$("loginError").textContent="No se pudo cargar tu cuenta. Revisa internet y vuelve a ingresar.";}
  }else{
    showAuthScreen("loginScreen");
    if(recoveryMode)$("loginError").textContent="El enlace de recuperación no es válido o venció. Solicita uno nuevo.";
    else if(sessionError)$("loginError").textContent="Tu sesión terminó. Vuelve a ingresar.";
  }
}
