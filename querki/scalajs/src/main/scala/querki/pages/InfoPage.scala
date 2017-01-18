package querki.pages

import scalatags.JsDom.all._
import autowire._

import models.Wikitext
import querki.api.ThingFunctions
import querki.display._
import querki.globals._

class InfoPage(params:ParamMap)(implicit val ecology:Ecology) extends Page() {
  
  lazy val Apps = interface[querki.apps.Apps]
  lazy val Client = interface[querki.client.Client]
  lazy val SkillLevel = interface[querki.identity.skilllevel.SkillLevel]
  
  // In theory, you can only get to this Page if you're in a Space.
  // TODO: we really should have a way of formalizing that. The Space might actually
  // be a *parameter* to this Page instead?
  lazy val spaceInfo = DataAccess.space.get
  
  lazy val isApp = spaceInfo.isApp
  
  def pageContent = {
    val summaryFut = Client[ThingFunctions].getPropertyDisplay(spaceInfo.oid, std.conventions.summaryProp.oid).call()
    val detailsFut = Client[ThingFunctions].getPropertyDisplay(spaceInfo.oid, std.conventions.detailsProp.oid).call()
    
    for {
      summaryOpt <- summaryFut
      summaryText = summaryOpt.getOrElse(Wikitext.empty)
      detailsOpt <- detailsFut
      detailsText = detailsOpt.getOrElse(Wikitext.empty)
      // For the moment, we only allow you to extract an App if there isn't already an App. This will change.
      allowExtract = 
        DataAccess.request.isOwner && 
        (SkillLevel.current == SkillLevel.AdvancedComplexity) &&
        spaceInfo.apps.isEmpty
      guts =
        div(
          h1(s"Info about ${if(isApp) "App" else ""} ${spaceInfo.displayName} ", SpaceLinkButton(ButtonGadget.Primary, "Done")),
          
          p(b(new QText(summaryText))),
          new QText(detailsText),
          
          if (isApp)
            // Let the user instantiate this App:
            div(
              h3("Press this button to use this App yourself:"),
              p(new ButtonGadget(ButtonGadget.Normal, s"Create a Space using ${spaceInfo.displayName}")({ () => Apps.useApp() }))
            ),
          
          // Do we show the Apps section? For the time being, we specifically don't do so for Apps:
          if (!isApp && (allowExtract || !spaceInfo.apps.isEmpty)) {
            MSeq(
              h2("Apps"),
              for (app <- spaceInfo.apps)
                // Link to the Info Page of the App:
                yield QText(
                  s"""**${IndexPage.spaceLink(app, app.displayName, Pages.infoFactory)}**"""),
              
              if (allowExtract) {
                div(
                  a(
                    cls:="btn btn-default btn-xs btn-primary_noPrint", 
                    href:=Apps.extractAppFactory.pageUrl(),
                    "Extract an App from this Space")
                )
              }
            )
          }
        )
    }
      yield PageContents(s"Info about ${spaceInfo.displayName}", guts)
  }
}
