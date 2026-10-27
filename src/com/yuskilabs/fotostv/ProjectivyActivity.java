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

/** Elegir qué álbumes y cómo se usan como fondo de Projectivy Launcher. */
public class ProjectivyActivity extends Activity {
    private LinearLayout list;
    private boolean changed;
    private int focusIdx;

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
        s.setText("Elige cómo se ven y de qué álbumes salen las fotos de fondo.");
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

    private Button item(String label, final Runnable action) {
        Button bt = Ui.button(this, label, null);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bt.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        bt.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        bt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i) == v) focusIdx = i;
                action.run();
                changed = true;
                fill();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, Ui.dp(this, 6));
        bt.setLayoutParams(lp);
        return bt;
    }

    private TextView head(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF7CC4FF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 4));
        return t;
    }

    private static String next(String cur, String[] opts) {
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(cur)) return opts[(i + 1) % opts.length];
        return opts[0];
    }

    private void put(String k, String v) { ProjectivyService.sp(this).edit().putString(k, v).apply(); }

    private void saveAlbums(Set<String> s) { ProjectivyService.sp(this).edit().putStringSet("pv_albums", s).apply(); }

    private void fill() {
        list.removeAllViews();
        final String fit = ProjectivyService.fit(this), vert = ProjectivyService.vert(this);
        list.addView(item((ProjectivyService.videos(this) ? "\u2611  " : "\u2610  ") + "Incluir también vídeos", new Runnable() {
            public void run() {
                ProjectivyService.sp(ProjectivyActivity.this).edit().putBoolean("pv_videos", !ProjectivyService.videos(ProjectivyActivity.this)).apply();
            }
        }));
        list.addView(item("Fotos horizontales: " + ("fit".equals(fit) ? "originales (con bandas)" : "blur".equals(fit) ? "originales sobre fondo difuminado" : "rellenar la pantalla (automático)"), new Runnable() {
            public void run() { put("pv_fit", next(fit, new String[]{"auto", "fit", "blur"})); }
        }));
        list.addView(item("Fotos verticales y cuadradas: " + ("blur".equals(vert) ? "incluir, con fondo difuminado" : "fill".equals(vert) ? "incluir, rellenando (recorta)" : "no incluir"), new Runnable() {
            public void run() { put("pv_vert", next(vert, new String[]{"no", "blur", "fill"})); }
        }));
        list.addView(head("ÁLBUMES"));
        final List<Prefs.Album> albums = Prefs.albums(this);
        list.addView(item("Marcar todos", new Runnable() {
            public void run() {
                Set<String> n = new HashSet<String>();
                for (Prefs.Album x : albums) n.add(x.url);
                saveAlbums(n);
            }
        }));
        list.addView(item("Desmarcar todos", new Runnable() {
            public void run() { saveAlbums(new HashSet<String>()); }
        }));
        for (final Prefs.Album a : albums) {
            String name = a.name;
            if (name == null || name.length() == 0) {
                String[] inf = Prefs.albumInfo(this, a.url);
                name = inf != null && inf[0].length() > 0 ? inf[0] : "Álbum sin nombre";
            }
            list.addView(item((ProjectivyService.albumOn(this, a.url) ? "\u2611  " : "\u2610  ") + name, new Runnable() {
                public void run() {
                    Set<String> sel = ProjectivyService.sp(ProjectivyActivity.this).getStringSet("pv_albums", null);
                    Set<String> n = new HashSet<String>();
                    if (sel != null) n.addAll(sel);
                    if (!n.remove(a.url)) n.add(a.url);
                    saveAlbums(n);
                }
            }));
        }
        if (list.getChildCount() > 0) list.getChildAt(Math.min(focusIdx, list.getChildCount() - 1)).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (changed) { changed = false; ProjectivyService.notifyChanged(this); }
    }
}
