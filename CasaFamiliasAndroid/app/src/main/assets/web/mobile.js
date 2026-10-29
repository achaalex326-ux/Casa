/* Mobile navigation and presentation. Business operations remain in app.js. */
window.Mobile = (() => {
  const $ = (id) => document.getElementById(id),
    esc = (v) =>
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
  const money = (n) => window.CasaAccounts.money(n);
  const paths = {
    home: "M3 10 12 3l9 7v11h-6v-8H9v8H3Z",
    payments: "M3 5h18v14H3Z M3 10h18 M7 15h3",
    expense: "M6 3h12v18l-3-2-3 2-3-2-3 2Z M9 8h6 M9 12h6",
    cleaning: "M15 3l-4 9 M8 11l7 3-4 8-8-3Z",
    users:
      "M3 21v-3a5 5 0 0 1 10 0v3 M16 14a5 5 0 0 1 5 4v3 M8 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M17 3a4 4 0 0 1 0 8",
    bell: "M6 9a6 6 0 0 1 12 0v6l3 3H3l3-3Z M10 21h4",
    settings:
      "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v3 M12 19v3 M2 12h3 M19 12h3 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2",
    water: "M12 2S5 11 5 15a7 7 0 0 0 14 0C19 11 12 2 12 2Z",
    light: "M9 17h6 M10 21h4 M8 14a6 6 0 1 1 8 0l-1 3H9Z",
    gas: "M12 2c4 6-3 7 2 11 2-2 2-4 2-4 7 8 1 14-4 13C3 20 3 12 8 8c-1 5 4 6 4-6Z",
    wifi: "M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8 16a6 6 0 0 1 8 0 M12 20h.1",
    check: "M4 12l5 5L20 6",
    more: "M4 12h1 M11 12h1 M18 12h1",
    plus: "M12 4v16 M4 12h16",
    logout: "M9 3H4v18h5 M9 12h12 M17 8l4 4-4 4",
    lock: "M5 10h14v11H5Z M8 10V6a4 4 0 0 1 8 0v4",
    calendar: "M4 5h16v16H4Z M8 3v4 M16 3v4 M4 10h16",
    chart: "M4 3v18h17 M8 17v-4 M13 17V8 M18 17V5",
  };
  Object.assign(paths, {
    arrow: "M5 12h14 M13 6l6 6-6 6",
    back: "M19 12H5 M11 6l-6 6 6 6",
    shield: "M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6Z M8 12l3 3 5-6",
    eye: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
    eyeOff:
      "M3 3l18 18 M10 5a11 11 0 0 1 12 7s-2 3-5 5 M7 7a19 19 0 0 0-5 5s4 7 10 7c1 0 2-.2 3-.6 M10 10a3 3 0 0 0 4 4",
  });
  const icon = (k) =>
    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="${paths[k] || paths.expense}"/></svg>`;
  let selectedFamily = null;
  let view = "home",
    filter = "pending",
    month = new Date().toISOString().slice(0, 7),
    borrowed = null,
    selectedPay = null,
    reportMode = "monthly";
  let demo = new URLSearchParams(location.search).get("demo") === "1",
    demoRole = "family";
  const demoData = {
    families: [
      "García",
      "López",
      "Martínez",
      "Rodríguez",
      "Sánchez",
      "Pérez",
    ].map((x, i) => ({ id: i + 1, name: "Familia " + x })),
    profiles: [],
    periods: [],
    templates: [],
    expenses: [
      ["Luz", 1200, "2026-10-10"],
      ["Agua", 900, "2026-10-08"],
      ["Internet", 720, "2026-10-05"],
      ["Gas", 1080, "2026-10-02"],
      ["Mantenimiento", 600, "2026-10-15"],
      ["Otros", 400, "2026-10-20"],
    ].map((x, i) => ({
      id: "e" + i,
      concept: x[0],
      total_amount: x[1],
      expense_date: x[2],
      category: i < 4 ? "basic" : "external",
      justification: "Gasto del hogar",
    })),
    payments: [
      {
        id: "p1",
        family_id: 1,
        amount: 150,
        paid_at: "2026-10-14",
        status: "pending",
        note: "Agua",
      },
      {
        id: "p2",
        family_id: 2,
        amount: 120,
        paid_at: "2026-10-13",
        status: "pending",
        note: "Internet",
      },
      {
        id: "p3",
        family_id: 3,
        amount: 180,
        paid_at: "2026-10-12",
        status: "pending",
        note: "Gas",
      },
      {
        id: "p4",
        family_id: 1,
        amount: 120,
        paid_at: "2026-09-15",
        status: "approved",
        note: "Internet",
      },
    ],
    shares: [],
    credits: [],
    cleaning: [1, 2, 3, 4].map((n, i) => ({
      id: "t" + n,
      family_id: n,
      turn_date: "2026-10-" + String(5 + i * 7).padStart(2, "0"),
      status: i === 0 ? "done" : "pending",
      checklist: ["Limpieza de salas", "Cocina", "Baños", "Áreas comunes"],
    })),
    notices: [
      {
        title: "Nuevo gasto registrado",
        body: "Se registró un gasto de mantenimiento.",
        created_at: "2026-10-14T10:00:00",
      },
      {
        title: "Tu comprobante fue aprobado",
        body: "Pago de agua – 150 Bs.",
        created_at: "2026-10-13T10:00:00",
      },
      {
        title: "Nuevo turno de limpieza",
        body: "Tu turno es del 12 al 18 de octubre.",
        created_at: "2026-10-12T10:00:00",
      },
    ],
    notifications: [],
    audit: [],
    qr: [],
    monthly: [],
    proposals: [],
    votes: [],
  };
  demoData.profiles = demoData.families.map((f) => ({
    id: "u" + f.id,
    display_name: f.name,
    contact_email: "familia" + f.id + "@correo.com",
    family_id: f.id,
    role: "family",
  }));
  demoData.profiles.push({
    id: "up",
    display_name: "Carlos",
    contact_email: "carlos@correo.com",
    role: "payer",
  });
  const debts = [
    ["Luz", 200],
    ["Agua", 150],
    ["Internet", 120],
    ["Gas", 180],
  ].map((x, i) => ({
    share_id: "s" + i,
    concept: x[0],
    amount_due: x[1],
    balance: i === 2 ? 0 : x[1],
    debt_status: i === 2 ? "paid" : "pending",
    month_start: "2026-10-01",
    due_date: "2026-10-20",
  }));
  function state() {
    return demo
      ? {
          me: {
            role: demoRole,
            family_id: 1,
            display_name:
              demoRole === "family"
                ? "Familia García"
                : demoRole === "payer"
                  ? "Carlos"
                  : "Administrador",
          },
          D: demoData,
          familyDebts: debts,
        }
      : window.CF?.state();
  }
  function family(id) {
    return state()?.D.families.find((f) => f.id === id)?.name || "Familia";
  }
  const roles = {
    family: "Familia",
    payer: "Encargado de pagos",
    sysadmin: "Administrador",
    restaurant: "Restaurante",
  };
  function tone(concept) {
    const s = String(concept || "").toLowerCase();
    return s.includes("agua")
      ? ["water", "blue"]
      : s.includes("luz")
        ? ["light", "orange"]
        : s.includes("gas")
          ? ["gas", "pink"]
          : s.includes("internet") || s.includes("wifi")
            ? ["wifi", "green"]
            : ["expense", "purple"];
  }
  const statusText = {
    pending: "Pendiente",
    approved: "Aprobado",
    rejected: "Rechazado",
    paid: "Pagado",
    done: "Completado",
    review: "En revisión",
    overdue: "Vencido",
    active: "En curso",
  };
  function badge(s, label) {
    return `<span class="badge ${esc(s)}">${esc(label || statusText[s] || s)}</span>`;
  }
  function tile(k, t) {
    return `<span class="tileIcon ${t}">${icon(k)}</span>`;
  }
  function empty(text = "Todavía no hay registros") {
    return `<div class="empty">${icon("expense")}<strong>${esc(text)}</strong><br><small>Los nuevos registros aparecerán aquí.</small></div>`;
  }
  function formatDate(value, options = { day: "numeric", month: "short" }) {
    if (!value) return "—";
    const date = new Date(String(value).slice(0, 10) + "T12:00:00");
    return Number.isNaN(date.getTime())
      ? String(value)
      : date.toLocaleDateString("es-BO", options);
  }
  function monthbar(compact = false) {
    return `<div class="month ${compact ? "compact" : ""}"><label><span>Mes de consulta</span><input type="month" data-month-picker value="${month}"></label>${compact ? "" : `<div class="month-actions"><button type="button" data-month="-1">Mes anterior</button><button type="button" data-month="1">Mes siguiente</button></div>`}</div>`;
  }
  function tabs(list) {
    return `<div class="tabs line">${list.map(([v, t]) => `<button data-filter="${v}" class="${filter === v ? "selected" : ""}">${t}</button>`).join("")}</div>`;
  }
  function item(k, t, title, sub, right, action) {
    let label = "Ver detalle";
    if (action?.startsWith("data-user")) label = "Ver cuenta y familia";
    if (action?.startsWith("data-turn")) label = "Ver turno y tareas";
    if (action?.startsWith("data-pay")) label = "Ver comprobante";
    if (action?.startsWith("data-family")) label = "Consultar saldo";
    if (action?.startsWith("data-debt")) label = "Pagar esta cuota";
    return `<article class="item">${tile(k, t)}<div class="copy"><strong>${esc(title)}</strong><small>${esc(sub || "")}</small></div><div class="right">${right || ""}</div>${action ? `<button type="button" class="record-action" ${action}>${label}</button>` : ""}</article>`;
  }
  function task(title, description, k, action, primary = false) {
    return `<button type="button" class="task-button ${primary ? "task-primary" : ""}" ${action}>${tile(k, primary ? "plain" : "blue")}<span><strong>${esc(title)}</strong><small>${esc(description)}</small></span></button>`;
  }
  function published(period) {
    return `<div class="published-through">${icon("calendar")}<div><small>Registrado hasta</small><strong>${esc(period ? CasaAccounts.monthName(period) : "Todavía sin registros")}</strong></div></div>`;
  }
  function accountHero(s, id) {
    const account = CasaAccounts.account(s, id);
    return `<section class="account-hero ${account.balance ? "" : "is-clear"}"><span>${account.balance ? "Te falta pagar" : "Estás al día"}</span><strong>${money(CasaAccounts.number(account.balance))}</strong><small>${esc(account.latest ? "Registrado hasta " + CasaAccounts.monthName(account.latest) : "Todavía no se publicaron gastos")}</small></section>`;
  }
  function home(s) {
    if (s.me.role === "restaurant") return restaurant(s);
    const isFamily = s.me.role === "family",
      pending = CasaAccounts.entries(s).filter(
        (p) => p.status === "pending",
      ).length;
    let out = `<div class="hello"><h1>Hola, ${esc(s.me.display_name.split(" ")[0])}</h1><p>¿Qué necesitas hacer hoy?</p></div>`;
    if (isFamily) {
      out +=
        accountHero(s, s.me.family_id) +
        task(
          "Pagar",
          "Subir una foto del comprobante",
          "payments",
          'data-go="newPayment"',
          true,
        ) +
        task(
          "Ver mis deudas",
          "Lo que falta pagar en cada mes",
          "expense",
          'data-go="dues"',
        ) +
        task(
          "Mis pagos",
          "Ver si mi comprobante fue aprobado",
          "check",
          'data-go="payments"',
        );
      const turn = s.D.cleaning
        .filter(
          (t) =>
            t.status === "pending" &&
            (demo
              ? t.family_id === s.me.family_id
              : CF.canManageCleaning(t.family_id)),
        )
        .sort((a, b) => a.turn_date.localeCompare(b.turn_date))[0];
      out += turn
        ? `<section class="card"><h3>Tu próxima limpieza</h3><p>${esc(formatDate(turn.turn_date, { day: "numeric", month: "long", year: "numeric" }))}</p><button class="ghost" data-turn="${esc(turn.id)}">Ver turno y tareas</button></section>`
        : task(
            "Limpieza",
            "Consultar mi semana y mis tareas",
            "cleaning",
            'data-go="cleaning"',
          );
    } else {
      out +=
        published(CasaAccounts.periods(s).at(-1)) +
        task(
          "Registrar facturas",
          "Agua, luz, gas e internet",
          "expense",
          'data-source="monthlyServicesPanel" data-title="Registrar facturas"',
          true,
        ) +
        task(
          "Revisar pagos",
          pending
            ? pending + " comprobantes por revisar"
            : "No hay comprobantes por revisar",
          "payments",
          'data-go="payments"',
        ) +
        task(
          "Ver deudas",
          "Consultar cuánto debe cada familia",
          "users",
          'data-go="balances"',
        ) +
        task(
          "Limpieza esta semana",
          "Ver la semana y tareas de las familias",
          "cleaning",
          'data-go="cleaning"',
        );
    }
    return out;
  }
  function restaurant(s) {
    const rows = s.D.externalDebts || [];
    const balance = (d) =>
      Math.max(Number(d.amount_due || 0) - Number(d.amount_paid || 0), 0);
    const total = rows.reduce((sum, d) => sum + balance(d), 0);
    const now = new Date();
    const today = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, "0"),
      String(now.getDate()).padStart(2, "0"),
    ].join("-");
    const records = rows
      .map((d) => {
        const pending = balance(d),
          paid = pending <= 0.005;
        const status = paid
          ? "paid"
          : d.due_date && d.due_date < today
            ? "overdue"
            : Number(d.amount_paid || 0) > 0
              ? "partial"
              : "pending";
        const service = d.service === "electricity" ? "Luz" : "Agua";
        return item(
          d.service === "electricity" ? "light" : "water",
          d.service === "electricity" ? "orange" : "blue",
          service,
          formatDate(d.month_start, { month: "long", year: "numeric" }) +
            " · " +
            (d.due_date ? "Vence " + formatDate(d.due_date) : "Sin fecha"),
          `<strong>${money(pending)}</strong>${badge(status, status === "partial" ? "Pago parcial" : null)}`,
        );
      })
      .join("");
    return (
      `<div class="hello"><span class="eyebrow">RESTAURANTE</span><h1>Hola, <span>${esc(s.me.display_name)}</span></h1><small>Tus servicios, claros y al día.</small></div><section class="balance-hero"><span class="eyebrow">MI CUENTA</span><p class="hero-label">Saldo pendiente de servicios</p><div class="hero-amount"><strong>${esc(money(total).replace(/ Bs$/, ""))}</strong><span>Bs</span></div><p class="hero-note">Aquí solo aparecen tus importes de luz y agua.</p></section><div class="section-heading"><h3>Mis servicios</h3><button class="section-link" data-click="refreshBtn">Actualizar datos</button></div>` +
      (records || empty("No tienes deudas registradas")) +
      `<button class="ghost" data-source="view-restaurant" data-title="Historial de servicios">Ver historial completo</button>`
    );
  }

  function payments(s) {
    const list = CasaAccounts.entries(s).filter(
      (p) =>
        (s.me.role !== "family" ||
          Number(p.family_id) === Number(s.me.family_id)) &&
        (filter === "all" || p.status === filter),
    );
    return (
      (s.me.role === "family"
        ? task(
            "Pagar",
            "Subir una foto del comprobante",
            "payments",
            'data-go="newPayment"',
            true,
          )
        : "") +
      tabs([
        ["pending", "Por revisar"],
        ["approved", "Aprobados"],
        ["rejected", "Rechazados"],
        ["all", "Todos"],
      ]) +
      list
        .map(
          (p) =>
            `<section class="card"><h3>${esc(s.me.role === "family" ? "Comprobante" : family(p.family_id))}</h3><strong class="card-amount">${money(p.amount)}</strong>${badge(p.status)}<p>${esc(formatDate(p.paid_at, { day: "numeric", month: "short", year: "numeric" }))}</p>${p.status === "pending" ? '<p class="form-note">Todavía no descontado de la deuda.</p>' : ""}<button class="ghost" data-pay="${esc(p.id)}">${s.me.role !== "family" && p.status === "pending" ? "Revisar comprobante" : "Ver detalle"}</button></section>`,
        )
        .join("") +
      (list.length ? "" : empty("No hay pagos en esta sección"))
    );
  }

  function accountScreen(s, id) {
    const account = CasaAccounts.account(s, id);
    const months = account.months.filter(
      (row) =>
        filter === "all" ||
        (filter === "paid" ? row.balance === 0 : row.balance > 0),
    );
    return (
      accountHero(s, id) +
      (s.me.role === "family" && account.balance > 0
        ? task(
            "Pagar",
            "Un comprobante puede cubrir varios meses",
            "payments",
            'data-go="newPayment"',
            true,
          )
        : "") +
      `<h2>Meses registrados</h2>` +
      tabs([
        ["pending", "Por pagar"],
        ["paid", "Al día"],
        ["all", "Todos"],
      ]) +
      months
        .map(
          (row) =>
            `<section class="card"><h3>${esc(CasaAccounts.monthName(row.month))}</h3>${badge(row.balance === 0 ? "paid" : row.paid > 0 ? "partial" : "pending", row.balance === 0 ? "Al día" : row.paid > 0 ? "Pago parcial" : "Pendiente")}<small class="amount-label">Falta pagar</small><strong class="card-amount">${money(CasaAccounts.number(row.balance))}</strong><details><summary>Ver detalle</summary><div class="amount-line"><span>Importe asignado</span><strong>${money(CasaAccounts.number(row.assigned))}</strong></div><div class="amount-line"><span>Pagos y coberturas aplicados</span><strong>${money(CasaAccounts.number(row.paid))}</strong></div>${row.debts.map((d) => `<p>${esc(d.concept)}</p>${s.me.role === "family" && d.balance > 0 ? `<button class="ghost" data-debt="${esc(d.id)}">Pagar solo esta deuda</button>` : ""}`).join("")}</details></section>`,
        )
        .join("") +
      (months.length ? "" : empty("No hay meses para esta selección")) +
      task(
        "Mis pagos",
        "Consultar comprobantes y su estado",
        "payments",
        'data-go="payments"',
      )
    );
  }
  function dues(s) {
    return accountScreen(s, s.me.family_id);
  }

  function expenses(s) {
    const periods = CasaAccounts.periods(s);
    let out =
      published(periods.at(-1)) +
      task(
        "Registrar facturas",
        "Agua, luz, gas e internet",
        "expense",
        'data-source="monthlyServicesPanel" data-title="Registrar facturas"',
        true,
      ) +
      tabs([
        ["services", "Facturas"],
        ["external", "Otros gastos"],
      ]) +
      `<label>Mes que quieres consultar<select data-period><option value="all">Todos los meses</option>${periods
        .slice()
        .reverse()
        .map(
          (p) =>
            `<option value="${p}" ${month === p ? "selected" : ""}>${esc(CasaAccounts.monthName(p))}</option>`,
        )
        .join("")}</select></label>`;
    if (filter === "external")
      return (
        out +
        s.D.expenses
          .filter(
            (e) =>
              e.category === "external" &&
              (month === "all" || e.expense_date.startsWith(month)),
          )
          .map((e) =>
            item(
              "expense",
              "blue",
              e.concept,
              formatDate(e.expense_date),
              `<strong>${money(e.total_amount)}</strong>`,
              `data-expense="${esc(e.id)}"`,
            ),
          )
          .join("") +
        task(
          "Registrar otro gasto",
          "Arreglos o compras de la casa",
          "plus",
          'data-go="newExpense"',
        )
      );
    const list = (s.D.monthly || [])
      .filter((b) => month === "all" || b.month_start.startsWith(month))
      .sort((a, b) => b.month_start.localeCompare(a.month_start));
    out +=
      list
        .map((b) => {
          const gross = ["water", "electricity", "gas", "wifi"].reduce(
            (sum, key) => sum + CasaAccounts.cents(b[key + "_amount"]),
            0,
          );
          return `<section class="card"><h3>${esc(CasaAccounts.monthName(b.month_start.slice(0, 7)))}</h3><small class="amount-label">Total del mes</small><strong class="card-amount">${money(CasaAccounts.number(gross))}</strong><p>Total repartido: ${money(b.family_total_amount)}</p><details><summary>Ver facturas</summary>${[
            ["water", "Agua"],
            ["electricity", "Luz"],
            ["gas", "Gas"],
            ["wifi", "Internet"],
          ]
            .map(
              ([key, label]) =>
                `<div class="amount-line"><span>${label}</span><strong>${money(b[key + "_amount"])}</strong></div>`,
            )
            .join(
              "",
            )}<p>Pagar hasta ${esc(formatDate(b.due_date, { day: "numeric", month: "short", year: "numeric" }))}</p></details></section>`;
        })
        .join("") || empty("No hay facturas para esta selección");
    return (
      out +
      `<details class="other-options"><summary>Más opciones de gastos</summary><button class="ghost" data-source="utilityCreditsPanel" data-title="Descuentos">Ver descuentos</button><button class="ghost" data-source="externalUtilityPanel" data-title="Restaurante">Ver aportes del restaurante</button></details>`
    );
  }

  function balances(s) {
    return (
      published(CasaAccounts.periods(s).at(-1)) +
      `<label>Buscar familia<input data-family-search type="search" placeholder="Nombre de la familia"></label><div class="family-choices">` +
      s.D.families
        .map((f) => {
          const balance = CasaAccounts.account(s, f.id).balance;
          return task(
            f.name,
            balance
              ? "Falta pagar " + money(CasaAccounts.number(balance))
              : "Al día · Bs 0,00",
            "users",
            `data-family="${esc(f.id)}" data-family-name="${esc(f.name.toLowerCase())}"`,
          );
        })
        .join("") +
      "</div>"
    );
  }
  function cleaningFamily(id) {
    return family(
      !demo && window.CF?.cleaningHousehold ? CF.cleaningHousehold(id) : id,
    );
  }
  function cleaning(s) {
    const all = [...s.D.cleaning].sort(
      (a, b) =>
        a.turn_date.localeCompare(b.turn_date) ||
        Number(a.family_id) - Number(b.family_id),
    );
    const rows = all.filter((t) => t.turn_date.startsWith(month));
    const rotation = s.D.rotation;
    const status = rotation?.enabled
      ? "Repetición semanal activa"
      : "Turnos organizados por semana";
    return (
      `<div class="cleaning-summary"><span class="tileIcon blue">${icon("calendar")}</span><div><strong>Una familia, cada semana</strong><p>${esc(status)}</p></div></div>` +
      monthbar() +
      (rows
        .map((t) =>
          item(
            "cleaning",
            "blue",
            cleaningFamily(t.family_id),
            "Semana del " +
              formatDate(t.turn_date, { day: "numeric", month: "long" }),
            badge(t.status === "done" ? "done" : "pending"),
            `data-turn="${esc(t.id)}"`,
          ),
        )
        .join("") || empty("No hay turnos para este mes")) +
      (s.me.role === "sysadmin"
        ? '<div class="action-stack"><button class="primary" data-source="cleaningRotationPanel" data-title="Repetir limpieza cada semana">Repetir el orden por semana</button><button class="ghost" data-source="cleaningCreatePanel" data-title="Asignar una semana">Asignar una semana manualmente</button></div>'
        : "") +
      (["sysadmin", "payer"].includes(s.me.role)
        ? '<button class="ghost" data-source="swapList" data-title="Cambios de turno">Consultar intercambios de turnos</button>'
        : "")
    );
  }
  function notices(s) {
    return (
      (s.D.notices
        .map((n) =>
          item(
            "bell",
            "orange",
            n.title,
            n.body,
            `<small>${esc(formatDate(n.created_at))}</small>`,
            `data-notice="${esc(n.title)}"`,
          ),
        )
        .join("") || empty("Sin avisos")) +
      (s.me.role !== "family"
        ? '<button class="primary" data-source="noticeCreatePanel" data-title="Publicar aviso">＋ Publicar aviso</button>'
        : "") +
      '<button class="ghost" data-source="notificationList" data-title="Notificaciones">Mis notificaciones</button>'
    );
  }
  function users(s) {
    let list = s.D.profiles.filter(
      (p) => filter === "all" || p.role === filter,
    );
    return (
      tabs([
        ["all", "Todos"],
        ["family", "Familias"],
        ["payer", "Encargado"],
        ["restaurant", "Restaurante"],
        ["sysadmin", "Admin"],
      ]) +
      list
        .map((p) =>
          item(
            "users",
            "purple",
            p.display_name,
            [
              p.contact_email,
              p.family_id ? family(p.family_id) : null,
              p.second_family_id ? family(p.second_family_id) : null,
            ]
              .filter(Boolean)
              .join(" · "),
            badge("active", roles[p.role] || p.role),
            `data-user="${esc(p.id)}"`,
          ),
        )
        .join("") +
      '<button class="primary" data-source="userForm" data-title="Nuevo usuario">＋ Nuevo usuario</button>'
    );
  }
  function reports(s) {
    let list = s.D.expenses.filter((e) =>
        reportMode === "annual"
          ? e.expense_date.startsWith(month.slice(0, 4))
          : e.expense_date.startsWith(month),
      ),
      total = list.reduce((a, e) => a + Number(e.total_amount), 0);
    const by = {};
    list.forEach(
      (e) => (by[e.concept] = (by[e.concept] || 0) + Number(e.total_amount)),
    );
    let angle = 0;
    const colors = [
      "#4a8b6c",
      "#e5b16a",
      "#85a3b7",
      "#c89981",
      "#9a90af",
      "#b3c79d",
    ];
    const stops = Object.entries(by)
      .map(([k, n], i) => {
        let start = angle;
        angle += total ? (n / total) * 100 : 0;
        return `${colors[i % colors.length]} ${start}% ${angle}%`;
      })
      .join(",");
    return (
      `<div class="tabs line">${[
        ["monthly", "Mensual"],
        ["annual", "Anual"],
        ["concept", "Por concepto"],
      ]
        .map(
          ([k, t]) =>
            `<button data-report="${k}" class="${reportMode === k ? "selected" : ""}">${t}</button>`,
        )
        .join("")}</div>` +
      monthbar() +
      `<div class="card"><div class="ring" style="background:${stops ? "conic-gradient(" + stops + ")" : "#eee"}"><div class="inside"><small>Total</small><strong>${money(total)}</strong></div></div>${
        Object.entries(by)
          .map(
            ([k, n], i) =>
              `<div class="reportline"><span><span style="color:${colors[i % colors.length]}">●</span> ${esc(k)}</span><strong>${money(n)}</strong></div>`,
          )
          .join("") || empty()
      }</div><div class="row"><button class="primary" data-click="exportPdfBtn">Exportar PDF</button><button class="ghost" data-click="exportXlsxBtn">Exportar Excel</button></div>`
    );
  }
  function row(k, n, action) {
    return `<button class="setting-row" ${action}>${icon(k)}<span>${n}</span></button>`;
  }
  function settings(s) {
    const size = document.documentElement.classList.contains("extra-large-text")
      ? "extra"
      : document.documentElement.classList.contains("large-text")
        ? "large"
        : "normal";
    return (
      profileCard(s) +
      row(
        "lock",
        "Cambiar contraseña",
        'data-source="passwordForm" data-title="Cambiar contraseña"',
      ) +
      `<section class="card"><h3>Tamaño de letra</h3><div class="tabs">${[
        ["normal", "Normal"],
        ["large", "Grande"],
        ["extra", "Extra grande"],
      ]
        .map(
          ([value, label]) =>
            `<button data-font-size="${value}" class="${size === value ? "selected" : ""}" aria-pressed="${size === value}">${label}</button>`,
        )
        .join(
          "",
        )}</div><label class="theme-toggle"><span>Modo oscuro</span><input type="checkbox" id="simpleDark" ${document.documentElement.classList.contains("dark-mode") ? "checked" : ""}></label></section>` +
      row("bell", "Activar notificaciones", 'data-click="phoneNotifyBtn"') +
      row("calendar", "Actualizar datos", 'data-click="refreshBtn"') +
      '<a class="ghost" href="https://raw.githubusercontent.com/achaalex326-ux/Casa/main/entregas/casa-familias-5.9/CasaFamilias-5.9-pruebas.apk">Descargar app Android</a><p class="form-note">Casa Familias 5.9</p>' +
      row("logout", "Cerrar sesión", 'data-logout="1"')
    );
  }
  function administration(s) {
    if (s.me.role !== "sysadmin")
      return empty("Tu cuenta no tiene acceso a esta sección");
    return (
      [
        ["payments", "Configurar QR", "qrAdminPanel"],
        ["users", "Nombres de familias", "familyNamesPanel"],
        ["users", "Cambiar familia de un usuario", "userFamiliesPanel"],
        ["users", "Familias del encargado", "payerFamilyConfigPanel"],
        ["expense", "Plantillas de gastos", "mobileTemplates"],
        ["calendar", "Cerrar o abrir mes", "periodPanel"],
        ["chart", "Auditoría", "auditBody"],
      ]
        .map(([icon, label, id]) =>
          row(icon, label, `data-source="${id}" data-title="${label}"`),
        )
        .join("") + row("chart", "Guardar respaldo", 'data-click="backupBtn"')
    );
  }
  function more(s) {
    if (s.me.role === "restaurant")
      return row("settings", "Mi cuenta", 'data-go="settings"');
    const list = [
      [
        "payments",
        s.me.role === "family" ? "Mis pagos" : "Revisar pagos",
        "payments",
      ],
    ];
    if (s.me.role !== "family") list.push(["cleaning", "Limpieza", "cleaning"]);
    if (s.me.role === "sysadmin") list.push(["users", "Usuarios", "users"]);
    if (s.me.role !== "family") list.push(["reports", "Reportes", "chart"]);
    list.push(
      ["notices", "Avisos", "bell"],
      ["proposals", "Propuestas", "expense"],
      ["settings", "Mi cuenta", "settings"],
    );
    if (s.me.role === "sysadmin")
      list.push(["administration", "Administración", "shield"]);
    return list
      .map(([view, label, icon]) => row(icon, label, `data-go="${view}"`))
      .join("");
  }
  function initials(n) {
    return String(n || "CF")
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((x) => x[0])
      .join("")
      .toUpperCase();
  }
  function profileCard(s) {
    return `<div class="settings-profile"><span class="avatar">${esc(initials(s.me.display_name))}</span><div><strong>${esc(s.me.display_name)}</strong><br><small>${esc(roles[s.me.role] || s.me.role)}</small></div></div>`;
  }
  const titles = {
    home: "",
    payments: "Mis pagos",
    expenses: "Gastos de la casa",
    dues: "Mis deudas",
    balances: "Deudas",
    cleaning: "Turnos de limpieza",
    notices: "Avisos de la casa",
    users: "Usuarios",
    reports: "Reportes",
    settings: "Mi cuenta",
    administration: "Administración",
    more: "Más",
    familyAccount: "Deudas por mes",
  };
  const descriptions = {
    payments: "Tu historial, comprobantes y estados de pago.",
    expenses: "Cada gasto del hogar, en un solo lugar.",
    dues: "Consulta tus cuotas y el detalle de tus pagos.",
    balances: "Un resumen claro para cada familia.",
    cleaning: "Un hogar cuidado entre todos.",
    notices: "Lo que pasa en casa, siempre a mano.",
    users: "Las personas que hacen de esta casa un hogar.",
    reports: "Entiende cómo se distribuyen los gastos.",
    settings: "Tu perfil, tus preferencias y la administración del hogar.",
    more: "Todo lo que necesitas, a un toque.",
  };
  try {
    document.documentElement.classList.toggle(
      "dark-mode",
      localStorage.getItem("cf_dark_mode") === "1",
    );
  } catch (_) {}
  function render() {
    try {
      const s = state();
      if (!s?.me) return;
      if (
        s.me.role === "restaurant" &&
        !["home", "settings", "more"].includes(view)
      )
        view = "home";
      if (
        s.me.role === "family" &&
        [
          "expenses",
          "users",
          "balances",
          "reports",
          "familyAccount",
          "administration",
        ].includes(view)
      )
        view = "home";
      if (
        s.me.role !== "sysadmin" &&
        ["users", "administration"].includes(view)
      )
        view = "home";
      const unread = s.D.notifications.some((n) => !n.read_at);
      $("mobileHeader").innerHTML =
        `<div class="header-brand"><span class="header-mark">${icon("home")}</span><div><strong>Casa Familias</strong><small>MI HOGAR, AL DÍA</small></div></div><div class="header-actions"><button data-go="settings" class="account-button" aria-label="Abrir mi cuenta">${icon("users")}<span>Cuenta</span></button></div>`;

      const screens = {
        home,
        payments,
        expenses,
        dues,
        balances,
        cleaning,
        notices,
        users,
        reports,
        settings,
        administration,
        more,
        familyAccount: (s) =>
          `<button class="text-action" data-go="balances">Volver a familias</button><h2>${esc(family(selectedFamily))}</h2>` +
          accountScreen(s, selectedFamily),
      };
      const demoBar = demo
        ? `<div class="demoBanner"><span><strong>Modo demostración</strong><span>Datos de ejemplo</span></span><div class="demoRoles">${[
            ["family", "Familia"],
            ["payer", "Encargado"],
            ["sysadmin", "Admin"],
          ]
            .map(
              ([role, label]) =>
                `<button data-role="${role}" aria-pressed="${s.me.role === role}">${label}</button>`,
            )
            .join("")}</div></div>`
        : "";
      const description =
        view !== "home"
          ? `<div class="screen-heading"><span class="eyebrow">CASA FAMILIAS</span><h1>${esc(s.me.role !== "family" && view === "payments" ? "Revisar pagos" : titles[view] || "")}</h1></div><p class="page-heading">${esc(s.me.role !== "family" && view === "payments" ? "Revisa cada comprobante y mantén las cuentas al día." : descriptions[view] || "")}</p>`
          : "";
      $("mobileContent").innerHTML =
        demoBar + description + (screens[view] || home)(s);
      const nav =
        s.me.role === "restaurant"
          ? [
              ["home", "Servicios", "expense"],
              ["settings", "Mi cuenta", "users"],
            ]
          : s.me.role === "family"
            ? [
                ["home", "Inicio", "home"],
                ["dues", "Deudas", "expense"],
                ["cleaning", "Limpieza", "cleaning"],
                ["more", "Más", "more"],
              ]
            : [
                ["home", "Inicio", "home"],
                ["expenses", "Gastos", "expense"],
                ["balances", "Deudas", "users"],
                ["more", "Más", "more"],
              ];
      const active =
        view === "familyAccount"
          ? "balances"
          : nav.some(([v]) => v === view)
            ? view
            : "more";
      $("mobileNav").innerHTML = nav
        .map(
          ([v, t, k]) =>
            `<button data-go="${v}" class="${active === v ? "active" : ""}" ${active === v ? 'aria-current="page"' : ""}>${icon(k)}<span>${t}</span></button>`,
        )
        .join("");
      bind();
      document.querySelectorAll("[data-font-size]").forEach(
        (button) =>
          (button.onclick = () => {
            if (demo) {
              document.documentElement.classList.toggle(
                "large-text",
                button.dataset.fontSize !== "normal",
              );
              document.documentElement.classList.toggle(
                "extra-large-text",
                button.dataset.fontSize === "extra",
              );
            } else CF.applyFontSize(button.dataset.fontSize);
            render();
          }),
      );
      const dark = document.getElementById("simpleDark");
      if (dark)
        dark.onchange = () => {
          document.documentElement.classList.toggle("dark-mode", dark.checked);
          try {
            localStorage.setItem("cf_dark_mode", dark.checked ? "1" : "0");
          } catch (_) {}
        };
      document.querySelectorAll("[data-period]").forEach((input) => {
        input.value = month;
        input.onchange = () => {
          month = input.value;
          render();
        };
      });
      document.querySelectorAll("[data-family-search]").forEach(
        (input) =>
          (input.oninput = () => {
            document
              .querySelectorAll("[data-family-name]")
              .forEach(
                (row) =>
                  (row.hidden = !row.dataset.familyName.includes(
                    input.value.toLowerCase().trim(),
                  )),
              );
          }),
      );
      document
        .querySelectorAll(".center-screen")
        .forEach((x) => x.classList.add("hidden"));
      $("mobileApp").hidden = false;
      window.CasaStartup?.ready();
    } catch (error) {
      if (window.CasaStartup) window.CasaStartup.fail();
      else throw error;
    }
  }
  function go(v) {
    $("modal").classList.add("hidden");
    closeSource(false);
    const s = state();
    if (!s?.me) return;
    if (s.me.role === "restaurant" && !["home", "settings", "more"].includes(v))
      return;
    if (v === "newPayment") {
      window.SimplePayment.open();
      return;
    }
    if (v === "newExpense") {
      if (demo) {
        showDemoForm("Registrar gasto");
        return;
      }
      openSource("expenseCreatePanel", "Registrar gasto");
      return;
    }
    if (v === "proposals") {
      if (demo) {
        modal(
          "<h2>Propuestas y votación</h2><p>No hay propuestas abiertas.</p>",
        );
        return;
      }
      openSource("view-proposals", "Propuestas y votación");
      return;
    }
    if (
      s.me.role === "family" &&
      [
        "expenses",
        "users",
        "balances",
        "reports",
        "familyAccount",
        "administration",
      ].includes(v)
    )
      return;
    if (s.me.role !== "sysadmin" && ["users", "administration"].includes(v))
      return;
    view = v;
    filter = v === "expenses" ? "services" : v === "users" ? "all" : "pending";
    if (v === "expenses") month = "all";
    else if (month === "all") month = CasaAccounts.today().slice(0, 7);
    render();
    window.scrollTo(0, 0);
  }
  function bind() {
    document
      .querySelectorAll("#mobileApp [data-go]")
      .forEach((b) => (b.onclick = () => go(b.dataset.go)));
    document.querySelectorAll("#mobileApp [data-role]").forEach(
      (b) =>
        (b.onclick = () => {
          demoRole = b.dataset.role;
          go("home");
        }),
    );
    document.querySelectorAll("[data-month]").forEach(
      (b) =>
        (b.onclick = () => {
          const d = new Date(month + "-02T12:00:00");
          d.setMonth(d.getMonth() + Number(b.dataset.month));
          month =
            d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0");
          if (!demo) {
            $("expenseMonthFilter").value = month;
            $("reportMonth").value = month;
          }
          render();
        }),
    );
    document.querySelectorAll("[data-month-picker]").forEach(
      (input) =>
        (input.onchange = () => {
          if (!/^\d{4}-\d{2}$/.test(input.value)) return;
          month = input.value;
          if (!demo) {
            $("expenseMonthFilter").value = month;
            $("reportMonth").value = month;
          }
          render();
        }),
    );
    document.querySelectorAll("#mobileApp [data-filter]").forEach(
      (b) =>
        (b.onclick = () => {
          filter = b.dataset.filter;
          render();
        }),
    );
    document.querySelectorAll("[data-report]").forEach(
      (b) =>
        (b.onclick = () => {
          reportMode = b.dataset.report;
          render();
        }),
    );
    document
      .querySelectorAll("#mobileApp [data-source]")
      .forEach(
        (b) =>
          (b.onclick = () => openSource(b.dataset.source, b.dataset.title)),
      );
    document.querySelectorAll("#mobileApp [data-click]").forEach(
      (b) =>
        (b.onclick = () => {
          if (demo) {
            notify("Disponible con tu cuenta real");
            return;
          }
          if (["exportPdfBtn", "exportXlsxBtn"].includes(b.dataset.click))
            $("reportMonth").value = month;
          $(b.dataset.click)?.click();
        }),
    );
    document
      .querySelectorAll("[data-pay]")
      .forEach((b) => (b.onclick = () => detailPayment(b.dataset.pay)));
    document
      .querySelectorAll("[data-turn]")
      .forEach((b) => (b.onclick = () => detailTurn(b.dataset.turn)));
    document.querySelectorAll("[data-debt]").forEach(
      (b) =>
        (b.onclick = () => {
          window.SimplePayment.open({ shareId: b.dataset.debt });
        }),
    );
    document.querySelectorAll("[data-family]").forEach(
      (b) =>
        (b.onclick = () => {
          selectedFamily = Number(b.dataset.family);
          go("familyAccount");
        }),
    );
    document.querySelectorAll("[data-expense]").forEach(
      (b) =>
        (b.onclick = () => {
          const e = state().D.expenses.find((x) => x.id === b.dataset.expense);
          modal(
            `<h2>${esc(e.concept)}</h2><strong class="big-number">${money(e.total_amount)}</strong><p>${esc(e.justification || "")}</p><p>${esc(e.expense_date)}</p>${!demo ? CF.receiptBtn(e.receipt_path) : ""}${!demo ? '<button id="expenseComments" class="ghost">Comentarios</button>' : ""}`,
          );
          if (!demo) {
            CF.bindReceiptButtons();
            $("expenseComments").onclick = () => CF.openComments(e.id);
          }
        }),
    );
    document.querySelectorAll("[data-user]").forEach(
      (b) =>
        (b.onclick = () => {
          const u = state().D.profiles.find((x) => x.id === b.dataset.user);
          if (!demo && CF.prepareUserAssignment(u.id)) {
            openSource("userFamiliesPanel", "Familia de " + u.display_name);
            return;
          }
          modal(
            `<h2>${esc(u.display_name)}</h2><p>${esc(u.contact_email)}</p><p>${esc(roles[u.role])}</p>`,
          );
        }),
    );
    document.querySelectorAll("[data-notice]").forEach(
      (b) =>
        (b.onclick = () => {
          const n = state().D.notices.find((x) => x.title === b.dataset.notice);
          modal(`<h2>${esc(n.title)}</h2><p>${esc(n.body)}</p>`);
        }),
    );
    document
      .querySelectorAll("[data-logout]")
      .forEach(
        (b) =>
          (b.onclick = () =>
            demo ? (location.href = "index.html") : CF.logout()),
      );
  }
  function notify(t) {
    $("toast").textContent = t;
    $("toast").classList.remove("hidden");
    setTimeout(() => $("toast").classList.add("hidden"), 3500);
  }
  function modal(html) {
    $("modalContent").innerHTML = html;
    $("modal").classList.remove("hidden");
    $("modalClose").onclick = () => $("modal").classList.add("hidden");
  }
  function detailPayment(id) {
    selectedPay = id;
    const s = state(),
      p = CasaAccounts.entries(s).find((row) => row.id === id),
      manager = ["payer", "sysadmin"].includes(s.me.role);
    if (!p) return;
    const parts = p.batch
      ? p.status === "approved"
        ? s.D.payments.filter((row) => row.batch_id === id)
        : p.planned_allocation || []
      : [];
    const debtRows = CasaAccounts.debts(s, p.family_id);
    modal(
      `<h2>Detalle del comprobante</h2><strong class="card-amount">${money(p.amount)}</strong>${badge(p.status)}<p>${esc(formatDate(p.paid_at, { day: "numeric", month: "long", year: "numeric" }))}</p>${parts.length ? "<h3>" + (p.status === "approved" ? "Meses pagados" : "Meses previstos") + "</h3>" : ""}${parts
        .map((part) => {
          const debt = debtRows.find((d) => d.id === part.share_id);
          const period =
            CasaAccounts.month(part.month_start || part.expense_date) ||
            debt?.month;
          return `<div class="amount-line"><span>${esc(CasaAccounts.monthName(period))}</span><strong>${money(p.status === "approved" ? part.applied_amount : part.amount)}</strong></div>`;
        })
        .join(
          "",
        )}<p>${esc(p.note || "")}</p><div id="receiptDetail"></div>${manager && p.status === "pending" ? '<button id="approvePay" class="primary">Aprobar pago</button><button id="rejectPay" class="ghost">Rechazar comprobante</button>' : ""}`,
    );
    if (!demo && p.receipt_path)
      CF.receiptURL(p.receipt_path)
        .then((url) => {
          if (selectedPay === id && $("receiptDetail")) {
            const img = document.createElement("img");
            img.src = url;
            img.alt = "Comprobante del pago";
            img.className = "receiptPreview";
            $("receiptDetail").append(img);
          }
        })
        .catch(() => {
          $("receiptDetail").textContent =
            "No se pudo cargar la foto. Intenta de nuevo.";
        });
    for (const [button, approve] of [
      ["approvePay", true],
      ["rejectPay", false],
    ])
      if ($(button))
        $(button).onclick = async () => {
          if (demo) {
            notify("Demostración: no se modifican datos.");
            return;
          }
          if (
            !window.confirm(
              (approve ? "¿Aprobar" : "¿Rechazar") +
                " el pago de " +
                money(p.amount) +
                "?",
            )
          )
            return;
          if (p.batch) {
            try {
              $(button).disabled = true;
              await CF.reviewPaymentBatch(id, approve);
              $("modal").classList.add("hidden");
              render();
            } catch (error) {
              notify(error.message);
              if ($(button)) $(button).disabled = false;
            }
          } else {
            const old = document.querySelector(
              `#paymentsBody .review-pay[data-id="${CSS.escape(id)}"][data-ok="${approve ? "1" : "0"}"]`,
            );
            if (old) {
              await old.onclick();
              $("modal").classList.add("hidden");
              render();
            } else notify("Actualiza los datos y vuelve a intentar.");
          }
        };
  }
  function detailTurn(id) {
    const s = state(),
      t = s.D.cleaning.find((x) => x.id === id),
      can = demo
        ? s.me.role === "sysadmin" || s.me.family_id === t.family_id
        : CF.canManageCleaning(t.family_id);
    modal(
      `<h2>Mi turno</h2><h3>${esc(formatDate(t.turn_date, { day: "numeric", month: "long", year: "numeric" }))}</h3>${badge(t.status === "done" ? "done" : "active")}<h3>Tareas</h3><div class="card">${(t.checklist || []).map((x) => `<label class="checkrow"><input type="checkbox" ${t.status === "done" ? "checked disabled" : ""}>${esc(x)}</label>`).join("")}</div><p>${esc(t.note || "")}</p>${can && t.status === "pending" ? '<button class="primary" id="completeTurn">Marcar como completado</button>' + (["sysadmin", "payer"].includes(s.me.role) ? '<button class="ghost" id="swapTurn">Solicitar intercambio</button>' : "") : ""}`,
    );
    if ($("completeTurn"))
      $("completeTurn").onclick = () => {
        if (demo) {
          notify("Demostración: no se modifican datos");
          return;
        }
        if (
          [...$("modalContent").querySelectorAll("input[type=checkbox]")].some(
            (x) => !x.checked,
          )
        ) {
          notify("Marca las tareas realizadas antes de completar");
          return;
        }
        CF.openCompleteCleaning(id);
      };
    if ($("swapTurn"))
      $("swapTurn").onclick = () =>
        demo ? notify("Disponible con tu cuenta real") : CF.openSwap(id);
  }
  function openSource(id, title) {
    if (id === "monthlyServicesPanel" && window.SimpleServices) {
      window.SimpleServices.open();
      return;
    }
    if (
      state()?.me?.role === "restaurant" &&
      !["view-restaurant", "view-settings", "passwordForm"].includes(id)
    )
      return;
    if (demo) {
      showDemoForm(title);
      return;
    }
    closeSource(false);
    let node = $(id);
    if (!node) {
      notify("Esta función no está disponible en este momento");
      return;
    }
    const s = state();
    if (
      [
        "userForm",
        "view-admin",
        "qrAdminPanel",
        "periodPanel",
        "payerFamilyConfigPanel",
        "familyNamesPanel",
        "userFamiliesPanel",
        "cleaningRotationPanel",
        "auditBody",
        "cleaningCreatePanel",
      ].includes(id) &&
      s.me.role !== "sysadmin"
    )
      return;
    if (node.tagName === "TBODY") node = node.closest("table");
    if (id === "paymentForm") {
      node = node.parentElement;
    }
    const marker = document.createComment("presentation mount");
    node.parentNode.insertBefore(marker, node);
    borrowed = {
      node,
      marker,
      hidden: node.classList.contains("hidden"),
      style: node.style.display,
    };
    node.classList.remove("hidden");
    node.style.display = "block";
    node.classList.add("source-host");
    if (node.tagName === "DETAILS") node.open = true;
    node.querySelectorAll("details").forEach((d) => (d.open = true));
    $("sourceTitle").textContent = title || "Detalle";
    $("sourceContent").append(node);
    $("sourceOverlay").hidden = false;
  }
  function closeSource(refresh = true) {
    if (borrowed) {
      let { node, marker, hidden, style } = borrowed;
      marker.replaceWith(node);
      node.classList.toggle("hidden", hidden);
      node.style.display = style;
      node.classList.remove("source-host");
      borrowed = null;
    }
    $("sourceOverlay").hidden = true;
    if (refresh) render();
  }
  function back() {
    if (window.SimpleServices?.isOpen()) return window.SimpleServices.back();
    if (window.SimplePayment?.isOpen()) return window.SimplePayment.back();
    if (!$("modal").classList.contains("hidden")) {
      $("modal").classList.add("hidden");
      return true;
    }
    if (borrowed || !$("sourceOverlay").hidden) {
      closeSource();
      return true;
    }
    if (view !== "home") {
      go("home");
      return true;
    }
    return false;
  }
  function showDemoForm(title) {
    modal(
      `<h2>${esc(title)}</h2><label>Concepto<input value="Luz"></label><label>Monto (Bs)<input type="number" value="200"></label><label>Fecha<input type="date" value="2026-10-14"></label><label>Comprobante<input type="file" accept="image/*"></label><label>Notas (opcional)<textarea placeholder="Añade una nota..."></textarea></label><button class="primary" onclick="Mobile.notify('Vista de demostración: usa tu cuenta para guardar')">Guardar</button>`,
    );
  }
  window.addEventListener("cf:update", () => {
    if (!$("sourceOverlay").hidden) return;
    render();
  });
  document.addEventListener("DOMContentLoaded", () => {
    document
      .querySelectorAll(".brand-logo")
      .forEach((image) => (image.src = "logo.svg"));
    $("loginForm").querySelector(".primary").textContent = "Entrar a mi cuenta";
    const password = $("loginPassword"),
      toggle = $("toggleLoginPassword");
    toggle.innerHTML = icon("eye");
    toggle.onclick = () => {
      const visible = password.type === "password";
      password.type = visible ? "text" : "password";
      toggle.setAttribute("aria-pressed", String(visible));
      toggle.setAttribute(
        "aria-label",
        visible ? "Ocultar contraseña" : "Mostrar contraseña",
      );
      toggle.innerHTML = icon(visible ? "eyeOff" : "eye");
    };
    $("modalClose").onclick = () => $("modal").classList.add("hidden");
    const settingIcons = {
      secure: "lock",
      accessibility: "settings",
      app: "home",
      help: "bell",
    };
    document.querySelectorAll(".setting-icon").forEach((node) => {
      const key = Object.keys(settingIcons).find((key) =>
        node.classList.contains(key),
      );
      if (key) node.innerHTML = icon(settingIcons[key]);
    });
    if (window.AndroidFiles) $("settingsInstallBtn")?.classList.add("hidden");
    const sessionHelp = document.querySelector(
      ".settings-actions-card .setting-heading p",
    );
    if (sessionHelp)
      sessionHelp.textContent =
        "Administra el acceso a tu cuenta en este dispositivo.";
    let activeOverlay = null,
      previousFocus = null;
    const syncOverlay = () => {
      if (!window.document?.body) return;
      const next = !$("modal").classList.contains("hidden")
        ? $("modal")
        : !$("sourceOverlay").hidden
          ? $("sourceOverlay")
          : null;
      document.body.classList.toggle("overlay-open", Boolean(next));
      if (next === activeOverlay) return;
      if (next) {
        if (!activeOverlay) previousFocus = document.activeElement;
        const focusTarget = next.querySelector(
          "input:not([type=hidden]),select,textarea,button",
        );
        focusTarget?.focus({ preventScroll: true });
      } else if (previousFocus?.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
      activeOverlay = next;
    };
    const observer = new MutationObserver(syncOverlay);
    observer.observe($("modal"), {
      attributes: true,
      attributeFilter: ["class"],
    });
    observer.observe($("sourceOverlay"), {
      attributes: true,
      attributeFilter: ["hidden"],
    });
    window.addEventListener("pagehide", (event) => {
      if (!event.persisted) observer.disconnect();
    });
    document.addEventListener(
      "keydown",
      (event) => {
        if (event.key === "Escape") {
          if (!$("modal").classList.contains("hidden")) {
            $("modal").classList.add("hidden");
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
          }
          if (!$("sourceOverlay").hidden) {
            closeSource();
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
          }
        }
        if (event.key !== "Tab" || !activeOverlay) return;
        const focusable = [
          ...activeOverlay.querySelectorAll(
            "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]",
          ),
        ].filter((node) => node.getClientRects().length && !node.hidden);
        if (!focusable.length) return;
        const first = focusable[0],
          last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      },
      true,
    );
    if (demo) {
      month = "2026-10";
      render();
    }
    // Convert generated Blob downloads into Android's native document picker.
    const click = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (window.AndroidFiles && this.href.startsWith("blob:")) {
        fetch(this.href)
          .then((r) => r.blob())
          .then((blob) => {
            const reader = new FileReader();
            reader.onload = () =>
              AndroidFiles.save(
                this.download || "CasaFamilias",
                blob.type || "application/octet-stream",
                String(reader.result).split(",")[1],
              );
            reader.readAsDataURL(blob);
          });
        return;
      }
      return click.call(this);
    };
  });
  return {
    go,
    render,
    closeSource,
    openSource,
    back,
    notify,
    getState: state,
    accountScreen,
    icon,
    isDemo: () => demo,
  };
})();
