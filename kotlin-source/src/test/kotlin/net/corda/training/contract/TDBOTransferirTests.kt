package net.corda.training.contract

import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.packageName
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.training.ALICE
import net.corda.training.BOB
import net.corda.training.CHARLIE
import net.corda.training.MINICORP
import net.corda.training.state.EstadoTDBO
import org.junit.Test

/**
 * Instrucciones para ejercicio práctico de Contratos parte 2.
 * El objetivo es escribir codigo de contrato que verifique una transacción para transferir un [EstadoTDBO].
 * Como con las puebas de [TDBOEmitirTests] descomenta cada prueba y ejecutala una a la vez. Utiliza la definición de las pruebas y
 * la descripción de cada tarea para determinar como pasar las pruebas.
 */
class TDBOTransferirTests {
    // Un estado de prueba pre-hecho que podemos necesitar en algunas pruebas.
    class DummyState : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }
    // Un comando de prueba.
    class DummyCommand : CommandData
    var ledgerServices = MockServices(listOf("net.corda.training", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    /**
     * Tarea 1.
     * ¡Ahora las cosas se pondrán interesantes!
     * Necesitamos que el [ContratoTDBO] no pueda solo emitir TDBOs si no que tambien transferirlos.
     * pues claro, necesitaremos agregar un nuevo comando y un poco de codido de contrato adicional para manejar transferencias.
     * TODO: Agrega el comando "Transferir" al EstadoTDBO (ContratoTDBO) y actualiza la función verify() para que maneje multiples comandos.
     * Consejos:
     * - Como con el comando [Emitir], agrega el comando [Transferir] dentro de [ContratoTDBO.Commands].
     * - Una vez mas, solo nos interesa la existencia del comando [Transferir] en una transacción, por lo tanto debe
     *   subclassear [TypeOnlyCommandData].
     * - Puedes utilizar la función [requireSingleCommand] para verificar la ecistencia de un comando que implementa una
     *   interfaz específica. En lugar de usar:
     *
     *       tx.commands.requireSingleCommand<Commands.Emitir>()
     *
     *   Podemos utilizar:
     *
     *       tx.commands.requireSingleCommand<Commands>()
     *
     *   Para constatar cualquier comando que implemente [ContratoTDBO.Commands]
     * - Ahora debemos conocer el tipo de [Command.value], en Kotlin lo puedes hacer utilizando un bloque "when"
     * - Para cada caso en el bloque "when", puedes verificar el tipo de [Command.value] usando la palabra "is":
     *
     *       val command = ...
     *       when (command.value) {
     *           is Commands.X -> hagaAlgo()
     *           is Commands.Y -> hagaAlgoMas()
     *       }
     * - La funcion [requireSingleCommand] manejará los tipos de comandos desconocidos. (mira la primera prueba).
     */
    @Test
    fun debeManejarValoresDeMultiplesComandos() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                output(ContratoTDBO::class.java.name, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand())
                this `fails with` "Required net.corda.training.contract.ContratoTDBO.Commands command"
            }
            transaction {
                output(ContratoTDBO::class.java.name, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                this.verifies()
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 2.
     * La transacción de transferir debe contener únicamente un estado de salida y uno de entrada.
     * TODO: Agrega restricciones al codigo de contrato para asegurar que la transacción solo tiene un estado de entrada y uno de salida
     * Consejo:
     * - Mira el codigo de contrato para "Emitir".
     */
    @Test
    fun debeTenerUnaEntradaYUnaSalida() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                input(ContratoTDBO::class.java.name, DummyState())
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Una transferencia de TDBO solo debe consumir un estado de entrada."
            }
            transaction {
                output(ContratoTDBO::class.java.name, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Una transferencia de TDBO solo debe consumir un estado de entrada."
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Solo un estado de salida debe ser creado al transferir un TDBO."
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                output(ContratoTDBO::class.java.name, DummyState())
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Solo un estado de salida debe ser creado al transferir un TDBO."
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 3.
     * TODO: Agrega una restricción al código de contrato para asegurar que solo la propiedad prestamista pueda cambiar al transferir TDBOs.
     * Consejo:
     * - Puedes utilizar el metodo [EstadoTDBO.copy].
     * - Puedes comparar la copia de una entrada con la salida con el prestamista de la salida como el prestamista de la entrada
     * - Necesitarás referencias a la entrada y salida de TDBOs
     * - Recuerda que debes envolver con [ContractState]s a los [EstadoTDBO]s.
     * - ¡Es mas facil tomar este acercamiento en vez de comprobar que todas las otras propiedades que no sean el prestamista
     *   no hayan cambiado, incluyendo el [linearId] y el [contracto]!
     */
    @Test
    fun soloElPrestamistaPuedeCambiar() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO::class.java.name, EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party))
                output(ContratoTDBO::class.java.name, EstadoTDBO(1.DOLLARS, ALICE.party, BOB.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Solo la propiedad prestamista puede cambiar."
            }
            transaction {
                input(ContratoTDBO::class.java.name, EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party))
                output(ContratoTDBO::class.java.name, EstadoTDBO(10.DOLLARS, ALICE.party, CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Solo la propiedad prestamista puede cambiar."
            }
            transaction {
                input(ContratoTDBO::class.java.name, EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party, 5.DOLLARS))
                output(ContratoTDBO::class.java.name, EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party, 10.DOLLARS))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "Solo la propiedad prestamista puede cambiar."
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 4.
     * Es bastante obvio que en una transferencia el prestamista debe cambiar.
     * TODO: Agregar una restriccion para comprobar que el prestamista haya cambiado en el TDBO de salida.
     */
    @Test
    fun elPrestamistaTieneQueCambiar() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "La propiedad prestamista debe de cambiar en una transferencia."
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 5.
     * Todos los participantes de una transferencia de TDBO deben firmar.
     * TODO: Agrega una restricción para revisar que el prestamista anterior, el nuevo prestamista y el deudor hayan firmado.
     */
    @Test
    fun todosLosParticipantesDebenFirmar() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO"
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO"
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO"
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, MINICORP.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO"
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey, MINICORP.publicKey), ContratoTDBO.Commands.Transferir())
                this `fails with` "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO"
            }
            transaction {
                input(ContratoTDBO::class.java.name, tdbo)
                output(ContratoTDBO::class.java.name, tdbo.conNuevoPrestamista(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Transferir())
                this.verifies()
            }
        }
    }
}