package com.kotlinorm.example.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kotlinorm.Kronos
import com.kotlinorm.orm.ddl.table
import com.kotlinorm.utils.GeneratedTypeProvider
import java.util.ServiceLoader
import kotlin.reflect.typeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKronosIntegrationTest {
    @Test
    fun generatedProviderSupportsAndroidCrudWithoutManualRegistration() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as KronosExampleApp
        val providers = ServiceLoader.load(GeneratedTypeProvider::class.java).toList()

        assertTrue(providers.any { it.id.startsWith("gradle:") })
        assertEquals(
            MarkdownDocument::class,
            Kronos.createKPojo(typeOf<MarkdownDocument>())::class
        )

        val repository = DocumentRepository()
        val created = repository.create("provider-${System.nanoTime()}", "generated factory", false)
        assertNotNull(created.id)
        assertEquals(created.id, repository.find(requireNotNull(created.id))?.id)

        val rollbackTitle = "rollback-${System.nanoTime()}"
        runCatching {
            Kronos.transact {
                repository.create(rollbackTitle, "rollback", false)
                error("force rollback")
            }
        }
        assertNull(repository.all().firstOrNull { it.title == rollbackTitle })

        application.database.table.dropTable(MarkdownDocument())
        application.database.table.syncTable(MarkdownDocument())
    }
}
