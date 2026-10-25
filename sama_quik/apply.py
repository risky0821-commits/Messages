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
patch(gradle, 'applicationIdSuffix ".debug"', 'applicationIdSuffix ".debug.fasttabs"')

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
            android:layout_height="54dp"
            android:fillViewport="false"
            android:scrollbars="none"
            app:layout_constraintTop_toBottomOf="@id/toolbar">

            <LinearLayout
                android:id="@+id/samaCategoryRow"
                android:layout_width="wrap_content"
                android:layout_height="match_parent"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:paddingStart="12dp"
                android:paddingEnd="12dp" />
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
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import org.json.JSONObject
import io.realm.Realm
import io.realm.RealmResults
import io.realm.RealmChangeListener
import dev.octoshrimpy.quik.model.Conversation
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
''')
patch(main, '    private lateinit var binding: MainActivityBinding',
      '''    private val samaPrefs by lazy { getSharedPreferences("sama_quik_categories", Context.MODE_PRIVATE) }
    private var samaSelectedCategoryId = 0L
    private var samaSource: RealmResults<Conversation>? = null
    // Keep each tab's live Realm result instead of restarting an async query on every click.
    private val samaTabResults = mutableMapOf<Long, RealmResults<Conversation>>()
    private var samaUnreadChip: TextView? = null
    private val samaUnreadListener = RealmChangeListener<RealmResults<Conversation>> {
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
            textSize = 14f
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(samaDp(18), samaDp(9), samaDp(18), samaDp(9))
            background = GradientDrawable().apply {
                cornerRadius = samaDp(20).toFloat()
                setColor(tint)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, samaDp(42)
            ).apply { marginEnd = samaDp(5) }
            setOnClickListener { onClick() }
        }
    }

    private fun samaUpdateUnreadCount() {
        val count = samaTabResults[-1L]?.takeIf { it.isLoaded }?.size
        samaUnreadChip?.text = if (count == null) "غير مقروءة" else "غير مقروءة ($count)"
    }

    private fun samaRefreshChips() {
        binding.samaCategoryRow.apply {
            removeAllViews()
            addView(samaChip("الكل", samaSelectedCategoryId == 0L) { samaSelectCategory(0L) })
            samaUnreadChip = samaChip("غير مقروءة", samaSelectedCategoryId == -1L) {
                samaSelectCategory(-1L)
            }.also { addView(it) }
            samaUpdateUnreadCount()
            samaCategories().forEach { (id, name) ->
                addView(samaChip(name, samaSelectedCategoryId == id) { samaSelectCategory(id) })
            }
            addView(samaChip("+", false) { samaNewCategory() })
        }
    }

    private fun samaVisibleConversations(source: RealmResults<Conversation>?): RealmResults<Conversation>? {
        if (source == null) return null
        if (samaSource !== source) {
            samaTabResults[-1L]?.removeChangeListener(samaUnreadListener)
            samaSource = source
            samaTabResults.clear()
            // Keep a live unread query even while another tab is active.
            val unread = source.where().equalTo("lastMessage.read", false).findAllAsync()
            samaTabResults[-1L] = unread
            unread.addChangeListener(samaUnreadListener)
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
        samaRefreshChips()
        if (binding.recyclerView.adapter === conversationsAdapter) {
            conversationsAdapter.updateData(samaVisibleConversations(samaCurrentInbox))
        }
    }

    private var samaCurrentInbox: RealmResults<Conversation>? = null

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
      '''                samaCurrentInbox = state.page.data
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

print("Sama QUIK base patch applied successfully. Original QUIK SMS receive engine remains unchanged.")
