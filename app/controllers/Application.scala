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
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val versionJson = request.body
    val version = (versionJson \ "version").as[String]
    def execTime: String = s"PT${(DateTime.now.getMillis - start + 500) / 1000}S"

    def reportFailure(): Option[Result] = {
      val message = JsObject(Seq(
        "stdout" → JsString(stdout.toString()),
        "stderr" → JsString(stderr.toString())
      ))
      Logger.warn(resultString("failed", Some(message)))
      Some(ServiceUnavailable(Json.parse(resultString("failed", Some(message)))))
    }

    def resultString(status: String, message: Option[JsValue]): String = {
      JsObject(Seq(
        "result" → JsObject(Seq(
          "buildId" → JsString(buildId),
          "refId" -> JsString(refId),
          "inventory" → JsString(inventoryName),
          "playbook" → JsString(playbookName),
          "status" → JsString(status),
          "execTime" → JsString(execTime),
          "message" → Json.toJson(message)
        )))).toString
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
      cmd ! ProcessLogger(appendLine(stdout, _), appendLine(stderr, _)) match {
        case 0 =>
          Logger.debug(s"$cmd ok")
          None
        case _ => reportFailure()
      }
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
        "-e", versionJson,
        "--vault-password-file", passwordFile,
        playbook.toString())
      val cmd = if (verbose) {
        (cmdPre :+ "-v").mkString(" ")
      } else {
        cmdPre.mkString(" ")
      }
      Logger.debug(JsObject(Seq(
        "buildId" → JsString(buildId),
        "refId" → JsString(refId),
        "command" → JsString(cmd)
      )).toString)
      val code = cmd ! ProcessLogger(appendLine(stdout, _), appendLine(stderr, _))

      if (code == 0) {
        git(Seq("tag", "-f", s"${inventoryName}_$playbookName-${version}")) orElse {
          git(Seq("tag", "-f", s"build_${buildId}"))
        } orElse {
          if (refId.isEmpty) None else git(Seq("tag", "-f", s"ref_$refId"))
        } orElse {
          git(Seq("push", "--tags")) orElse {
            Logger.trace(resultString("success", Some(JsString(stdout.toString))))
            Logger.info(resultString("success", None))
            Some(Ok(Json.parse(resultString("success", None))))
          }
        }
      } else {
        reportFailure()
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
