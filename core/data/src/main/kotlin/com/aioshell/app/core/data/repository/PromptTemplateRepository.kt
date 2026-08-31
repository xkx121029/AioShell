package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.database.AppDatabase
import com.aioshell.app.core.data.database.PromptTemplateEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 领域层提示词模板。 */
data class PromptTemplate(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val builtIn: Boolean = false,
)

/**
 * 提示词模板仓库。内置模板由软件预设（不可删除，可编辑），
 * 用户可自定义新增/编辑/删除模板。
 */
@Singleton
class PromptTemplateRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.promptTemplateDao()

    val templates: Flow<List<PromptTemplate>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 确保内置模板已存在（仅在应用首次升级到含模板表时写入一次）。 */
    suspend fun ensureBuiltIns() {
        val existing = dao.getAll().map { it.id }.toSet()
        val missing = BUILT_IN_TEMPLATES.filter { it.id !in existing }
        if (missing.isNotEmpty()) {
            missing.forEachIndexed { index, t ->
                dao.insert(t.copy(orderIndex = index))
            }
        }
    }

    suspend fun getAll(): List<PromptTemplate> = dao.getAll().map { it.toDomain() }

    suspend fun add(title: String, content: String, category: String): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            PromptTemplateEntity(
                id = id,
                title = title.trim().ifBlank { "未命名模板" },
                content = content,
                category = category.trim().ifBlank { "自定义" },
                builtIn = false,
            )
        )
        return id
    }

    suspend fun update(id: String, title: String, content: String, category: String) {
        val entity = dao.getById(id) ?: return
        dao.update(
            entity.copy(
                title = title.trim().ifBlank { "未命名模板" },
                content = content,
                category = category.trim().ifBlank { "自定义" },
            )
        )
    }

    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        if (!entity.builtIn) dao.deleteById(id)
    }

    private fun PromptTemplateEntity.toDomain() =
        PromptTemplate(id, title, content, category, builtIn)

    /** 内置模板：写作 / 翻译 / 代码 / 总结 / 头脑风暴 五大类，由软件预设。 */
    companion object {
        private val BUILT_IN_TEMPLATES = listOf(
            PromptTemplateEntity(
                id = "builtin_polish",
                title = "润色改写",
                content = "请帮我润色下面这段文字，使其表达更流畅、更专业，保留原意，直接输出润色后的结果：\n\n{{text}}",
                category = "写作",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_shorten",
                title = "精简摘要",
                content = "请将下面这段内容压缩成要点式摘要，保留关键信息，用简洁的中文输出：\n\n{{text}}",
                category = "写作",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_tone",
                title = "语气调整",
                content = "请将下面这段文字改写成{{style}}的语气（如：更正式、更亲切、更幽默），只输出改写结果：\n\n{{text}}",
                category = "写作",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_zh2en",
                title = "中译英",
                content = "请将下面这段中文翻译成地道、自然的英文，直接输出译文：\n\n{{text}}",
                category = "翻译",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_en2zh",
                title = "英译中",
                content = "请将下面这段英文翻译成流畅、准确的中文，直接输出译文：\n\n{{text}}",
                category = "翻译",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_code_review",
                title = "代码审查",
                content = "请审查下面这段代码，指出潜在问题（正确性、性能、可读性、安全性），并给出改进建议，用中文回答：\n\n```\n{{text}}\n```",
                category = "代码",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_code_explain",
                title = "代码解释",
                content = "请用通俗易懂的方式解释下面这段代码的作用、关键逻辑和核心语法，用中文回答：\n\n```\n{{text}}\n```",
                category = "代码",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_code_debug",
                title = "帮我找 Bug",
                content = "下面这段代码运行异常，请帮我定位并解释可能的原因，给出修复后的完整代码，用中文回答：\n\n```\n{{text}}\n```",
                category = "代码",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_summarize",
                title = "内容总结",
                content = "请对下面这段内容进行总结，提炼核心观点，条理清晰，用中文输出：\n\n{{text}}",
                category = "总结",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_meeting_notes",
                title = "会议纪要",
                content = "请根据下面的讨论内容整理一份会议纪要，包含议题、结论、待办事项，用中文输出：\n\n{{text}}",
                category = "总结",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_brainstorm",
                title = "头脑风暴",
                content = "围绕以下主题进行头脑风暴，给出有创意、可落地的点子清单（10 个以上），用中文输出：\n\n{{text}}",
                category = "头脑风暴",
                builtIn = true,
            ),
            PromptTemplateEntity(
                id = "builtin_outline",
                title = "生成大纲",
                content = "请为下面的主题生成一份结构清晰的内容大纲，包含章节标题与要点说明，用中文输出：\n\n{{text}}",
                category = "头脑风暴",
                builtIn = true,
            ),
        )
    }
}
