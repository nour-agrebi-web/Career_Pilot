package tn.careerpilot.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginLockoutTextTest {

    @Test
    fun `compte a rebours au format minutes et secondes`() {
        assertEquals("5:00", formatCountdown(300))
        assertEquals("1:05", formatCountdown(65))
        assertEquals("0:09", formatCountdown(9))
    }

    @Test
    fun `pendant un blocage le bouton et le message affichent le temps restant`() {
        assertEquals("Bloqué · 2:59", loginButtonText(179))
        assertEquals("Trop de tentatives. Réessayez dans 0:42.", authMessage(FormState(), 42))
    }

    @Test
    fun `sans blocage le bouton et le message reviennent a la normale`() {
        assertEquals("Se connecter", loginButtonText(0))
        assertEquals("E-mail ou mot de passe incorrect.", authMessage(FormState(error = "E-mail ou mot de passe incorrect."), 0))
        assertNull(authMessage(FormState(), 0))
    }
}
