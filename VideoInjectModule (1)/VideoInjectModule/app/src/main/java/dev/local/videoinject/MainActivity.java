package dev.local.videoinject;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Pattern;

/**
 * Settings screen. "Apply" copies the video and settings into the target app's own data folder
 * using root, so the target app itself never has to be changed. "Check setup" reads back what
 * is in that folder and what the hook last did.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "config";
    private static final String KEY_TARGET = "target";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOOP = "loop";
    private static final String LOCAL_VIDEO = "video.mp4";
    private static final int REQ_VIDEO = 1;
    private static final int MODE_APPLY = 0;
    private static final int MODE_REMOVE = 1;
    private static final int MODE_CHECK = 2;
    private static final Pattern PKG = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    private SharedPreferences sp;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Video Inject");
        title.setTextSize(22);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("1. Type your app's package name.\n"
                + "2. Tap Choose video.\n"
                + "3. Tap Apply to app and allow the root popup.\n"
                + "4. Open your app's camera screen, then tap Check setup.");
        hint.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(hint);

        final EditText target = new EditText(this);
        target.setHint("com.yourcompany.yourapp");
        target.setSingleLine(true);
        target.setText(sp.getString(KEY_TARGET, ""));
        target.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable e) {
                sp.edit().putString(KEY_TARGET, e.toString().trim()).apply();
            }
        });
        root.addView(target);

        Button pick = new Button(this);
        pick.setText("Choose video");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("video/*");
                startActivityForResult(i, REQ_VIDEO);
            }
        });
        root.addView(pick);

        Switch enabled = new Switch(this);
        enabled.setText("Inject video");
        enabled.setChecked(sp.getBoolean(KEY_ENABLED, true));
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                sp.edit().putBoolean(KEY_ENABLED, on).apply();
                applyIfReady();
            }
        });
        root.addView(enabled);

        Switch loop = new Switch(this);
        loop.setText("Loop video");
        loop.setChecked(sp.getBoolean(KEY_LOOP, true));
        loop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                sp.edit().putBoolean(KEY_LOOP, on).apply();
                applyIfReady();
            }
        });
        root.addView(loop);

        Button apply = new Button(this);
        apply.setText("Apply to app");
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { runRoot(MODE_APPLY); }
        });
        root.addView(apply);

        Button check = new Button(this);
        check.setText("Check setup");
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { runRoot(MODE_CHECK); }
        });
        root.addView(check);

        Button remove = new Button(this);
        remove.setText("Remove video from app");
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { runRoot(MODE_REMOVE); }
        });
        root.addView(remove);

        status = new TextView(this);
        status.setPadding(0, pad, 0, 0);
        status.setTextIsSelectable(true);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    private void say(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { status.setText(msg); }
        });
    }

    private void refreshStatus() {
        File f = new File(getFilesDir(), LOCAL_VIDEO);
        status.setText(f.isFile()
                ? "Video ready (" + (f.length() / 1024) + " KB). Tap Apply to app."
                : "No video selected yet.");
    }

    /** Re-sends settings only if a video and package name are already set. */
    private void applyIfReady() {
        String pkg = sp.getString(KEY_TARGET, "").trim();
        if (PKG.matcher(pkg).matches() && new File(getFilesDir(), LOCAL_VIDEO).isFile()) {
            runRoot(MODE_APPLY);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VIDEO || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        say("Copying video...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IllegalStateException("could not open the video");
                    OutputStream out = new FileOutputStream(new File(getFilesDir(), LOCAL_VIDEO));
                    try {
                        byte[] buf = new byte[1 << 16];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        in.close();
                        out.close();
                    }
                } catch (Exception e) {
                    err = "Copy failed: " + e.getMessage();
                }
                if (err != null) {
                    say(err);
                } else {
                    runOnUiThread(new Runnable() {
                        public void run() { refreshStatus(); }
                    });
                }
            }
        }).start();
    }

    /** Builds the shell script that runs as root for the chosen mode. */
    private String buildScript(String pkg, int mode, File src, boolean enabled, boolean loop) {
        StringBuilder s = new StringBuilder();
        s.append("B=/data/data/").append(pkg).append("\n");
        s.append("D=$B/files\n");
        s.append("[ -d \"$B\" ] || { echo ERR:NOAPP; exit; }\n");
        if (mode == MODE_REMOVE) {
            s.append("rm -f \"$D/vinject.mp4\" \"$D/vinject.cfg\" \"$D/vinject.status\"\n");
            s.append("echo @@DONE@@\n");
        } else if (mode == MODE_CHECK) {
            s.append("echo \"App found: yes\"\n");
            s.append("if [ -f \"$D/vinject.mp4\" ]; then echo \"Video in app: yes ($(wc -c < \"$D/vinject.mp4\") bytes)\";"
                    + " else echo \"Video in app: NO (tap Apply to app)\"; fi\n");
            s.append("if [ -f \"$D/vinject.cfg\" ]; then echo \"Settings: $(tr '\\n' ' ' < \"$D/vinject.cfg\")\";"
                    + " else echo \"Settings: missing (tap Apply to app)\"; fi\n");
            s.append("if [ -f \"$D/vinject.status\" ]; then echo \"Last camera event: $(cat \"$D/vinject.status\")\";"
                    + " else echo \"Last camera event: none yet. Open the camera screen in your app,"
                    + " then tap Check setup again.\"; fi\n");
            s.append("echo @@DONE@@\n");
        } else {
            s.append("U=$(stat -c %u \"$B\" 2>/dev/null)\n");
            s.append("if [ ! -d \"$D\" ]; then mkdir -p \"$D\"; chmod 771 \"$D\";"
                    + " [ -z \"$U\" ] || chown $U:$U \"$D\";"
                    + " chcon --reference=\"$B\" \"$D\" 2>/dev/null; fi\n");
            s.append("cp \"").append(src.getAbsolutePath()).append("\" \"$D/vinject.mp4\""
                    + " || { echo ERR:COPY; exit; }\n");
            s.append("printf 'enabled=%s\\nloop=%s\\n' ").append(enabled ? 1 : 0).append(' ')
                    .append(loop ? 1 : 0).append(" > \"$D/vinject.cfg\" || { echo ERR:CFG; exit; }\n");
            s.append("[ -z \"$U\" ] || chown $U:$U \"$D/vinject.mp4\" \"$D/vinject.cfg\"\n");
            s.append("chmod 666 \"$D/vinject.mp4\" \"$D/vinject.cfg\"\n");
            s.append("chcon --reference=\"$B\" \"$D/vinject.mp4\" \"$D/vinject.cfg\" 2>/dev/null\n");
            s.append("echo @@DONE@@\n");
        }
        s.append("exit\n");
        return s.toString();
    }

    /** Runs apply / remove / check as root and shows the result. */
    private void runRoot(final int mode) {
        final String pkg = sp.getString(KEY_TARGET, "").trim();
        if (!PKG.matcher(pkg).matches()) {
            say("Type your app's package name first (like com.company.app).");
            return;
        }
        final File src = new File(getFilesDir(), LOCAL_VIDEO);
        if (mode == MODE_APPLY && !src.isFile()) {
            say("Choose a video first.");
            return;
        }
        final String script = buildScript(pkg, mode, src,
                sp.getBoolean(KEY_ENABLED, true), sp.getBoolean(KEY_LOOP, true));

        say("Asking for root permission. Please tap Allow in the popup...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder out = new StringBuilder();
                try {
                    Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
                    OutputStream os = p.getOutputStream();
                    os.write(script.getBytes("UTF-8"));
                    os.flush();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) out.append(line).append('\n');
                    p.waitFor();
                } catch (Exception e) {
                    say("Root (su) not found or blocked: " + e.getMessage()
                            + "\nThis phone needs Magisk or KernelSU root.");
                    return;
                }
                String o = out.toString();
                if (o.contains("ERR:NOAPP")) {
                    say("App " + pkg + " is not installed on this phone. Check the package name.");
                } else if (o.contains("@@DONE@@")) {
                    if (mode == MODE_REMOVE) {
                        say("Removed from " + pkg + ".");
                    } else if (mode == MODE_CHECK) {
                        say(o.replace("@@DONE@@", "").trim());
                    } else {
                        say("Done. Video placed for " + pkg + ".\n"
                                + "Now open that app's camera screen. If it was already open,"
                                + " swipe it away from recent apps and open it again.");
                    }
                } else {
                    say("Failed. Did you tap Allow on the root popup?\n" + o.trim());
                }
            }
        }).start();
    }
}
