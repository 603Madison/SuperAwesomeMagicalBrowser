package app.superawesomemagical.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressTest {
    @Test fun addressesOpenAsTheyAre() {
        assertEquals("https://example.org/a?b=c", Address.toUrl("https://example.org/a?b=c"))
        assertEquals("http://example.org", Address.toUrl("  http://example.org "))
        assertEquals("about:config", Address.toUrl("about:config"))
    }

    @Test fun bareDomainsGetHttps() {
        assertEquals("https://example.org", Address.toUrl("example.org"))
        assertEquals("https://www.ecosia.org/search?q=x", Address.toUrl("www.ecosia.org/search?q=x"))
        assertEquals("https://bücher.de", Address.toUrl("bücher.de"))
        assertEquals("https://music.example.com:8443/app", Address.toUrl("music.example.com:8443/app"))
    }

    @Test fun otherSchemesGoToTheSystem() {
        assertEquals("mailto:someone@example.org", Address.toUrl("mailto:someone@example.org"))
        assertEquals("tel:+15555550100", Address.toUrl("tel:+15555550100"))
    }

    @Test fun localAddressesGetHttp() {
        assertEquals("http://localhost:8080", Address.toUrl("localhost:8080"))
        assertEquals("http://192.168.1.20/admin", Address.toUrl("192.168.1.20/admin"))
    }

    @Test fun everythingElseIsSearchedWithEcosia() {
        assertEquals("https://www.ecosia.org/search?q=northern+lights", Address.toUrl("northern lights"))
        assertEquals("https://www.ecosia.org/search?q=kotlin", Address.toUrl("kotlin"))
        assertEquals("https://www.ecosia.org/search?q=what+is+example.org", Address.toUrl("what is example.org"))
        assertEquals("https://www.ecosia.org/search?q=c%2B%2B", Address.toUrl("c++"))
        assertNull(Address.toUrl("   "))
    }

    @Test fun searchTermsShowInsteadOfTheAddress() {
        assertEquals("northern lights", Address.searchTerms(Address.search("northern lights")))
        assertNull(Address.searchTerms("https://example.org/?q=x"))
    }

    @Test fun hostsAndNames() {
        assertEquals("github.com", Address.host("https://www.github.com/603Madison"))
        assertEquals("Github", Address.siteName("https://github.com/"))
        assertTrue(Address.isWeb("https://example.org"))
        assertFalse(Address.isWeb("tel:+15555550100"))
        assertFalse(Address.isWeb("intent://scan/#Intent;scheme=zxing;end"))
    }
}
