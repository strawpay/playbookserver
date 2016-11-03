import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  implicit override lazy val app: FakeApplication = {
    FakeApplication(
      additionalConfiguration = Map(
        "ansible.playbooks" -> "test/resources",
        "ansible.vault_password_file" -> "test/resources/password",
        "ansible.command" -> "test/resources/ansible")
    )
  }

  "Rocannon" should {

    implicit val extraVars: JsValue = Json.parse( """{"version": "1.0" }""")

    "send 404 on a non existing inventory" in {
      val result = route(FakeRequest(GET, "/not-an-inventory")).get
      status(result) must be(NOT_FOUND)
    }

    "send 404 on a non existing playbook" in {
      val result = route(FakeRequest(GET, "/dev/not-a-file")).get
      status(result) must be(NOT_FOUND)
    }

    "render the index page" in {
      val home = route(FakeRequest(GET, "/")).get
      status(home) must be(OK)
      contentType(home) must be(Some("text/html"))
      contentAsString(home) must include("Rocannon")
    }

    "respond on ping" in {
      val home = route(FakeRequest(GET, "/ping")).get
      status(home) must be(OK)
    }

    "run a playbook with success" in {
      val refId = "4711"
      val  result = post(refId)
      verifySuccess(result, refId)
    }

    "map develop to dev" in {
      val refId = "4711"
      val  result = post(refId, "develop")
      verifySuccess(result, refId, "dev")
    }

    "map master to stage" in {
      val refId = "4711"
      val  result = post(refId, "master")
      verifySuccess(result, refId, "stage")
    }

    "map unknown to dev" in {
      val refId = "4711"
      val  result = post(refId, "unknown")
      verifySuccess(result, refId, "dev")
    }

    "build with \" are escaped" in {
      val refId = """ evil\" ' \'"""
      val refEscaped = " evil/^ ^ /^"
      val result = post(refId)
      verifySuccess(result, refEscaped)
    }

    "report error if the playbook fails" in {
      val refId = "17"
      val result = post(refId)(Json.parse( """{"failure": "true" }"""))
      status(result) must be(SERVICE_UNAVAILABLE)
      contentType(result) must be(Some("application/json"))
      val js = contentAsJson(result) \ "result"
      (js \ "buildId").as[String].toLong > 0
      (js \ "refId").as[String] must startWith (refId)
      (js \ "status").as[String] must be("failed")
      (js \ "execTime").as[String] must be("PT0S")
      (js \ "message" \ "stderr").as[String] must be ("")
    }

    "report empty refId in response if refId query parameter is empty" in {
      val result = post()
      verifySuccess(result, "")
    }

  }

  def post()(implicit extraVars:JsValue): Future[Result] = {
    route(FakeRequest(POST, s"/dev/play.yaml", FakeHeaders(), extraVars)).get
  }

  def post(refId: String, inventory:String = "dev")(implicit extraVars:JsValue): Future[Result] = {
    route(FakeRequest(POST, s"/$inventory/play.yaml?refId=$refId", FakeHeaders(), extraVars)).get
  }

  def verifySuccess(result: Future[Result], refId: String, inventory:String = "dev"): Unit = {
    status(result) must be(OK)
    contentType(result) must be(Some("application/json"))
    val js = Json.parse(contentAsString(result)) \ "result"
    (js \ "buildId").as[String].toLong > 0
    (js \ "inventory").as[String] must be(inventory)
    (js \ "refId").as[String] must startWith (refId)
    (js \ "status").as[String] must be("success")
    (js \ "execTime").as[String] must be("PT0S")
  }
}
