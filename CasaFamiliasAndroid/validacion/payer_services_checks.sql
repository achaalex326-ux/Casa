select test.check((select public.share_approved_paid('20000000-0000-0000-0000-000000000001')=100),'Primera familia cubierta hasta su cuota');
select test.check((select public.share_approved_paid('20000000-0000-0000-0000-000000000002')=100),'Segunda familia cubierta hasta su cuota');
select test.check((select count(*)=2 from public.payments where kind='auto'),'Una cobertura por cuota, sin duplicados tras reaplicar migración');
select test.check((select amount=60 and applied_amount=60 and credit_amount=0 from public.payments where kind='auto' and family_id=1),'Un abono previo se descuenta: solo se regulariza el restante');
select test.check((select amount=100 from public.payments where kind='auto' and family_id=2),'Un comprobante pendiente no se contabiliza como aprobado');
select test.check((select count(*)=0 from public.payments where family_id not in(1,2)),'No se pagan cuotas de otras familias');
select test.check(public.share_approved_paid('20000000-0000-0000-0000-000000000007')=0,'Los gastos extraordinarios se mantienen pendientes');
select test.check(not exists((select * from test.original_payments) except (select * from public.payments)),'Pagos existentes íntegros');
select test.check(not exists((select * from test.original_shares) except (select * from public.expense_shares)),'Importes de cuotas originales íntegros');
select test.check(not exists((select * from test.original_expenses) except (select * from public.expenses)),'Gastos originales íntegros');
select test.check(not exists((select * from test.original_credits) except (select * from public.family_credits)),'Créditos previos no consumidos por la regularización');
select test.check((select count(*)=2 from test.audit_log where action='cover_payer_services'),'Cada cobertura regularizada tiene auditoría');
select test.check((select bool_and(created_by is not null and reviewed_by is not null and reviewed_at is not null and status='approved') from public.payments where kind='auto'),'Actor, revisión y estado conservados');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select public.create_monthly_basic_services('2026-10-01',100,100,200,200,'2026-10-20',null,null,null,null);
select test.check((select count(*)=1 from public.monthly_basic_services),'Mes nuevo registrado con la función corregida');
select test.check((select expense_id=monthly_expense_id and expense_id is not null from public.monthly_basic_services),'Los enlaces del portal y el gasto mensual coinciden');
select test.check((select count(*)=6 from public.expense_shares where expense_id=(select expense_id from public.monthly_basic_services)),'Se conserva el reparto de seis identidades');
select test.check((select sum(amount_due)=600 from public.expense_shares where expense_id=(select expense_id from public.monthly_basic_services)),'Reparto total consistente');
select test.check(not exists(select 1 from public.expense_shares s where family_id in(1,2) and expense_id=(select expense_id from public.monthly_basic_services) and amount_due<>public.share_approved_paid(s.id)),'Meses futuros: ambas familias del encargado quedan en cero');
select test.check((select balance=15 from public.family_credits where family_id=1),'No se gasta el crédito del encargado para cubrir facturas');
select test.check((select balance=0 from public.family_credits where family_id=3),'Las demás familias siguen usando su crédito normal');
select test.check((select public.share_approved_paid(s.id)=20 from public.expense_shares s where family_id=3 and expense_id=(select expense_id from public.monthly_basic_services)),'Solo se cubre el crédito disponible de la familia ordinaria');
select test.check((select count(*)=4 from public.payments where kind='auto'),'Dos coberturas nuevas, sin duplicación');
do $$ begin
 begin
  perform public.create_monthly_basic_services('2026-10-01',100,100,200,200,'2026-10-20',null,null,null,null);
  raise exception 'Se permitió registrar el mes dos veces';
 exception when others then
  if position('Ese mes ya tiene' in sqlerrm)=0 then raise;end if;
  perform test.check(true,'Mes duplicado rechazado');
 end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
do $$ begin
 begin
  perform public.create_monthly_basic_services('2026-11-01',100,100,200,200,'2026-11-20',null,null,null,null);
  raise exception 'Una familia pudo registrar servicios';
 exception when others then
  if position('No tienes permiso' in sqlerrm)=0 then raise;end if;
  perform test.check(true,'Familia ordinaria no puede registrar servicios');
 end;
end $$;
select set_config('request.jwt.claim.sub','',false);
do $$ begin
 begin
  perform public.create_monthly_basic_services('2026-11-01',100,100,200,200,'2026-11-20',null,null,null,null);
  raise exception 'Un anónimo pudo registrar servicios';
 exception when others then
  if position('No tienes permiso' in sqlerrm)=0 then raise;end if;
  perform test.check(true,'Sin autenticación no se registran servicios');
 end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.create_monthly_basic_services('2026-11-01',100,100,200,200,'2026-11-20',null,null,null,null,0,0,true);
select test.check((select monthly_expense_id is null and expense_id is null and family_total_amount=0 from public.monthly_basic_services where month_start='2026-11-01'),'Referencia histórica no genera deuda ni pagos');
select test.check((select count(*)=4 from public.payments where kind='auto'),'Referencia histórica no genera coberturas adicionales');
select json_build_object('payerServicesChecks',count(*),'status','PASS') from test.results;
