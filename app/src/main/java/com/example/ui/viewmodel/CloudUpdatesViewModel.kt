package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivationCodeRecord
import com.example.data.AdminAnnouncement
import com.example.data.AdminPermissions
import com.example.data.AnnouncementPriority
import com.example.data.AppReleaseInfo
import com.example.data.CloudAnalyticsSummary
import com.example.data.CloudPoll
import com.example.data.DeveloperFeedbackRecord
import com.example.data.FeedbackCategory
import com.example.data.FirebaseCloudPlatformRepository
import com.example.data.FirebaseUpdateAndNotificationsRepository
import com.example.data.GradeComparisonRecord
import com.example.data.NameChangeRequestRecord
import com.example.data.NotificationTargetType
import com.example.data.ParentReportRecord
import com.example.data.PromoBannerConfig
import com.example.data.RemoteControlConfig
import com.example.data.SharedStudyGuideRecord
import com.example.data.SubAdminRecord
import com.example.data.TeacherAccountRecord
import com.example.data.TeacherChatMessageRecord
import com.example.data.TrialAccessStatus
import com.example.data.UpdateCheckResult
import java.io.File
import com.example.ui.screens.ToolRoute
import com.example.utils.ApkDownloadState
import com.example.utils.ApkUpdateDownloader
import com.example.utils.OfflineSyncOutbox
import com.example.utils.OutboxOpType
import com.example.utils.authStateFlow
import com.example.utils.signOutWithCleanup
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

data class TeacherCloudContext(
    val username: String = "",
    val teacherName: String = "",
    val specialtyId: String = "physics",
    val gradeId: String = "first",
    val isOnlineVisible: Boolean = true
)

class CloudUpdatesViewModel(
    private val appContext: Context,
    private val repository: FirebaseUpdateAndNotificationsRepository,
    private val platformRepo: FirebaseCloudPlatformRepository,
    private val outbox: OfflineSyncOutbox,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isOnline = MutableStateFlow(OfflineSyncOutbox.isOnlineNow(appContext))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingOutboxCount = MutableStateFlow(outbox.pendingCount())
    val pendingOutboxCount: StateFlow<Int> = _pendingOutboxCount.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminPermissions = MutableStateFlow(AdminPermissions())
    val adminPermissions: StateFlow<AdminPermissions> = _adminPermissions.asStateFlow()

    private val _adminCheckLoading = MutableStateFlow(false)
    val adminCheckLoading: StateFlow<Boolean> = _adminCheckLoading.asStateFlow()

    private val _hasAdminPin = MutableStateFlow(repository.hasAdminPin())
    val hasAdminPin: StateFlow<Boolean> = _hasAdminPin.asStateFlow()

    private val _adminPinUnlocked = MutableStateFlow(false)
    val adminPinUnlocked: StateFlow<Boolean> = _adminPinUnlocked.asStateFlow()

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateCheckResult: StateFlow<UpdateCheckResult> = _updateCheckResult.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val apkDownloadState: StateFlow<ApkDownloadState> = _apkDownloadState.asStateFlow()

    private val _teacherContext = MutableStateFlow(TeacherCloudContext())
    private val _dismissedNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    private val _deviceInstallId = MutableStateFlow("")

    private val _rawActiveAnnouncements = MutableStateFlow<List<AdminAnnouncement>>(emptyList())
    private val _verifiedNowMillis = MutableStateFlow(platformRepo.getVerifiedNowMillis())
    val verifiedNowMillis: StateFlow<Long> = _verifiedNowMillis.asStateFlow()

    val activeAnnouncements: StateFlow<List<AdminAnnouncement>> = combine(
        _rawActiveAnnouncements,
        _teacherContext,
        _currentUser,
        _verifiedNowMillis,
        _dismissedNotificationIds
    ) { items, ctx, user, nowMs, dismissed ->
        val uid = user?.uid.orEmpty()
        items.filter { ann ->
            ann.id !in dismissed &&
                ann.isReadyToDisplay(nowMs) &&
                ann.matchesTeacher(
                    username = ctx.username,
                    teacherName = ctx.teacherName,
                    uid = uid,
                    specialtyId = ctx.specialtyId,
                    gradeId = ctx.gradeId
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _adminAnnouncements = MutableStateFlow<List<AdminAnnouncement>>(emptyList())
    val adminAnnouncements: StateFlow<List<AdminAnnouncement>> = _adminAnnouncements.asStateFlow()

    private val _readNotificationIds = MutableStateFlow(repository.getLocalReadIds())
    val readNotificationIds: StateFlow<Set<String>> = _readNotificationIds.asStateFlow()

    val unreadCount: StateFlow<Int> = combine(
        activeAnnouncements,
        _readNotificationIds
    ) { items, readSet ->
        items.count { it.id.isNotBlank() && it.id !in readSet }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _remoteReleaseDraft = MutableStateFlow<AppReleaseInfo?>(null)
    val remoteReleaseDraft: StateFlow<AppReleaseInfo?> = _remoteReleaseDraft.asStateFlow()

    private val _remoteControl = MutableStateFlow(RemoteControlConfig())
    val remoteControl: StateFlow<RemoteControlConfig> = _remoteControl.asStateFlow()

    private val _promoBanner = MutableStateFlow(PromoBannerConfig())
    val promoBanner: StateFlow<PromoBannerConfig> = _promoBanner.asStateFlow()

    private val _internalUpdateBannerVisible = MutableStateFlow(false)
    val internalUpdateBannerVisible: StateFlow<Boolean> = _internalUpdateBannerVisible.asStateFlow()
    private var lastShownInternalText: String = ""
    private var internalBannerTimerJob: Job? = null

    private val _showInactivityDuaDialog = MutableStateFlow(false)
    val showInactivityDuaDialog: StateFlow<Boolean> = _showInactivityDuaDialog.asStateFlow()
    private var inactivityCheckedThisSession = false

    private val _activePolls = MutableStateFlow<List<CloudPoll>>(emptyList())
    private val _votedPollIds = MutableStateFlow<Set<String>>(emptySet())

    val unvotedPoll: StateFlow<CloudPoll?> = combine(
        _activePolls,
        _votedPollIds,
        _teacherContext,
        _currentUser
    ) { polls, voted, ctx, user ->
        val uid = user?.uid.orEmpty()
        polls.firstOrNull { poll ->
            poll.id.isNotBlank() &&
                poll.id !in voted &&
                poll.matchesTeacher(
                    username = ctx.username,
                    teacherName = ctx.teacherName,
                    uid = uid,
                    specialtyId = ctx.specialtyId,
                    gradeId = ctx.gradeId
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _teacherAccount = MutableStateFlow<TeacherAccountRecord?>(null)
    val teacherAccount: StateFlow<TeacherAccountRecord?> = _teacherAccount.asStateFlow()

    val trialAccessStatus: StateFlow<TrialAccessStatus> = combine(
        _teacherAccount,
        _verifiedNowMillis,
        _isAdmin,
        _remoteControl
    ) { acc, nowMs, admin, rc ->
        if (admin || acc == null) TrialAccessStatus.OPEN
        else if (acc.accessMode.equals("SUSPENDED", ignoreCase = true)) TrialAccessStatus.SUSPENDED
        else if (!rc.subscriptionsEnabled) TrialAccessStatus.OPEN
        else when (acc.accessMode.uppercase()) {
            "TRIAL" -> {
                if (acc.trialExpiresAtMillis > 0L && nowMs > acc.trialExpiresAtMillis) {
                    TrialAccessStatus.TRIAL_EXPIRED
                } else {
                    TrialAccessStatus.TRIAL_ACTIVE
                }
            }
            else -> TrialAccessStatus.OPEN
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TrialAccessStatus.OPEN)

    val remainingTrialDays: StateFlow<Int> = combine(
        _teacherAccount,
        _verifiedNowMillis
    ) { acc, nowMs ->
        if (acc == null || acc.accessMode != "TRIAL" || acc.trialExpiresAtMillis <= 0L) 0
        else {
            val diff = (acc.trialExpiresAtMillis - nowMs).coerceAtLeast(0L)
            ceil(diff / 86_400_000.0).toInt()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _unlockedPointsTools = MutableStateFlow(platformRepo.getUnlockedToolNamesLocal())
    val unlockedPointsTools: StateFlow<Set<String>> = _unlockedPointsTools.asStateFlow()

    private val _myParentReports = MutableStateFlow<List<ParentReportRecord>>(emptyList())
    val myParentReports: StateFlow<List<ParentReportRecord>> = _myParentReports.asStateFlow()

    private val _adminActionMessage = MutableStateFlow<String?>(null)
    val adminActionMessage: StateFlow<String?> = _adminActionMessage.asStateFlow()

    private val _userActionMessage = MutableStateFlow<String?>(null)
    val userActionMessage: StateFlow<String?> = _userActionMessage.asStateFlow()

    private val _busyOperation = MutableStateFlow(false)
    val busyOperation: StateFlow<Boolean> = _busyOperation.asStateFlow()

    private val _newSystemAnnouncementEvent = MutableSharedFlow<AdminAnnouncement>(extraBufferCapacity = 8)
    val newSystemAnnouncementEvent: SharedFlow<AdminAnnouncement> = _newSystemAnnouncementEvent.asSharedFlow()

    // Teacher Chat & Cross-Subject Comparison States
    private val _teacherChatMessages = MutableStateFlow<List<TeacherChatMessageRecord>>(emptyList())
    val teacherChatMessages: StateFlow<List<TeacherChatMessageRecord>> = _teacherChatMessages.asStateFlow()

    private val _directChatMessages = MutableStateFlow<List<TeacherChatMessageRecord>>(emptyList())
    val directChatMessages: StateFlow<List<TeacherChatMessageRecord>> = _directChatMessages.asStateFlow()

    private val _chatRemainingToday = MutableStateFlow(50)
    val chatRemainingToday: StateFlow<Int> = _chatRemainingToday.asStateFlow()

    private val _myGradeComparisons = MutableStateFlow<List<GradeComparisonRecord>>(emptyList())
    val myGradeComparisons: StateFlow<List<GradeComparisonRecord>> = _myGradeComparisons.asStateFlow()

    private val _joinedGradeComparisons = MutableStateFlow<List<GradeComparisonRecord>>(emptyList())
    val joinedGradeComparisons: StateFlow<List<GradeComparisonRecord>> = _joinedGradeComparisons.asStateFlow()

    private val _sharedStudyGuides = MutableStateFlow<List<SharedStudyGuideRecord>>(emptyList())
    val sharedStudyGuides: StateFlow<List<SharedStudyGuideRecord>> = _sharedStudyGuides.asStateFlow()

    private val _studyGuideBusy = MutableStateFlow(false)
    val studyGuideBusy: StateFlow<Boolean> = _studyGuideBusy.asStateFlow()

    // Admin Dashboard States
    private val _adminAllPolls = MutableStateFlow<List<CloudPoll>>(emptyList())
    val adminAllPolls: StateFlow<List<CloudPoll>> = _adminAllPolls.asStateFlow()

    private val _adminPollTallies = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val adminPollTallies: StateFlow<Map<String, List<Int>>> = _adminPollTallies.asStateFlow()

    private val _adminAllUsers = MutableStateFlow<List<TeacherAccountRecord>>(emptyList())
    val adminAllUsers: StateFlow<List<TeacherAccountRecord>> = _adminAllUsers.asStateFlow()

    private val _adminAllFeedback = MutableStateFlow<List<DeveloperFeedbackRecord>>(emptyList())
    val adminAllFeedback: StateFlow<List<DeveloperFeedbackRecord>> = _adminAllFeedback.asStateFlow()

    private val _adminAllParentReports = MutableStateFlow<List<ParentReportRecord>>(emptyList())
    val adminAllParentReports: StateFlow<List<ParentReportRecord>> = _adminAllParentReports.asStateFlow()

    private val _adminSubAdmins = MutableStateFlow<List<SubAdminRecord>>(emptyList())
    val adminSubAdmins: StateFlow<List<SubAdminRecord>> = _adminSubAdmins.asStateFlow()

    private val _adminActivationCodes = MutableStateFlow<List<ActivationCodeRecord>>(emptyList())
    val adminActivationCodes: StateFlow<List<ActivationCodeRecord>> = _adminActivationCodes.asStateFlow()

    private val _adminNameChangeRequests = MutableStateFlow<List<NameChangeRequestRecord>>(emptyList())
    val adminNameChangeRequests: StateFlow<List<NameChangeRequestRecord>> = _adminNameChangeRequests.asStateFlow()

    private val _adminAllComparisons = MutableStateFlow<List<GradeComparisonRecord>>(emptyList())
    val adminAllComparisons: StateFlow<List<GradeComparisonRecord>> = _adminAllComparisons.asStateFlow()

    private val _analyticsSummary = MutableStateFlow(CloudAnalyticsSummary())
    val analyticsSummary: StateFlow<CloudAnalyticsSummary> = _analyticsSummary.asStateFlow()

    private var activeAnnouncementsJob: Job? = null
    private var readStatesJob: Job? = null
    private var adminAnnouncementsJob: Job? = null
    private var remoteControlJob: Job? = null
    private var promoBannerJob: Job? = null
    private var activePollsJob: Job? = null
    private var votedPollsJob: Job? = null
    private var myReportsJob: Job? = null
    private var adminPollsJob: Job? = null
    private var chatJob: Job? = null
    private var directChatJob: Job? = null
    private var myComparisonsJob: Job? = null
    private var joinedComparisonsJob: Job? = null
    private var studyGuidesJob: Job? = null
    private var downloadJob: Job? = null
    private var lastAutoCheckedUid: String? = null
    private var unsyncedUsageDeltaSec: Long = 0L

    init {
        viewModelScope.launch {
            OfflineSyncOutbox.observeOnlineStatus(appContext).collect { online ->
                _isOnline.value = online
                if (online && auth.currentUser != null) {
                    platformRepo.syncServerTime().onSuccess { _verifiedNowMillis.value = it }
                    flushOutbox()
                    syncTeacherAccountNow()
                }
            }
        }

        viewModelScope.launch {
            auth.authStateFlow().collect { user ->
                _currentUser.value = user
                stopCloudListeners()
                if (user == null) {
                    _isAdmin.value = false
                    _adminPinUnlocked.value = false
                    _rawActiveAnnouncements.value = emptyList()
                    _adminAnnouncements.value = emptyList()
                    _activePolls.value = emptyList()
                    _votedPollIds.value = emptySet()
                    _teacherAccount.value = null
                    _myParentReports.value = emptyList()
                    _sharedStudyGuides.value = emptyList()
                    _teacherChatMessages.value = emptyList()
                    _directChatMessages.value = emptyList()
                    lastAutoCheckedUid = null
                } else {
                    startAuthenticatedStreams(user)
                }
            }
        }
    }

    fun updateTeacherContext(
        username: String,
        teacherName: String,
        specialtyId: String,
        gradeId: String,
        localUnlockedTools: List<String>,
        dismissedNotificationIds: List<String> = emptyList(),
        deviceInstallId: String = "",
        isOnlineVisible: Boolean = _teacherContext.value.isOnlineVisible
    ) {
        val prev = _teacherContext.value
        val next = TeacherCloudContext(
            username = username.trim(),
            teacherName = teacherName.trim(),
            specialtyId = specialtyId,
            gradeId = gradeId,
            isOnlineVisible = isOnlineVisible
        )
        _teacherContext.value = next
        _dismissedNotificationIds.value = _dismissedNotificationIds.value + dismissedNotificationIds
        if (deviceInstallId.isNotBlank()) {
            _deviceInstallId.value = deviceInstallId
        }
        if (localUnlockedTools.isNotEmpty()) {
            _unlockedPointsTools.value = _unlockedPointsTools.value + localUnlockedTools
        }
        if (prev.specialtyId != next.specialtyId && auth.currentUser != null) {
            startChatStream(next.specialtyId)
        }
        if (prev != next && auth.currentUser != null) {
            syncTeacherAccountNow()
        }
    }

    fun checkSevenDayInactivity(lastAppActiveMillis: Long, onMarkActiveNow: (Long) -> Unit) {
        if (inactivityCheckedThisSession) return
        inactivityCheckedThisSession = true
        val now = platformRepo.getVerifiedNowMillis()
        if (lastAppActiveMillis > 0L && (now - lastAppActiveMillis) >= 7L * 86_400_000L) {
            _showInactivityDuaDialog.value = true
        }
        onMarkActiveNow(now)
    }

    fun dismissInactivityDuaDialog() {
        _showInactivityDuaDialog.value = false
    }

    fun triggerShowInternalUpdateBanner(durationSec: Int = _remoteControl.value.internalUpdateDurationSec) {
        internalBannerTimerJob?.cancel()
        _internalUpdateBannerVisible.value = true
        internalBannerTimerJob = viewModelScope.launch {
            delay(durationSec.coerceIn(3, 30) * 1000L)
            _internalUpdateBannerVisible.value = false
        }
    }

    fun dismissInternalUpdateBanner() {
        internalBannerTimerJob?.cancel()
        _internalUpdateBannerVisible.value = false
    }

    private fun stopCloudListeners() {
        activeAnnouncementsJob?.cancel()
        readStatesJob?.cancel()
        adminAnnouncementsJob?.cancel()
        remoteControlJob?.cancel()
        promoBannerJob?.cancel()
        activePollsJob?.cancel()
        votedPollsJob?.cancel()
        myReportsJob?.cancel()
        adminPollsJob?.cancel()
        chatJob?.cancel()
        directChatJob?.cancel()
        myComparisonsJob?.cancel()
        joinedComparisonsJob?.cancel()
        studyGuidesJob?.cancel()
    }

    private fun startChatStream(specialtyId: String) {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            platformRepo.observeSpecialtyChatMessages(specialtyId).collect { msgs ->
                _teacherChatMessages.value = msgs
            }
        }
        directChatJob?.cancel()
        directChatJob = viewModelScope.launch {
            platformRepo.observeDirectChatMessagesForUser().collect { msgs ->
                _directChatMessages.value = msgs
            }
        }
        viewModelScope.launch {
            val used = platformRepo.getTodayChatCount()
            _chatRemainingToday.value = (50 - used).coerceIn(0, 50)
        }
    }

    private fun startAuthenticatedStreams(user: FirebaseUser) {
        val uid = user.uid
        activeAnnouncementsJob = viewModelScope.launch {
            repository.observeActiveAnnouncements().collect { res ->
                res.onSuccess { items ->
                    _rawActiveAnnouncements.value = items
                    val ctx = _teacherContext.value
                    val nowMs = platformRepo.getVerifiedNowMillis()
                    val matched = items.filter {
                        it.id !in _dismissedNotificationIds.value &&
                            it.isReadyToDisplay(nowMs) &&
                            it.matchesTeacher(ctx.username, ctx.teacherName, uid, ctx.specialtyId, ctx.gradeId)
                    }
                    val alreadyNotified = repository.getAlreadySystemNotifiedIds()
                    val readIds = _readNotificationIds.value
                    val freshUnseen = matched.filter {
                        it.id.isNotBlank() && it.id !in alreadyNotified && it.id !in readIds
                    }
                    if (freshUnseen.isNotEmpty()) {
                        repository.markSystemNotifiedIds(freshUnseen.map { it.id })
                        freshUnseen.firstOrNull()?.let { _newSystemAnnouncementEvent.tryEmit(it) }
                    }
                }
            }
        }

        readStatesJob = viewModelScope.launch {
            repository.observeUserReadIds(uid).collect { ids ->
                _readNotificationIds.value = ids
            }
        }

        remoteControlJob = viewModelScope.launch {
            platformRepo.observeRemoteControl().collect { cfg ->
                _remoteControl.value = cfg
                if (cfg.internalUpdateEnabled && cfg.internalUpdateText.isNotBlank() && cfg.internalUpdateText != lastShownInternalText) {
                    lastShownInternalText = cfg.internalUpdateText
                    triggerShowInternalUpdateBanner(cfg.internalUpdateDurationSec)
                }
                syncTeacherAccountNow()
            }
        }

        promoBannerJob = viewModelScope.launch {
            platformRepo.observePromoBanner().collect { banner ->
                _promoBanner.value = banner
            }
        }

        activePollsJob = viewModelScope.launch {
            platformRepo.observeActivePolls().collect { polls ->
                _activePolls.value = polls
            }
        }

        votedPollsJob = viewModelScope.launch {
            platformRepo.observeMyVotedPollIds(uid).collect { ids ->
                _votedPollIds.value = _votedPollIds.value + ids
            }
        }

        myReportsJob = viewModelScope.launch {
            platformRepo.observeMyParentReports(uid).collect { reports ->
                _myParentReports.value = reports
            }
        }

        startChatStream(_teacherContext.value.specialtyId)

        myComparisonsJob = viewModelScope.launch {
            platformRepo.observeMyGradeComparisons(uid).collect { list ->
                _myGradeComparisons.value = list
            }
        }

        joinedComparisonsJob = viewModelScope.launch {
            platformRepo.observeJoinedGradeComparisons(uid).collect { list ->
                _joinedGradeComparisons.value = list
            }
        }

        studyGuidesJob = viewModelScope.launch {
            platformRepo.observeSharedStudyGuides().collect { guides ->
                _sharedStudyGuides.value = guides
            }
        }

        viewModelScope.launch {
            platformRepo.syncServerTime().onSuccess { _verifiedNowMillis.value = it }
            flushOutbox()
            syncTeacherAccountNow()
        }

        refreshAdminStatus()

        if (lastAutoCheckedUid != uid) {
            lastAutoCheckedUid = uid
            checkForUpdates(openDialogEvenIfUpToDate = false)
        }
    }

    fun syncTeacherAccountNow() {
        val uid = auth.currentUser?.uid ?: return
        val ctx = _teacherContext.value
        val rc = _remoteControl.value
        val devId = _deviceInstallId.value
        viewModelScope.launch {
            platformRepo.ensureOrSyncTeacherAccount(
                username = ctx.username,
                teacherName = ctx.teacherName,
                specialtyId = ctx.specialtyId,
                gradeId = ctx.gradeId,
                remoteControl = rc,
                deviceInstallId = devId,
                isOnlineVisible = ctx.isOnlineVisible
            ).onSuccess { acc ->
                _teacherAccount.value = acc
                val refBonus = platformRepo.collectPendingReferralRewards(acc).getOrDefault(0)
                val giftBonus = platformRepo.claimUnclaimedAdminGifts(acc).getOrDefault(0)
                if (refBonus > 0 || giftBonus > 0) {
                    if (giftBonus > 0) {
                        _userActionMessage.value = "تهانينا! حصلت على هدية جماعية (+$giftBonus نقطة) 🎁"
                    } else if (refBonus > 0) {
                        _userActionMessage.value = "حصلت على +$refBonus نقطة من دعوات الإحالة! 🎉"
                    }
                    platformRepo.ensureOrSyncTeacherAccount(
                        username = ctx.username,
                        teacherName = ctx.teacherName,
                        specialtyId = ctx.specialtyId,
                        gradeId = ctx.gradeId,
                        remoteControl = rc,
                        deviceInstallId = devId,
                        isOnlineVisible = ctx.isOnlineVisible
                    ).onSuccess { refreshed -> _teacherAccount.value = refreshed }
                }
            }
        }
    }

    fun toggleOnlinePresence(visible: Boolean) {
        _teacherContext.value = _teacherContext.value.copy(isOnlineVisible = visible)
        val uid = auth.currentUser?.uid ?: return
        val accKey = _teacherAccount.value?.accountKey
            ?: platformRepo.resolveAccountKey(_teacherContext.value.username, uid)
        viewModelScope.launch {
            platformRepo.updateOnlinePresence(accKey, visible)
        }
    }

    fun refreshTrialClock() {
        viewModelScope.launch {
            platformRepo.syncServerTime().onSuccess { _verifiedNowMillis.value = it }
            syncTeacherAccountNow()
        }
    }

    fun refreshAdminStatus() {
        if (auth.currentUser == null) {
            _isAdmin.value = false
            _adminPermissions.value = AdminPermissions()
            return
        }
        viewModelScope.launch {
            _adminCheckLoading.value = true
            val perms = repository.verifyAdminPermissions().getOrDefault(AdminPermissions())
            _adminPermissions.value = perms
            val hasAccess = perms.hasAnyAdminAccess
            _isAdmin.value = hasAccess
            _adminCheckLoading.value = false
            if (hasAccess) {
                startAdminStreams(perms)
            } else {
                adminAnnouncementsJob?.cancel()
                adminPollsJob?.cancel()
                _adminAnnouncements.value = emptyList()
                _adminAllPolls.value = emptyList()
            }
        }
    }

    private fun startAdminStreams(perms: AdminPermissions = _adminPermissions.value) {
        adminAnnouncementsJob?.cancel()
        adminAnnouncementsJob = viewModelScope.launch {
            repository.observeAllAnnouncementsForAdmin(perms.isFullAdmin || perms.canManageAnnouncements).collect { res ->
                res.onSuccess { items -> _adminAnnouncements.value = items }
            }
        }
        adminPollsJob?.cancel()
        adminPollsJob = viewModelScope.launch {
            platformRepo.observeAllPollsForAdmin().collect { polls ->
                _adminAllPolls.value = polls
            }
        }
        viewModelScope.launch {
            repository.getLatestReleaseRaw().onSuccess { _remoteReleaseDraft.value = it }
        }
        loadAdminDashboardData()
    }

    fun loadAdminDashboardData() {
        val perms = _adminPermissions.value
        if (!_isAdmin.value) return
        viewModelScope.launch {
            if (perms.isFullAdmin || perms.canManageUsers || perms.canViewAnalytics) {
                platformRepo.adminFetchAllAccounts().onSuccess { users ->
                    _adminAllUsers.value = users
                    computeAnalytics(users)
                }
            }
            if (perms.isFullAdmin) {
                platformRepo.adminFetchAllFeedback().onSuccess { fb ->
                    _adminAllFeedback.value = fb
                }
                platformRepo.adminFetchAllParentReports().onSuccess { reps ->
                    _adminAllParentReports.value = reps
                }
                repository.adminFetchSubAdmins().onSuccess { subs ->
                    _adminSubAdmins.value = subs
                }
            }
            if (perms.isFullAdmin || perms.canManageCodes) {
                platformRepo.adminFetchActivationCodes().onSuccess { codes ->
                    _adminActivationCodes.value = codes
                }
            }
            if (perms.isFullAdmin || perms.canManageUsers) {
                platformRepo.adminFetchNameChangeRequests().onSuccess { reqs ->
                    _adminNameChangeRequests.value = reqs
                }
            }
            if (perms.isFullAdmin || perms.canManageComparisons) {
                platformRepo.adminFetchAllGradeComparisons().onSuccess { cmps ->
                    _adminAllComparisons.value = cmps
                }
            }
        }
    }

    private fun computeAnalytics(users: List<TeacherAccountRecord>) {
        val todayKey = platformRepo.todayDateKey()
        val activeToday = users.count { it.todayDateKey == todayKey && it.todayUsageSeconds > 0L }
        val todayMin = users.filter { it.todayDateKey == todayKey }.sumOf { it.todayUsageSeconds } / 60L
        val allHours = users.sumOf { it.totalUsageSeconds } / 3600.0
        val rated = users.filter { it.ratingStars in 1..5 }
        val avgRating = if (rated.isEmpty()) 0.0 else rated.map { it.ratingStars }.average()
        val toolTotals = mutableMapOf<ToolRoute, Long>()
        users.forEach { acc ->
            acc.toolUsageCounts.forEach { (key, count) ->
                val route = ToolRoute.entries.firstOrNull { it.name == key }
                if (route != null) {
                    toolTotals[route] = (toolTotals[route] ?: 0L) + count
                }
            }
        }
        val ranked = toolTotals.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
        _analyticsSummary.value = CloudAnalyticsSummary(
            totalUsers = users.size,
            activeTodayUsers = activeToday,
            totalTodayMinutes = todayMin,
            totalAllTimeHours = allHours,
            averageRating = avgRating,
            ratingsCount = rated.size,
            topTools = ranked
        )
    }

    // ==================== Usage Telemetry & Offline Outbox ====================

    fun recordUsageTick(deltaSec: Long) {
        platformRepo.addLocalUsageSeconds(deltaSec, toolKey = null)
        unsyncedUsageDeltaSec += deltaSec
        if (unsyncedUsageDeltaSec >= 60L) {
            unsyncedUsageDeltaSec = 0L
            pushUsageSnapshot()
        }
    }

    fun recordToolOpened(route: ToolRoute) {
        platformRepo.addLocalUsageSeconds(0L, toolKey = route.name)
        pushUsageSnapshot()
    }

    private fun pushUsageSnapshot() {
        val uid = auth.currentUser?.uid ?: return
        val ctx = _teacherContext.value
        val accountKey = _teacherAccount.value?.accountKey
            ?: platformRepo.resolveAccountKey(ctx.username, uid)
        val todayKey = platformRepo.todayDateKey()
        val todaySec = platformRepo.getLocalTodayUsageSeconds(todayKey)
        val totalSec = platformRepo.getLocalTotalUsageSeconds()
        val counts = platformRepo.getLocalToolCounts()
        if (!_isOnline.value) {
            outbox.enqueue(
                OutboxOpType.TELEMETRY,
                JSONObject().apply {
                    put("accountKey", accountKey)
                    put("todayDateKey", todayKey)
                    put("todayUsageSeconds", todaySec)
                    put("totalUsageSeconds", totalSec)
                    put("toolCounts", JSONObject(counts))
                }
            )
            _pendingOutboxCount.value = outbox.pendingCount()
            return
        }
        viewModelScope.launch {
            platformRepo.syncLocalUsageAndTelemetry(accountKey, todayKey, todaySec, totalSec, counts)
        }
    }

    suspend fun flushOutbox() {
        if (!_isOnline.value || auth.currentUser == null) return
        val entries = outbox.getPendingEntries()
        if (entries.isEmpty()) return
        val succeeded = mutableSetOf<String>()
        for (entry in entries) {
            val obj = runCatching { JSONObject(entry.payloadJson) }.getOrNull() ?: continue
            val ok = when (entry.type) {
                OutboxOpType.POLL_VOTE -> {
                    platformRepo.submitPollVote(obj.optString("pollId"), obj.optInt("optionIndex", 0)).isSuccess
                }
                OutboxOpType.RATING -> {
                    platformRepo.submitRating(
                        obj.optString("accountKey"),
                        obj.optInt("stars", 5),
                        obj.optString("comment")
                    ).isSuccess
                }
                OutboxOpType.FEEDBACK -> {
                    platformRepo.submitDeveloperFeedback(
                        senderName = obj.optString("senderName"),
                        accountKey = obj.optString("accountKey"),
                        category = FeedbackCategory.fromRaw(obj.optString("category")),
                        message = obj.optString("message")
                    ).isSuccess
                }
                OutboxOpType.REFERRAL_CLAIM -> {
                    platformRepo.redeemFriendReferralCode(
                        obj.optString("accountKey"),
                        obj.optString("code")
                    ).isSuccess
                }
                OutboxOpType.TELEMETRY -> {
                    val toolObj = obj.optJSONObject("toolCounts")
                    val map = mutableMapOf<String, Long>()
                    toolObj?.keys()?.forEach { k -> map[k] = toolObj.optLong(k, 0L) }
                    platformRepo.syncLocalUsageAndTelemetry(
                        accountKey = obj.optString("accountKey"),
                        todayDateKey = obj.optString("todayDateKey"),
                        todayUsageSeconds = obj.optLong("todayUsageSeconds"),
                        totalUsageSeconds = obj.optLong("totalUsageSeconds"),
                        toolUsageCounts = map
                    ).isSuccess
                }
            }
            if (ok) succeeded += entry.id
        }
        outbox.removeByIds(succeeded)
        _pendingOutboxCount.value = outbox.pendingCount()
        if (succeeded.isNotEmpty()) {
            syncTeacherAccountNow()
        }
    }

    // ==================== Update & APK Downloader ====================

    fun checkForUpdates(openDialogEvenIfUpToDate: Boolean) {
        if (auth.currentUser == null) {
            _updateCheckResult.value = UpdateCheckResult.Error("يرجى تسجيل الدخول بحساب Google للتحقق من التحديثات أونلاين.")
            if (openDialogEvenIfUpToDate) _showUpdateDialog.value = true
            return
        }
        viewModelScope.launch {
            _updateCheckResult.value = UpdateCheckResult.Checking
            if (openDialogEvenIfUpToDate) _showUpdateDialog.value = true
            val res = repository.fetchLatestRelease(currentVersionCode, currentVersionName)
            res.onSuccess { state ->
                _updateCheckResult.value = state
                when (state) {
                    is UpdateCheckResult.UpdateAvailable -> _showUpdateDialog.value = true
                    is UpdateCheckResult.UpToDate -> if (openDialogEvenIfUpToDate) _showUpdateDialog.value = true
                    else -> Unit
                }
            }.onFailure { err ->
                _updateCheckResult.value = UpdateCheckResult.Error(
                    err.localizedMessage ?: "تعذر الاتصال بخادم التحديثات."
                )
                if (openDialogEvenIfUpToDate) _showUpdateDialog.value = true
            }
        }
    }

    fun dismissUpdateDialogIfAllowed() {
        val current = _updateCheckResult.value
        if (current is UpdateCheckResult.UpdateAvailable && current.release.forceUpdate) {
            return
        }
        _showUpdateDialog.value = false
    }

    fun startApkDownload(context: Context, apkUrl: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            ApkUpdateDownloader.downloadApk(context.applicationContext, apkUrl).collect { state ->
                _apkDownloadState.value = state
            }
        }
    }

    fun resetApkDownloadState() {
        downloadJob?.cancel()
        _apkDownloadState.value = ApkDownloadState.Idle
    }

    fun markNotificationRead(id: String) {
        _readNotificationIds.value = _readNotificationIds.value + id
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllRead() {
        val allIds = activeAnnouncements.value.mapNotNull { it.id.takeIf(String::isNotBlank) }
        _readNotificationIds.value = _readNotificationIds.value + allIds
        viewModelScope.launch {
            repository.markAllNotificationsRead(allIds)
        }
    }

    fun markNotificationsDismissed(ids: List<String>) {
        val clean = ids.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        _dismissedNotificationIds.value = _dismissedNotificationIds.value + clean
        _readNotificationIds.value = _readNotificationIds.value + clean
    }

    // ==================== Teacher User Cloud Actions ====================

    fun clearUserActionMessage() {
        _userActionMessage.value = null
    }

    fun voteOnPoll(pollId: String, optionIndex: Int) {
        // Hide poll window immediately
        _votedPollIds.value = _votedPollIds.value + pollId
        if (!_isOnline.value) {
            outbox.enqueue(
                OutboxOpType.POLL_VOTE,
                JSONObject().apply {
                    put("pollId", pollId)
                    put("optionIndex", optionIndex)
                }
            )
            _pendingOutboxCount.value = outbox.pendingCount()
            _userActionMessage.value = "تم تسجيل تصويتك محلياً وسيتم إرساله تلقائياً عند توفر الإنترنت ✓"
            return
        }
        viewModelScope.launch {
            platformRepo.submitPollVote(pollId, optionIndex)
                .onSuccess { _userActionMessage.value = "شكراً لمشاركتك! تم تسجيل تصويتك بنجاح ✓" }
                .onFailure {
                    outbox.enqueue(
                        OutboxOpType.POLL_VOTE,
                        JSONObject().apply {
                            put("pollId", pollId)
                            put("optionIndex", optionIndex)
                        }
                    )
                    _pendingOutboxCount.value = outbox.pendingCount()
                }
        }
    }

    fun backupAllDataEncrypted(
        username: String,
        password: String,
        plainStateJson: String,
        studentCount: Int,
        onAccountSynced: (String) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            _userActionMessage.value = null
            val ctx = _teacherContext.value
            val result = withContext(Dispatchers.Default) {
                platformRepo.uploadEncryptedBackup(
                    username = username,
                    password = password,
                    plainStateJson = plainStateJson,
                    studentCount = studentCount,
                    teacherName = ctx.teacherName,
                    specialtyId = ctx.specialtyId,
                    gradeId = ctx.gradeId,
                    remoteControl = _remoteControl.value
                )
            }
            _busyOperation.value = false
            result.onSuccess { acc ->
                _teacherAccount.value = acc
                onAccountSynced(username.trim())
                _userActionMessage.value = "تم نسخ جميع بياناتك وتشفيرها وحفظها في السحابة بنجاح ✓"
            }.onFailure { err ->
                _userActionMessage.value = "تعذر النسخ الاحتياطي: ${err.localizedMessage ?: "خطأ"}"
            }
        }
    }

    fun restoreAllDataEncrypted(
        username: String,
        password: String,
        onRestoredJson: (String, String) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            _userActionMessage.value = null
            val res = withContext(Dispatchers.Default) {
                platformRepo.restoreEncryptedBackupByCredentials(username, password)
            }
            _busyOperation.value = false
            res.onSuccess { plainJson ->
                onRestoredJson(username.trim(), plainJson)
                _userActionMessage.value = "تم استدعاء واسترجاع جميع السجلات والتفضيلات والدرجات بنجاح ✓"
                syncTeacherAccountNow()
            }.onFailure { err ->
                _userActionMessage.value = "تعذر الاسترجاع: ${err.localizedMessage ?: "بيانات غير متطابقة"}"
            }
        }
    }

    fun redeemReferralCode(code: String) {
        val accKey = _teacherAccount.value?.accountKey
            ?: auth.currentUser?.uid?.let { platformRepo.resolveAccountKey(_teacherContext.value.username, it) }
            ?: return
        if (!_isOnline.value) {
            outbox.enqueue(
                OutboxOpType.REFERRAL_CLAIM,
                JSONObject().apply {
                    put("accountKey", accKey)
                    put("code", code.trim())
                }
            )
            _pendingOutboxCount.value = outbox.pendingCount()
            _userActionMessage.value = "تم حفظ رمز الإحالة وسيتم تفعيله فور عودة الإنترنت ✓"
            return
        }
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.redeemFriendReferralCode(accKey, code)
                .onSuccess { bonus ->
                    _userActionMessage.value = "مبروك! حصلت على +$bonus نقطة وتم إرسال مكافأة لصديقك ✓"
                    syncTeacherAccountNow()
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "تعذر تفعيل رمز الإحالة."
                }
            _busyOperation.value = false
        }
    }

    fun submitAppRating(stars: Int, comment: String) {
        val accKey = _teacherAccount.value?.accountKey
            ?: auth.currentUser?.uid?.let { platformRepo.resolveAccountKey(_teacherContext.value.username, it) }
            ?: return
        if (!_isOnline.value) {
            outbox.enqueue(
                OutboxOpType.RATING,
                JSONObject().apply {
                    put("accountKey", accKey)
                    put("stars", stars)
                    put("comment", comment)
                }
            )
            _pendingOutboxCount.value = outbox.pendingCount()
            _userActionMessage.value = "تم حفظ تقييمك وسيتم إرساله فور توفر الإنترنت ✓"
            return
        }
        viewModelScope.launch {
            platformRepo.submitRating(accKey, stars, comment)
                .onSuccess {
                    _userActionMessage.value = "شكراً لتقييمك ($stars نجوم) ★"
                    syncTeacherAccountNow()
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "تعذر إرسال التقييم."
                }
        }
    }

    fun unlockToolWithPoints(tool: ToolRoute, cost: Int, onUnlocked: () -> Unit) {
        val acc = _teacherAccount.value
        if (acc == null) {
            _userActionMessage.value = "يرجى الاتصال بالإنترنت ومزامنة حسابك أولاً."
            return
        }
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.spendPointsForToolUnlock(acc, cost)
                .onSuccess { remaining ->
                    platformRepo.addUnlockedToolNameLocal(tool.name)
                    _unlockedPointsTools.value = _unlockedPointsTools.value + tool.name
                    _teacherAccount.value = acc.copy(points = remaining)
                    _userActionMessage.value = "تم فتح (${tool.shortTitleAr}) بنجاح! المتبقي $remaining نقطة ✓"
                    onUnlocked()
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "نقاطك غير كافية."
                }
            _busyOperation.value = false
        }
    }

    fun submitFeedbackToDeveloper(
        category: FeedbackCategory,
        message: String,
        onSaved: (DeveloperFeedbackRecord) -> Unit
    ) {
        val ctx = _teacherContext.value
        val uid = auth.currentUser?.uid
        val accKey = _teacherAccount.value?.accountKey
            ?: uid?.let { platformRepo.resolveAccountKey(ctx.username, it) }.orEmpty()
        val sender = ctx.teacherName.ifBlank { ctx.username.ifBlank { "مدرس التطبيق" } }
        if (!_isOnline.value) {
            outbox.enqueue(
                OutboxOpType.FEEDBACK,
                JSONObject().apply {
                    put("senderName", sender)
                    put("accountKey", accKey)
                    put("category", category.raw)
                    put("message", message)
                }
            )
            _pendingOutboxCount.value = outbox.pendingCount()
            _userActionMessage.value = "تم حفظ رسالتك للمطور وسيتم إرسالها تلقائياً عند توفر الإنترنت ✓"
            return
        }
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.submitDeveloperFeedback(sender, accKey, category, message)
                .onSuccess { saved ->
                    onSaved(saved)
                    _userActionMessage.value = "تم مراسلة المطور بنجاح ✓"
                }
                .onFailure { err ->
                    _userActionMessage.value = "تعذر الإرسال: ${err.localizedMessage ?: "خطأ"}"
                }
            _busyOperation.value = false
        }
    }

    fun publishParentReport(
        teacherName: String,
        subjectName: String,
        studentName: String,
        gradeTitle: String,
        sectionId: String,
        visibilityJson: String,
        reportDataJson: String,
        onResult: (Result<ParentReportRecord>) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            val res = platformRepo.publishParentReport(
                teacherName = teacherName,
                subjectName = subjectName,
                studentName = studentName,
                gradeTitle = gradeTitle,
                sectionId = sectionId,
                visibilityJson = visibilityJson,
                reportDataJson = reportDataJson
            )
            _busyOperation.value = false
            onResult(res)
        }
    }

    fun lookupParentReport(code: String, onResult: (Result<ParentReportRecord>) -> Unit) {
        viewModelScope.launch {
            _busyOperation.value = true
            val res = platformRepo.fetchParentReportByCode(code)
            _busyOperation.value = false
            onResult(res)
        }
    }

    // ==================== Admin PIN & Super Dashboard Actions ====================

    fun setupAdminPin(pin: String): Boolean {
        val saved = repository.saveAdminPin(pin)
        if (saved) {
            _hasAdminPin.value = true
            _adminPinUnlocked.value = true
        }
        return saved
    }

    fun unlockWithAdminPin(pin: String): Boolean {
        val ok = repository.verifyAdminPin(pin)
        if (ok) _adminPinUnlocked.value = true
        return ok
    }

    fun lockAdminPanelSession() {
        _adminPinUnlocked.value = false
    }

    fun clearAdminActionMessage() {
        _adminActionMessage.value = null
    }

    fun publishAnnouncement(
        title: String,
        body: String,
        priority: AnnouncementPriority,
        authorName: String,
        targetType: NotificationTargetType = NotificationTargetType.ALL,
        targetValue: String = "",
        scheduledForMillis: Long = 0L,
        iconBase64: String = ""
    ) {
        viewModelScope.launch {
            _adminActionMessage.value = null
            repository.createAnnouncement(title, body, priority, authorName, targetType, targetValue, scheduledForMillis, iconBase64)
                .onSuccess {
                    _adminActionMessage.value = if (scheduledForMillis > 0L) {
                        "تمت جدولة الإشعار وسيصل للمدرسين في الموعد المحدد ✓"
                    } else {
                        "تم إرسال الإشعار ونشره بنجاح ✓"
                    }
                }
                .onFailure { err ->
                    _adminActionMessage.value = "تعذر نشر الإشعار: ${err.localizedMessage ?: "خطأ غير متوقع"}"
                }
        }
    }

    fun toggleAnnouncementActive(id: String, active: Boolean) {
        viewModelScope.launch {
            repository.setAnnouncementActive(id, active)
                .onSuccess {
                    _adminActionMessage.value = if (active) "تم تفعيل الإشعار ✓" else "تم إخفاء الإشعار ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل تحديث الحالة: ${err.localizedMessage ?: "خطأ"}"
                }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            repository.deleteAnnouncement(id)
                .onSuccess {
                    _adminActionMessage.value = "تم حذف الإشعار نهائياً ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل الحذف: ${err.localizedMessage ?: "خطأ"}"
                }
        }
    }

    fun publishAppRelease(
        latestVersionCode: Int,
        latestVersionName: String,
        releaseNotes: String,
        forceUpdate: Boolean,
        apkUrl: String,
        releaseImageUrl: String = "",
        releaseImageBase64: String = ""
    ) {
        viewModelScope.launch {
            _adminActionMessage.value = null
            repository.publishReleaseConfig(
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                releaseNotes = releaseNotes,
                forceUpdate = forceUpdate,
                apkUrl = apkUrl,
                releaseImageUrl = releaseImageUrl,
                releaseImageBase64 = releaseImageBase64
            ).onSuccess {
                _adminActionMessage.value = "تم حفظ بيانات التحديث الجديد في Firebase ✓"
                repository.getLatestReleaseRaw().onSuccess { _remoteReleaseDraft.value = it }
            }.onFailure { err ->
                _adminActionMessage.value = "تعذر حفظ بيانات التحديث: ${err.localizedMessage ?: "خطأ"}"
            }
        }
    }

    fun publishPoll(
        question: String,
        options: List<String>,
        targetType: NotificationTargetType,
        targetValue: String
    ) {
        viewModelScope.launch {
            platformRepo.createPoll(question, options, targetType, targetValue)
                .onSuccess { _adminActionMessage.value = "تم نشر الاستطلاع للتصويت الحي ✓" }
                .onFailure { err -> _adminActionMessage.value = "تعذر نشر الاستطلاع: ${err.localizedMessage}" }
        }
    }

    fun togglePollActive(pollId: String, active: Boolean) {
        viewModelScope.launch {
            platformRepo.setPollActive(pollId, active)
                .onSuccess { _adminActionMessage.value = if (active) "تم تفعيل الاستطلاع ✓" else "تم إيقاف الاستطلاع ✓" }
        }
    }

    fun deletePoll(pollId: String) {
        viewModelScope.launch {
            platformRepo.deletePoll(pollId)
                .onSuccess { _adminActionMessage.value = "تم حذف الاستطلاع ✓" }
        }
    }

    fun loadPollTallies(poll: CloudPoll) {
        viewModelScope.launch {
            platformRepo.fetchPollVoteTallies(poll.id, poll.options.size)
                .onSuccess { counts ->
                    _adminPollTallies.value = _adminPollTallies.value + (poll.id to counts)
                }
        }
    }

    fun saveRemoteControl(config: RemoteControlConfig) {
        viewModelScope.launch {
            platformRepo.updateRemoteControl(config)
                .onSuccess {
                    _remoteControl.value = config
                    _adminActionMessage.value = "تم حفظ إعدادات التحكم عن بُعد ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل حفظ التحكم: ${err.localizedMessage}"
                }
        }
    }

    fun publishInternalUpdateNow(config: RemoteControlConfig) {
        val nextRev = maxOf(config.internalUpdateRevision + 1L, System.currentTimeMillis())
        val updated = config.copy(
            internalUpdateEnabled = true,
            internalUpdateRevision = nextRev,
            internalUpdateDurationSec = config.internalUpdateDurationSec.coerceIn(3, 30)
        )
        viewModelScope.launch {
            platformRepo.updateRemoteControl(updated)
                .onSuccess {
                    _remoteControl.value = updated
                    _adminActionMessage.value = "تم نشر التحديث الداخلي المباشر للجميع بنجاح ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "تعذر نشر التحديث الداخلي: ${err.localizedMessage}"
                }
        }
    }

    fun publishExperimentalLayoutNow(config: RemoteControlConfig) {
        val updated = config.copy(experimentalLayoutEnabled = true)
        viewModelScope.launch {
            platformRepo.updateRemoteControl(updated)
                .onSuccess {
                    _remoteControl.value = updated
                    _adminActionMessage.value = "تم نشر التصميم التجريبي الجديد (${updated.experimentalLayoutMode}) للجميع بنجاح ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "تعذر نشر التصميم التجريبي: ${err.localizedMessage ?: "تأكد من الاتصال وصلاحيات المشرف"}"
                }
        }
    }

    fun savePromoBanner(banner: PromoBannerConfig) {
        viewModelScope.launch {
            platformRepo.updatePromoBanner(banner)
                .onSuccess {
                    _promoBanner.value = banner
                    _adminActionMessage.value = "تم حفظ الإعلان الترويجي بنجاح ✓"
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل حفظ الإعلان: ${err.localizedMessage}"
                }
        }
    }

    fun updateUserAccessOrPoints(accountKey: String, points: Int, accessMode: String, trialDays: Int) {
        viewModelScope.launch {
            platformRepo.adminUpdateAccountControl(accountKey, points, accessMode, trialDays)
                .onSuccess {
                    _adminActionMessage.value = "تم تحديث حساب المستخدم والصلاحية ✓"
                    loadAdminDashboardData()
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل التحديث: ${err.localizedMessage}"
                }
        }
    }

    fun sendGroupGift(amount: Int, message: String) {
        viewModelScope.launch {
            platformRepo.adminCreateGroupGift(amount, message)
                .onSuccess {
                    _adminActionMessage.value = "تم إرسال هدية جماعية (+$amount نقطة) للجميع ✓"
                    syncTeacherAccountNow()
                }
                .onFailure { err ->
                    _adminActionMessage.value = "فشل إرسال الهدية: ${err.localizedMessage}"
                }
        }
    }

    fun toggleFeedbackReviewed(feedbackId: String, reviewed: Boolean) {
        viewModelScope.launch {
            platformRepo.adminMarkFeedbackReviewed(feedbackId, reviewed)
                .onSuccess {
                    loadAdminDashboardData()
                }
        }
    }

    fun adminDeleteParentReport(code: String) {
        viewModelScope.launch {
            platformRepo.adminDeleteParentReport(code)
                .onSuccess {
                    _adminActionMessage.value = "تم حذف تقرير ولي الأمر ($code) وإيقاف الرابط ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun signOut(context: Context) {
        val accKey = _teacherAccount.value?.accountKey.orEmpty()
        val devId = _deviceInstallId.value
        viewModelScope.launch {
            if (accKey.isNotBlank()) {
                platformRepo.unbindCurrentDeviceFromAccount(accKey)
            }
            if (devId.isNotBlank()) {
                platformRepo.releaseActivationCodeOnSignOut(devId)
            }
            signOutWithCleanup(
                context = context,
                scope = viewModelScope,
                onBeforeSignOut = {
                    stopCloudListeners()
                }
            )
        }
    }

    // ==================== Sub-Admins, Activation Codes, Teacher Chat, Name Requests, Comparisons ====================

    fun saveSubAdmin(record: SubAdminRecord) {
        viewModelScope.launch {
            repository.adminSaveSubAdmin(record)
                .onSuccess {
                    _adminActionMessage.value = "تم حفظ صلاحيات المشرف (${record.name}) ✓"
                    loadAdminDashboardData()
                }
                .onFailure { err ->
                    _adminActionMessage.value = "تعذر الحفظ: ${err.localizedMessage}"
                }
        }
    }

    fun deleteSubAdmin(uid: String) {
        viewModelScope.launch {
            repository.adminDeleteSubAdmin(uid)
                .onSuccess {
                    _adminActionMessage.value = "تمت إزالة المشرف ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun createActivationCode(
        customCode: String,
        assignedUsername: String,
        assignedPasswordPlain: String,
        notes: String
    ) {
        viewModelScope.launch {
            platformRepo.adminCreateActivationCode(customCode, assignedUsername, assignedPasswordPlain, notes)
                .onSuccess { rec ->
                    _adminActionMessage.value = "تم إنشاء كود التفعيل (${rec.code}) للحساب (${rec.assignedUsername}) ✓"
                    loadAdminDashboardData()
                }
                .onFailure { err ->
                    _adminActionMessage.value = "تعذر إنشاء الكود: ${err.localizedMessage}"
                }
        }
    }

    fun resetActivationCodeDevice(code: String) {
        viewModelScope.launch {
            platformRepo.adminResetActivationCodeDevice(code)
                .onSuccess {
                    _adminActionMessage.value = "تم فك ارتباط الكود ($code) عن الجهاز القديم ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun deleteActivationCode(code: String) {
        viewModelScope.launch {
            platformRepo.adminDeleteActivationCode(code)
                .onSuccess {
                    _adminActionMessage.value = "تم حذف الكود ($code) ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun claimActivationCode(
        code: String,
        deviceInstallId: String,
        onActivated: (ActivationCodeRecord) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.claimActivationCodeWithDeviceLock(code, deviceInstallId)
                .onSuccess { rec ->
                    _userActionMessage.value = "تم تفعيل الحساب (${rec.assignedUsername}) على هذا الجهاز بنجاح ✓"
                    onActivated(rec)
                    syncTeacherAccountNow()
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "تعذر التفعيل."
                }
            _busyOperation.value = false
        }
    }

    fun changePasswordWithCooldown(
        username: String,
        newPassword: String,
        lastPasswordChangeMillis: Long,
        onPasswordUpdated: (Long) -> Unit
    ) {
        val intervalDays = _remoteControl.value.passwordChangeIntervalDays.coerceIn(1, 90)
        val nowMs = platformRepo.getVerifiedNowMillis()
        val cooldownMs = intervalDays.toLong() * 86_400_000L
        if (lastPasswordChangeMillis > 0L && (nowMs - lastPasswordChangeMillis) < cooldownMs) {
            val remainingDays = ceil((cooldownMs - (nowMs - lastPasswordChangeMillis)) / 86_400_000.0).toInt().coerceAtLeast(1)
            _userActionMessage.value = "يمكنك تغيير كلمة المرور مرة كل $intervalDays أيام. المتبقي: $remainingDays يوم."
            return
        }
        val cleanUser = username.trim()
        val cleanPass = newPassword.trim()
        if (cleanUser.length < 2 || cleanPass.length < 4) {
            _userActionMessage.value = "أدخل اسم مستخدم صالح وكلمة مرور من 4 أحرف على الأقل."
            return
        }
        viewModelScope.launch {
            _busyOperation.value = true
            val ctx = _teacherContext.value
            platformRepo.ensureOrSyncTeacherAccount(
                username = cleanUser,
                teacherName = ctx.teacherName,
                specialtyId = ctx.specialtyId,
                gradeId = ctx.gradeId,
                remoteControl = _remoteControl.value,
                passwordOptional = cleanPass,
                deviceInstallId = _deviceInstallId.value
            ).onSuccess { acc ->
                _teacherAccount.value = acc
                onPasswordUpdated(nowMs)
                _userActionMessage.value = "تم تحديث كلمة المرور بنجاح ✓"
            }.onFailure { err ->
                _userActionMessage.value = err.localizedMessage ?: "تعذر تغيير كلمة المرور."
            }
            _busyOperation.value = false
        }
    }

    fun sendChatMessage(text: String) {
        sendChatMessageV2(text = text)
    }

    fun dmThreadIdFor(peerUid: String): String {
        val myUid = auth.currentUser?.uid ?: return ""
        return platformRepo.dmThreadIdFor(myUid, peerUid)
    }

    fun findChatUserByUsername(username: String, onResult: (Result<TeacherAccountRecord>) -> Unit) {
        viewModelScope.launch {
            _busyOperation.value = true
            val res = platformRepo.findTeacherByUsername(username)
            _busyOperation.value = false
            onResult(res)
        }
    }

    fun sendChatMessageV2(
        text: String,
        threadId: String = "ROOM",
        recipientAccountKey: String = "",
        recipientUid: String = "",
        recipientName: String = "",
        imageBase64: String = "",
        onSent: () -> Unit = {}
    ) {
        val ctx = _teacherContext.value
        val sender = ctx.teacherName.ifBlank { ctx.username.ifBlank { "أستاذ" } }
        viewModelScope.launch {
            platformRepo.sendSpecialtyChatMessage(
                senderName = sender,
                specialtyId = ctx.specialtyId,
                gradeId = ctx.gradeId,
                messageText = text,
                expiryHours = _remoteControl.value.chatExpiryHours,
                senderUsername = ctx.username,
                threadId = threadId,
                recipientAccountKey = recipientAccountKey,
                recipientUid = recipientUid,
                recipientName = recipientName,
                imageBase64 = imageBase64
            ).onSuccess { remaining ->
                _chatRemainingToday.value = remaining
                onSent()
            }.onFailure { err ->
                _userActionMessage.value = err.localizedMessage ?: "تعذر إرسال الرسالة."
            }
        }
    }

    fun editChatMessage(msgId: String, newText: String) {
        viewModelScope.launch {
            platformRepo.editChatMessage(msgId, newText)
                .onSuccess { _userActionMessage.value = "تم تعديل الرسالة ✓" }
                .onFailure { err -> _userActionMessage.value = err.localizedMessage ?: "تعذر تعديل الرسالة." }
        }
    }

    fun deleteChatMessageForMe(msgId: String) {
        viewModelScope.launch {
            platformRepo.deleteChatMessageForMe(msgId)
                .onFailure { err -> _userActionMessage.value = err.localizedMessage ?: "تعذر حذف الرسالة." }
        }
    }

    fun deleteChatMessageForEveryone(msg: TeacherChatMessageRecord) {
        viewModelScope.launch {
            platformRepo.deleteChatMessageForEveryone(msg)
                .onSuccess { markedNotice ->
                    _userActionMessage.value = if (markedNotice) {
                        "تم استبدال الرسالة بـ (تم حذف رسالة) لدى الجميع"
                    } else {
                        "تم حذف الرسالة نهائياً لأن الطرف الآخر لم يقرأها بعد ✓"
                    }
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "تعذر حذف الرسالة للجميع."
                }
        }
    }

    fun markMessagesDeliveredAndRead(messages: List<TeacherChatMessageRecord>, markRead: Boolean) {
        viewModelScope.launch {
            platformRepo.markMessagesDeliveredAndRead(messages, markRead)
        }
    }

    fun adminPurgeExpiredChats() {
        viewModelScope.launch {
            platformRepo.adminPurgeExpiredChatMessages()
                .onSuccess { count ->
                    _adminActionMessage.value = "تم تنظيف $count رسالة منتهية الصلاحية من الدردشة ✓"
                }
        }
    }

    fun requestTeacherNameChange(
        currentName: String,
        requestedName: String,
        initialChosen: Boolean,
        lastNameChangeMillis: Long,
        onDirectApplied: (String) -> Unit
    ) {
        val cleanNew = requestedName.trim()
        if (cleanNew.length < 2) {
            _userActionMessage.value = "يرجى كتابة الاسم الكامل (حرفين على الأقل)."
            return
        }
        if (!initialChosen) {
            onDirectApplied(cleanNew)
            _userActionMessage.value = "تم اعتماد اسمك الأول بنجاح ✓"
            return
        }
        val rc = _remoteControl.value
        val acc = _teacherAccount.value
        val hasWeeklyGift = rc.allowWeeklyNameChange || (acc?.allowWeeklyNameChange == true)
        val nowMs = platformRepo.getVerifiedNowMillis()
        if (hasWeeklyGift && (lastNameChangeMillis <= 0L || (nowMs - lastNameChangeMillis) >= 7L * 86_400_000L)) {
            onDirectApplied(cleanNew)
            _userActionMessage.value = "تم تبديل الاسم باستخدام ميزة التبديل الأسبوعي ✓"
            return
        }
        val accKey = acc?.accountKey ?: auth.currentUser?.uid?.let { platformRepo.resolveAccountKey(_teacherContext.value.username, it) }.orEmpty()
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.submitNameChangeRequest(accKey, currentName, cleanNew)
                .onSuccess {
                    _userActionMessage.value = "تم إرسال طلب تغيير الاسم للمراجعة، وسيتم إشعارك فور الموافقة ✓"
                }
                .onFailure { err ->
                    _userActionMessage.value = err.localizedMessage ?: "تعذر إرسال الطلب."
                }
            _busyOperation.value = false
        }
    }

    fun decideNameChangeRequest(req: NameChangeRequestRecord, approve: Boolean) {
        viewModelScope.launch {
            platformRepo.adminDecideNameChangeRequest(req, approve)
                .onSuccess {
                    if (req.requesterUid.isNotBlank()) {
                        repository.createAnnouncement(
                            title = if (approve) "تمت الموافقة على تغيير الاسم" else "تم رفض طلب تغيير الاسم",
                            body = if (approve) {
                                "تم اعتماد الاسم الجديد (${req.requestedName}) لحسابك بنجاح."
                            } else {
                                "تعذر اعتماد الاسم المطلوب (${req.requestedName}) حالياً."
                            },
                            priority = AnnouncementPriority.IMPORTANT,
                            authorName = "الإدارة",
                            targetType = NotificationTargetType.USER,
                            targetValue = req.requesterUid
                        )
                    }
                    _adminActionMessage.value = if (approve) "تمت الموافقة على تغيير الاسم وإشعار المدرس ✓" else "تم رفض الطلب ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun updateUserExtendedControl(
        accountKey: String,
        points: Int,
        accessMode: String,
        trialDays: Int,
        promotionTitle: String,
        allowWeeklyNameChange: Boolean
    ) {
        viewModelScope.launch {
            platformRepo.adminUpdateUserExtendedControl(
                accountKey = accountKey,
                points = points,
                accessMode = accessMode,
                trialDays = trialDays,
                promotionTitle = promotionTitle,
                allowWeeklyNameChange = allowWeeklyNameChange
            ).onSuccess {
                _adminActionMessage.value = "تم تحديث بيانات وصلاحيات المستخدم ✓"
                loadAdminDashboardData()
            }.onFailure { err ->
                _adminActionMessage.value = "فشل التحديث: ${err.localizedMessage}"
            }
        }
    }

    fun adminDeleteUserAccount(accountKey: String) {
        viewModelScope.launch {
            platformRepo.adminDeleteTeacherAccount(accountKey)
                .onSuccess {
                    _adminActionMessage.value = "تم حذف سجل الحساب ($accountKey) ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun adminUnbindUserDevice(accountKey: String) {
        viewModelScope.launch {
            platformRepo.adminUnbindAccountDevice(accountKey)
                .onSuccess {
                    _adminActionMessage.value = "تم فك ارتباط جهاز المستخدم ليتمكن من الدخول من هاتف آخر ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun autoPromoteTopActiveTeachers(rankTitle: String) {
        val topUsers = _adminAllUsers.value
            .filter { it.totalUsageSeconds > 0L }
            .sortedByDescending { it.totalUsageSeconds }
            .take(3)
        if (topUsers.isEmpty()) {
            _adminActionMessage.value = "لا يوجد مستخدمون نشطون للترقية التلقائية بعد."
            return
        }
        viewModelScope.launch {
            val cleanRank = rankTitle.trim().ifBlank { "معلم متميز" }
            topUsers.forEach { acc ->
                platformRepo.adminUpdateUserExtendedControl(
                    accountKey = acc.accountKey,
                    points = acc.points + 25,
                    accessMode = acc.accessMode,
                    trialDays = acc.trialDays,
                    promotionTitle = cleanRank,
                    allowWeeklyNameChange = true
                )
            }
            _adminActionMessage.value = "تمت ترقية أكثر ${topUsers.size} مدرسين تفاعلاً إلى ($cleanRank) + 25 نقطة ✓"
            loadAdminDashboardData()
        }
    }

    fun publishGradeComparison(
        creatorName: String,
        creatorSubject: String,
        creatorGradeTitle: String,
        creatorSectionScope: String,
        creatorStatsJson: String,
        onCreated: (GradeComparisonRecord) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.publishGradeComparison(
                creatorName = creatorName,
                creatorSubject = creatorSubject,
                creatorGradeTitle = creatorGradeTitle,
                creatorSectionScope = creatorSectionScope,
                creatorStatsJson = creatorStatsJson
            ).onSuccess { rec ->
                _userActionMessage.value = "تم إنشاء كود المقارنة (${rec.code}) بنجاح ✓"
                onCreated(rec)
            }.onFailure { err ->
                _userActionMessage.value = err.localizedMessage ?: "تعذر إنشاء كود المقارنة."
            }
            _busyOperation.value = false
        }
    }

    fun joinGradeComparison(
        code: String,
        partnerName: String,
        partnerSubject: String,
        partnerGradeTitle: String,
        partnerSectionScope: String,
        partnerStatsJson: String,
        onJoined: (GradeComparisonRecord) -> Unit
    ) {
        viewModelScope.launch {
            _busyOperation.value = true
            platformRepo.joinGradeComparisonByCode(
                codeRaw = code,
                partnerName = partnerName,
                partnerSubject = partnerSubject,
                partnerGradeTitle = partnerGradeTitle,
                partnerSectionScope = partnerSectionScope,
                partnerStatsJson = partnerStatsJson
            ).onSuccess { rec ->
                _userActionMessage.value = "تم إرسال طلب المقارنة وبانتظار موافقة الإدارة ✓"
                onJoined(rec)
            }.onFailure { err ->
                _userActionMessage.value = err.localizedMessage ?: "تعذر الانضمام لكود المقارنة."
            }
            _busyOperation.value = false
        }
    }

    fun decideGradeComparison(code: String, approve: Boolean) {
        viewModelScope.launch {
            platformRepo.adminDecideGradeComparison(code, approve)
                .onSuccess {
                    _adminActionMessage.value = if (approve) "تمت الموافقة على مقارنة الدرجات ($code) ✓" else "تم رفض طلب المقارنة ($code) ✓"
                    loadAdminDashboardData()
                }
        }
    }

    fun deleteGradeComparison(code: String) {
        viewModelScope.launch {
            platformRepo.deleteGradeComparison(code)
                .onSuccess {
                    _adminActionMessage.value = "تم حذف المقارنة ($code) ✓"
                    loadAdminDashboardData()
                }
        }
    }

    // ==================== Shared Teacher PDF Study Guides ====================

    fun uploadSharedPdfGuide(
        title: String,
        subjectTitle: String,
        gradeId: String,
        teacherName: String,
        description: String,
        fileName: String,
        pdfBytes: ByteArray,
        onResult: (Result<SharedStudyGuideRecord>) -> Unit
    ) {
        viewModelScope.launch {
            _studyGuideBusy.value = true
            val res = withContext(Dispatchers.IO) {
                platformRepo.uploadSharedPdfStudyGuide(
                    title = title,
                    subjectTitle = subjectTitle,
                    gradeId = gradeId,
                    teacherName = teacherName,
                    description = description,
                    fileName = fileName,
                    pdfBytes = pdfBytes
                )
            }
            res.onSuccess {
                _userActionMessage.value = "تم نشر الملزمة بصيغة PDF بنجاح ✓"
            }.onFailure { err ->
                _userActionMessage.value = err.localizedMessage ?: "تعذر رفع الملزمة."
            }
            _studyGuideBusy.value = false
            onResult(res)
        }
    }

    fun downloadSharedPdfGuide(
        guide: SharedStudyGuideRecord,
        onReady: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _studyGuideBusy.value = true
            val dir = File(appContext.filesDir, "library/shared_guides").apply { mkdirs() }
            val safeFile = File(dir, "guide_${guide.id.take(24)}.pdf")
            val res = withContext(Dispatchers.IO) {
                platformRepo.downloadSharedPdfToFile(guide, safeFile)
            }
            _studyGuideBusy.value = false
            res.onSuccess { file -> onReady(file) }
                .onFailure { err -> onError(err.localizedMessage ?: "تعذر تحميل ملف PDF.") }
        }
    }

    fun deleteSharedPdfGuide(guideId: String) {
        viewModelScope.launch {
            _studyGuideBusy.value = true
            platformRepo.deleteSharedStudyGuide(guideId)
                .onSuccess { _userActionMessage.value = "تم حذف الملزمة ✓" }
                .onFailure { err -> _userActionMessage.value = err.localizedMessage ?: "تعذر الحذف." }
            _studyGuideBusy.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCloudListeners()
        internalBannerTimerJob?.cancel()
    }
}
