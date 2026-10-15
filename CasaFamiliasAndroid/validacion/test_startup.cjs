const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM, VirtualConsole } = require('jsdom');
const web = path.join(__dirname, '../app/src/main/assets/web');
let checks = 0;
function check(condition, label) { assert(condition, label); checks++; }
function startupHTML(mode) {
  const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
  return html.replace(/<script src="([^"]+)"><\/script>/g, (_, url) => {
    const name = url.split('?')[0];
    let script;
    if (name === 'vendor/supabase.js') {
      script = `window.supabase = { createClient: function () {
        function from(table) {
          const q = {};
          for (const method of ['select','eq','order','limit','in']) q[method] = () => q;
          q.single = q.maybeSingle = async () => ({data: table === 'profiles' ? {id:'me',role:'family',family_id:1,display_name:'Mi familia'} : null});
          q.then = (r,j) => Promise.resolve({data:[]}).then(r,j);
          return q;
        }
        return { from, rpc: async () => ({data:[]}),
          channel: () => ({on(){return this},subscribe(){return this}}),
          storage: {from:()=>({createSignedUrl:async()=>({data:{signedUrl:'https://example.test/qr'}})})},
          auth: { onAuthStateChange() {}, getSession: () => ${mode === 'slow' ? 'new Promise(()=>{})' : `Promise.resolve({data:{session:${mode === 'login' ? 'null' : "{user:{id:'me'}}"}}})`} }
        };
      }};`;
    } else if (name.startsWith('vendor/') || (mode === 'missing-mobile' && name === 'mobile.js') || (mode === 'missing-app' && name === 'app.js')) script = '';
    else if (mode === 'syntax-error' && name === 'app.js') script = 'function { broken';
    else script = fs.readFileSync(path.join(web, name), 'utf8');
    // Execute while parsing, in the same position and order as Android.
    return '<script>' + script + '</script>';
  });
}
async function launch(mode) {
  const dom = new JSDOM(startupHTML(mode), {
    url: 'https://appassets.androidplatform.net/web/index.html', runScripts: 'dangerously',
    pretendToBeVisual: true, virtualConsole: new VirtualConsole(),
    beforeParse(w) {
      w.scrollTo = () => {}; w.fetch = () => Promise.reject(new Error('No network in test'));
      if (mode === 'old-webview') w.Object.fromEntries = undefined;
      if (mode === 'slow') {
        const set = w.setTimeout.bind(w);
        w.setTimeout = (fn, delay, ...args) => set(fn, delay === 15000 ? 30 : delay, ...args);
        Object.defineProperty(w.navigator, 'onLine', {value:false});
      }
      w.localStorage.setItem('saved-session-marker', 'preserve');
    }
  });
  await new Promise(resolve => dom.window.addEventListener('load', resolve, {once:true}));
  await new Promise(resolve => setTimeout(resolve, mode === 'slow' ? 80 : 20));
  return dom;
}
(async () => {
  for (const mode of ['login','saved-session','missing-mobile','missing-app','syntax-error','old-webview','slow']) {
    const dom = await launch(mode), w = dom.window, $ = id => w.document.getElementById(id);
    try {
      if (mode === 'login') {
        check($('startupScreen').hidden && !$('loginScreen').classList.contains('hidden'), 'Fresh install reaches login');
        check(typeof $('loginForm').onsubmit === 'function', 'Login is bound');
      } else if (mode === 'saved-session') {
        check($('startupScreen').hidden && !$('mobileApp').hidden && $('mobileNav').children.length === 4, 'Saved account reaches complete home');
        const clean = w.CF.state().D.cleaning;
        clean.push({id:'turn',family_id:1,status:'pending',turn_date:'2026-10-19'});
        const can = w.CF.canManageCleaning;
        w.CF.canManageCleaning = undefined;
        w.Mobile.render();
        check(!$('startupScreen').hidden && $('startupRetry').textContent === 'Reintentar', 'Render failure has visible retry');
        w.CF.canManageCleaning = can; w.Mobile.render();
        check($('startupScreen').hidden && !$('mobileApp').hidden, 'Successful rendering recovers');
      } else {
        check(!$('startupScreen').hidden && !$('startupRetry').hidden, mode + ' has a visible retry instead of white screen');
        if (mode === 'old-webview') check($('startupMessage').textContent.includes('WebView'), 'Outdated engine explains how to update');
        if (mode === 'slow') check($('startupMessage').textContent.includes('conexión'), 'Offline refresh explains connection failure');
      }
      check(w.localStorage.getItem('saved-session-marker') === 'preserve', mode + ' preserves local storage');
    } finally { w.close(); }
  }
  const staticDOM = new JSDOM(fs.readFileSync(path.join(web,'index.html'),'utf8'));
  check(!staticDOM.window.document.getElementById('startupScreen').hidden && !!staticDOM.window.document.getElementById('startupRetry').getAttribute('onclick'), 'HTML has a retry without JavaScript initialization');
  staticDOM.window.close();
  console.log(JSON.stringify({startupChecks:checks,result:'PASS'}));
})().catch(error => { console.error(error); process.exitCode = 1; });
