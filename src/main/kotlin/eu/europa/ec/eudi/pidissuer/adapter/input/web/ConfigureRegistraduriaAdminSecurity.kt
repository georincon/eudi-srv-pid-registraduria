/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 5.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers

/**
 * Security chain independiente para el panel admin de la Registraduría
 * (login propio con usuario/contraseña, **no** Keycloak — ver sección 5 de
 * CONTEXTO-REGISTRADURIA.md). Protege todo bajo [RegistraduriaAdminUi.ADMIN_BASE_PATH]
 * salvo la propia página de login.
 *
 * Debe registrarse con un `@Order` entre la cadena de la UI genérica
 * (que solo matchea sus propias rutas) y la cadena "catch-all" de la API
 * OAuth2/DPoP, para que intercepte antes de caer en esta última.
 */
fun configureRegistraduriaAdminSecurity(
    http: ServerHttpSecurity,
    userDetailsService: ReactiveUserDetailsService,
): SecurityWebFilterChain =
    http {
        securityMatcher(ServerWebExchangeMatchers.pathMatchers("${RegistraduriaAdminUi.ADMIN_BASE_PATH}/**"))

        authenticationManager = reactiveAuthenticationManager(userDetailsService)

        authorizeExchange {
            authorize(RegistraduriaAdminUi.ADMIN_LOGIN, permitAll)
            authorize(anyExchange, hasRole("ADMIN"))
        }

        formLogin {
            loginPage = RegistraduriaAdminUi.ADMIN_LOGIN
            authenticationSuccessHandler =
                org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler(
                    RegistraduriaAdminUi.ADMIN_CIUDADANOS,
                )
        }

        logout {
            logoutUrl = RegistraduriaAdminUi.ADMIN_LOGOUT
            logoutSuccessHandler =
                org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler()
                    .apply { setLogoutSuccessUrl(java.net.URI(RegistraduriaAdminUi.ADMIN_LOGIN)) }
        }

        csrf {
            // El formulario de alta/edición de ciudadano usa multipart/form-data
            // (foto de referencia); por defecto Spring Security reactivo no lee
            // el token CSRF de cuerpos multipart, solo de application/x-www-form-urlencoded
            // o de un header — hay que habilitarlo explícitamente.
            csrfTokenRequestHandler =
                XorServerCsrfTokenRequestAttributeHandler().apply { setTokenFromMultipartDataEnabled(true) }
        }
        cors { disable() }
    }

private fun reactiveAuthenticationManager(
    userDetailsService: ReactiveUserDetailsService,
): org.springframework.security.authentication.ReactiveAuthenticationManager {
    val manager =
        org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager(
            userDetailsService,
        )
    manager.setPasswordEncoder(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder())
    return manager
}
