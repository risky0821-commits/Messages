#!/usr/bin/env python3
"""Apply the minimal Sama QUIK category experiment to a pinned GPLv3 QUIK checkout.

This script only touches the extracted QUIK source used for the experiment.
It does not change the Fossify-derived Sama Android application.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
def patch(filename, old, new, count=1):
    p = root / filename
    text = p.read_text()
    found = text.count(old)
    if found != count:
        raise SystemExit(f"Cannot patch {filename}: expected {count} matches, got {found}: {old[:65]!r}")
    p.write_text(text.replace(old, new, count))

prefix = "presentation/src/main/"
main = prefix + "java/com/moez/QKSMS/feature/main/MainActivity.kt"
model = "domain/src/main/java/com/moez/QKSMS/model/Conversation.kt"
migration = "data/src/main/java/com/moez/QKSMS/migration/QkRealmMigration.kt"
sync = "data/src/main/java/com/moez/QKSMS/repository/SyncRepositoryImpl.kt"
layout = prefix + "res/layout/main_activity.xml"
gradle = "presentation/build.gradle"

# A distinct Android application to prevent replacing either QUIK or Sama Fossify.
patch(gradle, "applicationId 'dev.octoshrimpy.quik'", "applicationId 'dev.sama.quik'")
patch(gradle, 'resValue("string", "app_name", "QUIK-Debug")', 'resValue("string", "app_name", "Sama QUIK Fast Tabs Test")')
patch(gradle, 'applicationIdSuffix ".debug"', 'applicationIdSuffix ".debug.smooth"')

# Stable metadata lives in Realm, separately from message contents.
patch(model, "    @Index var pinned: Boolean = false,\n",
      "    @Index var pinned: Boolean = false,\n    @Index var samaCategoryId: Long = 0,\n")
patch(migration, "const val SCHEMA_VERSION: Long = 15", "const val SCHEMA_VERSION: Long = 16")
patch(migration, '        check(version >= SCHEMA_VERSION) {',
      '''        if (version == 15L) {
            realm.schema.get("Conversation")
                ?.addField("samaCategoryId", Long::class.java, FieldAttribute.REQUIRED, FieldAttribute.INDEXED)
            version++
        }

        check(version >= SCHEMA_VERSION) {''')
patch(sync, '                        .isNotNull("blockingClient")',
      '''                        .isNotNull("blockingClient")
                        .or()
                        .notEqualTo("samaCategoryId", 0L)''')
patch(sync, '                                    pinned = persistedConversation.pinned',
      '''                                    pinned = persistedConversation.pinned
                                    samaCategoryId = persistedConversation.samaCategoryId''')

# Modern top-of-list category chips. Keep original sender list and search unmodified.
patch(layout, '            app:layout_constraintBottom_toTopOf="@id/bottom"\n            app:layout_constraintTop_toBottomOf="@id/toolbar"',
      '            app:layout_constraintBottom_toTopOf="@id/bottom"\n            app:layout_constraintTop_toBottomOf="@id/samaCategoryTabs"', count=2)
patch(layout, '''        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerView"''',
      '''        <HorizontalScrollView
            android:id="@+id/samaCategoryTabs"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:fillViewport="false"
            android:scrollbars="none"
            app:layout_constraintTop_toBottomOf="@id/toolbar">

            <LinearLayout
                android:id="@+id/samaCategoryRow"
                android:layout_width="wrap_content"
                android:layout_height="match_parent"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:paddingStart="8dp"
                android:paddingEnd="8dp" />
        </HorizontalScrollView>

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerView"''')
# MainActivity uses a single live RealmResults adapter; constrain its query by category.
patch(main, 'import android.content.Intent\n',
      '''import android.content.Intent
import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.view.MotionEvent
import android.graphics.Rect
import kotlin.math.abs
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import org.json.JSONObject
import io.realm.Realm
import io.realm.RealmResults
import io.realm.RealmChangeListener
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
''')
patch(main, '    private lateinit var binding: MainActivityBinding',
      '''    private val samaPrefs by lazy { getSharedPreferences("sama_quik_categories", Context.MODE_PRIVATE) }
    private var samaSelectedCategoryId = 0L
    private var samaSource: RealmResults<Conversation>? = null
    // Keep each tab's live Realm result instead of restarting an async query on every click.
    private val samaTabResults = mutableMapOf<Long, RealmResults<Conversation>>()
    private val samaCategoryChips = mutableMapOf<Long, TextView>()
    private var samaUnreadChip: TextView? = null
    private var samaCountRealm: Realm? = null
    private var samaUnreadMessages: RealmResults<Message>? = null
    private var samaSwipeStartX = 0f
    private var samaSwipeStartY = 0f
    private var samaSwipeCandidate = false
    private var samaSwipeCaptured = false
    private var samaCanSwipeTabs = false
    private val samaMessageListener = RealmChangeListener<RealmResults<Message>> {
        samaUpdateUnreadCount()
    }
    private val samaSourceListener = RealmChangeListener<RealmResults<Conversation>> {
        samaUpdateUnreadCount()
    }
    private var samaSelectedIds: List<Long> = emptyList()
    private val samaMoveMenuId = 0x53414d41

    private fun samaCategories(): List<Pair<Long, String>> {
        val json = runCatching { JSONArray(samaPrefs.getString("categories", "[]")) }
            .getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { index ->
            runCatching {
                val item = json.getJSONObject(index)
                item.getLong("id") to item.getString("name")
            }.getOrNull()
        }
    }

    private fun samaSaveCategories(categories: List<Pair<Long, String>>) {
        val json = JSONArray()
        categories.forEach { (id, name) ->
            json.put(JSONObject().put("id", id).put("name", name))
        }
        samaPrefs.edit().putString("categories", json.toString()).apply()
    }

    private fun samaNewCategory() {
        val entry = EditText(this).apply {
            hint = "اسم الفئة"
            setSingleLine(true)
            setPadding(samaDp(20), samaDp(12), samaDp(20), samaDp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("فئة جديدة")
            .setView(entry)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إنشاء") { _, _ ->
                val title = entry.text.toString().trim()
                if (title.isNotEmpty()) {
                    val nextId = (samaCategories().maxOfOrNull { it.first } ?: 0L) + 1L
                    samaSaveCategories(samaCategories() + (nextId to title))
                    samaSelectCategory(nextId)
                }
            }
            .show()
    }

    private fun samaDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun samaChip(title: String, active: Boolean, onClick: () -> Unit): TextView {
        val tint = if (active) Color.rgb(75, 78, 88) else Color.TRANSPARENT
        return TextView(this).apply {
            text = title
            textSize = 12.5f
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(samaDp(12), samaDp(6), samaDp(12), samaDp(6))
            background = GradientDrawable().apply {
                cornerRadius = samaDp(20).toFloat()
                setColor(tint)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, samaDp(36)
            ).apply { marginEnd = samaDp(4) }
            setOnClickListener { onClick() }
        }
    }

    private fun samaUpdateUnreadCount() {
        val messages = samaUnreadMessages
        val conversations = samaCurrentInbox
        if (messages?.isLoaded != true || conversations?.isLoaded != true) return
        val categoryByThread = conversations.associate { it.id to it.samaCategoryId }
        val counts = mutableMapOf<Long, Int>()
        var total = 0
        messages.forEach { message ->
            val category = categoryByThread[message.threadId] ?: return@forEach
            total++
            counts[category] = (counts[category] ?: 0) + 1
        }
        samaCategoryChips[0L]?.text = "الكل ($total)"
        samaUnreadChip?.text = "غير مقروءة ($total)"
        samaCategories().forEach { (id, name) ->
            samaCategoryChips[id]?.text = "$name (${counts[id] ?: 0})"
        }
    }

    private fun samaRefreshChips() {
        samaCategoryChips.clear()
        binding.samaCategoryRow.apply {
            removeAllViews()
            addView(samaChip("الكل", samaSelectedCategoryId == 0L) {
                samaSelectCategory(0L)
            }.also { samaCategoryChips[0L] = it })
            samaUnreadChip = samaChip("غير مقروءة", samaSelectedCategoryId == -1L) {
                samaSelectCategory(-1L)
            }.also { addView(it) }
            samaCategories().forEach { (id, name) ->
                addView(samaChip(name, samaSelectedCategoryId == id) {
                    samaSelectCategory(id)
                }.also { samaCategoryChips[id] = it })
            }
            addView(samaChip("+", false) { samaNewCategory() })
        }
        samaUpdateUnreadCount()
    }

    private fun samaHighlightSelectedChip() {
        val chips = samaCategoryChips + listOfNotNull(samaUnreadChip?.let { -1L to it })
        chips.forEach { (id, chip) ->
            val active = id == samaSelectedCategoryId
            chip.setTypeface(chip.typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            chip.background = GradientDrawable().apply {
                cornerRadius = samaDp(18).toFloat()
                setColor(if (active) Color.rgb(75, 78, 88) else Color.TRANSPARENT)
            }
        }
        chips[samaSelectedCategoryId]?.let { selected ->
            binding.samaCategoryTabs.post {
                binding.samaCategoryTabs.smoothScrollTo(
                    (selected.left + selected.width / 2 - binding.samaCategoryTabs.width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    private fun samaVisibleConversations(source: RealmResults<Conversation>?): RealmResults<Conversation>? {
        if (source == null) return null
        if (samaSource !== source) {
            samaSource?.removeChangeListener(samaSourceListener)
            samaSource = source
            samaTabResults.clear()
            source.addChangeListener(samaSourceListener)
            samaTabResults[-1L] = source.where()
                .equalTo("lastMessage.read", false).findAllAsync()
            samaUpdateUnreadCount()
        }
        if (samaSelectedCategoryId == 0L) return source
        return samaTabResults.getOrPut(samaSelectedCategoryId) {
            // Live Realm results update automatically for incoming messages and read changes.
            val query = source.where()
            if (samaSelectedCategoryId == -1L) {
                query.equalTo("lastMessage.read", false)
            } else {
                query.equalTo("samaCategoryId", samaSelectedCategoryId)
            }
            query.findAllAsync()
        }
    }

    private fun samaSelectCategory(id: Long) {
        if (id == samaSelectedCategoryId) return
        samaSelectedCategoryId = id
        conversationsAdapter.clearSelection()
        samaHighlightSelectedChip()
        if (binding.recyclerView.adapter === conversationsAdapter) {
            conversationsAdapter.updateData(samaVisibleConversations(samaCurrentInbox))
        }
    }

    private var samaCurrentInbox: RealmResults<Conversation>? = null

    private fun samaEnsureCounterQuery() {
        if (samaUnreadMessages != null) return
        val realm = Realm.getDefaultInstance()
        samaCountRealm = realm
        val unread = realm.where(Message::class.java)
            .equalTo("read", false)
            .beginGroup()
            .beginGroup()
            .equalTo("type", Message.TYPE_SMS)
            .equalTo("boxId", android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX)
            .endGroup()
            .or()
            .beginGroup()
            .equalTo("type", Message.TYPE_MMS)
            .equalTo("boxId", android.provider.Telephony.Mms.MESSAGE_BOX_INBOX)
            .endGroup()
            .endGroup()
            .findAllAsync()
        samaUnreadMessages = unread
        unread.addChangeListener(samaMessageListener)
        samaUpdateUnreadCount()
    }

    private fun samaMoveSelected() {
        val selected = samaSelectedIds.distinct()
        if (selected.isEmpty()) return
        val categories = samaCategories()
        val labels = arrayOf("بدون فئة", *categories.map { it.second }.toTypedArray())
        AlertDialog.Builder(this)
            .setTitle("نقل المحادثات إلى")
            .setItems(labels) { _, index ->
                val destination = if (index == 0) 0L else categories[index - 1].first
                conversationsAdapter.clearSelection()
                io.reactivex.Single.fromCallable {
                    Realm.getDefaultInstance().use { realm ->
                        realm.executeTransaction {
                            realm.where(Conversation::class.java)
                                .`in`("id", selected.toTypedArray()).findAll()
                                .forEach { it.samaCategoryId = destination }
                        }
                    }
                    true
                }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { samaRefreshChips() },
                        { error ->
                            android.widget.Toast.makeText(this, "تعذر نقل المحادثات: " + error.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    ).also { disposables.add(it) }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private lateinit var binding: MainActivityBinding''')
patch(main, '        setContentView(binding.root)\n        viewModel.bindView(this)',
      '''        setContentView(binding.root)
        samaRefreshChips()
        disposables.add(conversationsAdapter.selectionChanges.subscribe {
            samaSelectedIds = it.toList()
        })
        viewModel.bindView(this)''')
patch(main, '            findItem(R.id.rename)?.isVisible = selectedConversations == 1',
      '''            findItem(R.id.rename)?.isVisible = selectedConversations == 1
            findItem(samaMoveMenuId)?.isVisible = state.page is Inbox && selectedConversations != 0''')
patch(main, '                conversationsAdapter.updateData(state.page.data)\n                itemTouchHelper.attachToRecyclerView(binding.recyclerView)',
      '''                samaCanSwipeTabs = state.page.selected == 0
                samaCurrentInbox = state.page.data
                samaEnsureCounterQuery()
                conversationsAdapter.updateData(samaVisibleConversations(state.page.data))
                itemTouchHelper.attachToRecyclerView(binding.recyclerView)''')
patch(main, '    override fun onCreateOptionsMenu(menu: Menu?) =\n        menu?.let {\n            menuInflater.inflate(R.menu.main, it)',
      '''    override fun onCreateOptionsMenu(menu: Menu?) =
        menu?.let {
            menuInflater.inflate(R.menu.main, it)
            it.add(Menu.NONE, samaMoveMenuId, Menu.NONE, "نقل إلى فئة")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)''')
patch(main, '    override fun onOptionsItemSelected(item: MenuItem) =\n        optionsItemIntent.onNext(item.itemId).let { true }',
      '''    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == samaMoveMenuId) {
            samaMoveSelected()
            return true
        }
        optionsItemIntent.onNext(item.itemId)
        return true
    }''')


# Replace the unread dot with a count in its existing accent colour.
rowLayout = prefix + "res/layout/conversation_list_item.xml"
rowAdapter = prefix + "java/com/moez/QKSMS/feature/conversations/ConversationsAdapter.kt"
patch(rowLayout, '''    <ImageView
        android:id="@+id/unread"
        android:layout_width="20dp"
        android:layout_height="16sp"
        android:paddingStart="8dp"
        android:paddingTop="2dp"
        android:paddingBottom="2dp"
        android:src="@drawable/circle"
        android:visibility="gone"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/snippet"
        tools:tint="@color/tools_theme"
        tools:visibility="visible" />''',
      '''    <TextView
        android:id="@+id/unread"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:minWidth="20dp"
        android:gravity="center"
        android:textSize="12sp"
        android:textStyle="bold"
        android:visibility="gone"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/snippet"
        tools:text="3"
        tools:visibility="visible" />''')
patch(rowAdapter, 'import dev.octoshrimpy.quik.model.Conversation\n',
      '''import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import io.realm.Realm
''')
patch(rowAdapter, '''        binding.pinned.isVisible = conversation.pinned
        binding.unread.setTint(theme)''',
      '''        binding.pinned.isVisible = conversation.pinned
        binding.unread.setTextColor(theme)
        if (conversation.unread) {
            // The badge counts unread SMS/MMS within this conversation, not the number of threads.
            val unreadCount = Realm.getDefaultInstance().use { realm ->
                realm.where(Message::class.java)
                    .equalTo("threadId", conversation.id)
                    .equalTo("read", false)
                    .beginGroup()
                    .equalTo("type", Message.TYPE_SMS)
                    .equalTo("boxId", android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX)
                    .or()
                    .beginGroup()
                    .equalTo("type", Message.TYPE_MMS)
                    .equalTo("boxId", android.provider.Telephony.Mms.MESSAGE_BOX_INBOX)
                    .endGroup()
                    .endGroup()
                    .count()
            }
            binding.unread.text = if (unreadCount > 99L) "99+" else unreadCount.coerceAtLeast(1L).toString()
        } else {
            binding.unread.text = ""
        }''')


# Long horizontal gesture navigates tabs. Short row swipes keep native QUIK actions.
patch(main, '    override fun onNewIntent(intent: Intent?) =',
      '''    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!::binding.isInitialized) return super.dispatchTouchEvent(event)
        val list = binding.recyclerView
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                list.animate().cancel()
                list.translationX = 0f
                samaSwipeCaptured = false
                samaSwipeStartX = event.rawX
                samaSwipeStartY = event.rawY
                val bounds = Rect()
                samaSwipeCandidate = samaCanSwipeTabs &&
                    list.getGlobalVisibleRect(bounds) &&
                    bounds.contains(event.rawX.toInt(), event.rawY.toInt()) &&
                    !binding.drawerLayout.isDrawerOpen(GravityCompat.START) &&
                    list.adapter === conversationsAdapter
            }
            MotionEvent.ACTION_MOVE -> {
                if (samaSwipeCandidate) {
                    val dx = event.rawX - samaSwipeStartX
                    val dy = event.rawY - samaSwipeStartY
                    if (!samaSwipeCaptured && abs(dy) > samaDp(25) && abs(dy) > abs(dx)) {
                        samaSwipeCandidate = false
                    } else if (!samaSwipeCaptured && abs(dx) > samaDp(76) &&
                        abs(dx) > abs(dy) * 1.4f) {
                        // Cancel the underlying row's swipe before it can commit on ACTION_UP.
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        samaSwipeCaptured = true
                    }
                    if (samaSwipeCaptured) {
                        list.translationX = dx.coerceIn(-samaDp(100).toFloat(), samaDp(100).toFloat()) * 0.28f
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (samaSwipeCaptured) {
                    val dx = event.rawX - samaSwipeStartX
                    val dy = event.rawY - samaSwipeStartY
                    val tabs = listOf(0L, -1L) + samaCategories().map { it.first }
                    val at = tabs.indexOf(samaSelectedCategoryId)
                    if (at >= 0 && abs(dx) >= samaDp(90) && abs(dx) > abs(dy) * 1.3f) {
                        val target = (at + if (dx < 0f) 1 else -1).coerceIn(0, tabs.lastIndex)
                        if (target != at) samaSelectCategory(tabs[target])
                    }
                    list.animate().translationX(0f).setDuration(170L).start()
                    samaSwipeCaptured = false
                    samaSwipeCandidate = false
                    return true
                }
                samaSwipeCandidate = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (samaSwipeCaptured) list.animate().translationX(0f).setDuration(170L).start()
                samaSwipeCaptured = false
                samaSwipeCandidate = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent?) =''')

patch(main, '        conversationsAdapter.hasScheduledConversation = state.scheduledConversationIds',
      '''        samaCanSwipeTabs = state.page is Inbox && state.page.selected == 0
        conversationsAdapter.hasScheduledConversation = state.scheduledConversationIds''')
patch(main, '                samaCanSwipeTabs = state.page.selected == 0\n                samaCurrentInbox',
      '                samaCurrentInbox')
patch(main, '''    override fun onDestroy() =
        super.onDestroy().also { disposables.dispose() }''',
      '''    override fun onDestroy() {
        samaSource?.removeChangeListener(samaSourceListener)
        samaUnreadMessages?.removeChangeListener(samaMessageListener)
        samaCountRealm?.close()
        super.onDestroy()
        disposables.dispose()
    }''')

print("Sama QUIK base patch applied successfully. Original QUIK SMS receive engine remains unchanged.")
