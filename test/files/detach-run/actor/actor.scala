object Test {

  val actors_logLevel = "0"
                   // = "3" // info+warning+error
  val logLevel = "silent"
            // = "info"     // debug user code only
            // = "info,lib" // debug user & library code

  // we assume an Apache server is running locally for deployment
  private val sep = java.io.File.separator
  val docPath = System.getProperty("user.home")+sep+"public_html"
  val docRoot = "http://127.0.0.1/~"+System.getProperty("user.name")

  val host = "127.0.0.1"
  val port = 8889

  def main(args: Array[String]) {
    setenv()
    println("Server.main "+port)
    Server.main(Array(port.toString))
    println("Client.main "+host+" "+port)
    Client.main(Array(host, port.toString))
    Server.terminate()
  }

  private def setenv() {
    import java.io._, java.util.jar._

    val policyTmpl =
      System.getProperty("partest.cwd")+sep+"actor"+sep+"java.policy"
    val outPath = System.getProperty("partest.output")
    val libPath = System.getProperty("partest.lib")
    val policyFile = outPath+sep+"java.policy"
    val codebaseDir = outPath+sep+"-"

    assert((new java.io.File(docPath)).isDirectory,
           "Root directory \""+docPath+"\" not found")
    val deployJar = docPath+sep+"actor_deploy.jar"
    val deployUrl = docRoot+"/actor_deploy.jar"

    // Java properties for server & client
    System.setProperty("scala.actors.logLevel", actors_logLevel)
    System.setProperty("scala.remoting.logLevel", logLevel)
    System.setProperty("java.security.manager", "")
    System.setProperty("java.security.policy", policyFile)
    // Java properties for server only
    System.setProperty("java.rmi.server.codebase", deployUrl)
    System.setProperty("java.rmi.server.hostname", host)
    System.setProperty("java.rmi.server.useCodebaseOnly", "true")

    val classNames = List(
      "$anonfun$main$1$proxy",
      "$anonfun$main$1$proxyImpl_Stub",
      "Bar$proxy",
      "Bar$proxyImpl_Stub",
      "Client$$anonfun$main$1$$anonfun$apply$1$detach",
      "Client$proxy",
      "Client$proxyImpl_Stub",
      "Foo$proxy",
      "Foo$proxyImpl_Stub")

    val proxyImplNames =
      for (n <- classNames; i = n lastIndexOf "_Stub"; if i > 0)
      yield n.substring(0, i)

    generatePolicyFile()
    generateRmiStubs(proxyImplNames)
    generateJarFile(classNames)

    def generatePolicyFile() {
      val in = new BufferedReader(new FileReader(policyTmpl))
      val out = new PrintWriter(new BufferedWriter(new FileWriter(policyFile)))
      var line = in.readLine()
      while (line != null) {
        val line1 = line.replaceAll("@PROJECT_LIB_BASE@", codebaseDir)
        out.println(line1)
        line = in.readLine()
      }
      in.close()
      out.close()
    }
    def exec(command: String) {
      val proc = Runtime.getRuntime exec command
      proc.waitFor()
      val out = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line = out.readLine()
      while (line != null) {
        println(line)
        line = out.readLine()
      }
      out.close()
      val err = new BufferedReader(new InputStreamReader(proc.getErrorStream))
      line = err.readLine()
      while (line != null) {
        println(line)
        line = err.readLine()
      }
      err.close()
    }

    def ls(path: String) { exec("ls -al "+path) }
    def rmic(options: List[String], classNames: List[String]) {
      val javaHome = scala.util.Properties.javaHome
      val jdkHome =
        if (javaHome endsWith "jre") javaHome.substring(0, javaHome.length-4)
        else javaHome
      val rmicExt = if (scala.util.Properties.isWin) ".exe" else ""
      val rmicCmd = jdkHome+sep+"bin"+sep+"rmic"+rmicExt
      val cmdLine = rmicCmd+options.mkString(" ", " ", "")+
                            classNames.mkString(" "," ","")
      // println(cmdLine)
      exec(cmdLine)
    }
    def generateRmiStubs(classNames: List[String]) {
      val options = List(
        "-v1.2",
        "-classpath "+libPath+File.pathSeparator+outPath,
        "-d "+outPath)
      rmic(options, classNames)
      //ls(outPath)
    }
    def generateJarFile(classNames: List[String]) {
      val out = new JarOutputStream(new FileOutputStream(deployJar))
      classNames foreach (name => {
        val className = name+".class"
        out putNextEntry new JarEntry(className)
        val in = new FileInputStream(outPath+sep+className)
        val buf = new Array[Byte](256)
        var len = in read buf
        while (len != -1) {
          out.write(buf, 0, len)
          len = in read buf
        }
        in.close()
      })
      out.close()
    }
  }
}
