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

  def play(branch: String, playbookName: String): Action[JsValue] = Action(parse.json) { request =>
    val start = DateTime.now().getMillis
    val buildId = Math.abs(random.nextInt).toString
    val refId = escapeJson(request.getQueryString("refId").getOrElse(""))
    val inventoryName = inventoryMap.getString(branch).getOrElse(defaultInventory)
    val inventory = playbooks / inventoryName
    val playbook = playbooks / playbookName + ".yaml"

    val versionJson = request.body

    def resultJson(status: Boolean, message: Option[JsValue]): JsValue = {
      val json = JsObject(Seq(
        "buildId" → JsString(buildId),
        "refId" -> JsString(refId),
        "inventory" → JsString(inventoryName),
        "playbook" → JsString(playbookName),
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
      val json = resultJson(false, Some(message))
      Logger.warn(json.toString)
      Some(BadRequest(json))
    }

    def reportServiceUnavailable(message: JsObject): Option[Result] = {
      val json = resultJson(false, Some(message))
      Logger.warn(json.toString)
      Some(ServiceUnavailable(json))
    }
    def checkPath(file: Path, hint: String): Option[Result] = {
      if (file.exists) {
        None
      } else {
        val message = s"buildId:$buildId refId:$refId File not found: $hint file: $file"
        Logger.warn(message)
        Some(NotFound(message))
      }
    }

    def appendLine(builder: StringBuilder, line: String): Unit = {
      builder.append(s"$line\n")
    }

    def git(operation: Seq[String]): Option[Result] = {
      val cmd = Seq("git", "-C", playbooks.toString) ++ operation
      runCommand(cmd) match {
        case (0, _) =>
          None
        case (code, message) =>
          if (message.toString() contains "is not a valid tag name")
            reportBadRequest(message)
          else
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
        Logger.warn(s"""CmdExecution=failed code=$code, message="$message", cmd=${cmd.mkString(" ")}"""")
      (code, message)
    }

    Logger.info(
      JsObject(Seq(
        "request" → JsObject(Seq(
          "buildId" → JsString(buildId),
          "refId" → JsString(refId),
          "inventory" → JsString(inventoryName),
          "playbook" → JsString(playbookName),
          "remoteAddress" → JsString(request.remoteAddress)
        )))).toString()
    )

    (checkPath(inventory, "inventory") orElse {
      checkPath(playbook, "playbook")
    } orElse git(Seq("pull")) orElse {
      // Run ansible
      val cmdPre = Seq(ansible,
        "-i", inventory.toString(),
        "-e", versionJson.toString(),
        "--vault-password-file", passwordFile,
        playbook.toString())
      val cmd = if (verbose) {
        (cmdPre :+ "-v")
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
              Logger.trace(resultJson(true, Some(JsString(stdout.toString))).toString())
              val json = resultJson(true, None)
              Logger.info(json.toString())
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
