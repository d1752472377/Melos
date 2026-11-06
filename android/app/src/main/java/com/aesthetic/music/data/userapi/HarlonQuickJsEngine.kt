package com.aesthetic.music.data.userapi

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext

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
        qjs.evaluate("void 0", "drain.js")
    }

    override fun close() {
        qjs.destroy()
    }
}
