"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const plan = require("../lib/plan_eliminacion");

const UID_CLIENTE = "uid-cliente-1";
const UID_ADMIN = "uid-admin-1";

test("objetivoDesdeUsuario: CLIENTE vinculado devuelve clienteId y negocioId", () => {
  const o = plan.objetivoDesdeUsuario({
    rol: "CLIENTE",
    clienteId: 42,
    negocioId: UID_ADMIN,
  });
  assert.deepEqual(o, { tipo: "CLIENTE", clienteId: 42, negocioId: UID_ADMIN });
});

test("objetivoDesdeUsuario: CLIENTE pendiente devuelve clienteId null", () => {
  const o = plan.objetivoDesdeUsuario({
    rol: "CLIENTE",
    clienteId: null,
    negocioId: null,
  });
  assert.deepEqual(o, { tipo: "CLIENTE", clienteId: null, negocioId: null });
});

test("objetivoDesdeUsuario: ADMIN devuelve su negocioId", () => {
  const o = plan.objetivoDesdeUsuario({ rol: "ADMIN", negocioId: UID_ADMIN });
  assert.deepEqual(o, { tipo: "ADMIN", negocioId: UID_ADMIN });
});

test("objetivoDesdeUsuario: sin documento devuelve null (no autenticado se rechaza antes)", () => {
  assert.equal(plan.objetivoDesdeUsuario(null), null);
});

test("fichaPerteneceAlUsuario: solo el dueño puede borrar su ficha", () => {
  assert.equal(plan.fichaPerteneceAlUsuario({ firebaseUid: UID_CLIENTE }, UID_CLIENTE), true);
  assert.equal(
    plan.fichaPerteneceAlUsuario({ firebaseUid: "otro-uid" }, UID_CLIENTE),
    false
  );
  assert.equal(plan.fichaPerteneceAlUsuario(null, UID_CLIENTE), false);
});

test("rutasFijasCliente: incluye usuarios, perfil pendiente y ficha/privados si hay clienteId", () => {
  assert.deepEqual(
    plan.rutasFijasCliente(UID_CLIENTE, null),
    [`usuarios/${UID_CLIENTE}`, `perfiles_pendientes/${UID_CLIENTE}`]
  );
  assert.deepEqual(
    plan.rutasFijasCliente(UID_CLIENTE, 42),
    [
      `usuarios/${UID_CLIENTE}`,
      `perfiles_pendientes/${UID_CLIENTE}`,
      "clientes/42",
      "clientes_privados/42",
    ]
  );
});

test("rutaIndice concatena negocio+dni", () => {
  assert.equal(
    plan.rutaIndice(UID_ADMIN, "12345678Z"),
    `indices_clientes/${UID_ADMIN}_12345678Z`
  );
});

test("rutasFijasAdmin: incluye negocio, publico, config, codigo y usuario (codigo opcional)", () => {
  assert.deepEqual(plan.rutasFijasAdmin(UID_ADMIN, "654321"), [
    `negocios/${UID_ADMIN}`,
    `negocios_publicos/${UID_ADMIN}`,
    `configuracion_notificaciones/${UID_ADMIN}`,
    `usuarios/${UID_ADMIN}`,
    "codigos_maestros/654321",
  ]);
  assert.deepEqual(plan.rutasFijasAdmin(UID_ADMIN, null), [
    `negocios/${UID_ADMIN}`,
    `negocios_publicos/${UID_ADMIN}`,
    `configuracion_notificaciones/${UID_ADMIN}`,
    `usuarios/${UID_ADMIN}`,
  ]);
});

test("coleccionesConNegocioId y Storage del cliente/logo", () => {
  const cols = plan.coleccionesConNegocioId();
  for (const c of [
    "servicios",
    "sesiones",
    "reservas",
    "movimientos",
    "solicitudes",
    "notificaciones",
    "notificaciones_por_destinatario",
    "clientes_privados",
  ]) {
    assert.ok(cols.includes(c), `falta ${c}`);
  }
  assert.deepEqual(plan.rutasStorageCliente(42), ["clientes/42/foto.jpg"]);
  assert.equal(plan.rutaStorageLogo(UID_ADMIN), `negocios/${UID_ADMIN}/logo.jpg`);
  assert.equal(plan.rutaDispositivos("clientes", 42), "clientes/42/dispositivos");
});
