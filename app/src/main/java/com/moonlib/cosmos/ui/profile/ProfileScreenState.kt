package com.moonlib.cosmos.ui.profile

/**
 * 档案 APP 内部路由子页面状态
 */
sealed interface ProfileScreenState {
    object List : ProfileScreenState
    
    /**
     * @param profileId 如果为 null 表示新建人设，否则为编辑已有的人设
     * @param isPlayer 明确该编辑状态定死是“用户设定”还是“角色设定”
     */
    data class Edit(
        val profileId: String?,
        val isPlayer: Boolean
    ) : ProfileScreenState
}
