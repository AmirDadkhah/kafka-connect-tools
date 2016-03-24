package com.github.rollulus

import scopt._

object AppCommand extends Enumeration {
  type AppCommand = Value
  val NONE, LIST, INFO, DELETE, CREATE, RUN  = Value
}
import AppCommand._

object Defaults {
  val BaseUrl = "http://localhost:8083/"
}

case class Config(cmd: AppCommand= NONE, url: String = Defaults.BaseUrl, connectorNames: Seq[String] = Seq())


object Go {
  def allStdIn = Iterator.
    continually(io.StdIn.readLine).
    takeWhile(x => {x != null})

  lazy val R = "([^#].*)=(.*)".r
  def propsToJson(s:Seq[String]) = "{" + s.map(_ match {
    case R(k,v) => s""""${k.trim}":"${v.trim}""""
    case _ => ""
  }).filterNot(_.isEmpty).mkString(", ") + "}"

  def apply(cfg: Config) = {
    val api = new KafkaConnectApi(cfg.url)
    val fmt = new PropertiesFormatter()

    lazy val configuration = propsToJson(allStdIn.toSeq)

    cfg.cmd match {
      case LIST => println(fmt.connectorNames(api.activeConnectorNames))
      case DELETE => cfg.connectorNames.foreach(api.delete)
      case CREATE => cfg.connectorNames.foreach(api.addConnector(_, configuration))
      case RUN => cfg.connectorNames.foreach(api.updateConnector(_, configuration))
      case INFO => println(cfg.connectorNames.map(api.connectorInfo).map(fmt.connectorInfo).mkString("\n"))
    }

  }
}

object HelloWorld {
  def main(args: Array[String]): Unit = {

    val parser = new OptionParser[Config]("kafconcli") {
      head("kafconcli", "1.0")
      cmd("ls") action { (_, c) => c.copy(cmd = LIST) } text "list active connectors names" children()
      cmd("info") action { (_, c) => c.copy(cmd = INFO) } text "retrieves information for the specified connector(s)" children()
      cmd("rm") action { (_, c) => c.copy(cmd = DELETE) } text "removes the specified connector(s)" children()
      cmd("create") action { (_, c) => c.copy(cmd = CREATE) } text "creates the specified connector with the .properties from stdin" children()
      cmd("run") action { (_, c) => c.copy(cmd = RUN) } text "creates or updates the specified connector with the .properties from stdin" children()
      arg[String]("<connector-name>...") unbounded() optional() action { (x, c) =>
        c.copy(connectorNames = c.connectorNames :+ x) } text("connector name(s)")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        // do stuff
        Go(config)

      case None =>
        // arguments are bad, error message will have been displayed
        println("fail")
    }
  }
}

