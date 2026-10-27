package com.yuskilabs.fotostv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Elegir qué álbumes (y si vídeos) se usan como fondo de Projectivy Launcher. */
public class ProjectivyActivity extends Activity {
    private LinearLayout list;
    private boolean changed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1D2E);
        root.setPadding(Ui.dp(this, 48), Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 16));
        TextView t = new TextView(this);
        t.setText("Fondo para Projectivy");
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(t);
        TextView s = new TextView(this);
        s.setText("Elige los álbumes cuyas fotos saldrán de fondo. Solo se usan las horizontales.");
        s.setTextColor(0xFFB8C7D9);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        root.addView(s);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        fill();
    }

    private Button toggle(final String label, boolean on, final Runnable flip) {
        final Button bt = Ui.button(this, "", null);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bt.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        bt.setTag(label);
        bt.setText((on ? "☑  " : "☐  ") + label);
        bt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { flip.run(); changed = true; fill(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, Ui.dp(this, 6));
        bt.setLayoutParams(lp);
        return bt;
    }

    private void fill() {
        View f = getCurrentFocus();
        CharSequence keep = f instanceof Button ? ((Button) f).getText() : null;
        list.removeAllViews();
        final List<Prefs.Album> albums = Prefs.albums(this);
        for (final Prefs.Album a : albums) {
            String name = a.name != null && a.name.length() > 0 ? a.name : a.url;
            list.addView(toggle(name, ProjectivyService.albumOn(this, a.url), new Runnable() {
                public void run() {
                    Set<String> sel = ProjectivyService.sp(ProjectivyActivity.this).getStringSet("pv_albums", null);
                    Set<String> n = new HashSet<String>();
                    if (sel == null) for (Prefs.Album x : albums) n.add(x.url); else n.addAll(sel);
                    if (!n.remove(a.url)) n.add(a.url);
                    ProjectivyService.sp(ProjectivyActivity.this).edit().putStringSet("pv_albums", n).apply();
                }
            }));
        }
        list.addView(toggle("Incluir también vídeos", ProjectivyService.videos(this), new Runnable() {
            public void run() {
                ProjectivyService.sp(ProjectivyActivity.this).edit()
                    .putBoolean("pv_videos", !ProjectivyService.videos(ProjectivyActivity.this)).apply();
            }
        }));
        if (keep != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                View c = list.getChildAt(i);
                if (c instanceof Button && ((Button) c).getText().toString().substring(3).equals(keep.toString().substring(3))) { c.requestFocus(); return; }
            }
        }
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (changed) { changed = false; ProjectivyService.notifyChanged(this); }
    }
}
