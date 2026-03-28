package tech.done.ads.parser.error

sealed class XmlParseError(
    open val code: Code,
    override val message: String,
    open val line: Int? = null,
    open val column: Int? = null,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {

    enum class Code {
        MalformedXml,
        UnsupportedVersion,
        WrapperDepthExceeded,
        WrapperLoopDetected,
        MissingRequiredField,
    }
}

class VASTParseError(
    override val code: Code,
    override val message: String,
    override val line: Int? = null,
    override val column: Int? = null,
    override val cause: Throwable? = null,
) : XmlParseError(code, message, line, column, cause)

class VMAPParseError(
    override val code: Code,
    override val message: String,
    override val line: Int? = null,
    override val column: Int? = null,
    override val cause: Throwable? = null,
) : XmlParseError(code, message, line, column, cause)

