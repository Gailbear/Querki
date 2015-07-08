package querki.session

import scala.concurrent.Future
import akka.actor._
import akka.event.LoggingReceive

import upickle._
import autowire._

import org.querki.requester._

import querki.globals._
import Implicits.execContext

import querki.notifications._
import querki.notifications.NotificationPersister._
import querki.values.RequestContext

import messages.{ClientRequest, ClientResponse}

// TODO: this is still too incestuous with UserSession per se.
class UserNotifications(userId:OID, val ecology:Ecology, userSession:ActorRef) extends Actor with Stash with Requester with
  autowire.Server[String, upickle.Reader, upickle.Writer] with EcologyMember
{
  import UserSessionMessages._
  
  lazy val PersistenceFactory = interface[querki.spaces.SpacePersistenceFactory]

  lazy val notePersister = PersistenceFactory.getNotificationPersister(userId)
  
  // Autowire functions
  def write[Result: Writer](r: Result) = upickle.write(r)
  def read[Result: Reader](p: String) = upickle.read[Result](p)
  
  // This is kept in most-recent-first order:
  var currentNotes:Seq[Notification] = Seq.empty
    
  var lastNoteChecked:Int = 0
  
  // How many of the Notifications are new since this User last looked at the Notifications Window?
  def numNewNotes:Int = {
    // TODO: once we have machinery to mark notes as Read, we should filter on that here:
    val newNotes = currentNotes.filter(note => (note.id > lastNoteChecked)/* && !note.isRead*/)
    newNotes.size
  }
  
  def currentMaxNote = {
    if (currentNotes.isEmpty)
      0
    else
      currentNotes.map(_.id).max    
  }
  def nextNoteId:Int = currentMaxNote + 1
  
  override def preStart() = {
    // This will result in a UserInfo message
    // TODO: this shouldn't be going through the NotificationPersister:
    notePersister ! LoadInfo
    super.preStart()
  }
  
  def receive = LoggingReceive (handleRequestResponse orElse {
    case msg @ UserInfo(id, version, lastChecked) => {
      QLog.spew("NotificationFunctionsImpl got the UserInfo")
      lastNoteChecked = lastChecked

      // TODO: This the overly-coupled bit. Initializing the UserSession and Notifications
      // should be separate, not the same message:
      userSession.forward(msg)

      // TODO: This is broken! This is loading all of the notifications before we
      // start doing anything, which can slow down startup times significantly! We need
      // to be able to show the UI, and then send the number of new notifications when
      // we have it loaded:
      notePersister.requestFor[CurrentNotifications](Load) foreach { notes =>
        QLog.spew("NotificationFunctionsImpl got the CurrentNotifications")
        currentNotes = notes.notes.sortBy(_.id).reverse
          
        // Okay, we're ready to roll:
        context.become(mainReceive)
        unstashAll()
      }
    }

    // Hold everything else off until we've created them all:
    case msg:UserSessionMsg => stash()    
  })
  
  def mainReceive:Receive = LoggingReceive (handleRequestResponse orElse {
    
    case NewNotification(_, noteRaw) => {
      // We decide what the actual Notification Id is:
      val note = noteRaw.copy(id = nextNoteId)
      
      notePersister ! NewNotification(userId, note)
      
      currentNotes = note +: currentNotes
    }
    
    case FetchSessionInfo(_) => {
      // TODO: make this real. This doesn't conceptually belong here, but until there is more
      // than just the number of notes, we might as well put it here:
      sender ! UserSessionInfo(numNewNotes)
    }
    
    case UserSessionClientRequest(_, ClientRequest(req, rc)) => {
      req.path(2) match {
        case "NotificationFunctions" => {
          // route() is asynchronous, so we need to store away the sender!
          val senderSaved = sender
          val handler = new NotificationFunctionsImpl(this, rc)(ecology)
          route[NotificationFunctions](handler)(req).foreach { result =>
            senderSaved ! ClientResponse(result)
          }          
        }
      }
    }    
  })
}

object UserNotifications {
  def actorProps(userId:OID, ecology:Ecology, userSession:ActorRef) = Props(classOf[UserNotifications], userId, ecology, userSession)
}

class NotificationFunctionsImpl/*(info:AutowireParams)*/(notes:UserNotifications, rc:RequestContext)(implicit val ecology:Ecology)
  extends /*AutowireApiImpl(info, e) with*/ NotificationFunctions with EcologyMember
{
  lazy val ClientApi = interface[querki.api.ClientApi]
  lazy val IdentityAccess = interface[querki.identity.IdentityAccess]
  lazy val Notifications = interface[querki.notifications.Notifications]
  
  def getRecentNotifications():Future[Seq[NotificationInfo]] = {
    // TODO: update lastNoteChecked. We probably should be refactoring all that Notification stuff into here.
    val identityIds = notes.currentNotes.map(_.sender)
    IdentityAccess.getIdentities(identityIds).map { identities =>
      try {
	      notes.currentNotes.map { note =>
	        val sender = ClientApi.identityInfo(identities(note.sender))
	        val rendered = Notifications.render(rc, note)
	        NotificationInfo(
	          note.id,
	          sender,
	          note.spaceId.map(_.toThingId.toString).getOrElse(""),
	          note.thingId.map(_.toThingId.toString).getOrElse(""),
	          // TODO: This should really be an implicit conversion:
	          note.sentTime.getMillis,
	          rendered,
	          note.isRead,
	          note.isDeleted
	        )
	      }
      } catch {
        case ex:Exception => { QLog.error("Exception in getRecentNotifications", ex); throw ex }
      }
    }
  }
  
  def numNewNotifications():Int = {
    notes.numNewNotes
  }
  
  def readThrough(id:NotificationId):Unit = {
    if (id > notes.lastNoteChecked) {
      notes.lastNoteChecked = id
      notes.notePersister ! UpdateLastChecked(notes.lastNoteChecked)
    }
  }
}
