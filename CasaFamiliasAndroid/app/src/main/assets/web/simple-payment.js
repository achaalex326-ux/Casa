/* The same three-step payment flow as the native application. No local storage. */
window.SimplePayment = (() => {
  "use strict";
  const esc = (value) =>
    String(value ?? "").replace(
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
  const state = () => window.Mobile.getState();
  const uuid = () => crypto.randomUUID();
  const owner = () => {
    const s = state();
    return s?.me ? String(s.me.id || s.me.role + ":" + s.me.family_id) : "";
  };
  const today = () =>
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/La_Paz",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  let draft, overlay, previousFocus, preview;
  function reset() {
    if (preview) URL.revokeObjectURL(preview);
    preview = null;
    draft = null;
    if (overlay) {
      overlay.remove();
      overlay = null;
      document.body.classList.remove("payment-open");
    }
  }
  function close() {
    if (draft?.busy) return;
    if (overlay) {
      overlay.remove();
      overlay = null;
      document.body.classList.remove("payment-open");
    }
    previousFocus?.focus?.();
  }
  function open(options = {}) {
    const s = state();
    if (!s?.me || s.me.role === "restaurant") return;
    window.Mobile.closeSource(false);
    document.getElementById("modal")?.classList.add("hidden");
    previousFocus = document.activeElement;
    if (!draft || draft.owner !== owner()) {
      reset();
      draft = {
        owner: owner(),
        requestId: uuid(),
        step: 0,
        amount: "",
        familyId: s.me.family_id || "",
        method: "qr",
        date: today(),
        note: "",
        file: null,
        shareId: options.shareId || "",
        busy: false,
        error: "",
      };
    } else if (
      options.shareId !== undefined &&
      options.shareId !== draft.shareId
    ) {
      draft.shareId = options.shareId;
      draft.requestId = uuid();
    }
    document.body.classList.add("payment-open");
    render();
  }
  function set(key, value) {
    draft[key] = value;
    draft.error = "";
    draft.requestId = uuid();
  }
  function amount() {
    return Number(String(draft.amount).trim().replace(",", "."));
  }
  function stepError() {
    if (draft.step === 0) {
      if (!draft.familyId) return "Elige la familia del pago.";
      if (
        !Number.isFinite(amount()) ||
        amount() <= 0 ||
        !Number.isSafeInteger(Math.round(amount() * 100))
      )
        return "Escribe un importe mayor a cero.";
    }
    if (draft.step === 1 && draft.method === "qr" && !draft.file)
      return "Adjunta la foto del comprobante para continuar.";
    return "";
  }
  async function prepare(file) {
    if (!file) return;
    if (
      !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
      file.size > 20 * 1024 * 1024
    )
      throw new Error("Elige una foto JPG, PNG o WebP de hasta 20 MB.");
    const bitmap = await createImageBitmap(file, {
      imageOrientation: "from-image",
    });
    try {
      if (bitmap.width * bitmap.height > 40000000)
        throw new Error("La foto es demasiado grande. Prueba con otra imagen.");
      const scale = Math.min(1, 2560 / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      canvas
        .getContext("2d")
        .drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      let blob;
      for (const quality of [0.88, 0.72, 0.56]) {
        blob = await new Promise((resolve) =>
          canvas.toBlob(resolve, "image/jpeg", quality),
        );
        if (blob && blob.size <= 5 * 1024 * 1024) break;
      }
      if (!blob || blob.size > 5 * 1024 * 1024)
        throw new Error("No se pudo preparar la foto. Elige otra imagen.");
      return new File([blob], "comprobante.jpg", { type: "image/jpeg" });
    } finally {
      bitmap.close();
    }
  }
  async function choose(file) {
    if (!file) return; // Cancelling a retake keeps the previously selected image.
    const current = draft;
    current.busy = true;
    render();
    try {
      const prepared = await prepare(file);
      if (draft !== current || current.owner !== owner()) return;
      set("file", prepared);
      if (preview) URL.revokeObjectURL(preview);
      preview = URL.createObjectURL(prepared);
    } catch (error) {
      if (draft === current) current.error = error.message;
    } finally {
      if (draft === current) {
        current.busy = false;
        render();
      }
    }
  }
  function fields() {
    const s = state(),
      a = CasaAccounts;
    if (draft.step === 0) {
      const families = s.D.families || [];
      return (
        (s.me.role === "family"
          ? ""
          : `<label>Familia del pago<select id="simpleFamily"><option value="">Elige una familia</option>${families.map((f) => `<option value="${esc(f.id)}" ${String(f.id) === String(draft.familyId) ? "selected" : ""}>${esc(f.name)}</option>`).join("")}</select></label>`) +
        `<label>Total depositado (Bs)<input id="simpleAmount" inputmode="decimal" autocomplete="off" placeholder="Ej.: 200" value="${esc(draft.amount)}"></label>` +
        (draft.familyId
          ? `<p class="form-note">Deuda pendiente: <strong>${a.money(a.number(a.account(s, Number(draft.familyId)).balance))}</strong></p>`
          : "") +
        `<details><summary>Elegir un mes específico (opcional)</summary><label>Aplicar el pago a<select id="simpleShare"><option value="">Mis deudas más antiguas</option>${a
          .debts(s, Number(draft.familyId))
          .filter((d) => d.balance > 0)
          .map(
            (d) =>
              `<option value="${esc(d.id)}" ${d.id === draft.shareId ? "selected" : ""}>${esc(a.monthName(d.month))} · ${a.money(a.number(d.balance))}</option>`,
          )
          .join("")}</select></label></details>`
      );
    }
    if (draft.step === 1)
      return (
        `<label>Forma de pago<select id="simpleMethod"><option value="qr" ${draft.method === "qr" ? "selected" : ""}>QR o transferencia</option><option value="cash" ${draft.method === "cash" ? "selected" : ""}>Efectivo</option></select></label>` +
        (preview
          ? `<img class="receiptPreview" src="${preview}" alt="Comprobante seleccionado"><button type="button" class="text-action" id="simpleRemove">Quitar imagen</button>`
          : "") +
        `<button type="button" class="ghost" id="simpleCamera">Tomar foto</button><button type="button" class="ghost" id="simpleGallery">Elegir de galería</button><input id="simpleCameraInput" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden><input id="simpleGalleryInput" type="file" accept="image/jpeg,image/png,image/webp" hidden><p class="form-note">La foto se envía cuando confirmas el pago.</p>`
      );
    const plan = a.plan(s, Number(draft.familyId), amount(), draft.shareId);
    return `<section class="card"><h3>Esto se pagará</h3>${plan.allocations.map((row) => `<div class="amount-line"><span>${esc(a.monthName(row.month))}</span><strong>${a.money(a.number(row.applied))}</strong></div>`).join("") || "<p>No hay deudas pendientes para esta selección.</p>"}<hr><div class="amount-line"><span>Total depositado</span><strong>${a.money(amount())}</strong></div><div class="amount-line"><span>Deuda que quedaría</span><strong>${a.money(a.number(plan.remainingDebt))}</strong></div>${plan.credit > 0 ? `<div class="amount-line"><span>Quedaría a favor</span><strong>${a.money(a.number(plan.credit))}</strong></div>` : ""}</section><p class="form-note">${s.me.role === "family" ? "El saldo baja cuando el encargado aprueba el comprobante." : "Al guardar, el pago se aprueba y se aplica a la deuda."} El reparto se vuelve a comprobar al aprobar.</p><details><summary>Cambiar fecha o añadir un mensaje</summary><label>Fecha del pago<input type="date" id="simpleDate" value="${esc(draft.date)}"></label><label>Mensaje (opcional)<textarea id="simpleNote" maxlength="1000">${esc(draft.note)}</textarea></label></details>`;
  }
  function render() {
    if (!draft) return;
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "simplePaymentOverlay";
      overlay.setAttribute("role", "dialog");
      overlay.setAttribute("aria-modal", "true");
      overlay.setAttribute("aria-labelledby", "simplePaymentTitle");
      document.body.append(overlay);
    }
    const titles = [
      "¿Cuánto pagaste?",
      "Adjunta el comprobante",
      "Revisa y envía",
    ];
    overlay.innerHTML = `<div class="guided-shell"><header><strong>Pagar</strong><button type="button" id="simpleClose" ${draft.busy ? "disabled" : ""}>Cerrar</button></header><main><p class="form-note">Paso ${draft.step + 1} de 3</p><ol class="guided-steps">${["Importe", "Foto", "Revisar"].map((label, i) => `<li ${i === draft.step ? 'aria-current="step"' : ""}><span>${i + 1}</span>${label}</li>`).join("")}</ol><h1 id="simplePaymentTitle" tabindex="-1">${titles[draft.step]}</h1><div class="guided-fields">${fields()}</div><p role="alert" class="form-error">${esc(draft.error)}</p><footer>${draft.step ? '<button type="button" class="ghost" id="simplePrevious">Paso anterior</button>' : ""}<button type="button" class="primary" id="simpleNext">${draft.busy ? "Preparando…" : draft.step < 2 ? "Continuar" : state().me.role === "family" ? "Enviar comprobante" : "Guardar pago"}</button></footer></main></div>`;
    overlay
      .querySelectorAll("button,input,select,textarea")
      .forEach((el) => (el.disabled = draft.busy));
    const bind = (id, event, fn) => {
      const node = overlay.querySelector("#" + id);
      if (node) node.addEventListener(event, fn);
    };
    bind("simpleClose", "click", close);
    bind("simpleFamily", "change", (e) => {
      set("familyId", e.target.value);
      set("shareId", "");
      render();
    });
    bind("simpleAmount", "input", (e) => set("amount", e.target.value));
    bind("simpleShare", "change", (e) => set("shareId", e.target.value));
    bind("simpleMethod", "change", (e) => {
      set("method", e.target.value);
      render();
    });
    bind("simpleDate", "change", (e) => set("date", e.target.value));
    bind("simpleNote", "input", (e) => set("note", e.target.value));
    bind("simpleCamera", "click", () =>
      overlay.querySelector("#simpleCameraInput").click(),
    );
    bind("simpleGallery", "click", () =>
      overlay.querySelector("#simpleGalleryInput").click(),
    );
    bind("simpleCameraInput", "change", (e) => choose(e.target.files[0]));
    bind("simpleGalleryInput", "change", (e) => choose(e.target.files[0]));
    bind("simpleRemove", "click", () => {
      set("file", null);
      if (preview) URL.revokeObjectURL(preview);
      preview = null;
      render();
    });
    bind("simplePrevious", "click", () => {
      draft.step--;
      draft.error = "";
      render();
    });
    bind("simpleNext", "click", async () => {
      if (draft.busy) return;
      draft.error = stepError();
      if (draft.error) {
        render();
        return;
      }
      if (draft.step < 2) {
        draft.step++;
        render();
        return;
      }
      if (window.Mobile.isDemo()) {
        draft.error = "Demostración: no se envían pagos reales.";
        render();
        return;
      }
      const current = draft;
      current.busy = true;
      render();
      try {
        await CF.recordPaymentBatch({
          requestId: current.requestId,
          familyId: current.familyId,
          amount: current.amount,
          method: current.method,
          file: current.file,
          date: current.date,
          note: current.note,
          shareId: current.shareId,
        });
        reset();
        window.Mobile.go("payments");
        window.Mobile.notify(
          "Comprobante enviado. Puedes ver su estado en Mis pagos.",
        );
      } catch (error) {
        if (draft === current) {
          current.error =
            error.message ||
            "No se pudo enviar. Revisa la conexión y vuelve a intentar.";
          current.busy = false;
          render();
        }
      }
    });
    overlay.scrollTop = 0;
    overlay.querySelector("#simplePaymentTitle").focus();
  }
  document.addEventListener(
    "keydown",
    (event) => {
      if (!overlay) return;
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation();
        close();
        return;
      }
      if (event.key === "Tab") {
        const nodes = [
          ...overlay.querySelectorAll(
            "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),summary",
          ),
        ].filter((el) => el.getClientRects().length);
        const first = nodes[0],
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
    prepareImage: prepare,
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
