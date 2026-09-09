"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const plan = require("../lib/plan_reservas");

const UID = "uid-cliente-1";
const NEGOCIO = "uid-admin-1";
const CLIENTE_ID = 42;
const SESION_ID = 7;
const ID_SERVICIO = 3;
const FECHA = 1700000000000; // medianoche local del día de la sesión
const AHORA = FECHA + 10 * 3600000; // 10:00 del mismo día

/** Contexto VÁLIDO de partida para reservar una sesión sin otras reservas. */
function contextoBase(extra) {
  return Object.assign(
    {
      uid: UID,
      negocioId: NEGOCIO,
      cliente: {
        negocioId: NEGOCIO,
        firebaseUid: UID,
        estado: "ACTIVO",
        serviciosContratados: [ID_SERVICIO],
      },
      sesion: {
        negocioId: NEGOCIO,
        idServicio: ID_SERVICIO,
        fecha: FECHA,
        horaDesdeReserva: null,
        plazasDisponibles: 10,
        capacidad: 10,
      },
      servicio: { negocioId: NEGOCIO, activo: true, permiteCombinarDia: true },
      reservaExiste: false,
      otrasPermiten: [],
      ahoraMs: AHORA,
    },
    extra
  );
}

test("reserva normal: todas las condiciones cumplidas permiten reservar", () => {
  const r = plan.resolverReserva(contextoBase());
  assert.deepEqual(r, { permitido: true });
});

test("reserva duplicada: si la reserva ya existe es un reintento idempotente (sin duplicar)", () => {
  const r = plan.resolverReserva(contextoBase({ reservaExiste: true }));
  assert.deepEqual(r, { permitido: true, yaReservada: true });
});

test("sin plazas: plazasDisponibles <= 0 bloquea la reserva", () => {
  const r = plan.resolverReserva(contextoBase({ sesion: { ...contextoBase().sesion, plazasDisponibles: 0 } }));
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "sinPlazas");
});

test("cliente no ACTIVO: BAJA bloquea con mensaje de baja", () => {
  const r = plan.resolverReserva(
    contextoBase({ cliente: { ...contextoBase().cliente, estado: "BAJA" } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "clienteDadoDeBaja");
});

test("cliente no ACTIVO: REGISTRADO bloquea con mensaje de cuenta no activa", () => {
  const r = plan.resolverReserva(
    contextoBase({ cliente: { ...contextoBase().cliente, estado: "REGISTRADO" } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "clienteNoActivo");
});

test("actividad no contratada: servicio fuera de serviciosContratados bloquea", () => {
  const r = plan.resolverReserva(
    contextoBase({ cliente: { ...contextoBase().cliente, serviciosContratados: [999] } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "actividadNoContratada");
});

test("servicio inactivo: bloquea la reserva", () => {
  const r = plan.resolverReserva(
    contextoBase({ servicio: { ...contextoBase().servicio, activo: false } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "servicioInactivo");
});

test("servicio de otro negocio: bloquea", () => {
  const r = plan.resolverReserva(
    contextoBase({ servicio: { ...contextoBase().servicio, negocioId: "otro" } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "servicioNoNegocio");
});

test("sesión de otro negocio: bloquea", () => {
  const r = plan.resolverReserva(
    contextoBase({ sesion: { ...contextoBase().sesion, negocioId: "otro" } })
  );
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "sesionNoNegocio");
});

test("horaDesdeReserva: antes de la apertura bloquea, en la apertura permite", () => {
  const sesion = { ...contextoBase().sesion, horaDesdeReserva: "09:30" };
  const antes = plan.resolverReserva(
    contextoBase({ sesion, ahoraMs: FECHA + 9 * 3600000 + 29 * 60000 })
  );
  assert.equal(antes.permitido, false);
  assert.equal(antes.motivo, "reservasAbrenA");

  const enApertura = plan.resolverReserva(
    contextoBase({ sesion, ahoraMs: FECHA + 9 * 3600000 + 30 * 60000 })
  );
  assert.deepEqual(enApertura, { permitido: true });
});

test("actividad no combinable: la NUEVA actividad con permiteCombinarDia=false y otra reserva el mismo día bloquea", () => {
  const servicio = { negocioId: NEGOCIO, activo: true, permiteCombinarDia: false };
  const r = plan.resolverReserva(contextoBase({ servicio, otrasPermiten: [true] }));
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "actividadNoCombinable");
});

test("actividad no combinable como UNICA del día: se permite", () => {
  const servicio = { negocioId: NEGOCIO, activo: true, permiteCombinarDia: false };
  const r = plan.resolverReserva(contextoBase({ servicio, otrasPermiten: [] }));
  assert.deepEqual(r, { permitido: true });
});

test("combinación permitida: nueva y existente permiten combinar", () => {
  const r = plan.resolverReserva(contextoBase({ otrasPermiten: [true] }));
  assert.deepEqual(r, { permitido: true });
});

test("combinación bloqueada: la OTRA reserva del día no permite combinar", () => {
  const r = plan.resolverReserva(contextoBase({ otrasPermiten: [false] }));
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "actividadNoCombinable");
});

test("permiteCombinarDia: default true cuando el campo falta o es null", () => {
  assert.equal(plan.permiteCombinar({ negocioId: NEGOCIO, activo: true }), true);
  assert.equal(plan.permiteCombinar({ negocioId: NEGOCIO, permiteCombinarDia: null }), true);
  assert.equal(plan.permiteCombinar({ negocioId: NEGOCIO, permiteCombinarDia: true }), true);
  assert.equal(plan.permiteCombinar({ negocioId: NEGOCIO, permiteCombinarDia: false }), false);
});

test("dos reservas simultáneas del mismo cliente/día: tras la primera en agenda, la segunda se evalúa contra ella", () => {
  // Primera reserva (CrossFit, no combinable) queda en la agenda del día.
  const primera = plan.resolverReserva(
    contextoBase({ servicio: { negocioId: NEGOCIO, activo: true, permiteCombinarDia: false }, otrasPermiten: [] })
  );
  assert.deepEqual(primera, { permitido: true });

  // Segunda sesión el mismo día, otra actividad combinable pero la YA reservada no combina.
  const segunda = plan.resolverReserva(contextoBase({ otrasPermiten: [false] }));
  assert.equal(segunda.permitido, false);
  assert.equal(segunda.motivo, "actividadNoCombinable");

  // Si ambas permiten combinar, la segunda se permite (el mapa final del día tiene dos sesiones).
  const ambasCombinan = plan.resolverReserva(contextoBase({ otrasPermiten: [true] }));
  assert.deepEqual(ambasCombinan, { permitido: true });
});

test("cancelación: con reserva y sesión existentes se permite cancelar", () => {
  const r = plan.resolverCancelacion({
    clienteId: CLIENTE_ID,
    negocioId: NEGOCIO,
    reserva: { clienteId: CLIENTE_ID, negocioId: NEGOCIO },
    sesion: { negocioId: NEGOCIO },
  });
  assert.deepEqual(r, { permitido: true });
});

test("cancelación idempotente: si la reserva ya no existe se permite como yaCancelada", () => {
  const r = plan.resolverCancelacion({
    clienteId: CLIENTE_ID,
    negocioId: NEGOCIO,
    reserva: null,
    sesion: null,
  });
  assert.deepEqual(r, { permitido: true, yaCancelada: true });
});

test("cancelación: reserva de otro cliente bloquea", () => {
  const r = plan.resolverCancelacion({
    clienteId: CLIENTE_ID,
    negocioId: NEGOCIO,
    reserva: { clienteId: 999, negocioId: NEGOCIO },
    sesion: { negocioId: NEGOCIO },
  });
  assert.equal(r.permitido, false);
  assert.equal(r.motivo, "reservaNoPertenece");
});

test("reconstrucción de agenda existente: reconstruye el día desde las reservas reales", () => {
  const agendaVieja = { sesiones: { 7: 3, 8: 4 } }; // sesion 7 ya no está realmente reservada
  const reales = [
    { sesionId: 8, idServicio: 4 },
    { sesionId: 9, idServicio: 3 },
  ];
  const r = plan.reconstruirAgenda(agendaVieja, reales);
  assert.deepEqual(r.sesiones, { 8: 4, 9: 3 });
  assert.equal(r.cambio, true);
});

test("reconstrucción de agenda existente: sin cambios cuando ya coincide", () => {
  const agenda = { sesiones: { 8: 4, 9: 3 } };
  const reales = [
    { sesionId: 8, idServicio: 4 },
    { sesionId: 9, idServicio: 3 },
  ];
  const r = plan.reconstruirAgenda(agenda, reales);
  assert.equal(r.cambio, false);
  assert.deepEqual(r.sesiones, agenda.sesiones);
});

test("reconstrucción de agenda existente: agenda null/ausente se construye desde cero", () => {
  const r = plan.reconstruirAgenda(null, [{ sesionId: 8, idServicio: 4 }]);
  assert.deepEqual(r.sesiones, { 8: 4 });
  assert.equal(r.cambio, true);
});

test("agenda: agregar y quitar sesiones del mapa del día", () => {
  let mapa = plan.agendaAgregarSesion(null, SESION_ID, ID_SERVICIO);
  assert.deepEqual(mapa, { [String(SESION_ID)]: ID_SERVICIO });

  mapa = plan.agendaAgregarSesion({ sesiones: { 8: 4 } }, SESION_ID, ID_SERVICIO);
  assert.deepEqual(mapa, { 8: 4, [String(SESION_ID)]: ID_SERVICIO });

  mapa = plan.agendaQuitarSesion({ sesiones: mapa }, SESION_ID);
  assert.deepEqual(mapa, { 8: 4 });
});

test("helpers: reservaId, rutaAgendaDelDia y esMismoDia", () => {
  assert.equal(plan.reservaId(42, 7), "42_7");
  assert.equal(plan.rutaAgendaDelDia(42, FECHA), `clientes/42/agenda/${FECHA}`);
  assert.equal(plan.esMismoDia(FECHA, FECHA), true);
  assert.equal(plan.esMismoDia(FECHA, FECHA + 86400000), false);
});
