package com.peoplehub.core.sync

import com.peoplehub.core.database.sync.SyncCollections
import com.peoplehub.core.domain.model.SyncAccount
import com.peoplehub.core.domain.model.SyncStatus
import com.peoplehub.core.domain.repository.CloudSyncRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import com.peoplehub.core.domain.sync.CloudSyncScheduler
import com.peoplehub.core.domain.sync.InitialSyncDecision
import com.peoplehub.core.domain.sync.RecurringWorkRescheduler
import com.peoplehub.core.domain.widget.WidgetRefresher
import com.peoplehub.core.sync.di.SyncScope
import com.peoplehub.core.sync.engine.LocalSyncStore
import com.peoplehub.core.sync.engine.Puller
import com.peoplehub.core.sync.engine.Pusher
import com.peoplehub.core.sync.engine.RemoteStore
import com.peoplehub.core.sync.engine.SettingsSync
import com.peoplehub.core.sync.engine.SyncFields
import com.peoplehub.core.sync.prefs.SyncPreferences
import com.peoplehub.core.sync.remote.CloudAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sync engine behind [CloudSyncRepository].
 *
 * One operation runs at a time ([operationLock]): upload, download or a sync (pull, then push, so a
 * newer cloud version wins over an older pending local change before anything goes up). While the
 * app is alive it also watches, for the signed-in account whose first sync is done:
 * - the outbox: any pending local change asks the [CloudSyncScheduler] for a background sync;
 * - the settings: a change is time-stamped and synced the same way;
 * - in the foreground only, Firestore listeners: a change written by someone else asks for a sync.
 * The scheduler collapses all those requests into one WorkManager job, which calls [syncNow].
 */
@Singleton
internal class CloudSyncRepositoryImpl
    @Inject
    constructor(
        private val auth: CloudAuth,
        private val remote: RemoteStore,
        private val local: LocalSyncStore,
        private val prefs: SyncPreferences,
        private val settingsRepository: SettingsRepository,
        private val scheduler: CloudSyncScheduler,
        private val rescheduler: RecurringWorkRescheduler,
        private val widgetRefresher: WidgetRefresher,
        private val clock: Clock,
        @SyncScope private val scope: CoroutineScope,
    ) : CloudSyncRepository {
        /** Whether the cloud of [uid] has data (`null` while checking or after a failed check). */
        private data class CloudCheck(
            val uid: String,
            val hasData: Boolean?,
        )

        private class Engine(
            val ownTag: String,
            val puller: Puller,
            val pusher: Pusher,
            val settings: SettingsSync,
        )

        private val operationLock = Mutex()
        private val engineLock = Mutex()
        private var engine: Engine? = null
        private val syncing = MutableStateFlow(false)
        private val cloudCheck = MutableStateFlow<CloudCheck?>(null)
        private val foreground = MutableStateFlow(false)

        /** The account whose automatic sync is on (signed in and first sync done), or `null`. */
        private val activeAccount: Flow<SyncAccount?> =
            combine(auth.account, prefs.snapshot.map { it.initialDoneUids }) { account, done ->
                account?.takeIf { it.uid in done }
            }.distinctUntilChanged()

        override val status: Flow<SyncStatus> =
            combine(
                auth.account,
                prefs.snapshot,
                local.observePendingCount(),
                cloudCheck,
                syncing,
            ) { account, saved, pending, check, busy ->
                SyncStatus(
                    account = account,
                    initialAction =
                        InitialSyncDecision.resolve(
                            signedIn = account != null,
                            initialSyncDone = account != null && account.uid in saved.initialDoneUids,
                            cloudHasData = check?.takeIf { it.uid == account?.uid }?.hasData,
                        ),
                    isSyncing = busy,
                    lastSyncAt = saved.lastSyncAt?.let(Instant::ofEpochMilli),
                    pendingChanges = pending,
                    lastError = saved.lastError,
                )
            }

        init {
            watchAccount()
            watchLocalChanges()
            watchRealtime()
        }

        override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
            resultOf { auth.signInWithGoogle(idToken) }
                .onFailure { prefs.recordError(it.message ?: it.javaClass.simpleName) }

        override suspend fun signOut() {
            auth.signOut()
            cloudCheck.value = null
        }

        override suspend fun uploadAll(): Result<Unit> =
            operation { uid, engine ->
                local.enqueueEverything(clock.millis())
                engine.pusher.push(uid)
                engine.settings.push(uid, force = true)
                prefs.setInitialDone(uid)
            }

        override suspend fun downloadAll(): Result<Unit> =
            operation { uid, engine ->
                local.wipeForDownload()
                prefs.resetAccount(uid)
                // A child can arrive before its parent; repeat until nothing is deferred (bounded).
                var rounds = 0
                while (engine.puller.pull(uid).deferred > 0 && ++rounds <= Puller.DEFAULT_MAX_DEFERRALS) Unit
                engine.settings.pull(uid, force = true)
                prefs.setInitialDone(uid)
                widgetRefresher.refresh()
                rescheduler.reschedule()
            }

        override suspend fun syncNow(): Result<Unit> {
            val account = auth.current() ?: return Result.success(Unit)
            if (!prefs.isInitialDone(account.uid)) return Result.success(Unit)
            return operation { uid, engine ->
                val pulled = engine.puller.pull(uid)
                val settingsChanged = engine.settings.pull(uid)
                engine.pusher.push(uid)
                engine.settings.push(uid)
                if (pulled.applied > 0 || settingsChanged) widgetRefresher.refresh()
                if (settingsChanged) rescheduler.reschedule()
            }
        }

        override suspend fun refreshCloudCheck() {
            auth.current()?.let { checkCloud(it.uid) }
        }

        override fun setForeground(foreground: Boolean) {
            this.foreground.value = foreground
        }

        private suspend fun operation(block: suspend (uid: String, engine: Engine) -> Unit): Result<Unit> {
            val account = auth.current() ?: return Result.failure(IllegalStateException("Not signed in"))
            return operationLock.withLock {
                syncing.value = true
                try {
                    resultOf { block(account.uid, engine()) }
                        .onSuccess { prefs.recordSuccess(clock.millis()) }
                        .onFailure { prefs.recordError(it.message ?: it.javaClass.simpleName) }
                } finally {
                    syncing.value = false
                }
            }
        }

        private suspend fun checkCloud(uid: String) {
            cloudCheck.value = CloudCheck(uid, null)
            resultOf { remote.hasData(uid) }
                .onSuccess { cloudCheck.value = CloudCheck(uid, it) }
                .onFailure { prefs.recordError(it.message ?: it.javaClass.simpleName) }
        }

        /** After a sign-in that still needs its first sync, find out whether the cloud has data. */
        private fun watchAccount() {
            auth.account
                .onEach { account -> if (account != null && !prefs.isInitialDone(account.uid)) checkCloud(account.uid) }
                .launchIn(scope)
        }

        /** Pending outbox entries or a settings change ask for a background sync. */
        private fun watchLocalChanges() {
            combine(activeAccount, local.observePendingCount()) { account, pending -> account != null && pending > 0 }
                .onEach { if (it) scheduler.requestSync() }
                .launchIn(scope)
            combine(activeAccount, settingsRepository.settings) { account, _ -> account }
                .onEach { account ->
                    if (account != null && engine().settings.hasLocalChange(account.uid)) {
                        prefs.markSettingsChanged(clock.millis())
                        scheduler.requestSync()
                    }
                }.launchIn(scope)
        }

        /** In the foreground, listen to the cloud and sync whenever someone else changes it. */
        private fun watchRealtime() {
            scope.launch {
                combine(foreground, activeAccount) { visible, account -> account?.takeIf { visible } }
                    .distinctUntilChanged()
                    .collectLatest { account ->
                        if (account == null) return@collectLatest
                        scheduler.requestSync()
                        val cursors = (SyncCollections.ORDERED + SyncCollections.SETTINGS).associateWith { prefs.cursor(account.uid, it) }
                        val registration = remote.listen(account.uid, cursors, engine().ownTag) { scheduler.requestSync() }
                        try {
                            awaitCancellation()
                        } finally {
                            registration.close()
                        }
                    }
            }
        }

        private suspend fun engine(): Engine =
            engineLock.withLock {
                engine ?: run {
                    val tag = SyncFields.androidTag(prefs.installId())
                    Engine(
                        ownTag = tag,
                        puller = Puller(remote, local, prefs, tag),
                        pusher = Pusher(remote, local, tag),
                        settings = SettingsSync(remote, settingsRepository, prefs, tag, { ZoneId.systemDefault().id }, clock::millis),
                    ).also { engine = it }
                }
            }

        /** Like [runCatching], but never swallows coroutine cancellation. */
        private inline fun <T> resultOf(block: () -> T): Result<T> =
            runCatching(block).onFailure { if (it is CancellationException) throw it }
    }
