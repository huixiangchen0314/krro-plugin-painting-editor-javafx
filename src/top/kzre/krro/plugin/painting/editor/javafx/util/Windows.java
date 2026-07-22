package top.kzre.krro.plugin.painting.editor.javafx.util;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import javafx.stage.Stage;

import java.lang.reflect.Method;
import java.util.OptionalLong;

/**
 * Windows 平台专用工具：从 JavaFX Stage 中获取原生窗口句柄 (HWND)。
 * 提供多级回退策略以兼容不同 JavaFX 版本（8 / 11+ / 17+ 等），并在内部 API 不可用时借助 JNA 查找窗口。
 * <p>
 * 使用前请确保：
 * <ul>
 *   <li>运行在 Windows 平台</li>
 *   <li>添加了 JNA 依赖（如 net.java.dev.jna:jna-platform）</li>
 *   <li>若使用 JavaFX 内部反射 API，需添加 JVM 参数：
 *       {@code --add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED --add-opens javafx.graphics/javafx.stage=ALL-UNNAMED}</li>
 * </ul>
 */
public final class Windows {

    private static final Class<?>[] NO_PARAMS = new Class<?>[0];

    private Windows() {
        // 工具类，禁止实例化
    }

    public static WinDef.RECT getWindowRect(long hwnd) {
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(new HWND(new Pointer(hwnd)), rect);
        return rect;
    }

    /**
     * 获取给定 Stage 的 Windows HWND (原生窗口句柄)。
     * 依次尝试多种获取方式，若全部失败则抛出 RuntimeException。
     *
     * @param stage JavaFX Stage 对象
     * @return 原生窗口句柄（long 类型）
     * @throws RuntimeException 如果所有回退策略均失败
     */
    public static long getHwnd(Stage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("Stage 不能为 null");
        }

        OptionalLong hwnd = getViaImplGetWindow(stage);
        if (!hwnd.isPresent()) hwnd = getViaImplGetPeer(stage);
        if (!hwnd.isPresent()) hwnd = getViaGlassWindowEnum(stage);
        if (!hwnd.isPresent()) hwnd = getViaToolkit(stage);
        if (!hwnd.isPresent()) hwnd = getViaJna(stage);

        if (hwnd.isPresent()) {
            return hwnd.getAsLong();
        }
        throw new RuntimeException("无法从 Stage 获取 HWND，所有回退策略均失败。" +
                "请确认在 Windows 平台运行且已添加 JNA 依赖，并已开放 JavaFX 内部模块（参见文档）。");
    }

    // ---------- 回退策略 1: impl_getWindow ----------
    private static OptionalLong getViaImplGetWindow(Stage stage) {
        try {
            Method implMethod = Stage.class.getDeclaredMethod("getOwner", NO_PARAMS);
            implMethod.setAccessible(true);
            Object tkStage = implMethod.invoke(stage);
            Method nativeMethod = tkStage.getClass().getMethod("getNativeHandle", NO_PARAMS);
            Object hwnd = nativeMethod.invoke(tkStage);
            return OptionalLong.of(((Number) hwnd).longValue());
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    // ---------- 回退策略 2: impl_getPeer ----------
    private static OptionalLong getViaImplGetPeer(Stage stage) {
        try {
            Method implMethod = Stage.class.getMethod("impl_getPeer", NO_PARAMS);
            implMethod.setAccessible(true);
            Object tkStage = implMethod.invoke(stage);
            Method nativeMethod = tkStage.getClass().getMethod("getNativeHandle", NO_PARAMS);
            Object hwnd = nativeMethod.invoke(tkStage);
            return OptionalLong.of(((Number) hwnd).longValue());
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    // ---------- 回退策略 3: com.sun.glass.ui.Window 枚举 ----------
    private static OptionalLong getViaGlassWindowEnum(Stage stage) {
        try {
            String title = stage.getTitle();
            Class<?> glassWindowClass = Class.forName("com.sun.glass.ui.Window");
            Method getWindowsMethod = glassWindowClass.getDeclaredMethod("getWindows", NO_PARAMS);
            getWindowsMethod.setAccessible(true);
            // getWindows() 返回 Window[] 数组
            Object windowsObj = getWindowsMethod.invoke(null);
            if (windowsObj instanceof Object[]) {
                Object[] windows = (Object[]) windowsObj;
                Method getTitleMethod = glassWindowClass.getMethod("getTitle", NO_PARAMS);
                Method getNativeHandleMethod = glassWindowClass.getMethod("getNativeHandle", NO_PARAMS);

                for (Object w : windows) {
                    String wTitle = (String) getTitleMethod.invoke(w);
                    if (title.equals(wTitle)) {
                        return OptionalLong.of(((Number) getNativeHandleMethod.invoke(w)).longValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return OptionalLong.empty();
    }

    // ---------- 回退策略 4: Toolkit 窗口列表 ----------
    private static OptionalLong getViaToolkit(Stage stage) {
        try {
            String title = stage.getTitle();
            Class<?> toolkitClass = Class.forName("com.sun.javafx.tk.Toolkit");
            Method getToolkitMethod = toolkitClass.getMethod("getToolkit", NO_PARAMS);
            Object toolkit = getToolkitMethod.invoke(null);
            Method getWindowsMethod = toolkit.getClass().getMethod("getWindows", NO_PARAMS);
            Object windowsObj = getWindowsMethod.invoke(toolkit);
            // getWindows() 可能返回 List 或其它 Iterable
            Iterable<?> windows;
            if (windowsObj instanceof Iterable) {
                windows = (Iterable<?>) windowsObj;
            } else if (windowsObj instanceof Object[]) {
                windows = java.util.Arrays.asList((Object[]) windowsObj);
            } else {
                return OptionalLong.empty();
            }

            for (Object tkStage : windows) {
                try {
                    Method getTitleMethod = tkStage.getClass().getMethod("getTitle", NO_PARAMS);
                    String tkTitle = (String) getTitleMethod.invoke(tkStage);
                    if (title.equals(tkTitle)) {
                        Method getNativeHandleMethod = tkStage.getClass().getMethod("getNativeHandle", NO_PARAMS);
                        return OptionalLong.of(((Number) getNativeHandleMethod.invoke(tkStage)).longValue());
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return OptionalLong.empty();
    }

    // ---------- 回退策略 5: JNA 查找 ----------
    private static OptionalLong getViaJna(Stage stage) {
        try {
            String title = stage.getTitle();
            User32 user32 = User32.INSTANCE;

            // 先尝试用通用 JavaFX 窗口类名查找（可能找到多个，优先返回第一个匹配标题的）
            HWND hwnd = user32.FindWindow("GlassWindowClass", title);
            if (hwnd != null) {
                return OptionalLong.of(Pointer.nativeValue(hwnd.getPointer()));
            }

            // 枚举所有顶层窗口进行标题匹配
            HWND[] result = {null};
            user32.EnumWindows((hWnd, pointer) -> {
                char[] buffer = new char[512];
                int length = user32.GetWindowText(hWnd, buffer, 512);
                if (length > 0) {
                    String text = new String(buffer, 0, length);
                    if (title.equals(text)) {
                        result[0] = hWnd;
                        return false; // 停止枚举
                    }
                }
                return true;
            }, null);

            if (result[0] != null) {
                return OptionalLong.of(Pointer.nativeValue(result[0].getPointer()));
            }
        } catch (Exception ignored) {
        }
        return OptionalLong.empty();
    }




}