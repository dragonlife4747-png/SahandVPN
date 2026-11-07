package com.v2ray.ang.simple

/** Pure policy: never infer OS lockdown from app preferences or always-on alone. */
object KillSwitchPolicy {
    fun protected(alwaysOn: Boolean?, lockdown: Boolean?): Boolean? =
        if (alwaysOn == null || lockdown == null) null else alwaysOn && lockdown

    fun recover(selected: String?, winners: List<String>, valid: (String) -> Boolean): String? =
        selected?.takeIf { it.isNotBlank() && valid(it) }
            ?: winners.firstOrNull { it.isNotBlank() && valid(it) }
}
