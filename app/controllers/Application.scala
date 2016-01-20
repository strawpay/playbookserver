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
import scala.util.Random

object Application extends Controller {

  val dir = Directory(Play.configuration.getString("ansible.playbooks").get)
  if (!dir.isDirectory) throw new ConfigurationException(s"$dir is not a directory")
  val ansible = Play.configuration.getString("ansible.command").get
  val vaultPassword = Play.configuration.getString("ansible.vault_password").get
  val verbose = Play.configuration.getBoolean("ansible.verbose").getOrElse(false)
  val passwordFile = createTempVaultPassFile()
  val startedAt = DateTime.now.toDateTime(DateTimeZone.UTC)
  val ansibleVersion = s"$ansible --version" !!
  val random = Random

  def index = Action {
    Ok(views.html.index(dir, ansible, ansibleVersion, startedAt))
  }

  def play(inventoryName: String, playbookName: String): Action[JsValue] = Action(parse.json) { request =>

    val buildId = Math.abs(random.nextInt).toString
    val refId = escapeJson(request.getQueryString("refId").getOrElse(""))
    Logger.info(s"""{buildId:$buildId refId:$refId action:"play" inventory:$inventoryName playbook:$playbookName remoteAddress:${request.remoteAddress}""")

    val inventory = dir / inventoryName
    val playbook = dir / playbookName

    val result = checkPath(inventory, "inventory", buildId, refId) orElse {
      checkPath(playbook, "playbook", buildId, refId)
    } orElse {

      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val cmdPre = Seq(ansible,
        "-i", inventory.toString(),
        "-e", request.body.toString(),
        "--vault-password-file", passwordFile,
        playbook.toString())
      val cmd = if (verbose) {
        (cmdPre :+ "-v").mkString(" ")
      } else {
        cmdPre.mkString(" ")
      }
      Logger.debug(s"""buildId:$buildId refId:"$refId" command:"$cmd""""")
      val start = DateTime.now().getMillis
      val code = cmd ! ProcessLogger(appendLine(stdout, _), appendLine(stderr, _))
      val execTime = s"PT${(DateTime.now.getMillis - start + 500) / 1000}S"
      def resultString(status:String, message:String):String = {
        s"""{
            |"playbook":"$playbookName",
            | "inventory":"$inventoryName",
            | "status":"$status",
            | "buildId":$buildId,
            | "refId":"$refId",
            | "execTime":"$execTime",
            | "message":"$message"
            | }""".stripMargin
      }

      if (code == 0) {
        Logger.info(resultString("success", stdout.toString))
        Some(Ok(Json.parse(resultString("success", escapeJson(stdout.toString)) )))
      }
      else {
        val message = stdout.append(s"\nstderr:$stderr").toString
        Logger.warn(resultString("failed", message))
        Some(ServiceUnavailable(Json.parse(resultString("failed", escapeJson(message)))))
      }
    }
    result.get
  }



  def ping = Action {
    Ok.withHeaders(CACHE_CONTROL -> "no-cache")
  }

  private def escapeJson(input: String): String = {
    input.replace("\"", "^").replace("\'", "^").replace("\\", "/").replace("\n", "\\n")
  }

  private def checkPath(file: Path, hint: String, buildId:String, refId:String): Option[Result] = {
    if (file.exists) {
      None
    } else {
      val message = s"buildId:$buildId refId:$refId File not found: $hint file: $file"
      Logger.warn(message)
      Some(NotFound(message))
    }
  }

  private def appendLine(builder:StringBuilder, line:String):Unit = {
    builder.append(s"$line\n")
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
