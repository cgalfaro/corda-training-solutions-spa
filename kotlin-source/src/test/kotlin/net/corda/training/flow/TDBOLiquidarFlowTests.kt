package net.corda.training.flow

import net.corda.core.contracts.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.node.MockNetwork
import net.corda.training.state.EstadoTDBO
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.training.contract.ContratoTDBO
import org.junit.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Instrucciones para ejercicio práctico de flujos parte 3.
 * Descomente las pruebas y utilice los consejos + descripciones de tareas para completar el flujo para que las pruebas pasen.
 */
class TDBOLiquidarFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.training", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // Para nodos reales esto pasa automaticamente, pero necesitamos registrar a mano el flujo para las pruebas
        startedNodes.forEach { it.registerInitiatedFlow(TDBOEmitirFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(TDBOLiquidarFlowResponder::class.java) }
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
     * Emitir un poco de cash en el libro mayor para nosotros, necesitamos esto antes de poder liquidar un TDBO.
     */
    private fun emitirCash(amount: Amount<Currency>): Cash.State {
        val flow = SelfIssueCashFlow(amount)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Tarea 1.
     * La primera tarea es obtener de la boveda el [EstadoTDBO] para el [linearId] que recibimos, construye la transacción
     * y fírmala.
     * TODO: Obten el TDBO de la bóveda para [linearId] que recibimos, construye y firma la transacción de liquiar.
     * Consejos:
     * - Use el codigo de [TDBOTransferirFlow] para obtener [EstadoTDBO] correcto de la bóveda.
     * - Necesitará utilizar la funcionalidad de [Cash.generateSpend] de la bóveda para agregar los estados cash y comando cash
     *   a tu transacción. El API es bastante simple. Toma una referencia al [TransactionBuilder], un [Amount] y
     *   el objeto [Party] del recipiente. La función mutará tu constructor agregando los estados y los comandos.
     * - Despues debes producir la salida de [EstadoTDBO] utilizando la función [EstadoTDBO.pay].
     * - Agrega la entrada [EstadoTDBO] [StateAndRef] a la nueva salida de [EstadoTDBO] a la transacción.
     * - Firma la transacción y devuélvela.
     */
    @Test
    fun flowDevuelveTransaccionParcialmenteFirmadaCorrectamenteFormada() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        emitirCash(5.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val resultadoLiquidar = futuro.getOrThrow()
        // Revisa que la transacción esté bien formada...
        // Una EstadoTDBO de salida, una referencia de EstadoTDBO de entrada, cash de entrada y de salida
        a.transaction {
            val ledgerTx = resultadoLiquidar.toLedgerTransaction(a.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 2)
            val tdboSalida = ledgerTx.outputs.map { it.data }.filterIsInstance<EstadoTDBO>().single()
            assertEquals(
                    tdboSalida,
                    tdboEntrada.pagar(5.POUNDS))
            // Suma cash de salida completo. Esto es complejo ya que pueda que existan múltiples estados de salida cash
            // y puede que no todos han sido asignados al prestamista.
            val outputCashSum = ledgerTx.outputs
                    .map { it.data }
                    .filterIsInstance<Cash.State>()
                    .filter { it.owner == b.info.chooseIdentityAndCert().party }
                    .sumCash()
                    .withoutIssuer()
            // Compare el cash asignado al prestamista con la cantidad reclamada que el deudor liquida.
            assertEquals(
                    outputCashSum,
                    (tdboEntrada.cantidad - tdboEntrada.pagado - tdboSalida.pagado))
            val command = ledgerTx.commands.requireSingleCommand<ContratoTDBO.Commands>()
            assert(command.value == ContratoTDBO.Commands.Liquidar())
            // Compruebe que la transacción haya sido firmada por el deudor.
            resultadoLiquidar.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey,
                    mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
        }
    }

    /**
     * Tarea 2.
     * Solo el deudor debería de ejecutar este flujo para un TDBO en particular.
     * TODO: Obten el TDBO de la boveda con el [linearId] entregado y revisa que el nodo ejecutando el flujo sea el deudor.
     * Consejo: Utiliza la data obtenida de la bóveda para revisar que el nodo correcto esté ejecutando el flujo.
     */
    @Test
    fun flowEmitirSoloPuedeSerEjecutadoPorDeudor() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        emitirCash(5.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = b.startFlow(flujo)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { futuro.getOrThrow() }
    }

    /**
     * Tarea 3.
     * El deudor debe tener al menos ALGO de cash en la moneda correcta para pagar al prestamista.
     * TODO: Agrega una comprobación al flujo para asegurarnos que el deudor tiene cash en la moneda correcta.
     * Consejo:
     * - Usa [serviceHub.getCashBalances] - es un mapa que puede ser consultado por [Currency].
     * - Use una declaración if para comprobar que hay cash en la moneda correcta.
     */
    @Test
    fun deudorDebeTenerCashEnMonedaCorrecta() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException>("Deudor no tiene GBP para liquidar.") { futuro.getOrThrow() }
    }

    /**
     * Tarea 4.
     * El deudor debe tener suficiente cash en la moneda correcta para pagar al prestamista.
     * TODO: Agrega una comprobación al flujo que asegure que el deudor tiene suficiente cash para pagar al prestamista.
     * Consejo: Agregue otra declaración if similar a la requerida anteriormente.
     */
    @Test
    fun deudorDebeTenerSuficienteCashEnMonedaCorrecta() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        emitirCash(1.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException>("Deudor únicamente tiene 1.00 GBP pero necesita 5.00 GBP para liquidar.") { futuro.getOrThrow() }
    }

    /**
     * Tarea 5.
     * Necesitamos que la transacción sea firmada por el otro participante.
     * TODO: Use una llamada subFlow para [initateFlow] y el [SignTransactionFlow] para obtener la firma del prestamista.
     */
    @Test
    fun flowDevuelveTransaccionFirmadaPorLosDosParticipantes() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        emitirCash(5.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val resultadoDeLiquidacion = futuro.getOrThrow()
        // Comprueba que la transacción está bien formada
        // Un EstadoTDBO de salida, una referencia a un EstadoTDBO de entrada, cash de entrada y salida
        resultadoDeLiquidacion.verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Tarea 6.
     * Necesitamos obtener las transacción firmada por el servicio de notario
     * TODO: Use una llamada subFlow al [FinalityFlow] para obtener la firma del notario.
     */
    @Test
    fun flowDevuelveUnaTransaccionCometida() {
        val stx = emitirTDBO(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        emitirCash(5.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val resultadoDeLiquidacion = futuro.getOrThrow()
        // Comprueba que la transacción está bien formada
        // Un EstadoTDBO de salida, una referencia a un EstadoTDBO de entrada, cash de entrada y salida
        resultadoDeLiquidacion.verifyRequiredSignatures()
    }
}
