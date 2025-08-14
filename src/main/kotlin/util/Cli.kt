package util

data class CliArgs(
    val specPath: String,
    val agent2Output: String,
    val agent2System: String
)

object Cli {
    data class Defaults(
        val specPath: String,
        val agent2Output: String,
        val agent2System: String
    )

    fun parse(args: Array<String>, defaults: Defaults): CliArgs {
        var specPath = defaults.specPath
        var agent2Output = defaults.agent2Output
        var agent2System = defaults.agent2System

        fun takeValue(i: Int, key: String, arr: Array<String>): Pair<String, Int> {
            val cur = arr[i]
            if (cur.contains("=")) {
                val v = cur.substringAfter("=", "")
                if (v.isBlank()) invalid("$key requires a value.")
                return v to i
            }
            if (i + 1 >= arr.size) invalid("$key requires a value.")
            val v = arr[i + 1]
            if (v.startsWith("--")) invalid("$key requires a value.")
            return v to (i + 1)
        }

        var i = 0
        while (i < args.size) {
            when {
                args[i].startsWith("--specPath") -> {
                    val (v, ni) = takeValue(i, "--specPath", args)
                    specPath = v; i = ni
                }
                args[i].startsWith("--agent2Output") -> {
                    val (v, ni) = takeValue(i, "--agent2Output", args)
                    agent2Output = v; i = ni
                }
                args[i].startsWith("--agent2System") -> {
                    val (v, ni) = takeValue(i, "--agent2System", args)
                    agent2System = v; i = ni
                }
                else -> invalid("Unknown argument: ${args[i]}")
            }
            i++
        }

        if (specPath.isBlank()) invalid("specPath cannot be blank.")
        if (agent2Output.isBlank()) invalid("agent2Output cannot be blank.")
        if (agent2System.isBlank()) invalid("agent2System cannot be blank.")

        // Ensure parent directories for files that will be written
        FileUtils.ensureParentDir(specPath)
        FileUtils.ensureParentDir(agent2Output)
        FileUtils.ensureParentDir(agent2System)

        return CliArgs(specPath, agent2Output, agent2System)
    }

    private fun invalid(msg: String): Nothing = throw IllegalArgumentException(msg)
}
