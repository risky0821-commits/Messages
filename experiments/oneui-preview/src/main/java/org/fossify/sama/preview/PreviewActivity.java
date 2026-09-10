package org.fossify.sama.preview;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.*;

public class PreviewActivity extends AppCompatActivity {
    final Set<Integer> selected = new HashSet<>();
    final List<Integer> visible = new ArrayList<>();
    final String[] names = {"أحمد", "العائلة", "خدمة التوصيل", "البنك", "محمد", "العمل"};
    final String[] messages = {"نشوفك اليوم إن شاء الله", "وصلت بالسلامة", "طلبك في الطريق", "تمت العملية بنجاح", "شكرًا لك", "موعد الاجتماع غدًا"};
    ActionMode mode;
    Rows rows;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), 0);
        TextView title = new TextView(this);
        title.setText("الرسائل"); title.setTextSize(34);
        title.setPadding(0, dp(40), 0, dp(20)); root.addView(title);
        TextView note = new TextView(this);
        note.setText("بيانات تجريبية • معاينة الواجهة"); root.addView(note);
        SearchView search = new SearchView(this);
        search.setIconifiedByDefault(false); search.setQueryHint("بحث");
        root.addView(search);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        rows = new Rows(); list.setAdapter(rows);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        filter("");
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String q) { return true; }
            public boolean onQueryTextChange(String q) { filter(q); return true; }
        });
    }
    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    void filter(String q) {
        visible.clear();
        for(int i=0;i<names.length;i++) if((names[i]+messages[i]).contains(q)) visible.add(i);
        rows.notifyDataSetChanged();
    }
    void toggle(int id) {
        if(mode == null) mode = startActionMode(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode m, Menu menu) {
                menu.add("تحديد الكل").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM); return true;
            }
            public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            public boolean onActionItemClicked(ActionMode m, MenuItem item) {
                selected.addAll(visible); m.setTitle(selected.size()+" محدد"); rows.notifyDataSetChanged(); return true;
            }
            public void onDestroyActionMode(ActionMode m) { mode=null; selected.clear(); rows.notifyDataSetChanged(); }
        });
        if(!selected.add(id)) selected.remove(id);
        if(mode != null) { if(selected.isEmpty()) mode.finish(); else mode.setTitle(selected.size()+" محدد"); }
        rows.notifyDataSetChanged();
    }
    class Row extends RecyclerView.ViewHolder {
        CheckedTextView text;
        Row(CheckedTextView v) { super(v); text=v; }
    }
    class Rows extends RecyclerView.Adapter<Row> {
        Rows() { setHasStableIds(true); }
        public long getItemId(int p) { return visible.get(p); }
        public int getItemCount() { return visible.size(); }
        public Row onCreateViewHolder(ViewGroup parent,int type) {
            CheckedTextView text = new CheckedTextView(PreviewActivity.this);
            text.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(88)));
            text.setGravity(Gravity.CENTER_VERTICAL); text.setTextSize(17);
            text.setPadding(dp(12),0,dp(12),0);
            text.setCheckMarkDrawable(android.R.drawable.checkbox_on_background);
            return new Row(text);
        }
        public void onBindViewHolder(Row h,int position) {
            int id=visible.get(position);
            h.text.setText(names[id]+"\n"+messages[id]);
            h.text.setChecked(selected.contains(id));
            h.text.setCheckMarkDrawable(selected.contains(id) ? android.R.drawable.checkbox_on_background : 0);
            h.text.setOnLongClickListener(v->{toggle(id);return true;});
            h.text.setOnClickListener(v->{if(mode!=null) toggle(id); else Toast.makeText(PreviewActivity.this,messages[id],Toast.LENGTH_SHORT).show();});
        }
    }
}
