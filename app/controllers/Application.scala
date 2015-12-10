package controllers

import java.io.{File, FileOutputStream}

import akka.ConfigurationException
import org.joda.time.{DateTimeZone, DateTime}
import play.api.Play.current
import play.api._
import play.api.libs.json.{Json, JsValue}
import play.api.mvc._
import play.api.Logger

import scala.reflect.io.{Directory, Path}
import scala.sys.process._

object Application extends Controller {

  val dir = Directory(Play.configuration.getString("ansible.playbooks").get)
  if (!dir.isDirectory) throw new ConfigurationException(s"$dir is not a directory")
  val ansible = Play.configuration.getString("ansible.command").get
  val vaultPassword = Play.configuration.getString("ansible.vault_password").get
  val verbose = Play.configuration.getBoolean("ansible.verbose").getOrElse(false)
  val passwordFile = createTempVaultPassFile()
  val startedAt = DateTime.now.toDateTime(DateTimeZone.UTC)
  val ansibleVersion = s"$ansible --version" !!

  def index = Action {
    Ok(views.html.index(dir, ansible, ansibleVersion, startedAt))
  }

  def play(inventoryName: String, playbookName: String): Action[JsValue] = Action(parse.json) { request =>

    val inventory = dir / inventoryName
    val playbook = dir / playbookName

    val result = checkPath(inventory, "inventory") orElse {
      checkPath(playbook, "playbook")
    } orElse {

      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val cmdPre = Seq(ansible,
        "-i", inventory.toString(),
        "-e", request.body.toString(),
        "--vault-password-file", passwordFile,
        playbook.toString())
      val (cmd) = if (verbose) {
        (cmdPre :+ "-v").mkString(" ")
      } else {
        cmdPre.mkString(" ")
      }
      val buildNumber = escapeJson(request.getQueryString("buildNumber").getOrElse(""))
      Logger.debug(s"running:$cmd")
      val code = cmd ! ProcessLogger(stdout append _, stderr append _)
      if (code == 0) {
        Logger.info(s"Playbook:success buildNumber:$buildNumber command:{$cmd} stdout:{$stdout}")
        val message = escapeJson(stdout.toString)
        Some(Ok(Json.parse( s"""{"status":"success","buildNumber": "$buildNumber","message": "$message"}""")))
      }
      else {
        val message = s"Playbook:failed buildNumber:$buildNumber, exitcode:$code, command:{$cmd}, stdout:$stdout stderr:$stderr}"
        Logger.warn(message)
        val escaped = escapeJson(message)
        Some(ServiceUnavailable(Json.parse( s"""{"status":"failed","buildNumber": "$buildNumber","message": "$escaped"}""")))
      }
    }
    result.get
  }

  def ping = Action {
    Ok.withHeaders(CACHE_CONTROL -> "no-cache")
  }

  private def escapeJson(input: String): String = {
    input.replace("\"", "^").replace("\'", "^").replace("\\", "/")
  }

  private def checkPath(file: Path, hint: String): Option[Result] = {
    if (file.exists) {
      None
    } else {
      Some(NotFound(s"File not found: $hint file: $file"))
    }
  }

  private def createTempVaultPassFile(): String = {
    val passwordFile = File.createTempFile("rocannon-", "tmp")
    passwordFile.deleteOnExit()
    val stream = new FileOutputStream(passwordFile)
    stream.write(vaultPassword.getBytes)
    stream.close()
    passwordFile.getCanonicalPath
  }

}
