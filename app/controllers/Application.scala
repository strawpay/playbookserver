package controllers

import java.io.{File, FileOutputStream}

import akka.ConfigurationException
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api._
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc._
import play.api.Logger
import buildinfo.BuildInfo

import scala.reflect.io.{Directory, Path}
import scala.sys.process._
import scala.util.Random

object Application extends Controller {

  val playbooks = Directory(Play.configuration.getString("ansible.playbooks").get)
  if (!playbooks.isDirectory) throw new ConfigurationException(s"$playbooks is not a directory")
  val ansible = Play.configuration.getString("ansible.command").get
  val vaultPassword = Play.configuration.getString("ansible.vault_password").get
  val verbose = Play.configuration.getBoolean("ansible.verbose").getOrElse(false)
  val passwordFile = createTempVaultPassFile()
  val startedAt = DateTime.now.toDateTime(DateTimeZone.UTC)
  val ansibleVersion = s"$ansible --version" !!
  val random = Random
  val inventoryMap = Play.configuration.getConfig("inventoryMap").get
  private val defaultInventory = Play.configuration.getString("defaultInventory").get

  case class Version(version: String)

  implicit val versionFormat = Json.format[Version]

  def index = Action {
    Ok(views.html.index(playbooks, ansible, ansibleVersion, startedAt))
  }

  def play: Action[JsValue] = Action(parse.json) { request =>
    val buildId = Math.abs(random.nextInt).toString
    val refId = escapeJson(request.getQueryString("refId").getOrElse(""))
    val branch = request.getQueryString("inventory")
    val jsValue: JsValue = Json.parse("""{"message":"must give playbook"}""")
    val playbookName = request.getQueryString("playbook")
    val inventoryName = branch map (inventoryMap.getString(_).getOrElse(defaultInventory))
    val start = DateTime.now().getMillis
    val inventory = inventoryName map (playbooks / _)
    val playbook = playbookName map (n => playbooks / (n + ".yaml"))
    val versionJson = request.body

    def resultJson(status: Boolean, message: Option[JsValue]): JsValue = {
      val json = JsObject(Seq(
        "buildId" → JsString(buildId),
        "refId" -> JsString(refId),
        "inventory" → JsString(inventoryName.getOrElse("N/A")),
        "playbook" → JsString(playbookName.getOrElse("N/A")),
        "status" → JsString(if (status) "success" else "failed"),
        "execTime" → JsString(execTime)
      ))
      val result = message match {
        case Some(m) => json + ("message" → message.get)
        case None => json
      }
      JsObject(Seq("result" → result))
    }

    def execTime: String = s"PT${(DateTime.now.getMillis - start + 500) / 1000}S"

    def reportBadRequest(message: JsValue): Option[Result] = {
      val json = resultJson(status = false, message = Some(message))
      Logger.warn(json.toString)
      Some(BadRequest(json))
    }

    def reportServiceUnavailable(message: JsObject): Option[Result] = {
      val json = resultJson(status = false, message = Some(message))
      Logger.warn(json.toString)
      Some(ServiceUnavailable(json))
    }
    def checkPath(file: Option[Path], hint: String): Option[Result] = {
      file match {
        case Some(f) =>
          if (f.exists) {
            None
          } else {
            val json = resultJson(status = false, message = Some(JsString(s"File not found: $hint file: $f")))
            Logger.warn(json.toString())
            Some(NotFound(json))
          }
        case None =>
          val json = resultJson(status = false, message = Some(JsString(s"$hint not set")))
          Logger.warn(json.toString)
          Some(BadRequest(json))
      }
    }

    def appendLine(builder: StringBuilder, line: String): Unit = {
      builder.append(s"$line\n")
    }

    def gitPull: Option[Result] = {
      val cmd = Seq("git", "-C", playbooks.toString, "pull")
      runCommand(cmd) match {
        case (0, _) =>
          None
        case (code, message) =>
          reportServiceUnavailable(message)
      }
    }

    def runCommand(cmd: Seq[String]) = {
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val code = cmd ! ProcessLogger(appendLine(stdout, _), appendLine(stderr, _))
      val message = JsObject(Seq(
        "stdout" → JsString(stdout.toString()),
        "stderr" → JsString(stderr.toString())
      ))
      if (code == 0)
        Logger.debug(s"""CmdExecution=ok, code=0, cmd="${cmd.mkString(" ")}"""")
      else
        Logger.warn(s"""CmdExecution=failed code=$code, cmd="${cmd.mkString(" ")}", message="$message"""")
      (code, message)
    }

    Logger.info(
      JsObject(Seq(
        "request" → JsObject(Seq(
          "buildId" → JsString(buildId),
          "refId" → JsString(refId),
          "inventory" → JsString(inventoryName.getOrElse("N/A")),
          "playbook" → JsString(playbookName.getOrElse("N/A")),
          "remoteAddress" → JsString(request.remoteAddress)
        )))).toString()
    )

    (checkPath(inventory, "inventory") orElse {
      checkPath(playbook, "playbook")
    } orElse gitPull orElse {
      // Run ansible
      val cmdPre = Seq(ansible,
        "-i", inventory.toString,
        "-e", versionJson.toString,
        "--vault-password-file", passwordFile,
        playbook.toString)
      val cmd = if (verbose) {
        cmdPre :+ "-v"
      } else {
        cmdPre
      }
      Logger.debug(JsObject(Seq(
        "buildId" → JsString(buildId),
        "refId" → JsString(refId),
        "command" → JsString(cmd.mkString(" "))
      )).toString)
      versionJson.asOpt[Version] match {
        case Some(Version(version)) =>
          runCommand(cmd) match {
            case (0, message) =>
              Logger.trace(resultJson(status = true, message = Some(JsString(stdout.toString))).toString)
              val json = resultJson(status = true, message = None)
              Logger.info(json.toString)
              Some(Ok(json))
            case (_, message) =>
              reportServiceUnavailable(message)
          }
        case _ => reportBadRequest(JsString("must give version"))
      }
    }).get
  }

  def createTempVaultPassFile(): String = {
    val passwordFile = File.createTempFile("rocannon-", ".tmp")
    passwordFile.deleteOnExit()
    val stream = new FileOutputStream(passwordFile)
    stream.write(vaultPassword.getBytes)
    stream.close()
    passwordFile.getCanonicalPath
  }

  def escapeJson(input: String): String = {
    input.replace("\"", "^").replace("\'", "^").replace("\\", "/").replace("\n", "\\n")
  }

  def ping = Action {
    Ok(Json.obj(
      "name" -> JsString(BuildInfo.name),
      "version" -> JsString(BuildInfo.version)
    )).withHeaders(CACHE_CONTROL -> "no-cache")
  }

}
