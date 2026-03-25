import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.nio.file.*
import kotlin.io.path.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val root = args.firstOrNull() ?: "../.."
    val graph = CallGraph()
    Config.CLASS_DIRS.map { Path(root, it) }.filter { it.exists() }.forEach(graph::load)
    println("Classes: ${graph.classes.size}")
    graph.buildHierarchy()
    val edges = graph.extractEdges()
    println("Edges: ${edges.size}\n")
    report(edges)
}

// ── Call graph extraction ──

class CallGraph {
    val classes = linkedMapOf<String, ClassNode>()
    val implementors = hashMapOf<String, MutableSet<String>>()
    val innerToOuter = hashMapOf<String, Pair<String, String?>>()

    fun load(dir: Path) {
        Files.walk(dir)
            .filter { it.toString().endsWith(".class") }
            .forEach { path ->
                runCatching {
                    val cn = ClassNode()
                    ClassReader(path.readBytes()).accept(cn, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    if (cn.name.startsWith(Config.SCOPE)) classes[cn.name] = cn
                }
            }
    }

    fun buildHierarchy() {
        for (cn in classes.values) {
            if (cn.isAbstractOrInterface) continue
            cn.interfaces.forEach { addImpl(it, cn.name) }
            var c = cn.superName
            while (c != null && c in classes) {
                val sup = classes[c]!!
                if (sup.access and Opcodes.ACC_ABSTRACT != 0) addImpl(c, cn.name)
                sup.interfaces.forEach { addImpl(it, cn.name) }
                c = sup.superName
            }
        }
        for (cn in classes.values) {
            if ('$' !in cn.name) continue
            val outer = cn.name.substringBefore('$')
            if (outer !in classes) continue
            val token = cn.name.substringAfter('$').substringBefore('$')
            innerToOuter[cn.name] = outer to token.takeUnless { it.all(Char::isDigit) }
        }
    }

    fun extractEdges(): Set<String> {
        val edges = linkedSetOf<String>()
        for (cn in classes.values)
            for (mn in cn.methods) {
                if (mn.name == "<clinit>" || mn.instructions == null) continue
                val caller = collapse(cn.name, mn.name)
                for (insn in mn.instructions) {
                    if (insn !is MethodInsnNode || !insn.owner.startsWith(Config.SCOPE)) continue
                    for ((cls, mtd) in resolve(insn)) {
                        val callee = collapse(cls, mtd)
                        if (caller != callee && !mtd.startsWith("<init>") && !mtd.startsWith("access$"))
                            edges += "$caller\t$callee"
                    }
                }
            }
        return edges
    }

    private fun collapse(cls: String, method: String): String {
        val (outer, enclosing) = innerToOuter[cls] ?: return "${cls.dotted}.$method"
        return "${outer.dotted}.${enclosing ?: method}"
    }

    private fun resolve(call: MethodInsnNode): List<Pair<String, String>> {
        val op = call.opcode
        if (op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKESPECIAL)
            return if (hasMethod(call.owner, call.name, call.desc)) listOf(call.owner to call.name) else emptyList()

        val impls = when {
            op == Opcodes.INVOKEINTERFACE -> implementors[call.owner]
            classes[call.owner]?.isAbstractOrInterface == true -> implementors[call.owner]
            else -> null
        }
        if (!impls.isNullOrEmpty())
            return impls.mapNotNull { findInHierarchy(it, call.name, call.desc)?.let { c -> c to call.name } }

        return if (hasMethod(call.owner, call.name, call.desc)) listOf(call.owner to call.name) else emptyList()
    }

    private fun hasMethod(cls: String, name: String, desc: String) =
        classes[cls]?.methods?.any { it.name == name && it.desc == desc } == true

    private fun findInHierarchy(cls: String, name: String, desc: String): String? {
        var c: String? = cls
        while (c != null && c in classes) {
            if (hasMethod(c, name, desc)) return c
            c = classes[c]!!.superName
        }
        return null
    }

    private fun addImpl(iface: String, impl: String) =
        implementors.getOrPut(iface) { mutableSetOf() }.add(impl)

    private val ClassNode.isAbstractOrInterface get() = access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0
    private val String.dotted get() = replace('/', '.')
}

// ── Classification ──

fun classify(fqcn: String): String? {
    val pkg = fqcn.substringBeforeLast('.')
    return Config.VERSIONS.entries.firstOrNull { (_, pkgs) -> pkg in pkgs }?.key
}

// ── Report ──

fun report(edges: Set<String>) {
    val versions = hashMapOf<String, String?>()
    val unclassified = sortedSetOf<String>()

    for (e in edges) for (id in e.split("\t")) {
        val cls = id.substringBeforeLast('.')
        versions.getOrPut(cls) {
            classify(cls).also { if (it == null) unclassified += cls.substringBeforeLast('.') }
        }
    }

    if (unclassified.isNotEmpty()) {
        System.err.println("ERROR: Unclassified packages. Add to Config.kt.\n")
        unclassified.forEach { System.err.println("    $it") }
        exitProcess(1)
    }

    data class Leak(val srcCls: String, val tgtCls: String, val srcMtd: String, val tgtMtd: String)

    val leaks = mutableListOf<Leak>()
    var bridges = 0L

    for (e in edges) {
        val (from, to) = e.split("\t")
        val sv = versions[from.substringBeforeLast('.')] ?: continue
        val tv = versions[to.substringBeforeLast('.')] ?: continue
        if (sv == tv) continue
        for ((f, t, _) in Config.LEAKS)
            if (f == sv && t == tv) leaks += Leak(from.className, to.className, from.methodName, to.methodName)
        for ((f, t) in Config.BRIDGES)
            if (f == sv && t == tv) { bridges++; break }
    }

    // Group: source class → target class → methods
    val grouped = leaks.groupBy { it.srcCls }
        .toSortedMap()
        .mapValues { (_, v) ->
            v.groupBy { it.tgtCls }.toSortedMap()
                .mapValues { (_, v2) -> v2.map { "${it.srcMtd} → ${it.tgtMtd}" }.toSortedSet() }
        }

    println("=== LEAK REPORT ===\n")
    Config.LEAKS.forEach { (f, t, desc) -> println("  Rule: $f → $t ($desc)") }
    println("\n  Leaks:   ${leaks.size} edges, ${grouped.size} source classes")
    println("  Bridges: $bridges edges\n")

    if (leaks.isEmpty()) { println("  No leaks found."); return }

    grouped.entries.forEachIndexed { i, (src, targets) ->
        val n = targets.values.sumOf { it.size }
        println("  [${i + 1}] $src ($n edges)")
        targets.forEach { (tgt, methods) ->
            println("      → $tgt")
            methods.forEach { println("          $it") }
        }
        println()
    }
}

private val String.className get() = split('.').let { it[it.size - 2] }
private val String.methodName get() = substringAfterLast('.')
