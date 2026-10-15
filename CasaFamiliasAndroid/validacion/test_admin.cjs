const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const web = path.join(__dirname, '../app/src/main/assets/web');
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
let checks = 0;
function check(condition, label) { assert(condition, label); checks++; }
async function setup(role = 'sysadmin') {
  const dom = new JSDOM(fs.readFileSync(path.join(web, 'index.html'), 'utf8'), {
    url: 'https://appassets.androidplatform.net/web/index.html', runScripts: 'outside-only', pretendToBeVisual: true
  });
  const w = dom.window;
  w.scrollTo = () => {};
  w.URL.createObjectURL = () => 'blob:qr-preview'; w.URL.revokeObjectURL = () => {};
  const tables = {
    families: [{ id: 1, name: 'Familia Uno' }, { id: 2, name: 'Familia Dos' }, { id: 3, name: 'Familia Tres' }],
    profiles: [
      { id: 'me', role, display_name: 'Mi cuenta', family_id: role === 'family' ? 1 : null },
      { id: 'u1', role: 'family', display_name: 'Ana', family_id: 1 },
      { id: 'p1', role: 'payer', display_name: 'Encargado', family_id: 1, second_family_id: 2 }
    ], payment_qr_settings: []
  };
  const calls = []; const removed = []; const uploads = [];
  let rpcError = null, uploadError = null, release = null, hold = false;
  function from(table) {
    const q = {}; let eq = null;
    for (const method of ['select', 'order', 'limit', 'in']) q[method] = () => q;
    q.eq = (field, value) => { eq = [field, value]; return q; };
    const rows = () => (tables[table] || []).filter(row => !eq || row[eq[0]] === eq[1]);
    q.single = async () => ({ data: rows()[0], error: null });
    q.maybeSingle = async () => ({ data: rows()[0] || null, error: null });
    q.then = (resolve, reject) => Promise.resolve({ data: rows(), error: null }).then(resolve, reject);
    return q;
  }
  const client = { from, auth: { onAuthStateChange() {}, getSession: async () => ({ data: { session: { user: { id: 'me' } } } }) },
    channel: () => ({ on() { return this; }, subscribe() { return this; } }),
    storage: { from: () => ({
      upload: async (key, file) => { uploads.push({ key, file }); return { error: uploadError }; },
      remove: async keys => { removed.push(...keys); return { error: null }; },
      createSignedUrl: async () => ({ data: { signedUrl: 'https://example.test/qr.png' }, error: null })
    }) },
    rpc: async (name, args) => {
      if (name === 'generate_due_notifications') return { data: [], error: null };
      calls.push({ name, args });
      if (hold) await new Promise(resolve => { release = resolve; });
      if (rpcError) return { error: rpcError };
      if (name === 'casa_admin_rename_family') tables.families.find(f => f.id === args.p_family_id).name = args.p_name;
      if (name === 'casa_admin_assign_user_families') Object.assign(tables.profiles.find(p => p.id === args.p_user_id), { family_id: args.p_family_id, second_family_id: args.p_second_family_id });
      if (name === 'casa_configure_cleaning_rotation') {
        tables.cleaning_rotation_settings = [{ enabled: args.p_enabled, family_order: [...args.p_family_order], anchor_monday: args.p_anchor_monday, default_checklist: [...args.p_default_checklist] }];
        return { data: { enabled: args.p_enabled, created_weeks: args.p_enabled ? 7 : 0 }, error: null };
      }
      if (name === 'save_payment_qr_settings') tables.payment_qr_settings = [{ qr_image_path: args.p_qr_image_path, qr_title: args.p_qr_title, qr_note: args.p_qr_note }];
      return { data: [], error: null };
    }
  };
  w.supabase = { createClient: () => client };
  for (const file of ['mobile.js', 'config.js', 'app.js']) w.eval(fs.readFileSync(path.join(web, file), 'utf8'));
  await tick();
  assert(w.CF.state().me, 'Account entered');
  const $ = id => w.document.getElementById(id);
  const change = id => $(id).dispatchEvent(new w.Event('change', { bubbles: true }));
  const input = (id, value) => { $(id).value = value; $(id).dispatchEvent(new w.Event('input', { bubbles: true })); };
  const submit = id => $(id).onsubmit({ preventDefault() {}, currentTarget: $(id) });
  const file = (type = 'image/png', size = 10) => {
    const image = new w.File(['a'.repeat(size)], 'codigo.png', { type });
    Object.defineProperty($('qrImage'), 'files', { value: [image], configurable: true }); return image;
  };
  return { dom, w, $, change, input, submit, file, tables, calls, uploads, removed,
    setError: error => { rpcError = error; }, setUploadError: error => { uploadError = error; },
    hold: () => { hold = true; }, release: () => { hold = false; release(); }
  };
}
if (require.main !== module) module.exports = { setup };
else (async () => {
  const h = await setup(); const { w, $, change, input, submit } = h;
  try {
    w.Mobile.go('settings');
    check(w.document.querySelector('[data-source="familyNamesPanel"]'), 'Family names entry point');
    check(w.document.querySelector('[data-source="userFamiliesPanel"]'), 'Assignments entry point');
    $('renameFamily').value = '1'; change('renameFamily'); input('familyNameInput', 'Familia García');
    input('qrTitle', 'Mi QR nuevo');
    await w.CF.loadAll();
    check($('familyNameInput').value === 'Familia García' && $('qrTitle').value === 'Mi QR nuevo', 'Polling preserves drafts');
    h.setError({ code: 'PGRST202', message: 'Could not find the function' }); await submit('familyNameForm');
    check($('familyNameStatus').textContent.includes('Supabase'), 'Missing migration explained');
    check($('familyNameInput').value === 'Familia García' && !$('familyNameForm').querySelector('button').disabled, 'Retry preserves name');
    h.setError(null); await submit('familyNameForm');
    check(w.CF.state().D.families[0].name === 'Familia García', 'Rename persisted and refreshed');
    check($('newFamily').textContent.includes('Familia García'), 'Updated names in create-user form');
    input('familyNameInput', '<img data-casa-injected> & García'); await submit('familyNameForm');
    check(!w.document.querySelector('img[data-casa-injected]') && $('usersBody').textContent.includes('<img data-casa-injected> & García'), 'Family names render as text without injected markup');
    w.Mobile.go('users'); w.document.querySelector('[data-user="u1"]').click();
    check(!$('sourceOverlay').hidden && $('assignUser').value === 'u1', 'User row opens editable assignment');
    check($('assignSecondFamilyWrap').hidden, 'Family has one assignment');
    $('assignFamily').value = '3'; change('assignFamily'); await w.CF.loadAll();
    check($('assignFamily').value === '3', 'Polling preserves pending assignment');
    await submit('userFamiliesForm');
    check(h.tables.profiles.find(p => p.id === 'u1').family_id === 3, 'User reassigned');
    check(h.calls.at(-1).args.p_second_family_id === null, 'Family clears unused second assignment');
    w.CF.prepareUserAssignment('p1');
    check(!$('assignSecondFamilyWrap').hidden && $('assignSecondFamily').required, 'Payer has two families');
    const before = h.calls.length; $('assignSecondFamily').value = '1'; change('assignSecondFamily'); await submit('userFamiliesForm');
    check(h.calls.length === before && $('userFamiliesStatus').dataset.tone === 'error', 'Duplicate payer families blocked');
    $('assignSecondFamily').value = '3'; change('assignSecondFamily'); await submit('userFamiliesForm');
    check(h.tables.profiles.find(p => p.id === 'p1').second_family_id === 3, 'Payer reassigned');
    h.file('application/pdf'); await submit('qrSettingsForm');
    check(h.uploads.length === 0 && $('qrSaveStatus').textContent.includes('PNG'), 'Invalid QR type rejected before upload');
    h.file('image/png', 5 * 1024 * 1024 + 1); await submit('qrSettingsForm');
    check(h.uploads.length === 0 && $('qrSaveStatus').textContent.includes('5 MB'), 'Oversized QR rejected');
    h.file(); change('qrImage'); check(!$('qrPreview').hidden, 'QR preview visible');
    h.setUploadError({ message: 'Bucket not found' }); await submit('qrSettingsForm');
    check($('qrSaveStatus').textContent.includes('almacenamiento') && !$('qrSettingsForm').querySelector('button').disabled, 'Missing bucket explained with retry');
    check($('qrTitle').value === 'Mi QR nuevo' && !$('qrPreview').hidden, 'Failed upload preserves draft and preview');
    h.setUploadError(null); h.setError({ message: 'network unavailable' }); await submit('qrSettingsForm');
    check(h.removed.length === 0, 'Ambiguous network failure preserves image in case the server committed');
    h.setError({ code: '42501', message: 'permission denied' }); await submit('qrSettingsForm');
    check(h.removed.length === 1 && h.tables.payment_qr_settings.length === 0, 'Confirmed rejection removes only new orphan');
    h.setError(null); h.hold(); const save = submit('qrSettingsForm'); await tick();
    const count = h.uploads.length; await submit('qrSettingsForm');
    check(h.uploads.length === count && $('qrSettingsForm').querySelector('button').disabled, 'Duplicate QR submit prevented');
    h.release(); await save;
    check(h.tables.payment_qr_settings[0].qr_title === 'Mi QR nuevo' && $('qrSaveStatus').dataset.tone === 'success', 'QR reference saved');
    check($('qrPreview').hidden && !$('qrSettingsForm').querySelector('button').disabled, 'Successful save clears preview and restores button');
  } finally { h.dom.window.close(); }
  for (const role of ['family', 'payer', 'restaurant']) {
    const h = await setup(role);
    try {
      for (const panel of ['familyNamesPanel', 'userFamiliesPanel']) {
        h.w.Mobile.openSource(panel, panel); check(h.$('sourceOverlay').hidden, role + ' cannot open ' + panel);
      }
      const count = h.calls.length;
      await h.submit('familyNameForm'); await h.submit('userFamiliesForm'); await h.submit('qrSettingsForm');
      check(h.calls.length === count && h.uploads.length === 0, role + ' cannot submit admin forms');
      if (role === 'family') {
        h.tables.profiles[0].family_id = 2; await h.w.CF.loadAll();
        check(h.w.CF.state().me.family_id === 2, 'Open family session receives reassignment on refresh');
      }
    } finally { h.dom.window.close(); }
  }
  console.log(JSON.stringify({ adminChecks: checks, mockedBackend: true, result: 'PASS' }));
})().catch(error => { console.error(error); process.exitCode = 1; });
