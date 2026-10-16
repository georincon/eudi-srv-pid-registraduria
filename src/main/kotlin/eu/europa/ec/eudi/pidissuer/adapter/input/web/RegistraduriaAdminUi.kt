/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 5 y 9.5.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.GrupoSanguineo
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.DeleteCedula
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.ListCedulas
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.SaveCedula
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.server.*

/**
 * Panel admin de la Registraduría: login propio (ver
 * [configureRegistraduriaAdminSecurity]) + CRUD de ciudadanos + subida de la
 * foto de referencia. Deliberadamente sin firma, huella ni código de barras
 * (ver CONTEXTO-REGISTRADURIA.md, sección 3).
 */
class RegistraduriaAdminUi(
    private val listCedulas: ListCedulas,
    private val findCedulaByDocumentNumber: FindCedulaByDocumentNumber,
    private val saveCedula: SaveCedula,
    private val deleteCedula: DeleteCedula,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(ADMIN_LOGIN) { handleLogin(it) }

            GET(ADMIN_CIUDADANOS) { handleList(it) }
            GET(ADMIN_CIUDADANOS_NEW) { handleNewForm() }
            GET(ADMIN_CIUDADANOS_EDIT) { handleEditForm(it) }
            GET(ADMIN_CIUDADANOS_FOTO) { handlePhoto(it) }

            POST(
                ADMIN_CIUDADANOS,
                contentType(MediaType.MULTIPART_FORM_DATA),
                ::handleSave,
            )
            POST(ADMIN_CIUDADANOS_DELETE) { handleDelete(it) }
        }

    private suspend fun handleLogin(request: ServerRequest): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "registraduria-admin-login",
                mapOf(
                    "error" to request.queryParam("error").isPresent,
                    "loggedOut" to request.queryParam("logout").isPresent,
                ),
            )

    private suspend fun handleList(request: ServerRequest): ServerResponse {
        val query = request.queryParam("q").orElse(null)
        val cedulas = listCedulas(query)
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "registraduria-admin-list",
                mapOf(
                    "cedulas" to cedulas,
                    "query" to (query ?: ""),
                    "total" to cedulas.size,
                ),
            )
    }

    private suspend fun handleNewForm(): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("registraduria-admin-form", formModel(cedula = null))

    private suspend fun handleEditForm(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        val cedula = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))
        return if (cedula == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait("registraduria-admin-form", formModel(cedula))
        }
    }

    private suspend fun handlePhoto(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        val foto = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))?.fotoReferencia
        return if (foto == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse
                .ok()
                .contentType(MediaType.parseMediaType(foto.contentType))
                .bodyValueAndAwait(foto.bytes)
        }
    }

    private suspend fun handleSave(request: ServerRequest): ServerResponse {
        val parts = request.awaitMultipartData()
        return try {
            val form = parts.toCedulaForm()
            val existing = findCedulaByDocumentNumber(CedulaDocumentNumber(form.documentNumber))
            val fotoReferencia = form.foto ?: existing?.fotoReferencia
            val cedula =
                Cedula(
                    id = existing?.id,
                    documentNumber = CedulaDocumentNumber(form.documentNumber),
                    apellidos = form.apellidos,
                    nombres = form.nombres,
                    fechaNacimiento = form.fechaNacimiento,
                    lugarNacimientoCiudad = form.lugarNacimientoCiudad,
                    lugarNacimientoDepartamento = form.lugarNacimientoDepartamento,
                    sexo = form.sexo,
                    estaturaCm = form.estaturaCm,
                    grupoSanguineo = form.grupoSanguineo,
                    fechaExpedicion = form.fechaExpedicion,
                    lugarExpedicion = form.lugarExpedicion,
                    nacionalidad = form.nacionalidad,
                    fotoReferencia = fotoReferencia,
                )
            saveCedula(cedula)
            ServerResponse
                .status(303)
                .location(java.net.URI(ADMIN_CIUDADANOS))
                .buildAndAwait()
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid citizen form submission: {}", e.message)
            ServerResponse
                .badRequest()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "registraduria-admin-form",
                    formModel(cedula = null) +
                        mapOf("validationError" to (e.message ?: "Datos inválidos")),
                )
        }
    }

    private suspend fun handleDelete(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        deleteCedula(CedulaDocumentNumber(documentNumber))
        return ServerResponse
            .status(303)
            .location(java.net.URI(ADMIN_CIUDADANOS))
            .buildAndAwait()
    }

    private fun formModel(cedula: Cedula?): Map<String, Any?> =
        mapOf(
            "cedula" to cedula,
            "isEdit" to (cedula != null),
            "gruposSanguineos" to GrupoSanguineo.entries.map { it.etiqueta },
        )

    companion object {
        const val ADMIN_BASE_PATH: String = "/registraduria/admin"
        const val ADMIN_LOGIN: String = "$ADMIN_BASE_PATH/login"
        const val ADMIN_LOGOUT: String = "$ADMIN_BASE_PATH/logout"
        const val ADMIN_CIUDADANOS: String = "$ADMIN_BASE_PATH/ciudadanos"
        const val ADMIN_CIUDADANOS_NEW: String = "$ADMIN_CIUDADANOS/nuevo"
        const val ADMIN_CIUDADANOS_EDIT: String = "$ADMIN_CIUDADANOS/{documentNumber}/editar"
        const val ADMIN_CIUDADANOS_FOTO: String = "$ADMIN_CIUDADANOS/{documentNumber}/foto"
        const val ADMIN_CIUDADANOS_DELETE: String = "$ADMIN_CIUDADANOS/{documentNumber}/borrar"
        private val log = LoggerFactory.getLogger(RegistraduriaAdminUi::class.java)
    }
}

private data class CedulaForm(
    val documentNumber: String,
    val apellidos: String,
    val nombres: String,
    val fechaNacimiento: LocalDate,
    val lugarNacimientoCiudad: String,
    val lugarNacimientoDepartamento: String?,
    val sexo: Sexo,
    val estaturaCm: Int?,
    val grupoSanguineo: GrupoSanguineo?,
    val fechaExpedicion: LocalDate?,
    val lugarExpedicion: String?,
    val nacionalidad: String,
    val foto: FotoReferencia?,
)

private suspend fun MultiValueMap<String, Part>.toCedulaForm(): CedulaForm {
    fun field(name: String): String? = (getFirst(name) as? FormFieldPart)?.value()?.takeIf { it.isNotBlank() }

    val documentNumber =
        requireNotNull(field("documentNumber")) { "El número de documento es obligatorio" }
    val apellidos = requireNotNull(field("apellidos")) { "Los apellidos son obligatorios" }
    val nombres = requireNotNull(field("nombres")) { "Los nombres son obligatorios" }
    val fechaNacimiento =
        requireNotNull(field("fechaNacimiento")) { "La fecha de nacimiento es obligatoria" }.let(LocalDate::parse)
    val lugarNacimientoCiudad =
        requireNotNull(field("lugarNacimientoCiudad")) { "La ciudad de nacimiento es obligatoria" }
    val sexo =
        requireNotNull(field("sexo")?.let { runCatching { Sexo.valueOf(it) }.getOrNull() }) {
            "El sexo debe ser M o F"
        }

    val fotoPart = get("foto")?.firstOrNull() as? FilePart
    val foto =
        if (fotoPart != null && fotoPart.filename().isNotBlank()) {
            val bytes = fotoPart.readAllBytes()
            val contentType = fotoPart.headers().contentType?.toString() ?: "image/jpeg"
            FotoReferencia(bytes, contentType)
        } else {
            null
        }

    return CedulaForm(
        documentNumber = documentNumber,
        apellidos = apellidos,
        nombres = nombres,
        fechaNacimiento = fechaNacimiento,
        lugarNacimientoCiudad = lugarNacimientoCiudad,
        lugarNacimientoDepartamento = field("lugarNacimientoDepartamento"),
        sexo = sexo,
        estaturaCm = field("estaturaCm")?.toIntOrNull(),
        grupoSanguineo = field("grupoSanguineo")?.let { GrupoSanguineo.fromEtiqueta(it) },
        fechaExpedicion = field("fechaExpedicion")?.let(LocalDate::parse),
        lugarExpedicion = field("lugarExpedicion"),
        nacionalidad = field("nacionalidad") ?: "CO",
        foto = foto,
    )
}

private suspend fun FilePart.readAllBytes(): ByteArray {
    val buffer = DataBufferUtils.join(content()).awaitSingle()
    return try {
        ByteArray(buffer.readableByteCount()).also { buffer.read(it) }
    } finally {
        DataBufferUtils.release(buffer)
    }
}
