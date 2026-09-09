"use strict";

/**
 * reservas.js
 * -----------
 * Backend de reservas del CLIENTE como Cloud Functions callable (FASE 1).
 * La identidad (clienteId, negocioId) se deriva EXCLUSIVAMENTE de
 * usuarios/{uid}; las callables nunca aceptan datos del cliente/negocio de la
 * app (mismo criterio de seguridad que eliminacion.js).
 *
 * Callables:
 *   - reservar({ sesionId })
 *   - cancelarReserva({ sesionId })
 *
 * Operaciones ATÓMICAS (runTransaction) e IDEMPOTENTES:
 *   - reservar cuando la reserva ya existe -> éxito sin efectos (yaReservada).
 *   - cancelarReserva cuando la reserva ya no existe -> éxito (yaCancelada).
 *
 * Escrituras (FASE 1):
 *   - reservas/{clienteId}_{sesionId}            (estructura actual intacta).
 *   - sesiones/{idSesion}.plazasDisponibles ± 1  (sin superar capacidad).
 *   - sesiones/{idSesion}.asistentes[clienteId]  = nombre del cliente.
 *   - clientes/{clienteId}/agenda/{fecha}        = día derivado del cliente,
 *         { negocioId, fecha, sesiones: { [sesionId]: idServicio } }.
 *   - servicios/{idServicio}.permiteCombinarDia  (default true si falta; se lee,
 *         no se escribe aquí: lo gestiona el ADMIN en su pantalla de servicio).
 *
 * La AGENDA es un dato DERIVADO: nunca se decide a partir de ella sin validar
 * contra las reservas reales del día (reservas/{clienteId}_{sesionId}). Si la
 * agenda está ausente/incompleta se RECONSTRUYE bajo demanda consultando las
 * reservas reales del cliente para ese día.
 *
 * NO modifica Rules ni el flujo actual del CLIENTE (FASE 1).
 */

const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const plan = require("./plan_reservas");

const COLECCION_USUARIOS = "usuarios";
const COLECCION_CLIENTES = "clientes";
const COLECCION_SESIONES = "sesiones";
const COLECCION_SERVICIOS = "servicios";
const COLECCION_RESERVAS = "reservas";

/** Límite defensivo de reservas del cliente a reconciliar por día. */
const MAX_RESERVAS_RECONCILIACION = 100;

function db() {
  return getFirestore();
}

function refCliente(clienteId) {
  return db().collection(COLECCION_CLIENTES).doc(String(clienteId));
}

function refSesion(sesionId) {
  return db().collection(COLECCION_SESIONES).doc(String(sesionId));
}

function refServicio(idServicio) {
  return db().collection(COLECCION_SERVICIOS).doc(String(idServicio));
}

function refReserva(clienteId, sesionId) {
  return db()
    .collection(COLECCION_RESERVAS)
    .doc(plan.reservaId(clienteId, sesionId));
}

function refAgenda(clienteId, fecha) {
  return db().doc(plan.rutaAgendaDelDia(clienteId, fecha));
}

function docUsuario(uid) {
  return db().collection(COLECCION_USUARIOS).doc(uid);
}

/**
 * leerUsuarioVinculado
 * --------------------
 * Devuelve { clienteId, negocioId } del CLIENTE autenticado, o lanza HttpsError
 * si no es un CLIENTE activo y vinculado.
 */
async function leerUsuarioVinculado(uid) {
  const snap = await docUsuario(uid).get();
  if (!snap.exists) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión para reservar");
  }
  const datos = snap.data();
  if (datos.rol !== "CLIENTE" || datos.activo !== true) {
    throw new HttpsError("permission-denied", "Tu cuenta no permite reservar");
  }
  const clienteId = datos.clienteId;
  const negocioId = datos.negocioId;
  if (!Number.isInteger(clienteId) || typeof negocioId !== "string") {
    throw new HttpsError(
      "failed-precondition",
      "Todavía no estás vinculado a ningún gimnasio"
    );
  }
  return { clienteId, negocioId };
}

/**
 * errorDeDecision
 * ---------------
 * Convierte una decisión rechazada del plan en un HttpsError con mensaje en
 * español (la app mapeará los motivos a recursos i18n en fases posteriores).
 */
function errorDeDecision(decision) {
  const codigo =
    decision.motivo === "reservaNoPertenece" ||
    decision.motivo === "clienteCuenta"
      ? "permission-denied"
      : "failed-precondition";
  return new HttpsError(codigo, decision.mensaje || "No se pudo realizar la operación");
}

/**
 * reservasRealesDelDia
 * --------------------
 * Reconstrucción BAJO DEMANDA del "día real": consulta las reservas del cliente
 * en Firestore y devuelve las que pertenecen a una sesión con la fecha indicada
 * (fecha normalizada a la medianoche local). Devuelve [{ sesionId, idServicio }].
 * Solo se usa cuando la agenda está ausente/incompleta (nunca como backfill).
 */
async function reservasRealesDelDia(clienteId, negocioId, fecha) {
  const snap = await db()
    .collection(COLECCION_RESERVAS)
    .where("clienteId", "==", clienteId)
    .where("negocioId", "==", negocioId)
    .limit(MAX_RESERVAS_RECONCILIACION)
    .get();

  const reales = [];
  await Promise.all(
    snap.docs.map(async (reservaDoc) => {
      const sesionId = reservaDoc.get("sesionId");
      if (!Number.isInteger(sesionId)) return;
      const sesionSnap = await refSesion(sesionId).get();
      if (!sesionSnap.exists) return;
      const datosSesion = sesionSnap.data();
      if (datosSesion.negocioId === negocioId && datosSesion.fecha === fecha) {
        reales.push({ sesionId, idServicio: datosSesion.idServicio });
      }
    })
  );
  return reales;
}

/**
 * sesionesCandidatasDelDia
 * ------------------------
 * Une las dos fuentes de candidatas del "día real" del cliente (excluye la
 * sesión objetivo):
 *   1. La AGENDA existente (clientes/{clienteId}/agenda/{fecha}): índice que
 *      toda reserva vía estas callables mantiene de forma transaccional. El
 *      hecho de escribir SIEMPRE ese documento convierte el día en un punto de
 *      contención: dos reservas simultáneas del mismo cliente/día se serializan
 *      (una reintenta y relee la agenda con la primera ya dentro).
 *   2. La reconciliación bajo demanda (reservas REALES del cliente del día):
 *      cubre reservas previas que aún no tienen agenda (sin backfill masivo).
 * Cada candidata se valida después dentro de la transacción contra su reserva
 * real; la agenda nunca decide por sí sola.
 */
function sesionesCandidatasDelDia(agendaSnap, reales, sesionObjetivoId) {
  const porId = new Map();
  const agenda = agendaSnap.exists && agendaSnap.data() ? agendaSnap.data().sesiones : null;
  if (agenda && typeof agenda === "object") {
    for (const sesionIdStr of Object.keys(agenda)) {
      const sesionId = Number(sesionIdStr);
      if (Number.isInteger(sesionId) && sesionId !== sesionObjetivoId) {
        porId.set(sesionId, Number(agenda[sesionIdStr]));
      }
    }
  }
  for (const real of reales || []) {
    if (real.sesionId !== sesionObjetivoId) {
      porId.set(real.sesionId, real.idServicio);
    }
  }
  return [...porId.entries()].map(([sesionId, idServicio]) => ({
    sesionId,
    idServicio,
  }));
}

/**
 * datosDeOtrasSesionesDelDia
 * --------------------------
 * Dentro de la transacción valida las reservas REALES candidatas del día y
 * resuelve los datos necesarios para la regla de combinación. Excluye la sesión
 * objetivo. Devuelve:
 *   - vivas:  [{ sesionId, idServicio }] reservas que siguen existiendo en la tx.
 *   - otrasPermiten: [boolean] permiteCombinarDia de cada reserva viva distinta.
 */
async function datosDeOtrasSesionesDelDia(
  tx,
  clienteId,
  negocioId,
  fecha,
  candidatas
) {
  const vivas = [];
  const serviciosIds = new Set();

  for (const candidata of candidatas) {
    const reservaSnap = await tx.get(refReserva(clienteId, candidata.sesionId));
    if (!reservaSnap.exists) continue;
    const sesionSnap = await tx.get(refSesion(candidata.sesionId));
    if (!sesionSnap.exists) continue;
    const datosSesion = sesionSnap.data();
    if (datosSesion.negocioId !== negocioId || datosSesion.fecha !== fecha) continue;
    const idServicio = Number.isInteger(datosSesion.idServicio)
      ? datosSesion.idServicio
      : candidata.idServicio;
    if (!Number.isInteger(idServicio)) continue;
    vivas.push({ sesionId: candidata.sesionId, idServicio });
    serviciosIds.add(idServicio);
  }

  // Resolver permiteCombinarDia de los servicios implicados (una lectura por servicio).
  const permitePorServicio = {};
  for (const idServicio of serviciosIds) {
    const servicioSnap = await tx.get(refServicio(idServicio));
    permitePorServicio[idServicio] = plan.permiteCombinar(
      servicioSnap.exists ? servicioSnap.data() : null
    );
  }

  const otrasPermiten = vivas.map((v) => permitePorServicio[v.idServicio]);
  return { vivas, otrasPermiten };
}

/**
 * reservar
 * --------
 * Callable reservar({ sesionId }). Crea la reserva de forma ATÓMICA junto con:
 * la plaza de la sesión, el asistente y la agenda del día. Idempotente.
 */
async function reservar(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión para reservar");
  }

  const sesionId = request.data && request.data.sesionId;
  if (!Number.isInteger(sesionId) || sesionId <= 0) {
    throw new HttpsError("invalid-argument", "La sesión indicada no es válida");
  }

  const { clienteId, negocioId } = await leerUsuarioVinculado(uid);

  const clienteRef = refCliente(clienteId);
  const sesionRef = refSesion(sesionId);
  const reservaRef = refReserva(clienteId, sesionId);

  // Lectura previa (fuera de la tx) para conocer la fecha del día y reconciliar
  // la agenda si hace falta. Dentro de la tx se vuelve a leer todo.
  const sesionPrevia = await sesionRef.get();
  if (!sesionPrevia.exists) {
    throw new HttpsError("failed-precondition", "La sesión no existe");
  }
  const fecha = sesionPrevia.get("fecha");
  if (!Number.isInteger(fecha)) {
    throw new HttpsError("failed-precondition", "La sesión no tiene fecha");
  }

  // Día real bajo demanda (fuente de verdad: reservas). La agenda se reconstruye
  // con estas reservas al escribir; nunca se decide solo con la agenda.
  const reales = await reservasRealesDelDia(clienteId, negocioId, fecha);

  const ahoraMs = Date.now();

  try {
    const resultado = await db().runTransaction(async (tx) => {
      const [clienteSnap, sesionSnap, reservaSnap] = await Promise.all([
        tx.get(clienteRef),
        tx.get(sesionRef),
        tx.get(reservaRef),
      ]);

      const cliente = clienteSnap.exists ? clienteSnap.data() : null;
      const sesion = sesionSnap.exists ? sesionSnap.data() : null;

      // La agenda se escribe siempre en el MISMO día de la sesión. Si la fecha
      // cambió entre la lectura previa y esta transacción (edición simultánea
      // del ADMIN), se aborta para no reconstruir el día equivocado.
      if (!sesion || sesion.fecha !== fecha) {
        throw new HttpsError(
          "failed-precondition",
          "La sesión ha cambiado. Inténtalo de nuevo"
        );
      }

      // Validación de la identidad de la ficha (fuente de verdad dentro de la tx).
      if (cliente && cliente.negocioId !== negocioId) {
        throw errorDeDecision({
          motivo: "clienteSinNegocio",
          mensaje: "El cliente no tiene negocio",
        });
      }
      if (cliente && cliente.firebaseUid !== uid) {
        throw errorDeDecision({
          motivo: "clienteCuenta",
          mensaje: "La ficha no pertenece a tu cuenta",
        });
      }

      const idServicioObjetivo = sesion ? sesion.idServicio : null;
      let servicio = null;
      if (Number.isInteger(idServicioObjetivo)) {
        const servicioSnap = await tx.get(refServicio(idServicioObjetivo));
        servicio = servicioSnap.exists ? servicioSnap.data() : null;
      }

      // Otras reservas REALES del día: candidatas = agenda existente + reservas
      // reales del día reconciliadas bajo demanda (todas validadas en la tx).
      const agendaRef = refAgenda(clienteId, fecha);
      const agendaSnap = await tx.get(agendaRef);
      const candidatas = sesionesCandidatasDelDia(agendaSnap, reales, sesionId);
      const { vivas, otrasPermiten } = await datosDeOtrasSesionesDelDia(
        tx,
        clienteId,
        negocioId,
        fecha,
        candidatas
      );

      const decision = plan.resolverReserva({
        uid,
        negocioId,
        cliente,
        sesion,
        servicio,
        reservaExiste: reservaSnap.exists,
        otrasPermiten,
        ahoraMs,
      });

      if (!decision.permitido) {
        throw errorDeDecision(decision);
      }
      if (decision.yaReservada) {
        return { ok: true, yaReservada: true };
      }

      const plazas = Number(sesion.plazasDisponibles);
      const nombreCliente =
        typeof cliente.nombre === "string" && cliente.nombre.length > 0
          ? cliente.nombre
          : "Cliente";

      // 1) Reserva.
      tx.set(reservaRef, {
        idReserva: plan.reservaId(clienteId, sesionId),
        negocioId,
        sesionId,
        clienteId,
        fechaReserva: Timestamp.now(),
      });

      // 2) Plaza + asistente en la sesión (dentro de la misma tx).
      tx.update(sesionRef, {
        plazasDisponibles: plazas - 1,
        [`asistentes.${clienteId}`]: nombreCliente,
      });

      // 3) Agenda del día reconstruida desde las reservas VIVAS validadas en la
      //    tx + la nueva sesión. Las entradas obsoletas desaparecen aquí (la
      //    agenda es derivada de las reservas reales).
      const sesiones = {};
      for (const viva of vivas) {
        sesiones[String(viva.sesionId)] = viva.idServicio;
      }
      sesiones[String(sesionId)] = idServicioObjetivo;
      tx.set(agendaRef, {
        negocioId,
        fecha,
        sesiones,
      });

      return { ok: true, creada: true };
    });

    return resultado;
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      `No se pudo realizar la reserva: ${e && e.message ? e.message : e}`
    );
  }
}

/**
 * cancelarReserva
 * ---------------
 * Callable cancelarReserva({ sesionId }). Cancela la reserva de forma ATÓMICA:
 * libera la plaza, retira el asistente y actualiza la agenda del día.
 * Idempotente: si la reserva ya no existe devuelve éxito (yaCancelada).
 */
async function cancelarReserva(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión para cancelar");
  }

  const sesionId = request.data && request.data.sesionId;
  if (!Number.isInteger(sesionId) || sesionId <= 0) {
    throw new HttpsError("invalid-argument", "La sesión indicada no es válida");
  }

  const { clienteId, negocioId } = await leerUsuarioVinculado(uid);

  const reservaRef = refReserva(clienteId, sesionId);
  const sesionRef = refSesion(sesionId);

  try {
    const resultado = await db().runTransaction(async (tx) => {
      const reservaSnap = await tx.get(reservaRef);
      if (!reservaSnap.exists) {
        return { ok: true, yaCancelada: true };
      }
      const reserva = reservaSnap.data();
      if (Number(reserva.clienteId) !== clienteId || reserva.negocioId !== negocioId) {
        throw errorDeDecision({
          motivo: "reservaNoPertenece",
          mensaje: "Esta reserva no pertenece a tu cuenta",
        });
      }

      const sesionSnap = await tx.get(sesionRef);
      const sesion = sesionSnap.exists ? sesionSnap.data() : null;

      const decision = plan.resolverCancelacion({ clienteId, negocioId, reserva, sesion });
      if (!decision.permitido) {
        throw errorDeDecision(decision);
      }
      if (decision.yaCancelada) {
        return { ok: true, yaCancelada: true };
      }

      // TODAS las lecturas ANTES de cualquier escritura (requisito de las
      // Transaction de Firestore: no se puede leer tras escribir). Aquí se lee
      // la agenda del día de la sesión para decidir si se actualiza o se borra.
      const fecha = sesion.fecha;
      let agendaSnap = null;
      let agendaRef = null;
      if (Number.isInteger(fecha)) {
        agendaRef = refAgenda(clienteId, fecha);
        agendaSnap = await tx.get(agendaRef);
      }
      const sinSesion = agendaRef
        ? plan.agendaQuitarSesion(
            agendaSnap && agendaSnap.exists ? agendaSnap.data() : null,
            sesionId
          )
        : null;

      // Escrituras (después de todas las lecturas):
      // 1) Reserva eliminada.
      // 2) Plaza recuperada (sin superar la capacidad) y asistente retirado.
      // 3) Agenda actualizada o eliminada según el resultado anterior.
      const plazas = Number(sesion.plazasDisponibles);
      const capacidad = Number(sesion.capacidad);
      const plazasFinales = Number.isFinite(capacidad)
        ? Math.min(capacidad, plazas + 1)
        : plazas + 1;

      tx.delete(reservaRef);
      tx.update(sesionRef, {
        plazasDisponibles: plazasFinales,
        [`asistentes.${clienteId}`]: FieldValue.delete(),
      });

      if (agendaRef) {
        if (Object.keys(sinSesion).length === 0) {
          if (agendaSnap && agendaSnap.exists) tx.delete(agendaRef);
        } else {
          tx.set(agendaRef, {
            negocioId,
            fecha,
            sesiones: sinSesion,
          });
        }
      }

      return { ok: true, cancelada: true };
    });

    return resultado;
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      `No se pudo cancelar la reserva: ${e && e.message ? e.message : e}`
    );
  }
}

module.exports = { reservar, cancelarReserva };
