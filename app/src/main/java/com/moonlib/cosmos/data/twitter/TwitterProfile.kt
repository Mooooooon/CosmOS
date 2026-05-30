package com.moonlib.cosmos.data.twitter

/**
 * 推特独立主页档案模型
 * 
 * 职责单一：作为推特中账号资料的纯数据类载体。
 */
data class TwitterProfile(
    val characterId: String,  // "user" 表示用户本身，或者对应系统人设档案的 ID
    val nickname: String,     // 昵称 (例如: 爱丽丝)
    val username: String,     // 用户名 (无须带 @，例如: alice_wonderland，界面渲染为 @alice_wonderland)
    val avatar: String,       // 本地头像路径，若为空白则代表未自定义（使用系统档案头像或默认）
    val bio: String,          // 个人简介
    val isFollowed: Boolean = false // 是否已关注 (对于用户本身固定为 true)
)
