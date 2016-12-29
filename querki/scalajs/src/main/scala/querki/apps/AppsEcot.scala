package querki.apps

import scalatags.JsDom.all._
import autowire._

import querki.display.{ButtonGadget, Dialog}
import querki.display.rx.{GadgetRef, RxInput}
import querki.ecology._
import querki.globals._
import querki.pages.CreateSpacePage
import querki.session.UserFunctions
import querki.util.InputUtils

/**
 * @author jducoeur
 */
class AppsEcot(e:Ecology) extends ClientEcot(e) with Apps {
  def implements = Set(classOf[Apps])
  
  lazy val Client = interface[querki.client.Client]
  lazy val DataAccess = interface[querki.data.DataAccess]
  lazy val Pages = interface[querki.pages.Pages]
  
  lazy val appMgmtFactory = Pages.registerStandardFactory("_appMgmt", { (params) => new AppManagementPage(params) })
  lazy val extractAppFactory = Pages.registerStandardFactory("_extractApp", { (params) => new ExtractAppPage(params) })
  
  override def postInit() = {
    appMgmtFactory
    extractAppFactory
  }
    
  lazy val spaceInfo = DataAccess.space.get
  lazy val isApp = spaceInfo.isApp
  
  def useApp() = {
    // Belt-and-suspenders check:
    if (isApp) {
      val spaceName = GadgetRef[RxInput]
      spaceName.whenSet { g => 
        g.onEnter { text =>
          if (text.length() > 0) {
            createSpace()
          }
        }
      }
      
      def createSpace() = {
        val newName = spaceName.get.text().trim
        if (newName.length > 0) {
          Client[UserFunctions].createSpace(newName, Some(spaceInfo.oid)).call().map { newSpaceInfo =>
            CreateSpacePage.navigateToSpace(newSpaceInfo)
          }
        }
      }
      
      val confirmDialog = new Dialog(
        s"Create a Space using ${spaceInfo.displayName}",
        div(
          p(s"This will create a new Space, owned by you, using ${spaceInfo.displayName}. What should the Space be named?"),
          spaceName <= new RxInput(
              Some(InputUtils.spaceNameFilter _), "text", value := spaceInfo.displayName,
              id:="_newSpaceName", cls:="form-control", maxlength:=254, tabindex:=200)
        ),
        (ButtonGadget.Primary, Seq("Create", id := "_modelSelected"), { dialog =>
          createSpace
          dialog.done()
        }),
        (ButtonGadget.Normal, Seq("Cancel", id := "_modelCancel"), { dialog => 
          dialog.done() 
        })
      )
      
      confirmDialog.show()
    }
  }
}
