package com.aioshell.app.core.data.model

import kotlinx.serialization.Serializable

/**
 * AI 人格预设：为当前对话注入身份与回答风格。
 * [identity] 身份定位（system 中的角色）；[style] 回答风格；
 * [prompt] 额外的行为约束（可为空）；[builtin] 内置预设标志。
 */
@Serializable
data class Persona(
    val id: String,
    val name: String,
    val identity: String = "",
    val style: String = "",
    val prompt: String = "",
    val builtin: Boolean = false,
) {
    /** 是否包含可用于注入的实质内容。 */
    val hasContent: Boolean
        get() = identity.isNotBlank() || style.isNotBlank() || prompt.isNotBlank()
}

/** 内置人格预设（仅可编辑名称，不可删除）。 */
object BuiltinPersonas {
    val list: List<Persona> = listOf(
        Persona(
            id = "builtin_general", name = "通用助手",
            identity = "你是一名通用 AI 助手。",
            style = "回答友好、简洁、准确，优先直接给出结论。",
            prompt = "", builtin = true,
        ),
        Persona(
            id = "builtin_coder", name = "代码专家",
            identity = "你是一名资深软件工程师。",
            style = "优先给出可直接运行的最小示例，并附简短解释与注意事项。",
            prompt = "涉及代码时请标注语言、保持缩进清晰。", builtin = true,
        ),
        Persona(
            id = "builtin_translator", name = "翻译官",
            identity = "你是一名专业翻译员。",
            style = "准确贴合原文语气，中英双向均可，必要时给出多选译法。",
            prompt = "仅翻译，不额外评论。", builtin = true,
        ),
        Persona(
            id = "builtin_writer", name = "写作助手",
            identity = "你是一名资深中文写作编辑。",
            style = "结构清晰、凝练通顺、避免空话套话。",
            prompt = "", builtin = true,
        ),
        Persona(
            id = "builtin_tutor", name = "学习导师",
            identity = "你是一名耐心讲解的老师。",
            style = "循序渐进、多举例、及时小结，鼓励分步思考。",
            prompt = "", builtin = true,
        ),
    )
}