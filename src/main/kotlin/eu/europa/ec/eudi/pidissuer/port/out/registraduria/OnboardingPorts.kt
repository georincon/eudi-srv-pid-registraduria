/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package eu.europa.ec.eudi.pidissuer.port.out.registraduria

import kotlinx.datetime.LocalDate

/**
 * Resultado de una sesión de onboarding, tal como lo reporta el backend de onboarding
 * (lectura del PDF417/OCR de la cédula + comparación facial). Es la evidencia que la
 * Registraduría homologa contra su base antes de ofrecer el PID.
 */
data class OnboardingSession(
    val decision: String?,
    val status: String?,
    val documentNumber: String?,
    val firstNames: String,
    val lastNames: String,
    val birthDate: LocalDate?,
)

sealed interface OnboardingSessionResult {
    data class Found(
        val session: OnboardingSession,
    ) : OnboardingSessionResult

    data object NotFound : OnboardingSessionResult

    data class Unavailable(
        val reason: String,
    ) : OnboardingSessionResult
}

fun interface GetOnboardingSession {
    suspend operator fun invoke(sessionId: String): OnboardingSessionResult
}

/** Ticket de un solo uso que liga una oferta de credenciales a una cédula ya homologada. */
sealed interface TicketRedemption {
    data class Redeemed(
        val documentNumber: String,
    ) : TicketRedemption

    data object NotFound : TicketRedemption

    data object Expired : TicketRedemption
}

interface OfferTickets {
    fun issue(documentNumber: String): String

    fun redeem(ticket: String): TicketRedemption
}
