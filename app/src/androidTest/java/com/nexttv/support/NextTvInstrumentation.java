package com.nexttv.support;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Shared UI helpers for device instrumentations: tree walk, focus, click, keys, launch.
 */
public abstract class NextTvInstrumentation extends Instrumentation {
    protected Activity activity;
    protected int passed;
    protected final StringBuilder results = new StringBuilder();
    protected boolean singleColumn;

    protected String tree(AccessibilityNodeInfo n) {
        if (n == null) return "";
        StringBuilder b = new StringBuilder();
        if (n.getText() != null) b.append(n.getText());
        b.append('\n');
        for (int i = 0; i < n.getChildCount(); i++) b.append(tree(n.getChild(i)));
        return b.toString();
    }

    protected String tree() {
        return tree(getUiAutomation().getRootInActiveWindow());
    }

    protected AccessibilityNodeInfo find(String text, boolean prefix, AccessibilityNodeInfo n) {
        if (n == null) return null;
        String value = n.getText() == null ? "" : n.getText().toString();
        if (prefix ? value.startsWith(text) : value.equals(text)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = find(text, prefix, n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    protected AccessibilityNodeInfo focused(AccessibilityNodeInfo n) {
        if (n == null) return null;
        n.refresh();
        if (n.isFocused()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = focused(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    protected String focus() {
        return tree(focused(getUiAutomation().getRootInActiveWindow())).trim();
    }

    protected void await(String label, Callable<Boolean> condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 12000;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.call()) return;
            SystemClock.sleep(80);
        }
        throw new IllegalStateException(label + " focus=" + focus() + " UI=" + tree());
    }

    protected void text(String t) throws Exception {
        await(t, () -> tree().contains(t));
    }

    protected void click(String t, boolean prefix) throws Exception {
        await(t, () -> find(t, prefix, getUiAutomation().getRootInActiveWindow()) != null);
        AccessibilityNodeInfo n = find(t, prefix, getUiAutomation().getRootInActiveWindow());
        while (!n.isClickable()) {
            n = n.getParent();
            if (n == null) throw new IllegalStateException("No clickable " + t);
        }
        n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(100);
        if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new IllegalStateException("Click " + t);
        waitForIdleSync();
        SystemClock.sleep(100);
    }

    protected void click(String t) throws Exception {
        click(t, false);
    }

    protected void focus(String t) throws Exception {
        await(t, () -> find(t, false, getUiAutomation().getRootInActiveWindow()) != null);
        AccessibilityNodeInfo n = find(t, false, getUiAutomation().getRootInActiveWindow());
        while (!n.isFocusable()) {
            n = n.getParent();
            if (n == null) throw new IllegalStateException("No focusable " + t);
        }
        if (!n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) throw new IllegalStateException("Focus " + t);
        await("focused " + t, () -> Arrays.asList(focus().split("\n")).contains(t));
    }

    protected void key(int code) {
        sendKeyDownUpSync(code);
        waitForIdleSync();
        SystemClock.sleep(120);
    }

    protected void launch() throws Exception {
        activity = startActivitySync(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName("com.nexttv", "com.nexttv.core.tv.TvActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        text("频道分类：");
        SystemClock.sleep(2300);
    }

    protected void pass(String name) {
        passed++;
        results.append("PASS ").append(name).append('\n');
        Bundle b = new Bundle();
        b.putString("stream", "PASS " + name + "\n");
        sendStatus(0, b);
    }

    protected void finishOk(String prefix) {
        Bundle b = new Bundle();
        b.putString("stream", results + prefix + passed + " groups passed\n");
        finish(Activity.RESULT_OK, b);
    }
}
