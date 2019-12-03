package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.training.contract.ContratoTDBO
import java.util.*

/**
 * El objeto EstadoTDBO, con las siguientes propiedades:
 * - [cantidad] La cantidad que debe el [deudor] al [prestamista]
 * - [prestamista] La parte (party) que es el prestamista.
 * - [deudor] La parte (party) que presta el dinero.
 * - [contract] Guarda una referencia a [ContratoTDBO]
 * - [pagado] Almacena cuanto de la [cantidad] se ha pagado.
 * - [linearId] Una identificación única compartida por todos los estados LinearState representando el mismo acuerdo durante la historia
 *   dentro de las bóvedas de todas las partes involucradas. Los métodos "verify" deberían comprobar que una entrada y una salida compartan el id
 *   en una transacción, excepto en una emisión o terminación.
 */
@BelongsToContract(ContratoTDBO::class)
data class EstadoTDBO(val cantidad: Amount<Currency>,
                      val prestamista: Party,
                      val deudor: Party,
                      val pagado: Amount<Currency> = Amount(0, cantidad.token),
                      override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    /**
     *  Esta propiedad almacena una lista de los nodos que pueden "usar" este estado en una transacción válida. En este caso, el
     *  prestamista o el deudor.
     */
    override val participants: List<Party> get() = listOf(prestamista, deudor)

    /**
     * Métodos de ayuda para cuando se construyen transacciones de liquidación o transferencia de TDBOs.
     * - [pagar] agrega una cantidad a la propiedad "pagado". No hace ninguna validación.
     * - [conNuevoPrestamista] crea una copia del estado actual con un nuevo prestamista especificado. Para usarse en las transferencias de TDBOs.
     */
    fun pagar(amountToPay: Amount<Currency>) = copy(pagado = pagado.plus(amountToPay))
    fun conNuevoPrestamista(nuevoPrestamista: Party) = copy(prestamista = nuevoPrestamista)
}