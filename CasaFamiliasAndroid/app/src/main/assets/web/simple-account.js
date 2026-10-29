/* Shared presentation rules: integer cents, existing approved allocations only. */
window.CasaAccounts = (() => {
  "use strict";
  const today = () => {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/La_Paz",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date());
    return ["year", "month", "day"]
      .map((type) => parts.find((p) => p.type === type).value)
      .join("-");
  };
  const cents = (value) => {
    let text = String(value || 0)
      .trim()
      .replace(/\s/g, "");
    if (text.includes(",") && text.includes("."))
      text =
        text.lastIndexOf(",") > text.lastIndexOf(".")
          ? text.replace(/\./g, "").replace(",", ".")
          : text.replace(/,/g, "");
    else text = text.replace(",", ".");
    const match = /^([+-]?)(\d+)(?:\.(\d*))?$/.exec(text);
    if (!match) return NaN;
    const fraction = (match[3] || "").padEnd(3, "0");
    let result = BigInt(match[2]) * 100n + BigInt(fraction.slice(0, 2));
    if (fraction[2] >= "5") result++;
    if (match[1] === "-") result = -result;
    return result > BigInt(Number.MAX_SAFE_INTEGER) ||
      result < BigInt(Number.MIN_SAFE_INTEGER)
      ? NaN
      : Number(result);
  };
  const number = (value) => value / 100;
  const month = (value) =>
    /^\d{4}-(0[1-9]|1[0-2])/.test(String(value || ""))
      ? String(value).slice(0, 7)
      : "";
  const monthName = (value) =>
    value
      ? new Date(value + "-02T12:00:00").toLocaleDateString("es-BO", {
          month: "long",
          year: "numeric",
        })
      : "Sin mes";
  const money = (value) =>
    "Bs " +
    Number(value || 0).toLocaleString("es-BO", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  function debts(state, familyId = state.me.family_id) {
    if (
      state.me.role === "restaurant" ||
      (state.me.role === "family" &&
        Number(familyId) !== Number(state.me.family_id))
    )
      return [];
    const data = state.D;
    const source =
      state.me.role === "family"
        ? state.familyDebts || []
        : (data.shares || []).filter(
            (row) => Number(row.family_id) === Number(familyId),
          );
    return source
      .map((row) => {
        const expense = (data.expenses || []).find(
          (item) => item.id === row.expense_id,
        );
        const bill = (data.monthly || []).find(
          (item) =>
            (item.expense_id || item.monthly_expense_id) === row.expense_id,
        );
        const id = row.share_id || row.id;
        const paid =
          state.me.role === "family"
            ? cents(row.amount_paid)
            : (data.payments || [])
                .filter((p) => p.share_id === id && p.status === "approved")
                .reduce((sum, p) => sum + cents(p.applied_amount), 0);
        return {
          id,
          concept: row.concept || expense?.concept || "Gasto de la casa",
          month: month(
            row.month_start ||
              bill?.month_start ||
              row.expense_date ||
              expense?.expense_date,
          ),
          due: row.due_date || expense?.due_date || "",
          assigned: cents(row.amount_due),
          paid,
          balance:
            state.me.role === "family"
              ? cents(row.balance)
              : Math.max(cents(row.amount_due) - paid, 0),
        };
      })
      .sort(
        (a, b) =>
          (a.month || "9999").localeCompare(b.month || "9999") ||
          a.due.localeCompare(b.due) ||
          String(a.id).localeCompare(String(b.id)),
      );
  }
  function account(state, familyId) {
    const rows = debts(state, familyId),
      grouped = new Map();
    for (const row of rows) {
      if (!grouped.has(row.month))
        grouped.set(row.month, {
          month: row.month,
          assigned: 0,
          paid: 0,
          balance: 0,
          debts: [],
        });
      const result = grouped.get(row.month);
      result.assigned += row.assigned;
      result.paid += row.paid;
      result.balance += row.balance;
      result.debts.push(row);
    }
    const months = [...grouped.values()];
    return {
      months,
      balance: rows.reduce((sum, row) => sum + row.balance, 0),
      latest: months
        .map((row) => row.month)
        .filter(Boolean)
        .sort()
        .at(-1),
      pending: months.filter((row) => row.balance > 0),
    };
  }
  function plan(state, familyId, amount, selected) {
    let remaining = cents(amount);
    const allocations = [];
    const rows = debts(state, familyId);
    for (const debt of rows.filter(
      (row) => row.balance > 0 && (!selected || row.id === selected),
    )) {
      const applied = Math.min(remaining, debt.balance);
      if (applied > 0) allocations.push({ ...debt, applied });
      remaining -= applied;
    }
    return {
      allocations,
      credit: remaining,
      remainingDebt:
        rows.reduce((sum, row) => sum + row.balance, 0) -
        allocations.reduce((sum, row) => sum + row.applied, 0),
    };
  }
  function entries(state) {
    const batches = state.D.batches || [];
    return [
      ...batches.map((row) => ({ ...row, batch: true })),
      ...(state.D.payments || []).filter((row) => !row.batch_id),
    ];
  }
  function periods(state) {
    return [
      ...new Set(
        (state.D.monthly || [])
          .map((row) => month(row.month_start))
          .filter(Boolean),
      ),
    ].sort();
  }
  function servicePlan(state, values) {
    const keys = ["water", "electricity", "gas", "wifi"],
      bills = keys.map((k) => cents(values[k]));
    const discounts = keys.map((k) =>
      values.reference
        ? 0
        : (state.D.utilityCredits || [])
            .filter((c) => c.service === k && !c.applied_month_start)
            .reduce((n, c) => n + cents(c.amount), 0) +
          cents(values["other_" + k]),
    );
    const invoices = bills.reduce((a, b) => a + b, 0),
      total = bills.reduce(
        (sum, bill, i) => sum + Math.max(0, bill - discounts[i]),
        0,
      ),
      count = state.D.families.length;
    let base = count ? Math.round(total / count) : 0;
    if (base * (count - 1) > total) base = Math.floor(total / count);
    return {
      invoices,
      discounts: discounts.reduce((a, b) => a + b, 0),
      total,
      shares: Array.from({ length: count }, (_, i) =>
        i === count - 1 ? total - base * (count - 1) : base,
      ),
      invalid: bills.some((bill, i) => discounts[i] > bill),
    };
  }
  return {
    today,
    servicePlan,
    cents,
    number,
    money,
    month,
    monthName,
    debts,
    account,
    plan,
    entries,
    periods,
  };
})();
