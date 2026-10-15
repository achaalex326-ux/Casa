const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

const web = path.join(__dirname, '../app/src/main/assets/web');

function startupHTML() {
  const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
  return html.replace(/<script src="([^"]+)"><\/script>/g, (_, name) => {
    let script;
    if (name === 'vendor/supabase.js') {
      // A fresh installation has no session and must reach the login form.
      script = `window.supabase = {
        createClient: () => ({ auth: {
          onAuthStateChange() {},
          getSession: async () => ({ data: { session: null } })
        } })
      };`;
    } else if (name.startsWith('vendor/')) {
      script = '';
    } else {
      script = fs.readFileSync(path.join(web, name), 'utf8');
    }
    // Inline local scripts in-place so they execute while parsing the HTML,
    // like the blocking script tags used by the Android WebView.
    return '<script>' + script + '</script>';
  });
}

async function test() {
  // Let the HTML parser load scripts in their actual position in index.html.
  // Evaluating scripts after parsing the whole document hides startup failures.
  const dom = new JSDOM(startupHTML(), {
    url: 'https://appassets.androidplatform.net/web/index.html',
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    beforeParse(window) { window.scrollTo = () => {}; }
  });
  try {
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Startup did not complete')), 5000);
      dom.window.addEventListener('load', () => {
        clearTimeout(timeout);
        resolve();
      }, { once: true });
    });
    const { document } = dom.window;
    assert(document.getElementById('setupScreen').classList.contains('hidden'),
      'First launch must not show the generic startup error');
    assert(!document.getElementById('loginScreen').classList.contains('hidden'),
      'First launch must show the login form');
    assert.equal(typeof document.getElementById('loginForm').onsubmit, 'function');
    assert.equal(typeof document.getElementById('modalClose').onclick, 'function');
    assert(dom.window.CF, 'Application initialization must complete');
    console.log(JSON.stringify({ firstLaunch: 'login', result: 'PASS' }));
  } finally {
    dom.window.close();
  }
}

test().catch(error => { console.error(error); process.exitCode = 1; });
