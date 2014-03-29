package querki.conversations

import akka.actor.{ActorRef, Props}

import querki.ecology._
import querki.identity.User
import querki.spaces.SpacePersistenceFactory
import querki.values.SpaceState

object MOIDs extends EcotIds(35) {
  val CommentTextOID = moid(1)
}
import MOIDs._

class ConversationEcot(e:Ecology) extends QuerkiEcot(e) with Conversations {
  
  val Basic = initRequires[querki.basic.Basic]
  
  lazy val AccessControl = interface[querki.security.AccessControl]
  
  // TODO: the following Props signature is now deprecated, and should be replaced (in Akka 2.2)
  // with "Props(classOf(Space), ...)". See:
  //   http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
  def conversationActorProps(persistenceFactory:SpacePersistenceFactory, spaceId:OID, space:ActorRef):Props = 
    Props(new SpaceConversationsActor(ecology, persistenceFactory, spaceId, space))
  
  // TODO: the following Props signature is now deprecated, and should be replaced (in Akka 2.2)
  // with "Props(classOf(Space), ...)". See:
  //   http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
  def conversationPersisterProps(spaceId:OID):Props = 
    Props(new ConversationPersister(spaceId, ecology))
      
  def canReadComments(req:User, thingId:OID, state:SpaceState) = {
    // TODO: this will eventually need its own permission
    AccessControl.canRead(state, req, thingId)
  }
  
  def canWriteComments(identity:OID, thingId:OID, state:SpaceState) = {
    // TODO: this will eventually need its own permission
    AccessControl.isMember(identity, state)
  }
  
  /***********************************************
   * PROPERTIES
   ***********************************************/
  
  /**
   * TODO: this shouldn't really be PlainText -- it should be a Type that is explicitly Wikitext for now.
   * Model this on QL.ParsedTextType, but make it realer.
   */
  lazy val CommentText = new SystemProperty(CommentTextOID, Basic.PlainTextType, Optional,
      toProps(
        setName("Comment Text"),
        setInternal))
  
  override lazy val props = Seq(
    CommentText
  )
}