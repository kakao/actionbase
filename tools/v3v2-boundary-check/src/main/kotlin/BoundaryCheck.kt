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
    val v3 = graph.extractAllEndpoints().filter { it.path.startsWith("/graph/v3") }
    reportByPath(v3, graph.traceEndpointDeps(v3), verbose)
}

// ── Call graph ──

class CallGraph {
    private val classes = linkedMapOf<String, ClassNode>()
    private val implementors = hashMapOf<String, MutableSet<String>>()
    private val innerToOuter = hashMapOf<String, Pair<String, String?>>()
    private val targets by lazy { Config.TARGET_CLASSES.map { it.replace('.', '/') }.toSet() }

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

    // ── By class ──

    data class Edge(val callerCls: String, val callerMtd: String, val calleeCls: String, val calleeMtd: String)

    fun extractCallsToTarget(): List<Edge> {
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

    // ── By path ──

    data class Endpoint(val httpMethod: String, val path: String, val cls: String, val method: String, val desc: String) {
        val controller: String get() = cls.substringAfterLast('/')
    }
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
                .firstOrNull { it.desc == reqMapping }?.let { annStrings(it).firstOrNull() } ?: ""
            for (mn in cn.methods) for (ann in mn.visibleAnnotations.orEmpty()) {
                val http = mappings[ann.desc]
                if (http != null) {
                    for (p in annStrings(ann).ifEmpty { listOf("") })
                        endpoints += Endpoint(http, prefix + p, cn.name, mn.name, mn.desc)
                    break
                }
                if (ann.desc == reqMapping) {
                    val methods = annEnums(ann, "method").ifEmpty { listOf("GET") }
                    for (m in methods) for (p in annStrings(ann).ifEmpty { listOf("") })
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
                    val d = classes[cls]?.methods?.firstOrNull { it.name == mtd && it.desc == insn.desc }?.desc ?: insn.desc
                    val callee = MKey(cls, mtd, d)
                    if (caller != callee) adj.getOrPut(caller) { mutableSetOf() }.add(callee)
                }
            }
        }

        val deps = mutableListOf<V2Dep>()
        for (ep in endpoints) {
            val seed = MKey(ep.cls, ep.method, ep.desc)
            val visited = mutableMapOf<MKey, MKey?>(seed to null)
            val queue = ArrayDeque<MKey>().apply { add(seed) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val outer = innerToOuter[cur.cls]?.first ?: cur.cls
                if (isAdapter(outer)) continue
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

    private fun isAdapter(cls: String) =
        Config.EXCLUDED_CLASS_PREFIXES.any { cls.substringAfterLast('/').startsWith(it) }

    private fun collapse(cls: String, method: String): Pair<String, String> {
        val (outer, enclosing) = innerToOuter[cls] ?: return cls.dotted to method
        return outer.dotted to (enclosing ?: method)
    }

    private fun resolve(call: MethodInsnNode): List<Pair<String, String>> {
        if (call.opcode == Opcodes.INVOKESTATIC || call.opcode == Opcodes.INVOKESPECIAL)
            return if (hasMethod(call.owner, call.name, call.desc)) listOf(call.owner to call.name) else emptyList()
        val impls = when {
            call.opcode == Opcodes.INVOKEINTERFACE -> implementors[call.owner]
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
    private fun annStrings(ann: AnnotationNode, key: String = "value"): List<String> {
        val values = ann.values ?: return emptyList()
        for (i in values.indices step 2)
            if (values[i] as String == key || (key == "value" && values[i] as String == "path"))
                return values[i + 1] as? List<String> ?: continue
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun annEnums(ann: AnnotationNode, key: String): List<String> {
        val values = ann.values ?: return emptyList()
        for (i in values.indices step 2)
            if (values[i] as String == key) {
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

fun isExcluded(fqcn: String): Boolean {
    val simple = fqcn.substringAfterLast('.')
    if (Config.EXCLUDED_CLASS_PREFIXES.any { simple.startsWith(it) }) return true
    val pkg = fqcn.substringBeforeLast('.')
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

    grouped.entries.forEachIndexed { i, (src, tgts) ->
        println("  [${i + 1}] $src (${tgts.values.sumOf { it.size }} edges)")
        if (verbose) {
            tgts.forEach { (tgt, mtds) -> println("      → $tgt"); mtds.forEach { println("          $it") } }
            println()
        }
    }
}

fun reportByPath(endpoints: List<CallGraph.Endpoint>, deps: List<CallGraph.V2Dep>, verbose: Boolean) {
    val allPaths = endpoints.map { it.path }.distinct()
    val depsByPath = deps.groupBy { it.endpoint.path }
    val depPaths = allPaths.filter { it in depsByPath }
    val ctrlByPath = endpoints.associate { it.path to it.controller }

    println("=== V2 Dependencies by Path ===\n")
    println("  ${depPaths.size}/${allPaths.size} paths with direct V2 dependencies\n")

    if (!verbose) {
        depPaths.forEach { println("  ${ctrlByPath[it]} — $it") }
        return
    }
    depPaths.forEachIndexed { i, path ->
        println("  [${i + 1}] ${ctrlByPath[path]} — $path")
        val pd = depsByPath[path]!!
        for (tgt in pd.map { it.targetCls }.distinct().sorted()) {
            println("      → $tgt")
            pd.filter { it.targetCls == tgt }.forEach { println("          ${it.chain.joinToString(" → ")}") }
        }
        println()
    }
}
