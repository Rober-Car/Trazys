"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const {
  BUCKET_MS,
  TITULO_ES,
  TITULO_EN,
  aperturaDeSesion,
  bucketStartOf,
  cubosCerrados,
  agruparAperturasPorNegocioYCubo,
  puedeRecibirApertura,
  serviciosDelClienteEnElBucket,
  nombresOrdenados,
  formatearLista,
  construirMensajeApertura,
} = require("../lib/plan_aperturas");

const { idNotificacionApertura } = require("../lib/ids");

// Base alineada a 5 minutos (múltiplo de BUCKET_MS) para que las horas "en
// punto" caigan exactamente en límites de bucket.
const BASE = 1789000200000;
const MIN = 60000;
const HORA = 3600000;
const aLas = (h, m = 0) => BASE + h * HORA + m * MIN;

function sesion(idServicio, horaDesdeReserva, fecha = BASE, negocioId = "neg1") {
  return { idServicio, negocioId, fecha, horaDesdeReserva };
}

// 1. Una apertura -> instante correcto (fecha + horaDesdeReserva).
test("aperturaDeSesion: una apertura calcula fecha + horaDesdeReserva", () => {
  assert.equal(aperturaDeSesion(sesion(1, "10:00")), aLas(10, 0));
  assert.equal(aperturaDeSesion(sesion(1, "10:02")), aLas(10, 2));
});

// 11. horaDesdeReserva null/ausente -> no es evento de apertura.
test("aperturaDeSesion: horaDesdeReserva null o inválida no genera apertura", () => {
  assert.equal(aperturaDeSesion(sesion(1, null)), null);
  assert.equal(aperturaDeSesion(sesion(1, undefined)), null);
  assert.equal(aperturaDeSesion(sesion(1, "sin-hora")), null);
});

// 2. Varias aperturas a la misma hora -> mismo bucket y una sola oleada.
test("agrupación: varias aperturas a la misma hora -> una oleada", () => {
  const sesiones = [sesion(1, "10:00"), sesion(2, "10:00"), sesion(3, "10:00")];
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, [bucketStartOf(aLas(10, 0))]);
  assert.deepEqual(Object.keys(grupos), ["neg1"]);
  assert.deepEqual(grupos.neg1[bucketStartOf(aLas(10, 0))].sort(), [1, 2, 3]);
});

// 3. Aperturas a 1-4 minutos -> mismo bucket.
test("agrupación: aperturas a 1-4 minutos caen en el mismo bucket", () => {
  const bucket = bucketStartOf(aLas(10, 0));
  assert.equal(bucketStartOf(aLas(10, 1)), bucket);
  assert.equal(bucketStartOf(aLas(10, 4)), bucket);
  const sesiones = [sesion(1, "10:00"), sesion(2, "10:02"), sesion(3, "10:04")];
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, [bucket]);
  assert.deepEqual(grupos.neg1[bucket].sort(), [1, 2, 3]);
});

// 4. Aperturas en buckets diferentes (10:00 y 10:10) -> dos oleadas.
test("agrupación: aperturas en buckets diferentes -> dos oleadas", () => {
  const sesiones = [sesion(1, "10:00"), sesion(2, "10:10")];
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, [
    bucketStartOf(aLas(10, 0)),
    bucketStartOf(aLas(10, 10)),
  ]);
  assert.deepEqual(Object.keys(grupos.neg1).sort(), [
    String(bucketStartOf(aLas(10, 0))),
    String(bucketStartOf(aLas(10, 10))),
  ]);
});

// 5. 10:04 y 10:06 caen en buckets distintos (10:00 y 10:05).
test("agrupación: 10:04 y 10:06 caen en buckets diferentes", () => {
  assert.notEqual(bucketStartOf(aLas(10, 4)), bucketStartOf(aLas(10, 6)));
  const sesiones = [sesion(1, "10:04"), sesion(2, "10:06")];
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, [
    bucketStartOf(aLas(10, 4)),
    bucketStartOf(aLas(10, 6)),
  ]);
  assert.equal(grupos.neg1[bucketStartOf(aLas(10, 4))].length, 1);
  assert.equal(grupos.neg1[bucketStartOf(aLas(10, 6))].length, 1);
});

// El bucket en curso NO se procesa (solo los cerrados) y el lookback es acotado.
test("cubosCerrados: no incluye el bucket en curso", () => {
  const ahora = aLas(10, 6); // bucket en curso = 10:05
  const cubos = cubosCerrados(ahora);
  assert.equal(cubos[0], bucketStartOf(aLas(10, 0))); // último cerrado
  assert.ok(!cubos.includes(bucketStartOf(aLas(10, 5)))); // en curso
  assert.equal(cubos.length, 3);
});

// Una sesión cuya apertura cae en un bucket no cerrado queda fuera.
test("agrupación: solo procesa buckets cerrados recibidos", () => {
  const sesiones = [sesion(1, "10:06")]; // bucket 10:05 (en curso)
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, cubosCerrados(aLas(10, 6)));
  assert.deepEqual(grupos, {});
});

// Las sesiones sin horaDesdeReserva nunca se agrupan.
test("agrupación: sesiones sin horaDesdeReserva se ignoran", () => {
  const sesiones = [sesion(1, null), sesion(2, "10:00")];
  const grupos = agruparAperturasPorNegocioYCubo(sesiones, [bucketStartOf(aLas(10, 0))]);
  assert.deepEqual(grupos.neg1[bucketStartOf(aLas(10, 0))], [2]);
});

// 8. Cliente con servicios que no coinciden -> sin destinatario.
test("serviciosDelClienteEnElBucket: intersección vacía si no coincide", () => {
  assert.deepEqual(serviciosDelClienteEnElBucket([1, 2], [3, 4]), []);
  assert.deepEqual(serviciosDelClienteEnElBucket([1, 2], [2, 3]), [2]);
  assert.deepEqual(serviciosDelClienteEnElBucket(null, [1]), []);
});

// 9 y 10. Filtro de estado/firebaseUid.
test("puedeRecibirApertura: solo ACTIVO y vinculado", () => {
  assert.equal(puedeRecibirApertura({ estado: "ACTIVO", firebaseUid: "uid" }), true);
  assert.equal(puedeRecibirApertura({ estado: "BAJA", firebaseUid: "uid" }), false);
  assert.equal(puedeRecibirApertura({ estado: "ARCHIVADO", firebaseUid: "uid" }), false);
  assert.equal(puedeRecibirApertura({ estado: "REGISTRADO", firebaseUid: "uid" }), false);
  assert.equal(puedeRecibirApertura({ estado: "ACTIVO", firebaseUid: "" }), false);
  assert.equal(puedeRecibirApertura({ estado: "ACTIVO", firebaseUid: null }), false);
  assert.equal(puedeRecibirApertura(null), false);
});

// 6. Una sola actividad.
test("mensaje: una sola actividad (ES)", () => {
  assert.equal(
    construirMensajeApertura(["CrossFit"], "es"),
    "CrossFit ya está disponible. ¡No te quedes sin la tuya!"
  );
});

// 7. Varias actividades.
test("mensaje: varias actividades (ES), con coma y 'y'", () => {
  assert.equal(
    construirMensajeApertura(["CrossFit", "Pilates", "Yoga"], "es"),
    "CrossFit, Pilates y Yoga ya están disponibles. ¡No te quedes sin la tuya!"
  );
  assert.equal(
    construirMensajeApertura(["CrossFit", "Pilates"], "es"),
    "CrossFit y Pilates ya están disponibles. ¡No te quedes sin la tuya!"
  );
});

// 15. Título y textos ES.
test("titulo: ES", () => {
  assert.equal(TITULO_ES, "¡Ya están abiertas las reservas!");
});

// 16. Título y textos EN (singular/plural).
test("mensaje y titulo: EN", () => {
  assert.equal(TITULO_EN, "Reservations are now open!");
  assert.equal(
    construirMensajeApertura(["CrossFit"], "en"),
    "CrossFit is now available. Don't miss your spot!"
  );
  assert.equal(
    construirMensajeApertura(["CrossFit", "Pilates", "Yoga"], "en"),
    "CrossFit, Pilates and Yoga are now available. Don't miss your spot!"
  );
});

test("formatearLista y nombresOrdenados: orden alfabético y conjunción", () => {
  assert.equal(formatearLista(["A"], "y"), "A");
  assert.equal(formatearLista(["A", "B"], "y"), "A y B");
  assert.equal(formatearLista(["A", "B", "C"], "and"), "A, B and C");
  const nombres = nombresOrdenados([3, 1, 2], {
    1: "CrossFit",
    2: "Pilates",
    3: "Yoga",
  });
  assert.deepEqual(nombres, ["CrossFit", "Pilates", "Yoga"]);
});

// 12. ID determinista.
test("idNotificacionApertura: formato determinista cliente + bucket", () => {
  assert.equal(
    idNotificacionApertura(123, 1789000000000),
    "apertura_123_1789000000000"
  );
});

// 13. Mismo cliente + mismo bucket -> mismo ID.
test("idNotificacionApertura: mismo cliente y bucket -> mismo id", () => {
  const bucket = bucketStartOf(aLas(10, 0));
  assert.equal(
    idNotificacionApertura(7, bucket),
    idNotificacionApertura(7, bucket)
  );
});

// 14. Clientes diferentes + mismo bucket -> IDs diferentes.
test("idNotificacionApertura: clientes distintos -> ids distintos", () => {
  const bucket = bucketStartOf(aLas(10, 0));
  assert.notEqual(
    idNotificacionApertura(7, bucket),
    idNotificacionApertura(8, bucket)
  );
});

// El bucket mide 5 minutos exactos.
test("BUCKET_MS es 5 minutos", () => {
  assert.equal(BUCKET_MS, 5 * 60 * 1000);
});

// Reproducción del escenario REAL de producción (España, UTC+2):
// sesión de hoy con horaDesdeReserva 12:58; barrido a las 13:03. La apertura
// debe caer en un bucket CERRADO y, por tanto, procesarse.
test("escenario real: apertura 12:58 procesada a las 13:03 (bucket cerrado)", () => {
  const fecha = Date.parse("2026-09-15T22:00:00.000Z"); // medianoche local 16/09
  const ahora = Date.parse("2026-09-16T11:03:00.000Z"); // 13:03 local (UTC+2)
  const s = { idServicio: 11, negocioId: "neg1", fecha, horaDesdeReserva: "12:58" };

  const apertura = aperturaDeSesion(s);
  assert.equal(apertura, Date.parse("2026-09-16T10:58:00.000Z")); // 12:58 local

  const bucket = bucketStartOf(apertura);
  assert.ok(
    cubosCerrados(ahora).includes(bucket),
    "el bucket de la apertura debe estar cerrado a las 13:03"
  );

  const grupos = agruparAperturasPorNegocioYCubo(
    [s, { idServicio: 10, negocioId: "neg1", fecha, horaDesdeReserva: "12:55" }],
    cubosCerrados(ahora)
  );
  // Ambas caen en el mismo bucket -> una sola oleada con los dos servicios.
  const buckets = Object.keys(grupos.neg1);
  assert.equal(buckets.length, 1);
  assert.deepEqual(grupos.neg1[buckets[0]].sort(), [10, 11]);
});

// D/E) Dos actividades juntas: cliente con ambas -> un único ID con ambas;
// cliente con una -> un único ID con la suya.
test("mismo bucket: un cliente recibe una sola notificación con sus actividades", () => {
  const bucket = bucketStartOf(aLas(10, 0));
  const abiertos = [1, 2];

  // Cliente con ambas contratadas.
  const servA = serviciosDelClienteEnElBucket([1, 2], abiertos);
  const idA = idNotificacionApertura(100, bucket);
  assert.deepEqual(servA, [1, 2]);
  assert.equal(idA, "apertura_100_" + bucket);
  assert.equal(
    construirMensajeApertura(nombresOrdenados(servA, { 1: "CrossFit", 2: "Pilates" }), "es"),
    "CrossFit y Pilates ya están disponibles. ¡No te quedes sin la tuya!"
  );

  // Cliente con solo una contratada.
  const servB = serviciosDelClienteEnElBucket([1], abiertos);
  const idB = idNotificacionApertura(200, bucket);
  assert.deepEqual(servB, [1]);
  assert.equal(
    construirMensajeApertura(nombresOrdenados(servB, { 1: "CrossFit", 2: "Pilates" }), "es"),
    "CrossFit ya está disponible. ¡No te quedes sin la tuya!"
  );
});
