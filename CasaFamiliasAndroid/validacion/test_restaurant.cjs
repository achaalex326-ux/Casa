const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const web = path.join(__dirname, '../app/src/main/assets/web');
const dom = new JSDOM(fs.readFileSync(path.join(web, 'index.html'), 'utf8'), {
  url: 'https://appassets.androidplatform.net/web/index.html', runScripts: 'outside-only', pretendToBeVisual: true
});
const w = dom.window;
w.scrollTo = () => {};
const tables = {
  profiles: [{ id: 'restaurant-test', role: 'restaurant', display_name: 'Restaurante de prueba' }],
  external_utility_debts: [
    { id: 'water', service: 'water', month_start: '2026-10-01', amount_due: 100, amount_paid: 40, due_date: '2099-10-30', status: 'pending' },
    { id: 'light', service: 'electricity', month_start: '2026-10-01', amount_due: 50, amount_paid: 50, status: 'paid' }
  ]
};
function from(table) {
  const q = {};
  for (const method of ['select', 'eq', 'order', 'limit', 'in']) q[method] = () => q;
  q.single = async () => ({ data: tables[table]?.[0] || {}, error: null });
  q.then = (resolve, reject) => Promise.resolve({ data: tables[table] || [], error: null }).then(resolve, reject);
  return q;
}
w.supabase = { createClient: () => ({ from, auth: {
  onAuthStateChange() {},
  getSession: async () => ({ data: { session: { user: { id: 'restaurant-test' } } } })
}, channel: () => ({ on() { return this; }, subscribe() { return this; } }) }) };
for (const name of ['mobile.js', 'config.js', 'app.js']) w.eval(fs.readFileSync(path.join(web, name), 'utf8'));
setTimeout(() => {
  try {
    const doc = w.document;
    assert(!doc.getElementById('mobileApp').hidden);
    assert.equal(doc.querySelector('.hero-amount strong').textContent, '60');
    assert(doc.getElementById('mobileContent').textContent.includes('Agua'));
    assert(doc.getElementById('mobileContent').textContent.includes('Luz'));
    assert(doc.querySelector('.badge.partial'));
    assert(doc.querySelector('.badge.paid'));
    for (const view of ['home', 'settings', 'more']) {
      w.Mobile.go(view);
      assert(!doc.querySelector('#mobileApp [data-go="newExpense"]'));
      assert(!doc.querySelector('#mobileApp [data-go="newPayment"]'));
      assert(!doc.querySelector('#mobileApp [data-go="users"]'));
    }
    w.Mobile.go('home');
    const previous = doc.getElementById('mobileContent').innerHTML;
    w.Mobile.go('expenses');
    assert.equal(doc.getElementById('mobileContent').innerHTML, previous);
    w.Mobile.openSource('userForm', 'Usuarios');
    assert(doc.getElementById('sourceOverlay').hidden);
    w.Mobile.openSource('view-restaurant', 'Mis servicios');
    assert(!doc.getElementById('sourceOverlay').hidden);
    assert(doc.getElementById('sourceContent').contains(doc.getElementById('restaurantDebtsBody')));
    assert(w.Mobile.back());
    assert(doc.getElementById('sourceOverlay').hidden);
    console.log(JSON.stringify({ restaurant: 'PASS', balance: 60, readOnlyNavigation: 'PASS', mockedBackend: true }));
  } catch (error) { console.error(error); process.exitCode = 1; }
  finally { dom.window.close(); }
}, 100);
