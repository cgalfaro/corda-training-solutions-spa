package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.training.state.EstadoTDBO

/**
 * El ContratoTDBO puede manejar tres tipos de transacciones que involucran [EstadoTDBO]s.
 * - Emitir: Emite un nuevo [EstadoTDBO] en el libro mayor, que es un acuerdo bilateral entre dos partes.
 * - Transferir: Reasignar el prestamista/beneficiario.
 * - Liquidar: Liquida completamente o parcialmente el [EstadoTDBO] usando el contrato Corda [Cash].
 *
 * LegalProseReference: Por el momento únicamente es un string que no hace nada.
 */
@LegalProseReference(uri = "<prose_contract_uri>")
class ContratoTDBO : Contract {
    companion object {
        @JvmStatic
        val TDBO_CONTRACT_ID = "net.corda.training.contract.ContratoTDBO"
    }

    /**
     * Agrega cualquier cantidad de comandos que sean necesarios para este contrato como clases dentro de esta interfaz.
     * Es de utilidad encapsular tus comandos dentro de una interfaz, para que puedas usar la función [requireSingleCommand]
     * para chequear comandos que implementen esta interfaz.
     */
    interface Commands : CommandData {
        class Emitir : TypeOnlyCommandData(), Commands
        class Transferir : TypeOnlyCommandData(), Commands
        class Liquidar : TypeOnlyCommandData(), Commands
    }

    /**
     * El código de contrato para [ContratoTDBO].
     * Las restricciones se documentan a sí mismas por lo cual no se requiere de explicaciones adicionales.
     */
    override fun verify(tx: LedgerTransaction) {
        val comando = tx.commands.requireSingleCommand<ContratoTDBO.Commands>()
        when (comando.value) {
            is Commands.Emitir -> requireThat {
                "No se deben incluir entradas para la emision de un TDBO" using (tx.inputs.isEmpty())
                "Solo un estado de salida debe ser creado al emitir un TDBO." using (tx.outputs.size == 1)
                val estadoSalida = tx.outputStates.single() as EstadoTDBO
                "Un TDBO recién emitido debe contener una cantidad positiva" using (estadoSalida.cantidad.quantity > 0)
                "El prestamista y el deudor no pueden tener la misma identidad." using (estadoSalida.prestamista != estadoSalida.deudor)
                "El prestamista y el deudor deben de firmar juntos para emitir un TDBO." using
                        (comando.signers.toSet() == estadoSalida.participants.map { it.owningKey }.toSet())
            }
            is Commands.Transferir -> requireThat {
                "Una transferencia de TDBO solo debe consumir un estado de entrada." using (tx.inputs.size == 1)
                "Solo un estado de salida debe ser creado al transferir un TDBO." using (tx.outputs.size == 1)
                val estadoEntrada = tx.inputStates.single() as EstadoTDBO
                val estadoSalida = tx.outputStates.single() as EstadoTDBO
                "Solo la propiedad prestamista puede cambiar." using
                        (estadoEntrada == estadoSalida.conNuevoPrestamista(estadoEntrada.prestamista))
                "La propiedad prestamista debe de cambiar en una transferencia." using
                        (estadoEntrada.prestamista != estadoSalida.prestamista)
                "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO" using
                        (comando.signers.toSet() == (estadoEntrada.participants.map { it.owningKey }.toSet() union
                                estadoSalida.participants.map { it.owningKey }.toSet()))
            }
            is Commands.Liquidar -> {
                // Revisar que solo exista un grupo de TDBOs y que siempre hay un TDBO de entrada.
                val tdbos = tx.groupStates<EstadoTDBO, UniqueIdentifier> { it.linearId }.single()
                "Debe existir un TDBO de entrada para liquidar." using (tdbos.inputs.size == 1)
                // Revisar que exista salida de los estados cash
                val cash = tx.outputsOfType<Cash.State>()
                "Debe existir una salida cash." using (cash.isNotEmpty())
                // Revisar que la salida cash sea asignada correctamente
                val tdboEntrada = tdbos.inputs.single()
                val cashAceptado = cash.filter { it.owner == tdboEntrada.prestamista }
                "Debe existir cash en estado de salida pagado al prestamista." using (cashAceptado.isNotEmpty())
                // Suma del cash que es enviado (no nos interesa quien lo emitió).
                val sumaCashAceptado = cashAceptado.sumCash().withoutIssuer()
                val faltaPorPagar = tdboEntrada.cantidad - tdboEntrada.pagado
                "La cantidad que se paga no puede ser más que la cantidad que falta pagar." using
                        (faltaPorPagar >= sumaCashAceptado)
                // Revisar si necesitamos un TDBO de salida o no.
                if (faltaPorPagar == sumaCashAceptado) {
                    // Si el TDBO se ha liquidado completamente no debería de haber un TDBO de salida.
                    "La deuda se pagó completa no debe existir TDBO de salida." using
                            (tdbos.outputs.isEmpty())
                } else {
                    // Si el TDBO se ha liquidado parcialmente entonces debería existir una salida de TDBO
                    "La deuda no se pagó completa debe haber un TDBO de salida." using
                            (tdbos.outputs.size == 1)
                    // Revisar que solo cambie la propiedad pagado.
                    val tdboSalida = tdbos.outputs.single()
                    "El deudor no puede cambiar cuando liquidamos." using (tdboEntrada.deudor == tdboSalida.deudor)
                    "La cantidad no puede cambiar cuando liquidamos." using (tdboEntrada.cantidad == tdboSalida.cantidad)
                    "El prestamista no puede cambiar cuando liquidamos." using (tdboEntrada.prestamista == tdboSalida.prestamista)
                }
                "Ambos el prestamista y el deudor deben firmar una transacción de liquidar." using
                        (comando.signers.toSet() == tdboEntrada.participants.map { it.owningKey }.toSet())
            }
        }
    }
}
