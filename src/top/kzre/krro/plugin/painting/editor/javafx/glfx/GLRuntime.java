package top.kzre.krro.plugin.painting.editor.javafx.glfx;

import com.huskerdev.grapl.gl.GLContext;
import com.huskerdev.grapl.gl.GLProfile;
import org.lwjgl.opengl.GL;
import top.kzre.krro.canvas.gl.util.ExecutorUtils;
import top.kzre.krro.core.util.SerialExecutor;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GL 运行时。直接持有 grapl {@link GLContext} 与其 GL 线程执行器，
 * 并管理其上所有“GL 线程绑定资源”——凡必须在 GL 线程构造、
 * 跟随 GL 上下文生命周期的对象，都通过 {@link #computeIfAbsent}
 * 注册进来。
 *
 * <p><b>资源示例</b>：{@code GLCompositeFlyweight}、program cache、
 * 默认 VAO/VBO、共享 quad……它们没有公共父类型，也不该有——只
 * 需要在这里被缓存一次。
 *
 * <p><b>线程契约</b>：{@link #computeIfAbsent} 任意线程；factory
 * 在 GL 线程执行，GL 上下文已 current。{@link #close()} 任意线程。
 *
 * <p><b>生命周期</b>：非幂等。{@link #close()} 先在 GL 线程执行
 * close hook、释放所有资源，然后关闭执行器（触发 onStop →
 * {@code context.delete()}）。
 */
public final class GLRuntime implements AutoCloseable {

    private final GLContext context;
    private final SerialExecutor executor;
    private final ConcurrentHashMap<Object, Object> resources = new ConcurrentHashMap<>();
    private final List<Runnable> closeHooks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private GLRuntime(GLContext context, SerialExecutor executor) {
        this.context = context;
        this.executor = executor;
    }

    /**
     * 用给定 grapl 上下文创建离屏 runtime。worker 启动时
     * {@code makeCurrent} 并调用 {@code GL.createCapabilities()}
     * 加载 LWJGL 函数指针；worker 退出时销毁上下文。
     *
     * <p>容量、overflow 策略、超时均为离屏渲染调优值——其他场景
     * 需要不同的配置时应新增工厂方法，不要复用本方法。
     */
    public static GLRuntime newOffscreen(GLContext context) {
        if (context == null) throw new NullPointerException("context must not be null");
        SerialExecutor executor = new SerialExecutor(
                "grapl-context-",
                1000,
                SerialExecutor.OverflowPolicy.BLOCK_TIMEOUT,
                5000,
                () -> {
                    context.makeCurrent();
                    GL.createCapabilities();
                },
                context::delete);
        return new GLRuntime(context, executor);
    }

    // ── 上下文与执行器代理 ────────────────────────

    /** GL 上下文。 */
    public GLContext getContext() {
        return context;
    }

    /** GL 线程执行器。 */
    public SerialExecutor getExecutor() {
        return executor;
    }

    // ── GL 线程绑定资源 ────────────────────────────

    /**
     * 获取或惰性创建 GL 线程绑定资源。
     * 默认GL 线程下初始化
     * @param key     资源标识（通常用资源 Class）
     * @param factory 创建函数——在 GL 线程执行，GL 上下文已 current
     * @return 解析为资源的 future；同一 key 并发调用只创建一次
     */
    public <T> CompletableFuture<T> computeIfAbsent(Object key, Callable<T> factory) {
       return computeIfAbsent(key,factory, true);
    }

    /**
     * 获取或创建资源，同步执行时候错误立即抛出
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> computeIfAbsent(Object key, Callable<T> factory, boolean onGlThread) {
        if (key == null) throw new NullPointerException("key must not be null");
        if (factory == null) throw new NullPointerException("factory must not be null");
        if (closed.get()) throw new IllegalStateException("GLRuntime is closed");

        Object cached = resources.get(key);
        if (cached != null) return CompletableFuture.completedFuture((T) cached);
        Callable<T> cb =  () -> {
            Object hit = resources.get(key);
            if (hit != null) return (T) hit;
            T created = factory.call();
            resources.put(key, created);
            return created;
        };
        if (onGlThread) {
            return ExecutorUtils.runOnThread(executor,cb);
        }else {
            try {
                return CompletableFuture.completedFuture(cb.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    // ── 关闭 hook ──────────────────────────────────

    /**
     * 注册关闭 hook。在 GL 线程、资源清理前执行。用于在资源被
     * 释放前做一些额外动作（比如 fence、显式 flush）。
     */
    public void addCloseHook(Runnable callback) {
        if (callback == null) throw new NullPointerException("callback must not be null");
        if (closed.get()) throw new IllegalStateException("GLRuntime is closed");
        closeHooks.add(callback);
    }

    // ── 生命周期 ──────────────────────────────────

    /**
     * 关闭运行时。在 GL 线程依次执行：close hook → 资源释放；然后
     * 关闭执行器——worker 退出时触发 onStop → {@code context.delete()}。
     * 非幂等。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            throw new IllegalStateException("GLRuntime already closed");
        }
        ExecutorUtils.runOnThread(executor, () -> {
            for (Runnable hook : closeHooks) {
                try { hook.run(); } catch (Throwable ignored) { }
            }
            closeHooks.clear();
            for (Object r : resources.values()) {
                if (r instanceof AutoCloseable) {
                    try { ((AutoCloseable) r).close(); } catch (Throwable ignored) { }
                }
            }
            resources.clear();
        });
        executor.close();
    }

    // ── 默认离屏 runtime ──────────────────────────

    private static volatile GLRuntime DEFAULT;

    /**
     * 默认的离屏渲染 runtime。懒创建，全局唯一。
     */
    public static GLRuntime offscreen() {
        GLRuntime r = DEFAULT;
        if (r == null) {
            synchronized (GLRuntime.class) {
                r = DEFAULT;
                if (r == null) {
                    GLContext ctx = GLContext.create(
                            0, GLProfile.CORE, -1, -1, false);
                    r = GLRuntime.newOffscreen(ctx);
                    DEFAULT = r;
                }
            }
        }
        return r;
    }
}