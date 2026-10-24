package com.herbertindustries.alex311

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.ui.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Exercises real Android Keystore/draft storage against a local server; never files a City request. */
@RunWith(AndroidJUnit4::class)
class ReportWorkflowTest {
    private lateinit var app: Alex311Application
    private lateinit var server: MockWebServer
    private lateinit var api: Api
    private lateinit var scope: CoroutineScope
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var allow = true
    private var validationDelay = false
    @Before fun setup() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Alex311Application
        app.secure.clearAccount()
        server = MockWebServer()
        server.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val response = when (request.requestUrl?.encodedPath) {
                    "/submit/api/registry/full" -> """{"version":"test","schema":1,"services":[{"service_code":"TEST","service_name":"Test service","questions":[]}]}"""
                    "/submit/api/nearby" -> """{"nearby":[],"open_count":0}"""
                    "/submit/api/validate" -> """{"ok":true,"errors":[],"blocking":[],"advisories":[],"info":[]}"""
                    "/submit/api/precheck" -> """{"attempt_id":7,"outcome":"${if (allow) "allow" else "block"}","may_proceed":$allow,"findings":[]}"""
                    "/submit/api/queue" -> """{"attempt_id":7,"submit_state":"queued"}"""
                    "/submit/api/status/7" -> """{"attempt_id":7,"submit_state":"filed","city_case_number":"26-00000007"}"""
                    else -> "{}"
                }
                return MockResponse().setBody(response).setHeader("Content-Type", "application/json").apply {
                    if (validationDelay && request.requestUrl?.encodedPath == "/submit/api/validate") setBodyDelay(250, TimeUnit.MILLISECONDS)
                }
            }
        }
        server.start()
        api = Api(server.url("/").toString()).also { it.sessionToken = "test-session" }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    @After fun cleanup() {
        scope.cancel(); server.shutdown(); app.secure.clearAccount()
        app.filesDir.listFiles()?.filter { it.name.startsWith("report-test-") }?.forEach { it.delete() }
    }
    private suspend fun prepared(): ReportState {
        val state = ReportState(app, scope, api)
        state.load().join(); state.choose(state.services.single()); state.pick(Candidate("1 KING ST", 38.8048, -77.0469)); state.step(3)
        state.contact = Contact("Test", "Resident", "test@example.test", "5550100")
        return state
    }
    @Test fun uploadThenQueueAndResumeWithoutAnotherSubmission() = runBlocking {
        withContext(Dispatchers.Main) {
            val state = prepared()
            val name = "report-test-photo.jpg"
            File(app.filesDir, name).writeBytes(byteArrayOf(-1, -40, -1))
            state.edit { it.copy(photos = listOf(name)) }
            state.check().join(); assertTrue(state.canSend)
            state.send().join()
            withTimeout(5000) { while (state.attempt?.isFinal != true) delay(20) }
            val paths = requests.map { it.requestUrl!!.encodedPath }
            assertTrue(paths.indexOf("/submit/api/validate") < paths.indexOf("/submit/api/precheck"))
            assertTrue(paths.indexOf("/submit/api/attempt/7/photos") < paths.indexOf("/submit/api/queue"))
            val queue = requests.single { it.requestUrl!!.encodedPath == "/submit/api/queue" }
            val json = WireJson.parseToJsonElement(queue.body.readUtf8()).toString()
            assertTrue(json.contains("\"attempt_id\":7")); assertTrue(json.contains("\"first_name\":\"Test\""))
            assertNull(app.secure.read("contact"))
            val restored = ReportState(app, scope, api)
            assertEquals(7, restored.draft.pendingAttempt)
            withTimeout(5000) { while (restored.attempt?.isFinal != true) delay(20) }
            assertEquals("26-00000007", restored.attempt!!.city_case_number)
            assertEquals(1, requests.count { it.requestUrl!!.encodedPath == "/submit/api/queue" })
        }
    }
    @Test fun blockedReportNeverQueues() = runBlocking {
        withContext(Dispatchers.Main) {
            allow = false
            val state = prepared(); state.check().join(); assertFalse(state.canSend)
            state.send().join()
            assertNotNull(state.error)
            assertFalse(requests.any { it.requestUrl!!.encodedPath == "/submit/api/queue" })
        }
    }
    @Test fun editingDuringValidationDiscardsTheOldCheck() = runBlocking {
        withContext(Dispatchers.Main) {
            validationDelay = true
            val state = prepared(); val job = state.check()
            withTimeout(5000) { while (requests.none { it.requestUrl!!.encodedPath == "/submit/api/validate" }) delay(10) }
            state.edit { it.copy(description = "Changed after check started") }
            job.join()
            assertNull(state.precheck); assertFalse(state.canSend)
            assertFalse(requests.any { it.requestUrl!!.encodedPath == "/submit/api/precheck" })
        }
    }
    @Test fun photoPreparationAppliesOrientationScalesAndRemovesMetadata() = runBlocking {
        val file = File(app.filesDir, "report-test-large.jpg")
        val image = android.graphics.Bitmap.createBitmap(4032, 3024, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        image.recycle()
        androidx.exifinterface.media.ExifInterface(file).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, "38/1,48/1,0/1")
            saveAttributes()
        }
        val jpeg = Photos.prepare(app, android.net.Uri.fromFile(file))
        val output = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(1920, output.width); assertEquals(2560, output.height)
        output.recycle()
        val exif = androidx.exifinterface.media.ExifInterface(jpeg.inputStream())
        assertNull(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(0xff.toByte(), jpeg[0]); assertEquals(0xd8.toByte(), jpeg[1])
    }
    @Test fun cameraCaptureThroughFileProviderJoinsDraftAndIsDeleted() = runBlocking {
        withContext(Dispatchers.Main) {
            val state = ReportState(app, scope, api)
            val target = state.captureUri()
            assertEquals("content", target.scheme)
            val image = android.graphics.Bitmap.createBitmap(640, 480, android.graphics.Bitmap.Config.ARGB_8888)
            app.contentResolver.openOutputStream(target)!!.use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            image.recycle()
            state.addCapture().join()
            assertNull(state.error)
            val name = state.draft.photos.single()
            assertTrue(state.photoFile(name).length() > 0)
            assertFalse(File(File(app.cacheDir, "camera"), "capture.jpg").exists())
            state.reset()
            assertFalse(state.photoFile(name).exists())
        }
    }
    @Test fun keystoreRoundTripAndSignOutClearPrivateData() {
        app.secure.write("contact", "private-contact")
        assertEquals("private-contact", app.secure.read("contact"))
        val persisted = app.getSharedPreferences("secure", 0).getString("contact", "").orEmpty()
        assertFalse(persisted.contains("private-contact"))
        app.session.forget()
        assertNull(app.secure.read("contact")); assertNull(app.secure.read("session"))
    }
}
