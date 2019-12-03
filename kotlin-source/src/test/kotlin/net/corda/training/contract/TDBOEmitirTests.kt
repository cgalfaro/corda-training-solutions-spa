package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.finance.*
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.training.ALICE
import net.corda.training.BOB
import net.corda.training.DUMMY
import net.corda.training.MINICORP
import net.corda.training.state.EstadoTDBO
import org.junit.*

/**
 * Instrucciones para ejercicio práctico de contratos Parte 1.
 * El objetivo es escribir codigo de contrato que verifique una transacción para emitir un [EstadoTDBO].
 * Como con [IOUStateTests] descomenta cada unit test y ejecutalo uno a la vez. Utiliza la definición de las pruebas y
 * la descripción de cada tarea para determinar como pasar las pruebas.
 */
class TDBOEmitirTests {
    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("net.corda.training"))

    /**
     * Tarea 1.
     * Recuerda que os comandos son requeridos para mostrar la intención de la transacción y la lista de
     * llaves publicas como parámetros que corresponden a los firmantes necesarios para la transacción.
     * Los comandos se vuelven más importantes mas tarde cuando multiples acciones son posibles con el EstadoTDBO,
     * ej: Transferir y Liquidar.
     * TODO: Agrega el comando "Emitir" al ContratoTDBO y chequea por la existencia del comando en la función verify
     * Consejo:
     * - Para el comando emitir únicamente nos importa la existencia de este mismo en una transacción, por ende debe ser subclase de
     *   la clase [TypeOnlyCommandData].
     * - El comando debe ser definido dentro de [ContratoTDBO].
     * - Puedes utilizar la función [requireSingleCommand] en [tx.commands] para verificar la existencia y el tipo del comando especificado
     *   en la transacción. [requireSingleCommand] requiere un tipo genérico para identificar el tipo de comando requerido.
     *
     *   requireSingleCommand<REQUIRED_COMMAND>()
     *
     * - Generalment encapsulamos nuestros comandos alrededor de una interface andentro de la clase del contrato llamada [Commands] la cual
     *   implementa la interfaz [CommandData]. El comando [ContratoTDBO.Commands.Issue] debería ser definido en la
     *   interfaz [Commands] e implementarla, por ejemplo:
     *
     *     interface Commands : CommandData {
     *         class X : TypeOnlyCommandData(), Commands
     *     }
     *
     * - Podemos buscar la existencia de cualquier comando que implemente [ContratoTDBO.Commands] utilizando la
     *   función [requireSingleCommand] que recibe un parámetro.
     */
    @Test
    fun tieneQueIncluirComandoEmitir() {
        val tdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                output(ContratoTDBO.TDBO_CONTRACT_ID,  tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand()) // Tipo incorrecto.
                this.fails()
            }
            transaction {
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir()) // Tipo correcto.
                this.verifies()
            }
        }
    }

    /**
     * Tarea 2.
     * Como lo vimos anteriorment, las restricciones de emitir no deben tener referencias a estados de entrada. Por lo tanto debemos
     * asegurarnos que ningun estado de entrada sea incluido en la transacción para emitir un TDBO.
     * TODO: Escriba una restricción para asegurar que una transacción para emitir un TDBO no incluya estados de entrada.
     * Consejo: use un bloque [requireThat] con una restricción adentro de la función [ContratoTDBO.verify] para encapsular las
     * restricciones:
     *
     *     requireThat {
     *         "Mensaje cuando falla" using (expresión de restricción booleana)
     *     }
     *
     * fíjese que las pruebas generalmente esperan que una verificación falle con un mensaje específico que debe
     * ser definido en las restricciones del contrato. ¡Si no lo haces la prueba fallará!
     *
     * Puedes accesar la lista de entradas por medio del objeto [LedgerTransaction] que se pasa a
     * [ContratoTDBO.verify].
     */
    @Test
    fun transaccionEmitirNoPuedeTenerEntradas() {
        val tdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, DummyState())
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "No se deben incluir entradas para la emision de un TDBO"
            }
            transaction {
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                this.verifies() // Ya que no hay estados de entrada.
            }
        }
    }

    /**
     * Tarea 3.
     * Ahora necesitamos asegurar que solo un [EstadoTDBO] es emitido por transacción.
     * TODO: Escriba una restriccion de contrato para asegurar que solo un estado de salida es creado en la transacción.
     * Consejo: Escriba una restricción adicional dentro del bloque [requireThat] que creaste en la tarea anterior.
     */
    @Test
    fun transaccionEmitirTieneQueTenerUnaSalida() {
        val tdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo) // Two outputs fails.
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "Solo un estado de salida debe ser creado al emitir un TDBO."
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo) // One output passes.
                this.verifies()
            }
        }
    }

    /**
     * Tarea 4.
     * Ahora debemos considerar las propiedades del [EstadoTDBO]. Necesitamos asegurar que un TDBO siempre
     * tiene un valor positivo.
     * TODO: Esriba una restriccion de contrato para asegurar que los nuevos TDBO emitidos tengan valor positivo.
     * Consejo: ¡Necesitarás varios consejos para completar esta tarea!
     * - Use la palabra clave de kotlin 'val' para crear una nueva constante que almacenará la referencia al estado de salida TDBO.
     * - Puedes usar la funcion de kotlin [single] para atrapar el unico elemento de la lista o tirar una excepción.
     *   si existen 0 o más de un elemento en la lista. Fíjese que ya hemos comprobado en la tarea anterior que la lista de salida
     *   tenga sólo un elemento.
     * - Necesitamos obtener referencia del TDBO propuesto para emisión de la lista [LedgerTransaction.outputs].
     *   Esta lista es de tipo [ContractState]s, por ende necesitamos castear el [ContractState] que recibimos
     *   de [single] a un [EstadoTDBO]. Puedes utilizar la palabra clave de Kotlin 'as' para castear una clase. E.j.
     *
     *       val estado = tx.outputStates.single() as EstadoX
     *
     * - Cuando comprobamos la propiedad [EstadoTDBO.cantidad] es mayor a cero, necesitas comprobar el campo
     *   [EstadoTDBO.amount.quantity].
     */
    @Test
    fun noPuedeCrearTDBOSConValorCero() {
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, EstadoTDBO(0.POUNDS, ALICE.party, BOB.party)) // Zero amount fails.
                this `fails with` "Un TDBO recién emitido debe contener una cantidad positiva"
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, EstadoTDBO(100.SWISS_FRANCS, ALICE.party, BOB.party))
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, EstadoTDBO(1.POUNDS, ALICE.party, BOB.party))
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party))
                this.verifies()
            }
        }
    }

    /**
     * Tarea 5.
     * Por razones obvias, la identidad del prestamista y el deudor deben ser diferentes.
     * TODO: Agrega una restricción de contrato para revisar que el prestamista no sea el deudor.
     * Consejo:
     * - Puedes utilizar las propiedades [EstadoTDBO.lender] y [EstadoTDBO.borrower].
     * - Esta comprobación se debe hacer antes de revisar quienes han firmado.
     */
    @Test
    fun deudorYPrestamistaNoPuedenSerElMismo() {
        val tdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        val prestamistaEsDeudorTDBO = EstadoTDBO(10.POUNDS, ALICE.party, ALICE.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey),ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, prestamistaEsDeudorTDBO)
                this `fails with` "El prestamista y el deudor no pueden tener la misma identidad."
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this.verifies()
            }
        }
    }

    /**
     * Tarea 6.
     * La lista de las llaves publicas que los comandos almacenan deben contener a todos los participantes definidos en [EstadoTDBO].
     * Esto es por que el TDBO es un acuerdo bilateral donde ambos participantes involucrados deben firmar para emitir un
     * TDBO nuevo o cambiar las propiedades de un TDBO existente.
     * TODO: Agrega una restricción de contrato para comprobar que todos los firmantes registrados en [EstadoTDBO] son los participantes.
     * Consejo:
     * - En kotlin puedes comprobar si dos sets son iguales usando el operador ==.
     * - Necesitamos comprobar que los firmantes de la transacción son un subset de la lista de participantes.
     * - No queremos llaves publicas adicionales que no estén en la lista de particpantes del TDBO.
     * - Necesitarás una referencia al comando Emitir para tener acceso a la lista de firmantes.
     * - [requireSingleCommand] devuelve el comando singular requerido - puedes asignar el valor devuelto a una constante.
     *
     * Consejos de Kotlin
     * Kotlin provee la función map para una conversión fácil de [Collection] usando map
     * - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/map.html
     * [Collection] puede convertirse en un set usando toSet()
     * - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/to-set.html
     */
    @Test
    fun deudorYPrestamistaDebenFirmarLaTransaccion() {
        val tdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(DUMMY.publicKey, ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "El prestamista y el deudor deben de firmar juntos para emitir un TDBO."
            }
            transaction {
                command(ALICE.publicKey, ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "El prestamista y el deudor deben de firmar juntos para emitir un TDBO."
            }
            transaction {
                command(BOB.publicKey, ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "El prestamista y el deudor deben de firmar juntos para emitir un TDBO."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, BOB.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "El prestamista y el deudor deben de firmar juntos para emitir un TDBO."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, MINICORP.publicKey, ALICE.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "El prestamista y el deudor deben de firmar juntos para emitir un TDBO."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, BOB.publicKey, ALICE.publicKey), ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey),ContratoTDBO.Commands.Emitir())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this.verifies()
            }
        }
    }
}
