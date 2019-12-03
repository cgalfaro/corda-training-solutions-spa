package net.corda.training.contract

import net.corda.core.contracts.Amount
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.packageName
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.training.ALICE
import net.corda.training.BOB
import net.corda.training.CHARLIE
import net.corda.training.state.EstadoTDBO
import org.junit.Test
import java.util.*

/**
 * Instrucciones para ejercicios prácticos de contrato Parte 3.
 * El objetivo es escribir código de contrato que verifique una transacción de liquidar un [EstadoTDBO].
 * Liquidar es un poco mas complejo que transferir y emitir ya que requiere el uso de multiples tipos de estado
 * en una transacción.
 * Como con los [TDBOEmitirTests] y [TDBOTransferirTests] descomenta cada prueba y ejecutala una a la vez. Utiliza la definición
 * de las pruebas y la descripción de cada tarea para determinar como pasar las pruebas.
 */
class TDBOLiquidarTests {
    private fun createCashState(amount: Amount<Currency>, owner: AbstractParty): Cash.State {
        val defaultRef = ByteArray(1, { 1 })
        return Cash.State(amount = amount `issued by`
                TestIdentity(CordaX500Name(organisation = "MegaCorp", locality = "MegaPlanet", country = "US")).ref(defaultRef.first()),
                owner = owner)
    }

    // Un comando pre-definido
    class DummyCommand : TypeOnlyCommandData()

    var ledgerServices = MockServices(listOf("net.corda.training", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    /**
     * Tarea 1.
     * Necesitamos agregar otro caso en nuestro "when" para manejar la liquidación con la función [ContratoTDBO.verify].
     * TODO: Agregar el caso [ContratoTDBO.Commands.Settle] a la función verify.
     * Consejo: Por el momento puedes dejar el cuerpo vacio.
     */
    @Test
    fun debeIncluidComandoLiquidar() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        val cashEntrada = createCashState(5.POUNDS, BOB.party)
        val cashSalida = cashEntrada.withNewOwner(newOwner = ALICE.party).ownableState
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cashEntrada)
                output(Cash.PROGRAM_ID, cashSalida)
                command(BOB.publicKey, Cash.Commands.Move())
                this.failsWith("Contract verification failed");
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cashEntrada)
                output(Cash.PROGRAM_ID, cashSalida)
                command(BOB.publicKey, Cash.Commands.Move())
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand()) // Tipo incorrecto.
                this.failsWith("Contract verification failed");
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cashEntrada)
                output(Cash.PROGRAM_ID, cashSalida)
                command(BOB.publicKey, Cash.Commands.Move())
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar()) // Correct Type.
                this.verifies()
            }
        }
    }

    /**
     * Tarea 2.
     * Por ahora, solo queremos liquidar un TDBO de un solo. Podemos usar la función [TransactionForContract.groupStates]
     * para agrupar los TDBOs por la propiedad [linearId]. Queremos asegurarnos que solo exista un grupo de entradas y salidas
     * de TDBOs.
     * TODO: Usando [groupStates] agrega una restricción que revise por un grupo de entradas/salidas de TDBOs.
     * Consejo:
     * - La función [single] fuerza a un elemento único en la lista o lanza una excepción.
     * - La función [groupStates] recibe dos tipos de parámetros: el tipo de estado por el que quieres agrupar y el tipo
     *   de llave de agrupación que utilizas, en este caso debes utilizar [linearId] y es un [UniqueIdentifier].
     * - La función [groupStates] tambien recibe una función lambda que selecciona una propiedad del estado para decidir los grupos.
     * - En Kotlin si el último argumento de una función es una lambda, la puedes llamar de la siguiente manera:
     *
     *       fun funcionConLambda() { it.property }
     *
     *   Esto es como map / filter son utilizados en Kotlin.
     */
    @Test
    fun debeSerUnGrupoDeTDBOs() {
        val tdboUno = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        val tdboDos = EstadoTDBO(5.POUNDS, ALICE.party, BOB.party)
        val cashEntrada = createCashState(5.POUNDS, BOB.party)
        val cashSalida = cashEntrada.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdboDos)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cashEntrada)
                output(Cash.PROGRAM_ID, cashSalida.ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this `fails with` "List has more than one element."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cashEntrada)
                output(Cash.PROGRAM_ID, cashSalida.ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 3.
     * Siempre debe de haber un TDBO de entrada en una transacción de liquidar, pero pueda que no exista un TDBO de salida.
     * TODO: Agrega una restricción para comprobar que siempre exista un TDBO de entrada.
     */
    @Test
    fun debeTenerUnTDBODeEntrada() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        val tdboUno = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        val diezLibras = createCashState(10.POUNDS, BOB.party)
        val cincoLibras = createCashState(5.POUNDS, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                this `fails with` "Debe existir un TDBO de entrada para liquidar."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno.pagar(5.POUNDS))
                input(Cash.PROGRAM_ID, cincoLibras)
                output(Cash.PROGRAM_ID, cincoLibras.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdboUno)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                input(Cash.PROGRAM_ID, diezLibras)
                output(Cash.PROGRAM_ID, diezLibras.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 4.
     * Ahora debemos asegurarnos que existan estados cash en la lista de salidas. Al [ContratoTDBO] no le importa
     * la validez del cash ya que la transacción será revisada por el contrato [Cash]. Sin embargo
     * cuanto cash está siendo utilizado para liquidar y actualizar nuestro [EstadoTDBO] debidamente.
     * TODO: Filtra los estados cash de la lista de salidas, asignalas a una constante y verifica que hay una salida de Cash.
     * Consejo:
     * - Use la función [outputsOfType] para filtrar lassalidas de la transacción por tipo, en este caso [Cash.State].
     */
    @Test
    fun estadosCashDebenEstarPresentes() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val cash = createCashState(5.DOLLARS, BOB.party)
        val pagoEnCash = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "Debe existir una salida cash."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cash)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                output(Cash.PROGRAM_ID, pagoEnCash.ownableState)
                command(BOB.publicKey, pagoEnCash.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 5.
     * No solo debemos verificar que los estados [Cash] de salida están presentes también necesitamos revisar que el pagador
     * está asignando correctamente al prestamista como el dueño de estos estados.
     * TODO: Agregar una restriccón para asegurarnos que el prestamista sea el dueño del estado de salida Cash.
     * Consejo:
     * - El cash completo no puede ser asignado a nosotros ya que parte del cash de input ouede ser devuelto al pagador como vuelto.
     * - Necesitamos usar la propiedad [Cash.State.owner] para revisar que sea el valor de la llave pública del prestamista.
     * - Use [filter] para filtrar la lista de estados cash para que el prestamista reciba los que tiene asignados.
     * - Una vez hayamos filtrado la lista, podemos sumar el cash pagado al prestamista para saber cuánto está liquidando.
     */
    @Test
    fun debeTenerEstadosCashDeSalidaConElRecibidorComoPropietario() {
        val tdbo = EstadoTDBO(10.POUNDS, ALICE.party, BOB.party)
        val cash = createCashState(5.POUNDS, BOB.party)
        val pagoCashInvalido = cash.withNewOwner(newOwner = CHARLIE.party)
        val pagoCashValido = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cash)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.POUNDS))
                output(Cash.PROGRAM_ID, "outputs cash", pagoCashInvalido.ownableState)
                command(BOB.publicKey, pagoCashInvalido.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "Debe existir cash en estado de salida pagado al prestamista."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cash)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.POUNDS))
                output(Cash.PROGRAM_ID, pagoCashValido.ownableState)
                command(BOB.publicKey, pagoCashValido.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 6.
     * Ahora debemos sumar el cash que se esta asignando al prestamista y comparar este total con cuanto del TDBO
     * hace falta pagar.
     * TODO: Agrega un restricción que revisa que no se puede pagar al prestamista mas de lo que falta pagar del TDBO.
     * Consejo:
     * - La cantidad restandte del TDBO lo obtenemos al restar las propiedades [EstadoTDBO.cantidad] menos [EstadoTDBO.pagado].
     * - Para sumar la lista de [Cash.State]s use la función [sumCash].
     * - La función [sumCash] devuelve de tipo [Issued<Amount<Currency>>]. No nos interesa quien lo creó por lo que podemos
     *   aplicar [withoutIssuer] para descubrir [Amount] de [Issuer].
     * - Podemos comparar la cantidad que falta pagar con la cantidad utilizada para pagar, asegurándonos
     *   que la cantidad que pagamos no sea demasiado.
     */
    @Test
    fun laCantidadDeCashConLaQueLiquidaDebeSerMenorALaCantidadRestanteDelTDBO() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val onceDolares = createCashState(11.DOLLARS, BOB.party)
        val diezDolares = createCashState(10.DOLLARS, BOB.party)
        val cincoDolares = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, onceDolares)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(11.DOLLARS))
                output(Cash.PROGRAM_ID, onceDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, onceDolares.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "La cantidad que se paga no puede ser más que la cantidad que falta pagar."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this.verifies()
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, diezDolares)
                output(Cash.PROGRAM_ID, diezDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, diezDolares.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this.verifies()
            }
        }
    }

    /**
     * Task 7.
     * El type system de Kotlin debería de manejar esto por nosotros pero debemos mencionar que solo deberiamos poder liquidar
     * en la moneda que el TDBO ha sido creada.
     * TODO: ¡No deberías de hacer nada pero aquí hay unas pruebas para asegurarnos!
     * Consejo: Lee y comprende las pruebas.
     */
    @Test
    fun elCashUtilizadoParaLiquidarDebeSerDeLaMonedaCorrecta() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val diezDolares = createCashState(10.DOLLARS, BOB.party)
        val diezLibras = createCashState(10.POUNDS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, diezLibras)
                output(Cash.PROGRAM_ID, diezLibras.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, diezLibras.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "Token mismatch: GBP vs USD"
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, diezDolares)
                output(Cash.PROGRAM_ID, diezDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, diezDolares.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this.verifies()
            }
        }
    }

    /**
     * Tarea 8.
     * Si pagamos el TDBO completo, ya terminamos y no se requiere un [EstadoTDBO] (ledgerServices.ledger). Sin embargo,
     * si solo pagamos parcialmente el TDBO, entonces queremos manten el TDBO en el libro mayor (ledger) con un cambio
     * a la propiedad [EstadoTDBO.pagado].
     * TODO: Escribe una restricción que aegure que el comportamiento correcto se ejecute dependiendo de la cantidad pagada vs el restante.
     * Consejo: Puedes usar un "if" y comparar el total de la cantidad pagada vs lo que falta por pagar.
     */
    @Test
    fun debTenerSoloUnTDBODeSalidaSiNoSeLiquidaCompleamente() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val diezDolares = createCashState(10.DOLLARS, BOB.party)
        val cincoDolares = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "La deuda no se pagó completa debe haber un TDBO de salida."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                verifies()
            }
            transaction {
                input(Cash.PROGRAM_ID, diezDolares)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(10.DOLLARS))
                output(Cash.PROGRAM_ID, diezDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, diezDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "La deuda se pagó completa no debe existir TDBO de salida."
            }
            transaction {
                input(Cash.PROGRAM_ID, diezDolares)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(Cash.PROGRAM_ID, diezDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, diezDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                verifies()
            }
        }
    }

    /**
     * Tarea 9.
     * Queremos asegurarnos que únicamente cambie la propiedad pagado en una transacción de liquidar TDBO.
     * TODO: Escribe una restricción para verificar que solo la propiedad [EstadoTDBO.pagado] cambie cuando liquidamos.
     */
    @Test
    fun soloLaPropiedadPagadoPuedeCambiar() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val cincoDolares = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.copy(deudor = ALICE.party, pagado = 5.DOLLARS))
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "El deudor no puede cambiar cuando liquidamos."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.copy(cantidad = 0.DOLLARS, pagado = 5.DOLLARS))
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "La cantidad no puede cambiar cuando liquidamos."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.copy(prestamista = CHARLIE.party, pagado = 5.DOLLARS))
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                this `fails with` "El prestamista no puede cambiar cuando liquidamos."
            }
            transaction {
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                input(Cash.PROGRAM_ID, cincoDolares)
                output(Cash.PROGRAM_ID, cincoDolares.withNewOwner(newOwner = ALICE.party).ownableState)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(BOB.publicKey, cincoDolares.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                verifies()
            }
        }
    }

    /**
     * Task 10.
     * Ambos el prestamista y el deudor deben firmar una transacción de liquidar.
     * TODO: Añada una restricción al codigo de contrato que asegure lo mencionado arriba.
     */
    @Test
    fun debeSerFirmadoPorTodosLosParticipantes() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        val cash = createCashState(5.DOLLARS, BOB.party)
        val pagoCash = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(Cash.PROGRAM_ID, pagoCash.ownableState)
                command(BOB.publicKey, pagoCash.command)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(listOf(ALICE.publicKey, CHARLIE.publicKey), ContratoTDBO.Commands.Liquidar())
                failsWith("Ambos el prestamista y el deudor deben firmar una transacción de liquidar.")
            }
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(Cash.PROGRAM_ID, pagoCash.ownableState)
                command(BOB.publicKey, pagoCash.command)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(BOB.publicKey, ContratoTDBO.Commands.Liquidar())
                failsWith("Ambos el prestamista y el deudor deben firmar una transacción de liquidar.")
            }
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(ContratoTDBO.TDBO_CONTRACT_ID, tdbo)
                output(Cash.PROGRAM_ID, pagoCash.ownableState)
                command(BOB.publicKey, pagoCash.command)
                output(ContratoTDBO.TDBO_CONTRACT_ID, tdbo.pagar(5.DOLLARS))
                command(listOf(ALICE.publicKey, BOB.publicKey), ContratoTDBO.Commands.Liquidar())
                verifies()
            }
        }
    }
}

