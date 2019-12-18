package net.corda.training.flow

import net.corda.core.contracts.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Instrucciones de ejercicio práctico para flujos parte 2.
 * Descomente las pruebas y utilice los consejos + descripciones de tareas para completar el flujo para que las pruebas pasen.
 */
class TDBOTransferirFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.training"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(TDBOEmitirFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(TDBOTransferirFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Emitir un TDBO en el libro mayor, necesitamos hacer esto antes de transferir uno.
     */
    private fun emitirTDBO(iou: EstadoTDBO): SignedTransaction {
        val flow = TDBOEmitirFlow(iou)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Tarea 1.
     * Construir los inicios de [TDBOTransferirFlow]!
     * TODO: Implementar el [TDBOTransferirFlow] que construya y devuelva una transacción parcialmente firmada ([SignedTransaction]).
     * Consejo:
     * - Este flujo se verá similar al [TDBOEmitirFlow].
     * - Esta vez nuestra transacción tiene un estado de entrada, asi que necesitamos obtenerlo de la bóveda!
     * - Puedes utilizar el método [serviceHub.vaultService.queryBy] para obtener los ultimos linear states de un tipo
     *   en específico de la bóveda. Este devuelve una lista de los estados que igualen la búsqueda (queryBy).
     * - Use el [UniqueIdentifier] que es pasado al flujo para obtener el [EstadoTDBO] correcto.
     * - Use el método [EstadoTDBO.conNuevoPrestamista] para crear una copia del estado con un nuevo prestamista.
     * - Cree comando [Command] - necesitaremos utilizar el comando Transferir.
     * - Recuerda, como estamos involucrando a tres partes necesitaremos recolectar 3 firmas, por lo tanto necesitamos agregar
     *   tres llaves públicas ([PublicKey]) a la lista de los firmantes del comando.Podemos obtener los firmantes del TDBO de entrada
     *   y del TDBO que acabas de crear con el nuevo prestamista.
     * - Verifica y firma la transacción como lo hiciste con el [TDBOEmitirFlow].
     * - Devuelva la transacción parcialmente firmada.
     */
    @Test
    fun flowDevuelveTransaccionParcialmenteFirmadaBienFormada() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, prestamista, deudor))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOTransferirFlow(tdboEntrada.linearId, c.info.chooseIdentityAndCert().party)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val ptx = futuro.getOrThrow()
        // Comprueba que la transacción est+a bien formada...
        // Un EstadoTDBO de salida, una referencia de estado de entrada y un comando Transferir con las propiedades correctas.
        assert(ptx.tx.inputs.size == 1)
        assert(ptx.tx.outputs.size == 1)
        assert(ptx.tx.inputs.single() == StateRef(stx.id, 0))
        println("State ref entrada: ${ptx.tx.inputs.single()} == ${StateRef(stx.id, 0)}")
        val outputIou = ptx.tx.outputs.single().data as EstadoTDBO
        println("Estado salida: $outputIou")
        val command = ptx.tx.commands.single()
        assert(command.value == ContratoTDBO.Commands.Transferir())
        ptx.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey, c.info.chooseIdentityAndCert().party.owningKey,
                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Tarea 2.
     * Debemos asegurarnos que únicamente el prestamista actual puede ejecutar este flujo.
     * TODO: Agrega al [TDBOTransferirFlow] para que solo el prestamista actual pueda ejecutar este flow.
     * Consejo:
     * - Recuerda: Puedes utilizar la identidad del nodo y compararla con el objeto [Party] en el [EstadoTDBO] que
     *   obtuviste de la bóveda.
     * - Lanza un [IllegalArgumentException] si el participante [Party] intenta ejecutar el flujo!
     */
    @Test
    fun flowPuedeSerEjecutadoSoloPorPrestamistaActual() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, prestamista, deudor))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOTransferirFlow(tdboEntrada.linearId, c.info.chooseIdentityAndCert().party)
        val futuro = b.startFlow(flujo)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { futuro.getOrThrow() }
    }

    /**
     * Tarea 3.
     * Comprueba que un [EstadoTDBO] no puede ser transferido al mismo prestamista.
     * TODO: No deberías de hacer nada adicional para que esta prueba pase. ¡Agárrate los Pantalones!
     */
    @Test
    fun tdboNoPuedeSerTransferidoAlMismoParticipante() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, prestamista, deudor))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOTransferirFlow(tdboEntrada.linearId, prestamista)
        val future = a.startFlow(flujo)
        mockNetwork.runNetwork()
        // Comprueba que no podemos transferir un TDBO al mismo prestamista
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    /**
     * Tarea 4.
     * Obten la firma del deudor y del nuevo prestamista.
     * TODO: Agrega al [TDBOTransferirFlow] para manejar la recolección de firmas de multiples participantes [Party].
     * Consejo: Usa [initiateFlow] y el [CollectSignaturesFlow] de la misma manera que en [TDBOEmitirFlow].
     */
    @Test
    fun flowDevuelveTransaccionFirmadaPorTodasLasPartes() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, prestamista, deudor))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOTransferirFlow(tdboEntrada.linearId, c.info.chooseIdentityAndCert().party)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        futuro.getOrThrow().verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Task 5.
     * Necesitamos que la transacción sea firmada por un servicio de notario.
     * TODO: Use una llamada de subFlow al [FinalityFlow] para obtener la firma del notario.
     */
    @Test
    fun flowDevuelveTransaccionFirmadaPorTodasLasPartesYNotario() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, prestamista, deudor))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOTransferirFlow(tdboEntrada.linearId, c.info.chooseIdentityAndCert().party)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        futuro.getOrThrow().verifyRequiredSignatures()
    }
}
