/* Browser-only synthetic backend. Never deployed with the website. */
window.CASA_TEST = { calls: [], uploads: [], requests: new Set(), loseFirstReply: false };
window.supabase = { createClient() {
  const role = window.TEST_ROLE || 'family';
  const families = ['Salinas', 'Salinas2', 'Acha', 'Choque', 'Galarza', 'David'].map((name, i) => ({ id: i+1, name: 'Familia '+name }));
  const months = ['2026-08','2026-09','2026-10'];
  const paid = window.TEST_UNPAID ? [0,0,0] : [80,40,0];
  const debts = months.map((month,i) => ({ share_id:'s'+i,expense_id:'e'+i,concept:'Servicios de la casa',expense_date:month+'-01',month_start:month+'-01',due_date:month+'-30',amount_due:80,amount_paid:paid[i],balance:80-paid[i] }));
  const tables = {
    families,
    profiles:[{id:'fixture-user',display_name:role==='family'?'Familia Salinas':'Alex',contact_email:'fixture@example.test',role,family_id:role==='sysadmin'?null:1,second_family_id:role==='payer'?2:null}],
    expenses:months.map((month,i)=>({id:'e'+i,concept:'Servicios de la casa',category:'basic',expense_date:month+'-01',due_date:month+'-30',total_amount:480})),
    monthly_basic_services:months.map((month,i)=>({id:'m'+i,month_start:month+'-01',due_date:month+'-30',expense_id:'e'+i,monthly_expense_id:'e'+i,water_amount:60,electricity_amount:240,gas_amount:0,wifi_amount:180,family_total_amount:480,calculation_method:'bill_total',family_count:6})),
    expense_shares:months.flatMap((month,i)=>families.map(f=>({id:f.id===1?'s'+i:'s'+i+'-'+f.id,expense_id:'e'+i,family_id:f.id,amount_due:80}))),
    payments:paid.flatMap((amount,i)=>amount?[{id:'p'+i,share_id:'s'+i,family_id:1,amount,applied_amount:amount,credit_amount:0,kind:'manual',status:'approved',paid_at:months[i]+'-15',created_at:months[i]+'-15T12:00:00Z'}]:[]),
    payment_batches:[],
    cleaning_turns:[{id:'t1',family_id:1,turn_date:'2026-10-26',status:'pending',checklist:['Baño','Cocina','Basura']}],
    cleaning_family_aliases:[{family_id:2,household_id:1}],
    family_credits:[],utility_credits:[],notices:[],notifications:[],payment_qr_settings:[],proposals:[],proposal_votes:[],cleaning_swap_requests:[]
  };
  window.CASA_TEST.tables=tables;
  const query=table=>{
    const q={};for(const name of ['select','eq','order','limit','in','range','insert','update','delete'])q[name]=()=>q;
    q.single=async()=>({data:tables[table]?.[0]||{},error:null});q.maybeSingle=async()=>({data:tables[table]?.[0]||null,error:null});
    q.then=(resolve,reject)=>Promise.resolve({data:tables[table]||[],error:null}).then(resolve,reject);return q;
  };
  return {
    from:query,
    rpc:async(name,body)=>{
      window.CASA_TEST.calls.push({name,body:body?structuredClone(body):null});
      if(name==='family_portal_debts')return{data:debts,error:null};
      if(name==='casa_record_payment_batch'){
        if(!window.CASA_TEST.requests.has(body.p_request_id)){
          window.CASA_TEST.requests.add(body.p_request_id);
          tables.payment_batches.push({id:body.p_request_id,family_id:body.p_family_id,amount:body.p_amount,status:'pending',paid_at:body.p_paid_at,created_at:'2026-10-29T12:00:00Z',receipt_path:body.p_receipt_path,planned_allocation:[]});
          tables.payments.push({id:body.p_request_id,batch_id:body.p_request_id,share_id:'s0',family_id:body.p_family_id,amount:body.p_amount,status:'pending',paid_at:body.p_paid_at});
        }
        if(window.CASA_TEST.loseFirstReply){window.CASA_TEST.loseFirstReply=false;return{error:{message:'Simulated lost reply; try again'}};}
        return{data:body.p_request_id,error:null};
      }
      return{data:[],error:null};
    },
    auth:{onAuthStateChange:()=>{},getSession:async()=>({data:{session:{user:{id:'fixture-user',email:'fixture@example.test'}}}}),signOut:async()=>({error:null})},
    channel:()=>({on(){return this},subscribe(){return this}}),
    storage:{from:()=>({upload:async(path,file)=>{window.CASA_TEST.uploads.push({path,type:file.type,size:file.size});return{error:null};},createSignedUrl:async()=>({data:{signedUrl:'https://casa.test/fixture-image.png'},error:null})})}
  };
} };
