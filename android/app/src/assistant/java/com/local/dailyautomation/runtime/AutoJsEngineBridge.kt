package com.local.dailyautomation.runtime

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs only the immutable project bundled in this APK. No path or script is accepted from callers. */
class AutoJsEngineBridge(private val context: Context) {
    @Volatile private var active: ScriptExecution? = null
    private val projectDirectory by lazy { installPackagedProject() }

    suspend fun execute(command: ModuleCommand): ModuleResponse {
        require(command.moduleId in ALLOWED_MODULE_IDS) { "Unknown module ID" }
        val timeout = (command.deadlineEpochMs - System.currentTimeMillis()).coerceAtLeast(1L)
        val responseDirectory = File(context.cacheDir, "assistant-responses").apply { mkdirs() }
        val responseFile = File.createTempFile("module-", ".json", responseDirectory).apply { delete() }
        val effectiveCommand = command.copy(responsePath = responseFile.absolutePath)
        return withTimeout(timeout) {
            suspendCancellableCoroutine { continuation ->
                val config = ExecutionConfig(workingDirectory = projectDirectory.absolutePath).apply {
                    setArgument("commandJson", ModuleProtocol.encodeCommand(effectiveCommand))
                }
                val listener = object : ScriptExecutionListener {
                    override fun onStart(execution: ScriptExecution) { active = execution }
                    override fun onSuccess(execution: ScriptExecution, result: Any?) {
                        active = null
                        runCatching {
                            check(responseFile.canonicalPath.startsWith(responseDirectory.canonicalPath + File.separator))
                            check(responseFile.isFile) { "Script response file was not created; engine result=${result.toString().take(160)}" }
                            ModuleProtocol.decodeResponse(responseFile.readText())
                        }
                            .onSuccess { if (continuation.isActive) continuation.resume(it) }
                            .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
                        responseFile.delete()
                    }
                    override fun onException(execution: ScriptExecution, e: Throwable) {
                        active = null
                        responseFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
                active = AutoJs.instance.scriptEngineService.execute(
                    JavaScriptFileSource(File(projectDirectory, "bootstrap.js")), listener, config,
                )
                continuation.invokeOnCancellation { responseFile.delete(); cancel() }
            }
        }
    }

    fun cancel() {
        active?.engine?.forceStop()
        active = null
    }

    private fun installPackagedProject(): File {
        val target = File(context.filesDir, "assistant-project").apply { mkdirs() }
        copyAssetTree("project", target)
        return target
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
        } else {
            target.mkdirs()
            children.forEach { copyAssetTree("$assetPath/$it", File(target, it)) }
        }
    }

    companion object {
        val ALLOWED_MODULE_IDS = setOf("weihuda-sign", "withdrawal-coupon", "xianyu-polish")
    }
}
