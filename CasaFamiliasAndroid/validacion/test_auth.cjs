const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const web = path.join(__dirname, '../app/src/main/assets/web');
const tick = () => new Promise(resolve => setTimeout(resolve, 25));

async function fixture(options = {}) {
  const dom = new JSDOM(fs.readFileSync(path.join(web, 'index.html'), 'utf8'), {
    url: 'https://appassets.androidplatform.net/web/index.html' + (options.recovery ? '#type=recovery' : ''),
    runScripts: 'outside-only', pretendToBeVisual: true
  });
  const w = dom.window;
  const session = { user: { id: 'test-user', email: 'test@example.com' } };
  const calls = { updates: 0, resets: 0, profiles: 0 };
  let subscriber, recoveryEmitted = false;
  const tables = {
    families: [{ id: 1, name: 'Familia de prueba' }],
    profiles: [{ id: 'test-user', role: 'family', family_id: 1, display_name: 'Cuenta de prueba' }]
  };
  function from(table) {
    const q = {};
    for (const method of ['select', 'eq', 'order', 'limit', 'in', 'insert', 'update', 'delete']) q[method] = () => q;
    q.single = async () => {
      calls.profiles++;
      return options.missingProfile ? { data: null, error: { message: 'Missing profile' } } : { data: tables[table]?.[0] || {}, error: null };
    };
    q.maybeSingle = async () => ({ data: null, error: null });
    q.then = (resolve, reject) => Promise.resolve({ data: tables[table] || [], error: null }).then(resolve, reject);
    return q;
  }
  w.scrollTo = () => {};
  w.supabase = { createClient: () => ({
    from, rpc: async () => ({ data: [], error: null }),
    auth: {
      onAuthStateChange(callback) { subscriber = callback; },
      async getSession() {
        if (options.recovery && !options.expired && !recoveryEmitted) {
          recoveryEmitted = true;
          subscriber('PASSWORD_RECOVERY', session);
        }
        return { data: { session: options.expired || !options.recovery && !options.loggedIn ? null : session }, error: null };
      },
      async updateUser(data) { calls.updates++; return options.update ? options.update(data) : { error: null }; },
      async resetPasswordForEmail() { calls.resets++; if (options.offline) throw new Error('Offline'); return { error: null }; },
      async signOut() { if (options.offline) throw new Error('Offline'); return { error: null }; }
    },
    channel: () => ({ on() { return this; }, subscribe() { return this; } }),
    storage: { from: () => ({ createSignedUrl: async () => ({ data: { signedUrl: 'https://example.com/receipt' }, error: null }) }) }
  }) };
  w.eval(fs.readFileSync(path.join(web, 'startup.js'), 'utf8'));
  for(const name of ['simple-account.js','mobile.js','simple-payment.js','simple-services.js'])w.eval(fs.readFileSync(path.join(web,name),'utf8'));
  w.eval(fs.readFileSync(path.join(web, 'config.js'), 'utf8'));
  w.eval(fs.readFileSync(process.env.CASA_TEST_APP_SOURCE || path.join(web, 'app.js'), 'utf8'));
  await tick();
  return { dom, w, calls, doc: w.document, recover: () => subscriber('PASSWORD_RECOVERY', session) };
}

async function run() {
  let passed = 0;
  async function check(name, options, test) {
    const f = await fixture(options);
    try { await test(f); passed++; console.log(name + ': PASS'); }
    finally { f.dom.window.close(); }
  }
  const shown = (doc, id) => !doc.getElementById(id).classList.contains('hidden');
  const submit = f => f.doc.getElementById('resetForm').onsubmit({ preventDefault() {} });
  const passwords = (f, confirmation = 'NewExample123') => {
    f.doc.getElementById('resetPassword').value = 'NewExample123';
    f.doc.getElementById('resetPasswordConfirm').value = confirmation;
  };
  await check('Recovery retains password screen before account entry', { recovery: true }, async f => {
    assert(shown(f.doc, 'resetScreen'));
    assert(!shown(f.doc, 'loginScreen'));
    assert(f.doc.getElementById('mobileApp').hidden);
    assert.equal(f.calls.profiles, 0);
  });
  await check('Recovery closes the account view and clears the prior display data', { loggedIn: true }, async f => {
    assert(f.w.CF.state().me);
    assert(f.w.CF.state().D.families.length);
    f.recover();
    assert(shown(f.doc, 'resetScreen'));
    assert(f.doc.getElementById('mobileApp').hidden);
    assert.equal(f.w.CF.state().me, null);
    assert.equal(f.w.CF.state().D.families.length, 0);
  });
  await check('Expired link returns to login with an explanation', { recovery: true, expired: true }, async f => {
    assert(shown(f.doc, 'loginScreen'));
    assert(!shown(f.doc, 'resetScreen'));
    assert(f.doc.getElementById('loginError').textContent.trim());
  });
  await check('Password confirmation prevents an accidental change', { recovery: true }, async f => {
    passwords(f, 'DifferentExample123'); await submit(f);
    assert.equal(f.calls.updates, 0);
    assert(f.doc.getElementById('resetError').textContent.trim());
    assert(shown(f.doc, 'resetScreen'));
  });
  let release;
  await check('Repeated submit sends one update and returns to the account', {
    recovery: true, update: () => new Promise(resolve => { release = resolve; })
  }, async f => {
    passwords(f);
    const pending = submit(f);
    await submit(f);
    assert.equal(f.calls.updates, 1);
    assert(f.doc.querySelector('#resetForm button').disabled);
    release({ error: null }); await pending;
    assert(!shown(f.doc, 'resetScreen'));
    assert(!f.doc.getElementById('mobileApp').hidden);
    assert.equal(f.doc.getElementById('resetPassword').value, '');
  });
  await check('Offline recovery preserves inputs and enables retry', {
    recovery: true, update: async () => { throw new Error('Offline'); }
  }, async f => {
    passwords(f); await submit(f);
    assert(shown(f.doc, 'resetScreen'));
    assert.equal(f.doc.getElementById('resetPassword').value, 'NewExample123');
    assert(!f.doc.querySelector('#resetForm button').disabled);
    assert(f.doc.getElementById('resetError').textContent.trim());
  });
  await check('Offline email request does not leave the control disabled', { offline: true }, async f => {
    f.doc.getElementById('loginEmail').value = 'test@example.com';
    await f.doc.getElementById('forgotBtn').onclick();
    assert.equal(f.calls.resets, 1);
    assert(!f.doc.getElementById('forgotBtn').disabled);
    assert(f.doc.getElementById('toast').textContent.includes('conectar'));
  });
  await check('Missing profile leaves a usable login screen', { loggedIn: true, missingProfile: true }, async f => {
    assert(shown(f.doc, 'loginScreen'));
    assert(f.doc.getElementById('mobileApp').hidden);
    assert(f.doc.getElementById('loginError').textContent.includes('administrador'));
  });
  await check('Account password confirmation rejects mismatched values', { loggedIn: true }, async f => {
    f.doc.getElementById('settingsPassword').value = 'NewExample123';
    f.doc.getElementById('settingsPasswordConfirm').value = 'DifferentExample123';
    f.doc.getElementById('passwordForm').dispatchEvent(new f.w.Event('submit', { cancelable: true }));
    await tick();
    assert.equal(f.calls.updates, 0);
    assert(f.doc.getElementById('passwordError').textContent.includes('coinciden'));
  });
  await check('Offline account password change preserves inputs and enables retry', {
    loggedIn: true, update: async () => { throw new Error('Offline'); }
  }, async f => {
    f.doc.getElementById('settingsPassword').value = 'NewExample123';
    f.doc.getElementById('settingsPasswordConfirm').value = 'NewExample123';
    f.doc.getElementById('passwordForm').dispatchEvent(new f.w.Event('submit', { cancelable: true }));
    await tick();
    assert.equal(f.calls.updates, 1);
    assert(!f.doc.querySelector('#passwordForm button').disabled);
    assert.equal(f.doc.getElementById('settingsPassword').value, 'NewExample123');
    assert(f.doc.getElementById('passwordError').textContent.includes('conectar'));
  });
  await check('Offline sign-out keeps the account open with a retry message', { loggedIn: true, offline: true }, async f => {
    await f.w.CF.logout();
    assert(!f.doc.getElementById('mobileApp').hidden);
    assert(f.doc.getElementById('toast').textContent.includes('conectar'));
  });
  console.log(JSON.stringify({ authChecks: passed, mockedBackend: true, result: 'PASS' }));
}
run().catch(error => { console.error(error); process.exitCode = 1; });
