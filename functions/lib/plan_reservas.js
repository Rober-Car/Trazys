"use strict";

/**
 * plan_reservas.js
 * ----------------
 * MÓDULO PURO (sin Firebase) con la lógica de decisión de las reservas del
 * CLIENTE ejecutadas desde Cloud Functions callable (FASE 1: backend de
 * reservas). Se puede testear con node --test sin dependencias.
 *
 * La ejecución real (Admin SDK + Transactions) vive en reservas.js y usa estas
 * reglas. La identidad del CLIENTE (clienteId, negocioId) se deriva SIEMPRE de
 * usuarios/{uid}; las callables nunca aceptan clienteId/negocioId de la app.
 *
 * Datos que lee/escribe (FASE 1):
 *   - servicios/{idServicio}.permiteCombinarDia : bool, default true si falta.
 *   - sesiones/{idSesion}.asistentes            : { [clienteId]: nombre }.
 *   - clientes/{clienteId}/agenda/{fecha}       : documento derivado del día,
 *         { negocioId, fecha, sesiones: { [sesionId]: idServicio } }.
 *   - reservas/{clienteId}_{sesionId}           : estructura actual intacta.
 */

const AGENDA_SUBCOLECCION = "agenda";

/**
 * reservaId
 * ---------
 * DocumentId determinista de una reserva: {clienteId}_{sesionId}.
 */
function reservaId(clienteId, sesionId) {
  return `${clienteId}_${sesionId}`;
}

/**
 * rutaAgendaDelDia
 * ----------------
 * Ruta del documento agenda derivado del día de una sesión:
 * clientes/{clienteId}/agenda/{fecha}, donde fecha es el epoch millis de la
 * medianoche local del día (el mismo valor que guarda sesiones/{id}.fecha).
 */
function rutaAgendaDelDia(clienteId, fecha) {
  return `clientes/${clienteId}/${AGENDA_SUBCOLECCION}/${fecha}`;
}

/**
 * esMismoDia
 * ----------
 * Dos sesiones pertenecen al mismo día cuando comparten su fecha normalizada
 * (epoch millis de la medianoche local). Es el mismo criterio del proyecto
 * (las sesiones se generan con esa medianoche en sesiones/{id}.fecha).
 */
function esMismoDia(fechaA, fechaB) {
  return fechaA === fechaB;
}

/**
 * permiteCombinar
 * ---------------
 * Regla `permiteCombinarDia`: default TRUE si el campo falta, es null o no es
 * booleano. Solo un false explícito bloquea la combinación con otra actividad
 * el mismo día.
 */
function permiteCombinar(servicio) {
  if (!servicio) return true;
  return servicio.permiteCombinarDia !== false;
}

/**
 * combinacionBloqueada
 * --------------------
 * Un CLIENTE puede tener varias sesiones reservadas el mismo día SOLO si todas
 * las actividades implicadas (la nueva y las ya reservadas ese día) permiten
 * combinar. Si ya hay otra sesión el mismo día y la nueva o cualquiera de las
 * existentes no permite combinar -> la nueva reserva se bloquea.
 *
 * - otrasPermiten: lista de booleanos (permiteCombinarDia ya resuelto) de las
 *   otras sesiones REALES del día del cliente (excluye la sesión objetivo).
 */
function combinacionBloqueada(nuevaPermite, otrasPermiten) {
  if (!otrasPermiten || otrasPermiten.length === 0) return false;
  if (nuevaPermite !== true) return true;
  return otrasPermiten.some((p) => p !== true);
}

/**
 * aperturaAlcanzada
 * -----------------
 * Una sesión sin horaDesdeReserva (o null) permite reservar desde el inicio
 * del día. Si tiene hora "HH:mm", la reserva solo se permite desde el instante
 * fecha + offset de la hora. Mismo criterio que la app y las Rules.
 */
function aperturaAlcanzada(fecha, horaDesdeReserva, ahoraMs) {
  if (horaDesdeReserva == null || typeof horaDesdeReserva !== "string") {
    return true;
  }
  const partes = horaDesdeReserva.split(":");
  const h = Number(partes[0]);
  const m = Number(partes[1]);
  if (!Number.isInteger(h) || !Number.isInteger(m)) return true;
  return ahoraMs >= fecha + (h * 3600000 + m * 60000);
}

/** ¿El cliente tiene contratado el servicio (lista de ids enteros)? */
function servicioContratado(idServicio, serviciosContratados) {
  return (
    Number.isInteger(idServicio) &&
    Array.isArray(serviciosContratados) &&
    serviciosContratados.some((s) => Number(s) === idServicio)
  );
}

/**
 * resolverReserva
 * ---------------
 * Valida una petición de NUEVA reserva (o reintento idempotente). Devuelve:
 *   { permitido: true,  yaReservada?: true }
 *   { permitido: false, motivo, mensaje }
 *
 * entrada: {
 *   uid, negocioId, clienteId,
 *   cliente: { negocioId, firebaseUid, estado, serviciosContratados } | null,
 *   sesion:  { negocioId, idServicio, fecha, horaDesdeReserva, plazasDisponibles } | null,
 *   servicio: { negocioId, activo, permiteCombinarDia } | null,
 *   reservaExiste: boolean,
 *   otrasPermiten: boolean[],       // permiteCombinarDia de cada otra sesión real del día
 *   ahoraMs: number
 * }
 */
function resolverReserva(entrada) {
  const {
    uid,
    negocioId,
    cliente,
    sesion,
    servicio,
    reservaExiste,
    otrasPermiten,
    ahoraMs,
  } = entrada;

  if (!cliente) {
    return { permitido: false, motivo: "clienteNoExiste", mensaje: "El cliente no existe" };
  }
  if (cliente.negocioId !== negocioId) {
    return { permitido: false, motivo: "clienteSinNegocio", mensaje: "El cliente no tiene negocio" };
  }
  if (cliente.firebaseUid !== uid) {
    return {
      permitido: false,
      motivo: "clienteCuenta",
      mensaje: "La ficha no pertenece a tu cuenta",
    };
  }
  if (cliente.estado !== "ACTIVO") {
    const motivo = cliente.estado === "BAJA" ? "clienteDadoDeBaja" : "clienteNoActivo";
    const mensaje =
      cliente.estado === "BAJA"
        ? "Estás dado de baja y no puedes reservar"
        : "Tu cuenta no está activa para reservar";
    return { permitido: false, motivo, mensaje };
  }

  if (!sesion) {
    return { permitido: false, motivo: "sesionNoExiste", mensaje: "La sesión no existe" };
  }
  if (sesion.negocioId !== negocioId) {
    return {
      permitido: false,
      motivo: "sesionNoNegocio",
      mensaje: "La sesión no pertenece a tu negocio",
    };
  }
  const idServicio = sesion.idServicio;
  if (!Number.isInteger(idServicio)) {
    return {
      permitido: false,
      motivo: "sesionSinServicio",
      mensaje: "La sesión no tiene servicio",
    };
  }
  if (!Number.isInteger(sesion.fecha)) {
    return {
      permitido: false,
      motivo: "sesionSinFecha",
      mensaje: "La sesión no tiene fecha",
    };
  }

  if (!servicio) {
    return { permitido: false, motivo: "servicioNoExiste", mensaje: "El servicio no existe" };
  }
  if (servicio.negocioId !== negocioId) {
    return {
      permitido: false,
      motivo: "servicioNoNegocio",
      mensaje: "El servicio no pertenece a tu negocio",
    };
  }
  if (servicio.activo !== true) {
    return {
      permitido: false,
      motivo: "servicioInactivo",
      mensaje: "El servicio está inactivo",
    };
  }

  const contratados = (cliente.serviciosContratados || []).map((s) => Number(s));
  if (!servicioContratado(idServicio, contratados)) {
    return {
      permitido: false,
      motivo: "actividadNoContratada",
      mensaje: "No tienes contratado este servicio",
    };
  }

  // Reintento idempotente: la reserva ya existe, no se crea de nuevo ni se
  // vuelve a restar plaza. Estado final ya correcto.
  if (reservaExiste) {
    return { permitido: true, yaReservada: true };
  }

  const plazas = Number(sesion.plazasDisponibles);
  if (plazas <= 0) {
    return {
      permitido: false,
      motivo: "sinPlazas",
      mensaje: "No hay plazas disponibles",
    };
  }

  if (!aperturaAlcanzada(sesion.fecha, sesion.horaDesdeReserva, ahoraMs)) {
    return {
      permitido: false,
      motivo: "reservasAbrenA",
      mensaje: `Las reservas se abren a las ${sesion.horaDesdeReserva}`,
    };
  }

  if (combinacionBloqueada(permiteCombinar(servicio), otrasPermiten)) {
    return {
      permitido: false,
      motivo: "actividadNoCombinable",
      mensaje:
        "No puedes reservar esta actividad porque ya tienes otra reserva el mismo día " +
        "y alguna de las dos no permite combinarse",
    };
  }

  return { permitido: true };
}

/**
 * resolverCancelacion
 * -------------------
 * Valida una petición de CANCELACIÓN de reserva. Idempotente: si la reserva ya
 * no existe el estado final ya es el deseado -> permitido con yaCancelada=true
 * (sin error y sin tocar nada). Si existe se exige que la sesión siga existiendo
 * (para poder devolver la plaza) y que la reserva pertenezca al cliente.
 *
 * entrada: { clienteId, negocioId, reserva, sesion }  (o null si no existen).
 */
function resolverCancelacion(entrada) {
  const { clienteId, negocioId, reserva, sesion } = entrada;

  if (!reserva) {
    return { permitido: true, yaCancelada: true };
  }
  if (Number(reserva.clienteId) !== clienteId || reserva.negocioId !== negocioId) {
    return {
      permitido: false,
      motivo: "reservaNoPertenece",
      mensaje: "Esta reserva no pertenece a tu cuenta",
    };
  }
  if (!sesion) {
    return { permitido: false, motivo: "sesionNoExiste", mensaje: "La sesión no existe" };
  }
  if (sesion.negocioId !== negocioId) {
    return {
      permitido: false,
      motivo: "sesionNoNegocio",
      mensaje: "La sesión no pertenece a tu negocio",
    };
  }
  return { permitido: true };
}

/**
 * agendaAgregarSesion
 * -------------------
 * Devuelve un NUEVO mapa de sesiones del día (sesionId -> idServicio) con la
 * sesión añadida o sustituida. `agenda` puede ser null/undefined (mapa vacío).
 */
function agendaAgregarSesion(agenda, sesionId, idServicio) {
  const mapa = agenda && typeof agenda.sesiones === "object" && agenda.sesiones
    ? Object.assign({}, agenda.sesiones)
    : {};
  mapa[String(sesionId)] = idServicio;
  return mapa;
}

/**
 * agendaQuitarSesion
 * ------------------
 * Devuelve un NUEVO mapa de sesiones del día sin la sesión indicada.
 */
function agendaQuitarSesion(agenda, sesionId) {
  const mapa = agenda && typeof agenda.sesiones === "object" && agenda.sesiones
    ? Object.assign({}, agenda.sesiones)
    : {};
  delete mapa[String(sesionId)];
  return mapa;
}

/**
 * reconstruirAgenda
 * -----------------
 * Reconstrucción bajo demanda del documento agenda de un día a partir de la
 * fuente de verdad (las reservas REALES del día). `reales` es una lista de
 * { sesionId, idServicio } confirmados. Devuelve { sesiones, cambio }:
 *   - sesiones: mapa reconstruido (sesionId -> idServicio).
 *   - cambio: true si difiere de lo que ya había en `agenda`.
 * La agenda es un dato DERIVADO: nunca se decide a partir de ella sin validar
 * contra las reservas reales del día.
 */
function reconstruirAgenda(agenda, reales) {
  const mapa = {};
  for (const real of reales || []) {
    mapa[String(real.sesionId)] = real.idServicio;
  }
  const previo = agenda && typeof agenda.sesiones === "object" && agenda.sesiones
    ? agenda.sesiones
    : {};
  const clavesPrevias = Object.keys(previo);
  const cambio =
    clavesPrevias.length !== Object.keys(mapa).length ||
    clavesPrevias.some((k) => Number(previo[k]) !== Number(mapa[k]));
  return { sesiones: mapa, cambio };
}

module.exports = {
  AGENDA_SUBCOLECCION,
  reservaId,
  rutaAgendaDelDia,
  esMismoDia,
  permiteCombinar,
  combinacionBloqueada,
  aperturaAlcanzada,
  servicioContratado,
  resolverReserva,
  resolverCancelacion,
  agendaAgregarSesion,
  agendaQuitarSesion,
  reconstruirAgenda,
};
