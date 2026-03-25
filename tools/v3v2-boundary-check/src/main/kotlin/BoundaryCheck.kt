import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.nio.file.*
import kotlin.io.path.*

fun main(args: Array<String>) {
    val root = args.firstOrNull() ?: "../.."
    val graph = CallGraph()
    Config.CLASS_DIRS.map { Path(root, it) }.filter { it.exists() }.forEach(graph::load)
    println("Classes: ${graph.classes.size}")
    graph.buildHierarchy()
    val edges = graph.extractCallsToTarget()
    println("Calls to Graph: ${edges.size}\n")
    report(edges)
}

// ── Call graph extraction ──

data class Edge(val callerCls: String, val callerMtd: String, val calleeCls: String, val calleeMtd: String)

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

    fun extractCallsToTarget(): List<Edge> {
        val targetInternal = Config.TARGET_CLASS.replace('.', '/')
        val edges = mutableListOf<Edge>()

        for (cn in classes.values)
            for (mn in cn.methods) {
                if (mn.name == "<clinit>" || mn.instructions == null) continue
                val (callerCls, callerMtd) = collapse(cn.name, mn.name)
                for (insn in mn.instructions) {
                    if (insn !is MethodInsnNode || !insn.owner.startsWith(Config.SCOPE)) continue
                    for ((cls, mtd) in resolve(insn)) {
                        if (cls != targetInternal) continue
                        if (mtd.startsWith("<init>") || mtd.startsWith("access$")) continue
                        val (cCls, cMtd) = collapse(cls, mtd)
                        if (callerCls != cCls)
                            edges += Edge(callerCls, callerMtd, cCls, cMtd)
                    }
                }
            }
        return edges
    }

    private fun collapse(cls: String, method: String): Pair<String, String> {
        val (outer, enclosing) = innerToOuter[cls] ?: return cls.dotted to method
        return outer.dotted to (enclosing ?: method)
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

// ── Report ──

fun isExcluded(callerFqcn: String): Boolean {
    // class exclude always wins (adapter classes)
    val simpleName = callerFqcn.substringAfterLast('.')
    if (Config.EXCLUDED_CLASS_PREFIXES.any { simpleName.startsWith(it) }) return true
    // include overrides package exclude
    val pkg = callerFqcn.substringBeforeLast('.')
    if (Config.INCLUDED_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }) return false
    if (Config.EXCLUDED_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }) return true
    return false
}

fun report(edges: List<Edge>) {
    val leaks = edges.filterNot { isExcluded(it.callerCls) }

    val grouped = leaks.groupBy { it.callerCls }
        .toSortedMap()
        .mapValues { (_, v) ->
            v.map { "${it.callerMtd} → ${it.calleeMtd}" }.toSortedSet()
        }

    println("=== Graph Direct-Call Report ===\n")
    println("  Target:   ${Config.TARGET_CLASS}")
    println("  Total callers: ${edges.map { it.callerCls }.toSet().size}")
    println("  Excluded: ${edges.count { isExcluded(it.callerCls) }} edges")
    println("  Leaks:    ${leaks.size} edges, ${grouped.size} classes\n")

    if (leaks.isEmpty()) { println("  No leaks found."); return }

    grouped.entries.forEachIndexed { i, (src, methods) ->
        println("  [${i + 1}] $src (${methods.size} edges)")
        methods.forEach { println("          $it") }
        println()
    }
}
