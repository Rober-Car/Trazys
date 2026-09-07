"use strict";

/**
 * eliminacion.js
 * --------------
 * Eliminación COMPLETA e idempotente de cuenta (CLIENTE) o cuenta+negocio
 * (ADMIN) usando Admin SDK (no pasa por las Firestore Rules, que mantienen los
 * borrados cerrados). El objetivo se deriva EXCLUSIVAMENTE de usuarios/{uid}.
 *
 * Orden de seguridad:
 *   1) Firestore (datos) -> 2) Storage (fotos/logo) -> 3) usuarios/{uid}
 *   -> 4) Firebase Auth (lo último).
 * Si una fase falla se lanza un HttpsError y NO se borra Auth; el reintento
 * vuelve a ejecutarse de forma idempotente (borrar inexistentes es no-op).
 */

const { getFirestore } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");
const { HttpsError } = require("firebase-functions/v2/https");
const plan = require("./plan_eliminacion");

function db() {
  return getFirestore();
}

/** Borra un documento por su ruta "coleccion/id" (idempotente). */
async function borrarDoc(ruta) {
  await db().doc(ruta).delete();
}

/** Borra todos los documentos de una consulta (idempotente). */
async function borrarConsulta(consulta) {
  const snap = await consulta.get();
  for (const d of snap.docs) {
    await d.ref.delete();
  }
}

/** Borra documentos de una colección con igualdad en un campo. */
async function borrarPorIgualdad(coleccion, campo, valor) {
  await borrarConsulta(db().collection(coleccion).where(campo, "==", valor));
}

/** Borra una subcolección completa (p. ej. dispositivos de un cliente). */
async function borrarSubcoleccion(rutaColeccion) {
  await borrarConsulta(db().collection(rutaColeccion));
}

async function borrarStorage(rutas) {
  const bucket = getStorage().bucket();
  for (const ruta of rutas) {
    try {
      await bucket.file(ruta).delete({ ignoreNotFound: true });
    } catch (_) {
      // no-op idempotente
    }
  }
}

/** Fase CLIENTE: borra su cuenta y limpia a un mínimo histórico la ficha. */
async function borrarCliente(uid, clienteId, negocioId) {
  if (clienteId == null) {
    // Sin ficha: solo perfil pendiente + usuarios (se limpia después).
    return;
  }

  const refFicha = db().doc(`${plan.CLIENTES}/${clienteId}`);
  const ficha = await refFicha.get();
  if (ficha.exists) {
    const datos = ficha.data();
    if (!plan.fichaPerteneceAlUsuario(datos, uid)) {
      throw new HttpsError(
        "permission-denied",
        "La ficha no pertenece a este usuario"
      );
    }
    // Conservar el histórico mínimo y DESVINCULAR la cuenta:
    // idCliente, negocioId, nombre, apellidos, dni, firebaseUid=null, foto="".
    await refFicha.set({
      idCliente: clienteId,
      negocioId: negocioId,
      nombre: datos.nombre || "",
      apellidos: datos.apellidos || "",
      dni: datos.dni || "",
      firebaseUid: null,
      foto: "",
    });
  }

  // Ámbito personal / actividad que ya no corresponde a la cuenta.
  await borrarSubcoleccion(plan.rutaDispositivos(plan.CLIENTES, clienteId));
  await borrarPorIgualdad(plan.RESERVAS, "clienteId", clienteId);
  await borrarPorIgualdad(plan.SOLICITUDES, "idCliente", clienteId);
  await borrarPorIgualdad(plan.NOTIFICACIONES_BUZON, "clienteId", clienteId);
  await borrarDoc(`${plan.CLIENTES_PRIVADOS}/${clienteId}`);
  await borrarStorage(plan.rutasStorageCliente(clienteId));
  // MOVIMIENTOS: no se tocan. ÍNDICE: se conserva (permite VÍA 1 futura).
}

/** Fase ADMIN: borra el negocio completo y sus fotos/logo. */
async function borrarNegocio(uid, negocioId) {
  // El ADMIN solo puede borrar SU negocio (negocioId == su UID).
  if (negocioId !== uid) {
    throw new HttpsError(
      "permission-denied",
      "No se puede eliminar un negocio que no te pertenece"
    );
  }

  const refNegocio = db().doc(`${plan.NEGOCIOS}/${negocioId}`);
  const negocio = await refNegocio.get();
  const codigo = negocio.exists && negocio.data()
    ? negocio.data().codigoMaestro
    : null;

  // Clientes del negocio: por cada uno se borran dispositivos, índice y foto.
  const fotos = [];
  const clientes = await db()
    .collection(plan.CLIENTES)
    .where("negocioId", "==", negocioId)
    .get();
  for (const c of clientes.docs) {
    const datos = c.data() || {};
    const clienteId = c.id;
    const dni = datos.dni ? String(datos.dni).toUpperCase() : null;
    await borrarSubcoleccion(plan.rutaDispositivos(plan.CLIENTES, clienteId));
    if (dni) await borrarDoc(plan.rutaIndice(negocioId, dni));
    await borrarDoc(`${plan.CLIENTES_PRIVADOS}/${clienteId}`);
    await c.ref.delete();
    fotos.push(...plan.rutasStorageCliente(clienteId));
  }

  // Colecciones por negocioId (borrado masivo por igualdad simple).
  for (const coleccion of plan.coleccionesConNegocioId()) {
    await borrarPorIgualdad(coleccion, "negocioId", negocioId);
  }

  // Documentos fijos del negocio + usuarios/{uid} + codigo.
  for (const ruta of plan.rutasFijasAdmin(negocioId, codigo)) {
    await borrarDoc(ruta);
  }

  fotos.push(plan.rutaStorageLogo(negocioId));
  await borrarStorage(fotos);
}

/**
 * eliminarMiCuenta
 * ----------------
 * Entrypoint de la Function callable. `request.auth.uid` es la ÚNICA fuente
 * del objetivo. No se aceptan uid/clienteId/negocioId enviados por la app.
 */
async function eliminarMiCuenta(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError(
      "unauthenticated",
      "Debes iniciar sesión para eliminar tu cuenta"
    );
  }

  const usuarioRef = db().doc(`${plan.USUARIOS}/${uid}`);
  const usuario = await usuarioRef.get();
  const datos = usuario.exists ? usuario.data() : null;
  const objetivo = plan.objetivoDesdeUsuario(datos);

  try {
    if (objetivo && objetivo.tipo === "ADMIN") {
      await borrarNegocio(uid, objetivo.negocioId);
    } else if (objetivo && objetivo.tipo === "CLIENTE") {
      await borrarCliente(
        uid,
        objetivo.clienteId,
        objetivo.negocioId
      );
    } else {
      // Sin documento de usuario (o rol desconocido): limpiar residuo posible.
      await borrarDoc(`${plan.PERFILES_PENDIENTES}/${uid}`);
    }

    // usuarios/{uid} se borra al final, justo antes de Auth.
    if (usuario.exists) {
      await usuarioRef.delete();
    }

    // Auth lo último: si algo falló antes no se pierde la identidad.
    await getAuth().deleteUser(uid);
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      `No se pudo completar la eliminación: ${e && e.message ? e.message : e}`
    );
  }

  return { ok: true };
}

module.exports = { eliminarMiCuenta };
