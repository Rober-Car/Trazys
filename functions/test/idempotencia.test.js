"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const {
  decidirCreacionNotificacion,
  decidirEntregaAutomatica,
} = require("../lib/idempotencia");

test("sin documento -> crear", () => {
  assert.equal(decidirCreacionNotificacion(null), "crear");
  assert.equal(decidirCreacionNotificacion(undefined), "crear");
});

test("documento PENDIENTE -> continuar (reanuda el envío)", () => {
  assert.equal(decidirCreacionNotificacion({ estado: "PENDIENTE" }), "continuar");
});

test("documento ENVIADA -> omitir (no reenvía)", () => {
  assert.equal(decidirCreacionNotificacion({ estado: "ENVIADA" }), "omitir");
});

test("documento en otro estado -> omitir", () => {
  assert.equal(decidirCreacionNotificacion({ estado: "CANCELADA" }), "omitir");
  assert.equal(decidirCreacionNotificacion({ estado: "ERROR" }), "omitir");
});

test("ejecución repetida: ENVIADA nunca se resetea a PENDIENTE", () => {
  // La segunda ejecución de un barrido sobre un documento ya enviado devuelve
  // "omitir", de modo que crearYEnviarAutomatica no hace set() ni reenvía.
  assert.equal(decidirCreacionNotificacion({ estado: "ENVIADA" }), "omitir");
});

// =========================================================
// ENTREGA AUTOMÁTICA (destinatario/dispositivo disponible)
// =========================================================

test("ENVIADA, sin pendientes y con dispositivos -> omitir (no duplica)", () => {
  assert.equal(
    decidirEntregaAutomatica({ estado: "ENVIADA", pendientes: 0, huboDispositivos: true }),
    "omitir"
  );
});

test("ENVIADA pero sin dispositivos en el intento previo -> enviar (reintenta)", () => {
  assert.equal(
    decidirEntregaAutomatica({ estado: "ENVIADA", pendientes: 0, huboDispositivos: false }),
    "enviar"
  );
});

test("ENVIADA con destinatarios pendientes (buzón que faltaba) -> enviar", () => {
  assert.equal(
    decidirEntregaAutomatica({ estado: "ENVIADA", pendientes: 1, huboDispositivos: true }),
    "enviar"
  );
});

test("PENDIENTE -> enviar", () => {
  assert.equal(
    decidirEntregaAutomatica({ estado: "PENDIENTE", pendientes: 0, huboDispositivos: false }),
    "enviar"
  );
});
