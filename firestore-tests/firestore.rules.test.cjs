const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { after, before, test } = require("node:test");

const {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment
} = require("@firebase/rules-unit-testing");
const {
    Timestamp,
    collection,
    deleteDoc,
    doc,
    getDoc,
    getDocs,
    query,
    runTransaction,
    setDoc,
    updateDoc,
    where,
    writeBatch
} = require("firebase/firestore");
const {
    ref: storageRef,
    uploadBytes,
    getBytes,
    deleteObject
} = require("firebase/storage");

const PROJECT_ID = "gestorpro-rules-test";
const CLIENTE_UID = "Vnyht6hlR5EYJ1G0vxxl";
const OTRO_CLIENTE_UID = "otro-cliente-de-prueba";
const NEGOCIO_A = "negocio-a";
const NEGOCIO_B = "negocio-b";

// Campos completos de la ficha publica de un cliente.
function fichaCliente(
    idCliente,
    negocioId,
    firebaseUid,
    dni,
    extra = {}
) {
    return {
        idCliente,
        negocioId,
        firebaseUid,
        nombre: extra.nombre ?? "Cliente",
        apellidos: extra.apellidos ?? "De Prueba",
        dni,
        telefono: extra.telefono ?? "600000000",
        email: extra.email ?? "cliente@test.com",
        foto: extra.foto ?? "",
        fechaNacimiento: extra.fechaNacimiento ?? 0,
        fechaRegistro: extra.fechaRegistro ?? 1,
        fechaAlta: extra.fechaAlta ?? null,
        fechaBaja: extra.fechaBaja ?? null,
        estado: extra.estado ?? "ACTIVO",
        serviciosContratados: extra.serviciosContratados ?? [],
        fechaInicioActual: extra.fechaInicioActual ?? null,
        fechaFinActual: extra.fechaFinActual ?? null,
        ...extra
    };
}

function indiceId(negocioId, dni) {
    return `${negocioId}_${dni}`;
}

// Documento completo de un servicio (contrato de servicios/{idServicio}).
function servicioDoc(idServicio, negocioId, extra = {}) {
    return {
        idServicio,
        negocioId,
        nombre: extra.nombre ?? "Servicio de prueba",
        descripcion: extra.descripcion ?? "DescripciÃ³n de prueba",
        activo: extra.activo ?? true,
        precio: extra.precio ?? 30,
        ...extra
    };
}

// Documento completo de una sesiÃ³n (contrato de sesiones/{idSesion}).
function sesionDoc(idSesion, negocioId, idServicio, extra = {}) {
    return {
        idSesion,
        negocioId,
        idServicio,
        fecha: extra.fecha ?? 1700000000000,
        hora: extra.hora ?? "18:00",
        duracionMinutos: extra.duracionMinutos ?? 60,
        capacidad: extra.capacidad ?? 20,
        plazasDisponibles: extra.plazasDisponibles ?? 20,
        ...extra
    };
}

// Documento completo de una reserva (contrato de reservas/{clienteId}_{sesionId}).
function reservaDoc(clienteId, sesionId, negocioId) {
    return {
        idReserva: `${clienteId}_${sesionId}`,
        negocioId,
        sesionId,
        clienteId,
        fechaReserva: Timestamp.now()
    };
}

let testEnvironment;

before(async () => {
    testEnvironment = await initializeTestEnvironment({
        projectId: PROJECT_ID,
        firestore: {
            rules: fs.readFileSync(
                path.resolve(__dirname, "..", "firestore.rules"),
                "utf8"
            )
        },
        storage: {
            rules: fs.readFileSync(
                path.resolve(__dirname, "..", "storage.rules"),
                "utf8"
            )
        }
    });

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        // CLIENTE sin vÃ­nculo (para pruebas de aislamiento).
        await setDoc(doc(database, "usuarios", CLIENTE_UID), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        // Ficha ajena ya vinculada.
        await setDoc(
            doc(database, "clientes", "1"),
            fichaCliente(1, NEGOCIO_A, OTRO_CLIENTE_UID, "12345678A")
        );
    });
});

after(async () => {
    await testEnvironment?.cleanup();
});

test("PRUEBA 1: un CLIENTE no puede leer otro cliente", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();

    await assertSucceeds(
        getDoc(doc(database, "usuarios", CLIENTE_UID))
    );

    await assertFails(
        getDoc(doc(database, "clientes", "1"))
    );
});

test("PRUEBA 2: un CLIENTE no puede modificar sus permisos ni su vinculacion", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    const usuario = doc(database, "usuarios", CLIENTE_UID);

    await assertFails(
        updateDoc(usuario, { rol: "ADMIN" })
    );

    await assertFails(
        updateDoc(usuario, { activo: false })
    );

    await assertFails(
        updateDoc(usuario, { clienteId: 1 })
    );

    await assertFails(
        updateDoc(usuario, { negocioId: "otro-negocio" })
    );
});

test("PRUEBA 3: un ADMIN solo puede leer datos de su negocio", async () => {
    const adminUid = "admin-negocio-a";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "negocios", NEGOCIO_A), {
            adminUid
        });

        await setDoc(doc(database, "negocios", NEGOCIO_B), {
            adminUid: "admin-negocio-b"
        });

        await setDoc(
            doc(database, "clientes", "10"),
            fichaCliente(10, NEGOCIO_A, "cliente-negocio-a", "11111111A")
        );

        await setDoc(
            doc(database, "clientes", "20"),
            fichaCliente(20, NEGOCIO_B, "cliente-negocio-b", "22222222B")
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();

    await assertSucceeds(
        getDoc(doc(database, "clientes", "10"))
    );

    await assertFails(
        getDoc(doc(database, "clientes", "20"))
    );

    await assertFails(
        getDoc(doc(database, "negocios", NEGOCIO_B))
    );

    await assertSucceeds(
        getDoc(doc(database, "negocios", NEGOCIO_A))
    );
});

test("PRUEBA 4: un CLIENTE no puede leer movimientos y un ADMIN sÃ­", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", "cliente-economico-test"), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 2,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "usuarios", "admin-economico-test"), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "movimientos", "movimiento-test"), {
            negocioId: NEGOCIO_A
        });
    });

    const clienteDatabase = testEnvironment
        .authenticatedContext("cliente-economico-test")
        .firestore();

    await assertFails(
        getDoc(doc(clienteDatabase, "movimientos", "movimiento-test"))
    );

    const adminDatabase = testEnvironment
        .authenticatedContext("admin-economico-test")
        .firestore();

    await assertSucceeds(
        getDoc(doc(adminDatabase, "movimientos", "movimiento-test"))
    );
});

test("PRUEBA 5: VIA 2 - un CLIENTE crea su ficha con codigo maestro + DNI", async () => {
    const clienteUid = "cliente-via2-test";
    const otroUid = "cliente-via2-otro-test";
    const dni = "11111111A";
    const dniOtro = "22222222B";
    const idLibre = 74000000001;
    const idOcupado = 74000000002;
    const negocioId = "negocio-via2-5";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        await setDoc(doc(database, "usuarios", otroUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        // Perfil pendiente con el DNI del cliente.
        await setDoc(doc(database, "perfiles_pendientes", clienteUid), {
            nombre: "Ana",
            apellidos: "Lopez",
            dni,
            telefono: "611111111",
            email: "ana@test.com",
            foto: "",
            fechaNacimiento: 0
        });

        await setDoc(doc(database, "perfiles_pendientes", otroUid), {
            nombre: "Pepe",
            apellidos: "Perez",
            dni: dniOtro,
            telefono: "622222222",
            email: "pepe@test.com",
            foto: "",
            fechaNacimiento: 0
        });

        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Prueba",
            codigoMaestro: "MAESTRO-5"
        });

        // Ficha ocupada con OTRO DNI (indice propio), para no interferir con
        // el DNI que el cliente usara en el caso valido.
        await setDoc(
            doc(database, "clientes", String(idOcupado)),
            fichaCliente(idOcupado, negocioId, otroUid, "99999999Z")
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, "99999999Z")),
            { negocioId, dni: "99999999Z", clienteId: idOcupado }
        );
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // Caso valido: Transaction crea ficha + indice + actualiza usuarios.
    const batchValido = writeBatch(database);
    batchValido.set(
        doc(database, "clientes", String(idLibre)),
        fichaCliente(idLibre, negocioId, clienteUid, dni, { estado: "REGISTRADO" })
    );
    batchValido.set(
        doc(database, "indices_clientes", indiceId(negocioId, dni)),
        { negocioId, dni, clienteId: idLibre }
    );
    batchValido.update(doc(database, "usuarios", clienteUid), {
        clienteId: idLibre,
        negocioId
    });

    await assertSucceeds(batchValido.commit());

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const usuario = await getDoc(doc(database, "usuarios", clienteUid));
        const ficha = await getDoc(doc(database, "clientes", String(idLibre)));

        assert.strictEqual(usuario.data().clienteId, idLibre);
        assert.strictEqual(usuario.data().negocioId, negocioId);
        assert.strictEqual(ficha.data().firebaseUid, clienteUid);
        assert.strictEqual(ficha.data().dni, dni);
    });

    // Caso invalido: ya existe un indice para ese negocio+DNI (duplicado).
    const batchDuplicado = writeBatch(database);
    batchDuplicado.set(
        doc(database, "clientes", String(idLibre + 100)),
        fichaCliente(idLibre + 100, negocioId, clienteUid, dni)
    );
    batchDuplicado.set(
        doc(database, "indices_clientes", indiceId(negocioId, dni)),
        { negocioId, dni, clienteId: idLibre + 100 }
    );
    batchDuplicado.update(doc(database, "usuarios", clienteUid), {
        clienteId: idLibre + 100,
        negocioId
    });

    await assertFails(batchDuplicado.commit());

    // Caso invalido: crear la ficha con un DNI distinto del perfil pendiente.
    const databaseOtro = testEnvironment.authenticatedContext(otroUid).firestore();
    const batchDniAjeno = writeBatch(databaseOtro);
    const idDniAjeno = 74000000003;
    batchDniAjeno.set(
        doc(databaseOtro, "clientes", String(idDniAjeno)),
        fichaCliente(idDniAjeno, negocioId, otroUid, dni) // dni de otro
    );
    batchDniAjeno.set(
        doc(databaseOtro, "indices_clientes", indiceId(negocioId, dni)),
        { negocioId, dni, clienteId: idDniAjeno }
    );
    batchDniAjeno.update(doc(databaseOtro, "usuarios", otroUid), {
        clienteId: idDniAjeno,
        negocioId
    });

    await assertFails(batchDniAjeno.commit());

    // Caso invalido: crear la ficha sin crear el indice en el mismo Batch.
    const idHuerfano = 74000000004;
    await assertFails(
        setDoc(
            doc(databaseOtro, "clientes", String(idHuerfano)),
            fichaCliente(idHuerfano, negocioId, otroUid, dniOtro)
        )
    );
});

test("PRUEBA 6: VIA 1 - un CLIENTE vincula una ficha creada por el ADMIN", async () => {
    const adminUid = "admin-via1-test";
    const clienteUid = "cliente-via1-test";
    const dni = "33333333C";
    const idFicha = 741;
    const negocioId = "negocio-via1-6";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId
        });

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Via1",
            codigoMaestro: "MAESTRO-6"
        });

        // Ficha creada por el ADMIN: sin UID y con su indice.
        await setDoc(
            doc(database, "clientes", String(idFicha)),
            fichaCliente(idFicha, negocioId, null, dni)
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, dni)),
            { negocioId, dni, clienteId: idFicha }
        );

        // Ficha ya vinculada (no reclamable).
        await setDoc(
            doc(database, "clientes", "742"),
            fichaCliente(742, negocioId, "cliente-ya-vinculado", "44444444D")
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, "44444444D")),
            { negocioId, dni: "44444444D", clienteId: 742 }
        );

        // Indice del MISMO dni pero en OTRO negocio (existe, para probar DENY).
        await setDoc(
            doc(database, "indices_clientes", indiceId("otro-negocio", dni)),
            { negocioId: "otro-negocio", dni, clienteId: 743 }
        );
    });

    // El CLIENTE declara su perfil pendiente con DNI + negocioId (VÃA 1) para
    // poder consultar el indice y reclamar su ficha.
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "perfiles_pendientes", clienteUid), {
            dni,
            negocioId
        });
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // Puede leer el indice de SU DNI + SU negocio (VÃA 1 declarado).
    await assertSucceeds(
        getDoc(doc(database, "indices_clientes", indiceId(negocioId, dni)))
    );
    // No puede leer el indice de otro DNI (mismo negocio).
    await assertFails(
        getDoc(doc(database, "indices_clientes", indiceId(negocioId, "44444444D")))
    );
    // No puede leer el indice del mismo DNI en OTRO negocio.
    await assertFails(
        getDoc(doc(database, "indices_clientes", indiceId("otro-negocio", dni)))
    );
    // No puede listar indices.
    await assertFails(
        getDocs(collection(database, "indices_clientes"))
    );

    // Caso invalido: vincularse sin actualizar usuarios/{uid} en el Batch
    // (la ficha sigue libre, el batch dejaria documentos incoherentes).
    const batchSinUsuario = writeBatch(database);
    batchSinUsuario.update(doc(database, "clientes", String(idFicha)), {
        firebaseUid: clienteUid,
        negocioId
    });
    await assertFails(batchSinUsuario.commit());

    // Caso invalido: reclamar una ficha que ya tiene UID.
    const batchYaVinculada = writeBatch(database);
    batchYaVinculada.update(doc(database, "clientes", "742"), {
        firebaseUid: clienteUid,
        negocioId
    });
    batchYaVinculada.update(doc(database, "usuarios", clienteUid), {
        clienteId: 742,
        negocioId
    });
    await assertFails(batchYaVinculada.commit());

    // Vinculacion valida: ficha libre + usuarios/{uid}.
    const batchVinculacion = writeBatch(database);
    batchVinculacion.update(doc(database, "clientes", String(idFicha)), {
        firebaseUid: clienteUid,
        negocioId
    });
    batchVinculacion.update(doc(database, "usuarios", clienteUid), {
        clienteId: idFicha,
        negocioId
    });

    await assertSucceeds(batchVinculacion.commit());

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const ficha = await getDoc(doc(database, "clientes", String(idFicha)));
        assert.strictEqual(ficha.data().firebaseUid, clienteUid);
    });
});

test("PRUEBA 6B: VIA 1 no puede actualizar una ficha inexistente sin indice", async () => {
    const clienteUid = "cliente-via1-ficha-inexistente-test";
    const negocioId = "negocio-via1-ficha-inexistente-6b";
    const dni = "88888888H";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        await setDoc(doc(database, "perfiles_pendientes", clienteUid), {
            dni,
            negocioId
        });
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    const batch = writeBatch(database);
    batch.update(doc(database, "clientes", "749"), {
        firebaseUid: clienteUid,
        negocioId
    });
    batch.update(doc(database, "usuarios", clienteUid), {
        clienteId: 749,
        negocioId
    });

    await assertFails(batch.commit());

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const ficha = await getDoc(doc(database, "clientes", "749"));
        const indice = await getDoc(
            doc(database, "indices_clientes", indiceId(negocioId, dni))
        );
        const usuario = await getDoc(doc(database, "usuarios", clienteUid));

        assert.strictEqual(ficha.exists(), false);
        assert.strictEqual(indice.exists(), false);
        assert.strictEqual(usuario.data().clienteId, null);
        assert.strictEqual(usuario.data().negocioId, null);
    });
});

test("PRUEBA 6C: cada DNI solo puede vincular su ficha correspondiente", async () => {
    const clienteUid = "cliente-via1-dni-a-test";
    const otroClienteUid = "cliente-via1-dni-b-test";
    const negocioId = "negocio-via1-dnis-6c";
    const dniA = "88888881A";
    const dniB = "88888882B";
    const idFichaA = 750;
    const idFichaB = 751;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "usuarios", otroClienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Via1 DNI",
            codigoMaestro: "MAESTRO-6C"
        });
        await setDoc(
            doc(database, "perfiles_pendientes", clienteUid),
            { dni: dniA, negocioId }
        );
        await setDoc(
            doc(database, "perfiles_pendientes", otroClienteUid),
            { dni: dniB, negocioId }
        );
        await setDoc(
            doc(database, "clientes", String(idFichaA)),
            fichaCliente(idFichaA, negocioId, null, dniA)
        );
        await setDoc(
            doc(database, "clientes", String(idFichaB)),
            fichaCliente(idFichaB, negocioId, null, dniB)
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, dniA)),
            { negocioId, dni: dniA, clienteId: idFichaA }
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, dniB)),
            { negocioId, dni: dniB, clienteId: idFichaB }
        );
    });

    const databaseClienteA = testEnvironment.authenticatedContext(clienteUid).firestore();
    const intentoIntercambio = writeBatch(databaseClienteA);
    intentoIntercambio.update(doc(databaseClienteA, "clientes", String(idFichaB)), {
        firebaseUid: clienteUid,
        negocioId
    });
    intentoIntercambio.update(doc(databaseClienteA, "usuarios", clienteUid), {
        clienteId: idFichaB,
        negocioId
    });
    await assertFails(intentoIntercambio.commit());

    const databaseClienteB = testEnvironment
        .authenticatedContext(otroClienteUid)
        .firestore();
    const vinculacionCorrecta = writeBatch(databaseClienteB);
    vinculacionCorrecta.update(
        doc(databaseClienteB, "clientes", String(idFichaB)),
        { firebaseUid: otroClienteUid, negocioId }
    );
    vinculacionCorrecta.update(doc(databaseClienteB, "usuarios", otroClienteUid), {
        clienteId: idFichaB,
        negocioId
    });
    await assertSucceeds(vinculacionCorrecta.commit());

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const fichaA = await getDoc(doc(database, "clientes", String(idFichaA)));
        const fichaB = await getDoc(doc(database, "clientes", String(idFichaB)));
        const usuarioA = await getDoc(doc(database, "usuarios", clienteUid));
        const usuarioB = await getDoc(doc(database, "usuarios", otroClienteUid));

        assert.strictEqual(fichaA.data().firebaseUid, null);
        assert.strictEqual(fichaB.data().firebaseUid, otroClienteUid);
        assert.strictEqual(usuarioA.data().clienteId, null);
        assert.strictEqual(usuarioB.data().clienteId, idFichaB);
    });
});

test("PRUEBA 7: un CLIENTE vinculado solo puede editar sus datos personales", async () => {
    const clienteUid = "cliente-personal-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 10,
            negocioId: NEGOCIO_A
        });

        await setDoc(
            doc(database, "clientes", "10"),
            fichaCliente(10, NEGOCIO_A, clienteUid, "11111111A", {
                estado: "ACTIVO",
                fechaAlta: 1,
                fechaBaja: null,
                fechaInicioActual: 1,
                fechaFinActual: 2,
                serviciosContratados: ["Servicio A"],
                nombre: "Cliente",
                apellidos: "Propio",
                dni: "11111111A",
                telefono: "600000000",
                email: "cliente@test.com",
                foto: "",
                fechaNacimiento: 0
            })
        );

        await setDoc(
            doc(database, "clientes", "20"),
            fichaCliente(20, NEGOCIO_A, "cliente-otro-test", "22222222B")
        );
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    await assertSucceeds(
        getDoc(doc(database, "clientes", "10"))
    );
    await assertFails(
        getDoc(doc(database, "clientes", "20"))
    );
    await assertFails(
        getDocs(collection(database, "clientes"))
    );
    await assertFails(
        updateDoc(doc(database, "clientes", "20"), { nombre: "Intento" })
    );

    const camposProtegidos = [
        { firebaseUid: "uid-alterado" },
        { negocioId: "otro-negocio" },
        { idCliente: 99 },
        { dni: "99999999Z" },
        { estado: "BAJA" },
        { fechaAlta: 3 },
        { fechaBaja: 4 },
        { fechaInicioActual: 3 },
        { fechaFinActual: 4 },
        { serviciosContratados: ["No contratado"] },
        { observaciones: "Intento de ver/editar observaciones" }
    ];

    for (const cambios of camposProtegidos) {
        await assertFails(
            updateDoc(doc(database, "clientes", "10"), cambios)
        );
    }

    await assertSucceeds(
        updateDoc(doc(database, "clientes", "10"), { nombre: "Nombre actualizado" })
    );
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "10"), { telefono: "699999999" })
    );
});

test("PRUEBA 8: un CLIENTE no accede a clientes_privados ni a perfiles ajenos", async () => {
    const clienteUid = "cliente-privado-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 10,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "clientes_privados", "10"), {
            negocioId: NEGOCIO_A,
            observaciones: "Nota interna del gimnasio"
        });

        await setDoc(doc(database, "perfiles_pendientes", "cliente-ajeno"), {
            nombre: "Ajeno",
            dni: "11111111A"
        });
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    await assertFails(
        getDoc(doc(database, "clientes_privados", "10"))
    );
    await assertFails(
        updateDoc(doc(database, "clientes_privados", "10"), { observaciones: "x" })
    );
    await assertFails(
        getDoc(doc(database, "perfiles_pendientes", "cliente-ajeno"))
    );
});

test("PRUEBA 9: el ADMIN gestiona clientes e indices de su negocio", async () => {
    const adminUid = "admin-escritura-test";
    const adminOtroUid = "admin-otro-escritura-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "usuarios", adminOtroUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_B
        });

        await setDoc(doc(database, "negocios", NEGOCIO_A), {
            adminUid,
            nombre: "Negocio A"
        });

        await setDoc(
            doc(database, "clientes", "31"),
            fichaCliente(31, NEGOCIO_A, "cliente-31", "31313131X")
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "31313131X")),
            { negocioId: NEGOCIO_A, dni: "31313131X", clienteId: 31 }
        );
        await setDoc(doc(database, "clientes_privados", "31"), {
            negocioId: NEGOCIO_A,
            observaciones: "Cliente 31"
        });
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    const databaseOtro = testEnvironment.authenticatedContext(adminOtroUid).firestore();

    // Crear una ficha nueva del propio negocio: clientes + indice + privados.
    const idNuevo = 32;
    const dniNuevo = "32323232Y";
    const batchCrear = writeBatch(database);
    batchCrear.set(
        doc(database, "clientes", String(idNuevo)),
        fichaCliente(idNuevo, NEGOCIO_A, null, dniNuevo)
    );
    batchCrear.set(
        doc(database, "indices_clientes", indiceId(NEGOCIO_A, dniNuevo)),
        { negocioId: NEGOCIO_A, dni: dniNuevo, clienteId: idNuevo }
    );
    batchCrear.set(doc(database, "clientes_privados", String(idNuevo)), {
        negocioId: NEGOCIO_A,
        observaciones: "Nuevo"
    });

    await assertSucceeds(batchCrear.commit());

    // Crear una ficha del negocio ajeno (NEGOCIO_A): denegado.
    const batchAjeno = writeBatch(databaseOtro);
    batchAjeno.set(
        doc(databaseOtro, "clientes", "33"),
        fichaCliente(33, NEGOCIO_A, null, "33333333C")
    );
    batchAjeno.set(
        doc(databaseOtro, "indices_clientes", indiceId(NEGOCIO_A, "33333333C")),
        { negocioId: NEGOCIO_A, dni: "33333333C", clienteId: 33 }
    );
    await assertFails(batchAjeno.commit());

    // Crear una ficha SIN indice: denegado (violaria unicidad).
    const batchSinIndice = writeBatch(database);
    batchSinIndice.set(
        doc(database, "clientes", "34"),
        fichaCliente(34, NEGOCIO_A, null, "34343434D")
    );
    await assertFails(batchSinIndice.commit());

    // Editar datos de gestion: permitido.
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "31"), { nombre: "Editado" })
    );

    // Editar observaciones via clientes_privados: permitido al ADMIN.
    await assertSucceeds(
        updateDoc(doc(database, "clientes_privados", "31"), { observaciones: "Nueva nota" })
    );

    // Borrar clientes: prohibido (baja logica).
    await assertFails(deleteDoc(doc(database, "clientes", "31")));
});

test("PRUEBA 9B: el ADMIN actualiza serviciosContratados de un cliente de su negocio -> ALLOW", async () => {
    const adminUid = "admin-servicios-test";
    const adminOtroUid = "admin-servicios-otro-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });

        await setDoc(doc(database, "usuarios", adminOtroUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_B
        });

        await setDoc(
            doc(database, "clientes", "31"),
            fichaCliente(31, NEGOCIO_A, null, "31313131X", {
                serviciosContratados: [1, 2, 5]
            })
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "31313131X")),
            { negocioId: NEGOCIO_A, dni: "31313131X", clienteId: 31 }
        );
        await setDoc(doc(database, "clientes_privados", "31"), {
            negocioId: NEGOCIO_A,
            observaciones: "Cliente 31"
        });
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    const databaseOtro = testEnvironment.authenticatedContext(adminOtroUid).firestore();

    // El ADMIN de su negocio puede sustituir la lista completa de servicios.
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "31"), { serviciosContratados: [1, 2, 5] })
    );
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "31"), { serviciosContratados: [5] })
    );
    // Lista vacia (quitar todos los servicios): permitido.
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "31"), { serviciosContratados: [] })
    );

    // Un ADMIN de otro negocio no puede tocar los servicios de esta ficha.
    await assertFails(
        updateDoc(doc(databaseOtro, "clientes", "31"), { serviciosContratados: [7] })
    );
});

test("PRUEBA 10: el ADMIN cambia el DNI manteniendo el indice atomico", async () => {
    const adminUid = "admin-cambiodni-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });

        await setDoc(
            doc(database, "clientes", "41"),
            fichaCliente(41, NEGOCIO_A, null, "41414141A")
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "41414141A")),
            { negocioId: NEGOCIO_A, dni: "41414141A", clienteId: 41 }
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();

    // Cambiar el DNI sin tocar el indice: denegado.
    await assertFails(
        updateDoc(doc(database, "clientes", "41"), { dni: "42424242B" })
    );

    // Cambiar el DNI con el indice atomico (borra el viejo, crea el nuevo).
    const batchCambioDni = writeBatch(database);
    batchCambioDni.update(doc(database, "clientes", "41"), { dni: "42424242B" });
    batchCambioDni.delete(
        doc(database, "indices_clientes", indiceId(NEGOCIO_A, "41414141A"))
    );
    batchCambioDni.set(
        doc(database, "indices_clientes", indiceId(NEGOCIO_A, "42424242B")),
        { negocioId: NEGOCIO_A, dni: "42424242B", clienteId: 41 }
    );

    await assertSucceeds(batchCambioDni.commit());

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const nuevo = await getDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "42424242B"))
        );
        const viejo = await getDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "41414141A"))
        );
        assert.strictEqual(nuevo.data().clienteId, 41);
        assert.ok(!viejo.exists());
    });
});

test("PRUEBA 11: un CLIENTE registrado gestiona su perfil pendiente", async () => {
    const clienteUid = "cliente-perfilpendiente-test";
    const otroUid = "cliente-perfilpendiente-otro";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "usuarios", otroUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    await assertSucceeds(
        setDoc(doc(database, "perfiles_pendientes", clienteUid), {
            nombre: "Ana",
            apellidos: "Lopez",
            dni: "11111111A",
            telefono: "611111111",
            email: "ana@test.com",
            foto: "",
            fechaNacimiento: 0
        })
    );

    await assertSucceeds(
        updateDoc(doc(database, "perfiles_pendientes", clienteUid), {
            telefono: "699999999"
        })
    );

    // No puede escribir el perfil pendiente de otro usuario.
    await assertFails(
        setDoc(doc(database, "perfiles_pendientes", otroUid), {
            nombre: "Hack",
            dni: "11111111A"
        })
    );

    // No puede listar perfiles pendientes.
    await assertFails(
        getDocs(collection(database, "perfiles_pendientes"))
    );

    await assertSucceeds(
        deleteDoc(doc(database, "perfiles_pendientes", clienteUid))
    );
});

test("PRUEBA 12: concurrencia - dos CLIENTES con el mismo DNI no duplican ficha", async () => {
    const clienteA = "cliente-race-a";
    const clienteB = "cliente-race-b";
    const dni = "55555555E";
    const negocioId = "negocio-race-12";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteA), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "usuarios", clienteB), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "perfiles_pendientes", clienteA), {
            dni, nombre: "A"
        });
        await setDoc(doc(database, "perfiles_pendientes", clienteB), {
            dni, nombre: "B"
        });
        await setDoc(doc(database, "negocios_publicos", negocioId), {
            codigoMaestro: "MAESTRO-12"
        });
    });

    const dbA = testEnvironment.authenticatedContext(clienteA).firestore();
    const dbB = testEnvironment.authenticatedContext(clienteB).firestore();

    // A crea la ficha + indice + usuarios.
    const batchA = writeBatch(dbA);
    batchA.set(
        doc(dbA, "clientes", "500"),
        fichaCliente(500, negocioId, clienteA, dni)
    );
    batchA.set(
        doc(dbA, "indices_clientes", indiceId(negocioId, dni)),
        { negocioId, dni, clienteId: 500 }
    );
    batchA.update(doc(dbA, "usuarios", clienteA), {
        clienteId: 500,
        negocioId
    });
    await assertSucceeds(batchA.commit());

    // B intenta crear su propia ficha con el MISMO indice: denegado.
    const batchB = writeBatch(dbB);
    batchB.set(
        doc(dbB, "clientes", "501"),
        fichaCliente(501, negocioId, clienteB, dni)
    );
    batchB.set(
        doc(dbB, "indices_clientes", indiceId(negocioId, dni)),
        { negocioId, dni, clienteId: 501 }
    );
    batchB.update(doc(dbB, "usuarios", clienteB), {
        clienteId: 501,
        negocioId
    });
    await assertFails(batchB.commit());

    // Verificar que solo existe una ficha con ese negocio+DNI.
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        const ficha = await getDoc(doc(database, "clientes", "500"));
        const fichaB = await getDoc(doc(database, "clientes", "501"));
        assert.strictEqual(ficha.data().firebaseUid, clienteA);
        assert.ok(!fichaB.exists());
    });
});

test("PRUEBA 13: un CLIENTE ya vinculado no puede volver a vincularse", async () => {
    const clienteUid = "cliente-repetido-13";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 600,
            negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", "600"),
            fichaCliente(600, NEGOCIO_A, clienteUid, "66666666F")
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(NEGOCIO_A, "66666666F")),
            { negocioId: NEGOCIO_A, dni: "66666666F", clienteId: 600 }
        );
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // No puede re-vincularse (usuarios/{uid}.clienteId ya no es null).
    const batchReintento = writeBatch(database);
    batchReintento.update(doc(database, "usuarios", clienteUid), {
        clienteId: 601,
        negocioId: NEGOCIO_A
    });
    await assertFails(batchReintento.commit());
});

test("PRUEBA 14: el ADMIN puede leer y listar clientes_privados; CLIENTE nunca", async () => {
    const adminUid = "admin-privados-test";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes_privados", "70"), {
            negocioId: NEGOCIO_A,
            observaciones: "Nota"
        });
        await setDoc(doc(database, "clientes_privados", "71"), {
            negocioId: NEGOCIO_B,
            observaciones: "Nota B"
        });
    });

    const dbAdmin = testEnvironment.authenticatedContext(adminUid).firestore();
    const dbCliente = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();

    await assertSucceeds(getDoc(doc(dbAdmin, "clientes_privados", "70")));
    await assertFails(getDoc(doc(dbAdmin, "clientes_privados", "71")));
    await assertFails(getDoc(doc(dbCliente, "clientes_privados", "70")));
});

test("PRUEBA 15: negocios_publicos es legible por cualquier autenticado", async () => {
    const negocioId = "negocio-publico-15";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Publico",
            codigoMaestro: "MAESTRO-15"
        });
    });

    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();

    await assertSucceeds(
        getDoc(doc(database, "negocios_publicos", negocioId))
    );

    const databaseAnonima = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(
        getDoc(doc(databaseAnonima, "negocios_publicos", negocioId))
    );
});

test("PRUEBA 16: un CLIENTE no puede modificar negocios_publicos", async () => {
    const negocioId = "negocio-publico-16";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Ajeno",
            codigoMaestro: "MAESTRO-16"
        });
    });

    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();

    await assertFails(
        updateDoc(doc(database, "negocios_publicos", negocioId), {
            codigoMaestro: "CODIGO-MALICIOSO"
        })
    );
    await assertFails(
        setDoc(doc(database, "negocios_publicos", "negocio-falso-16"), {
            nombre: "Falso",
            codigoMaestro: "FALSO"
        })
    );
    await assertFails(
        deleteDoc(doc(database, "negocios_publicos", negocioId))
    );
});

test("PRUEBA 17: VIA 1 - la lectura del indice exige dni y negocioId declarados en perfiles_pendientes", async () => {
    const clienteUid = "cliente-via1-decl-test";
    const negocioId = "negocio-via1-decl";
    const otroNegocioId = "otro-negocio-via1-decl";
    const dni = "77777777G";
    const idFicha = 780;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });

        await setDoc(
            doc(database, "clientes", String(idFicha)),
            fichaCliente(idFicha, negocioId, null, dni)
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, dni)),
            { negocioId, dni, clienteId: idFicha }
        );
        await setDoc(
            doc(database, "indices_clientes", indiceId(otroNegocioId, dni)),
            { negocioId: otroNegocioId, dni, clienteId: 781 }
        );
        // Indice existente con OTRO dni en el mismo negocio (para probar DENY).
        await setDoc(
            doc(database, "indices_clientes", indiceId(negocioId, "88888888H")),
            { negocioId, dni: "88888888H", clienteId: 782 }
        );
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // 1. VIA 1 valida: declara { dni, negocioId } y puede leer el indice.
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "perfiles_pendientes", clienteUid), {
            dni,
            negocioId
        });
    });
    await assertSucceeds(
        getDoc(doc(database, "indices_clientes", indiceId(negocioId, dni)))
    );

    // 2. DNI distinto al declarado -> DENY.
    await assertFails(
        getDoc(doc(database, "indices_clientes", indiceId(negocioId, "88888888H")))
    );

    // 3/4. indice del MISMO dni pero de OTRO negocio (no declarado) -> DENY.
    await assertFails(
        getDoc(doc(database, "indices_clientes", indiceId(otroNegocioId, dni)))
    );

    // 5. list de indices_clientes -> DENY.
    await assertFails(
        getDocs(collection(database, "indices_clientes"))
    );

    // 6. Cambiar la declaracion a otro negocio permite leer ese indice (no el anterior).
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "perfiles_pendientes", clienteUid), {
            dni,
            negocioId: otroNegocioId
        });
    });
    await assertSucceeds(
        getDoc(doc(database, "indices_clientes", indiceId(otroNegocioId, dni)))
    );
    await assertFails(
        getDoc(doc(database, "indices_clientes", indiceId(negocioId, dni)))
    );

    // 7. El CLIENTE puede borrar su perfil pendiente (tras vincular o rechazar).
    await assertSucceeds(
        deleteDoc(doc(database, "perfiles_pendientes", clienteUid))
    );
});

test("PRUEBA 18: VIA 1 - el CLIENTE no vinculado solo lee la ficha que declaro", async () => {
    const clienteUid = "cliente-via1-lectura-test";
    const clienteVinculadoUid = "cliente-via1-vinculado-test";
    const negocioId = "negocio-via1-lectura";
    const otroNegocioId = "otro-negocio-via1-lectura";
    const dni = "99999999Z";
    const idFicha = 800;
    const idOtraFicha = 801;
    const idFichaVinculado = 802;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();

        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
        await setDoc(doc(database, "usuarios", clienteVinculadoUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: idFichaVinculado,
            negocioId
        });

        // Ficha libre del negocio declarado.
        await setDoc(
            doc(database, "clientes", String(idFicha)),
            fichaCliente(idFicha, negocioId, null, dni)
        );
        // Ficha de OTRO DNI en el mismo negocio.
        await setDoc(
            doc(database, "clientes", String(idOtraFicha)),
            fichaCliente(idOtraFicha, negocioId, null, "88888888X")
        );
        // Ficha de otro negocio con el mismo DNI.
        await setDoc(
            doc(database, "clientes", "805"),
            fichaCliente(805, otroNegocioId, null, dni)
        );
        // Ficha propia de un CLIENTE ya vinculado.
        await setDoc(
            doc(database, "clientes", String(idFichaVinculado)),
            fichaCliente(idFichaVinculado, negocioId, clienteVinculadoUid, "77777777W")
        );
    });

    // CLIENTE sin vÃ­nculo con declaracion correcta.
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "perfiles_pendientes", clienteUid), {
            dni,
            negocioId
        });
    });

    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // 1. Declaracion correcta + ficha correspondiente -> ALLOW.
    await assertSucceeds(
        getDoc(doc(database, "clientes", String(idFicha)))
    );

    // 2. Ficha de OTRO DNI en el mismo negocio -> DENY.
    await assertFails(
        getDoc(doc(database, "clientes", String(idOtraFicha)))
    );

    // 3. Ficha del MISMO dni en OTRO negocio -> DENY.
    await assertFails(
        getDoc(doc(database, "clientes", "805"))
    );

    // 4. CLIENTE ya vinculado intentando leer ficha ajena -> DENY.
    const dbVinculado = testEnvironment.authenticatedContext(clienteVinculadoUid).firestore();
    await assertFails(
        getDoc(doc(dbVinculado, "clientes", String(idFicha)))
    );

    // 5. CLIENTE no vinculado SIN perfiles_pendientes -> DENY.
    const clienteSinDeclaracion = "cliente-via1-sin-declaracion-test";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", clienteSinDeclaracion), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: null
        });
    });
    const dbSinDecl = testEnvironment.authenticatedContext(clienteSinDeclaracion).firestore();
    await assertFails(
        getDoc(doc(dbSinDecl, "clientes", String(idFicha)))
    );

    // 6. list de clientes -> DENY (el CLIENTE no puede enumerar).
    await assertFails(
        getDocs(collection(database, "clientes"))
    );

    // 7. CLIENTE vinculado sigue leyendo SOLO su propia ficha.
    await assertSucceeds(
        getDoc(doc(dbVinculado, "clientes", String(idFichaVinculado)))
    );
    await assertFails(
        getDoc(doc(dbVinculado, "clientes", "805"))
    );
});

test("PRUEBA 19: Storage - el ADMIN propietario sube su logo y el resto no puede", async () => {
    const adminA = "admin-logo-a";
    const adminB = "admin-logo-b";
    const clienteUid = "cliente-logo-storage";
    const negocioA = "negocio-logo-a";
    const ruta = "negocios/negocio-logo-a/logo.jpg";
    const bytes = new Uint8Array([1, 2, 3, 4]);

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminA), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: negocioA
        });
        await setDoc(doc(database, "usuarios", adminB), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: "negocio-logo-b"
        });
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId: negocioA
        });
    });

    const storageAdminA = testEnvironment.authenticatedContext(adminA).storage();
    const storageAdminB = testEnvironment.authenticatedContext(adminB).storage();
    const storageCliente = testEnvironment.authenticatedContext(clienteUid).storage();
    const storageNoAuth = testEnvironment.unauthenticatedContext().storage();

    // 1. ADMIN propietario puede subir su logo.
    await assertSucceeds(
        uploadBytes(storageRef(storageAdminA, ruta), bytes)
    );

    // 2. ADMIN de otro negocio no puede escribir en ese logo.
    await assertFails(
        uploadBytes(storageRef(storageAdminB, ruta), bytes)
    );

    // 3. CLIENTE no puede escribir el logo.
    await assertFails(
        uploadBytes(storageRef(storageCliente, ruta), bytes)
    );

    // 4. Usuario no autenticado no puede escribir.
    await assertFails(
        uploadBytes(storageRef(storageNoAuth, ruta), bytes)
    );

    // 5. CLIENTE autenticado puede leer el logo.
    await assertSucceeds(
        getBytes(storageRef(storageCliente, ruta))
    );

    // 6. Usuario no autenticado no puede leer.
    await assertFails(
        getBytes(storageRef(storageNoAuth, ruta))
    );

    // 7. El ADMIN propietario puede reemplazar su logo (misma ruta).
    await assertSucceeds(
        uploadBytes(storageRef(storageAdminA, ruta), bytes)
    );

    // 8. El ADMIN propietario puede leer su logo.
    await assertSucceeds(
        getBytes(storageRef(storageAdminA, ruta))
    );
});

test("PRUEBA 19-STOR-FOTO-ADMIN: fotos de cliente - ADMIN propietario sube/reemplaza/lee/elimina y otro ADMIN no", async () => {
    const adminA = "admin-foto-a";
    const adminB = "admin-foto-b";
    const negocioA = "negocio-foto-a";
    const negocioB = "negocio-foto-b";
    const idCliente = "cliente-foto-1";
    const ruta = `clientes/${idCliente}/foto.jpg`;
    const bytes = new Uint8Array([1, 2, 3, 4]);
    const metadatosImagen = { contentType: "image/jpeg" };

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminA), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: negocioA
        });
        await setDoc(doc(database, "usuarios", adminB), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: negocioB
        });
        await setDoc(doc(database, "clientes", idCliente), {
            idCliente: parseInt(idCliente.replace(/\D/g, ""), 10),
            negocioId: negocioA,
            firebaseUid: null,
            nombre: "Cliente", apellidos: "Foto", dni: "12345678F",
            telefono: "600000000", email: "c@test.com", foto: "",
            estado: "ACTIVO", serviciosContratados: []
        });
    });

    const storageAdminA = testEnvironment.authenticatedContext(adminA).storage();
    const storageAdminB = testEnvironment.authenticatedContext(adminB).storage();

    // ADMIN propietario: subir y reemplazar.
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, ruta), bytes, metadatosImagen));
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, ruta), bytes, metadatosImagen));
    // ADMIN propietario: leer y eliminar.
    await assertSucceeds(getBytes(storageRef(storageAdminA, ruta)));
    await assertSucceeds(deleteObject(storageRef(storageAdminA, ruta)));

    // ADMIN de otro negocio: no puede leer, escribir ni eliminar.
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, ruta), bytes, metadatosImagen));
    await assertFails(getBytes(storageRef(storageAdminB, ruta)));
    await assertFails(uploadBytes(storageRef(storageAdminB, ruta), bytes, metadatosImagen));
    await assertFails(deleteObject(storageRef(storageAdminB, ruta)));
});

test("PRUEBA 19-STOR-FOTO-ALTA (doc-first): con la ficha EXISTENTE el ADMIN sube/lee/borra la foto; SIN ficha no hay autorizaciÃ³n; CLIENTE y no-auth no pueden", async () => {
    // negocioId del ADMIN = su UID (igual que producciÃ³n).
    const uidA = "admin-foto-alta-a";
    const uidB = "admin-foto-alta-b";
    const negocioA = uidA;
    const negocioB = uidB;
    const clienteUid = "cliente-foto-alta-x";
    // idNuevo: en el alta doc-first la ficha SE CREA ANTES de subir (foto="").
    const idNuevo = "cliente-foto-alta-nuevo";
    const idNuevoB = "cliente-foto-alta-nuevo-b";
    const ruta = `clientes/${idNuevo}/foto.jpg`;
    const rutaB = `clientes/${idNuevoB}/foto.jpg`;
    const bytes = new Uint8Array([15, 16, 17, 18]);
    const mdImagen = { contentType: "image/jpeg" };

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", uidA), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: negocioA
        });
        await setDoc(doc(database, "usuarios", uidB), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: negocioB
        });
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: null, negocioId: negocioA
        });
        // Fichas reales (alta doc-first: existen con foto="" antes de subir).
        await setDoc(doc(database, "clientes", idNuevo), {
            idCliente: 1, negocioId: negocioA, firebaseUid: null,
            nombre: "Alta", apellidos: "DocFirst", dni: "44444444Z",
            telefono: "600000000", email: "a@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
        await setDoc(doc(database, "clientes", idNuevoB), {
            idCliente: 2, negocioId: negocioB, firebaseUid: null,
            nombre: "AltaB", apellidos: "DocFirstB", dni: "55555555B",
            telefono: "600000000", email: "b@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
        await setDoc(doc(database, "clientes", "cliente-foto-alta-existente"), {
            idCliente: 999991, negocioId: negocioA, firebaseUid: null,
            nombre: "Existente", apellidos: "Alta", dni: "33333333E",
            telefono: "600000000", email: "e@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
        // Ficha SIN DOCUMENTO en Firestore: su foto NO debe poderse subir.
    });

    const storageAdminA = testEnvironment.authenticatedContext(uidA).storage();
    const storageAdminB = testEnvironment.authenticatedContext(uidB).storage();
    const storageCliente = testEnvironment.authenticatedContext(clienteUid).storage();
    const storageNoAuth = testEnvironment.unauthenticatedContext().storage();

    // ALTA doc-first: con la ficha YA existente el ADMIN sube la foto y la lee.
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, ruta), bytes, mdImagen));
    await assertSucceeds(getBytes(storageRef(storageAdminA, ruta)));

    // El ADMIN puede eliminar el objeto (compensaciÃ³n/reemplazo).
    await assertSucceeds(deleteObject(storageRef(storageAdminA, ruta)));
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, ruta), bytes, mdImagen));

    // CLIENTE (no propietario de esa ficha): no puede subir, leer ni borrar.
    await assertFails(uploadBytes(storageRef(storageCliente, ruta), bytes, mdImagen));
    await assertFails(getBytes(storageRef(storageCliente, ruta)));
    await assertFails(deleteObject(storageRef(storageCliente, ruta)));

    // No autenticado: no puede leer ni escribir.
    await assertFails(getBytes(storageRef(storageNoAuth, ruta)));
    await assertFails(uploadBytes(storageRef(storageNoAuth, ruta), bytes, mdImagen));

    // ADMIN de OTRO negocio: no puede leer, escribir ni borrar la de negocioA.
    await assertFails(getBytes(storageRef(storageAdminB, ruta)));
    await assertFails(uploadBytes(storageRef(storageAdminB, ruta), bytes, mdImagen));
    await assertFails(deleteObject(storageRef(storageAdminB, ruta)));

    // adminB SÃ puede gestionar la foto de SU ficha (idNuevoB).
    await assertSucceeds(uploadBytes(storageRef(storageAdminB, rutaB), bytes, mdImagen));
    await assertSucceeds(getBytes(storageRef(storageAdminB, rutaB)));

    // SIN ficha en Firestore: el ADMIN NO tiene autorizaciÃ³n para subir
    // (el alta ya no depende de metadata ni de autorizaciones temporales).
    const rutaSinFicha = "clientes/cliente-foto-sin-ficha/foto.jpg";
    await assertFails(uploadBytes(storageRef(storageAdminA, rutaSinFicha), bytes, mdImagen));
    await assertFails(getBytes(storageRef(storageAdminA, rutaSinFicha)));

    // Ficha EXISTENTE de negocioA: el ADMIN propietario lee/subiere igual.
    const rutaExistente = "clientes/cliente-foto-alta-existente/foto.jpg";
    await assertSucceeds(uploadBytes(storageRef(storageAdminA, rutaExistente), bytes, mdImagen));
    await assertFails(uploadBytes(storageRef(storageAdminB, rutaExistente), bytes, mdImagen));
    await assertFails(deleteObject(storageRef(storageAdminB, rutaExistente)));
    await assertFails(getBytes(storageRef(storageAdminB, rutaExistente)));
    await assertSucceeds(getBytes(storageRef(storageAdminA, rutaExistente)));
});

test("PRUEBA 19-STOR-FOTO-CLIENTE: fotos de cliente - el CLIENTE propietario gestiona la suya y otro CLIENTE no", async () => {
    const duenioUid = "cliente-dueno-2";
    const otroUid = "cliente-otro-2";
    const negocioX = "negocio-foto-x";
    const idPropia = "cliente-foto-propia";
    const idAjena = "cliente-foto-ajena";
    const ruta = `clientes/${idPropia}/foto.jpg`;
    const rutaAjena = `clientes/${idAjena}/foto.jpg`;
    const bytes = new Uint8Array([5, 6, 7, 8]);
    const metadatosImagen = { contentType: "image/jpeg" };

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", duenioUid), {
            rol: "CLIENTE", activo: true, clienteId: parseInt(idPropia.replace(/\D/g, ""), 10),
            negocioId: negocioX
        });
        await setDoc(doc(database, "usuarios", otroUid), {
            rol: "CLIENTE", activo: true, clienteId: parseInt(idAjena.replace(/\D/g, ""), 10),
            negocioId: negocioX
        });
        await setDoc(doc(database, "clientes", idPropia), {
            idCliente: parseInt(idPropia.replace(/\D/g, ""), 10),
            negocioId: negocioX, firebaseUid: duenioUid,
            nombre: "DueÃ±o", apellidos: "Foto", dni: "11111111D",
            telefono: "600000000", email: "d@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
        await setDoc(doc(database, "clientes", idAjena), {
            idCliente: parseInt(idAjena.replace(/\D/g, ""), 10),
            negocioId: negocioX, firebaseUid: otroUid,
            nombre: "Otro", apellidos: "Foto", dni: "22222222O",
            telefono: "600000000", email: "o@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
    });

    const storageDuenio = testEnvironment.authenticatedContext(duenioUid).storage();
    const storageOtro = testEnvironment.authenticatedContext(otroUid).storage();

    // CLIENTE propietario: subir, reemplazar, leer y eliminar su foto.
    await assertSucceeds(uploadBytes(storageRef(storageDuenio, ruta), bytes, metadatosImagen));
    await assertSucceeds(uploadBytes(storageRef(storageDuenio, ruta), bytes, metadatosImagen));
    await assertSucceeds(getBytes(storageRef(storageDuenio, ruta)));
    await assertSucceeds(deleteObject(storageRef(storageDuenio, ruta)));

    // CLIENTE de otro cliente: no puede leer, escribir ni eliminar la ajena.
    await assertFails(getBytes(storageRef(storageOtro, ruta)));
    await assertFails(uploadBytes(storageRef(storageOtro, ruta), bytes, metadatosImagen));
    await assertFails(deleteObject(storageRef(storageOtro, ruta)));
});

test("PRUEBA 19-STOR-FOTO-VALIDACION: no autenticado, tamaÃ±o >10MB, no imagen y rutas fuera quedan bloqueadas", async () => {
    const adminUid = "admin-foto-val";
    const negocioId = "negocio-foto-val";
    const idCliente = "cliente-foto-val";
    const ruta = `clientes/${idCliente}/foto.jpg`;
    const rutaFuera = `clientes/${idCliente}/otra.jpg`;
    const rutaLogoFuera = "negocios/negocio-foto-val/otro.jpg";
    const bytes = new Uint8Array([9, 10, 11, 12]);

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(doc(database, "clientes", idCliente), {
            idCliente: parseInt(idCliente.replace(/\D/g, ""), 10),
            negocioId, firebaseUid: null,
            nombre: "Val", apellidos: "Foto", dni: "12345678V",
            telefono: "600000000", email: "v@test.com", foto: "", estado: "ACTIVO",
            serviciosContratados: []
        });
        // Objeto de prueba para comprobar DELETE sin autenticar.
        await context.storage().ref().child(ruta).put(bytes, { contentType: "image/jpeg" });
        await context.storage().ref().child(rutaFuera).put(bytes, { contentType: "image/jpeg" });
        await context.storage().ref().child(rutaLogoFuera).put(bytes, { contentType: "image/jpeg" });
    });

    const storageAdmin = testEnvironment.authenticatedContext(adminUid).storage();
    const storageNoAuth = testEnvironment.unauthenticatedContext().storage();
    const grande = new Uint8Array(10 * 1024 * 1024 + 1).fill(13);

    // No autenticado: no puede leer, escribir ni eliminar.
    await assertFails(getBytes(storageRef(storageNoAuth, ruta)));
    await assertFails(uploadBytes(storageRef(storageNoAuth, ruta), bytes, { contentType: "image/jpeg" }));
    await assertFails(deleteObject(storageRef(storageNoAuth, ruta)));

    // Archivo > 10 MB rechazado.
    await assertFails(uploadBytes(storageRef(storageAdmin, ruta), grande, { contentType: "image/jpeg" }));

    // Archivo que no es imagen rechazado.
    await assertFails(uploadBytes(storageRef(storageAdmin, ruta), bytes, { contentType: "text/plain" }));

    // Rutas fuera de las permitidas bloqueadas (incluso por el ADMIN propietario).
    await assertFails(uploadBytes(storageRef(storageAdmin, rutaFuera), bytes, { contentType: "image/jpeg" }));
    await assertFails(getBytes(storageRef(storageAdmin, rutaFuera)));
    await assertFails(uploadBytes(storageRef(storageAdmin, rutaLogoFuera), bytes, { contentType: "image/jpeg" }));
});

test("PRUEBA 20: el ADMIN guarda el logo en negocios y negocios_publicos; el CLIENTE no", async () => {
    const adminUid = "admin-logo-firestore";
    const clienteUid = "cliente-logo-firestore";
    const negocioId = "negocio-logo-firestore";
    const url = "https://firebasestorage.googleapis.com/v0/b/x/o/logo.jpg";

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId
        });
        await setDoc(doc(database, "negocios", negocioId), {
            adminUid,
            nombre: "Gimnasio",
            codigoMaestro: "MAESTRO-LOGO"
        });
        await setDoc(doc(database, "negocios_publicos", negocioId), {
            nombre: "Gimnasio",
            codigoMaestro: "MAESTRO-LOGO"
        });
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: null,
            negocioId
        });
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();

    // El ADMIN puede aÃ±adir el logo a negocios_publicos.
    await assertSucceeds(
        updateDoc(doc(database, "negocios_publicos", negocioId), { logo: url })
    );

    // El ADMIN puede aÃ±adir el logo a negocios.
    await assertSucceeds(
        updateDoc(doc(database, "negocios", negocioId), { logo: url })
    );

    // Un CLIENTE no puede modificar negocios_publicos (ni el logo).
    const dbCliente = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        updateDoc(doc(dbCliente, "negocios_publicos", negocioId), { logo: url })
    );
});

test("PRUEBA 21: el ADMIN crea un servicio de su negocio -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "servicios", "100"), servicioDoc(100, NEGOCIO_A))
    );
});

test("PRUEBA 21B: el ADMIN crea un servicio con precio numÃ©rico -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "servicios", "104"), servicioDoc(104, NEGOCIO_A, { precio: 12.5 }))
    );
});

test("PRUEBA 21C: el ADMIN no puede crear un servicio con precio no numÃ©rico -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "servicios", "105"), servicioDoc(105, NEGOCIO_A, { precio: "treinta" }))
    );
});

test("PRUEBA 22: el ADMIN no puede crear un servicio indicando otro negocio -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "servicios", "101"), servicioDoc(101, NEGOCIO_B))
    );
});

test("PRUEBA 23: el ADMIN lee su servicio -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "servicios", "102"),
            servicioDoc(102, NEGOCIO_A)
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "servicios", "102"))
    );
});

test("PRUEBA 24: el ADMIN no puede leer un servicio de otro negocio -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "servicios", "103"),
            servicioDoc(103, NEGOCIO_B)
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        getDoc(doc(database, "servicios", "103"))
    );
});

test("PRUEBA 25: el ADMIN modifica su servicio -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "servicios", "102"), { nombre: "Nuevo nombre" })
    );
});

test("PRUEBA 25B: el ADMIN actualiza el precio de su servicio -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "servicios", "102"), { precio: 35 })
    );
});

test("PRUEBA 25C: el ADMIN no puede poner un precio no numÃ©rico al actualizar -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "servicios", "102"), { precio: "35" })
    );
});

test("PRUEBA 26: el ADMIN no puede modificar un servicio de otro negocio -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "servicios", "103"), { nombre: "Hack" })
    );
});

test("PRUEBA 27: el ADMIN elimina su servicio -> ALLOW", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        deleteDoc(doc(database, "servicios", "102"))
    );
});

test("PRUEBA 28: el ADMIN no puede eliminar un servicio de otro negocio -> DENY", async () => {
    const adminUid = "admin-servicios-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        deleteDoc(doc(database, "servicios", "103"))
    );
});

test("PRUEBA 29: un CLIENTE no puede leer servicios -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        getDoc(doc(database, "servicios", "100"))
    );
});

test("PRUEBA 30: un CLIENTE no puede crear servicios -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        setDoc(doc(database, "servicios", "200"), servicioDoc(200, NEGOCIO_A))
    );
});

test("PRUEBA 31: un CLIENTE no puede modificar servicios -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        updateDoc(doc(database, "servicios", "100"), { nombre: "Hack" })
    );
});

test("PRUEBA 32: un CLIENTE no puede eliminar servicios -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        deleteDoc(doc(database, "servicios", "100"))
    );
});

test("PRUEBA 33: usuario no autenticado no puede acceder a servicios -> DENY", async () => {
    const database = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(
        getDoc(doc(database, "servicios", "100"))
    );
});

test("PRUEBA 33A: el ADMIN no puede listar sesiones solo por idServicio -> DENY", async () => {
    const adminUid = "admin-query-sesiones-solo-id";
    const negocioId = "negocio-query-sesiones";
    const idServicio = 1100;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "sesiones", "1100"),
            sesionDoc(1100, negocioId, idServicio)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        getDocs(
            query(
                collection(database, "sesiones"),
                where("idServicio", "==", idServicio)
            )
        )
    );
});

test("PRUEBA 33B: el ADMIN lista sesiones por idServicio y negocioId -> ALLOW", async () => {
    const adminUid = "admin-query-sesiones-con-negocio";
    const negocioId = "negocio-query-sesiones-con-negocio";
    const idServicio = 1101;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "sesiones", "1101"),
            sesionDoc(1101, negocioId, idServicio)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDocs(
            query(
                collection(database, "sesiones"),
                where("idServicio", "==", idServicio),
                where("negocioId", "==", negocioId)
            )
        )
    );
});

test("PRUEBA 33C: el ADMIN no puede listar reservas solo por sesionId -> DENY", async () => {
    const adminUid = "admin-query-reservas-solo-sesion";
    const negocioId = "negocio-query-reservas";
    const sesionId = 1102;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "reservas", "9000_1102"),
            reservaDoc(9000, sesionId, negocioId)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        getDocs(
            query(
                collection(database, "reservas"),
                where("sesionId", "==", sesionId)
            )
        )
    );
});

test("PRUEBA 33D: el ADMIN lista reservas por sesionId y negocioId -> ALLOW", async () => {
    const adminUid = "admin-query-reservas-con-negocio";
    const negocioId = "negocio-query-reservas-con-negocio";
    const sesionId = 1103;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "reservas", "9001_1103"),
            reservaDoc(9001, sesionId, negocioId)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDocs(
            query(
                collection(database, "reservas"),
                where("sesionId", "==", sesionId),
                where("negocioId", "==", negocioId)
            )
        )
    );
});

test("PRUEBA 33E: la transaccion Android elimina una reserva y su sesion", async () => {
    const adminUid = "admin-transaccion-android";
    const negocioId = "negocio-transaccion-android";
    const sesionId = 1104;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "sesiones", String(sesionId)),
            sesionDoc(sesionId, negocioId, 1104)
        );
        await setDoc(
            doc(database, "reservas", `9002_${sesionId}`),
            reservaDoc(9002, sesionId, negocioId)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        runTransaction(database, async (transaction) => {
            await transaction.get(doc(database, "sesiones", String(sesionId)));
            transaction.delete(doc(database, "reservas", `9002_${sesionId}`));
            transaction.delete(doc(database, "sesiones", String(sesionId)));
        })
    );
});

test("PRUEBA 33F: la transaccion Android elimina varias reservas de una sesion", async () => {
    const adminUid = "admin-transaccion-varias-reservas";
    const negocioId = "negocio-transaccion-varias-reservas";
    const sesionId = 1105;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
        await setDoc(
            doc(database, "sesiones", String(sesionId)),
            sesionDoc(sesionId, negocioId, 1105)
        );
        await setDoc(
            doc(database, "reservas", `9003_${sesionId}`),
            reservaDoc(9003, sesionId, negocioId)
        );
        await setDoc(
            doc(database, "reservas", `9004_${sesionId}`),
            reservaDoc(9004, sesionId, negocioId)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        runTransaction(database, async (transaction) => {
            await transaction.get(doc(database, "sesiones", String(sesionId)));
            transaction.delete(doc(database, "reservas", `9003_${sesionId}`));
            transaction.delete(doc(database, "reservas", `9004_${sesionId}`));
            transaction.delete(doc(database, "sesiones", String(sesionId)));
        })
    );
});

test("PRUEBA 33G: el ADMIN puede actualizar solo activo manteniendo la Rule estricta", async () => {
    const adminUid = "admin-update-activo";
    const negocioId = "negocio-update-activo";
    const idServicio = 1106;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "usuarios", adminUid),
            { rol: "ADMIN", activo: true, clienteId: null, negocioId }
        );
        await setDoc(
            doc(context.firestore(), "servicios", String(idServicio)),
            servicioDoc(idServicio, negocioId)
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "servicios", String(idServicio)), { activo: false })
    );
});

test("PRUEBA 33H: actualizar un servicio incompleto -> DENY", async () => {
    const adminUid = "admin-update-servicio-incompleto";
    const negocioId = "negocio-update-servicio-incompleto";
    const idServicio = 1107;

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "usuarios", adminUid),
            { rol: "ADMIN", activo: true, clienteId: null, negocioId }
        );
        await setDoc(
            doc(context.firestore(), "servicios", String(idServicio)),
            { idServicio, negocioId, activo: true }
        );
    });

    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "servicios", String(idServicio)), { activo: false })
    );
});

test("PRUEBA 34: el ADMIN crea una sesion de su servicio activo -> ALLOW", async () => {
    const adminUid = "admin-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "1000"), servicioDoc(1000, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "sesiones", "500"), sesionDoc(500, NEGOCIO_A, 1000))
    );
});

test("PRUEBA 35: el ADMIN no puede crear una sesion de un servicio de otro negocio -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "servicios", "1001"), servicioDoc(1001, NEGOCIO_B));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "sesiones", "501"), sesionDoc(501, NEGOCIO_A, 1001))
    );
});

test("PRUEBA 36: el ADMIN no puede crear una sesion para un servicio inexistente -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "sesiones", "504"), sesionDoc(504, NEGOCIO_A, 999999))
    );
});

test("PRUEBA 37: el ADMIN no puede crear una sesion para un servicio inactivo -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "servicios", "1002"),
            servicioDoc(1002, NEGOCIO_A, { activo: false })
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "sesiones", "505"), sesionDoc(505, NEGOCIO_A, 1002))
    );
});

test("PRUEBA 38: el ADMIN lee una sesion de su negocio -> ALLOW", async () => {
    const adminUid = "admin-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "506"),
            sesionDoc(506, NEGOCIO_A, 1000)
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "sesiones", "506"))
    );
});

test("PRUEBA 39: el ADMIN no puede leer una sesion de otro negocio -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "507"),
            sesionDoc(507, NEGOCIO_B, 1001)
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "507"))
    );
});

test("PRUEBA 40: el ADMIN modifica su sesion -> ALLOW", async () => {
    const adminUid = "admin-sesiones-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "sesiones", "506"), { hora: "19:30" })
    );
});

test("PRUEBA 41: el ADMIN no puede modificar una sesion de otro negocio -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "sesiones", "507"), { hora: "19:30" })
    );
});

test("PRUEBA 42: el ADMIN elimina su sesion -> ALLOW", async () => {
    const adminUid = "admin-sesiones-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        deleteDoc(doc(database, "sesiones", "506"))
    );
});

test("PRUEBA 43: el ADMIN no puede eliminar una sesion de otro negocio -> DENY", async () => {
    const adminUid = "admin-sesiones-a";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        deleteDoc(doc(database, "sesiones", "507"))
    );
});

test("PRUEBA 44: un CLIENTE no puede crear sesiones -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        setDoc(doc(database, "sesiones", "508"), sesionDoc(508, NEGOCIO_A, 1000))
    );
});

test("PRUEBA 45: un CLIENTE no puede modificar sesiones -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        updateDoc(doc(database, "sesiones", "500"), { hora: "20:00" })
    );
});

test("PRUEBA 46: un CLIENTE no puede eliminar sesiones -> DENY", async () => {
    const database = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertFails(
        deleteDoc(doc(database, "sesiones", "500"))
    );
});

test("PRUEBA 47: usuario no autenticado no puede acceder a sesiones -> DENY", async () => {
    const database = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "500"))
    );
});

test("PRUEBA 48: un CLIENTE con el servicio contratado y activo lee la sesion -> ALLOW", async () => {
    const clienteUid = "cliente-sesiones-a";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 700,
            negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", "700"),
            fichaCliente(700, NEGOCIO_A, clienteUid, "77777700X", {
                serviciosContratados: [1000]
            })
        );
        await setDoc(
            doc(database, "sesiones", "509"),
            sesionDoc(509, NEGOCIO_A, 1000)
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "sesiones", "509"))
    );
});

test("PRUEBA 49: un CLIENTE sin servicios contratados no lee la sesion -> DENY", async () => {
    const clienteUid = "cliente-sesiones-vacio";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 701,
            negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", "701"),
            fichaCliente(701, NEGOCIO_A, clienteUid, "77777701X", {
                serviciosContratados: []
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "509"))
    );
});

test("PRUEBA 50: un CLIENTE con otro servicio contratado no lee la sesion -> DENY", async () => {
    const clienteUid = "cliente-sesiones-otro-servicio";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 702,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "1003"), servicioDoc(1003, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "702"),
            fichaCliente(702, NEGOCIO_A, clienteUid, "77777702X", {
                serviciosContratados: [1003]
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "509"))
    );
});

test("PRUEBA 51: un CLIENTE no lee la sesion si el servicio esta inactivo -> DENY", async () => {
    const clienteUid = "cliente-sesiones-inactivo";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 703,
            negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", "703"),
            fichaCliente(703, NEGOCIO_A, clienteUid, "77777703X", {
                serviciosContratados: [1002]
            })
        );
        await setDoc(
            doc(database, "sesiones", "510"),
            sesionDoc(510, NEGOCIO_A, 1002)
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "510"))
    );
});

test("PRUEBA 52: un CLIENTE no lee una sesion de un servicio de otro negocio -> DENY", async () => {
    const clienteUid = "cliente-sesiones-a";
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "507"))
    );
});

test("PRUEBA 53: un CLIENTE vinculado a otro negocio no lee la sesion -> DENY", async () => {
    const clienteUid = "cliente-sesiones-negocio-b";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 704,
            negocioId: NEGOCIO_B
        });
        await setDoc(
            doc(database, "clientes", "704"),
            fichaCliente(704, NEGOCIO_B, clienteUid, "77777704X", {
                serviciosContratados: [1001]
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "509"))
    );
});

// ============================================================
// RESERVAS EN FIRESTORE (Transaction + Rules)
// ============================================================
// Fixtures compartidas entre las pruebas de reservas:
//  - admin-reservas-a (negocio A), admin-reservas-b (negocio B)
//  - cliente-reserva-a  -> clienteId 800 (negocio A, servicios [2000])
//  - cliente-reserva-otro -> clienteId 801 (negocio A, servicios [2000])
//  - cliente-reserva-b  -> clienteId 802 (negocio B, servicios [2002])
//  - servicios: 2000 (A, activo), 2001 (A, inactivo), 2002 (B, activo), 2003 (A, activo)
//  - sesiones: 600(A/2000,plazas 5), 601(A/2001), 602(B/2002), 603(A/2000,plazas 0),
//              604(A/2002), 605(A/2003), 606(A/2000,plazas 5), 607(A/2000,plazas 5),
//              608(A/2000,plazas 5)
//  - reservas: 800_606 (negocio A), 802_602 (negocio B), 800_608 (negocio A)

test("PRUEBA 54: el CLIENTE lee la sesion pero ya NO puede crear la reserva directamente (callable) -> DENY", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", "admin-reservas-a"), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "usuarios", "admin-reservas-b"), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_B
        });
        await setDoc(doc(database, "usuarios", "cliente-reserva-a"), {
            rol: "CLIENTE", activo: true, clienteId: 800, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "usuarios", "cliente-reserva-otro"), {
            rol: "CLIENTE", activo: true, clienteId: 801, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "usuarios", "cliente-reserva-b"), {
            rol: "CLIENTE", activo: true, clienteId: 802, negocioId: NEGOCIO_B
        });

        await setDoc(doc(database, "servicios", "2000"), servicioDoc(2000, NEGOCIO_A));
        await setDoc(doc(database, "servicios", "2001"), servicioDoc(2001, NEGOCIO_A, { activo: false }));
        await setDoc(doc(database, "servicios", "2002"), servicioDoc(2002, NEGOCIO_B));
        await setDoc(doc(database, "servicios", "2003"), servicioDoc(2003, NEGOCIO_A));

        await setDoc(doc(database, "sesiones", "600"), sesionDoc(600, NEGOCIO_A, 2000, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "601"), sesionDoc(601, NEGOCIO_A, 2001, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "602"), sesionDoc(602, NEGOCIO_B, 2002, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "603"), sesionDoc(603, NEGOCIO_A, 2000, { plazasDisponibles: 0, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "604"), sesionDoc(604, NEGOCIO_A, 2002, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "605"), sesionDoc(605, NEGOCIO_A, 2003, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "606"), sesionDoc(606, NEGOCIO_A, 2000, { plazasDisponibles: 4, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "607"), sesionDoc(607, NEGOCIO_A, 2000, { plazasDisponibles: 5, capacidad: 5 }));
        await setDoc(doc(database, "sesiones", "608"), sesionDoc(608, NEGOCIO_A, 2000, { plazasDisponibles: 5, capacidad: 5 }));

        await setDoc(
            doc(database, "clientes", "800"),
            fichaCliente(800, NEGOCIO_A, "cliente-reserva-a", "88888800X", { serviciosContratados: [2000] })
        );
        await setDoc(
            doc(database, "clientes", "801"),
            fichaCliente(801, NEGOCIO_A, "cliente-reserva-otro", "88888801X", { serviciosContratados: [2000] })
        );
        await setDoc(
            doc(database, "clientes", "802"),
            fichaCliente(802, NEGOCIO_B, "cliente-reserva-b", "88888802X", { serviciosContratados: [2002] })
        );

        await setDoc(doc(database, "reservas", "800_606"), reservaDoc(800, 606, NEGOCIO_A));
        await setDoc(doc(database, "reservas", "802_602"), reservaDoc(802, 602, NEGOCIO_B));
        await setDoc(doc(database, "reservas", "800_608"), reservaDoc(800, 608, NEGOCIO_A));
    });

    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();

    // Lectura de la sesión legítima.
    await assertSucceeds(getDoc(doc(database, "sesiones", "600")));

    // FASE 4: la creación directa de reservas por el CLIENTE queda cerrada
    // (la reserva se hace mediante la Cloud Function `reservar`, Admin SDK).
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "800"));
            await tx.get(doc(database, "sesiones", "600"));
            await tx.get(doc(database, "servicios", "2000"));
            await tx.get(doc(database, "reservas", "800_600"));
            await tx.set(doc(database, "reservas", "800_600"), reservaDoc(800, 600, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "600"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 55: el CLIENTE no puede reservar un servicio no contratado -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "605"));
            await tx.set(doc(database, "reservas", "800_605"), reservaDoc(800, 605, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "605"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 56: el CLIENTE no puede reservar una sesion de servicio inactivo -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "601"));
            await tx.set(doc(database, "reservas", "800_601"), reservaDoc(800, 601, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "601"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 57: el CLIENTE no puede reservar una sesion de otro negocio -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "602"));
            await tx.set(doc(database, "reservas", "800_602"), reservaDoc(800, 602, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "602"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 58: el CLIENTE no puede reservar para otro cliente -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "600"));
            await tx.set(doc(database, "reservas", "801_600"), reservaDoc(801, 600, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "600"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 59: el CLIENTE no puede reservar una sesion inexistente -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "9999"));
            await tx.set(doc(database, "reservas", "800_9999"), reservaDoc(800, 9999, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "9999"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 60: el CLIENTE no puede duplicar su reserva -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "600"));
            await tx.set(doc(database, "reservas", "800_600"), reservaDoc(800, 600, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "600"), { plazasDisponibles: 3 });
        })
    );
});

test("PRUEBA 61: el CLIENTE no puede reservar sin plazas -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "603"));
            await tx.set(doc(database, "reservas", "800_603"), reservaDoc(800, 603, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "603"), { plazasDisponibles: -1 });
        })
    );
});

test("PRUEBA 62: usuario no autenticado no puede reservar -> DENY", async () => {
    const database = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.set(doc(database, "reservas", "800_600"), reservaDoc(800, 600, NEGOCIO_A));
        })
    );
});

test("PRUEBA 63: el CLIENTE lee su propia reserva pero ya NO puede cancelarla directamente (callable) -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();

    // Lectura de su propia reserva legítima.
    await assertSucceeds(
        getDoc(doc(database, "reservas", "800_606"))
    );

    // FASE 4: cancelar reserva solo a través de la callable `cancelarReserva`.
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_606"));
            await tx.get(doc(database, "sesiones", "606"));
            await tx.delete(doc(database, "reservas", "800_606"));
            await tx.update(doc(database, "sesiones", "606"), { plazasDisponibles: 5 });
        })
    );
});

test("PRUEBA 64: el CLIENTE no puede cancelar la reserva de otro cliente -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-otro").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_606"));
            await tx.delete(doc(database, "reservas", "800_606"));
            await tx.update(doc(database, "sesiones", "606"), { plazasDisponibles: 5 });
        })
    );
});

test("PRUEBA 65: el CLIENTE no puede cancelar sin devolver la plaza -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_606"));
            await tx.delete(doc(database, "reservas", "800_606"));
        })
    );
});

test("PRUEBA 66: el CLIENTE no puede cancelar superando la capacidad -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_608"));
            await tx.delete(doc(database, "reservas", "800_608"));
            await tx.update(doc(database, "sesiones", "608"), { plazasDisponibles: 6 });
        })
    );
});

test("PRUEBA 67: el ADMIN consulta una reserva de su negocio -> ALLOW", async () => {
    const database = testEnvironment.authenticatedContext("admin-reservas-a").firestore();
    await assertSucceeds(
        getDoc(doc(database, "reservas", "800_606"))
    );
});

test("PRUEBA 68: el ADMIN no puede consultar una reserva de otro negocio -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("admin-reservas-a").firestore();
    await assertFails(
        getDoc(doc(database, "reservas", "802_602"))
    );
});

test("PRUEBA 69: el ADMIN elimina una reserva de su negocio con ajuste de plazas -> ALLOW", async () => {
    const database = testEnvironment.authenticatedContext("admin-reservas-a").firestore();
    await assertSucceeds(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_606"));
            await tx.get(doc(database, "sesiones", "606"));
            await tx.delete(doc(database, "reservas", "800_606"));
            await tx.update(doc(database, "sesiones", "606"), { plazasDisponibles: 5 });
        })
    );
});

test("PRUEBA 70: el ADMIN no puede eliminar una reserva de otro negocio -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("admin-reservas-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "802_602"));
            await tx.delete(doc(database, "reservas", "802_602"));
            await tx.update(doc(database, "sesiones", "602"), { plazasDisponibles: 6 });
        })
    );
});

test("PRUEBA 71: no se permite crear una reserva sin decrementar la plaza -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "607"));
            await tx.set(doc(database, "reservas", "800_607"), reservaDoc(800, 607, NEGOCIO_A));
        })
    );
});

test("PRUEBA 72: no se permite decrementar la plaza sin crear la reserva -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "607"));
            await tx.update(doc(database, "sesiones", "607"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 73: no se permite que plazasDisponibles quede por debajo de 0 -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "603"));
            await tx.set(doc(database, "reservas", "800_603"), reservaDoc(800, 603, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "603"), { plazasDisponibles: -1 });
        })
    );
});

test("PRUEBA 74: no se permite que plazasDisponibles supere la capacidad -> DENY", async () => {
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "800_608"));
            await tx.delete(doc(database, "reservas", "800_608"));
            await tx.update(doc(database, "sesiones", "608"), { plazasDisponibles: 6 });
        })
    );
});

test("PRUEBA 75: no se permite reservar si el servicio no existe -> DENY", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "607"),
            sesionDoc(607, NEGOCIO_A, 9999, { plazasDisponibles: 5, capacidad: 5 })
        );
    });
    const database = testEnvironment.authenticatedContext("cliente-reserva-a").firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "607"));
            await tx.set(doc(database, "reservas", "800_607"), reservaDoc(800, 607, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "607"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 76: no se permite reservar si el servicio no pertenece al negocio -> DENY", async () => {
    const clienteUid = "cliente-reserva-servicio-ajeno";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 804, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(context.firestore(), "clientes", "804"),
            fichaCliente(804, NEGOCIO_A, clienteUid, "88888804X", { serviciosContratados: [2002] })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "sesiones", "604"));
            await tx.set(doc(database, "reservas", "804_604"), reservaDoc(804, 604, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "604"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 77: el ADMIN borra una reserva y elimina su sesion en la misma operacion -> ALLOW", async () => {
    const adminUid = "admin-cascada-ok";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "sesiones", "900"),
            sesionDoc(900, NEGOCIO_A, 2000, { plazasDisponibles: 3, capacidad: 5 })
        );
        await setDoc(doc(database, "reservas", "900_900"), reservaDoc(900, 900, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "900_900"));
            await tx.get(doc(database, "sesiones", "900"));
            await tx.delete(doc(database, "reservas", "900_900"));
            await tx.delete(doc(database, "sesiones", "900"));
        })
    );
});

test("PRUEBA 78: el ADMIN no puede borrar una reserva sin eliminar su sesion ni incrementar plazas -> DENY", async () => {
    const adminUid = "admin-cascada-sin-sesion";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "sesiones", "901"),
            sesionDoc(901, NEGOCIO_A, 2000, { plazasDisponibles: 3, capacidad: 5 })
        );
        await setDoc(doc(database, "reservas", "900_901"), reservaDoc(900, 901, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "900_901"));
            await tx.delete(doc(database, "reservas", "900_901"));
        })
    );
});

test("PRUEBA 79: el ADMIN de otro negocio no puede realizar la cascada -> DENY", async () => {
    const adminUid = "admin-cascada-ajena";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_B
        });
        await setDoc(
            doc(database, "sesiones", "902"),
            sesionDoc(902, NEGOCIO_A, 2000, { plazasDisponibles: 3, capacidad: 5 })
        );
        await setDoc(doc(database, "reservas", "900_902"), reservaDoc(900, 902, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "900_902"));
            await tx.get(doc(database, "sesiones", "902"));
            await tx.delete(doc(database, "reservas", "900_902"));
            await tx.delete(doc(database, "sesiones", "902"));
        })
    );
});

test("PRUEBA 80: el CLIENTE no puede realizar la cascada (reserva + sesion) -> DENY", async () => {
    const clienteUid = "cliente-cascada-deny";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 900, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "2000"), servicioDoc(2000, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "900"),
            fichaCliente(900, NEGOCIO_A, clienteUid, "88888900X", { serviciosContratados: [2000] })
        );
        await setDoc(
            doc(database, "sesiones", "903"),
            sesionDoc(903, NEGOCIO_A, 2000, { plazasDisponibles: 3, capacidad: 5 })
        );
        await setDoc(doc(database, "reservas", "900_903"), reservaDoc(900, 903, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "900_903"));
            await tx.get(doc(database, "sesiones", "903"));
            await tx.delete(doc(database, "reservas", "900_903"));
            await tx.delete(doc(database, "sesiones", "903"));
        })
    );
});

test("PRUEBA 81: la cancelacion normal del ADMIN (reserva + plazas+1, sin eliminar sesion) sigue permitida -> ALLOW", async () => {
    const adminUid = "admin-cascada-normal";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "2000"), servicioDoc(2000, NEGOCIO_A));
        await setDoc(
            doc(database, "sesiones", "904"),
            sesionDoc(904, NEGOCIO_A, 2000, { plazasDisponibles: 4, capacidad: 5 })
        );
        await setDoc(doc(database, "reservas", "900_904"), reservaDoc(900, 904, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "reservas", "900_904"));
            await tx.get(doc(database, "sesiones", "904"));
            await tx.delete(doc(database, "reservas", "900_904"));
            await tx.update(doc(database, "sesiones", "904"), { plazasDisponibles: 5 });
        })
    );
});

// =========================================================
// HORA DE APERTURA DE RESERVAS (horaDesdeReserva)
// =========================================================
// Sesiones con horaDesdeReserva = "HH:mm": el CLIENTE solo puede reservar a
// partir de ese instante (request.time >= sesion.fecha + horaDesdeReserva).
// null / ausente = reservas abiertas desde el inicio del dÃ­a.

test("PRUEBA 82: sesion create con horaDesdeReserva string -> ALLOW", async () => {
    const adminUid = "admin-apertura";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(doc(context.firestore(), "servicios", "1100"), servicioDoc(1100, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "sesiones", "9100"),
            sesionDoc(9100, NEGOCIO_A, 1100, { horaDesdeReserva: "18:00" })
        )
    );
});

test("PRUEBA 83: sesion create sin horaDesdeReserva -> ALLOW", async () => {
    const adminUid = "admin-apertura";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "sesiones", "9101"),
            sesionDoc(9101, NEGOCIO_A, 1100)
        )
    );
});

test("PRUEBA 84: sesion create con horaDesdeReserva de tipo incorrecto -> DENY", async () => {
    const adminUid = "admin-apertura";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "sesiones", "9102"),
            sesionDoc(9102, NEGOCIO_A, 1100, { horaDesdeReserva: 18 })
        )
    );
});

test("PRUEBA 85: sesion update modificando horaDesdeReserva -> ALLOW", async () => {
    const adminUid = "admin-apertura";
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "sesiones", "9101"), { horaDesdeReserva: "19:30" })
    );
});

test("PRUEBA 86: la sesion sin horaDesdeReserva es legible por el CLIENTE, pero crear la reserva directa -> DENY (callable)", async () => {
    const clienteUid = "cliente-apertura";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 910, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", "910"),
            fichaCliente(910, NEGOCIO_A, clienteUid, "88888910X", { serviciosContratados: [1100] })
        );
        // SesiÃ³n de hoy SIN horaDesdeReserva -> abierta desde el inicio del dÃ­a.
        await setDoc(
            doc(database, "sesiones", "9103"),
            sesionDoc(9103, NEGOCIO_A, 1100, {
                fecha: Date.now(),
                plazasDisponibles: 5,
                capacidad: 5
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // Lectura legítima de la sesión.
    await assertSucceeds(getDoc(doc(database, "sesiones", "9103")));

    // FASE 4: la creación directa queda cerrada (valida la callable `reservar`).
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "910"));
            await tx.get(doc(database, "sesiones", "9103"));
            await tx.get(doc(database, "servicios", "1100"));
            await tx.get(doc(database, "reservas", "910_9103"));
            await tx.set(doc(database, "reservas", "910_9103"), reservaDoc(910, 9103, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "9103"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 87: la sesion con apertura ya pasada es legible, pero crear la reserva directa -> DENY (callable)", async () => {
    const clienteUid = "cliente-apertura";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "9104"),
            sesionDoc(9104, NEGOCIO_A, 1100, {
                fecha: Date.now() - 86400000,
                horaDesdeReserva: "00:00",
                plazasDisponibles: 5,
                capacidad: 5
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    await assertSucceeds(getDoc(doc(database, "sesiones", "9104")));

    // FASE 4: la creación directa queda cerrada (valida la callable `reservar`).
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "910"));
            await tx.get(doc(database, "sesiones", "9104"));
            await tx.get(doc(database, "servicios", "1100"));
            await tx.get(doc(database, "reservas", "910_9104"));
            await tx.set(doc(database, "reservas", "910_9104"), reservaDoc(910, 9104, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "9104"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 88: reserva con apertura futura -> DENY", async () => {
    const clienteUid = "cliente-apertura";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "9105"),
            sesionDoc(9105, NEGOCIO_A, 1100, {
                fecha: Date.now() + 86400000,
                horaDesdeReserva: "00:00",
                plazasDisponibles: 5,
                capacidad: 5
            })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "910"));
            await tx.get(doc(database, "sesiones", "9105"));
            await tx.get(doc(database, "servicios", "1100"));
            await tx.get(doc(database, "reservas", "910_9105"));
            await tx.set(doc(database, "reservas", "910_9105"), reservaDoc(910, 9105, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "9105"), { plazasDisponibles: 4 });
        })
    );
});

// =========================================================
// NOTIFICACIONES (Fase B)
// =========================================================

async function seedAdminNotif(adminUid, negocioId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId
        });
    });
}

async function seedClienteNotif(clienteUid, clienteId, negocioId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId
        });
        await setDoc(
            doc(database, "clientes", String(clienteId)),
            fichaCliente(clienteId, negocioId, clienteUid, `88999${clienteId}X`)
        );
    });
}

function notificacionDoc(negocioId, extra = {}) {
    return {
        negocioId,
        titulo: "Aviso del gimnasio",
        mensaje: "Mensaje de prueba",
        tipo: "MANUAL",
        origen: "MANUAL",
        modoDestino: "INDIVIDUAL",
        clienteId: 3,
        fechaCreacion: Timestamp.now(),
        programada: false,
        estado: "ENVIADA",
        ...extra
    };
}

function notifDestinatarioDoc(negocioId, clienteId, notificacionId, firebaseUid, extra = {}) {
    return {
        negocioId,
        notificacionId,
        clienteId,
        firebaseUid,
        titulo: "Aviso del gimnasio",
        mensaje: "Mensaje de prueba",
        tipo: "MANUAL",
        origen: "MANUAL",
        fechaEnvio: Timestamp.now(),
        leida: false,
        ...extra
    };
}

test("PRUEBA 89: el ADMIN crea la configuracion de notificaciones de su negocio -> ALLOW", async () => {
    const adminUid = "admin-notif-a";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "configuracion_notificaciones", NEGOCIO_A), {
            negocioId: NEGOCIO_A,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true }
        })
    );
});

test("PRUEBA 90: un CLIENTE no puede crear la configuracion de notificaciones -> DENY", async () => {
    const clienteUid = "cliente-notif-config";
    await seedClienteNotif(clienteUid, 890, NEGOCIO_A);
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(doc(database, "configuracion_notificaciones", NEGOCIO_A), {
            negocioId: NEGOCIO_A,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true }
        })
    );
});

test("PRUEBA 91: el ADMIN actualiza la configuracion (solo morosidad) -> ALLOW y no puede cambiar negocioId -> DENY", async () => {
    const adminUid = "admin-notif-b";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "configuracion_notificaciones", NEGOCIO_A), {
            negocioId: NEGOCIO_A,
            morosidad: { activa: false, recordatorioHoras: 24 },
            bajaConfirmada: { activa: false }
        });
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "configuracion_notificaciones", NEGOCIO_A), {
            morosidad: { activa: true, recordatorioHoras: 12 }
        })
    );
    await assertFails(
        updateDoc(doc(database, "configuracion_notificaciones", NEGOCIO_A), {
            negocioId: NEGOCIO_B
        })
    );
});

test("PRUEBA 92: el ADMIN crea una notificacion (individual) valida -> ALLOW", async () => {
    const adminUid = "admin-notif-c";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "notificaciones", "n-001"), notificacionDoc(NEGOCIO_A))
    );
});

test("PRUEBA 93: un CLIENTE no puede crear notificaciones y el ADMIN con tipo invalido -> DENY", async () => {
    const adminUid = "admin-notif-d";
    const clienteUid = "cliente-notif-93";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    await seedClienteNotif(clienteUid, 893, NEGOCIO_A);
    const dbCliente = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(doc(dbCliente, "notificaciones", "n-093"), notificacionDoc(NEGOCIO_A))
    );
    const dbAdmin = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(dbAdmin, "notificaciones", "n-093b"), notificacionDoc(NEGOCIO_A, { tipo: "DESCONOCIDO" }))
    );
});

test("PRUEBA 94: el ADMIN crea un doc por destinatario con documentId coherente -> ALLOW y con documentId incoherente -> DENY", async () => {
    const adminUid = "admin-notif-e";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "notificaciones_por_destinatario", "890_n-002"),
            notifDestinatarioDoc(NEGOCIO_A, 890, "n-002", CLIENTE_UID)
        )
    );
    await assertFails(
        setDoc(
            doc(database, "notificaciones_por_destinatario", "id-incoherente"),
            notifDestinatarioDoc(NEGOCIO_A, 890, "n-002", CLIENTE_UID)
        )
    );
});

test("PRUEBA 95: un CLIENTE lee SOLO su propia notificacion -> ALLOW y la ajena -> DENY", async () => {
    const clienteUid = "cliente-notif-95";
    const otroClienteUid = "otro-notif-95";
    await seedClienteNotif(clienteUid, 895, NEGOCIO_A);
    await seedClienteNotif(otroClienteUid, 8951, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(
            doc(database, "notificaciones_por_destinatario", "895_n-095"),
            notifDestinatarioDoc(NEGOCIO_A, 895, "n-095", clienteUid)
        );
        await setDoc(
            doc(database, "notificaciones_por_destinatario", "8951_n-095"),
            notifDestinatarioDoc(NEGOCIO_A, 8951, "n-095", otroClienteUid)
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "notificaciones_por_destinatario", "895_n-095"))
    );
    await assertFails(
        getDoc(doc(database, "notificaciones_por_destinatario", "8951_n-095"))
    );
});

test("PRUEBA 96: un CLIENTE marca leida su notificacion -> ALLOW; la ajena -> DENY; cambiar otro campo -> DENY", async () => {
    const clienteUid = "cliente-notif-96";
    await seedClienteNotif(clienteUid, 896, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(
            doc(database, "notificaciones_por_destinatario", "896_n-096"),
            notifDestinatarioDoc(NEGOCIO_A, 896, "n-096", clienteUid)
        );
        await setDoc(
            doc(database, "notificaciones_por_destinatario", "897_n-096"),
            notifDestinatarioDoc(NEGOCIO_A, 897, "n-096", "otro-uid-96")
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "notificaciones_por_destinatario", "896_n-096"), {
            leida: true,
            fechaLeida: Timestamp.now()
        })
    );
    await assertFails(
        updateDoc(doc(database, "notificaciones_por_destinatario", "896_n-096"), {
            titulo: "Cambio no permitido"
        })
    );
    await assertFails(
        updateDoc(doc(database, "notificaciones_por_destinatario", "897_n-096"), {
            leida: true
        })
    );
});

test("PRUEBA 97: el ADMIN lista notificaciones de su negocio -> ALLOW y de otro negocio -> DENY", async () => {
    const adminUid = "admin-notif-97";
    await seedAdminNotif(adminUid, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "notificaciones", "n-097"), notificacionDoc(NEGOCIO_A));
        await setDoc(doc(database, "notificaciones", "n-097b"), notificacionDoc(NEGOCIO_B));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDocs(query(collection(database, "notificaciones"), where("negocioId", "==", NEGOCIO_A)))
    );
    await assertFails(
        getDocs(query(collection(database, "notificaciones"), where("negocioId", "==", NEGOCIO_B)))
    );
});

test("PRUEBA 98: un CLIENTE registra y actualiza su token FCM -> ALLOW y no puede registrar el de otro cliente -> DENY", async () => {
    const clienteUid = "cliente-notif-98";
    await seedClienteNotif(clienteUid, 898, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "clientes", "899"),
            fichaCliente(899, NEGOCIO_A, "otro-uid-98", "88999899X")
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    const token = "token-fcm-898";
    await assertSucceeds(
        setDoc(doc(database, "clientes", "898", "dispositivos", token), {
            token,
            plataforma: "android",
            updatedAt: Timestamp.now()
        })
    );
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "898", "dispositivos", token), {
            plataforma: "android",
            updatedAt: Timestamp.now()
        })
    );
    await assertFails(
        setDoc(doc(database, "clientes", "899", "dispositivos", token), {
            token,
            plataforma: "android",
            updatedAt: Timestamp.now()
        })
    );
});

// =========================================================
// SOLICITUDES DE BAJA (PRUEBA 99-108)
// =========================================================

function solicitudDoc(idSolicitud, negocioId, idCliente, firebaseUid, extra = {}) {
    return {
        idSolicitud,
        negocioId,
        idCliente,
        firebaseUid,
        fechaSolicitud: Timestamp.now(),
        estado: extra.estado ?? "PENDIENTE",
        tipo: extra.tipo ?? "BAJA",
        ...extra
    };
}

test("PRUEBA 99: un CLIENTE crea su solicitud de baja -> ALLOW", async () => {
    const clienteUid = "cliente-sol-99";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 990,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "990"), fichaCliente(990, NEGOCIO_A, clienteUid, "99000099A"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "solicitudes", "baja_990_1700000000000"),
            solicitudDoc("baja_990_1700000000000", NEGOCIO_A, 990, clienteUid)
        )
    );
});

test("PRUEBA 100: un CLIENTE no puede crear una solicitud para otro cliente -> DENY", async () => {
    const clienteUid = "cliente-sol-100";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 1000,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1000"), fichaCliente(1000, NEGOCIO_A, clienteUid, "10000010A"));
        await setDoc(doc(database, "clientes", "1001"), fichaCliente(1001, NEGOCIO_A, "otro-uid-100", "10000011A"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "solicitudes", "baja_1001_1700000000000"),
            solicitudDoc("baja_1001_1700000000000", NEGOCIO_A, 1001, "otro-uid-100")
        )
    );
});

test("PRUEBA 101: un CLIENTE consulta su propia solicitud -> ALLOW", async () => {
    const clienteUid = "cliente-sol-101";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 1010,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1010"), fichaCliente(1010, NEGOCIO_A, clienteUid, "10100010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1010_1700000000000"),
            solicitudDoc("baja_1010_1700000000000", NEGOCIO_A, 1010, clienteUid)
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "solicitudes", "baja_1010_1700000000000"))
    );
});

test("PRUEBA 102: un CLIENTE no puede consultar la solicitud de otro -> DENY", async () => {
    const clienteUid = "cliente-sol-102";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 1020,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1020"), fichaCliente(1020, NEGOCIO_A, clienteUid, "10200010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1021_1700000000000"),
            solicitudDoc("baja_1021_1700000000000", NEGOCIO_A, 1021, "otro-uid-102")
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "solicitudes", "baja_1021_1700000000000"))
    );
});

test("PRUEBA 103: un CLIENTE no puede cambiar el estado de su solicitud -> DENY", async () => {
    const clienteUid = "cliente-sol-103";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 1030,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1030"), fichaCliente(1030, NEGOCIO_A, clienteUid, "10300010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1030_1700000000000"),
            solicitudDoc("baja_1030_1700000000000", NEGOCIO_A, 1030, clienteUid)
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        updateDoc(doc(database, "solicitudes", "baja_1030_1700000000000"), {
            estado: "ACEPTADA"
        })
    );
});

test("PRUEBA 104: el ADMIN consulta solicitudes de su negocio -> ALLOW", async () => {
    const adminUid = "admin-sol-104";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1040"), fichaCliente(1040, NEGOCIO_A, "cliente-104", "10400010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1040_1700000000000"),
            solicitudDoc("baja_1040_1700000000000", NEGOCIO_A, 1040, "cliente-104")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        getDocs(query(collection(database, "solicitudes"), where("negocioId", "==", NEGOCIO_A)))
    );
    await assertSucceeds(
        getDoc(doc(database, "solicitudes", "baja_1040_1700000000000"))
    );
});

test("PRUEBA 105: el ADMIN no consulta solicitudes de otro negocio -> DENY", async () => {
    const adminUid = "admin-sol-105";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1050"), fichaCliente(1050, NEGOCIO_B, "cliente-105", "10500010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1050_1700000000000"),
            solicitudDoc("baja_1050_1700000000000", NEGOCIO_B, 1050, "cliente-105")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        getDoc(doc(database, "solicitudes", "baja_1050_1700000000000"))
    );
    await assertFails(
        getDocs(query(collection(database, "solicitudes"), where("negocioId", "==", NEGOCIO_B)))
    );
});

test("PRUEBA 106: el ADMIN acepta una solicitud de su negocio -> ALLOW", async () => {
    const adminUid = "admin-sol-106";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1060"), fichaCliente(1060, NEGOCIO_A, "cliente-106", "10600010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1060_1700000000000"),
            solicitudDoc("baja_1060_1700000000000", NEGOCIO_A, 1060, "cliente-106")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "solicitudes", "baja_1060_1700000000000"), {
            estado: "ACEPTADA",
            fechaResolucion: Timestamp.now(),
            resueltaPor: adminUid
        })
    );
});

test("PRUEBA 107: el ADMIN no modifica una solicitud de otro negocio -> DENY", async () => {
    const adminUid = "admin-sol-107";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", "1070"), fichaCliente(1070, NEGOCIO_B, "cliente-107", "10700010A"));
        await setDoc(
            doc(database, "solicitudes", "baja_1070_1700000000000"),
            solicitudDoc("baja_1070_1700000000000", NEGOCIO_B, 1070, "cliente-107")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "solicitudes", "baja_1070_1700000000000"), {
            estado: "RECHAZADA",
            fechaResolucion: Timestamp.now(),
            resueltaPor: adminUid
        })
    );
});

test("PRUEBA 108: solicitudes con datos invÃ¡lidos o cliente no apto -> DENY", async () => {
    const clienteUid = "cliente-sol-108";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE",
            activo: true,
            clienteId: 1080,
            negocioId: NEGOCIO_A
        });
        // Ficha de otro cliente ya en BAJA (no puede solicitar).
        await setDoc(doc(database, "clientes", "1081"), fichaCliente(1081, NEGOCIO_A, "otro-uid-108", "10800010A", { estado: "BAJA" }));
        // Ficha propia ACTIVA.
        await setDoc(doc(database, "clientes", "1080"), fichaCliente(1080, NEGOCIO_A, clienteUid, "10800000A"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();

    // estado no PENDIENTE
    await assertFails(
        setDoc(doc(database, "solicitudes", "bad-108-1"), solicitudDoc("bad-108-1", NEGOCIO_A, 1080, clienteUid, { estado: "ACEPTADA" }))
    );
    // tipo invÃ¡lido
    await assertFails(
        setDoc(doc(database, "solicitudes", "bad-108-2"), solicitudDoc("bad-108-2", NEGOCIO_A, 1080, clienteUid, { tipo: "CANCELACION" }))
    );
    // campos extra fuera del contrato
    await assertFails(
        setDoc(doc(database, "solicitudes", "bad-108-3"), {
            ...solicitudDoc("bad-108-3", NEGOCIO_A, 1080, clienteUid),
            campoInesperado: "x"
        })
    );
    // firebaseUid ajeno
    await assertFails(
        setDoc(doc(database, "solicitudes", "bad-108-4"), solicitudDoc("bad-108-4", NEGOCIO_A, 1080, "otro-uid-108"))
    );
    // cliente en BAJA no puede solicitar
    await assertFails(
        setDoc(doc(database, "solicitudes", "bad-108-5"), solicitudDoc("bad-108-5", NEGOCIO_A, 1081, "otro-uid-108"))
    );
});

// =========================================================
// BAJA: BLOQUEO DE ACCESO A SESIONES Y RESERVAS (PRUEBA 109-112)
// =========================================================

test("PRUEBA 109: un CLIENTE en BAJA no puede leer sesiones -> DENY", async () => {
    const clienteUid = "cliente-baja-sesiones-109";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 5000, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5001"), servicioDoc(5001, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "5000"),
            fichaCliente(5000, NEGOCIO_A, clienteUid, "50000000X", {
                estado: "BAJA",
                serviciosContratados: [5001]
            })
        );
        await setDoc(doc(database, "sesiones", "5002"), sesionDoc(5002, NEGOCIO_A, 5001, { plazasDisponibles: 5, capacidad: 5 }));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "5002"))
    );
});

test("PRUEBA 110: un CLIENTE en BAJA no puede listar sesiones -> DENY", async () => {
    const clienteUid = "cliente-baja-list-110";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 5010, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5011"), servicioDoc(5011, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "5010"),
            fichaCliente(5010, NEGOCIO_A, clienteUid, "50100000X", {
                estado: "BAJA",
                serviciosContratados: [5011]
            })
        );
        await setDoc(doc(database, "sesiones", "5012"), sesionDoc(5012, NEGOCIO_A, 5011, { plazasDisponibles: 5, capacidad: 5 }));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDocs(query(collection(database, "sesiones"), where("negocioId", "==", NEGOCIO_A)))
    );
});

test("PRUEBA 111: un CLIENTE en BAJA no puede crear una reserva -> DENY", async () => {
    const clienteUid = "cliente-baja-reserva-111";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 5020, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5021"), servicioDoc(5021, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "5020"),
            fichaCliente(5020, NEGOCIO_A, clienteUid, "50200000X", {
                estado: "BAJA",
                serviciosContratados: [5021]
            })
        );
        await setDoc(doc(database, "sesiones", "5022"), sesionDoc(5022, NEGOCIO_A, 5021, { plazasDisponibles: 5, capacidad: 5 }));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "5020"));
            await tx.get(doc(database, "sesiones", "5022"));
            await tx.get(doc(database, "servicios", "5021"));
            await tx.get(doc(database, "reservas", "5020_5022"));
            await tx.set(doc(database, "reservas", "5020_5022"), reservaDoc(5020, 5022, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "5022"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 112: un CLIENTE ACTIVO lee sesiones (ALLOW) pero ya no reserva directamente -> DENY", async () => {
    const clienteUid = "cliente-activo-regresion-112";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 5030, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5031"), servicioDoc(5031, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "5030"),
            fichaCliente(5030, NEGOCIO_A, clienteUid, "50300000X", {
                serviciosContratados: [5031]
            })
        );
        await setDoc(doc(database, "sesiones", "5032"), sesionDoc(5032, NEGOCIO_A, 5031, { plazasDisponibles: 5, capacidad: 5 }));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "sesiones", "5032"))
    );
    // FASE 4: la reserva se crea vía la callable `reservar`, nunca en Rules.
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "5030"));
            await tx.get(doc(database, "sesiones", "5032"));
            await tx.get(doc(database, "servicios", "5031"));
            await tx.get(doc(database, "reservas", "5030_5032"));
            await tx.set(doc(database, "reservas", "5030_5032"), reservaDoc(5030, 5032, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "5032"), { plazasDisponibles: 4 });
        })
    );
});

// =========================================================
// SESIONES: REGRESIONES EXPLICITAS (PRUEBA 113-115)
// =========================================================
// La regresiÃ³n real de producciÃ³n era que el ADMIN intentaba generar sesiones
// de un servicio que NO estaba replicado en Firestore: la regla sesiones/create
// (servicioValidoParaSesion) lo rechaza con PERMISSION_DENIED. Estas pruebas
// fijan el contrato: el payload exacto de la app pasa, y la falta del servicio
// remoto se rechaza (la app debe replicar el servicio antes de generar).

test("PRUEBA 113: el ADMIN crea una sesion con el payload exacto de la app (horaDesdeReserva null, plazas=capacidad) -> ALLOW", async () => {
    const adminUid = "admin-sesiones-regresion-113";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5100"), servicioDoc(5100, NEGOCIO_A));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(doc(database, "sesiones", "5101"), sesionDoc(5101, NEGOCIO_A, 5100, {
            horaDesdeReserva: null,
            plazasDisponibles: 20,
            capacidad: 20
        }))
    );
});

test("PRUEBA 114: el ADMIN NO puede crear una sesion si el servicio no esta replicado en Firestore -> DENY", async () => {
    const adminUid = "admin-sesiones-regresion-114";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(doc(database, "sesiones", "5199"), sesionDoc(5199, NEGOCIO_A, 5198))
    );
});

test("PRUEBA 115: el ADMIN regenera la programacion (batch: borra futura + crea nuevas con plazas=capacidad) -> ALLOW", async () => {
    const adminUid = "admin-sesiones-regresion-115";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "5110"), servicioDoc(5110, NEGOCIO_A));
        await setDoc(doc(database, "sesiones", "5111"), sesionDoc(5111, NEGOCIO_A, 5110, {
            fecha: Date.now() + 86400000,
            plazasDisponibles: 20,
            capacidad: 20
        }));
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    const batch = writeBatch(database);
    batch.delete(doc(database, "sesiones", "5111"));
    batch.set(doc(database, "sesiones", "5112"), sesionDoc(5112, NEGOCIO_A, 5110, {
        fecha: Date.now() + 172800000,
        horaDesdeReserva: null,
        plazasDisponibles: 20,
        capacidad: 20
    }));
    await assertSucceeds(batch.commit());
});

// =========================================================
// SOLICITUDES: BORRADO DE HISTORIAL (PRUEBA 116-117)
// =========================================================

test("PRUEBA 116: el ADMIN elimina una solicitud ACEPTADA del historial -> ALLOW", async () => {
    const adminUid = "admin-solicitudes-delete-116";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(context.firestore(), "solicitudes", "baja_116_1"),
            solicitudDoc("baja_116_1", NEGOCIO_A, 1160, "cliente-116", { estado: "ACEPTADA" })
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        deleteDoc(doc(database, "solicitudes", "baja_116_1"))
    );
});

test("PRUEBA 117: el ADMIN NO puede eliminar una solicitud PENDIENTE -> DENY", async () => {
    const adminUid = "admin-solicitudes-delete-117";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(context.firestore(), "solicitudes", "baja_117_1"),
            solicitudDoc("baja_117_1", NEGOCIO_A, 1170, "cliente-117", { estado: "PENDIENTE" })
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        deleteDoc(doc(database, "solicitudes", "baja_117_1"))
    );
});

// =========================================================
// NOTIFICACION AL ADMIN POR SOLICITUD DE BAJA (PRUEBA 118-120)
// =========================================================
// La notificaciÃ³n SOLICITUD_BAJA la crea el ADMIN (Ãºnico escritor permitido por
// las Rules) cuando carga sus solicitudes PENDIENTES. El CLIENTE no puede
// fabricar notificaciones en absoluto.

function notificacionSolicitudBajaDoc(negocioId, clienteId, fechaMillis) {
    return {
        negocioId,
        titulo: "Solicitud de baja",
        mensaje: "Cliente De Prueba ha solicitado la baja.",
        tipo: "SOLICITUD_BAJA",
        origen: "AUTOMATICA",
        modoDestino: "INDIVIDUAL",
        clienteId,
        fechaCreacion: Timestamp.now(),
        programada: false,
        estado: "PENDIENTE"
    };
}

test("PRUEBA 118: el ADMIN crea la notificacion SOLICITUD_BAJA de su negocio -> ALLOW", async () => {
    const adminUid = "admin-notif-baja-118";
    const clienteId = 1180;
    const fechaMillis = 1700000000000;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "notificaciones", `solicitud_baja_${clienteId}_${fechaMillis}`),
            notificacionSolicitudBajaDoc(NEGOCIO_A, clienteId, fechaMillis)
        )
    );
});

test("PRUEBA 119: un CLIENTE NO puede crear una notificacion SOLICITUD_BAJA ni ninguna otra -> DENY", async () => {
    const clienteUid = "cliente-notif-baja-119";
    const clienteId = 1190;
    const fechaMillis = 1700000000000;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", String(clienteId)), fichaCliente(clienteId, NEGOCIO_A, clienteUid, "11900000X"));
        await setDoc(
            doc(database, "solicitudes", `baja_${clienteId}_${fechaMillis}`),
            solicitudDoc(`baja_${clienteId}_${fechaMillis}`, NEGOCIO_A, clienteId, clienteUid, { estado: "PENDIENTE" })
        );
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "notificaciones", `solicitud_baja_${clienteId}_${fechaMillis}`),
            notificacionSolicitudBajaDoc(NEGOCIO_A, clienteId, fechaMillis)
        )
    );
});

test("PRUEBA 120: el ADMIN NO puede crear una notificacion SOLICITUD_BAJA de otro negocio -> DENY", async () => {
    const adminUid = "admin-notif-baja-120";
    const clienteId = 1200;
    const fechaMillis = 1700000000000;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "notificaciones", `solicitud_baja_${clienteId}_${fechaMillis}`),
            notificacionSolicitudBajaDoc(NEGOCIO_B, clienteId, fechaMillis)
        )
    );
});

// =========================================================
// ACCESO SOLO CON ESTADO ACTIVO (PRUEBA 121-124)
// =========================================================
// Un CLIENTE lee sesiones y reserva SOLO si su estado administrativo es ACTIVO.
// BAJA, REGISTRADO, ARCHIVADO u otro estado no activo quedan excluidos en Rules.
// La morosidad es independiente: un ACTIVO con deuda (campo moroso) sigue ACTIVO.

function sesionActivaDoc(idSesion, negocioId, idServicio) {
    return sesionDoc(idSesion, negocioId, idServicio, { plazasDisponibles: 5, capacidad: 5 });
}

test("PRUEBA 121: un CLIENTE en estado REGISTRADO no puede leer sesiones -> DENY", async () => {
    const clienteUid = "cliente-registrado-sesiones-121";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 6000, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "6001"), servicioDoc(6001, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "6000"),
            fichaCliente(6000, NEGOCIO_A, clienteUid, "60000000X", {
                estado: "REGISTRADO",
                serviciosContratados: [6001]
            })
        );
        await setDoc(doc(database, "sesiones", "6002"), sesionActivaDoc(6002, NEGOCIO_A, 6001));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDoc(doc(database, "sesiones", "6002"))
    );
});

test("PRUEBA 122: un CLIENTE en estado ARCHIVADO no puede listar sesiones -> DENY", async () => {
    const clienteUid = "cliente-archivado-list-122";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 6010, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "6011"), servicioDoc(6011, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "6010"),
            fichaCliente(6010, NEGOCIO_A, clienteUid, "60100000X", {
                estado: "ARCHIVADO",
                serviciosContratados: [6011]
            })
        );
        await setDoc(doc(database, "sesiones", "6012"), sesionActivaDoc(6012, NEGOCIO_A, 6011));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        getDocs(query(collection(database, "sesiones"), where("negocioId", "==", NEGOCIO_A)))
    );
});

test("PRUEBA 123: un CLIENTE en estado REGISTRADO no puede crear una reserva -> DENY", async () => {
    const clienteUid = "cliente-registrado-reserva-123";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 6020, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "6021"), servicioDoc(6021, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "6020"),
            fichaCliente(6020, NEGOCIO_A, clienteUid, "60200000X", {
                estado: "REGISTRADO",
                serviciosContratados: [6021]
            })
        );
        await setDoc(doc(database, "sesiones", "6022"), sesionActivaDoc(6022, NEGOCIO_A, 6021));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "6020"));
            await tx.get(doc(database, "sesiones", "6022"));
            await tx.get(doc(database, "servicios", "6021"));
            await tx.get(doc(database, "reservas", "6020_6022"));
            await tx.set(doc(database, "reservas", "6020_6022"), reservaDoc(6020, 6022, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "6022"), { plazasDisponibles: 4 });
        })
    );
});

test("PRUEBA 124: un CLIENTE ACTIVO con deuda (moroso=true) lee sesiones (ALLOW) pero ya no reserva directamente -> DENY", async () => {
    const clienteUid = "cliente-moroso-activo-124";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId: 6030, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "servicios", "6031"), servicioDoc(6031, NEGOCIO_A));
        await setDoc(
            doc(database, "clientes", "6030"),
            fichaCliente(6030, NEGOCIO_A, clienteUid, "60300000X", {
                estado: "ACTIVO",
                moroso: true,
                fechaEntradaMorosidad: Timestamp.fromMillis(1700000000000),
                serviciosContratados: [6031]
            })
        );
        await setDoc(doc(database, "sesiones", "6032"), sesionActivaDoc(6032, NEGOCIO_A, 6031));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        getDoc(doc(database, "sesiones", "6032"))
    );
    // FASE 4: la reserva se crea vía la callable `reservar`; el CLIENTE ACTIVO
    // con deuda puede reservar en la callable, pero NO crear la reserva en Rules.
    await assertFails(
        runTransaction(database, async (tx) => {
            await tx.get(doc(database, "clientes", "6030"));
            await tx.get(doc(database, "sesiones", "6032"));
            await tx.get(doc(database, "servicios", "6031"));
            await tx.get(doc(database, "reservas", "6030_6032"));
            await tx.set(doc(database, "reservas", "6030_6032"), reservaDoc(6030, 6032, NEGOCIO_A));
            await tx.update(doc(database, "sesiones", "6032"), { plazasDisponibles: 4 });
        })
    );
});

// =========================================================
// NOTIFICACION DE VINCULACION AL ADMIN (PRUEBA 125-128)
// =========================================================
// Al vincularse (ficha propia + negocio propio), el CLIENTE crea UN aviso
// idempotente `notificaciones/vinculacion_{negocioId}_{clienteId}` con tipo
// VINCULACION. No puede crear notificaciones de otro negocio, con otro tipo, ni
// duplicar el aviso (si el documento ya existe, la escritura es un update y se
// deniega). No se abre escritura genÃ©rica de notificaciones al cliente.

function notificacionVinculacionDoc(negocioId, clienteId) {
    return {
        negocioId,
        titulo: "Cliente vinculado",
        mensaje: "Cliente De Prueba se ha vinculado al negocio.",
        tipo: "VINCULACION",
        origen: "AUTOMATICA",
        modoDestino: "INDIVIDUAL",
        clienteId,
        fechaCreacion: Timestamp.now(),
        estado: "PENDIENTE"
    };
}

test("PRUEBA 125: un CLIENTE vinculado crea el aviso VINCULACION de su negocio -> ALLOW", async () => {
    const clienteUid = "cliente-vinculacion-125";
    const clienteId = 6050;
    const notifId = `vinculacion_${NEGOCIO_A}_${clienteId}`;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", String(clienteId)), fichaCliente(clienteId, NEGOCIO_A, clienteUid, "60500000X"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertSucceeds(
        setDoc(
            doc(database, "notificaciones", notifId),
            notificacionVinculacionDoc(NEGOCIO_A, clienteId)
        )
    );
});

test("PRUEBA 126: un CLIENTE NO puede crear el aviso VINCULACION de otro negocio -> DENY", async () => {
    const clienteUid = "cliente-vinculacion-126";
    const clienteId = 6060;
    const notifId = `vinculacion_${NEGOCIO_B}_${clienteId}`;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", String(clienteId)), fichaCliente(clienteId, NEGOCIO_A, clienteUid, "60600000X"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "notificaciones", notifId),
            notificacionVinculacionDoc(NEGOCIO_B, clienteId)
        )
    );
});

test("PRUEBA 127: un CLIENTE NO puede duplicar el aviso VINCULACION si ya existe -> DENY", async () => {
    const clienteUid = "cliente-vinculacion-127";
    const clienteId = 6070;
    const notifId = `vinculacion_${NEGOCIO_A}_${clienteId}`;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", String(clienteId)), fichaCliente(clienteId, NEGOCIO_A, clienteUid, "60700000X"));
        await setDoc(doc(database, "notificaciones", notifId), notificacionVinculacionDoc(NEGOCIO_A, clienteId));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "notificaciones", notifId),
            notificacionVinculacionDoc(NEGOCIO_A, clienteId)
        )
    );
});

test("PRUEBA 128: un CLIENTE NO puede crear una notificacion de otro tipo en notificaciones -> DENY", async () => {
    const clienteUid = "cliente-vinculacion-128";
    const clienteId = 6080;
    const notifId = `otra_${NEGOCIO_A}_${clienteId}`;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
        });
        await setDoc(doc(database, "clientes", String(clienteId)), fichaCliente(clienteId, NEGOCIO_A, clienteUid, "60800000X"));
    });
    const database = testEnvironment.authenticatedContext(clienteUid).firestore();
    await assertFails(
        setDoc(
            doc(database, "notificaciones", notifId),
            { ...notificacionVinculacionDoc(NEGOCIO_A, clienteId), tipo: "MOROSIDAD" }
        )
    );
});

// =========================================================
// RESUMEN ECONOMICO REMOTO en clientes/{id} (PRUEBA 129-136)
// =========================================================
// F2: el ADMIN publica el resumen economico en la ficha del cliente
// (moroso, deuda, fechaEntradaMorosidad, fechaInicioActual, fechaFinActual y
// exentoMorosidad). El CLIENTE NUNCA puede modificar esas claves.

test("PRUEBA 129: el ADMIN de su negocio actualiza el resumen economico -> ALLOW", async () => {
    const adminUid = "admin-resumen-129";
    const clienteId = 6090;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_A
        });
        await setDoc(
            doc(database, "clientes", String(clienteId)),
            fichaCliente(clienteId, NEGOCIO_A, null, "60900000X")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
        updateDoc(doc(database, "clientes", String(clienteId)), {
            moroso: true,
            deuda: 30,
            fechaEntradaMorosidad: Timestamp.fromMillis(1700000000000),
            fechaInicioActual: Timestamp.fromMillis(1700000000000),
            fechaFinActual: Timestamp.fromMillis(1704067200000),
            exentoMorosidad: false
        })
    );
});

test("PRUEBA 130: el ADMIN de OTRO negocio NO puede actualizar el resumen economico -> DENY", async () => {
    const adminUid = "admin-resumen-130";
    const clienteId = 6091;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", adminUid), {
            rol: "ADMIN", activo: true, clienteId: null, negocioId: NEGOCIO_B
        });
        await setDoc(
            doc(database, "clientes", String(clienteId)),
            fichaCliente(clienteId, NEGOCIO_A, null, "60910000X")
        );
    });
    const database = testEnvironment.authenticatedContext(adminUid).firestore();
    await assertFails(
        updateDoc(doc(database, "clientes", String(clienteId)), { moroso: true })
    );
});

// Cliente vinculado intentando tocar cada clave economica -> DENY.
function escenarioClienteIntentaCambiarClave(clienteUid, clienteId, clave, valor) {
    return (async () => {
        await testEnvironment.withSecurityRulesDisabled(async (context) => {
            const database = context.firestore();
            await setDoc(doc(database, "usuarios", clienteUid), {
                rol: "CLIENTE", activo: true, clienteId, negocioId: NEGOCIO_A
            });
            await setDoc(
                doc(database, "clientes", String(clienteId)),
                fichaCliente(clienteId, NEGOCIO_A, clienteUid, "60900000X")
            );
        });
        const database = testEnvironment.authenticatedContext(clienteUid).firestore();
        return updateDoc(doc(database, "clientes", String(clienteId)), { [clave]: valor });
    })();
}

test("PRUEBA 131: el CLIENTE NO puede modificar moroso -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave("cliente-resumen-131", 6100, "moroso", true));
});

test("PRUEBA 132: el CLIENTE NO puede modificar deuda -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave("cliente-resumen-132", 6101, "deuda", 50));
});

test("PRUEBA 133: el CLIENTE NO puede modificar fechaEntradaMorosidad -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave(
        "cliente-resumen-133", 6102, "fechaEntradaMorosidad", Timestamp.fromMillis(1700000000000)
    ));
});

test("PRUEBA 134: el CLIENTE NO puede modificar exentoMorosidad -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave("cliente-resumen-134", 6103, "exentoMorosidad", true));
});

test("PRUEBA 135: el CLIENTE NO puede modificar fechaInicioActual -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave(
        "cliente-resumen-135", 6104, "fechaInicioActual", Timestamp.fromMillis(1700000000000)
    ));
});

test("PRUEBA 136: el CLIENTE NO puede modificar fechaFinActual -> DENY", async () => {
    await assertFails(escenarioClienteIntentaCambiarClave(
        "cliente-resumen-136", 6105, "fechaFinActual", Timestamp.fromMillis(1704067200000)
    ));
});

// ============================================================================
// UNICIDAD GLOBAL DEL CÃ“DIGO MAESTRO (codigos_maestros/{codigo})
// ============================================================================

function docNegocio(adminUid, codigo) {
    return { adminUid, nombre: "Centro", codigoMaestro: codigo };
}
function docPublico(codigo) {
    return { nombre: "Centro", codigoMaestro: codigo };
}
function docCodigo(negocioId) {
    return { negocioId };
}

async function sembrarAdminSinNegocio(uid) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "usuarios", uid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: null
        });
    });
}

async function sembrarAdminConNegocio(uid, codigo) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(doc(db, "usuarios", uid), {
            rol: "ADMIN",
            activo: true,
            clienteId: null,
            negocioId: uid
        });
        await setDoc(doc(db, "negocios", uid), docNegocio(uid, codigo));
        await setDoc(doc(db, "negocios_publicos", uid), docPublico(codigo));
        await setDoc(doc(db, "codigos_maestros", codigo), docCodigo(uid));
    });
}

function batchCrearNegocioConCodigo(db, adminUid, codigo) {
    const batch = writeBatch(db);
    batch.set(doc(db, "negocios", adminUid), docNegocio(adminUid, codigo));
    batch.set(doc(db, "negocios_publicos", adminUid), docPublico(codigo));
    batch.update(doc(db, "usuarios", adminUid), { negocioId: adminUid });
    batch.set(doc(db, "codigos_maestros", codigo), docCodigo(adminUid));
    return batch.commit();
}

test("PRUEBA 137: crear negocio con cÃ³digo libre -> ALLOW", async () => {
    const admin = "admin-cod-137";
    const codigo = "C137";
    await sembrarAdminSinNegocio(admin);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(batchCrearNegocioConCodigo(db, admin, codigo));
});

test("PRUEBA 138: crear negocio con cÃ³digo ya existente -> DENY", async () => {
    const dueno = "admin-cod-138a";
    const admin = "admin-cod-138b";
    const codigo = "C138";
    await sembrarAdminConNegocio(dueno, codigo);
    await sembrarAdminSinNegocio(admin);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertFails(batchCrearNegocioConCodigo(db, admin, codigo));
});

test("PRUEBA 139: concurrencia - el segundo create sobre el mismo cÃ³digo falla", async () => {
    const a = "admin-cod-139a";
    const b = "admin-cod-139b";
    const codigo = "C139";
    await sembrarAdminSinNegocio(a);
    await sembrarAdminSinNegocio(b);
    const dbA = testEnvironment.authenticatedContext(a).firestore();
    await assertSucceeds(batchCrearNegocioConCodigo(dbA, a, codigo));
    const dbB = testEnvironment.authenticatedContext(b).firestore();
    await assertFails(batchCrearNegocioConCodigo(dbB, b, codigo));
});

test("PRUEBA 140: cambiar a cÃ³digo libre libera el anterior y reserva el nuevo", async () => {
    const admin = "admin-cod-140";
    const viejo = "C140V";
    const nuevo = "C140N";
    await sembrarAdminConNegocio(admin, viejo);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    const batch = writeBatch(db);
    batch.delete(doc(db, "codigos_maestros", viejo));
    batch.set(doc(db, "codigos_maestros", nuevo), docCodigo(admin));
    batch.update(doc(db, "negocios", admin), { codigoMaestro: nuevo });
    batch.update(doc(db, "negocios_publicos", admin), { codigoMaestro: nuevo });
    await assertSucceeds(batch.commit());
});

test("PRUEBA 141: cambiar a cÃ³digo ocupado por otro negocio -> DENY", async () => {
    const dueno = "admin-cod-141a";
    const admin = "admin-cod-141b";
    const propio = "C141B";
    const ocupado = "C141A";
    await sembrarAdminConNegocio(dueno, ocupado);
    await sembrarAdminConNegocio(admin, propio);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    const batch = writeBatch(db);
    batch.delete(doc(db, "codigos_maestros", propio));
    batch.set(doc(db, "codigos_maestros", ocupado), docCodigo(admin));
    batch.update(doc(db, "negocios", admin), { codigoMaestro: ocupado });
    batch.update(doc(db, "negocios_publicos", admin), { codigoMaestro: ocupado });
    await assertFails(batch.commit());
});

test("PRUEBA 142: mantener el mismo cÃ³digo (sin tocar codigos_maestros) -> ALLOW", async () => {
    const admin = "admin-cod-142";
    const codigo = "C142";
    await sembrarAdminConNegocio(admin, codigo);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    const batch = writeBatch(db);
    batch.update(doc(db, "negocios", admin), { codigoMaestro: codigo });
    batch.update(doc(db, "negocios_publicos", admin), { codigoMaestro: codigo });
    await assertSucceeds(batch.commit());
});

test("PRUEBA 143: solo el propio negocio puede liberar su reserva", async () => {
    const dueno = "admin-cod-143a";
    const otro = "admin-cod-143b";
    const codigo = "C143";
    await sembrarAdminConNegocio(dueno, codigo);
    await sembrarAdminConNegocio(otro, "C143B");
    const db = testEnvironment.authenticatedContext(otro).firestore();
    await assertFails(deleteDoc(doc(db, "codigos_maestros", codigo)));

    const dbDueno = testEnvironment.authenticatedContext(dueno).firestore();
    const batch = writeBatch(dbDueno);
    batch.delete(doc(dbDueno, "codigos_maestros", codigo));
    batch.set(doc(dbDueno, "codigos_maestros", "C143N"), docCodigo(dueno));
    batch.update(doc(dbDueno, "negocios", dueno), { codigoMaestro: "C143N" });
    batch.update(doc(dbDueno, "negocios_publicos", dueno), { codigoMaestro: "C143N" });
    await assertSucceeds(batch.commit());
});

test("PRUEBA 144: VÃA 1 - un autenticado puede leer la reserva de un cÃ³digo", async () => {
    const admin = "admin-cod-144";
    const codigo = "C144";
    await sembrarAdminConNegocio(admin, codigo);
    const db = testEnvironment.authenticatedContext(CLIENTE_UID).firestore();
    await assertSucceeds(getDoc(doc(db, "codigos_maestros", codigo)));
});

test("PRUEBA 145: un no autenticado NO puede leer codigos_maestros", async () => {
    const db = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "codigos_maestros", "C144")));
});

test("PRUEBA 146: no se puede enumerar codigos_maestros (list -> DENY)", async () => {
    const admin = "admin-cod-146";
    await sembrarAdminConNegocio(admin, "C146");
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertFails(getDocs(query(collection(db, "codigos_maestros"))));
});

test("PRUEBA 147: un Admin no puede reservar un cÃ³digo para otro negocio", async () => {
    const admin = "admin-cod-147";
    const codigo = "C147";
    await sembrarAdminSinNegocio(admin);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    // La reserva apuntarÃ­a a otro negocio y no hay coherencia con negocios del uid.
    await assertFails(
        setDoc(doc(db, "codigos_maestros", codigo), docCodigo("negocio-ajeno"))
    );
});

test("PRUEBA 148: un Admin no puede eliminar la reserva de otro negocio", async () => {
    const dueno = "admin-cod-148a";
    const otro = "admin-cod-148b";
    const codigo = "C148";
    await sembrarAdminConNegocio(dueno, codigo);
    await sembrarAdminConNegocio(otro, "C148B");
    const db = testEnvironment.authenticatedContext(otro).firestore();
    await assertFails(deleteDoc(doc(db, "codigos_maestros", codigo)));
});

test("PRUEBA 149: actualizar negocios a un cÃ³digo reservado por otro negocio -> DENY", async () => {
    const dueno = "admin-cod-149a";
    const admin = "admin-cod-149b";
    const ocupado = "C149A";
    await sembrarAdminConNegocio(dueno, ocupado);
    await sembrarAdminConNegocio(admin, "C149B");
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertFails(
        updateDoc(doc(db, "negocios", admin), { codigoMaestro: ocupado })
    );
});

test("PRUEBA 150: crear negocio SIN reservar el cÃ³digo en la misma operaciÃ³n -> DENY", async () => {
    const admin = "admin-cod-150";
    const codigo = "C150";
    await sembrarAdminSinNegocio(admin);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    const batch = writeBatch(db);
    batch.set(doc(db, "negocios", admin), docNegocio(admin, codigo));
    batch.set(doc(db, "negocios_publicos", admin), docPublico(codigo));
    batch.update(doc(db, "usuarios", admin), { negocioId: admin });
    await assertFails(batch.commit());
});

// =========================================================
// DENUNCIAS UGC (PRUEBA 151+)
// =========================================================

function denunciaDoc(negocioId, denuncianteUid, extra = {}) {
    return {
        negocioId,
        denuncianteUid,
        tipo: "FOTO_CLIENTE",
        referencia: "clientes/123",
        motivo: "INAPROPIADO",
        fecha: Timestamp.now(),
        estado: "PENDIENTE",
        ...extra
    };
}

async function setDenunciaComo(uid, idDenuncia, datos) {
    const db = testEnvironment.authenticatedContext(uid).firestore();
    await setDoc(doc(db, "denuncias", idDenuncia), datos);
}

async function sembrarDenunciaDirecta(idDenuncia, datos) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "denuncias", idDenuncia), datos);
    });
}

test("PRUEBA 151: el ADMIN denuncia la foto de un CLIENTE sin vÃ­nculo (solo contenido) -> ALLOW", async () => {
    const admin = "admin-den-151";
    await seedAdminNotif(admin, NEGOCIO_A);
    const datos = denunciaDoc(NEGOCIO_A, admin, {
        tipo: "FOTO_CLIENTE",
        referencia: "clientes/151"
    });
    await assertSucceeds(setDenunciaComo(admin, "denuncia-151", datos));
});

test("PRUEBA 152: el ADMIN denuncia al usuario CLIENTE (foto con firebaseUid) -> ALLOW", async () => {
    const admin = "admin-den-152";
    const denunciado = "cliente-den-152";
    await seedAdminNotif(admin, NEGOCIO_A);
    await seedClienteNotif(denunciado, 15201, NEGOCIO_A);
    const datos = denunciaDoc(NEGOCIO_A, admin, {
        tipo: "FOTO_CLIENTE",
        referencia: "clientes/15201",
        usuarioDenunciadoUid: denunciado
    });
    await assertSucceeds(setDenunciaComo(admin, "denuncia-152", datos));
});

test("PRUEBA 153: el CLIENTE denuncia una notificaciÃ³n MANUAL y al ADMIN creador -> ALLOW", async () => {
    const cliente = "cliente-den-153";
    await seedClienteNotif(cliente, 15301, NEGOCIO_A);
    const datos = denunciaDoc(NEGOCIO_A, cliente, {
        tipo: "NOTIFICACION",
        referencia: "notificaciones/notif-153",
        usuarioDenunciadoUid: NEGOCIO_A,
        motivo: "OFENSIVO"
    });
    await assertSucceeds(setDenunciaComo(cliente, "denuncia-153", datos));
});

test("PRUEBA 154: el CLIENTE denuncia al ADMIN (logo del negocio) -> ALLOW", async () => {
    const cliente = "cliente-den-154";
    await seedClienteNotif(cliente, 15401, NEGOCIO_A);
    const datos = denunciaDoc(NEGOCIO_A, cliente, {
        tipo: "LOGO_NEGOCIO",
        referencia: `negocios_publicos/${NEGOCIO_A}`,
        usuarioDenunciadoUid: NEGOCIO_A,
        motivo: "INAPROPIADO"
    });
    await assertSucceeds(setDenunciaComo(cliente, "denuncia-154", datos));
});

test("PRUEBA 155: el CLIENTE denuncia el logo (solo contenido, sin usuario) -> ALLOW", async () => {
    const cliente = "cliente-den-155";
    await seedClienteNotif(cliente, 15501, NEGOCIO_A);
    const datos = denunciaDoc(NEGOCIO_A, cliente, {
        tipo: "LOGO_NEGOCIO",
        referencia: `negocios_publicos/${NEGOCIO_A}`
    });
    await assertSucceeds(setDenunciaComo(cliente, "denuncia-155", datos));
});

test("PRUEBA 156: un usuario NO autenticado no puede crear denuncias -> DENY", async () => {
    const db = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(
        setDoc(doc(db, "denuncias", "denuncia-156"), denunciaDoc(NEGOCIO_A, "x"))
    );
});

test("PRUEBA 157: un usuario de OTRO negocio no puede denunciar contenido de otro negocio -> DENY", async () => {
    const clienteB = "cliente-den-157b";
    await seedClienteNotif(clienteB, 15701, NEGOCIO_B);
    await assertFails(
        setDenunciaComo(
            clienteB,
            "denuncia-157-b",
            denunciaDoc(NEGOCIO_A, clienteB, { referencia: "clientes/157" })
        )
    );
    const adminA = "admin-den-157a";
    await seedAdminNotif(adminA, NEGOCIO_A);
    await assertFails(
        setDenunciaComo(
            adminA,
            "denuncia-157-a",
            denunciaDoc(NEGOCIO_B, adminA, { referencia: "clientes/157" })
        )
    );
});

test("PRUEBA 158: denuncianteUid distinto de auth.uid -> DENY", async () => {
    const cliente = "cliente-den-158";
    await seedClienteNotif(cliente, 15801, NEGOCIO_A);
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-158",
            denunciaDoc(NEGOCIO_A, "otro-uid", { referencia: "clientes/158" })
        )
    );
});

test("PRUEBA 159: el CLIENTE no puede listar denuncias ni leer denuncias ajenas -> DENY", async () => {
    const cliente = "cliente-den-159";
    await seedClienteNotif(cliente, 15901, NEGOCIO_A);
    await sembrarDenunciaDirecta("denuncia-159-ajena", denunciaDoc(NEGOCIO_A, "otro-denunciante"));
    const db = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(getDoc(doc(db, "denuncias", "denuncia-159-ajena")));
    await assertFails(getDocs(query(collection(db, "denuncias"))));
});

test("PRUEBA 160: el ADMIN lee y lista las denuncias de SU negocio -> ALLOW; las de otro negocio -> DENY", async () => {
    const adminA = "admin-den-160a";
    const adminB = "admin-den-160b";
    await seedAdminNotif(adminA, NEGOCIO_A);
    await seedAdminNotif(adminB, NEGOCIO_B);
    await sembrarDenunciaDirecta("denuncia-160-a", denunciaDoc(NEGOCIO_A, adminA));
    await sembrarDenunciaDirecta("denuncia-160-b", denunciaDoc(NEGOCIO_B, adminB));
    const dbA = testEnvironment.authenticatedContext(adminA).firestore();
    await assertSucceeds(getDoc(doc(dbA, "denuncias", "denuncia-160-a")));
    await assertSucceeds(getDocs(query(collection(dbA, "denuncias"), where("negocioId", "==", NEGOCIO_A))));
    await assertFails(getDoc(doc(dbA, "denuncias", "denuncia-160-b")));
});

test("PRUEBA 161: el ADMIN marca REVISADA -> ALLOW; el CLIENTE no; cambiar otro campo -> DENY", async () => {
    const adminA = "admin-den-161";
    const cliente = "cliente-den-161";
    await seedAdminNotif(adminA, NEGOCIO_A);
    await seedClienteNotif(cliente, 16101, NEGOCIO_A);
    await sembrarDenunciaDirecta("denuncia-161", denunciaDoc(NEGOCIO_A, adminA));

    const dbAdmin = testEnvironment.authenticatedContext(adminA).firestore();
    await assertSucceeds(updateDoc(doc(dbAdmin, "denuncias", "denuncia-161"), { estado: "REVISADA" }));
    await assertFails(updateDoc(doc(dbAdmin, "denuncias", "denuncia-161"), { tipo: "NOTIFICACION" }));

    const dbCliente = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(updateDoc(doc(dbCliente, "denuncias", "denuncia-161"), { estado: "REVISADA" }));
});

test("PRUEBA 162: autodenuncia y creaciÃ³n con estado distinto de PENDIENTE -> DENY", async () => {
    const cliente = "cliente-den-162";
    await seedClienteNotif(cliente, 16201, NEGOCIO_A);
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-162-self",
            denunciaDoc(NEGOCIO_A, cliente, { usuarioDenunciadoUid: cliente })
        )
    );
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-162-estado",
            denunciaDoc(NEGOCIO_A, cliente, { estado: "REVISADA" })
        )
    );
});

test("PRUEBA 163: tipo y motivo no permitidos (contenido no denunciable ni automÃ¡tico) -> DENY", async () => {
    const cliente = "cliente-den-163";
    await seedClienteNotif(cliente, 16301, NEGOCIO_A);
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-163-tipo",
            denunciaDoc(NEGOCIO_A, cliente, { tipo: "COMENTARIO" })
        )
    );
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-163-origen",
            denunciaDoc(NEGOCIO_A, cliente, { tipo: "BAJA_CONFIRMADA" })
        )
    );
    await assertFails(
        setDenunciaComo(
            cliente,
            "denuncia-163-motivo",
            denunciaDoc(NEGOCIO_A, cliente, { motivo: "SPAM" })
        )
    );
});

// =========================================================
// AGENDA DERIVADA DEL CLIENTE (PRUEBA 164+)
// =========================================================
// La agenda vive en clientes/{clienteId}/agenda/{fecha} con la estructura
// { negocioId, fecha, sesiones: { [sesionId]: idServicio } }. Es un Ã­ndice
// DERIVADO: el ADMIN de su negocio puede leerlo/crearlo/actualizarlo/borrarlo
// (cascadas); el CLIENTE solo puede leer SU PROPIA agenda.

function agendaDoc(fecha, negocioId, extra = {}) {
    return {
        negocioId,
        fecha,
        sesiones: { "7001": 3 },
        ...extra
    };
}

async function seedAgenda(clienteId, negocioId, fecha, datos) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "clientes", String(clienteId)),
            fichaCliente(clienteId, negocioId, `cliente-agenda-${clienteId}`, `77${clienteId}77A`)
        );
        await setDoc(
            doc(context.firestore(), "clientes", String(clienteId), "agenda", String(fecha)),
            agendaDoc(fecha, negocioId, datos)
        );
    });
}

test("PRUEBA 164: el ADMIN de SU negocio lee, crea y borra la agenda de un cliente -> ALLOW", async () => {
    const admin = "admin-agenda-164";
    await seedAdminNotif(admin, NEGOCIO_A);
    await seedAgenda(16401, NEGOCIO_A, 1700000000000);

    const db = testEnvironment.authenticatedContext(admin).firestore();
    const ref = doc(db, "clientes", "16401", "agenda", "1700000000000");

    await assertSucceeds(getDoc(ref));
    await assertSucceeds(
        setDoc(doc(db, "clientes", "16401", "agenda", "1700000000001"), agendaDoc(1700000000001, NEGOCIO_A))
    );
    await assertSucceeds(
        updateDoc(ref, { sesiones: { "7002": 4 } })
    );
    await assertSucceeds(deleteDoc(ref));
});

test("PRUEBA 165: el ADMIN de OTRO negocio no accede a la agenda de un cliente ajeno -> DENY", async () => {
    const adminB = "admin-agenda-165b";
    await seedAdminNotif(adminB, NEGOCIO_B);
    await seedAgenda(16501, NEGOCIO_A, 1700000000000);

    const db = testEnvironment.authenticatedContext(adminB).firestore();
    const ref = doc(db, "clientes", "16501", "agenda", "1700000000000");

    await assertFails(getDoc(ref));
    await assertFails(
        updateDoc(ref, { sesiones: { "7002": 4 } })
    );
    await assertFails(deleteDoc(ref));
});

test("PRUEBA 166: el CLIENTE lee SU PROPIA agenda (un dÃ­a concreto) -> ALLOW", async () => {
    const cliente = "cliente-agenda-166";
    const clienteId = 16601;
    await seedClienteNotif(cliente, clienteId, NEGOCIO_A);
    await seedAgenda(clienteId, NEGOCIO_A, 1700000000000);

    const db = testEnvironment.authenticatedContext(cliente).firestore();
    const ref = doc(db, "clientes", String(clienteId), "agenda", "1700000000000");

    await assertSucceeds(getDoc(ref));
});

test("PRUEBA 167: el CLIENTE NO puede crear, modificar ni borrar su propia agenda -> DENY", async () => {
    const cliente = "cliente-agenda-167";
    const clienteId = 16701;
    await seedClienteNotif(cliente, clienteId, NEGOCIO_A);
    await seedAgenda(clienteId, NEGOCIO_A, 1700000000000);

    const db = testEnvironment.authenticatedContext(cliente).firestore();
    const ref = doc(db, "clientes", String(clienteId), "agenda", "1700000000000");

    await assertFails(
        setDoc(doc(db, "clientes", String(clienteId), "agenda", "1700000000001"), agendaDoc(1700000000001, NEGOCIO_A))
    );
    await assertFails(
        updateDoc(ref, { sesiones: { "7002": 4 } })
    );
    await assertFails(deleteDoc(ref));
});

test("PRUEBA 168: el CLIENTE NO puede leer la agenda de otro cliente -> DENY", async () => {
    const cliente = "cliente-agenda-168";
    const otroClienteId = 16899;
    await seedClienteNotif(cliente, 16801, NEGOCIO_A);
    await seedAgenda(otroClienteId, NEGOCIO_A, 1700000000000);

    const db = testEnvironment.authenticatedContext(cliente).firestore();
    const ref = doc(db, "clientes", String(otroClienteId), "agenda", "1700000000000");

    await assertFails(getDoc(ref));
    await assertFails(
        getDocs(query(collection(db, "clientes", String(otroClienteId), "agenda")))
    );
});

test("PRUEBA 169: usuario NO autenticado no accede a ninguna agenda -> DENY", async () => {
    await seedAgenda(16901, NEGOCIO_A, 1700000000000);
    const db = testEnvironment.unauthenticatedContext().firestore();
    const ref = doc(db, "clientes", "16901", "agenda", "1700000000000");

    await assertFails(getDoc(ref));
    await assertFails(
        setDoc(doc(db, "clientes", "16901", "agenda", "1700000000002"), agendaDoc(1700000000002, NEGOCIO_A))
    );
    await assertFails(deleteDoc(ref));
});

// =========================================================
// FASE 4 — CIERRE DEL ACCESO DIRECTO DEL CLIENTE A RESERVAS
// =========================================================
// El CLIENTE ya NO crea/elimina reservas directamente ni modifica sesiones:
// reservar y cancelarReserva viven en las Cloud Functions callable (Admin SDK),
// que no pasan por estas Rules. Solo se mantiene la LECTURA de sus reservas y
// sesiones/servicios legítimos. El ADMIN conserva la gestión.

async function seedClienteFase4(clienteUid, clienteId, negocioId, servicios) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, "usuarios", clienteUid), {
            rol: "CLIENTE", activo: true, clienteId, negocioId
        });
        await setDoc(
            doc(database, "clientes", String(clienteId)),
            fichaCliente(clienteId, negocioId, clienteUid, `F4${clienteId}X`, { serviciosContratados: servicios })
        );
        for (const idServicio of servicios) {
            await setDoc(doc(database, "servicios", String(idServicio)), servicioDoc(idServicio, negocioId));
        }
    });
}

test("PRUEBA 170: CLIENTE create reserva directa -> DENY (aunque tenga servicio contratado y ACTIVO)", async () => {
    const cliente = "cliente-f4-170";
    const clienteId = 17001;
    const sesionId = 17002;
    await seedClienteFase4(cliente, clienteId, NEGOCIO_A, [17003]);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", String(sesionId)),
            sesionDoc(sesionId, NEGOCIO_A, 17003)
        );
    });
    const database = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(
        setDoc(
            doc(database, "reservas", `${clienteId}_${sesionId}`),
            reservaDoc(clienteId, sesionId, NEGOCIO_A)
        )
    );
});

test("PRUEBA 171: CLIENTE delete reserva directa -> DENY (incluso la suya propia)", async () => {
    const cliente = "cliente-f4-171";
    const clienteId = 17101;
    const sesionId = 17102;
    await seedClienteFase4(cliente, clienteId, NEGOCIO_A, [17103]);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(
            doc(database, "sesiones", String(sesionId)),
            sesionDoc(sesionId, NEGOCIO_A, 17103)
        );
        await setDoc(
            doc(database, "reservas", `${clienteId}_${sesionId}`),
            reservaDoc(clienteId, sesionId, NEGOCIO_A)
        );
    });
    const database = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(
        deleteDoc(doc(database, "reservas", `${clienteId}_${sesionId}`))
    );
    // La lectura de su propia reserva sigue permitida.
    await assertSucceeds(
        getDoc(doc(database, "reservas", `${clienteId}_${sesionId}`))
    );
});

test("PRUEBA 172: CLIENTE no puede modificar plazasDisponibles ni asistentes de una sesion -> DENY", async () => {
    const cliente = "cliente-f4-172";
    const clienteId = 17201;
    const sesionId = 17202;
    await seedClienteFase4(cliente, clienteId, NEGOCIO_A, [17203]);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(
            doc(database, "sesiones", String(sesionId)),
            sesionDoc(sesionId, NEGOCIO_A, 17203, { asistentes: { [String(clienteId)]: "Cliente" } })
        );
        await setDoc(
            doc(database, "reservas", `${clienteId}_${sesionId}`),
            reservaDoc(clienteId, sesionId, NEGOCIO_A)
        );
    });
    const database = testEnvironment.authenticatedContext(cliente).firestore();
    const sesionRef = doc(database, "sesiones", String(sesionId));
    await assertFails(updateDoc(sesionRef, { plazasDisponibles: 4 }));
    await assertFails(updateDoc(sesionRef, { [`asistentes.${clienteId}`]: "Otro" }));
});

test("PRUEBA 173: CLIENTE solo lee SU agenda (ALLOW) y no escribe la propia -> DENY (ya en 166/167, regresión)", async () => {
    const cliente = "cliente-f4-173";
    const clienteId = 17301;
    await seedClienteNotif(cliente, clienteId, NEGOCIO_A);
    await seedAgenda(clienteId, NEGOCIO_A, 1700000000000);
    const database = testEnvironment.authenticatedContext(cliente).firestore();
    await assertSucceeds(
        getDoc(doc(database, "clientes", String(clienteId), "agenda", "1700000000000"))
    );
    await assertFails(
        updateDoc(doc(database, "clientes", String(clienteId), "agenda", "1700000000000"), {
            sesiones: { "7": 9 }
        })
    );
});

test("PRUEBA 174: CLIENTE NO accede a agenda de otro cliente ni de otro negocio -> DENY", async () => {
    const clienteA = "cliente-f4-174a";
    await seedClienteNotif(clienteA, 17401, NEGOCIO_A);
    await seedAgenda(17499, NEGOCIO_A, 1700000000000); // ficha de otro cliente del MISMO negocio
    const database = testEnvironment.authenticatedContext(clienteA).firestore();
    await assertFails(
        getDoc(doc(database, "clientes", "17499", "agenda", "1700000000000"))
    );
    // Agenda de un cliente del negocio B (otro negocio): también DENY.
    const adminB = "admin-f4-174b";
    await seedAdminNotif(adminB, NEGOCIO_B);
    const dbAdminB = testEnvironment.authenticatedContext(adminB).firestore();
    await assertFails(
        getDoc(doc(dbAdminB, "clientes", "17499", "agenda", "1700000000000"))
    );
});

test("PRUEBA 175: ADMIN de SU negocio gestiona reservas, sesiones y agenda -> ALLOW", async () => {
    const admin = "admin-f4-175";
    await seedAdminNotif(admin, NEGOCIO_A);
    await seedClienteFase4("cliente-f4-175", 17501, NEGOCIO_A, [17503]);
    await seedAgenda(17501, NEGOCIO_A, 1700000000000);
    const database = testEnvironment.authenticatedContext(admin).firestore();

    const sesionId = 17502;
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", String(sesionId)),
            sesionDoc(sesionId, NEGOCIO_A, 17503)
        );
        await setDoc(
            doc(context.firestore(), "reservas", "17501_17502"),
            reservaDoc(17501, sesionId, NEGOCIO_A)
        );
    });

    // Lectura de reserva, sesión y agenda.
    await assertSucceeds(getDoc(doc(database, "reservas", "17501_17502")));
    await assertSucceeds(getDoc(doc(database, "sesiones", String(sesionId))));
    await assertSucceeds(
        getDoc(doc(database, "clientes", "17501", "agenda", "1700000000000"))
    );

    // ADMIN puede retirar el asistente y limpiar la agenda en su gestión.
    await assertSucceeds(
        updateDoc(doc(database, "sesiones", String(sesionId)), {
            asistentes: {}
        })
    );
    await assertSucceeds(
        updateDoc(doc(database, "clientes", "17501", "agenda", "1700000000000"), {
            sesiones: {}
        })
    );
    await assertSucceeds(
        deleteDoc(doc(database, "clientes", "17501", "agenda", "1700000000000"))
    );
});

test("PRUEBA 176: ADMIN de OTRO negocio no gestiona reservas/sesiones/agenda ajenas -> DENY", async () => {
    const adminB = "admin-f4-176b";
    await seedAdminNotif(adminB, NEGOCIO_B);
    await seedAgenda(17601, NEGOCIO_A, 1700000000000); // cliente del negocio A
    const dbB = testEnvironment.authenticatedContext(adminB).firestore();
    await assertFails(
        getDoc(doc(dbB, "clientes", "17601", "agenda", "1700000000000"))
    );
    await assertFails(
        updateDoc(doc(dbB, "clientes", "17601", "agenda", "1700000000000"), { sesiones: {} })
    );
    await assertFails(
        deleteDoc(doc(dbB, "clientes", "17601", "agenda", "1700000000000"))
    );
});

test("PRUEBA 177: lectura normal de sesiones y servicios del CLIENTE sigue funcionando -> ALLOW", async () => {
    const cliente = "cliente-f4-177";
    const clienteId = 17701;
    await seedClienteFase4(cliente, clienteId, NEGOCIO_A, [17703]);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(
            doc(context.firestore(), "sesiones", "17702"),
            sesionDoc(17702, NEGOCIO_A, 17703)
        );
    });
    const database = testEnvironment.authenticatedContext(cliente).firestore();
    await assertSucceeds(getDoc(doc(database, "servicios", "17703")));
    await assertSucceeds(getDoc(doc(database, "sesiones", "17702")));
});

test("PRUEBA 178: permiteCombinarDia del servicio solo lo modifica el ADMIN de SU negocio -> ALLOW; CLIENTE/otro negocio -> DENY", async () => {
    const admin = "admin-f4-178";
    await seedAdminNotif(admin, NEGOCIO_A);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "servicios", "17801"), servicioDoc(17801, NEGOCIO_A));
    });
    const dbAdmin = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        updateDoc(doc(dbAdmin, "servicios", "17801"), { permiteCombinarDia: false })
    );
    await assertSucceeds(
        setDoc(doc(dbAdmin, "servicios", "17802"), servicioDoc(17802, NEGOCIO_A, { permiteCombinarDia: true }))
    );

    const adminB = "admin-f4-178b";
    await seedAdminNotif(adminB, NEGOCIO_B);
    const dbAdminB = testEnvironment.authenticatedContext(adminB).firestore();
    await assertFails(
        updateDoc(doc(dbAdminB, "servicios", "17801"), { permiteCombinarDia: false })
    );

    const cliente = "cliente-f4-178c";
    await seedClienteFase4(cliente, 17899, NEGOCIO_A, [17801]);
    const dbCliente = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(
        updateDoc(doc(dbCliente, "servicios", "17801"), { permiteCombinarDia: false })
    );
});

// =========================================================
// HORARIO DEL NEGOCIO (PRUEBA 179+)
// =========================================================

test("PRUEBA 179: el ADMIN guarda el horario (centro y actividades) en negocios_publicos -> ALLOW", async () => {
    const admin = "admin-horario-179";
    const negocioId = "negocio-horario-179";
    await seedAdminNotif(admin, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "negocios_publicos", negocioId), {
            nombre: "Gimnasio Horario",
            codigoMaestro: "MAESTRO-H179"
        });
    });
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        updateDoc(doc(db, "negocios_publicos", negocioId), {
            horarioCentro: {
                MONDAY: { cerrado: false, apertura: "09:00", cierre: "21:00" },
                TUESDAY: { cerrado: true, apertura: "", cierre: "" }
            },
            horarioActividades: {
                MONDAY: [{ idServicio: 7, hora: "18:00" }, { idServicio: 9, hora: "18:00" }]
            }
        })
    );
});

test("PRUEBA 180: un CLIENTE no puede guardar el horario en negocios_publicos -> DENY", async () => {
    const cliente = "cliente-horario-180";
    const negocioId = "negocio-horario-180";
    await seedClienteNotif(cliente, 18001, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "negocios_publicos", negocioId), {
            nombre: "Gimnasio Horario",
            codigoMaestro: "MAESTRO-H180"
        });
    });
    const db = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(
        updateDoc(doc(db, "negocios_publicos", negocioId), {
            horarioCentro: { MONDAY: { cerrado: false, apertura: "09:00", cierre: "21:00" } }
        })
    );
});

test("PRUEBA 181: el ADMIN crea/actualiza la configuracion con el switch cambioHorario -> ALLOW", async () => {
    const admin = "admin-horario-181";
    const negocioId = "negocio-horario-181";
    await seedAdminNotif(admin, negocioId);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        setDoc(doc(db, "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: false, recordatorioHoras: 0 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: true }
        })
    );
    await assertSucceeds(
        updateDoc(doc(db, "configuracion_notificaciones", negocioId), {
            cambioHorario: { activa: false }
        })
    );
});

test("PRUEBA 182: el ADMIN crea una notificacion tipo CAMBIO_HORARIO -> ALLOW", async () => {
    const admin = "admin-horario-182";
    const negocioId = "negocio-horario-182";
    await seedAdminNotif(admin, negocioId);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        setDoc(doc(db, "notificaciones", "n-cambio-horario-182"), {
            negocioId,
            titulo: "Cambio de horario",
            mensaje: "El horario del centro ha cambiado.",
            tipo: "CAMBIO_HORARIO",
            origen: "MANUAL",
            modoDestino: "TODOS",
            idsClientes: [1, 2],
            fechaCreacion: Timestamp.now(),
            programada: false,
            estado: "PENDIENTE"
        })
    );
});

test("PRUEBA 183: el ADMIN crea un buzon tipo CAMBIO_HORARIO -> ALLOW", async () => {
    const admin = "admin-horario-183";
    const negocioId = "negocio-horario-183";
    await seedAdminNotif(admin, negocioId);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        setDoc(
            doc(db, "notificaciones_por_destinatario", "1_n-cambio-horario-183"),
            notifDestinatarioDoc(negocioId, 1, "n-cambio-horario-183", CLIENTE_UID, {
                tipo: "CAMBIO_HORARIO"
            })
        )
    );
});

test("PRUEBA 185: el ADMIN guarda el horario con el MISMO batch que la app (negocios + negocios_publicos) -> ALLOW", async () => {
    const admin = "admin-horario-185";
    const negocioId = "negocio-horario-185";
    await seedAdminNotif(admin, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        const d = context.firestore();
        await setDoc(doc(d, "negocios", negocioId), {
            adminUid: admin,
            nombre: "Gimnasio Horario",
            codigoMaestro: "MAESTRO-H185"
        });
        await setDoc(doc(d, "negocios_publicos", negocioId), {
            nombre: "Gimnasio Horario",
            codigoMaestro: "MAESTRO-H185"
        });
    });
    const db = testEnvironment.authenticatedContext(admin).firestore();
    const datos = {
        horarioCentro: {
            MONDAY: { cerrado: false, apertura: "09:00", cierre: "21:00" }
        },
        horarioActividades: {
            MONDAY: [{ idServicio: 7, hora: "18:00" }]
        },
        horarioExcepciones: [
            { fecha: 1790000000000, cerrado: true, apertura: "", cierre: "" }
        ]
    };
    const batch = writeBatch(db);
    batch.update(doc(db, "negocios", negocioId), datos);
    batch.update(doc(db, "negocios_publicos", negocioId), datos);
    await assertSucceeds(batch.commit());
});

test("PRUEBA 184: el ADMIN guarda excepciones de horario (dias concretos) -> ALLOW", async () => {
    const admin = "admin-horario-184";
    const negocioId = "negocio-horario-184";
    await seedAdminNotif(admin, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "negocios_publicos", negocioId), {
            nombre: "Gimnasio Horario",
            codigoMaestro: "MAESTRO-H184"
        });
    });
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        updateDoc(doc(db, "negocios_publicos", negocioId), {
            horarioExcepciones: [
                { fecha: 1790000000000, cerrado: true, apertura: "", cierre: "" },
                { fecha: 1791000000000, cerrado: false, apertura: "10:00", cierre: "14:00" }
            ]
        })
    );
});

// =========================================================
// CONFIGURACION DE NOTIFICACIONES (regresion guardado ADMIN)
// =========================================================

test("PRUEBA 186: el ADMIN propietario puede leer su configuracion aunque aun no exista (get) -> ALLOW", async () => {
    const admin = "admin-config-186";
    const negocioId = "negocio-config-186";
    await seedAdminNotif(admin, negocioId);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    // La app hace get() antes de crear; el documento todavia no existe.
    await assertSucceeds(getDoc(doc(db, "configuracion_notificaciones", negocioId)));
});

test("PRUEBA 187: el ADMIN propietario guarda la configuracion con los 4 bloques -> ALLOW", async () => {
    const admin = "admin-config-187";
    const negocioId = "negocio-config-187";
    await seedAdminNotif(admin, negocioId);
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        setDoc(doc(db, "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: true }
        })
    );
});

test("PRUEBA 188: el ADMIN propietario modifica solo cambioHorario -> ALLOW", async () => {
    const admin = "admin-config-188";
    const negocioId = "negocio-config-188";
    await seedAdminNotif(admin, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: false, recordatorioHoras: 0 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: false }
        });
    });
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        updateDoc(doc(db, "configuracion_notificaciones", negocioId), {
            cambioHorario: { activa: true }
        })
    );
});

test("PRUEBA 189: el ADMIN propietario modifica morosidad y bajaConfirmada -> ALLOW", async () => {
    const admin = "admin-config-189";
    const negocioId = "negocio-config-189";
    await seedAdminNotif(admin, negocioId);
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: false, recordatorioHoras: 0 },
            bajaConfirmada: { activa: false },
            cambioHorario: { activa: true }
        });
    });
    const db = testEnvironment.authenticatedContext(admin).firestore();
    await assertSucceeds(
        updateDoc(doc(db, "configuracion_notificaciones", negocioId), {
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true }
        })
    );
});

test("PRUEBA 190: el ADMIN de OTRO negocio no puede leer ni guardar la configuracion -> DENY", async () => {
    const adminAjeno = "admin-config-190";
    const negocioId = "negocio-config-190";
    await seedAdminNotif(adminAjeno, "negocio-ajeno-190");
    const db = testEnvironment.authenticatedContext(adminAjeno).firestore();
    await assertFails(getDoc(doc(db, "configuracion_notificaciones", negocioId)));
    await assertFails(
        setDoc(doc(db, "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: true }
        })
    );
});

test("PRUEBA 191: el CLIENTE no puede leer ni guardar la configuracion -> DENY", async () => {
    const cliente = "cliente-config-191";
    const negocioId = "negocio-config-191";
    await seedClienteNotif(cliente, 19101, negocioId);
    const db = testEnvironment.authenticatedContext(cliente).firestore();
    await assertFails(getDoc(doc(db, "configuracion_notificaciones", negocioId)));
    await assertFails(
        setDoc(doc(db, "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: true }
        })
    );
});

test("PRUEBA 192: sin autenticacion no se puede leer ni guardar la configuracion -> DENY", async () => {
    const negocioId = "negocio-config-192";
    const db = testEnvironment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "configuracion_notificaciones", negocioId)));
    await assertFails(
        setDoc(doc(db, "configuracion_notificaciones", negocioId), {
            negocioId,
            morosidad: { activa: true, recordatorioHoras: 24 },
            bajaConfirmada: { activa: true },
            cambioHorario: { activa: true }
        })
    );
});
