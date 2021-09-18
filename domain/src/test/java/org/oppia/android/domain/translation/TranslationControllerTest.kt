package org.oppia.android.domain.translation

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.android.app.model.AppLanguageSelection
import org.oppia.android.app.model.AudioTranslationLanguageSelection
import org.oppia.android.app.model.LanguageSupportDefinition.LanguageId.LanguageTypeCase.IETF_BCP47_ID
import org.oppia.android.app.model.OppiaLanguage
import org.oppia.android.app.model.OppiaLanguage.BRAZILIAN_PORTUGUESE
import org.oppia.android.app.model.OppiaLanguage.ENGLISH
import org.oppia.android.app.model.OppiaLanguage.HINDI
import org.oppia.android.app.model.OppiaLanguage.LANGUAGE_UNSPECIFIED
import org.oppia.android.app.model.OppiaLocaleContext.LanguageUsageMode.APP_STRINGS
import org.oppia.android.app.model.OppiaLocaleContext.LanguageUsageMode.AUDIO_TRANSLATIONS
import org.oppia.android.app.model.OppiaLocaleContext.LanguageUsageMode.CONTENT_STRINGS
import org.oppia.android.app.model.OppiaRegion.BRAZIL
import org.oppia.android.app.model.OppiaRegion.REGION_UNSPECIFIED
import org.oppia.android.app.model.OppiaRegion.UNITED_STATES
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.SubtitledHtml
import org.oppia.android.app.model.SubtitledUnicode
import org.oppia.android.app.model.Translation
import org.oppia.android.app.model.WrittenTranslationContext
import org.oppia.android.app.model.WrittenTranslationLanguageSelection
import org.oppia.android.domain.locale.LocaleController
import org.oppia.android.domain.oppialogger.LogStorageModule
import org.oppia.android.testing.TestLogReportingModule
import org.oppia.android.testing.data.DataProviderTestMonitor
import org.oppia.android.testing.robolectric.RobolectricModule
import org.oppia.android.testing.threading.TestDispatcherModule
import org.oppia.android.testing.time.FakeOppiaClockModule
import org.oppia.android.util.data.DataProvidersInjector
import org.oppia.android.util.data.DataProvidersInjectorProvider
import org.oppia.android.util.locale.MachineLocaleModule
import org.oppia.android.util.locale.OppiaLocale
import org.oppia.android.util.logging.LoggerModule
import org.oppia.android.util.networking.NetworkConnectionUtilDebugModule
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Tests for [TranslationController]. */
// FunctionName: test names are conventionally named with underscores.
// SameParameterValue: tests should have specific context included/excluded for readability.
@Suppress("FunctionName", "SameParameterValue")
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = TranslationControllerTest.TestApplication::class)
class TranslationControllerTest {
  @Inject
  lateinit var translationController: TranslationController

  @Inject
  lateinit var localeController: LocaleController

  @Inject
  lateinit var monitorFactory: DataProviderTestMonitor.Factory

  @Inject
  lateinit var context: Context

  @Before
  fun setUp() {
    setUpTestApplicationComponent()
  }

  /* Tests for getSystemLanguageLocale */

  @Test
  fun testGetSystemLanguageLocale_rootLocale_returnsLocaleWithBlankContext() {
    forceDefaultLocale(Locale.ROOT)

    val localeProvider = translationController.getSystemLanguageLocale()

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    val appStringId = context.languageDefinition.appStringId
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(LANGUAGE_UNSPECIFIED)
    assertThat(appStringId.languageTypeCase).isEqualTo(IETF_BCP47_ID)
    assertThat(appStringId.ietfBcp47Id.ietfLanguageTag).isEmpty()
    assertThat(appStringId.androidResourcesLanguageId.languageCode).isEmpty()
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  @Test
  fun testGetSystemLanguageLocale_usLocale_returnsLocaleWithEnglishContext() {
    forceDefaultLocale(Locale.US)

    val localeProvider = translationController.getSystemLanguageLocale()

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    assertThat(context.regionDefinition.region).isEqualTo(UNITED_STATES)
  }

  @Test
  fun testGetSystemLanguageLocale_updateLocaleToIndia_updatesProviderWithNewLocale() {
    forceDefaultLocale(Locale.US)
    val localeProvider = translationController.getSystemLanguageLocale()
    val monitor = monitorFactory.createMonitor(localeProvider)
    monitor.waitForNextSuccessResult()

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())

    // Verify that the provider has changed.
    val locale = monitor.waitForNextSuccessResult()
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(HINDI)
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  /* Tests for app language functions */

  @Test
  fun testUpdateAppLanguage_returnsSuccess() {
    forceDefaultLocale(Locale.ROOT)

    val resultProvider =
      translationController.updateAppLanguage(PROFILE_ID_0, createAppLanguageSelection(ENGLISH))

    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  @Test
  fun testUpdateAppLanguage_notifiesProviderWithChange() {
    forceDefaultLocale(Locale.ROOT)
    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)
    val langMonitor = monitorFactory.createMonitor(languageProvider)
    langMonitor.waitForNextSuccessResult()

    // The result must be observed immediately otherwise it won't execute (which will result in the
    // language not being updated).
    val resultProvider =
      translationController.updateAppLanguage(PROFILE_ID_0, createAppLanguageSelection(ENGLISH))
    val updateMonitor = monitorFactory.createMonitor(resultProvider)

    updateMonitor.waitForNextSuccessResult()
    langMonitor.ensureNextResultIsSuccess()
  }

  @Test
  fun testGetAppLanguage_uninitialized_returnsSystemLanguage() {
    forceDefaultLocale(Locale.ROOT)

    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(LANGUAGE_UNSPECIFIED)
  }

  @Test
  fun testGetAppLanguage_updateLanguageToEnglish_returnsEnglish() {
    forceDefaultLocale(Locale.ROOT)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAppLanguage_updateLanguageToHindi_returnsHindi() {
    forceDefaultLocale(Locale.ROOT)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(HINDI)
  }

  @Test
  fun testGetAppLanguage_updateLanguageToUseSystem_returnsSystemLanguage() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)

    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAppLanguage_useSystemLang_updateLocale_notifiesProviderWithNewLanguage() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    val languageProvider = translationController.getAppLanguage(PROFILE_ID_0)
    val monitor = monitorFactory.createMonitor(languageProvider)
    monitor.waitForNextSuccessResult()

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())

    // The language should be updated to English since the system language was changed.
    val updatedLanguage = monitor.waitForNextSuccessResult()
    assertThat(updatedLanguage).isEqualTo(HINDI)
  }

  @Test
  fun testGetAppLanguage_updateLanguageToEnglish_differentProfile_returnsDifferentLang() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getAppLanguage(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAppLanguageLocale_uninitialized_returnsLocaleWithSystemLanguage() {
    forceDefaultLocale(Locale.ROOT)

    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    val appStringId = context.languageDefinition.appStringId
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(LANGUAGE_UNSPECIFIED)
    assertThat(appStringId.languageTypeCase).isEqualTo(IETF_BCP47_ID)
    assertThat(appStringId.ietfBcp47Id.ietfLanguageTag).isEmpty()
    assertThat(appStringId.androidResourcesLanguageId.languageCode).isEmpty()
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  @Test
  fun testGetAppLanguageLocale_updateLanguageToEnglish_returnsEnglishLocale() {
    forceDefaultLocale(Locale.ROOT)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  @Test
  fun testGetAppLanguageLocale_updateLanguageToPortuguese_returnsPortugueseLocale() {
    forceDefaultLocale(BRAZIL_ENGLISH_LOCALE)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)

    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(BRAZILIAN_PORTUGUESE)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(BRAZIL)
  }

  @Test
  fun testGetAppLanguageLocale_updateLanguageToUseSystem_returnsSystemLanguageLocale() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)

    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAppLanguageLocale_useSystemLang_updateLocale_notifiesProviderWithNewLocale() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_0)
    val monitor = monitorFactory.createMonitor(localeProvider)
    monitor.waitForNextSuccessResult()

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())

    // The language should be updated to English since the system language was changed.
    val updateLocale = monitor.waitForNextSuccessResult()
    val context = updateLocale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(HINDI)
  }

  @Test
  fun testGetAppLanguageLocale_updateLangToEnglish_differentProfile_returnsDifferentLocale() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val localeProvider = translationController.getAppLanguageLocale(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(APP_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }

  /* Tests for written translation content functions */

  @Test
  fun testUpdateWrittenContentLanguage_returnsSuccess() {
    forceDefaultLocale(Locale.ROOT)

    val resultProvider =
      translationController.updateWrittenTranslationContentLanguage(
        PROFILE_ID_0, createWrittenTranslationLanguageSelection(ENGLISH)
      )

    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  @Test
  fun testUpdateWrittenContentLanguage_notifiesProviderWithChange() {
    forceDefaultLocale(Locale.US)
    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)
    val langMonitor = monitorFactory.createMonitor(languageProvider)
    langMonitor.waitForNextSuccessResult()

    // The result must be observed immediately otherwise it won't execute (which will result in the
    // language not being updated).
    val resultProvider =
      translationController.updateWrittenTranslationContentLanguage(
        PROFILE_ID_0, createWrittenTranslationLanguageSelection(BRAZILIAN_PORTUGUESE)
      )
    val updateMonitor = monitorFactory.createMonitor(resultProvider)

    updateMonitor.waitForNextSuccessResult()
    langMonitor.ensureNextResultIsSuccess()
  }

  @Test
  fun testGetWrittenContentLang_uninitialized_rootLocale_returnsFailure() {
    forceDefaultLocale(Locale.ROOT)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    val error = monitorFactory.waitForNextFailureResult(languageProvider)
    assertThat(error).hasMessageThat().contains("doesn't match supported language definitions")
  }

  @Test
  fun testGetWrittenContentLang_uninitialized_englishLocale_returnsSystemLanguage() {
    forceDefaultLocale(Locale.US)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetWrittenContentLang_updateLanguageToEnglish_returnsEnglish() {
    forceDefaultLocale(Locale.ROOT)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetWrittenContentLang_updateLanguageToHindi_returnsHindi() {
    forceDefaultLocale(Locale.ROOT)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(HINDI)
  }

  @Test
  fun testGetWrittenContentLang_updateLanguageToUseApp_returnsAppLanguage() {
    // First, initialize the language to Hindi before overwriting to use the app language.
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetWrittenContentLang_useAppLang_updateAppLanguage_notifiesProviderWithNewLang() {
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)
    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    // Changing the app language should change the provided language since this provider depends on
    // the app strings language.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(BRAZILIAN_PORTUGUESE)
  }

  @Test
  fun testGetWrittenContentLang_useSystemLangForApp_updateLocale_notifiesProviderWithNewLang() {
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())
    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_0)

    // Changing the locale should change the language since this provider depends on the app strings
    // language & app strings depend on the system locale.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(HINDI)
  }

  @Test
  fun testGetWrittenContentLang_updateLanguageToEnglish_differentProfile_returnsDifferentLang() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getWrittenTranslationContentLanguage(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetWrittenContentLocale_uninitialized_rootLocale_returnsFailure() {
    forceDefaultLocale(Locale.ROOT)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    val error = monitorFactory.waitForNextFailureResult(localeProvider)
    assertThat(error).hasMessageThat().contains("doesn't match supported language definitions")
  }

  @Test
  fun testGetWrittenContentLocale_uninitialized_englishLocale_returnsLocaleWithSystemLanguage() {
    forceDefaultLocale(Locale.US)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    assertThat(context.regionDefinition.region).isEqualTo(UNITED_STATES)
  }

  @Test
  fun testGetWrittenContentLocale_updateLanguageToEnglish_returnsEnglishLocale() {
    forceDefaultLocale(Locale.ROOT)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  @Test
  fun testGetWrittenContentLocale_updateLanguageToPortuguese_returnsPortugueseLocale() {
    forceDefaultLocale(BRAZIL_ENGLISH_LOCALE)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(BRAZILIAN_PORTUGUESE)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(BRAZIL)
  }

  @Test
  fun testGetWrittenContentLocale_updateLanguageToUseApp_returnsAppLanguage() {
    // First, initialize the language to Hindi before overwriting to use the app language.
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetWrittenContentLocale_useAppLang_updateAppLanguage_notifiesProviderWithNewLang() {
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)
    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    // Changing the app language should change the provided language since this provider depends on
    // the app strings language.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(BRAZILIAN_PORTUGUESE)
  }

  @Test
  fun testGetWrittenContentLocale_useSystemLangForApp_updateLocale_notifiesProviderWithNewLang() {
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureWrittenTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())
    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_0)

    // Changing the locale should change the language since this provider depends on the app strings
    // language & app strings depend on the system locale.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(HINDI)
  }

  @Test
  fun testGetWrittenContentLocale_updateLangToEnglish_differentProfile_returnsDifferentLocale() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureWrittenTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val localeProvider = translationController.getWrittenTranslationContentLocale(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(CONTENT_STRINGS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }

  /* Tests for audio translation content functions */

  @Test
  fun testUpdateAudioLanguage_returnsSuccess() {
    forceDefaultLocale(Locale.ROOT)

    val resultProvider =
      translationController.updateAudioTranslationContentLanguage(
        PROFILE_ID_0, createAudioTranslationLanguageSelection(ENGLISH)
      )

    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  @Test
  fun testUpdateAudioLanguage_notifiesProviderWithChange() {
    forceDefaultLocale(Locale.US)
    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)
    val langMonitor = monitorFactory.createMonitor(languageProvider)
    langMonitor.waitForNextSuccessResult()

    // The result must be observed immediately otherwise it won't execute (which will result in the
    // language not being updated).
    val resultProvider =
      translationController.updateAudioTranslationContentLanguage(
        PROFILE_ID_0, createAudioTranslationLanguageSelection(BRAZILIAN_PORTUGUESE)
      )
    val updateMonitor = monitorFactory.createMonitor(resultProvider)

    updateMonitor.waitForNextSuccessResult()
    langMonitor.ensureNextResultIsSuccess()
  }

  @Test
  fun testGetAudioLanguage_uninitialized_rootLocale_returnsFailure() {
    forceDefaultLocale(Locale.ROOT)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    val error = monitorFactory.waitForNextFailureResult(languageProvider)
    assertThat(error).hasMessageThat().contains("doesn't match supported language definitions")
  }

  @Test
  fun testGetAudioLanguage_uninitialized_englishLocale_returnsSystemLanguage() {
    forceDefaultLocale(Locale.US)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAudioLanguage_updateLanguageToEnglish_returnsEnglish() {
    forceDefaultLocale(Locale.ROOT)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAudioLanguage_updateLanguageToHindi_returnsHindi() {
    forceDefaultLocale(Locale.ROOT)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(HINDI)
  }

  @Test
  fun testGetAudioLanguage_updateLanguageToUseApp_returnsAppLanguage() {
    // First, initialize the language to Hindi before overwriting to use the app language.
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAudioLanguage_useAppLang_updateAppLanguage_notifiesProviderWithNewLang() {
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)
    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    // Changing the app language should change the provided language since this provider depends on
    // the app strings language.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(BRAZILIAN_PORTUGUESE)
  }

  @Test
  fun testGetAudioLanguage_useSystemLangForApp_updateLocale_notifiesProviderWithNewLang() {
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())
    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_0)

    // Changing the locale should change the language since this provider depends on the app strings
    // language & app strings depend on the system locale.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(HINDI)
  }

  @Test
  fun testGetAudioLanguage_updateLanguageToEnglish_differentProfile_returnsDifferentLang() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val languageProvider = translationController.getAudioTranslationContentLanguage(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val language = monitorFactory.waitForNextSuccessfulResult(languageProvider)
    assertThat(language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAudioLocale_uninitialized_rootLocale_returnsFailure() {
    forceDefaultLocale(Locale.ROOT)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    val error = monitorFactory.waitForNextFailureResult(localeProvider)
    assertThat(error).hasMessageThat().contains("doesn't match supported language definitions")
  }

  @Test
  fun testGetAudioLocale_uninitialized_englishLocale_returnsLocaleWithSystemLanguage() {
    forceDefaultLocale(Locale.US)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    assertThat(context.regionDefinition.region).isEqualTo(UNITED_STATES)
  }

  @Test
  fun testGetAudioLocale_updateLanguageToEnglish_returnsEnglishLocale() {
    forceDefaultLocale(Locale.ROOT)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, ENGLISH)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(REGION_UNSPECIFIED)
  }

  @Test
  fun testGetAudioLocale_updateLanguageToPortuguese_returnsPortugueseLocale() {
    forceDefaultLocale(BRAZIL_ENGLISH_LOCALE)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(BRAZILIAN_PORTUGUESE)
    // This region comes from the default locale.
    assertThat(context.regionDefinition.region).isEqualTo(BRAZIL)
  }

  @Test
  fun testGetAudioLocale_updateLanguageToUseApp_returnsAppLanguage() {
    // First, initialize the language to Hindi before overwriting to use the app language.
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }

  @Test
  fun testGetAudioLocale_useAppLang_updateAppLanguage_notifiesProviderWithNewLang() {
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    ensureAppLanguageIsUpdatedTo(PROFILE_ID_0, BRAZILIAN_PORTUGUESE)
    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    // Changing the app language should change the provided language since this provider depends on
    // the app strings language.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(BRAZILIAN_PORTUGUESE)
  }

  @Test
  fun testGetAudioLocale_useSystemLangForApp_updateLocale_notifiesProviderWithNewLang() {
    ensureAppLanguageIsUpdatedToUseSystem(PROFILE_ID_0)
    forceDefaultLocale(Locale.US)
    ensureAudioTranslationsLanguageIsUpdatedToUseApp(PROFILE_ID_0)

    localeController.setAsDefault(createDisplayLocaleForLanguage(HINDI), Configuration())
    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_0)

    // Changing the locale should change the language since this provider depends on the app strings
    // language & app strings depend on the system locale.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(HINDI)
  }

  @Test
  fun testGetAudioLocale_updateLangToEnglish_differentProfile_returnsDifferentLocale() {
    forceDefaultLocale(Locale.ENGLISH)
    ensureAudioTranslationsLanguageIsUpdatedTo(PROFILE_ID_0, HINDI)

    val localeProvider = translationController.getAudioTranslationContentLocale(PROFILE_ID_1)

    // English is returned since the language is being fetched for a different profile.
    val locale = monitorFactory.waitForNextSuccessfulResult(localeProvider)
    val context = locale.localeContext
    assertThat(context.usageMode).isEqualTo(AUDIO_TRANSLATIONS)
    assertThat(context.languageDefinition.language).isEqualTo(ENGLISH)
  }
  
  /* Tests for string extraction functions */

  @Test
  fun testExtractString_defaultSubtitledHtml_defaultContext_returnsEmptyString() {
    val extracted =
      translationController.extractString(
        SubtitledHtml.getDefaultInstance(), WrittenTranslationContext.getDefaultInstance()
      )

    assertThat(extracted).isEmpty()
  }

  @Test
  fun testExtractString_defaultSubtitledHtml_validContext_returnsEmptyString() {
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("other_content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted = translationController.extractString(SubtitledHtml.getDefaultInstance(), context)

    assertThat(extracted).isEmpty()
  }

  @Test
  fun testExtractString_subtitledHtml_defaultContext_returnsUntranslatedHtml() {
    val subtitledHtml = SubtitledHtml.newBuilder().apply {
      contentId = "content_id"
      html = "default html"
    }.build()

    val extracted =
      translationController.extractString(
        subtitledHtml, WrittenTranslationContext.getDefaultInstance()
      )

    assertThat(extracted).isEqualTo("default html")
  }

  @Test
  fun testExtractString_subtitledHtml_validContext_missingContentId_returnsUntranslatedHtml() {
    val subtitledHtml = SubtitledHtml.newBuilder().apply {
      contentId = "content_id"
      html = "default html"
    }.build()
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("other_content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted = translationController.extractString(subtitledHtml, context)

    // The content ID doesn't match in the context.
    assertThat(extracted).isEqualTo("default html")
  }

  @Test
  fun testExtractString_subtitledHtml_validContext_includesContentId_returnsTranslatedHtml() {
    val subtitledHtml = SubtitledHtml.newBuilder().apply {
      contentId = "content_id"
      html = "default html"
    }.build()
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted = translationController.extractString(subtitledHtml, context)

    // The context ID does match, so the matching string is extracted.
    assertThat(extracted).isEqualTo("Translated string")
  }

  @Test
  fun testExtractString_defaultSubtitledUnicode_defaultContext_returnsEmptyString() {
    val extracted =
      translationController.extractString(
        SubtitledUnicode.getDefaultInstance(), WrittenTranslationContext.getDefaultInstance()
      )

    assertThat(extracted).isEmpty()
  }

  @Test
  fun testExtractString_defaultSubtitledUnicode_validContext_returnsEmptyString() {
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("other_content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted =
      translationController.extractString(SubtitledUnicode.getDefaultInstance(), context)

    assertThat(extracted).isEmpty()
  }

  @Test
  fun testExtractString_subtitledUnicode_defaultContext_returnsUntranslatedUnicode() {
    val subtitledUnicode = SubtitledUnicode.newBuilder().apply {
      contentId = "content_id"
      unicodeStr = "default str"
    }.build()

    val extracted =
      translationController.extractString(
        subtitledUnicode, WrittenTranslationContext.getDefaultInstance()
      )

    assertThat(extracted).isEqualTo("default str")
  }

  @Test
  fun testExtractString_subtitledUnicode_validContext_missingContentId_returnsUnxlatedUnicode() {
    val subtitledUnicode = SubtitledUnicode.newBuilder().apply {
      contentId = "content_id"
      unicodeStr = "default str"
    }.build()
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("other_content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted = translationController.extractString(subtitledUnicode, context)

    // The content ID doesn't match in the context.
    assertThat(extracted).isEqualTo("default str")
  }

  @Test
  fun testExtractString_subtitledUnicode_validContext_includesContentId_returnsTranslatedUnicode() {
    val subtitledUnicode = SubtitledUnicode.newBuilder().apply {
      contentId = "content_id"
      unicodeStr = "default str"
    }.build()
    val context = WrittenTranslationContext.newBuilder().apply {
      putTranslations("content_id", Translation.newBuilder().apply {
        html = "Translated string"
      }.build())
    }.build()

    val extracted = translationController.extractString(subtitledUnicode, context)

    // The context ID does match, so the matching string is extracted.
    assertThat(extracted).isEqualTo("Translated string")
  }

  private fun setUpTestApplicationComponent() {
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  private fun forceDefaultLocale(locale: Locale) {
    context.applicationContext.resources.configuration.setLocale(locale)
    Locale.setDefault(locale)
  }

  private fun createDisplayLocaleForLanguage(language: OppiaLanguage): OppiaLocale.DisplayLocale {
    val localeProvider = localeController.retrieveAppStringDisplayLocale(language)
    return monitorFactory.waitForNextSuccessfulResult(localeProvider)
  }

  private fun ensureAppLanguageIsUpdatedToUseSystem(profileId: ProfileId) {
    val resultProvider =
      translationController.updateAppLanguage(profileId, APP_LANGUAGE_SELECTION_SYSTEM)
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun ensureAppLanguageIsUpdatedTo(profileId: ProfileId, language: OppiaLanguage) {
    val resultProvider =
      translationController.updateAppLanguage(profileId, createAppLanguageSelection(language))
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun createAppLanguageSelection(language: OppiaLanguage): AppLanguageSelection {
    return AppLanguageSelection.newBuilder().apply {
      selectedLanguage = language
    }.build()
  }

  private fun ensureWrittenTranslationsLanguageIsUpdatedTo(
    profileId: ProfileId, language: OppiaLanguage
  ) {
    val resultProvider =
      translationController.updateWrittenTranslationContentLanguage(
        profileId, createWrittenTranslationLanguageSelection(language)
      )
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun ensureWrittenTranslationsLanguageIsUpdatedToUseApp(profileId: ProfileId) {
    val resultProvider =
      translationController.updateWrittenTranslationContentLanguage(
        profileId, WRITTEN_TRANSLATION_LANGUAGE_SELECTION_APP_LANGUAGE
      )
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun createWrittenTranslationLanguageSelection(
    language: OppiaLanguage
  ): WrittenTranslationLanguageSelection {
    return WrittenTranslationLanguageSelection.newBuilder().apply {
      selectedLanguage = language
    }.build()
  }

  private fun ensureAudioTranslationsLanguageIsUpdatedTo(
    profileId: ProfileId, language: OppiaLanguage
  ) {
    val resultProvider =
      translationController.updateAudioTranslationContentLanguage(
        profileId, createAudioTranslationLanguageSelection(language)
      )
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun ensureAudioTranslationsLanguageIsUpdatedToUseApp(profileId: ProfileId) {
    val resultProvider =
      translationController.updateAudioTranslationContentLanguage(
        profileId, AUDIO_TRANSLATION_LANGUAGE_SELECTION_APP_LANGUAGE
      )
    monitorFactory.waitForNextSuccessfulResult(resultProvider)
  }

  private fun createAudioTranslationLanguageSelection(
    language: OppiaLanguage
  ): AudioTranslationLanguageSelection {
    return AudioTranslationLanguageSelection.newBuilder().apply {
      selectedLanguage = language
    }.build()
  }

  // TODO(#89): Move this to a common test application component.
  @Module
  class TestModule {
    @Provides
    @Singleton
    fun provideContext(application: Application): Context {
      return application
    }
  }

  // TODO(#89): Move this to a common test application component.
  @Singleton
  @Component(
    modules = [
      TestModule::class, LogStorageModule::class, NetworkConnectionUtilDebugModule::class,
      TestLogReportingModule::class, LoggerModule::class, TestDispatcherModule::class,
      MachineLocaleModule::class, FakeOppiaClockModule::class, RobolectricModule::class
    ]
  )
  interface TestApplicationComponent: DataProvidersInjector {
    @Component.Builder
    interface Builder {
      @BindsInstance
      fun setApplication(application: Application): Builder

      fun build(): TestApplicationComponent
    }

    fun inject(translationControllerTest: TranslationControllerTest)
  }

  class TestApplication : Application(), DataProvidersInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerTranslationControllerTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build()
    }

    fun inject(translationControllerTest: TranslationControllerTest) {
      component.inject(translationControllerTest)
    }

    override fun getDataProvidersInjector(): DataProvidersInjector = component
  }

  private companion object {
    private val BRAZIL_ENGLISH_LOCALE = Locale("en", "BR")

    private val PROFILE_ID_0 = ProfileId.newBuilder().apply {
      internalId = 0
    }.build()

    private val PROFILE_ID_1 = ProfileId.newBuilder().apply {
      internalId = 1
    }.build()

    private val APP_LANGUAGE_SELECTION_SYSTEM = AppLanguageSelection.newBuilder().apply {
      useSystemLanguageOrAppDefault = true
    }.build()

    private val WRITTEN_TRANSLATION_LANGUAGE_SELECTION_APP_LANGUAGE =
      WrittenTranslationLanguageSelection.newBuilder().apply {
        useAppLanguage = true
      }.build()

    private val AUDIO_TRANSLATION_LANGUAGE_SELECTION_APP_LANGUAGE =
      AudioTranslationLanguageSelection.newBuilder().apply {
        useAppLanguage = true
      }.build()
  }
}
