const assert = require('node:assert/strict');
const { setup } = require('./test_admin.cjs');
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
let checks = 0;
function check(value, label) { assert(value, label); checks++; }
(async () => {
  const h = await setup(); const { w, $, change, input, submit } = h;
  try {
    h.tables.cleaning_family_aliases = [{ family_id:2, household_id:1 }];
    h.tables.families = [1,2,3,4,5,6].map(id => ({ id, name: 'Familia ' + id }));
    const anchor = new Date(); anchor.setUTCHours(12,0,0,0);
    anchor.setUTCDate(anchor.getUTCDate() - (anchor.getUTCDay()+6)%7 - 7);
    const dates = [0,1,2,3,4].map(i => { const date = new Date(anchor); date.setUTCDate(date.getUTCDate()+i*7); return date.toISOString().slice(0,10); });
    h.tables.cleaning_turns = [4,5,1,6,3].map((id,i) => ({ id:'turn'+i, family_id:id, turn_date:dates[i], status:'pending', checklist:[] }));
    await w.CF.loadAll();
    w.Mobile.go('cleaning');
    check(w.document.querySelector('[data-source="cleaningRotationPanel"]'), 'Admin has explicit repeat button');
    w.document.querySelector('[data-source="cleaningRotationPanel"]').click();
    check(!$('sourceOverlay').hidden, 'Repeat button opens settings');
    check($('sourceOverlay').querySelector('header button').textContent === 'Cerrar', 'Close uses a labeled button');
    const order = () => [...$('cleaningRotationOrder').querySelectorAll('select')].map(x => Number(x.value));
    assert.deepEqual(order(), [4,5,1,6,3]); checks++;
    check($('cleaningRotationOrder').querySelectorAll('select').length === 5, 'Shared accounts occupy one position');
    check($('cleaningSharedFamilies').textContent.includes('comparten un solo turno'), 'Grouping is explained');
    check($('rotationAnchor').value === dates[0], 'Anchor follows the most recent weekly chain');
    check($('rotationPreview').querySelectorAll('li').length === 6, 'Six-week preview');
    check($('rotationPreview').textContent.includes('Ya registrada'), 'Preview preserves manual assignments');
    $('rotationFamily1').value = '4'; change('rotationFamily1'); const before = h.calls.length;
    await submit('cleaningRotationForm');
    check(h.calls.length === before && $('rotationFeedback').textContent.includes('una sola vez'), 'Duplicate family blocked before network');
    $('rotationFamily1').value = '5'; change('rotationFamily1'); input('rotationAnchor', dates[0]);
    input('rotationTasks','Baño\nCocina');
    await w.CF.loadAll();
    check($('rotationTasks').value === 'Baño\nCocina', 'Background refresh preserves rotation draft');
    h.setError({ code:'PGRST202', message:'Could not find the function' }); await submit('cleaningRotationForm');
    check($('rotationFeedback').textContent.includes('Supabase') && !$('rotationEnableBtn').disabled, 'Missing backend leaves retry enabled');
    check($('rotationTasks').value === 'Baño\nCocina', 'Failed save preserves tasks');
    h.setError(null); h.hold(); const save = submit('cleaningRotationForm'); await tick();
    const count = h.calls.length; await submit('cleaningRotationForm');
    check(h.calls.length === count && $('rotationPauseBtn').disabled, 'Repeated activation sends one request');
    h.release(); await save;
    check(w.CF.state().D.rotation.enabled && !$('rotationPauseBtn').hidden, 'Enabled plan loaded');
    assert.deepEqual(Array.from(h.calls.at(-1).args.p_family_order), [4,5,1,6,3]); checks++;
    check($('rotationEnableBtn').textContent === 'Guardar orden y continuar', 'Button reflects active state after save');
    check($('rotationFeedback').textContent.includes('7 semanas'), 'New weeks are reported');
    await $('rotationPauseBtn').onclick();
    check(h.calls.at(-1).args.p_enabled === false && !w.CF.state().D.rotation.enabled, 'Admin can pause');
    check($('rotationPauseBtn').hidden && $('rotationFeedback').textContent.includes('se conservan'), 'Pause explains existing turns remain');
    check(w.CF.canManageCleaning(1), 'Admin can manage a shared turn');
    w.Mobile.closeSource();
    for (const view of ['home','payments','cleaning','settings','more','users']) {
      w.Mobile.go(view);
      check(!w.document.querySelector('#mobileApp .back-button,#mobileApp .chevron,#mobileApp .shortcut-arrow'), 'No arrow navigation on ' + view);
      check(!/[‹›←→]/.test($('mobileApp').textContent), 'No arrow-only controls on ' + view);
    }
    w.Mobile.go('payments');
    const picker = w.document.querySelector('[data-month-picker]');
    if (picker) { picker.value='2026-12'; picker.dispatchEvent(new w.Event('change')); }
  } finally { h.dom.window.close(); }
  const shared = await setup('family');
  try {
    shared.tables.cleaning_family_aliases = [{ family_id:2, household_id:1 }];
    shared.tables.profiles[0].family_id = 2;
    const date = new Date().toISOString().slice(0,10);
    shared.tables.cleaning_turns = [{ id:'shared-turn', family_id:1, turn_date:date, status:'pending', checklist:['Baño'] }];
    await shared.w.CF.loadAll();
    check(shared.w.CF.canManageCleaning(1), 'Second account can manage the canonical household in UI');
    check(!shared.w.CF.canManageCleaning(3), 'UI rejects other households');
    shared.w.Mobile.go('cleaning');
    shared.w.document.querySelector('[data-turn="shared-turn"]').click();
    check(shared.$('completeTurn'), 'Shared account has a complete-turn button');
  } finally { shared.dom.window.close(); }
  for (const role of ['family','payer','restaurant']) {
    const h = await setup(role);
    try {
      h.w.Mobile.openSource('cleaningRotationPanel','Repetición');
      check(h.$('sourceOverlay').hidden, role + ' cannot open schedule settings');
      const count = h.calls.length; await h.submit('cleaningRotationForm');
      check(h.calls.length === count, role + ' cannot configure rotation');
    } finally { h.dom.window.close(); }
  }
  console.log(JSON.stringify({rotationInterfaceChecks:checks,mockedBackend:true,result:'PASS'}));
})().catch(error => { console.error(error); process.exitCode=1; });
