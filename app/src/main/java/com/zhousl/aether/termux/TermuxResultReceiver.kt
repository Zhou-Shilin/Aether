package com.zhousl.aether.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        try {
            val executionId = intent.getIntExtra(TermuxContract.ExecutionIdExtra, -1)
            if (executionId < 0) return

            val resultBundle = intent.extras?.getBundle(TermuxContract.ResultBundleExtra)
                ?: intent.extras?.findFirstBundle()

            val result = TermuxCommandResult(
                stdout = resultBundle?.getString(TermuxContract.ResultStdoutExtra).orEmpty(),
                stderr = resultBundle?.getString(TermuxContract.ResultStderrExtra).orEmpty(),
                exitCode = resultBundle?.getInt(TermuxContract.ResultExitCodeExtra, -1) ?: -1,
                err = resultBundle?.getInt(TermuxContract.ResultErrExtra, -1) ?: -1,
                errmsg = resultBundle?.getString(TermuxContract.ResultErrmsgExtra).orEmpty(),
            )

            TermuxPendingResults.complete(executionId, result)
        } finally {
            pendingResult.finish()
        }
    }
}

private fun Bundle.findFirstBundle(): Bundle? =
    keySet().firstNotNullOfOrNull(::getBundle)
