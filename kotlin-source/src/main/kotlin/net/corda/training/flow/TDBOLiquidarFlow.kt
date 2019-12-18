package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO
import net.corda.finance.workflows.asset.CashUtils
import net.corda.finance.workflows.getCashBalance
import java.util.*

/**
 * Este es el flujo que maneja la liquidación (parcial) de TDBOs existentes en el libro mayor.
 * La adquisición de la firma de la contraparte es manejado por [CollectSignaturesFlow].
 * La notarización (si es necesaria) y la actualización del libro mayor es manejado por [FinalityFlow].
 * El flujo devuelve la transacción firmada ([SignedTransaction]) que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOLiquidarFlow(val linearId: UniqueIdentifier, val cantidadAPagar: Amount<Currency>): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Paso 1. Obtener el EstadoTDBO de la bóveda.
        val criterioDeBusqueda = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val tdboALiquidar = serviceHub.vaultService.queryBy<EstadoTDBO>(criterioDeBusqueda).states.single()
        val contraParte = tdboALiquidar.state.data.prestamista

        // Paso 2. Comprueba que el participante que inicia este flujo sea el deudor.
        if (ourIdentity != tdboALiquidar.state.data.deudor) {
            throw IllegalArgumentException("La liquidación de un TDBO debe ser iniciada por el deudor.")
        }

        // Paso 3. Crea el constructor de la transacción.
        val notario = tdboALiquidar.state.notary
        val constructorDeTransaccion = TransactionBuilder(notary = notario)

        // Paso 4. Revisa que el deudor tiene suficiente dinero para liquidar el monto seleccionado
        val balanceDeDinero = serviceHub.getCashBalance(cantidadAPagar.token)

        if (balanceDeDinero < cantidadAPagar) {
            throw IllegalArgumentException("Deudor únicamente tiene $balanceDeDinero pero necesita $cantidadAPagar para liquidar.")
        } else if (cantidadAPagar > (tdboALiquidar.state.data.cantidad - tdboALiquidar.state.data.pagado)) {
            throw IllegalArgumentException("Deduor intentó liquidar con $cantidadAPagar pero solo necesita " +
                    "${ (tdboALiquidar.state.data.cantidad - tdboALiquidar.state.data.pagado) }")
        }

        // Paso 5. Obtener cash de la bóveda y agregar el gasto al contrsuctor de transacción.
        // La bóveda puede contener estados "que pertenecen a (owned)" participantes anónimos. Esta es una de las técnicas para anonimizar transacciones
        // generateSpend devuelve todas las llaves públicas que deben ser utilizadas para firmar la transacción.
        val (_, llavesCash) = CashUtils.generateSpend(serviceHub, constructorDeTransaccion, cantidadAPagar, ourIdentityAndCert, contraParte)

        // Paso 6. Agrega el EstadoTDBO de entrada y el comando liquidar al constructor de la transacción.
        val comandoLiquidar = Command(ContratoTDBO.Commands.Liquidar(), listOf(contraParte.owningKey, ourIdentity.owningKey))
        constructorDeTransaccion.addCommand(comandoLiquidar)
        constructorDeTransaccion.addInputState(tdboALiquidar)

        // Paso 7. Solo agrega un EstadoTDBO de salida si el TDBO no se ha liquidado completamente.
        val cantidadRemanente = tdboALiquidar.state.data.cantidad - tdboALiquidar.state.data.pagado - cantidadAPagar
        if (cantidadRemanente > Amount(0, cantidadAPagar.token)) {
            val tdboPagado: EstadoTDBO = tdboALiquidar.state.data.pagar(cantidadAPagar)
            constructorDeTransaccion.addOutputState(tdboPagado, ContratoTDBO.TDBO_CONTRACT_ID)
        }

        // Paso 8. Verificar y firmar la transacción.
        constructorDeTransaccion.verify(serviceHub)
        // Necesitamos firmar la transacción con todas las llaves de los estados cash de entrada y nuestra llave publica
        val misLLavesParaFirmar = (llavesCash.toSet() + ourIdentity.owningKey).toList()
        val ptx = serviceHub.signInitialTransaction(constructorDeTransaccion, misLLavesParaFirmar)

        // Iniciando sesión con el otro participante
        val sesionContraparte = initiateFlow(contraParte)

        // Enviando a la contraparte nuestras identidades para que estén atentos de llaves publicas anonimas
        subFlow(IdentitySyncFlow.Send(sesionContraparte, ptx.tx))

        // Paso 9. Recolectando las firmas que hacen falta
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(sesionContraparte), myOptionalKeys = misLLavesParaFirmar))

        // Paso 10. Finalizar la transacción
        return subFlow(FinalityFlow(stx, sesionContraparte))
    }
}

/**
 * Este es el flow que firma las liquidaciones de TDBOs.
 * La firma es manejada por el [SignTransactionFlow].
 */
@InitiatedBy(TDBOLiquidarFlow::class)
class TDBOLiquidarFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {

        // Recibiendo informacion de identidades anónimas
        subFlow(IdentitySyncFlow.Receive(flowSession))

        // Firmando la transacción
        val flowDeTransaccionFirmada = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }

        val txQueAcabamosDeFirmar = subFlow(flowDeTransaccionFirmada)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txQueAcabamosDeFirmar.id))
    }
}

@InitiatingFlow
@StartableByRPC
/**
 * Emite la cantidad de cash en la moneda deseada al nodo que llama.
 * ¡Sólo para ser utilizado para propósito de demo/ejemplo/entrenamiento.
 */
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {
    @Suspendable
    override fun call(): Cash.State {
        /** Crea el comando de emitir (issue) cash. */
        val issueRef = OpaqueBytes.of(0)
        /** Nota: seguimos trabajando en poder manejar con multiples notarios, aún no está listo. */
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        /** Crea la transacción para emitir el cash. */
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, notary))
        /** Devuelve el cash de salida. */
        return cashIssueTransaction.stx.tx.outputs.single().data as Cash.State
    }
}