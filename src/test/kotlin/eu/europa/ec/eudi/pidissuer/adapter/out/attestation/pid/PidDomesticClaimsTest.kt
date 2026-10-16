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
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PidDomesticClaimsTest {
    @Test
    fun `domestic namespace follows ARF PID_06 (pid namespace + country code + version)`() {
        assertEquals("eu.europa.ec.eudi.pid.co.1", MsoMdocPidCoClaims.nameSpace)
        assertEquals("eu.europa.ec.eudi.pid.1", MsoMdocPidClaims.nameSpace)
    }

    @Test
    fun `mdoc and sd-jwt PID expose the cedula specific claims`() {
        val names = listOf("estatura_cm", "grupo_sanguineo_rh", "fecha_expedicion", "lugar_expedicion")
        val mdocAll = MsoMdocPidClaims.all().map { it.name }
        val sdAll = SdJwtVcPidClaims.all().map { it.name }
        names.forEach {
            assertTrue(it in mdocAll, "mdoc missing $it")
            assertTrue(it in sdAll, "sd-jwt missing $it")
        }
    }

    @Test
    fun `age_over_18 is not part of the PID (PID Rulebook v1_1 removed age verification attributes)`() {
        assertTrue(MsoMdocPidClaims.all().none { it.name.startsWith("age_") })
        assertTrue(SdJwtVcPidClaims.all().none { it.name.startsWith("age_") })
    }
}
