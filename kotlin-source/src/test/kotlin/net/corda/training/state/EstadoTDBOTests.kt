package net.corda.training.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.finance.*
import net.corda.training.ALICE
import net.corda.training.BOB
import net.corda.training.MEGACORP
import net.corda.training.MINICORP
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Instrucciones de ejercicio práctico.
 * Descomenta el primer unit test [tieneCampoCantidadTDBODelTipoCorrecto] y luego ejecuta el unit test con la flecha verde
 * a la izquierda de la clase [EstadoTDBOTests] o el método [tieneCampoCantidadTDBODelTipoCorrecto].
 * Ejecutando las pruebas desde [EstadoTDBOTests] ejecuta todos los unit tests definidos en la clase.
 * La primera prueba debería de fallar por que necesitas hacer algunos cambios al EstadoTDBO para que pase. Lea el TODO
 * bajo cada número de tarea para una descripción y un concejo de que es necesario hacer.
 * Una vez hayas la prueba pase, descomenta la siguiente prueba.
 * Continúa con todas las pruebas hasta que todas pasen.
 * Consejo: CMD / Ctrl + en los nombres color café en corchetes "[]" para su definición en el código base.
 */
class EstadoTDBOTests {

    /**
     * Tarea 1.
     * TODO: Agregue una propiedad 'cantidad' de tipo [Amount] a la clase [EstadoTDBO] para que esta prueba pase.
     * Consejo: [Amount] es una clase plantilla que toma una clase como parámetro del token que quieras de [Amount].
     * Como estamos lidiando con dinero prestado de un participante a otro hace sentioo que nuestro token sea [Currency].
     */
    @Test
    fun tieneCampoCantidadTDBODelTipoCorrecto() {
        // Does the amount field exist?
        EstadoTDBO::class.java.getDeclaredField("cantidad")
        // Is the amount field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("cantidad").type, Amount::class.java)
    }

    /**
     * Tarea 2.
     * TODO: Agregar 'prestamista' del tipo [Party] a la clase [EstadoTDBO] para que esta prueba pase.
     */
    @Test
    fun tieneCampoPrestamistaDelTipoCorrecto() {
        // Does the lender field exist?
        EstadoTDBO::class.java.getDeclaredField("prestamista")
        // Is the lender field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("prestamista").type, Party::class.java)
    }

    /**
     * Tarea 3.
     * TODO: Agregar 'deudor' de tipo [Party] a la clase [EstadoTDBO] para que esta prueba pase.
     */
    @Test
    fun tieneCampoDeudorDelTipoCorrecto() {
        // Does the borrower field exist?
        EstadoTDBO::class.java.getDeclaredField("deudor")
        // Is the borrower field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("deudor").type, Party::class.java)
    }

    /**
     * Tarea 4.
     * TODO: Agregar una propiedad 'pagado' de tipo [Amount] a la clase [EstadoTDBO] para que esta prueba pase.
     * Consejo:
     * - Queremos que esta propiedad sea iniciada con monto cero en la creación de [EstadoTDBO].
     * - Puedes usar [POUNDS] una extensión sobre [Int] para crear una cantidad de libras esterlina e.j. '10.POUNDS'.
     * - Esta propiedad lleva el registro de cuanto del inicial de [EstadoTDBO.amount] ha sido pagado por el deudor.
     * - Puedes iniciar una propiedad con un valor por defecto en Kotlin de la siguiente manera:
     *
     *       data class(val numero: Int = 10)
     *
     * - Necesitamos asegurarnos que la propiedad [EstadoTDBO.paid] sea de la misma moneda que la propiedad
     *   [EstadoTDBO.amount]. Puedes crear una instancia de la clase [Amount] que toma como valor cero y un token
     *   representando la moneda - que deberia ser la misma moneda que la propiedad [EstadoTDBO.amount].
     */
    @Test
    fun tieneCampoPagadoDelTipoCorrecto() {
        // Does the paid field exist?
        EstadoTDBO::class.java.getDeclaredField("pagado")
        // Is the paid field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("pagado").type, Amount::class.java)
    }

    /**
     * Tarea 5.
     * TODO: Incluya al prestamista en la lista [EstadoTDBO.participants]
     * Consejo: [listOf] toma cualquier numero de parametros y los agrega a la lista
     */
    @Test
    fun prestamistaEsParticipante() {
        val estadoTdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        assertNotEquals(estadoTdbo.participants.indexOf(ALICE.party), -1)
    }

    /**
     * Tarea 6.
     * TODO: Similar a la tarea anterior, incluya al deudor en la lista [EstadoTDBO.participants]
     */
    @Test
    fun deudorEsParticipante() {
        val estadoTdbo = EstadoTDBO(1.POUNDS, ALICE.party, BOB.party)
        assertNotEquals(estadoTdbo.participants.indexOf(BOB.party), -1)
    }

    /**
     * Tarea 7.
     * TODO: Implementar [LinearState] junto con las propiedades y metodos requeridos.
     * Consejo: [LinearState] implementa [ContractState] que define una propiedad y metodo adicional. Puedes usar
     * IntellIJ para que agrefue las definiciones o puedes agregarlas tu mismo. Mira la definicion
     * de [LinearState] para ver que es requerido añadir.
     */
    @Test
    fun esLinearState() {
        assert(LinearState::class.java.isAssignableFrom(EstadoTDBO::class.java))
    }

    /**
     * Tarea 8.
     * TODO: Override la propiedad [LinearState.linearId] y asignarle un valor por medio del constructor.
     * Consejo:
     * - La propiedad [LinearState.linearId] es de tipo [UniqueIdentifier]. Necesitas crear una nueva instancia de
     * la clase [UniqueIdentifier].
     * - El [LinearState.linearId] está diseñado para enlazar todos los [LinearState]s (que representan un estado de un
     * acuerdo un punto de tiempo especifico) juntos. Todos los [LinearState]s con el mismo [LinearState.linearId]
     * representan el ciclo de vida completo de un acuerdo, un valor o un hecho compartido.
     * - Provee un valor por defecto para el [linearId] para un nuevo [EstadoTDBO]
     */
    @Test
    fun tieneCampoLinearIdFieldDeTipoCorrecto() {
        // Does the linearId field exist?
        EstadoTDBO::class.java.getDeclaredField("linearId")
        // Is the linearId field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
    }

    /**
     * Tarea 9.
     * TODO: Asegurarse que los parámetros han sido ordenados correctamente.
     * Consejo: Asegúrate que el deudor y el prestamista no estén en el orden equivocado ya que esto puede causar
     * confusión en las tareas mas adelante!
     */
    @Test
    fun compruebaEstadoTDBOOrdenParametros() {
        val fields = EstadoTDBO::class.java.declaredFields
        val amountIdx = fields.indexOf(EstadoTDBO::class.java.getDeclaredField("cantidad"))
        val lenderIdx = fields.indexOf(EstadoTDBO::class.java.getDeclaredField("prestamista"))
        val borrowerIdx = fields.indexOf(EstadoTDBO::class.java.getDeclaredField("deudor"))
        val paidIdx = fields.indexOf(EstadoTDBO::class.java.getDeclaredField("pagado"))
        val linearIdIdx = fields.indexOf(EstadoTDBO::class.java.getDeclaredField("linearId"))

        assert(amountIdx < lenderIdx)
        assert(lenderIdx < borrowerIdx)
        assert(borrowerIdx < paidIdx)
        assert(paidIdx < linearIdIdx)
    }

    /**
     * Tarea 10.
     * TODO: agregar un metodo para ayuda llamado [pagar] que pueda ser llamado desde [EstadoTDBO] para liquidar una cantidad del TDBO.
     * Consejo:
     * - Necesitarás incrementar la propiedad [EstadoTDBO.pagado] por la cantidad que el deudor quiera pagar.
     * - Agrega una función llamada [pagar] en [EstadoTDBO]. Esta funcion debe devolver un [EstadoTDBO].
     * - El estado existente es inmutable así que un nuevo estado debe ser creado. Kotlin provee un metodo [copy]
     * el cual crea un nuevo objeto con los nuevos valores para los campos especificados.
     * - [copy] devuelve una copia de la instancia del objeto y los campos pueden ser cambiados escpecificando los nuevos valores
     * como parametros de [copy]     */
    @Test
    fun compruebaMetodoAyudaPagar() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        assertEquals(5.DOLLARS, tdbo.pagar(5.DOLLARS).pagado)
        assertEquals(3.DOLLARS, tdbo.pagar(1.DOLLARS).pagar(2.DOLLARS).pagado)
        assertEquals(10.DOLLARS, tdbo.pagar(5.DOLLARS).pagar(3.DOLLARS).pagar(2.DOLLARS).pagado)
    }

    /**
     * Tarea 11.
     * TODO: Agregar metodo de ayuda [conNuevoPrestamista] que pueda ser llamado desde [EstadoTDBO] para cambiar el prestamista del TDBO.
     */
    @Test
    fun compurbeTieneMetodoAyudaNuevoPrestamista() {
        val tdbo = EstadoTDBO(10.DOLLARS, ALICE.party, BOB.party)
        assertEquals(MINICORP.party, tdbo.conNuevoPrestamista(MINICORP.party).prestamista)
        assertEquals(MEGACORP.party, tdbo.conNuevoPrestamista(MEGACORP.party).prestamista)
    }
}
