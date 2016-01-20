package models

import java.time.{Duration, LocalDateTime}

import models.BuildResult.BuildResult

case class Build(id:BuildId, result:BuildResult, cmd:String, output:String, startedAt:LocalDateTime, duration:Duration) {

}
