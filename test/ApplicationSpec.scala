import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  implicit override lazy val app: FakeApplication = {
    FakeApplication(
      additionalConfiguration = Map(
        "ansible.playbooks" -> "test/resources",
        "ansible.vault_password_file" -> "test/resources/password",
        "ansible.command" -> "test/resources/ansible")
    )
  }

  "Application" should {

    "send 404 on a bad request" in {
      val result = route(FakeRequest(GET, "/boum")).get
      status(result) must be(NOT_FOUND)
    }

    "render the index page" in {
      val home = route(FakeRequest(GET, "/")).get
      status(home) must be(OK)
      contentType(home) must be(Some("text/html"))
      contentAsString(home) must include("Playbookserver")
    }

    "respond on ping" in {
      val home = route(FakeRequest(GET, "/ping")).get
      status(home) must be(OK)
    }

    "run a playbook with success" in {
      val extraVars = Json.parse( """{"version": "1.0" }""")
      val result = route(FakeRequest(POST, "/inventory/play.yaml", FakeHeaders(), extraVars)).get
      status(result) must be(OK)
      contentType(result) must be(Some("application/json"))
      contentAsString(result) must be("{\"status\":\"success\",\"message\":\"-v -i test/resources/inventory -e {'version':'1.0'} --vault-password-file test/resources/password test/resources/play.yaml\"}")
    }

    "report error if the playbook fails" in {
      val extraVars = Json.parse( """{"failure": "true" }""")
      val result = route(FakeRequest(POST, "/inventory/play.yaml", FakeHeaders(), extraVars)).get
      status(result) must be(SERVICE_UNAVAILABLE)
      contentType(result) must be(Some("application/json"))
      contentAsString(result) must include ("{\"status\":\"failed\",\"message\":")
    }

  }
}
