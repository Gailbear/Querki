package querki.api

import scala.concurrent.Future

import querki.globals._

import querki.data.ThingInfo
import querki.values.RequestContext

class PassthroughHandler(val ecology:Ecology, rc:RequestContext) extends PassthroughHandlerBase with EcologyMember {
  val ClientApi = interface[ClientApi]
  val System = interface[querki.system.System]
  
  implicit val state = System.State
  
  var contents = Map.empty[String, ThingInfo]
  
  def pass(name:String):ThingInfo = {
    state.anythingByName(name) match {
      case Some(t) => {
        val ti = ClientApi.thingInfo(t, rc)
        contents += (name -> ti)
        ti
      }
      case None => {
        throw new Exception(s"Attempting to send unknown Standard Thing $name")
      }
    }
  }
}

class CommonFunctionsImpl(info:AutowireParams)(implicit e:Ecology) extends AutowireApiImpl(info, e) with CommonFunctions
{ 
  def doRoute(req:Request):Future[String] = route[CommonFunctions](this)(req)

  def getStandardThings():Map[String, ThingInfo] = {
    val passthrough = new PassthroughHandler(ecology, rc)
    val translator = new StandardThings(passthrough)
    val toucher = translator.touchEverything()
    passthrough.contents
  }
  
  def getProgress(handle:OperationHandle):Future[OperationProgress] = {
    handle match {
      case ActorOperationHandle(path) => {
        val selection = context.system.actorSelection(path)
        selection.requestFor[OperationProgress](ProgressActor.GetProgress)
      }
      case _ => throw new Exception(s"Received unknown OperationHandle $handle")
    }
  }
  
  def acknowledgeComplete(handle:OperationHandle):Unit = {
    handle match {
      case ActorOperationHandle(path) => {
        val selection = context.system.actorSelection(path)
        selection ! ProgressActor.CompletionAcknowledged
      }
      case _ => throw new Exception(s"Received unknown OperationHandle $handle")
    }    
  }
}
