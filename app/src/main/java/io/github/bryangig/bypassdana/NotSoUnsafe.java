package io.github.bryangig.bypassdana;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotSoUnsafe implements IXposedHookLoadPackage {
    // Define a consistent TAG for easier log filtering
    private static final String TAG = "BypassDANA";
    private ClassLoader classLoader;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        // Target only the DANA application
        if (!"id.dana".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Found DANA application (" + lpparam.packageName + "). Initializing hooks.");

        // Hook into the application's attach method to get a reliable ClassLoader
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log(TAG + ": Application attached. Obtaining ClassLoader.");
                classLoader = ((Context) param.args[0]).getClassLoader();
                // Apply all our specific hooks now that we have the ClassLoader
                applyHooks();
            }
        });
    }

    private void applyHooks() {
        XposedBridge.log(TAG + ": Applying all method hooks.");
        try {
            hookActivityLifecycle();
            hookIntentRedirection();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": A critical error occurred while applying hooks.");
            XposedBridge.log(t);
        }
    }

    /**
     * Hooks into specific Activity/Fragment lifecycle methods that might contain security checks.
     * This prevents initial checks from running.
     */
    private void hookActivityLifecycle() {
        try {
            // Hook for HomeTabActivity to prevent potential startup checks
            XposedHelpers.findAndHookMethod("id.dana.home.HomeTabActivity", classLoader, "onStart", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + ": Bypassing onStart for HomeTabActivity.");
                    try {
                        XposedHelpers.findField(Activity.class, "mCalled").set(param.thisObject, true);
                        param.setResult(null); // Safely skip the original method
                    } catch (Exception e) {
                        XposedBridge.log(TAG + ": Failed to bypass onStart for HomeTabActivity.");
                        XposedBridge.log(e);
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + ": Class id.dana.home.HomeTabActivity not found. Skipping hook. (This is okay if DANA updated the class name)");
        }

        try {
            // Hook for VerifyNumberFragment, another potential entry point for checks
            XposedHelpers.findAndHookMethod("id.dana.onboarding.verify.VerifyNumberFragment", classLoader, "onStart", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + ": Bypassing onStart for VerifyNumberFragment.");
                    try {
                        var fragmentClass = XposedHelpers.findClass("androidx.fragment.app.Fragment", classLoader);
                        XposedHelpers.findField(fragmentClass, "mCalled").set(param.thisObject, true);
                        param.setResult(null); // Safely skip the original method
                    } catch (Exception e) {
                        XposedBridge.log(TAG + ": Failed to bypass onStart for VerifyNumberFragment.");
                        XposedBridge.log(e);
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + ": Class id.dana.onboarding.verify.VerifyNumberFragment not found. Skipping hook.");
        }
    }

    /**
     * The core bypass logic. Instead of blocking the "unsafe" screen which causes a crash,
     * we intercept the Intent, remove the flag that triggers the warning, and let it proceed.
     * This keeps the application's workflow intact.
     */
    private void hookIntentRedirection() {
        XposedHelpers.findAndHookMethod(ContextWrapper.class, "startActivity", Intent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length > 0 && param.args[0] instanceof Intent) {
                        Intent intent = (Intent) param.args[0];
                        if (intent != null && intent.getExtras() != null) {
                            Bundle bundle = intent.getExtras();
                            // Check for the specific key DANA uses to flag an unsafe device
                            if (bundle.containsKey("unsafe_status")) {
                                String unsafeStatus = bundle.getString("unsafe_status", "N/A");
                                XposedBridge.log(TAG + ": Intercepted an Intent with 'unsafe_status'. Value: " + unsafeStatus);
                                XposedBridge.log(TAG + ": Removing 'unsafe_status' extra to prevent warning screen.");
                                
                                // THE ADVANCED FIX v2: Replace the value instead of removing the key.
                                // This might satisfy the app's workflow better.
                                bundle.putString("unsafe_status", ""); // Try with an empty string
                                
                                XposedBridge.log(TAG + ": Bypass successful. Replaced 'unsafe_status' with empty string.");
                            }
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": Error in startActivity hook. The app might behave unexpectedly.");
                    XposedBridge.log(e);
                }
            }
        });
    }
}
