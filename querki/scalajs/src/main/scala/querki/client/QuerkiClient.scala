package querki.client

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.JSApp

import org.scalajs.dom

import querki.ecology._

/**
 * The root Querki Client. This is the main program that runs inside the browser, and renders
 * Querki pages in the standard modern-browser UI. It is not necessarily the be-all and end-all
 * of Querki rendering -- we're likely to have a highly dumbed-down version for older browsers,
 * and possibly some tuned apps
 */
object QuerkiClient extends JSApp with EcologyMember {
  
  /**
   * The top-level initializer for the application. This will be called first, when the page loads,
   * and should do all necessary setup. After that is done, build and display the current page.
   */
  def main(): Unit = {
    setupEcology()
  }
  
  /**
   * The One True Pointer to the Ecology. NOTHING should use this pointer outside this class! Everything
   * else should receive their own Ecology pointers, and use that!
   */
  var ecology:EcologyImpl = null
  
  /**
   * Build and initialize the Ecology. Once this is complete, the world is running.
   */
  def setupEcology() = {
    ecology = new EcologyImpl
    createEcots(ecology)
    ecology.init(ClientState()) { state => state }
  }

  /**
   * Create all of the Ecots. Every time a new one is created, it should be placed here.
   */
  def createEcots(ecology:Ecology) = {
    new querki.client.ClientImpl(ecology)
    new querki.comm.ApiCommEcot(ecology)
    new querki.data.ClientDataEcot(ecology)
    new querki.display.PageManagerEcot(ecology)
    new querki.display.input.InputGadgetsEcot(ecology)
    new querki.identity.UserManagerEcot(ecology)
    new querki.pages.PagesEcot(ecology)
  }
  
  // Entry points, exposed for the Javascript layer:
  @JSExport def dataSetting = interface[querki.data.DataSetting]
  @JSExport def pageManager = interface[querki.display.PageManager]
  @JSExport def userManager = interface[querki.identity.UserAccess]
}
