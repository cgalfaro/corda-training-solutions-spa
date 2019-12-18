package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO

/**
 * Este es el flujo que se encarga de transferir TDBOs existentes en el libro mayor.
 * Adquirir la firma de la contraparte es manejado por [CollectSignaturesFlow].
 * La notarización (si es necesaria) y la actualización del libro mayor es manejado por [FinalityFlow].
 * El flujo devuelve la transacción firmada ([SignedTransaction]) que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOTransferirFlow(val linearId: UniqueIdentifier,
                         val nuevoPrestamista: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Paso 1. Obten el TDBO especificado por linearId de la bóveda.
        val criterioDeBusqueda = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val tdboStateAndRef =  serviceHub.vaultService.queryBy<EstadoTDBO>(criterioDeBusqueda).states.single()
        val tdboEntrada = tdboStateAndRef.state.data

        // Paso 2. Este flow solo puede ser iniciado por el prestamista.
        if (ourIdentity != tdboEntrada.prestamista) {
            throw IllegalArgumentException("La transferencia de un TDBO solo puede ser iniciada por el prestamista actual del TDBO.")
        }

        // Paso 3. Crear el nuevo EstadoTDBO que refleje al nuevo prestamista.
        val tdboSalida = tdboEntrada.conNuevoPrestamista(nuevoPrestamista)

        // Paso 4. Crear el nuevo comando transferir.
        val firmantes = (tdboEntrada.participants + nuevoPrestamista).map { it.owningKey }
        val comandoTransferir = Command(ContratoTDBO.Commands.Transferir(), firmantes)

        // Paso 5. Obtener referencia a un constructor de transacción.
        // Nota: Hay trabajo en ejecución para soportar multiples identidades de notarios.
        val notario = serviceHub.networkMapCache.notaryIdentities.first()
        val constructorDeTransaccion = TransactionBuilder(notary = notario)

        // Paso 6. Crear la transaccion que tenga una entrada, una salida y un comando.
        constructorDeTransaccion.withItems(tdboStateAndRef,
                        StateAndContract(tdboSalida, ContratoTDBO.TDBO_CONTRACT_ID),
                        comandoTransferir)

        // Paso 7. Verificar y firmar la transacción.
        constructorDeTransaccion.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(constructorDeTransaccion)

        // Paso 8. Recolectar las firmas del deudor y el nuevo prestamista y agregarlas a la transaccion.
        // Esto también verifica la transacción y comprueba las firmas.
        val sesiones = (tdboEntrada.participants - ourIdentity + nuevoPrestamista).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sesiones))

        // Paso 9. Notarizar y almacenar la transacción en nuestras bovedas.
        return subFlow(FinalityFlow(stx, sesiones))
    }
}

/**
 * Este es el flujo que firma las transferencias de TDBOs.
 * La firma es manejada por el [SignTransactionFlow].
 */
@InitiatedBy(TDBOTransferirFlow::class)
class TDBOTransferirFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val flujoDeTransaccionFirmada = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "Esta debe ser una transacción de TDBO" using (output is EstadoTDBO)
            }
        }

        val txQueAcabamosDeFirmar = subFlow(flujoDeTransaccionFirmada)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txQueAcabamosDeFirmar.id))
    }
}