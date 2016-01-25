package querki.display

import org.scalajs.dom

import org.querki.jquery._

import querki.globals._

/**
 * The client side of the _menuButton function.
 * 
 * TBD: is this too general? Is it dangerous? It provides a way to define a button that, when clicked,
 * invokes arbitrary other objects in the window, by id. So far, I don't see a problem with that, but
 * think about it carefully.
 * 
 * @author jducoeur
 */
class MenuButton(implicit e:Ecology) extends HookedGadget[dom.html.Button](e) with EcologyMember  {
  def doRender() = ???
  
  def hook() = {
    $(elem).click { btn:dom.Element =>
      val id = $(btn).dataString("menuid")
      $(s"#$id").click()      
    }
  }
}