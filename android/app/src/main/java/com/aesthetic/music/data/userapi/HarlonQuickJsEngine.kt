package com.aesthetic.music.data.userapi

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext

/**
 * [JsEngine] backed by wang.harlon.quickjs:wrapper-android (verified against 3.2.3,
 * the version Melos/android already uses).
 *
 *   implementation("wang.harlon.quickjs:wrapper-android:3.2.3")
 *
 * Threading: LxRuntime calls every method from one executor thread, and QuickJSContext
 * enforces `checkSameThread()`. Create the engine on that same thread (LxRuntime does).
 *
 * Microtasks: the wrapper drains QuickJS's pending Promise jobs around evaluate/call.
 * The no-op `evaluate("void 0")` after each callback is a cheap safety net for bindings
 * whose `call` does not; it makes lx.send()/lx.request() settle without another host event.
 */
class HarlonQuickJsEngine : JsEngine {

    init {
        QuickJSLoader.init()
    }

    private val qjs = QuickJSContext.create()
    private val global: JSObject = qjs.getGlobalObject()
    private val functions = HashMap<String, JSFunction>()

    override fun bindHost(handlers: Map<String, (List<Any?>) -> Any?>) {
        val host = qjs.createNewJSObject()
        for ((name, handler) in handlers) {
            host.setProperty(name, JSCallFunction { args -> handler(args.toList()) })
        }
        global.setProperty("__host", host)
        // setProperty dups the object into JS; drop the Java-side reference (same pattern
        // as QuickJSObject.setProperty(name, Class) in the wrapper itself).
        host.release()
    }

    override fun evaluate(code: String, fileName: String) {
        qjs.evaluate(code, fileName)
    }

    override fun callGlobal(path: String, vararg args: Any?) {
        val fn = functions.getOrPut(path) {
            var target: JSObject = global
            val parts = path.split('.')
            for (part in parts.dropLast(1)) target = target.getJSObject(part)
            target.getJSFunction(parts.last()) ?: error("JS function not found: $path")
        }
        fn.call(*args)
        // Pump the Promise job queue; see the class comment.
        qjs.evaluate("void 0", "drain.js")
    }

    override fun close() {
        qjs.destroy()
    }
}
