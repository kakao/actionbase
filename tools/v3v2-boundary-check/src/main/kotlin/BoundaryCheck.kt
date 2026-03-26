import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.nio.file.*
import kotlin.io.path.*

fun main(args: Array<String>) {
    val verbose = "--verbose" in args
    val root = args.firstOrNull { !it.startsWith("--") } ?: "../.."
    val graph = CallGraph()
    Config.CLASS_DIRS.map { Path(root, it) }.filter { it.exists() }.forEach(graph::load)
    graph.buildHierarchy()

    reportByClass(graph.extractCallsToTarget(), verbose)
    println()
    val v3endpoints = graph.extractAllEndpoints().filter { it.path.startsWith("/graph/v3") }
    reportByPath(v3endpoints, graph.traceEndpointDeps(v3endpoints), verbose)
}

// ── Call graph ──

class CallGraph {
    private val classes = linkedMapOf<String, ClassNode>()
    private val implementors = hashMapOf<String, MutableSet<String>>()
    private val innerToOuter = hashMapOf<String, Pair<String, String?>>()

    fun load(dir: Path) {
        Files.walk(dir).filter { it.toString().endsWith(".class") }.forEach { path ->
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

    // ── By class: who calls V2 target classes ──

    data class Edge(val callerCls: String, val callerMtd: String, val calleeCls: String, val calleeMtd: String)

    fun extractCallsToTarget(): List<Edge> {
        val targets = Config.TARGET_CLASSES.map { it.replace('.', '/') }.toSet()
        val targetDotted = Config.TARGET_CLASSES.toSet()
        val edges = mutableListOf<Edge>()
        for (cn in classes.values) for (mn in cn.methods) {
            if (mn.name == "<clinit>" || mn.instructions == null) continue
            val (callerCls, callerMtd) = collapse(cn.name, mn.name)
            for (insn in mn.instructions) {
                if (insn is FieldInsnNode && insn.owner.dotted in targetDotted) {
                    val (cCls, _) = collapse(insn.owner, insn.name)
                    if (callerCls != cCls) edges += Edge(callerCls, callerMtd, cCls, insn.name)
                    continue
                }
                if (insn !is MethodInsnNode || !insn.owner.startsWith(Config.SCOPE)) continue
                for ((cls, mtd) in resolve(insn)) {
                    if (cls !in targets || mtd.startsWith("access$")) continue
                    val (cCls, cMtd) = collapse(cls, mtd)
                    if (callerCls != cCls) edges += Edge(callerCls, callerMtd, cCls, cMtd)
                }
            }
        }
        return edges
    }

    // ── By path: scan all endpoints, trace V2 dependencies ──

    data class Endpoint(val httpMethod: String, val path: String, val cls: String, val method: String, val desc: String)
    data class V2Dep(val endpoint: Endpoint, val targetCls: String, val chain: List<String>)

    fun extractAllEndpoints(): List<Endpoint> {
        val mappings = mapOf(
            "Lorg/springframework/web/bind/annotation/GetMapping;" to "GET",
            "Lorg/springframework/web/bind/annotation/PostMapping;" to "POST",
            "Lorg/springframework/web/bind/annotation/PutMapping;" to "PUT",
            "Lorg/springframework/web/bind/annotation/DeleteMapping;" to "DELETE",
            "Lorg/springframework/web/bind/annotation/PatchMapping;" to "PATCH",
        )
        val reqMapping = "Lorg/springframework/web/bind/annotation/RequestMapping;"
        val endpoints = mutableListOf<Endpoint>()

        for (cn in classes.values) {
            val prefix = cn.visibleAnnotations.orEmpty()
                .firstOrNull { it.desc == reqMapping }?.let { annPaths(it).firstOrNull() } ?: ""
            for (mn in cn.methods) for (ann in mn.visibleAnnotations.orEmpty()) {
                val http = mappings[ann.desc]
                if (http != null) {
                    for (p in annPaths(ann).ifEmpty { listOf("") })
                        endpoints += Endpoint(http, prefix + p, cn.name, mn.name, mn.desc)
                    break
                }
                if (ann.desc == reqMapping) {
                    val methods = annMethods(ann).ifEmpty { listOf("GET") }
                    for (m in methods) for (p in annPaths(ann).ifEmpty { listOf("") })
                        endpoints += Endpoint(m, prefix + p, cn.name, mn.name, mn.desc)
                    break
                }
            }
        }
        val order = mapOf("GET" to 0, "POST" to 1, "PUT" to 2, "PATCH" to 3, "DELETE" to 4)
        return endpoints.sortedWith(compareBy({ it.path }, { order[it.httpMethod] ?: 9 }))
    }

    fun traceEndpointDeps(endpoints: List<Endpoint>): List<V2Dep> {
        data class MKey(val cls: String, val name: String, val desc: String)
        val adj = hashMapOf<MKey, MutableSet<MKey>>()
        for (cn in classes.values) for (mn in cn.methods) {
            if (mn.instructions == null) continue
            val caller = MKey(cn.name, mn.name, mn.desc)
            for (insn in mn.instructions) {
                if (insn !is MethodInsnNode || !insn.owner.startsWith(Config.SCOPE)) continue
                for ((cls, mtd) in resolve(insn)) {
                    val desc = classes[cls]?.methods?.firstOrNull { it.name == mtd && it.desc == insn.desc }?.desc ?: insn.desc
                    val callee = MKey(cls, mtd, desc)
                    if (caller != callee) adj.getOrPut(caller) { mutableSetOf() }.add(callee)
                }
            }
        }

        val targets = Config.TARGET_CLASSES.map { it.replace('.', '/') }.toSet()
        val deps = mutableListOf<V2Dep>()
        for (ep in endpoints) {
            val seed = MKey(ep.cls, ep.method, ep.desc)
            val visited = mutableMapOf<MKey, MKey?>(seed to null)
            val queue = ArrayDeque<MKey>().apply { add(seed) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val outer = innerToOuter[cur.cls]?.first ?: cur.cls
                if (Config.EXCLUDED_CLASS_PREFIXES.any { outer.substringAfterLast('/').startsWith(it) }) continue
                if (outer in targets) {
                    val chain = generateSequence(cur) { visited[it] }.map { "${it.cls.dotted}.${it.name}" }.toList().reversed()
                    deps += V2Dep(ep, outer.dotted, chain)
                    continue
                }
                for (next in adj[cur].orEmpty()) if (next !in visited) { visited[next] = cur; queue.add(next) }
            }
        }
        return deps
    }

    // ── Internals ──

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
        while (c != null && c in classes) { if (hasMethod(c, name, desc)) return c; c = classes[c]!!.superName }
        return null
    }

    private fun addImpl(iface: String, impl: String) = implementors.getOrPut(iface) { mutableSetOf() }.add(impl)

    @Suppress("UNCHECKED_CAST")
    private fun annPaths(ann: AnnotationNode): List<String> {
        val values = ann.values ?: return emptyList()
        for (i in values.indices step 2)
            if (values[i] as String in listOf("value", "path"))
                return values[i + 1] as? List<String> ?: continue
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun annMethods(ann: AnnotationNode): List<String> {
        val values = ann.values ?: return emptyList()
        for (i in values.indices step 2)
            if (values[i] as String == "method") {
                val list = values[i + 1] as? List<*> ?: continue
                return list.filterIsInstance<Array<*>>().map { it[1] as String }
                    .ifEmpty { list.chunked(2).mapNotNull { it.getOrNull(1) as? String } }
            }
        return emptyList()
    }

    private val ClassNode.isAbstractOrInterface get() = access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0
    private val String.dotted get() = replace('/', '.')
}

// ── Report ──

fun isExcluded(callerFqcn: String): Boolean {
    val simpleName = callerFqcn.substringAfterLast('.')
    if (Config.EXCLUDED_CLASS_PREFIXES.any { simpleName.startsWith(it) }) return true
    val pkg = callerFqcn.substringBeforeLast('.')
    if (Config.INCLUDED_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }) return false
    if (Config.EXCLUDED_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }) return true
    return false
}

fun reportByClass(edges: List<CallGraph.Edge>, verbose: Boolean) {
    val direct = edges.filterNot { isExcluded(it.callerCls) }
    val grouped = direct.groupBy { it.callerCls }.toSortedMap()
        .mapValues { (_, v) ->
            v.groupBy { it.calleeCls }.toSortedMap()
                .mapValues { (_, v2) -> v2.map { "${it.callerMtd} → ${it.calleeMtd}" }.toSortedSet() }
        }

    println("=== V2 Dependencies by Class ===\n")
    println("  ${direct.size} edges, ${grouped.size} classes\n")
    if (direct.isEmpty()) { println("  No direct V2 dependencies found. Consider reverting #221."); return }

    grouped.entries.forEachIndexed { i, (src, targets) ->
        println("  [${i + 1}] $src (${targets.values.sumOf { it.size }} edges)")
        if (verbose) {
            targets.forEach { (tgt, methods) -> println("      → $tgt"); methods.forEach { println("          $it") } }
            println()
        }
    }
}

fun reportByPath(endpoints: List<CallGraph.Endpoint>, deps: List<CallGraph.V2Dep>, verbose: Boolean) {
    val byKey = deps.groupBy { "${it.endpoint.httpMethod} ${it.endpoint.path}" }
    val allKeys = endpoints.map { "${it.httpMethod} ${it.path}" }.distinct()
    val depKeys = allKeys.filter { it in byKey }

    // Group by path, collect methods
    val byPath = linkedMapOf<String, MutableList<String>>()
    for (key in depKeys) byPath.getOrPut(key.substringAfter(' ')) { mutableListOf() }.add(key.substringBefore(' '))

    println("=== V2 Dependencies by Path ===\n")
    println("  ${depKeys.size}/${allKeys.size} paths with direct V2 dependencies\n")

    if (!verbose) {
        byPath.forEach { (path, methods) -> println("  $path (${methods.joinToString()})") }
        return
    }
    byPath.entries.forEachIndexed { i, (path, methods) ->
        println("  [${i + 1}] $path (${methods.joinToString()})")
        val allDeps = methods.flatMap { byKey["$it $path"].orEmpty() }
        for (tgt in allDeps.map { it.targetCls }.distinct().sorted()) {
            println("      → $tgt")
            allDeps.filter { it.targetCls == tgt }.forEach { println("          ${it.chain.joinToString(" → ")}") }
        }
        println()
    }
}
