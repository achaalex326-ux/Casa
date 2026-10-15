/* Keep this file compatible with older WebViews: it must also explain syntax failures. */
(function () {
  var timer, completed = false, failed = false;
  function screen() { return document.getElementById('startupScreen'); }
  function fail(message) {
    failed = true;
    clearTimeout(timer);
    var node = screen();
    if (!node) return;
    node.hidden = false;
    document.getElementById('startupTitle').textContent = 'Vamos a volver a intentarlo';
    document.getElementById('startupMessage').textContent = message || 'No se pudo cargar la aplicación. Pulsa Reintentar para abrirla de nuevo.';
    document.getElementById('startupRetry').hidden = false;
  }
  window.CasaStartup = {
    ready: function () {
      completed = true;
      failed = false;
      clearTimeout(timer);
      if (screen()) screen().hidden = true;
    },
    fail: fail
  };
  window.addEventListener('error', function (event) {
    // Optional images and export libraries must not interrupt an open account.
    var source = event.target && event.target.src;
    if (!completed && (event.error || /\/(mobile|app|config)\.js(?:\?|$)/.test(source || ''))) fail();
  }, true);
  window.addEventListener('unhandledrejection', function () { if (!completed) fail(); });
  document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('startupRetry').onclick = function () { window.location.reload(); };
    if (failed) fail();
    if (!completed) {
      try {
        new Function('var value = {}; return value?.name ?? "";');
        if (!Object.fromEntries || !window.fetch || !window.Promise) throw new Error('WebView');
      } catch (error) {
        fail('Actualiza Android System WebView y Google Chrome desde Play Store y vuelve a abrir Casa Familias.');
        return;
      }
      if (!window.Mobile || !window.CF) { fail(); return; }
      timer = setTimeout(function () {
        fail(navigator.onLine === false ? 'No tienes conexión. Conéctate a internet y pulsa Reintentar.' : 'La carga está tardando demasiado. Revisa tu conexión y pulsa Reintentar.');
      }, 15000);
    }
  });
}());
