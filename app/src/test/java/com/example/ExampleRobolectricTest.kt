package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.FirebaseUpdateAndNotificationsRepository
import com.example.model.AppState
import com.example.model.GradeLevel
import com.example.model.SectionLetter
import com.example.model.Student
import com.example.model.StudentGrades
import com.example.model.TeacherProfile
import com.example.ui.screens.InactivityDuaDialog
import com.example.ui.screens.InternalUpdateMarqueeStrip
import com.example.ui.theme.TeacherLedgerTheme
import com.example.utils.BackupCryptoHelper
import com.example.utils.ExportManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("رفيق المدرس - أدوات شاملة", appName)
  }

  @Test
  fun `aes 256 gcm encrypted backup round trip and password verification`() {
    val salt = BackupCryptoHelper.generateSaltHex()
    val password = "StrongTeacherPassword#2026"
    val hash = BackupCryptoHelper.hashPassword(password, salt)
    assertTrue(BackupCryptoHelper.verifyPassword(password, salt, hash))
    assertFalse(BackupCryptoHelper.verifyPassword("WrongPassword", salt, hash))

    val payload = """{"teacherName":"أ. مصطفى عيسى","students":[{"name":"علي حسن","score":95}]}"""
    val (encryptedBlob, checksum) = BackupCryptoHelper.encryptAndCompress(payload, password, salt)
    assertTrue(encryptedBlob.isNotBlank())
    assertEquals(64, checksum.length)

    val decrypted = BackupCryptoHelper.decryptAndDecompress(encryptedBlob, password, salt, checksum)
    assertNotNull(decrypted)
    assertEquals(payload, decrypted)

    val wrongPass = BackupCryptoHelper.decryptAndDecompress(encryptedBlob, "WrongPassword", salt, checksum)
    assertNull(wrongPass)

    val badChecksum = BackupCryptoHelper.decryptAndDecompress(encryptedBlob, password, salt, "0".repeat(64))
    assertNull(badChecksum)
  }

  @Test
  fun `verify stealth admin pin Mustfa and folder excel word exports`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(context)
        ?: com.google.firebase.FirebaseApp.initializeApp(
          context,
          com.google.firebase.FirebaseOptions.Builder()
            .setProjectId("demo-no-project")
            .setApplicationId("1:1234567890:android:abcdef")
            .setApiKey("AIzaTestKeyForRobolectric123456789")
            .build()
        )
    }
    val repo = FirebaseUpdateAndNotificationsRepository(context)
    assertTrue(repo.verifyAdminPin("Mustfa"))
    assertTrue(repo.verifyAdminPin("mustfa"))
    assertFalse(repo.verifyAdminPin("WrongPin123"))

    val state = AppState(
      profile = TeacherProfile(teacherName = "أ. مصطفى", schoolName = "مدرستي"),
      students = listOf(
        Student(
          id = "st_robo_1",
          orderNumber = 1,
          fullName = "حسين علي",
          gradeLevel = GradeLevel.THIRD,
          section = SectionLetter.A,
          grades = StudentGrades(term1Month1 = 80.0, term1Month2 = 85.0, midYearTotal = 84.0)
        )
      )
    )
    val excelRes = ExportManager.exportFullGradesAndAnalyticsToExcel(context, state, GradeLevel.THIRD, SectionLetter.A, "general")
    assertTrue(excelRes.success)
    assertNotNull(excelRes.file)
    assertTrue(excelRes.file!!.exists() && excelRes.file!!.length() > 100L)

    val wordRes = ExportManager.exportFullGradesAndAnalyticsToWord(context, state, GradeLevel.THIRD, SectionLetter.A, "general")
    assertTrue(wordRes.success)
    assertNotNull(wordRes.file)
    assertTrue(wordRes.file!!.exists() && wordRes.file!!.length() > 100L)
  }

  @Test
  fun `internal update marquee strip and inactivity dua dialog render cleanly`() {
    composeTestRule.setContent {
      TeacherLedgerTheme(darkTheme = false) {
        InternalUpdateMarqueeStrip(
          visible = true,
          text = "تم تحديث المحتوى الداخلي بنجاح",
          onDismiss = {}
        )
        InactivityDuaDialog(
          message = "بارك الله في وقتك وجهدك يا أستاذنا الفاضل",
          teacherName = "مصطفى",
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("internal_update_marquee_strip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inactivity_dua_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("بارك الله في وقتك وجهدك يا أستاذنا الفاضل").assertIsDisplayed()
  }

  @Test
  fun `link only update dialog and 4-target color wheel render cleanly`() {
    val release = com.example.data.AppReleaseInfo(
      latestVersionCode = 99,
      latestVersionName = "تحديث المزايا الجديدة",
      releaseNotes = "تحسينات شاملة وإضافة عجلة الألوان",
      forceUpdate = false,
      apkUrl = "https://example.com/app-update"
    )
    composeTestRule.setContent {
      TeacherLedgerTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Column {
          com.example.ui.screens.AppUpdateDialog(
            updateState = com.example.data.UpdateCheckResult.UpdateAvailable(release, 1, "1.0"),
            downloadState = com.example.utils.ApkDownloadState.Idle,
            currentUser = null,
            onStartDownload = {},
            onRetryCheck = {},
            onResetDownload = {},
            onDismiss = {}
          )
          com.example.ui.components.ColorWheelCustomizerSection(
            customColorsEnabled = false,
            customTextColorHex = 0L,
            customButtonColorHex = 0L,
            customBgColorHex = 0L,
            customCardAccentColorHex = 0L,
            onApplyColors = { _, _, _, _, _ -> }
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("تحديث المزايا الجديدة").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_view_link_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("color_wheel_target_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("color_wheel_target_buttons").assertIsDisplayed()
    composeTestRule.onNodeWithTag("color_wheel_target_background").assertIsDisplayed()
    composeTestRule.onNodeWithTag("color_wheel_target_cards_data").assertIsDisplayed()
  }
}

