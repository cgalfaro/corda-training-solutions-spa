package net.corda.training.flow

import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.*
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Instrucciones para ejercicio práctico de Flujos parte 1.
 * Descomenta las pruebas, utiliza los consejos y la descripción de las tareas para completar los flujos y que las pruebas pasen.
 * ¡Nota! Estas pruebas dependen de que Quasar sea cargado, cambia tu "run configuration" a "-ea -javaagent:lib/quasar.jar"
 * Run configuration puede ser editado en IntelliJ bajo Run -> Edit Configurations -> VM options
 * En algunas maquinas/configuraciones pueda que sea necesario que proveas un path completo al archivo quasar.jar.
 * En algunas maquinas/configuraciones es posible que puedas utilizar la opcion "JAR manifest" para acortar el comando.
 */
class TDBOEmitirFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.training"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // Para nodos reales esto pasa automaticamente, pero necesitamos registrar a mano el flujo para las pruebas
        startedNodes.forEach { it.registerInitiatedFlow(TDBOEmitirFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Tarea 1.
     * Construir el [TDBOEmitirFlow]!
     * TODO: Implementar el flujo [TDBOEmitirFlow] que construye y devuelve una transacción parcialmente firmada del tipo [SignedTransaction].
     * Consejo:
     * - ¡Hay bastante por hacer para que esta prueba pase!
     * - Crear un [TransactionBuilder] y pasalo a una referencia de notario.
     * -- Un objeto notario del tipo [Party] se puede obtener de: [FlowLogic.serviceHub.networkMapCache].
     * -- Para este proyecto de entrenamiento únicamente existe un notario.
     * - Crear un [ContratoTDBO.Commands.Emitir] dentro de un nuevo [Command].
     * -- Los firmantes requeridos serán los mismos que los participantes de los estados.
     * -- Agrega el [Command] al constructor de la transacción (TransactionBuilder) con [addCommand].
     * - Utilice el parametro del flow [EstadoTDBO] como el estado de salida con [addOutputState]
     * - Puntos extras: Utilice [TransactionBuilder.withItems] para crear la transacción.
     * - Firme la transacción y convértala a una [SignedTransaction] usando el método [serviceHub.signInitialTransaction].
     * - Devuelva la [SignedTransaction].
     * Consejo: ptx representa una transacción parcialmente firmada.
     */
    @Test
    fun flowDevuelveTransaccionParcialmenteFirmadaCrrectamenteFormada() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val flujo = TDBOEmitirFlow(tdbo)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        // Devuelva el objeto SignedTransaction parcialmente firmado del TDBOEmitirFlow.
        val ptx: SignedTransaction = futuro.getOrThrow()
        // Imprima la transacción para propósitos de debugging.
        println(ptx.tx)
        // Compruebe que la transacción está bien formada...
        // Cero salidas, una entrada de EstadoTDBO y un comando con las propiedades correctas.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is EstadoTDBO)
        val command = ptx.tx.commands.single()
        assert(command.value is ContratoTDBO.Commands.Emitir)
        assert(command.signers.toSet() == tdbo.participants.map { it.owningKey }.toSet())
        ptx.verifySignaturesExcept(deudor.owningKey,
                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Tarea 2.
     * Ahora ya tenemos una transacción bien formada, necesitamos verificarla adecuadamente usando el [ContratoTDBO].
     * TODO: Agregue al [TDBOEmitirFlow] para que ademas de firmar la transacción también la verifique usando verify.
     * Consejo: Puedes verificar directamente en el constructor antes de finalizar la transacción. De esta forma
     * puedes confirmar la transacción antes de hacerla inmutable con la firma.
     */
    @Test
    fun flowDevuelveTransaccionParcialmenteFirmadaVerificada() {
        // Prueba con un TDBO de cantidad 0 falla.
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdboCero = EstadoTDBO(0.POUNDS, prestamista, deudor)
        val futuroUno = a.startFlow(TDBOEmitirFlow(tdboCero))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futuroUno.getOrThrow() }
        // Prueba con un TDBO del mismo particpante falla.
        val deudorEsPrestamista = EstadoTDBO(10.POUNDS, prestamista, prestamista)
        val futuroDos = a.startFlow(TDBOEmitirFlow(deudorEsPrestamista))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futuroDos.getOrThrow() }
        // Prueba que un TDBO bien hecho pasa
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val futureThree = a.startFlow(TDBOEmitirFlow(tdbo))
        mockNetwork.runNetwork()
        futureThree.getOrThrow()
    }

    /**
     * IMPORTANTE: Revisa [CollectSignaturesFlow] antes de continuar. //File es read only no se puede traducir
     * Tarea 3.
     * Ahora necesitamos recolectar la firma de la [otherParty] (contraparte) usando el flow [CollectSignaturesFlow].
     * TODO: Agregar al [TDBOEmitirFlow] para recolectar la firma de [otherParty] (otro participante).
     * Consejo:
     * Del lado del inicador:
     * - Obten el set de firmantes requeridos de los participantes que no están en el nodo.
     * - - [ourIdentity] brindará la identidad del nodo del cual estas operando
     * - Usa [initiateFlow] para obtener un set de objetos [FlowSession]
     * - - Es recomendado usar [state.participants] como base para determinar las sesiones necesarias. [participants] Está en
     * - - la interface del estado por lo tanto está garantizado que exista donde [prestamista] y [deudor] no estén.
     * - - Consejo: [ourIdentity] te devolverá el [Party] que representa la identidad del flujo iniciante.
     * - Use [subFlow] para iniciar [CollectSignaturesFlow]
     * - Pásale un objeto [SignedTransaction] y un set de [FlowSession]
     * - Devolverá una [SignedTransaction] con todas las firmas requeridas
     * - El subflow ejecuta el chequeo de firmas y la verificación de la transacción por nosotros.
     *
     * Del lado del que responde:
     * - Creee una subclase de [SignTransactionFlow]
     * - Override [SignTransactionFlow.checkTransaction] para imponer restricciones en la transacción
     *
     * Usando este flujo reduces toda la comunicación de ida y venida requerida para que las partes firmen una
     * transaccción.
     * Consejo: stx representa una transacción firmada por todas las partes
     */
    @Test
    fun flowDevuelveTransaccionFirmadaPorAmbosParticipantes() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val flujo = TDBOEmitirFlow(tdbo)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val stx = futuro.getOrThrow()
        stx.verifyRequiredSignatures()
    }

    /**
     * Tarea 4.
     * Ahora necesitamos almacenar la [SignedTransaction] terminada en las bovedas de ambos participantes.
     * TODO: Modificar el  [TDBOEmitirFlow] agregando una llamada al [FinalityFlow].
     * Consejo:
     * - Como lo mencionamos anteriormente, use el [FinalityFlow] para asegurar que la transacción sea almacenada en las
     *  bóvedas de ambos participantes ([Party]).
     * - No utilice el [BroadcastTransactionFlow]! Este es llamado por medio del [FinalityFlow].
     * - El [FinalityFlow] determina si la transacción requiere notarización o no.
     * - En este caso no necesitamos de la firma del notario ya que esto es una transacción de emision sin timestamp. No hay
     *   entradas en la transacción que puedan ser gastadas dos veces (double spent)! Si agregaramos un timestamp a esta transacción
     *   entonces requeriríamos la firma del notario ya que los notarios actúan como la autoridad del timestamp.
     */
    @Test
    fun flowDevuelveLaMismaTransaccionEnLaBovedaDeAmbosParticipantes() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val flujo = TDBOEmitirFlow(tdbo)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val stx = futuro.getOrThrow()
        println("Hash de transacción firmada: ${stx.id}")
        listOf(a, b).map {
            it.services.validatedTransactions.getTransaction(stx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${stx.id}")
            assertEquals(stx.id, txHash)
        }
    }
}
