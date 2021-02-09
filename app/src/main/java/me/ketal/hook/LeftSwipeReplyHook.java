/* QNotified - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2021 xenonhydride@gmail.com
 * https://github.com/ferredoxin/QNotified
 *
 * This software is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package me.ketal.hook;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import me.singleneuron.qn_kernel.tlb.ConfigTable;
import nil.nadph.qnotified.H;
import nil.nadph.qnotified.SyncUtils;
import nil.nadph.qnotified.config.ConfigManager;
import nil.nadph.qnotified.hook.CommonDelayableHook;
import nil.nadph.qnotified.step.DexDeobfStep;
import nil.nadph.qnotified.ui.ResUtils;
import nil.nadph.qnotified.util.DexKit;
import nil.nadph.qnotified.util.Toasts;
import nil.nadph.qnotified.util.Utils;

import static me.ketal.util.TIMVersion.TIM_3_1_1;
import static me.singleneuron.util.QQVersion.QQ_8_2_6;
import static nil.nadph.qnotified.util.Initiator._BaseChatPie;
import static nil.nadph.qnotified.util.Initiator._ChatMessage;
import static nil.nadph.qnotified.util.ReflexUtil.findMethodByTypes_1;
import static nil.nadph.qnotified.util.ReflexUtil.invoke_virtual_any;
import static nil.nadph.qnotified.util.Utils.*;

public class LeftSwipeReplyHook extends CommonDelayableHook {
    public static final LeftSwipeReplyHook INSTANCE = new LeftSwipeReplyHook();
    public static final String LEFT_SWIPE_NO_ACTION = "ketal_left_swipe_noAction";
    public static final String LEFT_SWIPE_MULTI_CHOOSE = "ketal_left_swipe_multiChoose";
    public static final String LEFT_SWIPE_REPLY_DISTANCE = "ketal_left_swipe_replyDistance";
    public static final int FLAG_REPLACE_PIC = 10001;
    private static Bitmap img;

    protected LeftSwipeReplyHook() {
        super("ketal_left_swipe_action", new DexDeobfStep(DexKit.N_LeftSwipeReply_Helper__reply), new DexDeobfStep(DexKit.N_BASE_CHAT_PIE__chooseMsg));
    }

    private static Bitmap getMultiBitmap() {
        if (img == null || img.isRecycled())
            img = BitmapFactory.decodeStream(ResUtils.openAsset("list_checkbox_selected_nopress.png"));
        return img;
    }

    @Override
    public boolean isValid() {
        if (H.isTIM() && H.getVersionCode() >= TIM_3_1_1)
            return true;
        else return !H.isTIM() && H.getVersionCode() >= QQ_8_2_6;
    }

    @Override
    protected boolean initOnce() {
        try {
            Method replyMethod = DexKit.doFindMethod(DexKit.N_LeftSwipeReply_Helper__reply);
            if (replyMethod == null) return false;
            Class<?> hookClass = replyMethod.getDeclaringClass();
            String methodName = H.isTIM() ? "L" : "a";
            XposedHelpers.findAndHookMethod(hookClass, methodName, float.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!isEnabled())
                        return;
                    if (isNoAction())
                        param.setResult(null);
                }
            });

            XposedBridge.hookMethod(findMethodByTypes_1(hookClass, void.class, View.class, int.class), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!isEnabled() || !isMultiChose())
                        return;
                    ImageView iv = (ImageView) param.args[0];
                    if (iv.getTag(FLAG_REPLACE_PIC) == null) {
                        iv.setImageBitmap(getMultiBitmap());
                        iv.setTag(FLAG_REPLACE_PIC, true);
                    }
                }
            });

            XposedBridge.hookMethod(replyMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled() || !isMultiChose())
                        return;
                    Object message = invoke_virtual_any(param.thisObject, _ChatMessage());
                    Object baseChatPie = getFirstByType(param.thisObject, (Class<?>) _BaseChatPie());
                    DexKit.doFindMethod(DexKit.N_BASE_CHAT_PIE__chooseMsg).invoke(baseChatPie, message);
                    param.setResult(null);
                }
            });

            methodName = H.isTIM() ? ConfigTable.INSTANCE.getConfig(LeftSwipeReplyHook.class.getSimpleName()) : "a";
            XposedBridge.hookMethod(hasMethod(hookClass, methodName, int.class), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isEnabled())
                        return;
                    if (getReplyDistance() <= 0)
                        setReplyDistance((Integer) param.getResult());
                    else
                        param.setResult(getReplyDistance());
                }
            });
            return true;
        } catch (Exception e) {
            log(e);
        }
        return false;
    }

    public boolean isNoAction() {
        return ConfigManager.getDefaultConfig().getBooleanOrDefault(LEFT_SWIPE_NO_ACTION, false);
    }

    public void setNoAction(boolean on) {
        putValue(LEFT_SWIPE_NO_ACTION, on);
    }

    public boolean isMultiChose() {
        return ConfigManager.getDefaultConfig().getBooleanOrDefault(LEFT_SWIPE_MULTI_CHOOSE, false);
    }

    public void setMultiChose(boolean on) {
        putValue(LEFT_SWIPE_MULTI_CHOOSE, on);
    }

    public int getReplyDistance() {
        return (int) ConfigManager.getDefaultConfig().getOrDefault(LEFT_SWIPE_REPLY_DISTANCE, -1);
    }

    public void setReplyDistance(int replyDistance) {
        putValue(LEFT_SWIPE_REPLY_DISTANCE, replyDistance);
    }

    private void putValue(String keyName, Object object) {
        try {
            ConfigManager mgr = ConfigManager.getDefaultConfig();
            mgr.getAllConfig().put(keyName, object);
            mgr.save();
        } catch (Exception e) {
            Utils.log(e);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toasts.error(H.getApplication(), e + "");
            } else {
                SyncUtils.post(() -> Toasts.error(H.getApplication(), e + ""));
            }
        }
    }
}
