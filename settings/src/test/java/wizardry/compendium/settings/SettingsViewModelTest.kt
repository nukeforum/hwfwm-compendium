package wizardry.compendium.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.drive.backup.AuthAccount
import wizardry.compendium.drive.backup.BackupCoordinatorApi
import wizardry.compendium.drive.backup.BackupNowResult
import wizardry.compendium.drive.backup.BackupStatus
import wizardry.compendium.drive.backup.BackupStatusStoreApi
import wizardry.compendium.drive.backup.DriveAuth
import wizardry.compendium.drive.backup.EnableResult
import wizardry.compendium.drive.backup.RestoreResult
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect as ModelStatusEffect
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.wire.ContributionDomain
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val manifestation = Essence.Manifestation(
        name = "Wind",
        rank = Rank.Iron,
        rarity = Rarity.Common,
        properties = emptyList(),
        description = "",
        isRestricted = false,
    )
    private val stone = AwakeningStone(
        name = "Volcano",
        rank = Rank.Unranked,
        rarity = Rarity.Epic,
        properties = emptyList(),
        effects = emptyList(),
        description = "",
    )

    private lateinit var essenceRepo: StubEssenceRepository
    private lateinit var stoneRepo: StubAwakeningStoneRepository
    private lateinit var listingRepo: StubAbilityListingRepository
    private lateinit var effectRepo: StubStatusEffectRepository
    private lateinit var prefs: StubPreferencesRepository
    private lateinit var wireIo: WireIoRepository
    private lateinit var fakeBackup: FakeSettingsBackupCoordinator
    private lateinit var fakeBackupStatus: FakeSettingsBackupStatusStore
    private lateinit var fakeDriveAuth: FakeSettingsDriveAuth
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = StubEssenceRepository(contributions = listOf(manifestation))
        stoneRepo = StubAwakeningStoneRepository(contributions = listOf(stone))
        listingRepo = StubAbilityListingRepository(contributions = emptyList())
        effectRepo = StubStatusEffectRepository(contributions = emptyList())
        prefs = StubPreferencesRepository()
        wireIo = WireIoRepository(
            essenceRepository = essenceRepo,
            awakeningStoneRepository = stoneRepo,
            abilityListingRepository = listingRepo,
            statusEffectRepository = effectRepo,
        )
        fakeBackup = FakeSettingsBackupCoordinator()
        fakeBackupStatus = FakeSettingsBackupStatusStore()
        fakeDriveAuth = FakeSettingsDriveAuth()
        viewModel = SettingsViewModel(
            preferencesRepository = prefs,
            essenceRepository = essenceRepo,
            awakeningStoneRepository = stoneRepo,
            abilityListingRepository = listingRepo,
            statusEffectRepository = effectRepo,
            wireIo = wireIo,
            ioDispatcher = dispatcher,
            backup = fakeBackup,
            backupStatus = fakeBackupStatus,
            driveAuth = fakeDriveAuth,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openExportPicker populates rows with per-domain counts`() = runTest(dispatcher) {
        viewModel.openExportPicker()
        advanceUntilIdle()

        val state = viewModel.ioState.value
        assertTrue(state is SettingsViewModel.IoState.ExportPickerOpen)
        val open = state as SettingsViewModel.IoState.ExportPickerOpen
        // 1 manifestation, 0 confluences, 1 stone, 0 listings, 0 effects.
        val byDomain = open.rows.associateBy { it.key }
        assertEquals(1, byDomain[ContributionDomain.Essences]!!.count)
        assertEquals(true, byDomain[ContributionDomain.Essences]!!.selected)
        assertEquals(true, byDomain[ContributionDomain.Essences]!!.enabled)
        assertEquals(0, byDomain[ContributionDomain.Confluences]!!.count)
        assertEquals(false, byDomain[ContributionDomain.Confluences]!!.selected)
        assertEquals(false, byDomain[ContributionDomain.Confluences]!!.enabled)
        assertEquals(1, byDomain[ContributionDomain.AwakeningStones]!!.count)
        assertEquals(true, byDomain[ContributionDomain.AwakeningStones]!!.selected)
    }

    @Test
    fun `toggleExportDomain flips selected for the targeted row`() = runTest(dispatcher) {
        viewModel.openExportPicker()
        advanceUntilIdle()

        viewModel.toggleExportDomain(ContributionDomain.Essences)
        advanceUntilIdle()

        val state = viewModel.ioState.value as SettingsViewModel.IoState.ExportPickerOpen
        val byDomain = state.rows.associateBy { it.key }
        assertEquals(false, byDomain[ContributionDomain.Essences]!!.selected)
        // Other rows untouched.
        assertEquals(true, byDomain[ContributionDomain.AwakeningStones]!!.selected)
    }

    @Test
    fun `confirmExport with one selected domain encodes only that domain`() = runTest(dispatcher) {
        viewModel.openExportPicker()
        advanceUntilIdle()
        // Uncheck stones; keep essences only.
        viewModel.toggleExportDomain(ContributionDomain.AwakeningStones)
        advanceUntilIdle()

        viewModel.confirmExport()
        advanceUntilIdle()

        val state = viewModel.ioState.value
        assertTrue(state is SettingsViewModel.IoState.ReadyToShare)
        val ready = state as SettingsViewModel.IoState.ReadyToShare
        // Decode the encoded payload and verify only manifestations are present.
        val envelope = EnvelopeCodec.decode(ready.text)
        assertEquals(1, envelope.manifestations.size)
        assertTrue(envelope.stones.isEmpty())
    }

    @Test
    fun `pasteImport with valid bundle transitions to ImportPreviewOpen with bundle counts`() = runTest(dispatcher) {
        // Build a bundle by exporting all, then paste-import it back.
        viewModel.openExportPicker()
        advanceUntilIdle()
        viewModel.confirmExport()
        advanceUntilIdle()
        val payload = (viewModel.ioState.value as SettingsViewModel.IoState.ReadyToShare).text
        viewModel.dismissPicker()

        viewModel.pasteImport(payload)
        advanceUntilIdle()

        val state = viewModel.ioState.value
        assertTrue(state is SettingsViewModel.IoState.ImportPreviewOpen)
        val open = state as SettingsViewModel.IoState.ImportPreviewOpen
        val byDomain = open.rows.associateBy { it.key }
        assertEquals(1, byDomain[ContributionDomain.Essences]!!.count)
        assertEquals(" in bundle", byDomain[ContributionDomain.Essences]!!.countSuffix)
        assertEquals(true, byDomain[ContributionDomain.Essences]!!.enabled)
        assertEquals(0, byDomain[ContributionDomain.Confluences]!!.count)
        assertEquals(false, byDomain[ContributionDomain.Confluences]!!.enabled)
    }

    @Test
    fun `pasteImport with garbage transitions to ImportFailed`() = runTest(dispatcher) {
        viewModel.pasteImport("not a real bundle")
        advanceUntilIdle()

        val state = viewModel.ioState.value
        assertTrue(state is SettingsViewModel.IoState.ImportFailed)
    }

    @Test
    fun `setDriveBackupEnabled true triggers coordinator enable`() = runTest(dispatcher) {
        viewModel.setDriveBackupEnabled(true, org.mockito.kotlin.mock(), wizardry.compendium.drive.backup.NoUiResolutionResolver)
        advanceUntilIdle()
        assertEquals(1, fakeBackup.enableCalls)
    }

    @Test
    fun `confirmImport applies only the selected domains`() = runTest(dispatcher) {
        // Build a bundle with both essences and stones, then re-import via paste.
        viewModel.openExportPicker()
        advanceUntilIdle()
        viewModel.confirmExport()
        advanceUntilIdle()
        val payload = (viewModel.ioState.value as SettingsViewModel.IoState.ReadyToShare).text
        viewModel.dismissPicker()

        // Receiver: empty repos.
        essenceRepo.contributionsList = mutableListOf()
        stoneRepo.contributionsList = mutableListOf()

        viewModel.pasteImport(payload)
        advanceUntilIdle()
        // Uncheck stones — only essences should land.
        viewModel.toggleImportDomain(ContributionDomain.AwakeningStones)
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(1, essenceRepo.savedManifestations.size)
        assertEquals(0, stoneRepo.savedStones.size)
        assertTrue(viewModel.ioState.value is SettingsViewModel.IoState.ImportComplete)
    }
}

// --- Stubs ---

private class StubEssenceRepository(
    contributions: List<Essence>,
) : EssenceRepository {
    var contributionsList: MutableList<Essence> = contributions.toMutableList()
    val savedManifestations = mutableListOf<Essence.Manifestation>()
    override val essences: Flow<List<Essence>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = contributionsList.toList()
    override suspend fun getContributions(): List<Essence> = contributionsList.toList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult {
        savedManifestations += manifestation
        contributionsList += manifestation
        return ContributionResult.Success
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(
        originalName: String,
        manifestation: Essence.Manifestation,
    ): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(
        originalName: String,
        confluence: Essence.Confluence,
    ): ContributionResult = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private class StubAwakeningStoneRepository(
    contributions: List<AwakeningStone>,
) : AwakeningStoneRepository {
    var contributionsList: MutableList<AwakeningStone> = contributions.toMutableList()
    val savedStones = mutableListOf<AwakeningStone>()
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = contributionsList.toList()
    override suspend fun getContributions(): List<AwakeningStone> = contributionsList.toList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult {
        savedStones += stone
        contributionsList += stone
        return ContributionResult.Success
    }
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone): ContributionResult =
        ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private class StubAbilityListingRepository(
    contributions: List<Ability.Listing>,
) : AbilityListingRepository {
    private val contributionsList = contributions.toMutableList()
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = contributionsList.toList()
    override suspend fun getContributions(): List<Ability.Listing> = contributionsList.toList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private class StubStatusEffectRepository(
    contributions: List<ModelStatusEffect>,
) : StatusEffectRepository {
    private val contributionsList = contributions.toMutableList()
    override val statusEffects: Flow<List<ModelStatusEffect>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
    override suspend fun getStatusEffects(): List<ModelStatusEffect> = contributionsList.toList()
    override suspend fun getContributions(): List<ModelStatusEffect> = contributionsList.toList()
    override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
    override suspend fun saveStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(originalName: String, effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact =
        wizardry.compendium.repositories.DeleteImpact()
}

private class FakeSettingsBackupCoordinator : BackupCoordinatorApi {
    var nextEnableResult: EnableResult = EnableResult.Success(restored = false)
    var nextBackupResult: BackupNowResult = BackupNowResult.Success
    var nextRestoreResult: RestoreResult = RestoreResult.Success
    var enableCalls = 0
    var disableCalls = 0
    var backupCalls = 0
    var restoreCalls = 0
    override suspend fun enable(
        activityContext: android.content.Context,
        resolver: wizardry.compendium.drive.backup.ResolutionResolver,
    ): EnableResult {
        enableCalls++
        return nextEnableResult
    }
    override suspend fun disable() { disableCalls++ }
    override suspend fun backupNow(): BackupNowResult {
        backupCalls++
        return nextBackupResult
    }
    override suspend fun restoreNow(): RestoreResult {
        restoreCalls++
        return nextRestoreResult
    }
}

private class FakeSettingsBackupStatusStore : BackupStatusStoreApi {
    private val state = MutableStateFlow(BackupStatus())
    override fun statusFlow() = state
    override suspend fun recordSuccess(at: java.time.Instant) { state.value = state.value.copy(lastSuccessAt = at) }
    override suspend fun recordError(message: String, at: java.time.Instant) { state.value = state.value.copy(lastError = message, lastErrorAt = at) }
    override suspend fun recordRestore(at: java.time.Instant) { state.value = state.value.copy(lastRestoreAt = at) }
    override suspend fun clear() { state.value = BackupStatus() }
}

private class FakeSettingsDriveAuth : DriveAuth {
    private val account = MutableStateFlow<AuthAccount?>(null)
    override val currentAccount = account
    override suspend fun signIn(activityContext: android.content.Context, resolver: wizardry.compendium.drive.backup.ResolutionResolver) = DriveAuth.SignInResult.Success(AuthAccount("test@example.com"))
    override suspend fun signOut() { account.value = null }
    override suspend fun getValidAccessToken() = DriveAuth.TokenResult.Success("t")
}

private class StubPreferencesRepository : PreferencesRepository {
    private val essenceContributionsState = MutableStateFlow(false)
    private val awakeningStoneContributionsState = MutableStateFlow(false)
    private val abilityListingContributionsState = MutableStateFlow(false)
    private val statusEffectContributionsState = MutableStateFlow(true)
    private val essencesAsAwakeningStonesState = MutableStateFlow(false)
    private val themeModeState = MutableStateFlow(ThemeMode.System)
    private val dynamicColorEnabledState = MutableStateFlow(false)

    override val isEssenceContributionsEnabled: Boolean
        get() = essenceContributionsState.value
    override val essenceContributionsEnabled: Flow<Boolean>
        get() = essenceContributionsState
    override val isAwakeningStoneContributionsEnabled: Boolean
        get() = awakeningStoneContributionsState.value
    override val awakeningStoneContributionsEnabled: Flow<Boolean>
        get() = awakeningStoneContributionsState
    override val isAbilityListingContributionsEnabled: Boolean
        get() = abilityListingContributionsState.value
    override val abilityListingContributionsEnabled: Flow<Boolean>
        get() = abilityListingContributionsState
    override val isStatusEffectContributionsEnabled: Boolean
        get() = statusEffectContributionsState.value
    override val statusEffectContributionsEnabled: Flow<Boolean>
        get() = statusEffectContributionsState
    override val isEssencesAsAwakeningStonesEnabled: Boolean
        get() = essencesAsAwakeningStonesState.value
    override val essencesAsAwakeningStonesEnabled: Flow<Boolean>
        get() = essencesAsAwakeningStonesState
    override val themeMode: Flow<ThemeMode>
        get() = themeModeState
    override val isCurrentThemeMode: ThemeMode
        get() = themeModeState.value
    override val dynamicColorEnabled: Flow<Boolean>
        get() = dynamicColorEnabledState
    override val isDynamicColorEnabled: Boolean
        get() = dynamicColorEnabledState.value

    override fun setEssenceContributionsEnabled(enabled: Boolean) {
        essenceContributionsState.value = enabled
    }
    override fun setAwakeningStoneContributionsEnabled(enabled: Boolean) {
        awakeningStoneContributionsState.value = enabled
    }
    override fun setAbilityListingContributionsEnabled(enabled: Boolean) {
        abilityListingContributionsState.value = enabled
    }
    override fun setStatusEffectContributionsEnabled(enabled: Boolean) {
        statusEffectContributionsState.value = enabled
    }
    override fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean) {
        essencesAsAwakeningStonesState.value = enabled
    }
    override fun setThemeMode(mode: ThemeMode) {
        themeModeState.value = mode
    }
    override fun setDynamicColorEnabled(enabled: Boolean) {
        dynamicColorEnabledState.value = enabled
    }

    private val _driveEnabled = MutableStateFlow(false)
    private val _driveAccount = MutableStateFlow<String?>(null)

    override val driveBackupEnabled: Flow<Boolean> = _driveEnabled
    override val isDriveBackupEnabled get() = _driveEnabled.value
    override fun setDriveBackupEnabled(enabled: Boolean) { _driveEnabled.value = enabled }

    override val driveBackupAccountEmail: Flow<String?> = _driveAccount
    override val currentDriveBackupAccountEmail get() = _driveAccount.value
    override fun setDriveBackupAccountEmail(email: String?) { _driveAccount.value = email }
}
