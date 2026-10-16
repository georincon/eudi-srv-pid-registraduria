/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.port.out.registraduria

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber

/**
 * Busca un ciudadano por número de cédula. Lo usan tanto el panel admin
 * (para editar) como el adapter que arma la PidAttributes para emisión.
 */
fun interface FindCedulaByDocumentNumber {
    suspend operator fun invoke(documentNumber: CedulaDocumentNumber): Cedula?
}

/**
 * Lista ciudadanos para el panel admin, con un filtro de texto libre opcional
 * (por documento o por nombre/apellido) como en la pantalla de listado.
 */
fun interface ListCedulas {
    suspend operator fun invoke(searchQuery: String?): List<Cedula>
}

/**
 * Crea o actualiza un ciudadano (upsert por documentNumber). Devuelve el
 * registro guardado, con su [Cedula.id] asignado si era nuevo.
 */
fun interface SaveCedula {
    suspend operator fun invoke(cedula: Cedula): Cedula
}

/**
 * Borra un ciudadano del panel admin. Nota (ver pantalla del panel de
 * referencia): esto no borra al usuario ya sincronizado en Keycloak si lo
 * hubiera — solo evita que se pueda volver a verificar/emitir para él.
 */
fun interface DeleteCedula {
    suspend operator fun invoke(documentNumber: CedulaDocumentNumber): Boolean
}
