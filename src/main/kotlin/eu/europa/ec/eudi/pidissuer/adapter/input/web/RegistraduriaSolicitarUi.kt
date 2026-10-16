/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 2 ("Pantalla ciudadana").
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import arrow.core.raise.effect
import arrow.core.raise.fold
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.domain.OpenId4VciSpec
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.qr.Dimensions
import eu.europa.ec.eudi.pidissuer.port.out.qr.Format
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.qr.Pixels
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import kotlin.io.encoding.Base64

/**
 * Pantalla ciudadana "sin login": el ciudadano escribe su número de cédula y
 * recibe un QR para pedir su PID con **cualquier wallet EUDI genérica** (a
 * diferencia del flujo automático de onboarding de [OnboardingOffersApi],
 * pensado para la wallet propia `newwallet-app`).
 *
 * Esta pantalla solo genera y muestra la oferta — la verificación real de
 * "quién es quién" ocurre después, cuando la wallet abre la oferta y
 * Keycloak pide de nuevo el número de cédula (sin contraseña: ver
 * `docker-compose/scripts/configurar-login-sin-password.py`, aplicado solo
 * al cliente OAuth2 que use esa wallet — no a todo el realm).
 */
class RegistraduriaSolicitarUi(
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val findCedulaByDocumentNumber: FindCedulaByDocumentNumber,
    private val generateQrCode: GenerateQqCode,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(SOLICITAR, contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML)) { handleForm(null) }
            POST(
                SOLICITAR,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handleSubmit,
            )
        }

    private suspend fun handleForm(error: String?): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("registraduria-solicitar-form", mapOf("error" to error))

    private suspend fun handleSubmit(request: ServerRequest): ServerResponse {
        val formData = request.awaitFormData()
        val documentNumber = formData["documentNumber"]?.firstOrNull()?.trim().orEmpty()
        if (documentNumber.isBlank()) {
            return handleForm("Debes ingresar tu número de cédula")
        }

        val cedula = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))
        if (cedula == null) {
            log.info("Solicitud de PID para cédula no encontrada")
            return handleForm("No encontramos esa cédula en la Registraduría")
        }
        if (cedula.fotoReferencia == null) {
            log.info("Solicitud de PID para cédula sin foto de referencia: {}", documentNumber)
            return handleForm("Tu registro está incompleto (falta la foto de referencia). Contacta a la Registraduría.")
        }

        return effect<CreateCredentialsOffer.Error, Uri> {
            createCredentialsOffer(CreateCredentialsOffer.Request(OnboardingOffersApi.PID_CONFIGURATION_IDS))
        }.fold(
            transform = { offerUri ->
                log.info("Oferta de PID generada para solicitud manual (cédula {})", documentNumber)
                context(generateQrCode) { offerUri.qrResponse(cedula.nombres) }
            },
            recover = { error ->
                log.warn("No se pudo generar la oferta para la cédula {}: {}", documentNumber, error)
                handleForm("No se pudo generar la oferta de credencial. Intenta de nuevo.")
            },
        )
    }

    companion object {
        const val SOLICITAR: String = "/registraduria/solicitar"
        private val log = LoggerFactory.getLogger(RegistraduriaSolicitarUi::class.java)
    }
}

context(generateQrCode: GenerateQqCode)
private suspend fun Uri.qrResponse(nombres: String): ServerResponse {
    val uri = this@qrResponse
    val qrCode = generateQrCode(uri, Format.PNG, Dimensions(Pixels(300u), Pixels(300u)))
    return ServerResponse
        .ok()
        .contentType(MediaType.TEXT_HTML)
        .renderAndAwait(
            "registraduria-solicitar-qr",
            mapOf(
                "nombres" to nombres,
                "uri" to uri.toString(),
                "qrCode" to Base64.encode(qrCode),
                "qrCodeMediaType" to "image/png",
                "openid4VciVersion" to OpenId4VciSpec.VERSION,
            ),
        )
}
