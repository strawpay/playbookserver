/**
  *
  */
package object models {

  implicit def string2buildId(value:String) = BuildId(value)

  case class BuildId(value:String)

  object BuildResult extends Enumeration {
    type BuildResult = Value
    val success, failure = Value
  }

}
