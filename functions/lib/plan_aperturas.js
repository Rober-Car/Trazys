"use strict";

/**
 * plan_aperturas.js
 * -----------------
 * Lógica PURA (sin Firebase) de las notificaciones automáticas de APERTURA DE
 * RESERVAS. Reutiliza EXACTAMENTE el criterio de apertura del resto del sistema
 * (app y Rules):
 *
 *      apertura = fecha (medianoche local en epoch ms) + horaDesdeReserva "HH:mm"
 *
 * Si `horaDesdeReserva` es null/ausente NO hay evento de apertura (la sesión ya
 * estaba disponible desde el inicio del día).
 *
 * AGRUPACIÓN ("oleadas"): buckets fijos de 5 minutos alineados al reloj. Un
 * bucket se procesa SOLO cuando está CERRADO (retardo intencionado de hasta
 * ~5 min) para que las aperturas próximas en el tiempo se agrupen en una única
 * notificación por cliente.
 *
 * IDENTIFICACIÓN DETERMINISTA: el ID de la notificación es
 * `idNotificacionApertura(clienteId, bucketStart)` (ver ids.js), de modo que
 * repetir el barrido nunca duplica el envío.
 *
 * Módulo sin dependencias: testeable sin Firebase.
 */

/** Tamaño de bucket: 5 minutos (alineado al reloj en epoch ms). */
const BUCKET_MS = 5 * 60 * 1000;

/**
 * Nº de buckets CERRADOS que se revisan en cada barrido. Permite tolerar
 * retrasos/reintentos del barrido sin perder oleadas. Reprocesar es inocuo
 * (IDs deterministas).
 */
const LOOKBACK_BUCKETS = 3;

const TITULO_ES = "¡Ya están abiertas las reservas!";
const TITULO_EN = "Reservations are now open!";

/**
 * aperturaDeSesion
 * ----------------
 * Instante absoluto (epoch ms) en que se abren las reservas de una sesión, o
 * null si no hay `horaDesdeReserva` válida (no es un evento de apertura).
 */
function aperturaDeSesion(sesion) {
  if (!sesion) return null;
  const hora = sesion.horaDesdeReserva;
  if (hora == null || typeof hora !== "string") return null;
  const partes = hora.split(":");
  const h = Number(partes[0]);
  const m = Number(partes[1]);
  if (!Number.isInteger(h) || !Number.isInteger(m)) return null;
  const fecha = Number(sesion.fecha);
  if (!Number.isFinite(fecha)) return null;
  return fecha + (h * 3600000 + m * 60000);
}

/**
 * bucketStartOf
 * -------------
 * Inicio del bucket de 5 minutos (alineado al reloj) al que pertenece un
 * instante.
 */
function bucketStartOf(instanteMs, bucketMs = BUCKET_MS) {
  return Math.floor(instanteMs / bucketMs) * bucketMs;
}

/**
 * cubosCerrados
 * -------------
 * Lista de buckets YA CERRADOS que deben procesarse en un barrido a `ahoraMs`.
 * El bucket en curso no se procesa (podrían llegar más aperturas).
 * Devuelve, de más reciente a más antiguo:
 *   [inicioCuboActual - B, inicioCuboActual - 2B, ...]
 */
function cubosCerrados(ahoraMs, bucketMs = BUCKET_MS, lookback = LOOKBACK_BUCKETS) {
  const actual = bucketStartOf(ahoraMs, bucketMs);
  const cubos = [];
  for (let i = 1; i <= lookback; i++) cubos.push(actual - i * bucketMs);
  return cubos;
}

/**
 * agruparAperturasPorNegocioYCubo
 * -------------------------------
 * A partir de una lista de sesiones y de los buckets cerrados a procesar,
 * devuelve:
 *   { [negocioId]: { [bucketStart]: [idServicio, ...] } }
 * Incluye solo sesiones cuya apertura cae dentro de un bucket cerrado. Los
 * servicios se deduplican dentro del bucket.
 */
function agruparAperturasPorNegocioYCubo(sesiones, cubos) {
  const cerrados = new Set(cubos);
  const resultado = {};
  for (const sesion of sesiones || []) {
    const apertura = aperturaDeSesion(sesion);
    if (apertura === null) continue;
    const bucket = bucketStartOf(apertura);
    if (!cerrados.has(bucket)) continue;
    const negocioId = sesion.negocioId;
    const idServicio = Number(sesion.idServicio);
    if (typeof negocioId !== "string" || negocioId.length === 0) continue;
    if (!Number.isInteger(idServicio)) continue;
    if (!resultado[negocioId]) resultado[negocioId] = {};
    if (!resultado[negocioId][bucket]) resultado[negocioId][bucket] = [];
    if (!resultado[negocioId][bucket].includes(idServicio)) {
      resultado[negocioId][bucket].push(idServicio);
    }
  }
  return resultado;
}

/**
 * puedeRecibirApertura
 * --------------------
 * ¿La ficha del cliente puede recibir un aviso de apertura? Solo ACTIVO y
 * vinculado (firebaseUid válido). BAJA/ARCHIVADO/REGISTRADO u otros estados
 * quedan excluidos.
 */
function puedeRecibirApertura(cliente) {
  return (
    !!cliente &&
    cliente.estado === "ACTIVO" &&
    typeof cliente.firebaseUid === "string" &&
    cliente.firebaseUid.length > 0
  );
}

/**
 * serviciosDelClienteEnElBucket
 * -----------------------------
 * Intersección `serviciosContratados ∩ serviciosAbiertos` (ids enteros,
 * deduplicados, preservando el orden de los contratados).
 */
function serviciosDelClienteEnElBucket(serviciosContratados, serviciosAbiertos) {
  const abiertos = new Set((serviciosAbiertos || []).map(Number));
  const contratados = Array.isArray(serviciosContratados) ? serviciosContratados : [];
  const vistos = new Set();
  const resultado = [];
  for (const raw of contratados) {
    const id = Number(raw);
    if (!Number.isInteger(id)) continue;
    if (abiertos.has(id) && !vistos.has(id)) {
      vistos.add(id);
      resultado.push(id);
    }
  }
  return resultado;
}

/**
 * nombresOrdenados
 * ----------------
 * Resuelve los nombres de los servicios y los ordena alfabéticamente para
 * construir un mensaje estable (p. ej. "CrossFit, Pilates y Yoga").
 */
function nombresOrdenados(ids, nombresPorId) {
  const nombres = [];
  for (const id of ids || []) {
    const nombre = nombresPorId ? nombresPorId[id] : null;
    if (typeof nombre === "string" && nombre.length > 0) nombres.push(nombre);
  }
  return nombres.sort((a, b) => a.localeCompare(b, "es"));
}

/**
 * formatearLista
 * --------------
 * Une nombres con coma y conjunción:
 *   ["A"]                       -> "A"
 *   ["A","B"]                   -> "A y B"      (ES) / "A and B" (EN)
 *   ["A","B","C"]               -> "A, B y C"   / "A, B and C"
 */
function formatearLista(nombres, conjuncion) {
  const n = (nombres || []).filter((x) => typeof x === "string" && x.length > 0);
  if (n.length === 0) return "";
  if (n.length === 1) return n[0];
  if (n.length === 2) return `${n[0]} ${conjuncion} ${n[1]}`;
  return `${n.slice(0, -1).join(", ")} ${conjuncion} ${n[n.length - 1]}`;
}

/**
 * construirMensajeApertura
 * ------------------------
 * Mensaje personalizado (ES/EN) con concordancia singular/plural.
 *   ES: "CrossFit ya está disponible. ¡No te quedes sin la tuya!"
 *   EN: "CrossFit is now available. Don't miss your spot!"
 */
function construirMensajeApertura(nombres, idioma) {
  const n = (nombres || []).filter((x) => typeof x === "string" && x.length > 0);
  if (n.length === 0) return "";
  if (idioma === "en") {
    const lista = formatearLista(n, "and");
    const verbo = n.length === 1 ? "is" : "are";
    return `${lista} ${verbo} now available. Don't miss your spot!`;
  }
  const lista = formatearLista(n, "y");
  const verbo = n.length === 1 ? "está disponible" : "están disponibles";
  return `${lista} ya ${verbo}. ¡No te quedes sin la tuya!`;
}

module.exports = {
  BUCKET_MS,
  LOOKBACK_BUCKETS,
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
};
