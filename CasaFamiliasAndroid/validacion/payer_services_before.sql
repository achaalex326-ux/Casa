select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$
begin
 begin
  perform public.create_monthly_basic_services('2026-10-01',100,100,200,200,'2026-10-20',null,null,null,null);
  raise exception 'El fallo original no fue reproducido';
 exception when undefined_column then
  if position('payment_method' in sqlerrm)=0 then raise; end if;
  perform test.check(true,'Regresión original reproducida: payment_method no existe');
 end;
end $$;
select test.check((select count(*)=0 from public.monthly_basic_services),'El intento original fallido no dejó meses parciales');
