package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO

/**
 * Este es el flujo que se encarga de Emitir un nuevo TDBO al libro mayor.
 * Recopilar la firma de la contraparte es manejado por el flujo [CollectSignaturesFlow].
 * La notarización (si es requerida) y su almacenamiento en el libro mayor es manejado por el flow [FinalityFlow].
 * este flujo devuelve la [SignedTransaction] que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOEmitirFlow(val state: EstadoTDBO): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Paso 1. Obten una referencia al servicio de norario en nuestra red y nuestro par de llaves.
        // Nota: Hay trabajo en ejecución para soportar multiples identidades de notarios.
        val notario = serviceHub.networkMapCache.notaryIdentities.first()

        // Paso 2. Crear un nuevo comando emitir.
        // Recuerda que un comando es un objeto CommandData y una lista de CompositeKeys
        val comandoEmitir = Command(ContratoTDBO.Commands.Emitir(), state.participants.map { it.owningKey })

        // Paso 3. Crear un nuevo objeto TransactionBuilder.
        val contructorDeTransaccion = TransactionBuilder(notary = notario)

        // Paso 4. Agrega el TDBO como un estado de salida y el comando al constructor de la transacción.
        contructorDeTransaccion.addOutputState(state, ContratoTDBO.TDBO_CONTRACT_ID)
        contructorDeTransaccion.addCommand(comandoEmitir)

        // Paso 5. Verificar y firmar con nuestro KeyPair.
        contructorDeTransaccion.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(contructorDeTransaccion)

        val sesiones = (state.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        // Paso 6. Recolectar la firma del otro participante usando el SignTransactionFlow
        val stx = subFlow(CollectSignaturesFlow(ptx, sesiones))

        // Paso 7. Asumiendo que no hay excepciones, ahora podemos finalizar la transacción.
        return subFlow(FinalityFlow(stx, sesiones))
    }
}

/**
 * Este es el flujo que firma las emisiones de TDBOs.
 * El firmar es manejador por el flujo [SignTransactionFlow].
 */
@InitiatedBy(TDBOEmitirFlow::class)
class TDBOEmitirFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flujoDeTransaccionFirmada = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "Esta debe de ser una transaccion de TDBO" using (output is EstadoTDBO)
            }
        }

        val txQueAcabamosDeFirmar = subFlow(flujoDeTransaccionFirmada)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txQueAcabamosDeFirmar.id))
    }
}