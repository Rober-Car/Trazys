"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const {
  esProgramadaProcesable,
  fechaProgramadaMs,
} = require("../lib/plan_programadas");
const { esNotificacionInmediataProcesable } = require("../lib/plan_inmediata");

const AHORA = Date.parse("2026-09-12T12:00:00Z");

test("A. PROGRAMADA con fechaProgramada <= ahora -> procesable", () => {
  assert.equal(
    esProgramadaProcesable({ estado: "PROGRAMADA", fechaProgramada: AHORA - 1000 }, AHORA),
    true
  );
  // Justo en el instante: se considera vencida.
  assert.equal(
    esProgramadaProcesable({ estado: "PROGRAMADA", fechaProgramada: AHORA }, AHORA),
    true
  );
});

test("B. PROGRAMADA con fechaProgramada > ahora -> NO procesable", () => {
  assert.equal(
    esProgramadaProcesable({ estado: "PROGRAMADA", fechaProgramada: AHORA + 1000 }, AHORA),
    false
  );
});

test("C. estado distinto de PROGRAMADA -> NO procesable", () => {
  for (const estado of ["PENDIENTE", "ENVIADA", "CANCELADA", "ERROR"]) {
    assert.equal(
      esProgramadaProcesable({ estado, fechaProgramada: AHORA - 1000 }, AHORA),
      false,
      `estado ${estado} no debe procesarse`
    );
  }
});

test("D. una PROGRAMADA no la procesa notificacionInmediata", () => {
  assert.equal(
    esNotificacionInmediataProcesable({
      estado: "PROGRAMADA",
      programada: true,
      origen: "MANUAL",
    }),
    false
  );
  // Una MANUAL inmediata sí la procesa (control).
  assert.equal(
    esNotificacionInmediataProcesable({
      estado: "PENDIENTE",
      programada: false,
      origen: "MANUAL",
    }),
    true
  );
});

test("E. idempotencia (parte pura): una ya ENVIADA no se procesa de nuevo", () => {
  assert.equal(
    esProgramadaProcesable({ estado: "ENVIADA", fechaProgramada: AHORA - 1000 }, AHORA),
    false
  );
});

test("fechaProgramada ausente o invalida -> NO procesable", () => {
  assert.equal(esProgramadaProcesable({ estado: "PROGRAMADA" }, AHORA), false);
  assert.equal(esProgramadaProcesable({ estado: "PROGRAMADA", fechaProgramada: null }, AHORA), false);
  assert.equal(esProgramadaProcesable({ estado: "PROGRAMADA", fechaProgramada: "no-fecha" }, AHORA), false);
  assert.equal(esProgramadaProcesable(null, AHORA), false);
});

test("fechaProgramadaMs acepta number/string/Date/Timestamp-like", () => {
  assert.equal(fechaProgramadaMs(1000), 1000);
  assert.equal(fechaProgramadaMs("2026-09-12T12:00:00Z"), AHORA);
  assert.equal(fechaProgramadaMs({ seconds: 10 }), 10000);
  assert.equal(fechaProgramadaMs({ toMillis: () => 5000 }), 5000);
  assert.equal(fechaProgramadaMs({ toDate: () => new Date(7000) }), 7000);
  assert.equal(fechaProgramadaMs(undefined), null);
  assert.equal(fechaProgramadaMs(NaN), null);
});
