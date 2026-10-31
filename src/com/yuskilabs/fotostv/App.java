package com.yuskilabs.fotostv;

import android.app.Application;
import android.graphics.Typeface;

/** Carga las tipografías de la app (Outfit) una vez, al arrancar el proceso. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            Ui.BLACK = Typeface.createFromAsset(getAssets(), "fonts/Outfit-ExtraBold.ttf");
            Ui.REG = Typeface.createFromAsset(getAssets(), "fonts/Outfit-Regular.ttf");
        } catch (RuntimeException e) { /* si falla, se queda la fuente de Android */ }
    }
}
