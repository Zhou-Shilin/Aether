package com.zhousl.aether.agentmode

/**
 * Android allocates [AgentModePerUserUidRange] uids per user, so a process uid also identifies the
 * user that process runs as. `UserHandle.getUserId` computes the same value but is hidden API and
 * cannot be referenced from app code, hence this constant.
 */
internal const val AgentModePerUserUidRange = 100_000

/** Returns the Android user id that [uid] belongs to. */
internal fun agentModeUserIdFromUid(uid: Int): Int = uid / AgentModePerUserUidRange
