package com.herbertindustries.alex311

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks installed manifest matching without depending on live domain verification. */
@RunWith(AndroidJUnit4::class)
class AppLinksTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun matches(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.name == MainActivity::class.java.name }
    }

    @Test fun bothHostsMatchOnlySupportedAppLinkPaths() {
        listOf("alex311-reborn.com", "alex311visibility.me").forEach { host ->
            listOf("/r/26-00039674", "/submit", "/submit/my", "/submit/login?mode=signIn&oobCode=TEST").forEach { path ->
                assertTrue("https://$host$path", matches("https://$host$path"))
            }
            listOf("/", "/api/requests", "/submit/api/my", "/submit/digest/confirm", "/submit/digest/unsubscribe", "/submit/account", "/submit/admin", "/submit/", "/submit/login/extra", "/submit/my/extra").forEach { path ->
                assertFalse("https://$host$path", matches("https://$host$path"))
            }
        }
        assertFalse(matches("https://evil.test/submit/login"))
        assertFalse(matches("https://alex311-reborn.com.evil.test/submit/login"))
    }

    @Test fun websiteSignInHandoffSchemeMatches() {
        assertTrue(matches("com.herbertindustries.alex311-reborn://signin?mode=signIn&oobCode=A%2BB%2FC%3D"))
    }
}
