"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const {
  debeNotificarMorosidadPorFecha,
  configMorosidadActiva,
  configRecordatorioActivo,
} = require("../lib/plan_morosidad");

const DIA = 86400000;
const AHORA = 1700000000000;

function cliente({
  estado = "ACTIVO",
  fechaFinActual = AHORA - DIA,
  exentoMorosidad = false,
} = {}) {
  return { estado, fechaFinActual, exentoMorosidad };
}

test("fechaFinActual vencida + movimiento PAGADO -> notifica", () => {
  // La decisión NO depende del pago: se aporta un movimiento PAGADO como
  // contexto del caso de negocio; debeNotificarMorosidadPorFecha lo ignora.
  const movimientos = [{ estado: "PAGADO", fechaFin: AHORA - 10 * DIA }];
  assert.equal(
    debeNotificarMorosidadPorFecha({ ...cliente(), movimientos, ahora: AHORA }),
    true
  );
});

test("fechaFinActual vencida + movimiento PENDIENTE -> también notifica", () => {
  const movimientos = [{ estado: "PENDIENTE", fechaFin: AHORA - 10 * DIA }];
  assert.equal(
    debeNotificarMorosidadPorFecha({ ...cliente(), movimientos, ahora: AHORA }),
    true
  );
});

test("fechaFinActual vencida sin movimiento PAGADO -> decide por fechaFinActual, no por pago", () => {
  assert.equal(
    debeNotificarMorosidadPorFecha({ ...cliente(), movimientos: [], ahora: AHORA }),
    true
  );
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente(),
      movimientos: [{ estado: "PENDIENTE", fechaFin: AHORA - 5 * DIA }],
      ahora: AHORA,
    }),
    true
  );
});

test("fechaFinActual futura -> no notifica", () => {
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente({ fechaFinActual: AHORA + DIA }),
      ahora: AHORA,
    }),
    false
  );
});

test("borde temporal: fechaFinActual == ahora no notifica; ahora - 1 sí", () => {
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente({ fechaFinActual: AHORA }),
      ahora: AHORA,
    }),
    false
  );
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente({ fechaFinActual: AHORA - 1 }),
      ahora: AHORA,
    }),
    true
  );
});

test("fechaFinActual ausente/null -> no notifica", () => {
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente({ fechaFinActual: null }),
      ahora: AHORA,
    }),
    false
  );
});

test("exentoMorosidad -> no notifica", () => {
  assert.equal(
    debeNotificarMorosidadPorFecha({
      ...cliente({ exentoMorosidad: true }),
      ahora: AHORA,
    }),
    false
  );
});

test("cliente no ACTIVO -> no notifica", () => {
  for (const estado of ["BAJA", "REGISTRADO", "ARCHIVADO", "MOROSO"]) {
    assert.equal(
      debeNotificarMorosidadPorFecha({ ...cliente({ estado }), ahora: AHORA }),
      false,
      estado
    );
  }
});

test("configuracion: morosidad activada/desactivada", () => {
  assert.equal(
    configMorosidadActiva({ morosidad: { activa: true, recordatorioHoras: 24 } }),
    true
  );
  assert.equal(
    configMorosidadActiva({ morosidad: { activa: false, recordatorioHoras: 24 } }),
    false
  );
  assert.equal(configMorosidadActiva({}), false);
  assert.equal(configMorosidadActiva(null), false);
});

test("configuracion: recordatorio 0 desactivado y 24 activado", () => {
  assert.equal(
    configRecordatorioActivo({ morosidad: { activa: true, recordatorioHoras: 0 } }),
    false
  );
  assert.equal(
    configRecordatorioActivo({ morosidad: { activa: true, recordatorioHoras: 24 } }),
    true
  );
  assert.equal(
    configRecordatorioActivo({ morosidad: { activa: false, recordatorioHoras: 24 } }),
    false
  );
});
