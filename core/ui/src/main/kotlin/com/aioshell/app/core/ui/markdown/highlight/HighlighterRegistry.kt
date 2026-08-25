package com.aioshell.app.core.ui.markdown.highlight

/** 语法高亮语言注册表：关键字与注释标记。 */
object HighlighterRegistry {

    private val aliases = mapOf(
        "kotlin" to "kotlin",
        "python" to "python",
        "py" to "python",
        "java" to "java",
        "javascript" to "javascript",
        "js" to "javascript",
        "typescript" to "javascript",
        "ts" to "javascript",
        "json" to "json",
        "bash" to "bash",
        "shell" to "bash",
        "sh" to "bash",
        "sql" to "sql",
    )

    private val keywords = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class",
            "object", "interface", "data", "private", "public", "protected", "internal",
            "import", "package", "suspend", "null", "true", "false", "this", "is", "in",
            "try", "catch", "finally", "throw", "do", "break", "continue", "companion",
        ),
        "python" to setOf(
            "def", "if", "elif", "else", "for", "while", "return", "import", "from", "class",
            "try", "except", "finally", "with", "as", "lambda", "pass", "None", "True",
            "False", "async", "await", "yield", "raise", "break", "continue", "global", "nonlocal", "del",
        ),
        "java" to setOf(
            "public", "private", "protected", "class", "interface", "void", "int", "long",
            "double", "float", "boolean", "if", "else", "for", "while", "return", "new",
            "import", "package", "static", "final", "try", "catch", "throw", "throws",
            "extends", "implements", "null", "true", "false", "this", "super", "do", "break", "continue",
        ),
        "javascript" to setOf(
            "const", "let", "var", "function", "if", "else", "for", "while", "return",
            "class", "import", "export", "from", "async", "await", "new", "try", "catch",
            "throw", "null", "true", "false", "this", "typeof", "instanceof", "extends", "yield", "break", "continue",
        ),
        "json" to setOf("true", "false", "null"),
        "bash" to setOf("if", "then", "else", "fi", "for", "while", "do", "done", "case", "esac", "function", "return", "export", "local", "exit", "in"),
        "sql" to setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE",
            "JOIN", "LEFT", "RIGHT", "INNER", "GROUP", "BY", "ORDER", "AND", "OR", "NOT",
            "NULL", "AS", "ON", "LIMIT", "VALUES", "INTO", "SET", "ALTER", "DROP", "ADD", "PRIMARY", "KEY",
        ),
    )

    private val commentTokens = mapOf(
        "kotlin" to setOf("//", "/*", "*/"),
        "java" to setOf("//", "/*", "*/"),
        "python" to setOf("#"),
        "javascript" to setOf("//", "/*", "*/"),
        "json" to emptySet(),
        "bash" to setOf("#"),
        "sql" to setOf("--", "/*", "*/"),
    )

    private val lineCommentMode = mapOf(
        "kotlin" to "//",
        "java" to "//",
        "javascript" to "//",
        "python" to "#",
        "bash" to "#",
        "sql" to "--",
    )

    fun normalize(language: String?): String? = aliases[language?.lowercase()]

    fun isKeyword(language: String, word: String): Boolean {
        val set = keywords[language] ?: return false
        return if (language == "sql") set.contains(word.uppercase()) || set.contains(word)
        else set.contains(word)
    }

    fun lineCommentOf(language: String): String? = lineCommentMode[language]
    fun hasBlockComment(language: String): Boolean = (commentTokens[language] ?: emptySet()).contains("/*")
}