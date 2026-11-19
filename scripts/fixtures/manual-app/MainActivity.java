package com.doxaiz.netoptfixture;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Disposable-emulator fixture; local probe runs only when explicitly requested by a test. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getIntent().getBooleanExtra("NETOPT_PROBE", false)) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("http://10.0.2.2:18765/probe").openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                int total = 0;
                try (InputStream stream = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) total += count;
                }
                Log.i("NetOptFixture", "LOCAL_PROBE_BYTES=" + total);
            } catch (Exception error) {
                Log.w("NetOptFixture", "LOCAL_PROBE_FAILED=" + error.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
