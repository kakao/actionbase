package actionbase.pipeline.testsupport

import com.github.dockerjava.api.model.{ExposedPort, PortBinding, Ports}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory}
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.{OutputFrame, Slf4jLogConsumer}
import org.testcontainers.containers.{BindMode, GenericContainer}
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile

import java.util.function.Consumer

import java.nio.file.{Files, Path => JPath, Paths}
import java.time.Duration

/**
 * Testcontainers-backed standalone HBase fixture built from
 * `src/integrationTest/resources/hbase-docker/` (see Dockerfile).
 *
 * The master advertises the region server to clients by hostname
 * (`hbase.regionserver.hostname=localhost` in `hbase-site.xml`), so the
 * host JVM must be able to reach the same port the master announced.
 * We achieve this by binding host:container ports 1:1 for the five HBase
 * ports via `withCreateContainerCmdModifier` — standard `addExposedPort`
 * alone randomises host ports, which would break the advertised address.
 */
object HBaseContainer {
  private val DockerContextResource = "hbase-docker"

  /**
   * Build the image once per classpath; testcontainers caches by the
   * content hash of the build context directory.
   */
  val image: ImageFromDockerfile = {
    val url = getClass.getClassLoader.getResource(DockerContextResource)
    require(url != null, s"$DockerContextResource not on test classpath")
    new ImageFromDockerfile("actionbase-pipeline-hbase-it", false)
      .withFileFromPath(".", Paths.get(url.toURI))
  }

  /**
   * Fixed shared host directory bind-mounted into the JVM-shared container.
   * Located under `/tmp/` because Docker Desktop on macOS shares `/tmp/`
   * with the Linux VM by default, and any path under this root is identity-
   * mapped inside the container.
   *
   * Accessing this val eagerly creates the directory and makes it world-
   * accessible, so suites that call `Files.createTempDirectory(sharedRootDir, ...)`
   * during their own field initialisation (before `shared()` is called)
   * still succeed.
   *
   * Suites should create a subdirectory here so cross-suite interference is
   * impossible while still reusing the same live container.
   */
  lazy val sharedRootDir: JPath = {
    val p = Paths.get("/tmp/ab-it-hbase-shared")
    Files.createDirectories(p)
    // Canonicalize: macOS `/tmp` is a symlink to `/private/tmp`, and
    // Docker Desktop resolves bind-mount sources to the real path. If we
    // keep the symlink form here, host-produced paths look like
    // `/tmp/...` while the container's mount actually targets
    // `/private/tmp/...`, and the region server's FileNotFoundException
    // does exactly that mismatch. Resolving to the real path upfront
    // makes both sides agree on a single canonical absolute path.
    val real = p.toRealPath()
    val f    = real.toFile
    f.setReadable(true, false)
    f.setWritable(true, false)
    f.setExecutable(true, false)
    real
  }

  @volatile private var sharedInstance: HBaseContainer = _
  private val sharedLock = new Object

  /**
   * JVM-shared, lazily-started `HBaseContainer`. The first caller pays the
   * ~25s warm-up cost; subsequent callers reuse the same running container.
   * A JVM shutdown hook stops the container at the end of the test JVM, so
   * ryuk or caller-side lifecycle management is not required.
   *
   * The shared instance is always created with `sharedRootDir` bind-mounted
   * (see `withSharedDir`), so tests can freely write files under it without
   * recreating the container.
   */
  def shared(): HBaseContainer = sharedLock.synchronized {
    if (sharedInstance == null) {
      val c = new HBaseContainer().withSharedDir(sharedRootDir)
      c.start()
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        try c.stop() catch { case _: Throwable => () }
      }))
      sharedInstance = c
    }
    sharedInstance
  }
}

class HBaseContainer extends GenericContainer[HBaseContainer](HBaseContainer.image) {
  import HBaseContainer._

  private val fixedPorts: Seq[Int] = Seq(2181, 16000, 16010, 16020, 16030)

  withCreateContainerCmdModifier { cmd =>
    val bindings = new Ports()
    fixedPorts.foreach { p =>
      bindings.bind(ExposedPort.tcp(p), Ports.Binding.bindPort(p))
    }
    val hc = cmd.getHostConfig
    hc.withPortBindings(bindings)
    cmd.withHostConfig(hc)
    cmd.withHostName("localhost")
  }

  fixedPorts.foreach(addExposedPort(_))

  // Forward container stdout/stderr to SLF4J so HBase server-side errors
  // (bulkload RPC failures, region-server exceptions) are visible in the
  // test JVM output stream and get captured in the JUnit report's
  // system-out CDATA — essential for diagnosing integration failures.
  // Forward WARN/ERROR/Exception lines from the container to SLF4J so
  // future debugging of bulkload / region-assignment failures has the
  // server-side cause visible without flipping this to verbose. Flip the
  // filter to `true` if you need every line (e.g. to diagnose a new
  // retry-bailout where the proximate cause isn't obvious).
  private val hbaseItLogger = LoggerFactory.getLogger("HBase-IT")
  withLogConsumer(new Consumer[OutputFrame] {
    override def accept(frame: OutputFrame): Unit = {
      val line = frame.getUtf8String.trim
      if (line.nonEmpty && (line.contains("WARN") || line.contains("ERROR") ||
          line.contains("Exception") || line.contains("bulkload") ||
          line.contains("BulkLoad"))) {
        hbaseItLogger.warn(s"[hbase] $line")
      }
    }
  })

  // `Master has completed initialization` is logged by the master after
  // all services (meta region assignment, namespace table, etc.) are up.
  waitingFor(
    Wait.forLogMessage(".*Master has completed initialization.*", 1)
      .withStartupTimeout(Duration.ofMinutes(3))
  )

  def hbaseConfiguration: Configuration = {
    val conf = HBaseConfiguration.create()
    conf.set("hbase.zookeeper.quorum", "localhost")
    conf.setInt("hbase.zookeeper.property.clientPort", 2181)
    conf.set("zookeeper.znode.parent", "/hbase")
    conf
  }

  def newConnection(): Connection = ConnectionFactory.createConnection(hbaseConfiguration)

  /**
   * Bind-mount a host directory into the container at the same absolute path.
   *
   * Integration tests that produce HFiles on the host (e.g.
   * `DynamicRegionSplitterIntegrationTest`) use this to make the same
   * `file://` path visible to the HBase process inside the container, so
   * bulkload semantics that translate HFile URIs 1:1 between the client
   * driver and the region server keep working without path rewriting.
   *
   * The host path is created if missing and made world-readable/writable/
   * executable so the container's `root` (or any uid) can access it.
   */
  def withSharedDir(hostPath: JPath): this.type = {
    Files.createDirectories(hostPath)
    val f = hostPath.toFile
    f.setReadable(true, false)
    f.setWritable(true, false)
    f.setExecutable(true, false)
    withFileSystemBind(hostPath.toString, hostPath.toString, BindMode.READ_WRITE)
    this
  }
}
