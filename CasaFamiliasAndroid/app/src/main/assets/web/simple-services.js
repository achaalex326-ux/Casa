/* Staff invoice entry mirrors the native month / invoices / review flow. */
window.SimpleServices = (() => {
  "use strict";
  const esc = (v) =>
    String(v ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  const keys = [
    ["water", "Agua"],
    ["electricity", "Luz"],
    ["gas", "Gas"],
    ["wifi", "Internet"],
  ];
  const state = () => Mobile.getState();
  let draft, overlay, previousFocus;
  const previews = {};
  function endOfMonth(month) {
    const [y, m] = month.split("-").map(Number);
    return month + "-" + new Date(Date.UTC(y, m, 0)).getUTCDate();
  }
  function reset() {
    for (const url of Object.values(previews)) URL.revokeObjectURL(url);
    Object.keys(previews).forEach((k) => delete previews[k]);
    draft = null;
    overlay?.remove();
    overlay = null;
    document.body.classList.remove("payment-open");
  }
  function close() {
    if (draft?.busy) return;
    overlay?.remove();
    overlay = null;
    document.body.classList.remove("payment-open");
    previousFocus?.focus?.();
  }
  function open() {
    const s = state();
    if (!s?.me || !["payer", "sysadmin"].includes(s.me.role)) return;
    SimplePayment.close();
    Mobile.closeSource(false);
    previousFocus = document.activeElement;
    if (!draft) {
      const iso = CasaAccounts.today().slice(0, 7);
      draft = {
        step: 0,
        month: iso,
        due: endOfMonth(iso),
        water: 0,
        electricity: 0,
        gas: 0,
        wifi: 0,
        other_water: 0,
        other_electricity: 0,
        reference: false,
        files: {},
        busy: false,
        error: "",
      };
    }
    document.body.classList.add("payment-open");
    render();
  }
  function summary() {
    const plan = CasaAccounts.servicePlan(state(), draft);
    return `<section class="card"><h3>Total de las facturas</h3><strong class="card-amount">${CasaAccounts.money(CasaAccounts.number(plan.invoices))}</strong>${
      draft.reference
        ? "<p>Solo referencia: no se crea deuda.</p>"
        : `${plan.discounts ? `<div class="amount-line"><span>Descuentos</span><strong>${CasaAccounts.money(CasaAccounts.number(plan.discounts))}</strong></div><div class="amount-line"><span>Total a repartir</span><strong>${CasaAccounts.money(CasaAccounts.number(plan.total))}</strong></div>` : ""}<p>${plan.shares.length} cuentas · ${CasaAccounts.money(CasaAccounts.number(plan.shares[0] || 0))} por cuenta, aproximadamente.</p><p class="form-note">Las cuentas del encargado quedan cubiertas.</p><details><summary>Ver importe por familia</summary>${state()
            .D.families.slice()
            .sort((a, b) => a.id - b.id)
            .map(
              (f, i) =>
                `<div class="amount-line"><span>${esc(f.name)}</span><strong>${CasaAccounts.money(CasaAccounts.number(plan.shares[i]))}</strong></div>`,
            )
            .join("")}</details>`
    }${plan.invalid ? '<p class="form-error">El descuento es mayor que su factura.</p>' : ""}</section>`;
  }
  function fields() {
    if (draft.step === 0)
      return `<label>Mes de las facturas<input id="servicesMonth" type="month" value="${draft.month}"></label><label>Pagar hasta<input id="servicesDue" type="date" value="${draft.due}"></label><details><summary>Opciones de referencia</summary><label><input id="servicesReference" type="checkbox" ${draft.reference ? "checked" : ""}>Guardar solo como referencia, sin crear deudas</label></details>`;
    if (draft.step === 1)
      return keys
        .map(
          ([key, label]) =>
            `<label>${label} (Bs)<input data-service-amount="${key}" inputmode="decimal" value="${esc(draft[key])}"></label><details><summary>Adjuntar factura de ${label.toLowerCase()}</summary>${previews[key] ? `<img class="receiptPreview" src="${previews[key]}" alt="Factura de ${label}"><button class="text-action" data-service-remove="${key}">Quitar imagen</button>` : ""}<button class="ghost" data-service-camera="${key}">Tomar foto</button><button class="ghost" data-service-gallery="${key}">Elegir de galería</button><input type="file" hidden id="serviceCamera-${key}" accept="image/jpeg,image/png,image/webp" capture="environment"><input type="file" hidden id="serviceGallery-${key}" accept="image/jpeg,image/png,image/webp"></details>`,
        )
        .join("");
    return `<details><summary>Aplicar un descuento (opcional)</summary><label>Descuento adicional de agua (Bs)<input data-service-amount="other_water" inputmode="decimal" value="${esc(draft.other_water)}"></label><label>Descuento adicional de luz (Bs)<input data-service-amount="other_electricity" inputmode="decimal" value="${esc(draft.other_electricity)}"></label></details><div id="serviceSummary">${summary()}</div>`;
  }
  async function choose(key, file) {
    if (!file) return;
    const current = draft;
    current.busy = true;
    render();
    try {
      const image = await SimplePayment.prepareImage(file);
      if (draft !== current) return;
      draft.files[key] = image;
      if (previews[key]) URL.revokeObjectURL(previews[key]);
      previews[key] = URL.createObjectURL(image);
    } catch (error) {
      if (draft === current) draft.error = error.message;
    } finally {
      if (draft === current) {
        draft.busy = false;
        render();
      }
    }
  }
  function render() {
    if (!draft) return;
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "simpleServicesOverlay";
      overlay.setAttribute("role", "dialog");
      overlay.setAttribute("aria-modal", "true");
      overlay.setAttribute("aria-labelledby", "servicesTitle");
      document.body.append(overlay);
    }
    overlay.innerHTML = `<div class="guided-shell"><header><strong>Facturas</strong><button id="servicesClose">Cerrar</button></header><main><p class="form-note">Paso ${draft.step + 1} de 3</p><ol class="guided-steps">${["Mes", "Facturas", "Revisar"].map((name, i) => `<li ${i === draft.step ? 'aria-current="step"' : ""}><span>${i + 1}</span>${name}</li>`).join("")}</ol><h1 id="servicesTitle" tabindex="-1">${["¿De qué mes son las facturas?", "Escribe los importes", "Revisa la suma"][draft.step]}</h1><div class="guided-fields">${fields()}</div><p role="alert" class="form-error">${esc(draft.error)}</p><footer>${draft.step ? '<button class="ghost" id="servicesPrevious">Paso anterior</button>' : ""}<button class="primary" id="servicesNext">${draft.busy ? "Preparando…" : draft.step < 2 ? "Continuar" : "Guardar facturas"}</button></footer></main></div>`;
    overlay
      .querySelectorAll("input,button")
      .forEach((el) => (el.disabled = draft.busy));
    overlay.querySelector("#servicesClose").onclick = close;
    overlay.querySelector("#servicesMonth")?.addEventListener("change", (e) => {
      draft.month = e.target.value;
      if (/^\d{4}-\d{2}$/.test(draft.month)) {
        draft.due = endOfMonth(draft.month);
        overlay.querySelector("#servicesDue").value = draft.due;
      }
    });
    overlay
      .querySelector("#servicesDue")
      ?.addEventListener("change", (e) => (draft.due = e.target.value));
    overlay
      .querySelector("#servicesReference")
      ?.addEventListener("change", (e) => (draft.reference = e.target.checked));
    overlay.querySelectorAll("[data-service-amount]").forEach(
      (input) =>
        (input.oninput = () => {
          draft[input.dataset.serviceAmount] = Number(
            input.value.replace(",", "."),
          );
          if (draft.step === 2)
            overlay.querySelector("#serviceSummary").innerHTML = summary();
        }),
    );
    for (const [key] of keys) {
      const camera = overlay.querySelector(
          '[data-service-camera="' + key + '"]',
        ),
        gallery = overlay.querySelector('[data-service-gallery="' + key + '"]');
      if (camera)
        camera.onclick = () =>
          overlay.querySelector("#serviceCamera-" + key).click();
      if (gallery)
        gallery.onclick = () =>
          overlay.querySelector("#serviceGallery-" + key).click();
      overlay
        .querySelector("#serviceCamera-" + key)
        ?.addEventListener("change", (e) => choose(key, e.target.files[0]));
      overlay
        .querySelector("#serviceGallery-" + key)
        ?.addEventListener("change", (e) => choose(key, e.target.files[0]));
      const remove = overlay.querySelector(
        '[data-service-remove="' + key + '"]',
      );
      if (remove)
        remove.onclick = () => {
          delete draft.files[key];
          URL.revokeObjectURL(previews[key]);
          delete previews[key];
          render();
        };
    }
    const previous = overlay.querySelector("#servicesPrevious");
    if (previous)
      previous.onclick = () => {
        draft.step--;
        draft.error = "";
        render();
      };
    overlay.querySelector("#servicesNext").onclick = async () => {
      if (draft.busy) return;
      draft.error = "";
      if (
        draft.step === 0 &&
        (!/^\d{4}-\d{2}$/.test(draft.month) || !draft.due)
      )
        draft.error = "Elige el mes y la fecha de pago.";
      if (
        [...keys.map((k) => k[0]), "other_water", "other_electricity"].some(
          (key) => !Number.isFinite(draft[key]) || draft[key] < 0,
        )
      )
        draft.error =
          "Revisa los importes: deben ser iguales o mayores a cero.";
      if (draft.step === 2 && CasaAccounts.servicePlan(state(), draft).invalid)
        draft.error = "El descuento es mayor que su factura.";
      if (draft.error) {
        render();
        return;
      }
      if (draft.step < 2) {
        draft.step++;
        render();
        return;
      }
      if (Mobile.isDemo()) {
        draft.error = "Demostración: no se guardan facturas reales.";
        render();
        return;
      }
      if (
        !window.confirm(
          "¿Guardar las facturas de " +
            CasaAccounts.monthName(draft.month) +
            "?",
        )
      )
        return;
      const current = draft;
      current.busy = true;
      render();
      try {
        await CF.recordServices(current);
        reset();
        Mobile.go("expenses");
        Mobile.notify("Facturas guardadas. Las deudas ya están actualizadas.");
      } catch (error) {
        if (draft === current) {
          draft.error =
            error.message || "No se pudo guardar. Revisa la conexión.";
          draft.busy = false;
          render();
        }
      }
    };
    overlay.scrollTop = 0;
    overlay.querySelector("#servicesTitle").focus();
  }
  document.addEventListener(
    "keydown",
    (event) => {
      if (!overlay) return;
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation();
        close();
      }
      if (event.key === "Tab") {
        const nodes = [
            ...overlay.querySelectorAll(
              "button:not(:disabled),input:not(:disabled),summary",
            ),
          ].filter((n) => n.getClientRects().length),
          first = nodes[0],
          last = nodes.at(-1);
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    },
    true,
  );
  return {
    open,
    close,
    reset,
    isOpen: () => !!overlay,
    back: () => {
      if (draft?.busy) return true;
      if (draft?.step) {
        draft.step--;
        render();
      } else close();
      return true;
    },
  };
})();
