package querki

import scala.concurrent.Future

import akka.actor.ActorRef

import anorm.SqlQuery

import models.{OID, PType, Thing}
import models.Thing.PropMap

import querki.ecology._
import querki.spaces.messages.SpaceMgrMsg
import querki.util.Sequencer
import querki.values.SpaceState

package object spaces {
  // This is a pure marker trait, indicating that this PropValue didn't load correctly yet:
  trait UnresolvedPropValue

  // The name of the Space Actor
  def sid(id:OID):String = id.toString
  
  trait SpaceOps extends EcologyInterface {
    /**
     * Fetch a reference to a SpaceManager Actor, to which you can send requests. This is the
     * only legal entry point to the Space Management system from Play! 
     */
    def spaceManager:ActorRef
    
    /**
     * Send a message to the SpaceManager, expecting a return of type A to be 
     * passed into the callback. This wraps up the messy logic to go from a
     * non-actor-based Play environment to the SpaceManager. We'll likely
     * generalize it further eventually.
     *
     * Type A is the response we expect to get back from the message, which will
     * be sent to the given callback.
     *
     * Type B is the type of the callback. I'm a little surprised that this isn't
     * inferred -- I suspect I'm doing something wrong syntactically.
     */ 
    def askSpaceManager[A,B](msg:SpaceMgrMsg)(cb: A => B)(implicit m:Manifest[A]):Future[B]
    
    /**
     * Simplified version of ask, which doesn't try to be excessively clever. This
     * one has a small disadvantage: it doesn't provide compile-time thoroughness checking
     * of the results. But on the plus side, it copes cleanly with errors from the back
     * end, which the older ask() does not.
     */
    def askSpaceManager2[B](msg:SpaceMgrMsg)(cb: PartialFunction[Any, B]):Future[B]
  }
    
  trait SpacePersistence extends EcologyInterface {
    def UnresolvedPropType:PType[String]
    
    // The name of the Space's Thing Table
    def thingTable(id:OID):String
    
    def SpaceSQL(spaceId:OID, query:String, version:Int = 0):SqlQuery
    def AttachSQL(spaceId:OID, query:String):SqlQuery

    def serializeProps(props:PropMap, space:SpaceState):String
    def deserializeProps(str:String, space:SpaceState):PropMap
    def createThingInSql(thingId:OID, spaceId:OID, modelId:OID, kind:Int, props:PropMap, serialContext:SpaceState)(implicit conn:java.sql.Connection):Int
  }
    
  case class ThingChangeRequest(state:SpaceState, modelIdOpt:Option[OID], thingOpt:Option[Thing], newProps:PropMap)

  trait SpaceChangeManager extends EcologyInterface {
    /**
     * Called before every Create or Modify operation. Listeners can use this specifically to edit the Props.
     * 
     * IMPORTANT: this is called by the Space, so it MUST NOT BLOCK.
     */
    def thingChanges:Sequencer[ThingChangeRequest]
  }
}