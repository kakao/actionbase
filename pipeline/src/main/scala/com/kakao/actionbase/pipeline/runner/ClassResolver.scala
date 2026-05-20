package com.kakao.actionbase.pipeline.runner

import java.io.File
import java.net.{JarURLConnection, URL}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

// Resolves a YAML class name to a Class, loaded without running `<clinit>` so callers can reject non-Job/non-Step
// candidates (e.g., `java.lang.Runtime`) before side-effecting init. A name resolves as (1) a fully-qualified name,
// (2) `<root>.<name>` per root, or (3) failing those, by recursively scanning the classpath beneath each root for a
// unique simple name. The scan is a convenience for the built-ins shipped under `…pipeline.steps.*`; third-party
// steps live outside that package and so are reachable by FQN only. Results are memoized per (roots, name).
object ClassResolver {

  val JobRoots: Seq[String] = Seq("com.kakao.actionbase.pipeline.jobs")

  // Single root; built-ins live in subpackages (source/transform/sink) found by the recursive scan.
  val StepRoots: Seq[String] = Seq("com.kakao.actionbase.pipeline.steps")

  private val cache = new ConcurrentHashMap[String, Class[_]]()

  def resolve(name: String, roots: Seq[String]): Class[_] =
    cache.computeIfAbsent(s"${roots.mkString(",")}::$name", _ => doResolve(name, roots))

  private def doResolve(name: String, roots: Seq[String]): Class[_] = {
    val direct = (name +: roots.map(r => s"$r.$name")).view.flatMap(tryLoad).headOption
    direct.getOrElse(scanForSimpleName(name, roots))
  }

  // Distinct FQNs sharing the requested simple name. Zero → not found; one → load it; many → ambiguous, so refuse
  // rather than pick by scan order (which varies across classpath layouts and would be silently nondeterministic).
  private def scanForSimpleName(name: String, roots: Seq[String]): Class[_] = {
    val matches = roots.flatMap(classNamesUnder).filter(fqn => fqn.substring(fqn.lastIndexOf('.') + 1) == name).distinct
    matches match {
      case Seq(only) =>
        tryLoad(only).getOrElse(throw new ClassNotFoundException(only))
      case Seq() =>
        throw new ClassNotFoundException(
          s"Cannot resolve '$name' as a full FQN or under any of: ${roots.mkString(", ")}"
        )
      case many =>
        throw new IllegalArgumentException(
          s"ambiguous step name '$name' matches ${many.mkString(", ")}; reference it by fully-qualified name"
        )
    }
  }

  private def loader: ClassLoader = Thread.currentThread().getContextClassLoader

  private def tryLoad(fqn: String): Option[Class[_]] =
    try Some(Class.forName(fqn, false, loader))
    catch { case _: ClassNotFoundException => None }

  // Top-level class FQNs (companion/anonymous `$` entries skipped) anywhere beneath `pkg` on the classpath.
  private def classNamesUnder(pkg: String): Seq[String] = {
    val path = pkg.replace('.', '/')
    loader.getResources(path).asScala.toSeq.flatMap { url =>
      url.getProtocol match {
        case "file" => scanDir(new File(url.toURI), pkg)
        case "jar"  => scanJar(url, path)
        case _      => Nil
      }
    }
  }

  private def scanDir(dir: File, pkg: String): Seq[String] =
    Option(dir.listFiles()).getOrElse(Array.empty[File]).toSeq.flatMap { f =>
      if (f.isDirectory) scanDir(f, s"$pkg.${f.getName}")
      else if (f.getName.endsWith(".class") && !f.getName.contains('$')) Seq(s"$pkg.${f.getName.stripSuffix(".class")}")
      else Nil
    }

  private def scanJar(url: URL, pathPrefix: String): Seq[String] = {
    val jar = url.openConnection().asInstanceOf[JarURLConnection].getJarFile
    jar
      .entries()
      .asScala
      .map(_.getName)
      .filter(n => n.startsWith(pathPrefix) && n.endsWith(".class") && !n.contains('$'))
      .map(_.stripSuffix(".class").replace('/', '.'))
      .toSeq
  }
}
