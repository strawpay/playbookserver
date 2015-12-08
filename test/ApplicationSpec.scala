import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._

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

    "send 404 on a non existing inventory" in {
      val result = route(FakeRequest(GET, "/not-an-inventory")).get
      status(result) must be(NOT_FOUND)
    }

    "send 404 on a non existing playbook" in {
      val result = route(FakeRequest(GET, "/inventory/not-a-file")).get
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
      val buildNumber = "4711"
      val extraVars = Json.parse( """{"version": "1.0" }""")
      val result = route(FakeRequest(POST, s"/inventory/play.yaml?buildNumber=$buildNumber", FakeHeaders(), extraVars)).get
      status(result) must be(OK)
      contentType(result) must be(Some("application/json"))
      contentAsString(result) must startWith(s"""{"status":"success","buildNumber":"$buildNumber","message":"-v -i test/resources/inventory -e {'version':'1.0'} --vault-password-file""")
      contentAsString(result) must endWith("""test/resources/play.yaml"}""")
    }

    "buildNumber with \" are escaped" in {
      val buildNumber = "evil\""
      val buildNumberEscaped = "evil\"".replace("\"", "'")
      val extraVars = Json.parse( """{"version": "1.0" }""")
      val result = route(FakeRequest(POST, s"/inventory/play.yaml?buildNumber=$buildNumber", FakeHeaders(), extraVars)).get
      status(result) must be(OK)
      contentType(result) must be(Some("application/json"))
      contentAsString(result) must include(s""""buildNumber":"$buildNumberEscaped"""")
    }

    "report error if the playbook fails" in {
      val extraVars = Json.parse( """{"failure": "true" }""")
      val result = route(FakeRequest(POST, "/inventory/play.yaml", FakeHeaders(), extraVars)).get
      status(result) must be(SERVICE_UNAVAILABLE)
      contentType(result) must be(Some("application/json"))
      contentAsString(result) must include ("""{"status":"failed","buildNumber":"","message":""")
    }

  }
}
